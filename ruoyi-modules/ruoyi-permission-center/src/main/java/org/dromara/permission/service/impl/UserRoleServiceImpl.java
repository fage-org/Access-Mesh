package org.dromara.permission.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.permission.constant.PermissionConstants;
import org.dromara.permission.domain.PcAbstractRole;
import org.dromara.permission.domain.PcAbstractUser;
import org.dromara.permission.domain.PcUserRole;
import org.dromara.permission.domain.dto.UserRoleAssignReq;
import org.dromara.permission.domain.dto.UserRoleListReq;
import org.dromara.permission.domain.dto.UserRoleRevokeReq;
import org.dromara.permission.domain.vo.UserRoleVo;
import org.dromara.permission.mapper.PcAbstractRoleMapper;
import org.dromara.permission.mapper.PcAbstractUserMapper;
import org.dromara.permission.mapper.PcUserRoleMapper;
import org.dromara.permission.service.PermissionChangeLogService;
import org.dromara.permission.service.UserRoleService;
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
public class UserRoleServiceImpl implements UserRoleService {

    private final PcUserRoleMapper userRoleMapper;
    private final PcAbstractUserMapper abstractUserMapper;
    private final PcAbstractRoleMapper abstractRoleMapper;
    private final PermissionChangeLogService permissionChangeLogService;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assign(UserRoleAssignReq req) {
        if (req == null || req.getTenantId() == null || req.getAbstractUserId() == null || CollUtil.isEmpty(req.getRoleIds())) {
            return;
        }
        PcAbstractUser user = abstractUserMapper.selectOne(new LambdaQueryWrapper<PcAbstractUser>()
            .eq(PcAbstractUser::getTenantId, req.getTenantId())
            .eq(PcAbstractUser::getId, req.getAbstractUserId())
            .eq(PcAbstractUser::getDeleteFlag, PermissionConstants.NOT_DELETED));
        if (user == null) {
            throw new IllegalArgumentException("abstract_user not found: " + req.getAbstractUserId());
        }
        LocalDateTime now = LocalDateTime.now();
        for (Long roleId : req.getRoleIds()) {
            PcAbstractRole role = abstractRoleMapper.selectOne(new LambdaQueryWrapper<PcAbstractRole>()
                .eq(PcAbstractRole::getTenantId, req.getTenantId())
                .eq(PcAbstractRole::getId, roleId)
                .eq(PcAbstractRole::getDeleteFlag, PermissionConstants.NOT_DELETED));
            if (role == null) {
                continue;
            }
            PcUserRole existing = userRoleMapper.selectOne(new LambdaQueryWrapper<PcUserRole>()
                .eq(PcUserRole::getTenantId, req.getTenantId())
                .eq(PcUserRole::getAbstractUserId, req.getAbstractUserId())
                .eq(PcUserRole::getAbstractRoleId, roleId)
                .eq(PcUserRole::getDeleteFlag, PermissionConstants.NOT_DELETED));
            if (existing != null) {
                existing.setValidFrom(req.getValidFrom());
                existing.setValidTo(req.getValidTo());
                existing.setUpdatedAt(now);
                userRoleMapper.updateById(existing);
            } else {
                PcUserRole ur = new PcUserRole();
                ur.setTenantId(req.getTenantId());
                ur.setAbstractUserId(req.getAbstractUserId());
                ur.setAbstractRoleId(roleId);
                ur.setValidFrom(req.getValidFrom());
                ur.setValidTo(req.getValidTo());
                ur.setDeleteFlag(PermissionConstants.NOT_DELETED);
                ur.setCreatedAt(now);
                ur.setUpdatedAt(now);
                userRoleMapper.insert(ur);
            }
        }
        permissionChangeLogService.writeChangeLog(req.getTenantId(), null, "user_role", req.getAbstractUserId(), "UPSERT", null, req, null, "API");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revoke(UserRoleRevokeReq req) {
        if (req == null || req.getTenantId() == null || req.getAbstractUserId() == null || CollUtil.isEmpty(req.getRoleIds())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (Long roleId : req.getRoleIds()) {
            PcUserRole ur = userRoleMapper.selectOne(new LambdaQueryWrapper<PcUserRole>()
                .eq(PcUserRole::getTenantId, req.getTenantId())
                .eq(PcUserRole::getAbstractUserId, req.getAbstractUserId())
                .eq(PcUserRole::getAbstractRoleId, roleId)
                .eq(PcUserRole::getDeleteFlag, PermissionConstants.NOT_DELETED));
            if (ur != null) {
                PermissionAuditSupport.markDeleted(ur, ur.getId(), now);
                userRoleMapper.updateById(ur);
            }
        }
        permissionChangeLogService.writeChangeLog(req.getTenantId(), null, "user_role", req.getAbstractUserId(), "DELETE", null, req, null, "API");
    }
}
