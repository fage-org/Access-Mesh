package org.dromara.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.*;
import org.dromara.permission.domain.dto.PermissionCheckReq;
import org.dromara.permission.domain.vo.PermissionCheckVo;
import org.dromara.permission.mapper.*;
import org.dromara.permission.service.PermissionCheckService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 鉴权实现：解析用户角色 → 解析授权 → 依赖展开
 */
@Service
@RequiredArgsConstructor
public class PermissionCheckServiceImpl implements PermissionCheckService {

    private static final int DEPTH_LIMIT = 5;

    private final PcUserRoleMapper userRoleMapper;
    private final PcAbstractRoleMapper abstractRoleMapper;
    private final PcRoleResourcePermissionMapper roleResourcePermissionMapper;
    private final PcPermissionConditionMapper permissionConditionMapper;
    private final PcResourceDependencyMapper resourceDependencyMapper;

    @Override
    public PermissionCheckVo check(PermissionCheckReq req) {
        if (req == null || req.getTenantId() == null || req.getAbstractUserId() == null
            || req.getResourceEntityId() == null || req.getOperationPermissionId() == null) {
            return PermissionCheckVo.deny("参数不完整");
        }
        List<Long> roleIds = resolveUserRoleIds(req.getTenantId(), req.getAbstractUserId(), req.getBizDomainId());
        if (roleIds.isEmpty()) {
            return PermissionCheckVo.deny("无角色");
        }
        boolean hasGrant = hasRoleResourceGrant(req.getTenantId(), roleIds, req.getResourceEntityId(), req.getOperationPermissionId(), req.getContext());
        if (!hasGrant) {
            return PermissionCheckVo.deny("无授权");
        }
        boolean depsOk = checkDependencies(req, roleIds, 0);
        if (!depsOk) {
            return PermissionCheckVo.deny("依赖不满足");
        }
        return PermissionCheckVo.allow();
    }

    private List<Long> resolveUserRoleIds(Long tenantId, Long abstractUserId, Long bizDomainId) {
        LocalDateTime now = LocalDateTime.now();
        List<PcUserRole> urList = userRoleMapper.selectList(new LambdaQueryWrapper<PcUserRole>()
            .eq(PcUserRole::getTenantId, tenantId)
            .eq(PcUserRole::getAbstractUserId, abstractUserId)
            .eq(PcUserRole::getDeleteFlag, PermissionConstants.NOT_DELETED));
        List<Long> roleIds = new ArrayList<>();
        for (PcUserRole ur : urList) {
            if (ur.getValidFrom() != null && now.isBefore(ur.getValidFrom())) {
                continue;
            }
            if (ur.getValidTo() != null && now.isAfter(ur.getValidTo())) {
                continue;
            }
            roleIds.add(ur.getAbstractRoleId());
        }
        if (roleIds.isEmpty()) {
            return roleIds;
        }
        List<PcAbstractRole> roles = abstractRoleMapper.selectBatchIds(roleIds);
        Set<Long> validRoleIds = roles.stream()
            .filter(r -> PermissionConstants.NOT_DELETED.equals(r.getDeleteFlag()))
            .filter(r -> bizDomainId == null || r.getBizDomainId() == null || r.getBizDomainId().equals(bizDomainId))
            .map(PcAbstractRole::getId)
            .collect(Collectors.toSet());
        return new ArrayList<>(validRoleIds);
    }

    private boolean hasRoleResourceGrant(Long tenantId, List<Long> roleIds, Long resourceEntityId, Long operationPermissionId, java.util.Map<String, String> context) {
        List<PcRoleResourcePermission> list = roleResourcePermissionMapper.selectList(
            new LambdaQueryWrapper<PcRoleResourcePermission>()
                .eq(PcRoleResourcePermission::getTenantId, tenantId)
                .in(PcRoleResourcePermission::getAbstractRoleId, roleIds)
                .eq(PcRoleResourcePermission::getResourceEntityId, resourceEntityId)
                .eq(PcRoleResourcePermission::getOperationPermissionId, operationPermissionId)
                .eq(PcRoleResourcePermission::getDeleteFlag, PermissionConstants.NOT_DELETED));
        for (PcRoleResourcePermission rrp : list) {
            if (rrp.getConditionId() != null) {
                PcPermissionCondition cond = permissionConditionMapper.selectById(rrp.getConditionId());
                if (cond != null && PermissionConstants.NOT_DELETED.equals(cond.getDeleteFlag()) && cond.getExpression() != null && !cond.getExpression().isEmpty()) {
                    // 条件表达式求值暂不实现，视为不通过
                    continue;
                }
            }
            return true;
        }
        return false;
    }

    private boolean checkDependencies(PermissionCheckReq req, List<Long> roleIds, int depth) {
        if (depth >= DEPTH_LIMIT) {
            return false;
        }
        List<PcResourceDependency> deps = resourceDependencyMapper.selectList(
            new LambdaQueryWrapper<PcResourceDependency>()
                .eq(PcResourceDependency::getTenantId, req.getTenantId())
                .eq(PcResourceDependency::getResourceEntityId, req.getResourceEntityId())
                .and(w -> w.isNull(PcResourceDependency::getSourceOperationPermissionId)
                    .or().eq(PcResourceDependency::getSourceOperationPermissionId, req.getOperationPermissionId()))
                .eq(PcResourceDependency::getDeleteFlag, PermissionConstants.NOT_DELETED));
        for (PcResourceDependency dep : deps) {
            boolean depGrant = hasRoleResourceGrant(req.getTenantId(), roleIds, dep.getDependsOnResourceEntityId(), dep.getRequiredOperationPermissionId(), req.getContext());
            if (!depGrant) {
                return false;
            }
            PermissionCheckReq subReq = new PermissionCheckReq();
            subReq.setTenantId(req.getTenantId());
            subReq.setAbstractUserId(req.getAbstractUserId());
            subReq.setResourceEntityId(dep.getDependsOnResourceEntityId());
            subReq.setOperationPermissionId(dep.getRequiredOperationPermissionId());
            subReq.setBizDomainId(req.getBizDomainId());
            subReq.setContext(req.getContext());
            if (!checkDependencies(subReq, roleIds, depth + 1)) {
                return false;
            }
        }
        return true;
    }
}
