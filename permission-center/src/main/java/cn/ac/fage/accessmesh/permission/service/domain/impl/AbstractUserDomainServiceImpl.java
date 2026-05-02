package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.AbstractUser;
import cn.ac.fage.accessmesh.permission.enums.UserType;
import cn.ac.fage.accessmesh.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.permission.service.domain.AbstractUserDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static cn.ac.fage.accessmesh.permission.entity.table.AbstractUserTableDef.ABSTRACT_USER;

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
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setDeleteFlag(0L);
        abstractUserMapper.insert(user);
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long tenantId, Long userId) {
        AbstractUser user = abstractUserMapper.selectOneById(userId);
        if (user != null && user.getDeleteFlag() == 0L) {
            user.setDeleteFlag(user.getId());
            user.setDeletedAt(LocalDateTime.now());
            abstractUserMapper.update(user);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enableUser(Long tenantId, Long userId) {
        updateUserStatus(userId, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disableUser(Long tenantId, Long userId) {
        updateUserStatus(userId, false);
    }

    @Override
    public AbstractUser findByExternalId(Long tenantId, Integer userType, String externalId) {
        return abstractUserMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(ABSTRACT_USER.TENANT_ID.eq(tenantId))
                .and(ABSTRACT_USER.USER_TYPE.eq(userType))
                .and(ABSTRACT_USER.EXTERNAL_ID.eq(externalId))
                .and(ABSTRACT_USER.DELETE_FLAG.eq(0))
        );
    }

    private void updateUserStatus(Long userId, boolean enabled) {
        AbstractUser user = new AbstractUser();
        user.setId(userId);
        user.setEnabled(enabled);
        user.setUpdatedAt(LocalDateTime.now());
        abstractUserMapper.update(user);
    }

    @Override
    public AbstractUser selectValidById(Long tenantId, Long userId) {
        if (userId == null) {
            return null;
        }
        return abstractUserMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(ABSTRACT_USER.ID.eq(userId))
                .and(ABSTRACT_USER.TENANT_ID.eq(tenantId))
                .and(ABSTRACT_USER.DELETE_FLAG.eq(0))
        );
    }
}
