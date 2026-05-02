package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.req.UserAssignRoleReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserSyncReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserRoleBatchAssignReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserRoleBatchRevokeReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserRoleListReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.UserResp;
import cn.ac.fage.accessmesh.permission.dto.resp.UserRolesResp;
import cn.ac.fage.accessmesh.permission.dto.resp.UserRolesResp.RoleSummary;
import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.entity.AbstractUser;
import cn.ac.fage.accessmesh.permission.entity.UserRole;
import cn.ac.fage.accessmesh.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.permission.mapper.UserRoleMapper;
import cn.ac.fage.accessmesh.permission.service.AuthorizationService;
import cn.ac.fage.accessmesh.permission.service.UserManageService;
import cn.ac.fage.accessmesh.permission.service.domain.OperationLogDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionChangeDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.UserRoleDomainService;
import cn.ac.fage.accessmesh.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.permission.util.SqlUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.permission.entity.table.AbstractRoleTableDef.ABSTRACT_ROLE;
import static cn.ac.fage.accessmesh.permission.entity.table.AbstractUserTableDef.ABSTRACT_USER;
import static cn.ac.fage.accessmesh.permission.entity.table.AbstractRoleTableDef.ABSTRACT_ROLE;
import static cn.ac.fage.accessmesh.permission.entity.table.UserRoleTableDef.USER_ROLE;

@Service
public class UserManageServiceImpl implements UserManageService {

    private final AbstractUserMapper abstractUserMapper;
    private final UserRoleMapper userRoleMapper;
    private final AbstractRoleMapper abstractRoleMapper;
    private final UserRoleDomainService userRoleDomainService;
    private final TypeResolutionService typeResolutionService;
    private final OperationLogDomainService operationLogDomainService;
    private final PermissionChangeDomainService permissionChangeDomainService;
    private final ObjectMapper objectMapper;
    private final AuthorizationService authorizationService;

