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
import cn.ac.fage.accessmesh.permission.service.domain.AbstractUserDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.OperationLogDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionChangeDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.UserRoleDomainService;
import cn.ac.fage.accessmesh.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.permission.util.PermissionConstants;
import cn.ac.fage.accessmesh.permission.util.SqlUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.permission.entity.table.AbstractRoleTableDef.ABSTRACT_ROLE;
import static cn.ac.fage.accessmesh.permission.entity.table.AbstractUserTableDef.ABSTRACT_USER;
import static cn.ac.fage.accessmesh.permission.entity.table.UserRoleTableDef.USER_ROLE;

@Service
public class UserManageServiceImpl implements UserManageService {

    private static final Logger log = LoggerFactory.getLogger(UserManageServiceImpl.class);

    private final AbstractUserMapper abstractUserMapper;
    private final UserRoleMapper userRoleMapper;
    private final AbstractRoleMapper abstractRoleMapper;
    private final AbstractUserDomainService abstractUserDomainService;
    private final UserRoleDomainService userRoleDomainService;
    private final TypeResolutionService typeResolutionService;
    private final OperationLogDomainService operationLogDomainService;
    private final PermissionChangeDomainService permissionChangeDomainService;
    private final ObjectMapper objectMapper;
    private final AuthorizationService authorizationService;

