package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.req.UserAssignRoleReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserSyncReq;
import cn.ac.fage.accessmesh.permission.dto.resp.UserResp;
import cn.ac.fage.accessmesh.permission.dto.resp.UserRolesResp;
import cn.ac.fage.accessmesh.permission.dto.resp.UserRolesResp.RoleSummary;
import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.entity.AbstractUser;
import cn.ac.fage.accessmesh.permission.entity.UserRole;
import cn.ac.fage.accessmesh.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.permission.mapper.UserRoleMapper;
import cn.ac.fage.accessmesh.permission.service.UserManageService;
import cn.ac.fage.accessmesh.permission.service.domain.UserRoleDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.permission.entity.table.AbstractRoleTableDef.ABSTRACT_ROLE;
import static cn.ac.fage.accessmesh.permission.entity.table.AbstractUserTableDef.ABSTRACT_USER;
import static cn.ac.fage.accessmesh.permission.entity.table.UserRoleTableDef.USER_ROLE;

@Service
public class UserManageServiceImpl implements UserManageService {

    private final AbstractUserMapper abstractUserMapper;
    private final UserRoleMapper userRoleMapper;
    private final AbstractRoleMapper abstractRoleMapper;
    private final UserRoleDomainService userRoleDomainService;

    public UserManageServiceImpl(AbstractUserMapper abstractUserMapper,
                                 UserRoleMapper userRoleMapper,
                                 AbstractRoleMapper abstractRoleMapper,
                                 UserRoleDomainService userRoleDomainService) {
        this.abstractUserMapper = abstractUserMapper;
        this.userRoleMapper = userRoleMapper;
        this.abstractRoleMapper = abstractRoleMapper;
        this.userRoleDomainService = userRoleDomainService;
    }

    @Override
    @Transactional
    public UserResp syncUser(Long tenantId, UserSyncReq req) {
        AbstractUser existing = abstractUserMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(ABSTRACT_USER.TENANT_ID.eq(tenantId))
                .and(ABSTRACT_USER.USER_TYPE.eq(req.userType()))
                .and(ABSTRACT_USER.EXTERNAL_ID.eq(req.externalId()))
                .and(ABSTRACT_USER.DELETE_FLAG.eq(0))
        );

        if (existing != null) {
            existing.setName(req.name() != null ? req.name() : existing.getName());
            existing.setEnabled(req.enabled() != null ? req.enabled() : existing.getEnabled());
            existing.setExtra(req.extra() != null ? req.extra() : existing.getExtra());
            existing.setUpdatedAt(LocalDateTime.now());
            abstractUserMapper.update(existing);
            return toUserResp(existing);
        }