    public UserManageServiceImpl(AbstractUserMapper abstractUserMapper,
                                 UserRoleMapper userRoleMapper,
                                 AbstractRoleMapper abstractRoleMapper,
                                 UserRoleDomainService userRoleDomainService,
                                 TypeResolutionService typeResolutionService,
                                 OperationLogDomainService operationLogDomainService,
                                 PermissionChangeDomainService permissionChangeDomainService,
                                 ObjectMapper objectMapper,
                                 AuthorizationService authorizationService) {
        this.abstractUserMapper = abstractUserMapper;
        this.userRoleMapper = userRoleMapper;
        this.abstractRoleMapper = abstractRoleMapper;
        this.userRoleDomainService = userRoleDomainService;
        this.typeResolutionService = typeResolutionService;
        this.operationLogDomainService = operationLogDomainService;
        this.permissionChangeDomainService = permissionChangeDomainService;
        this.objectMapper = objectMapper;
        this.authorizationService = authorizationService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResp syncUser(Long tenantId, UserSyncReq req) {
        // Permission check - sync user requires USER_SYNC permission
        Long operatorId = OperatorContext.getOperatorId();
        if (!authorizationService.hasPermission(tenantId, operatorId, "USER", "SYNC")) {
            throw new SecurityException("No permission to sync user");
        }

        Integer userType = typeResolutionService.resolveTypeValue(tenantId, "user_type", req.subjectTypeCode());
        if (userType == null) {
            throw new IllegalArgumentException("Unknown subjectTypeCode: " + req.subjectTypeCode());
        }
        AbstractUser existing = abstractUserMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(ABSTRACT_USER.TENANT_ID.eq(tenantId))
                .and(ABSTRACT_USER.USER_TYPE.eq(userType))
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
        user.setUserType(userType);
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
    @Transactional(rollbackFor = Exception.class)
    public UserResp createUser(Long tenantId, UserCreateReq req) {
        Long operatorId = OperatorContext.getOperatorId();

        if (!authorizationService.hasPermission(tenantId, operatorId, "USER", "CREATE")) {
            throw new SecurityException("No permission to create user");
        }

        Integer userType = typeResolutionService.resolveTypeValue(tenantId, "user_type", req.subjectTypeCode());
        if (userType == null) {
            throw new IllegalArgumentException("Unknown subjectTypeCode: " + req.subjectTypeCode());
        }
        AbstractUser existing = abstractUserMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(ABSTRACT_USER.TENANT_ID.eq(tenantId))
                .and(ABSTRACT_USER.USER_TYPE.eq(userType))
                .and(ABSTRACT_USER.EXTERNAL_ID.eq(req.externalId()))
                .and(ABSTRACT_USER.DELETE_FLAG.eq(0))
        );
        if (existing != null) {
            throw new IllegalArgumentException("User already exists");
        }
        AbstractUser user = new AbstractUser();
        user.setTenantId(tenantId);
        user.setUserType(userType);
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
    @Transactional(rollbackFor = Exception.class)
    public UserResp updateUser(Long tenantId, UserUpdateReq req) {
        Long operatorId = OperatorContext.getOperatorId();

        AbstractUser existing = abstractUserMapper.selectOneById(req.userId());
        if (existing == null || existing.getDeleteFlag() != 0L || !tenantId.equals(existing.getTenantId())) {
            throw new IllegalArgumentException("User not found: " + req.userId());
        }

        if (!authorizationService.canManageUser(tenantId, operatorId, req.userId())) {
            throw new SecurityException("No permission to update user: " + req.userId());
        }

        if (req.name() != null) {
            existing.setName(req.name());
        }
        if (req.enabled() != null) {
            existing.setEnabled(req.enabled());
        }
        if (req.extra() != null) {
            existing.setExtra(req.extra());
        }
        existing.setUpdatedAt(LocalDateTime.now());
        abstractUserMapper.update(existing);
        return toUserResp(existing);
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
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long tenantId, Long userId) {
        Long operatorId = OperatorContext.getOperatorId();

        AbstractUser user = abstractUserMapper.selectOneById(userId);
        if (user == null || user.getDeleteFlag() != 0L || !user.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("User not found: " + userId);
        }

        if (!authorizationService.canManageUser(tenantId, operatorId, userId)) {
            throw new SecurityException("No permission to delete user: " + userId);
        }

        user.setDeleteFlag(user.getId());
        user.setDeletedAt(LocalDateTime.now());
        abstractUserMapper.update(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUsers(Long tenantId, List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        Long operatorId = OperatorContext.getOperatorId();
        LocalDateTime now = LocalDateTime.now();

        // Batch query users to validate existence
        List<AbstractUser> users = abstractUserMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ABSTRACT_USER.TENANT_ID.eq(tenantId))
                .and(ABSTRACT_USER.ID.in(userIds))
                .and(ABSTRACT_USER.DELETE_FLAG.eq(0))
        );

        if (users.isEmpty()) {
            return;
        }

        // Permission check for each user (self-modification is allowed)
        Set<Long> existingUserIds = users.stream().map(AbstractUser::getId).collect(Collectors.toSet());
        for (Long userId : existingUserIds) {
            if (!authorizationService.canManageUser(tenantId, operatorId, userId)) {
                throw new SecurityException("No permission to delete user: " + userId);
            }
        }

        // Batch soft delete users
        for (AbstractUser user : users) {
            user.setDeleteFlag(user.getId());
            user.setDeletedAt(now);
            abstractUserMapper.update(user);
        }

        // Batch soft delete user_role associations
        List<UserRole> userRoles = userRoleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(USER_ROLE.TENANT_ID.eq(tenantId))
                .and(USER_ROLE.ABSTRACT_USER_ID.in(existingUserIds))
                .and(USER_ROLE.DELETE_FLAG.eq(0))
        );
        for (UserRole ur : userRoles) {
            ur.setDeleteFlag(ur.getId());
            ur.setDeletedAt(now);
            userRoleMapper.update(ur);
        }

        // Invalidate cache for affected users
        for (Long userId : existingUserIds) {
            userRoleDomainService.invalidateRoleCache(tenantId, userId);
        }

        // Single operation log
        operationLogDomainService.asyncRecord(
            "user", "BATCH_DELETE",
            "abstract_user", null,
            "Deleted " + users.size() + " users",
            operatorId, null, null, tenantId
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRole(Long tenantId, UserAssignRoleReq req) {
        Long operatorId = OperatorContext.getOperatorId();

        if (!authorizationService.hasPermission(tenantId, operatorId, "ROLE", "MANAGE")) {
            throw new SecurityException("No permission to assign roles");
        }

        if (req.items() == null || req.items().isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }
        for (UserAssignRoleReq.AssignItem item : req.items()) {
            assignRoleSingle(tenantId, operatorId, item);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRolesBatch(Long tenantId, UserRoleBatchAssignReq req) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!authorizationService.hasPermission(tenantId, operatorId, "ROLE", "MANAGE")) {
            throw new SecurityException("No permission to assign roles batch");
        }

        for (String subjectExternalId : req.subjectExternalIds()) {
            assignRoleSingle(tenantId, operatorId, new UserAssignRoleReq.AssignItem(
                req.subjectTypeCode(),
                subjectExternalId,
                req.domainCode(),
                req.roleTypeCode(),
                req.roleExternalId(),
                req.relationId(),
                null,
                null
            ));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeRolesBatch(Long tenantId, UserRoleBatchRevokeReq req) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!authorizationService.hasPermission(tenantId, operatorId, "ROLE", "MANAGE")) {
            throw new SecurityException("No permission to revoke roles");
        }

        List<Long> affectedUserIds = new ArrayList<>();
        List<Long> affectedRoleIds = new ArrayList<>();
        ArrayNode itemsJson = objectMapper.createArrayNode();
        int revoked = 0;
        for (UserRoleBatchRevokeReq.RevokeItem item : req.items()) {
            Long abstractUserId = typeResolutionService.resolveUserId(tenantId, item.subjectTypeCode(), item.subjectExternalId());
            if (abstractUserId == null) {
                throw new IllegalArgumentException("User not found: " + item.subjectTypeCode() + "/" + item.subjectExternalId());
            }
            Long targetRoleId = typeResolutionService.resolveRoleId(tenantId, item.roleTypeCode(), item.roleExternalId(), item.domainCode());
            if (targetRoleId == null) {
                throw new IllegalArgumentException("Role not found: " + item.roleTypeCode() + "/" + item.roleExternalId());
            }
            QueryWrapper qw = QueryWrapper.create()
                .where(USER_ROLE.TENANT_ID.eq(tenantId))
                .and(USER_ROLE.ABSTRACT_USER_ID.eq(abstractUserId))
                .and(USER_ROLE.TARGET_TYPE.eq("ROLE"))
                .and(USER_ROLE.TARGET_ID.eq(targetRoleId))
                .and(USER_ROLE.DELETE_FLAG.eq(0));
            if (item.relationId() == null) {
                qw.and(USER_ROLE.RELATION_ID.isNull());
            } else {
                qw.and(USER_ROLE.RELATION_ID.eq(item.relationId()));
            }
            UserRole ur = userRoleMapper.selectOneByQuery(qw);
            if (ur == null) {
                throw new IllegalArgumentException("User-role relation not found for item");
            }
            ur.setDeleteFlag(ur.getId());
            ur.setDeletedAt(LocalDateTime.now());
            userRoleMapper.update(ur);
            revoked++;
            affectedUserIds.add(abstractUserId);
            affectedRoleIds.add(targetRoleId);
            AbstractRole role = abstractRoleMapper.selectOneById(targetRoleId);
            ObjectNode it = objectMapper.createObjectNode();
            it.put("changeType", "REMOVE");
            ObjectNode roleNode = it.putObject("role");
            roleNode.put("roleTypeCode", item.roleTypeCode());
            roleNode.put("roleExternalId", item.roleExternalId());
            roleNode.put("roleName", role != null ? role.getName() : "");
            itemsJson.add(it);
        }
        Set<Long> uniqueUsers = new LinkedHashSet<>(affectedUserIds);
        for (Long uid : uniqueUsers) {
            userRoleDomainService.invalidateRoleCache(tenantId, uid);
        }
        ObjectNode diffRoot = objectMapper.createObjectNode();
        diffRoot.put("eventType", "USER_ROLE_CHANGE");
        diffRoot.set("items", itemsJson);
        String diffSnapshot;
        try {
            diffSnapshot = objectMapper.writeValueAsString(diffRoot);
        } catch (Exception e) {
            diffSnapshot = "{}";
        }
        Long[] userArr = uniqueUsers.toArray(Long[]::new);
        Set<Long> uniqueRoles = new LinkedHashSet<>(affectedRoleIds);
        Long[] roleArr = uniqueRoles.toArray(Long[]::new);
        permissionChangeDomainService.record(
            new PermissionChangeDomainService.ChangeLogContext(tenantId, null, operatorId, null, "MANUAL", "user-role-revoke"),
            List.of(new PermissionChangeDomainService.ChangeLogEntry(
                "user_role",
                0L,
                "BATCH_REMOVE",
                null,
                null,
                diffSnapshot,
                userArr,
                roleArr
            ))
        );
        operationLogDomainService.asyncRecord(
            "perm",
            "user-role-revoke",
            "BATCH",
            tenantId,
            "revoked " + revoked + " user-role relation(s)",
            null,
            null,
            null,
            tenantId
        );
    }

    @Override
    public UserRolesResp getUserRoles(Long tenantId, UserRoleListReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) {
            return new UserRolesResp(req.subjectTypeCode(), req.subjectExternalId(), List.of());
        }
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
                    role != null ? role.getExternalId() : null,
                    role != null ? role.getName() : null,
                    role != null ? typeResolutionService.resolveTypeCode(tenantId, "role_type", role.getRoleType()) : null,
                    ur.getTargetType(),
                    ur.getRelationId(),
                    ur.getValidFrom(),
                    ur.getValidTo()
                );
            })
            .collect(Collectors.toList());

