package org.dromara.permission.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.*;
import org.dromara.permission.domain.dto.RolePermissionAddReq;
import org.dromara.permission.domain.dto.RolePermissionListReq;
import org.dromara.permission.domain.dto.RolePermissionRemoveReq;
import org.dromara.permission.domain.vo.RolePermissionVo;
import org.dromara.permission.mapper.*;
import org.dromara.permission.service.PermissionChangeLogService;
import org.dromara.permission.service.RolePermissionService;
import org.dromara.permission.service.support.PermissionAuditSupport;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RolePermissionServiceImpl implements RolePermissionService {

    private final PcRoleResourcePermissionMapper roleResourcePermissionMapper;
    private final PcResourceEntityMapper resourceEntityMapper;
    private final PcOperationPermissionMapper operationPermissionMapper;
    private final PermissionChangeLogService permissionChangeLogService;

    @Override
    public List<RolePermissionVo> list(RolePermissionListReq req) {
        if (req == null || req.getTenantId() == null || req.getAbstractRoleId() == null) {
            return new ArrayList<>();
        }
        List<PcRoleResourcePermission> list = roleResourcePermissionMapper.selectList(
            new LambdaQueryWrapper<PcRoleResourcePermission>()
                .eq(PcRoleResourcePermission::getTenantId, req.getTenantId())
                .eq(PcRoleResourcePermission::getAbstractRoleId, req.getAbstractRoleId())
                .eq(PcRoleResourcePermission::getDeleteFlag, PermissionConstants.NOT_DELETED)
                .orderByDesc(PcRoleResourcePermission::getId));
        if (list.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> resourceIds = list.stream().map(PcRoleResourcePermission::getResourceEntityId).distinct().collect(Collectors.toList());
        List<Long> opIds = list.stream().map(PcRoleResourcePermission::getOperationPermissionId).distinct().collect(Collectors.toList());
        Map<Long, String> resourceNameMap = new HashMap<>();
        Map<Long, String> opNameMap = new HashMap<>();
        if (!resourceIds.isEmpty()) {
            resourceEntityMapper.selectBatchIds(resourceIds).stream()
                .filter(r -> PermissionConstants.NOT_DELETED.equals(r.getDeleteFlag()))
                .forEach(r -> resourceNameMap.put(r.getId(), r.getName()));
        }
        if (!opIds.isEmpty()) {
            operationPermissionMapper.selectBatchIds(opIds).stream()
                .filter(o -> PermissionConstants.NOT_DELETED.equals(o.getDeleteFlag()))
                .forEach(o -> opNameMap.put(o.getId(), o.getName()));
        }
        List<RolePermissionVo> result = new ArrayList<>();
        for (PcRoleResourcePermission rrp : list) {
            RolePermissionVo vo = new RolePermissionVo();
            BeanUtils.copyProperties(rrp, vo);
            vo.setResourceName(resourceNameMap.get(rrp.getResourceEntityId()));
            vo.setOperationName(opNameMap.get(rrp.getOperationPermissionId()));
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(RolePermissionAddReq req) {
        if (req == null || req.getTenantId() == null || req.getAbstractRoleId() == null || CollUtil.isEmpty(req.getItems())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (RolePermissionAddReq.RolePermissionItem item : req.getItems()) {
            if (item.getResourceEntityId() == null || item.getOperationPermissionId() == null) {
                continue;
            }
            PcRoleResourcePermission existing = roleResourcePermissionMapper.selectOne(
                new LambdaQueryWrapper<PcRoleResourcePermission>()
                    .eq(PcRoleResourcePermission::getTenantId, req.getTenantId())
                    .eq(PcRoleResourcePermission::getAbstractRoleId, req.getAbstractRoleId())
                    .eq(PcRoleResourcePermission::getResourceEntityId, item.getResourceEntityId())
                    .eq(PcRoleResourcePermission::getOperationPermissionId, item.getOperationPermissionId())
                    .eq(PcRoleResourcePermission::getDeleteFlag, PermissionConstants.NOT_DELETED));
            if (existing != null) {
                existing.setCanManage(Boolean.TRUE.equals(item.getCanManage()));
                existing.setConditionId(item.getConditionId());
                existing.setUpdatedAt(now);
                roleResourcePermissionMapper.updateById(existing);
            } else {
                PcRoleResourcePermission rrp = new PcRoleResourcePermission();
                rrp.setTenantId(req.getTenantId());
                rrp.setAbstractRoleId(req.getAbstractRoleId());
                rrp.setResourceEntityId(item.getResourceEntityId());
                rrp.setOperationPermissionId(item.getOperationPermissionId());
                rrp.setCanManage(Boolean.TRUE.equals(item.getCanManage()));
                rrp.setConditionId(item.getConditionId());
                rrp.setDeleteFlag(PermissionConstants.NOT_DELETED);
                rrp.setCreatedAt(now);
                rrp.setUpdatedAt(now);
                roleResourcePermissionMapper.insert(rrp);
            }
        }
        permissionChangeLogService.writeChangeLog(req.getTenantId(), null, "role_resource_permission", req.getAbstractRoleId(), "UPSERT", null, req, null, "API");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(RolePermissionRemoveReq req) {
        if (req == null || req.getTenantId() == null || req.getAbstractRoleId() == null || CollUtil.isEmpty(req.getItems())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (RolePermissionRemoveReq.RolePermissionPair pair : req.getItems()) {
            if (pair.getResourceEntityId() == null || pair.getOperationPermissionId() == null) {
                continue;
            }
            PcRoleResourcePermission rrp = roleResourcePermissionMapper.selectOne(
                new LambdaQueryWrapper<PcRoleResourcePermission>()
                    .eq(PcRoleResourcePermission::getTenantId, req.getTenantId())
                    .eq(PcRoleResourcePermission::getAbstractRoleId, req.getAbstractRoleId())
                    .eq(PcRoleResourcePermission::getResourceEntityId, pair.getResourceEntityId())
                    .eq(PcRoleResourcePermission::getOperationPermissionId, pair.getOperationPermissionId())
                    .eq(PcRoleResourcePermission::getDeleteFlag, PermissionConstants.NOT_DELETED));
            if (rrp != null) {
                PermissionAuditSupport.markDeleted(rrp, rrp.getId(), now);
                roleResourcePermissionMapper.updateById(rrp);
            }
        }
        permissionChangeLogService.writeChangeLog(req.getTenantId(), null, "role_resource_permission", req.getAbstractRoleId(), "DELETE", null, req, null, "API");
    }
}
