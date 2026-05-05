package cn.ac.fage.accessmesh.admin.service.domain.impl;

import cn.ac.fage.accessmesh.admin.entity.SysUser;
import cn.ac.fage.accessmesh.admin.mapper.SysUserMapper;
import cn.ac.fage.accessmesh.admin.service.domain.UserDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import cn.ac.fage.accessmesh.admin.entity.table.SysUserTableDef;

@Service
public class UserDomainServiceImpl implements UserDomainService {

    private final SysUserMapper userMapper;

    public UserDomainServiceImpl(SysUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public SysUser selectValidById(Long tenantId, Long userId) {
        if (userId == null) {
            return null;
        }
        return userMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SysUserTableDef.SYS_USER.ID.eq(userId))
                .and(SysUserTableDef.SYS_USER.TENANT_ID.eq(tenantId))
                .and(SysUserTableDef.SYS_USER.DELETE_FLAG.eq(0))
        );
    }

    @Override
    public List<SysUser> selectValidByIds(Long tenantId, Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return userMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SysUserTableDef.SYS_USER.TENANT_ID.eq(tenantId))
                .and(SysUserTableDef.SYS_USER.ID.in(userIds))
                .and(SysUserTableDef.SYS_USER.DELETE_FLAG.eq(0))
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDeleteBatch(Long tenantId, List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        userMapper.softDeleteBatch(tenantId, userIds, LocalDateTime.now());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdateStatus(Long tenantId, List<Long> userIds, Integer status) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        userMapper.batchUpdateStatus(tenantId, userIds, status, LocalDateTime.now());
    }

    @Override
    public SysUser findByUsername(Long tenantId, String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return userMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SysUserTableDef.SYS_USER.TENANT_ID.eq(tenantId))
                .and(SysUserTableDef.SYS_USER.USERNAME.eq(username))
                .and(SysUserTableDef.SYS_USER.DELETE_FLAG.eq(0))
        );
    }

    @Override
    public SysUser findByPhone(Long tenantId, String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return userMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SysUserTableDef.SYS_USER.TENANT_ID.eq(tenantId))
                .and(SysUserTableDef.SYS_USER.PHONE.eq(phone))
                .and(SysUserTableDef.SYS_USER.DELETE_FLAG.eq(0))
        );
    }

    @Override
    public boolean existsByUsername(Long tenantId, String username) {
        return findByUsername(tenantId, username) != null;
    }

    @Override
    public boolean existsByPhone(Long tenantId, String phone) {
        return findByPhone(tenantId, phone) != null;
    }
}