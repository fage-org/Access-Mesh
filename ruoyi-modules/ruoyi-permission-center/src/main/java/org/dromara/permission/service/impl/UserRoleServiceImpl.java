package org.dromara.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcAbstractRole;
import org.dromara.permission.domain.PcUserRole;
import org.dromara.permission.domain.dto.UserRoleListReq;
import org.dromara.permission.domain.vo.UserRoleVo;
import org.dromara.permission.mapper.PcAbstractRoleMapper;
import org.dromara.permission.mapper.PcUserRoleMapper;
import org.dromara.permission.service.UserRoleService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserRoleServiceImpl implements UserRoleService {

    private final PcUserRoleMapper userRoleMapper;
    private final PcAbstractRoleMapper abstractRoleMapper;

    @Override
    public List<UserRoleVo> list(UserRoleListReq req) {
        if (req == null || req.getTenantId() == null || req.getAbstractUserId() == null) {
            return new ArrayList<>();
        }
        List<PcUserRole> list = userRoleMapper.selectList(new LambdaQueryWrapper<PcUserRole>()
            .eq(PcUserRole::getTenantId, req.getTenantId())
            .eq(PcUserRole::getAbstractUserId, req.getAbstractUserId())
            .eq(PcUserRole::getDeleteFlag, PermissionConstants.NOT_DELETED)
            .orderByDesc(PcUserRole::getId));
        if (list.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> roleIds = list.stream().map(PcUserRole::getAbstractRoleId).distinct().collect(Collectors.toList());
        Map<Long, String> roleNameMap = new HashMap<>();
        if (!roleIds.isEmpty()) {
            List<PcAbstractRole> roles = abstractRoleMapper.selectBatchIds(roleIds);
            for (PcAbstractRole r : roles) {
                if (PermissionConstants.NOT_DELETED.equals(r.getDeleteFlag())) {
                    roleNameMap.put(r.getId(), r.getName());
                }
            }
        }
        List<UserRoleVo> result = new ArrayList<>();
        for (PcUserRole ur : list) {
            UserRoleVo vo = new UserRoleVo();
            BeanUtils.copyProperties(ur, vo);
            vo.setRoleName(roleNameMap.get(ur.getAbstractRoleId()));
            result.add(vo);
        }
        return result;
    }
}