        return new UserRolesResp(req.subjectTypeCode(), req.subjectExternalId(), summaries);
    }

    @Override
    public List<UserResp> listUsers(Long tenantId, String subjectTypeCode, String domainCode, String keyword, int offset, int limit) {
        QueryWrapper queryWrapper = buildUserListQuery(tenantId, subjectTypeCode, domainCode, keyword)
            .limit(limit)
            .offset(offset);
        return abstractUserMapper.selectListByQuery(queryWrapper)
            .stream().map(this::toUserResp).collect(Collectors.toList());
    }

    @Override
    public long countUsers(Long tenantId, String subjectTypeCode, String domainCode, String keyword) {
        return abstractUserMapper.selectCountByQuery(
            buildUserListQuery(tenantId, subjectTypeCode, domainCode, keyword)
        );
    }

    private QueryWrapper buildUserListQuery(Long tenantId, String subjectTypeCode, String domainCode, String keyword) {
        QueryWrapper queryWrapper = QueryWrapper.create()
            .where(ABSTRACT_USER.TENANT_ID.eq(tenantId))
            .and(ABSTRACT_USER.DELETE_FLAG.eq(0));
        if (subjectTypeCode != null && !subjectTypeCode.isBlank()) {
            Integer userType = typeResolutionService.resolveTypeValue(tenantId, "user_type", subjectTypeCode);
            if (userType == null) {
                return queryWrapper.and(ABSTRACT_USER.ID.eq(-1L));
            }
            queryWrapper.and(ABSTRACT_USER.USER_TYPE.eq(userType));
        }
        if (keyword != null && !keyword.isBlank()) {
            String pattern = SqlUtil.likePattern(keyword);
            queryWrapper.and(
                ABSTRACT_USER.NAME.like(pattern)
                    .or(ABSTRACT_USER.EXTERNAL_ID.like(pattern))
            );
        }
        if (domainCode != null && !domainCode.isBlank()) {
            Long domainId = typeResolutionService.resolveDomainId(tenantId, domainCode);
            if (domainId == null) {
                return queryWrapper.and(ABSTRACT_USER.ID.eq(-1L));
            }
            List<Long> roleIds = abstractRoleMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                    .and(ABSTRACT_ROLE.DELETE_FLAG.eq(0))
                    .and(ABSTRACT_ROLE.BIZ_DOMAIN_ID.eq(domainId).or(ABSTRACT_ROLE.BIZ_DOMAIN_ID.isNull()))
            ).stream().map(AbstractRole::getId).toList();
            if (roleIds.isEmpty()) {
                return queryWrapper.and(ABSTRACT_USER.ID.eq(-1L));
            }
            List<Long> userIds = userRoleMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(USER_ROLE.TENANT_ID.eq(tenantId))
                    .and(USER_ROLE.DELETE_FLAG.eq(0))
                    .and(USER_ROLE.TARGET_ID.in(roleIds))
            ).stream().map(UserRole::getAbstractUserId).distinct().toList();
            if (userIds.isEmpty()) {
                return queryWrapper.and(ABSTRACT_USER.ID.eq(-1L));
            }
            queryWrapper.and(ABSTRACT_USER.ID.in(userIds));
        }
        return queryWrapper;
    }

    private void assignRoleSingle(Long tenantId, Long operatorId, UserAssignRoleReq.AssignItem req) {
        Long abstractUserId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (abstractUserId == null) {
            throw new IllegalArgumentException("User not found by business key");
        }
        Long targetRoleId = typeResolutionService.resolveRoleId(tenantId, req.roleTypeCode(), req.roleExternalId(), req.domainCode());
        if (targetRoleId == null) {
            throw new IllegalArgumentException("Role not found by business key");
        }

        // Check if operator has permission to manage the target role
        if (!authorizationService.canManageRole(tenantId, operatorId, targetRoleId)) {
            throw new SecurityException("No permission to assign role: " + targetRoleId);
        }

        Long existing = userRoleMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(USER_ROLE.ABSTRACT_USER_ID.eq(abstractUserId))
                .and(USER_ROLE.TARGET_TYPE.eq("ROLE"))
                .and(USER_ROLE.TARGET_ID.eq(targetRoleId))
                .and(USER_ROLE.DELETE_FLAG.eq(0))
        ) != null ? 1L : null;
        if (existing != null) {
            throw new IllegalArgumentException("Role already assigned to user");
        }
        UserRole ur = new UserRole();
        ur.setTenantId(tenantId);
        ur.setAbstractUserId(abstractUserId);
        ur.setTargetType("ROLE");
        ur.setTargetId(targetRoleId);
        ur.setRelationId(req.relationId());
        ur.setValidFrom(req.validFrom());
        ur.setValidTo(req.validTo());
        ur.setCreatedAt(LocalDateTime.now());
        ur.setUpdatedAt(LocalDateTime.now());
        ur.setDeleteFlag(0L);
        userRoleMapper.insert(ur);
        userRoleDomainService.invalidateRoleCache(tenantId, abstractUserId);
    }

    private UserResp toUserResp(AbstractUser user) {
        return new UserResp(
            user.getId(), user.getTenantId(), typeResolutionService.resolveTypeCode(user.getTenantId(), "user_type", user.getUserType()),
            user.getExternalId(), user.getName(), user.getEnabled(),
            user.getExtra(), user.getCreatedAt(), user.getUpdatedAt()
        );
    }
}