    public UserManageServiceImpl(AbstractUserMapper abstractUserMapper,
                                 UserRoleMapper userRoleMapper,
                                 AbstractRoleMapper abstractRoleMapper,
                                 AbstractUserDomainService abstractUserDomainService,
                                 UserRoleDomainService userRoleDomainService,
                                 TypeResolutionService typeResolutionService,
                                 OperationLogDomainService operationLogDomainService,
                                 PermissionChangeDomainService permissionChangeDomainService,
                                 ObjectMapper objectMapper,
                                 AuthorizationService authorizationService) {
        this.abstractUserMapper = abstractUserMapper;
        this.userRoleMapper = userRoleMapper;
        this.abstractRoleMapper = abstractRoleMapper;
        this.abstractUserDomainService = abstractUserDomainService;
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

        AbstractUser existing = abstractUserDomainService.selectValidById(tenantId, req.userId());
        if (existing == null) {
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

        AbstractUser user = abstractUserDomainService.selectValidById(tenantId, userId);
        if (user == null) {
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
        abstractUserMapper.softDeleteBatch(tenantId, existingUserIds.stream().toList(), now);

        // Batch soft delete user_role associations
        List<Long> userRoleIds = userRoleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(USER_ROLE.TENANT_ID.eq(tenantId))
                .and(USER_ROLE.ABSTRACT_USER_ID.in(existingUserIds))
                .and(USER_ROLE.DELETE_FLAG.eq(0))
        ).stream().map(UserRole::getId).toList();
        if (!userRoleIds.isEmpty()) {
            userRoleMapper.softDeleteBatch(tenantId, userRoleIds, now);
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

        // Note: Type-level permission check removed - instance-level check is performed in assignRoleSingle
        // via authorizationService.canManageRole(tenantId, operatorId, targetRoleId)

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

        // Note: Type-level permission check removed - instance-level check is performed in assignRoleSingle
        // via authorizationService.canManageRole(tenantId, operatorId, targetRoleId)

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

        if (req.items() == null || req.items().isEmpty()) {
            return;
        }

        // ===== Batch resolution to avoid N+1 queries =====
        // 1. Collect all unique role external IDs and batch resolve
        Set<String> roleExternalIds = req.items().stream()
            .map(UserRoleBatchRevokeReq.RevokeItem::roleExternalId)
            .filter(id -> id != null && !id.isBlank())
            .collect(Collectors.toSet());
        // Group by roleTypeCode + domainCode for batch resolve
        Map<String, Set<String>> roleExternalIdsByTypeAndDomain = req.items().stream()
            .collect(Collectors.groupingBy(
                item -> item.roleTypeCode() + ":" + (item.domainCode() != null ? item.domainCode() : ""),
                Collectors.mapping(UserRoleBatchRevokeReq.RevokeItem::roleExternalId, Collectors.toSet())
            ));
        Map<String, Long> roleIdMap = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : roleExternalIdsByTypeAndDomain.entrySet()) {
            String[] parts = entry.getKey().split(":");
            String roleTypeCode = parts[0];
            String domainCode = parts.length > 1 && !parts[1].isEmpty() ? parts[1] : null;
            Map<String, Long> partialMap = typeResolutionService.batchResolveRoleIds(
                tenantId, roleTypeCode, entry.getValue(), domainCode);
            roleIdMap.putAll(partialMap.entrySet().stream()
                .collect(Collectors.toMap(
                    e -> roleTypeCode + ":" + (domainCode != null ? domainCode : "") + ":" + e.getKey(),
                    Map.Entry::getValue
                )));
        }

        // 2. Collect all unique user external IDs and batch resolve
        Map<String, Set<String>> userExternalIdsByType = req.items().stream()
            .collect(Collectors.groupingBy(
                UserRoleBatchRevokeReq.RevokeItem::subjectTypeCode,
                Collectors.mapping(UserRoleBatchRevokeReq.RevokeItem::subjectExternalId, Collectors.toSet())
            ));
        Map<String, Long> userIdMap = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : userExternalIdsByType.entrySet()) {
            Map<String, Long> partialMap = typeResolutionService.batchResolveUserIds(
                tenantId, entry.getKey(), entry.getValue());
            userIdMap.putAll(partialMap.entrySet().stream()
                .collect(Collectors.toMap(
                    e -> entry.getKey() + ":" + e.getKey(),
                    Map.Entry::getValue
                )));
        }

        // Collect all target role IDs for batch permission check
        Set<Long> targetRoleIds = new LinkedHashSet<>();
        for (UserRoleBatchRevokeReq.RevokeItem item : req.items()) {
            String roleKey = item.roleTypeCode() + ":" + (item.domainCode() != null ? item.domainCode() : "") + ":" + item.roleExternalId();
            Long targetRoleId = roleIdMap.get(roleKey);
            if (targetRoleId != null) {
                targetRoleIds.add(targetRoleId);
            }
        }

        // Batch permission check - avoid N+1 queries
        Map<Long, Boolean> permissionMap = authorizationService.canManageRoles(tenantId, operatorId, targetRoleIds);

        List<Long> affectedUserIds = new ArrayList<>();
        List<Long> affectedRoleIds = new ArrayList<>();
        ArrayNode itemsJson = objectMapper.createArrayNode();
        int revoked = 0;
        List<String> deniedItems = new ArrayList<>();

        for (UserRoleBatchRevokeReq.RevokeItem item : req.items()) {
            String userKey = item.subjectTypeCode() + ":" + item.subjectExternalId();
            Long abstractUserId = userIdMap.get(userKey);
            if (abstractUserId == null) {
                throw new IllegalArgumentException("User not found: " + item.subjectTypeCode() + "/" + item.subjectExternalId());
            }
            String roleKey = item.roleTypeCode() + ":" + (item.domainCode() != null ? item.domainCode() : "") + ":" + item.roleExternalId();
            Long targetRoleId = roleIdMap.get(roleKey);
            if (targetRoleId == null) {
                throw new IllegalArgumentException("Role not found: " + item.roleTypeCode() + "/" + item.roleExternalId());
            }

            // Check permission using pre-checked result
            if (!Boolean.TRUE.equals(permissionMap.get(targetRoleId))) {
                deniedItems.add(item.subjectTypeCode() + "/" + item.subjectExternalId() + " -> " + item.roleTypeCode() + "/" + item.roleExternalId());
                continue;  // Skip this item, no permission to revoke
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

        // Log denied items
        if (!deniedItems.isEmpty()) {
            log.info("Operator {} denied to revoke roles for items: {}", operatorId, deniedItems);
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
            "revoked " + revoked + " user-role relation(s), denied=" + deniedItems.size(),
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
                return queryWrapper.and(ABSTRACT_USER.ID.eq(PermissionConstants.NONEXISTENT_ID));
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
                return queryWrapper.and(ABSTRACT_USER.ID.eq(PermissionConstants.NONEXISTENT_ID));
            }
            List<Long> roleIds = abstractRoleMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                    .and(ABSTRACT_ROLE.DELETE_FLAG.eq(0))
                    .and(ABSTRACT_ROLE.BIZ_DOMAIN_ID.eq(domainId).or(ABSTRACT_ROLE.BIZ_DOMAIN_ID.isNull()))
            ).stream().map(AbstractRole::getId).toList();
            if (roleIds.isEmpty()) {
                return queryWrapper.and(ABSTRACT_USER.ID.eq(PermissionConstants.NONEXISTENT_ID));
            }
            List<Long> userIds = userRoleMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(USER_ROLE.TENANT_ID.eq(tenantId))
                    .and(USER_ROLE.DELETE_FLAG.eq(0))
                    .and(USER_ROLE.TARGET_ID.in(roleIds))
            ).stream().map(UserRole::getAbstractUserId).distinct().toList();
            if (userIds.isEmpty()) {
                return queryWrapper.and(ABSTRACT_USER.ID.eq(PermissionConstants.NONEXISTENT_ID));
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
