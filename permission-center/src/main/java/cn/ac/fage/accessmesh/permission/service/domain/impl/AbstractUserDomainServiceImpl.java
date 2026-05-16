package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.AbstractUser;
import cn.ac.fage.accessmesh.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.permission.service.domain.AbstractUserDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 抽象用户领域服务实现类
 * <p>
 * 提供抽象用户（AbstractUser）的CRUD操作。
 * 抽象用户是权限系统的统一用户模型，支持多种用户类型
 * （如系统用户、外部用户、服务账号等）。
 * 所有写操作均使用事务保证数据一致性。
 * 软删除通过设置deleteFlag实现，保留历史数据可追溯。
 * </p>
 */
@Service
public class AbstractUserDomainServiceImpl implements AbstractUserDomainService {

    private final AbstractUserMapper abstractUserMapper;

    public AbstractUserDomainServiceImpl(AbstractUserMapper abstractUserMapper) {
        this.abstractUserMapper = abstractUserMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AbstractUser createUser(Integer userType, String externalId, String name, Boolean enabled, String extra, Long tenantId) {
        AbstractUser user = new AbstractUser();
        user.setTenantId(tenantId);
        user.setUserType(userType);
        user.setExternalId(externalId);
        user.setName(name);
        user.setEnabled(enabled != null ? enabled : true);
        user.setExtra(extra);
        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setDeleteFlag(0L);
        abstractUserMapper.insert(user);
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long tenantId, Long userId) {
        AbstractUser user = selectValidById(tenantId, userId);
        if (user != null) {
            user.setDeleteFlag(user.getId());
            user.setDeletedAt(LocalDateTime.now());
            abstractUserMapper.update(user);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enableUser(Long tenantId, Long userId) {
        updateUserStatus(tenantId, userId, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disableUser(Long tenantId, Long userId) {
        updateUserStatus(tenantId, userId, false);
    }

    @Override
    public AbstractUser findByExternalId(Long tenantId, Integer userType, String externalId) {
        return abstractUserMapper.selectByTypeAndExternalId(tenantId, userType, externalId);
    }

    private void updateUserStatus(Long tenantId, Long userId, boolean enabled) {
        AbstractUser existing = selectValidById(tenantId, userId);
        if (existing == null) {
            return;
        }
        existing.setEnabled(enabled);
        existing.setUpdatedAt(LocalDateTime.now());
        abstractUserMapper.update(existing);
    }

    @Override
    public AbstractUser selectValidById(Long tenantId, Long userId) {
        if (userId == null) {
            return null;
        }
        return abstractUserMapper.selectValidById(userId, tenantId);
    }
}