        AbstractUser user = new AbstractUser();
        user.setTenantId(tenantId);
        user.setUserType(req.userType());
        user.setExternalId(req.externalId());
        user.setName(req.name());
        user.setEnabled(req.enabled() != null ? req.enabled() : true);
        user.setExtra(req.extra());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setDeleteFlag(0L);
        abstractUserMapper.insert(user);
        return toUserResp(user);
    }

    @Override
    public UserResp getUser(Long tenantId, Long userId) {
        AbstractUser user = abstractUserMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(ABSTRACT_USER.ID.eq(userId))
                .and(ABSTRACT_USER.TENANT_ID.eq(tenantId))
                .and(ABSTRACT_USER.DELETE_FLAG.eq(0))
        );
        return user != null ? toUserResp(user) : null;
    }

    @Override
    @Transactional
    public void deleteUser(Long tenantId, Long userId) {
        AbstractUser user = abstractUserMapper.selectOneById(userId);
        if (user != null && user.getDeleteFlag() == 0L && user.getTenantId().equals(tenantId)) {
            user.setDeleteFlag(user.getId());
            user.setDeletedAt(LocalDateTime.now());
            abstractUserMapper.update(user);
        }
    }

    @Override
    @Transactional
    public void setUserEnabled(Long tenantId, Long userId, boolean enabled) {
        AbstractUser user = abstractUserMapper.selectOneById(userId);
        if (user != null && user.getDeleteFlag() == 0L && user.getTenantId().equals(tenantId)) {
            user.setEnabled(enabled);
            user.setUpdatedAt(LocalDateTime.now());
            abstractUserMapper.update(user);
        }
    }

    @Override
    @Transactional
    public void assignRole(Long tenantId, UserAssignRoleReq req) {
        // Check duplicate
        Long existing = userRoleMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(USER_ROLE.ABSTRACT_USER_ID.eq(req.abstractUserId()))
                .and(USER_ROLE.TARGET_TYPE.eq(req.targetType()))
                .and(USER_ROLE.TARGET_ID.eq(req.targetId()))
                .and(USER_ROLE.DELETE_FLAG.eq(0))
        ) != null ? 1L : null;

        if (existing != null) {
            throw new IllegalArgumentException("Role already assigned to user");
        }

        UserRole ur = new UserRole();
        ur.setTenantId(tenantId);
        ur.setAbstractUserId(req.abstractUserId());
        ur.setTargetType(req.targetType());
        ur.setTargetId(req.targetId());
        ur.setRelationId(req.relationId());
        ur.setValidFrom(req.validFrom());
        ur.setValidTo(req.validTo());
        ur.setCreatedAt(LocalDateTime.now());
        ur.setUpdatedAt(LocalDateTime.now());
        ur.setDeleteFlag(0L);
        userRoleMapper.insert(ur);

        // Invalidate user role cache
        userRoleDomainService.invalidateRoleCache(tenantId, req.abstractUserId());
    }

    @Override
    @Transactional
    public void revokeRole(Long tenantId, Long userId, Long userRoleId) {
        UserRole ur = userRoleMapper.selectOneById(userRoleId);
        if (ur != null && ur.getDeleteFlag() == 0L && ur.getAbstractUserId().equals(userId)
            && ur.getTenantId().equals(tenantId)) {
            ur.setDeleteFlag(ur.getId());
            ur.setDeletedAt(LocalDateTime.now());
            userRoleMapper.update(ur);
            userRoleDomainService.invalidateRoleCache(tenantId, userId);
        }
    }

    @Override
    public UserRolesResp getUserRoles(Long tenantId, Long userId) {
        List<UserRole> userRoles = userRoleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(USER_ROLE.ABSTRACT_USER_ID.eq(userId))
                .and(USER_ROLE.TENANT_ID.eq(tenantId))
                .and(USER_ROLE.DELETE_FLAG.eq(0))
                .and(USER_ROLE.VALID_FROM.le(LocalDateTime.now()).or(USER_ROLE.VALID_FROM.isNull()))
                .and(USER_ROLE.VALID_TO.ge(LocalDateTime.now()).or(USER_ROLE.VALID_TO.isNull()))
        );

        List<RoleSummary> summaries = userRoles.stream()
            .map(ur -> {
                AbstractRole role = abstractRoleMapper.selectOneById(ur.getTargetId());
                return new RoleSummary(
                    ur.getTargetId(),
                    role != null ? role.getName() : null,
                    role != null ? role.getRoleType() : null,
                    ur.getTargetType(),
                    ur.getRelationId(),
                    ur.getValidFrom(),
                    ur.getValidTo()
                );
            })
            .collect(Collectors.toList());

        return new UserRolesResp(userId, summaries);
    }

    @Override
    public List<UserResp> listUsers(Long tenantId, int offset, int limit) {
        return abstractUserMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ABSTRACT_USER.TENANT_ID.eq(tenantId))
                .and(ABSTRACT_USER.DELETE_FLAG.eq(0))
                .limit(limit)
                .offset(offset)
        ).stream().map(this::toUserResp).collect(Collectors.toList());
    }

    private UserResp toUserResp(AbstractUser user) {
        return new UserResp(
            user.getId(), user.getTenantId(), user.getUserType(),
            user.getExternalId(), user.getName(), user.getEnabled(),
            user.getExtra(), user.getCreatedAt(), user.getUpdatedAt()
        );
    }
}
