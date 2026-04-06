package org.dromara.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcOperationPermission;
import org.dromara.permission.domain.PcResourceEntity;
import org.dromara.permission.domain.PcRoleResourcePermission;
import org.dromara.permission.domain.dto.RolePermissionListReq;
import org.dromara.permission.domain.vo.RolePermissionVo;
import org.dromara.permission.mapper.PcOperationPermissionMapper;
import org.dromara.permission.mapper.PcResourceEntityMapper;
import org.dromara.permission.mapper.PcRoleResourcePermissionMapper;
import org.dromara.permission.service.RolePermissionService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

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
}
