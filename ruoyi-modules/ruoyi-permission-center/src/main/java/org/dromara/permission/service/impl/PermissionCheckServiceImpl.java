package org.dromara.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcAbstractRole;
import org.dromara.permission.domain.PcPermissionCondition;
import org.dromara.permission.domain.PcResourceDependency;
import org.dromara.permission.domain.PcRoleResourcePermission;
import org.dromara.permission.domain.PcUserRole;
import org.dromara.permission.domain.dto.PermissionCheckReq;
import org.dromara.permission.domain.vo.PermissionCheckVo;
import org.dromara.permission.mapper.PcAbstractRoleMapper;
import org.dromara.permission.mapper.PcPermissionConditionMapper;
import org.dromara.permission.mapper.PcResourceDependencyMapper;
import org.dromara.permission.mapper.PcRoleResourcePermissionMapper;
import org.dromara.permission.mapper.PcUserRoleMapper;
import org.dromara.permission.service.PermissionCheckService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
        boolean hasGrant = hasRoleResourceGrant(req.getTenantId(), roleIds, req.getResourceEntityId(),
            req.getOperationPermissionId(), req.getContext());
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

    private boolean hasRoleResourceGrant(Long tenantId, List<Long> roleIds, Long resourceEntityId, Long operationPermissionId,
                                         Map<String, Object> context) {
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
                if (!isConditionSatisfied(cond, context)) {
                    continue;
                }
            }
            return true;
        }
        return false;
    }

    private boolean isConditionSatisfied(PcPermissionCondition condition, Map<String, Object> evalContext) {
        if (condition == null || !PermissionConstants.NOT_DELETED.equals(condition.getDeleteFlag())) {
            return false;
        }
        if (!PermissionConstants.CONDITION_STATUS_APPROVED.equals(condition.getStatus())) {
            return false;
        }
        if (condition.getExpression() == null || condition.getExpression().isBlank()) {
            return true;
        }
        if ("true".equalsIgnoreCase(condition.getExpression())) {
            return true;
        }
        if ("false".equalsIgnoreCase(condition.getExpression())) {
            return false;
        }
        if (evalContext == null || evalContext.isEmpty()) {
            return false;
        }
        return resolveContextBoolean(evalContext, condition.getCode(), condition.getExpression());
    }

    private boolean resolveContextBoolean(Map<String, Object> evalContext, String code, String expression) {
        List<String> keys = new ArrayList<>();
        if (code != null && !code.isBlank()) {
            keys.add(code);
            keys.add("condition:" + code);
        }
        if (expression != null && !expression.isBlank()) {
            keys.add(expression);
            keys.add("condition:" + expression);
        }
        for (String key : keys) {
            Object value = evalContext.get(key);
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value instanceof String str) {
                if ("true".equalsIgnoreCase(str)) {
                    return true;
                }
                if ("false".equalsIgnoreCase(str)) {
                    return false;
                }
            }
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
            boolean depGrant = hasRoleResourceGrant(req.getTenantId(), roleIds, dep.getDependsOnResourceEntityId(),
                dep.getRequiredOperationPermissionId(), req.getContext());
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
