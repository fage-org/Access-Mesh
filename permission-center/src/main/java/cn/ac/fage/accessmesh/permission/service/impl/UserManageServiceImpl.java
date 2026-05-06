package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;
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
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import cn.ac.fage.accessmesh.permission.entity.table.AbstractRoleTableDef;
import cn.ac.fage.accessmesh.permission.entity.table.AbstractUserTableDef;
import cn.ac.fage.accessmesh.permission.entity.table.UserRoleTableDef;

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
    private final PermQueryEngine engine;

    // TODO: 构造函数依赖过多(11个)，违反单一职责原则
    // 建议：拆分为 UserSyncService/UserRoleAssignService/UserQueryService
    // 优先级：P2（非阻塞，建议在下次大版本重构时处理）
    public UserManageServiceImpl(AbstractUserMapper abstractUserMapper,
                                 UserRoleMapper userRoleMapper,
                                 AbstractRoleMapper abstractRoleMapper,
                                 AbstractUserDomainService abstractUserDomainService,
                                 UserRoleDomainService userRoleDomainService,
                                 TypeResolutionService typeResolutionService,
                                 OperationLogDomainService operationLogDomainService,
                                 PermissionChangeDomainService permissionChangeDomainService,
                                 ObjectMapper objectMapper,
                                 AuthorizationService authorizationService,
                                 PermQueryEngine engine) {
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
        this.engine = engine;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResp syncUser(Long tenantId, UserSyncReq req) {
        // Permission check - sync user requires USER_SYNC permission
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.USER, null, OperationCodeConstants.SYNC)) {
            throw new SecurityException("No permission to sync user");
        }

        Integer userType = typeResolutionService.resolveTypeValue(tenantId, "user_type", req.subjectTypeCode());
        if (userType == null) {
            throw new IllegalArgumentException("Unknown subjectTypeCode: " + req.subjectTypeCode());
        }
        AbstractUser existing = abstractUserMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(AbstractUserTableDef.ABSTRACT_USER.TENANT_ID.eq(tenantId))
                .and(AbstractUserTableDef.ABSTRACT_USER.USER_TYPE.eq(userType))
                .and(AbstractUserTableDef.ABSTRACT_USER.EXTERNAL_ID.eq(req.externalId()))
                .and(AbstractUserTableDef.ABSTRACT_USER.DELETE_FLAG.eq(0))
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
        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setDeleteFlag(0L);
        abstractUserMapper.insert(user);
        return toUserResp(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResp createUser(Long tenantId, UserCreateReq req) {
        Long operatorId = OperatorContext.getOperatorId();

        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.USER, null, OperationCodeConstants.CREATE)) {
            throw new SecurityException("No permission to create user");
        }

        Integer userType = typeResolutionService.resolveTypeValue(tenantId, "user_type", req.subjectTypeCode());
        if (userType == null) {
            throw new IllegalArgumentException("Unknown subjectTypeCode: " + req.subjectTypeCode());
        }
        AbstractUser existing = abstractUserMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(AbstractUserTableDef.ABSTRACT_USER.TENANT_ID.eq(tenantId))
                .and(AbstractUserTableDef.ABSTRACT_USER.USER_TYPE.eq(userType))
                .and(AbstractUserTableDef.ABSTRACT_USER.EXTERNAL_ID.eq(req.externalId()))
                .and(AbstractUserTableDef.ABSTRACT_USER.DELETE_FLAG.eq(0))
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
        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
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

        // Self-modification is always allowed, otherwise requires MANAGE permission on USER
        if (!operatorId.equals(req.userId())) {
            engine.validate(tenantId, operatorId, ResourceTypeCode.USER, null, OperationCodeConstants.MANAGE);
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
                .where(AbstractUserTableDef.ABSTRACT_USER.ID.eq(userId))
                .and(AbstractUserTableDef.ABSTRACT_USER.TENANT_ID.eq(tenantId))
                .and(AbstractUserTableDef.ABSTRACT_USER.DELETE_FLAG.eq(0))
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

        // Self-modification is always allowed, otherwise requires MANAGE permission on USER
        if (!operatorId.equals(userId)) {
            engine.validate(tenantId, operatorId, ResourceTypeCode.USER, null, OperationCodeConstants.MANAGE);
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
                .where(AbstractUserTableDef.ABSTRACT_USER.TENANT_ID.eq(tenantId))
                .and(AbstractUserTableDef.ABSTRACT_USER.ID.in(userIds))
                .and(AbstractUserTableDef.ABSTRACT_USER.DELETE_FLAG.eq(0))
        );

        if (users.isEmpty()) {
            return;
        }

        Set<Long> existingUserIds = users.stream().map(AbstractUser::getId).collect(Collectors.toSet());

        // Permission check: self-modification is allowed, otherwise check MANAGE permission
        // For batch, we only check non-self users
        Set<Long> nonSelfUserIds = existingUserIds.stream()
            .filter(id -> !operatorId.equals(id))
            .collect(Collectors.toSet());

        if (!nonSelfUserIds.isEmpty()) {
            Set<Long> deniedIds = engine.getDeniedIds(tenantId, operatorId, ResourceTypeCode.USER, nonSelfUserIds, OperationCodeConstants.MANAGE);
            if (!deniedIds.isEmpty()) {
                throw new SecurityException("No permission to delete users: " + deniedIds);
            }
        }

        // Batch soft delete users
        abstractUserMapper.softDeleteBatch(tenantId, existingUserIds.stream().toList(), now);

        // Batch soft delete user_role associations
        List<Long> userRoleIds = userRoleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(UserRoleTableDef.USER_ROLE.TENANT_ID.eq(tenantId))
                .and(UserRoleTableDef.USER_ROLE.ABSTRACT_USER_ID.in(existingUserIds))
                .and(UserRoleTableDef.USER_ROLE.DELETE_FLAG.eq(0))
        ).stream().map(UserRole::getId).toList();
        if (!userRoleIds.isEmpty()) {
            userRoleMapper.softDeleteBatch(tenantId, userRoleIds, now);
        }

        // Invalidate cache for affected users（事务提交后执行）
        final Set<Long> existingUserIdsForCache = existingUserIds;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (Long userId : existingUserIdsForCache) {
                        userRoleDomainService.invalidateRoleCache(tenantId, userId);
                    }
                }
            });
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

        if (req.items() == null || req.items().isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }

        // ===== Batch processing to avoid N+1 queries =====
        // 1. Group items by subjectTypeCode and roleTypeCode+domainCode for batch resolution
        Map<String, Set<String>> userExternalIdsByType = req.items().stream()
            .collect(Collectors.groupingBy(
                UserAssignRoleReq.AssignItem::subjectTypeCode,
                Collectors.mapping(UserAssignRoleReq.AssignItem::subjectExternalId, Collectors.toSet())
            ));
        Map<String, Set<String>> roleExternalIdsByTypeAndDomain = req.items().stream()
            .collect(Collectors.groupingBy(
                item -> item.roleTypeCode() + ":" + (item.domainCode() != null ? item.domainCode() : ""),
                Collectors.mapping(UserAssignRoleReq.AssignItem::roleExternalId, Collectors.toSet())
            ));

        // 2. Batch resolve user IDs
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

        // 3. Batch resolve role IDs
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

        // 4. Collect all target role IDs for batch permission check
        Set<Long> targetRoleIds = new LinkedHashSet<>();
        for (UserAssignRoleReq.AssignItem item : req.items()) {
            String roleKey = item.roleTypeCode() + ":" + (item.domainCode() != null ? item.domainCode() : "") + ":" + item.roleExternalId();
            Long targetRoleId = roleIdMap.get(roleKey);
            if (targetRoleId != null) {
                targetRoleIds.add(targetRoleId);
            }
        }

        // 5. Batch permission check - avoid N+1 queries
        Set<Long> deniedRoleIds = engine.getDeniedIds(tenantId, operatorId, ResourceTypeCode.ROLE, targetRoleIds, OperationCodeConstants.MANAGE);

        // 6. Batch query existing user_role relations to avoid N+1 query in loop
        Set<Long> allUserIds = new LinkedHashSet<>();
        for (UserAssignRoleReq.AssignItem item : req.items()) {
            String userKey = item.subjectTypeCode() + ":" + item.subjectExternalId();
            Long userId = userIdMap.get(userKey);
            if (userId != null) {
                allUserIds.add(userId);
            }
        }

        // Build key format for existing relation check: userId:roleId
        Map<String, UserRole> existingRelationMap = new HashMap<>();
        if (!allUserIds.isEmpty() && !targetRoleIds.isEmpty()) {
            List<UserRole> existingRelations = userRoleMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(UserRoleTableDef.USER_ROLE.TENANT_ID.eq(tenantId))
                    .and(UserRoleTableDef.USER_ROLE.ABSTRACT_USER_ID.in(allUserIds))
                    .and(UserRoleTableDef.USER_ROLE.TARGET_ID.in(targetRoleIds))
                    .and(UserRoleTableDef.USER_ROLE.TARGET_TYPE.eq(ResourceTypeCode.ROLE))
                    .and(UserRoleTableDef.USER_ROLE.DELETE_FLAG.eq(0))
            );
            for (UserRole ur : existingRelations) {
                String key = ur.getAbstractUserId() + ":" + ur.getTargetId();
                existingRelationMap.put(key, ur);
            }
        }

        // 7. Build list of relations to insert (skip existing and denied roles)
        List<UserRole> toInsert = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Set<Long> affectedUserIds = new LinkedHashSet<>();
        LocalDateTime now = LocalDateTime.now();

        for (UserAssignRoleReq.AssignItem item : req.items()) {
            String userKey = item.subjectTypeCode() + ":" + item.subjectExternalId();
            Long abstractUserId = userIdMap.get(userKey);
            if (abstractUserId == null) {
                errors.add("User not found: " + item.subjectTypeCode() + "/" + item.subjectExternalId());
                continue;
            }

            String roleKey = item.roleTypeCode() + ":" + (item.domainCode() != null ? item.domainCode() : "") + ":" + item.roleExternalId();
            Long targetRoleId = roleIdMap.get(roleKey);
            if (targetRoleId == null) {
                errors.add("Role not found: " + item.roleTypeCode() + "/" + item.roleExternalId());
                continue;
            }

            // Check permission using pre-checked result
            if (deniedRoleIds.contains(targetRoleId)) {
                errors.add("No permission to manage role: " + item.roleTypeCode() + "/" + item.roleExternalId());
                continue;
            }

            // Check if relation already exists
            String relationKey = abstractUserId + ":" + targetRoleId;
            if (existingRelationMap.containsKey(relationKey)) {
                // Already exists, skip
                continue;
            }

            // Create new relation
            UserRole ur = new UserRole();
            ur.setTenantId(tenantId);
            ur.setAbstractUserId(abstractUserId);
            ur.setTargetType(ResourceTypeCode.ROLE);
            ur.setTargetId(targetRoleId);
            ur.setRelationId(item.relationId());
            ur.setValidFrom(item.validFrom());
            ur.setValidTo(item.validTo());
            ur.setCreatedAt(now);
            ur.setUpdatedAt(now);
            ur.setDeleteFlag(0L);
            toInsert.add(ur);
            affectedUserIds.add(abstractUserId);
        }

        // 8. Throw error if any validation failed
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }

        // 9. Batch insert
        if (!toInsert.isEmpty()) {
            userRoleMapper.insertBatch(toInsert);
        }

        // 10. Invalidate cache for affected users (after transaction commit)
        if (!affectedUserIds.isEmpty() && TransactionSynchronizationManager.isSynchronizationActive()) {
            final Set<Long> userIdsForCache = affectedUserIds;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (Long uid : userIdsForCache) {
                        userRoleDomainService.invalidateRoleCache(tenantId, uid);
                    }
                }
            });
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRolesBatch(Long tenantId, UserRoleBatchAssignReq req) {
        if (req.subjectExternalIds() == null || req.subjectExternalIds().isEmpty()) {
            return;
        }

        Long operatorId = OperatorContext.getOperatorId();

        // ===== Batch processing to avoid N+1 queries =====
        // 1. Batch resolve user IDs (all users share the same subjectTypeCode)
        Map<String, Long> userIdMap = typeResolutionService.batchResolveUserIds(
            tenantId, req.subjectTypeCode(), new LinkedHashSet<>(req.subjectExternalIds()));
        Map<String, Long> fullUserIdMap = userIdMap.entrySet().stream()
            .collect(Collectors.toMap(
                e -> req.subjectTypeCode() + ":" + e.getKey(),
                Map.Entry::getValue
            ));

        // 2. Resolve the single target role ID
        Long targetRoleId = typeResolutionService.resolveRoleId(
            tenantId, req.roleTypeCode(), req.roleExternalId(), req.domainCode());
        if (targetRoleId == null) {
            throw new IllegalArgumentException("Role not found: " + req.roleTypeCode() + "/" + req.roleExternalId());
        }

        // 3. Batch permission check
        Set<Long> deniedRoleIds = engine.getDeniedIds(
            tenantId, operatorId, ResourceTypeCode.ROLE, Set.of(targetRoleId), OperationCodeConstants.MANAGE);
        if (deniedRoleIds.contains(targetRoleId)) {
            throw new SecurityException("No permission to manage role: " + req.roleTypeCode() + "/" + req.roleExternalId());
        }

        // 4. Collect all user IDs
        Set<Long> allUserIds = new LinkedHashSet<>();
        List<String> userNotFoundErrors = new ArrayList<>();
        for (String subjectExternalId : req.subjectExternalIds()) {
            String userKey = req.subjectTypeCode() + ":" + subjectExternalId;
            Long userId = fullUserIdMap.get(userKey);
            if (userId == null) {
                userNotFoundErrors.add("User not found: " + req.subjectTypeCode() + "/" + subjectExternalId);
            } else {
                allUserIds.add(userId);
            }
        }

        if (!userNotFoundErrors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", userNotFoundErrors));
        }

        // 5. Batch query existing user_role relations
        Map<String, UserRole> existingRelationMap = new HashMap<>();
        if (!allUserIds.isEmpty()) {
            List<UserRole> existingRelations = userRoleMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(UserRoleTableDef.USER_ROLE.TENANT_ID.eq(tenantId))
                    .and(UserRoleTableDef.USER_ROLE.ABSTRACT_USER_ID.in(allUserIds))
                    .and(UserRoleTableDef.USER_ROLE.TARGET_ID.eq(targetRoleId))
                    .and(UserRoleTableDef.USER_ROLE.TARGET_TYPE.eq(ResourceTypeCode.ROLE))
                    .and(UserRoleTableDef.USER_ROLE.DELETE_FLAG.eq(0))
            );
            for (UserRole ur : existingRelations) {
                String key = ur.getAbstractUserId() + ":" + ur.getTargetId();
                existingRelationMap.put(key, ur);
            }
        }

        // 6. Build list of relations to insert
        List<UserRole> toInsert = new ArrayList<>();
        Set<Long> affectedUserIds = new LinkedHashSet<>();
        LocalDateTime now = LocalDateTime.now();

        for (String subjectExternalId : req.subjectExternalIds()) {
            String userKey = req.subjectTypeCode() + ":" + subjectExternalId;
            Long abstractUserId = fullUserIdMap.get(userKey);
            if (abstractUserId == null) {
                continue; // Already validated above
            }

            // Check if relation already exists
            String relationKey = abstractUserId + ":" + targetRoleId;
            if (existingRelationMap.containsKey(relationKey)) {
                continue; // Already exists, skip
            }

            // Create new relation
            UserRole ur = new UserRole();
            ur.setTenantId(tenantId);
            ur.setAbstractUserId(abstractUserId);
            ur.setTargetType(ResourceTypeCode.ROLE);
            ur.setTargetId(targetRoleId);
            ur.setRelationId(req.relationId());
            ur.setValidFrom(null); // UserRoleBatchAssignReq doesn't have validFrom/validTo
            ur.setValidTo(null);
            ur.setCreatedAt(now);
            ur.setUpdatedAt(now);
            ur.setDeleteFlag(0L);
            toInsert.add(ur);
            affectedUserIds.add(abstractUserId);
        }

        // 7. Batch insert
        if (!toInsert.isEmpty()) {
            userRoleMapper.insertBatch(toInsert);
        }

        // 8. Invalidate cache for affected users (after transaction commit)
        if (!affectedUserIds.isEmpty() && TransactionSynchronizationManager.isSynchronizationActive()) {
            final Set<Long> userIdsForCache = affectedUserIds;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (Long uid : userIdsForCache) {
                        userRoleDomainService.invalidateRoleCache(tenantId, uid);
                    }
                }
            });
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
        Set<Long> deniedIds = engine.getDeniedIds(tenantId, operatorId, ResourceTypeCode.ROLE, targetRoleIds, OperationCodeConstants.MANAGE);

        // Batch load roles to avoid N+1 query in loop
        Map<Long, AbstractRole> roleMap = targetRoleIds.isEmpty() ? Map.of()
            : abstractRoleMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(AbstractRoleTableDef.ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                    .and(AbstractRoleTableDef.ABSTRACT_ROLE.ID.in(targetRoleIds))
                    .and(AbstractRoleTableDef.ABSTRACT_ROLE.DELETE_FLAG.eq(0))
            ).stream().collect(Collectors.toMap(AbstractRole::getId, r -> r));

        // ===== Batch query user_role relations to avoid N+1 query in loop =====
        // Collect all (userId, roleId) pairs for batch query
        Set<Long> allUserIds = new LinkedHashSet<>();
        Set<Long> allRoleIds = new LinkedHashSet<>();
        for (UserRoleBatchRevokeReq.RevokeItem item : req.items()) {
            String userKey = item.subjectTypeCode() + ":" + item.subjectExternalId();
            Long abstractUserId = userIdMap.get(userKey);
            String roleKey = item.roleTypeCode() + ":" + (item.domainCode() != null ? item.domainCode() : "") + ":" + item.roleExternalId();
            Long targetRoleId = roleIdMap.get(roleKey);
            if (abstractUserId != null && targetRoleId != null) {
                allUserIds.add(abstractUserId);
                allRoleIds.add(targetRoleId);
            }
        }

        // Batch query all relevant user_role relations
        Map<String, UserRole> userRoleMap = new HashMap<>();
        if (!allUserIds.isEmpty() && !allRoleIds.isEmpty()) {
            List<UserRole> userRoles = userRoleMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(UserRoleTableDef.USER_ROLE.TENANT_ID.eq(tenantId))
                    .and(UserRoleTableDef.USER_ROLE.ABSTRACT_USER_ID.in(allUserIds))
                    .and(UserRoleTableDef.USER_ROLE.TARGET_TYPE.eq(ResourceTypeCode.ROLE))
                    .and(UserRoleTableDef.USER_ROLE.TARGET_ID.in(allRoleIds))
                    .and(UserRoleTableDef.USER_ROLE.DELETE_FLAG.eq(0))
            );
            for (UserRole ur : userRoles) {
                // Key format: userId:roleId:relationId (use "null" for null relationId)
                String key = ur.getAbstractUserId() + ":" + ur.getTargetId() + ":" + (ur.getRelationId() != null ? ur.getRelationId() : "null");
                userRoleMap.put(key, ur);
            }
        }

        List<Long> affectedUserIds = new ArrayList<>();
        List<Long> affectedRoleIds = new ArrayList<>();
        ArrayNode itemsJson = objectMapper.createArrayNode();
        int revoked = 0;
        List<String> deniedItems = new ArrayList<>();
        // Performance fix: collect IDs for batch soft delete
        List<Long> idsToDelete = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

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
            if (deniedIds.contains(targetRoleId)) {
                deniedItems.add(item.subjectTypeCode() + "/" + item.subjectExternalId() + " -> " + item.roleTypeCode() + "/" + item.roleExternalId());
                continue;  // Skip this item, no permission to revoke
            }

            // Look up user_role from pre-loaded map
            String urKey = abstractUserId + ":" + targetRoleId + ":" + (item.relationId() != null ? item.relationId() : "null");
            UserRole ur = userRoleMap.get(urKey);
            if (ur == null) {
                throw new IllegalArgumentException("User-role relation not found for item");
            }
            idsToDelete.add(ur.getId());
            revoked++;
            affectedUserIds.add(abstractUserId);
            affectedRoleIds.add(targetRoleId);
            AbstractRole role = roleMap.get(targetRoleId);
            ObjectNode it = objectMapper.createObjectNode();
            it.put("changeType", "REMOVE");
            ObjectNode roleNode = it.putObject("role");
            roleNode.put("roleTypeCode", item.roleTypeCode());
            roleNode.put("roleExternalId", item.roleExternalId());
            roleNode.put("roleName", role != null ? role.getName() : "");
            itemsJson.add(it);
        }
        // Performance fix: batch soft delete instead of loop updates
        if (!idsToDelete.isEmpty()) {
            userRoleMapper.softDeleteBatch(tenantId, idsToDelete, now);
        }

        // Log denied items
        if (!deniedItems.isEmpty()) {
            log.info("Operator {} denied to revoke roles for items: {}", operatorId, deniedItems);
        }

        Set<Long> uniqueUsers = new LinkedHashSet<>(affectedUserIds);
        // 缓存失效（事务提交后执行）
        final Set<Long> uniqueUsersForCache = uniqueUsers;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (Long uid : uniqueUsersForCache) {
                        userRoleDomainService.invalidateRoleCache(tenantId, uid);
                    }
                }
            });
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
            new PermissionChangeDomainService.ChangeLogContext(tenantId, null, operatorId, null, PermConstants.MaintainSource.MANUAL, "user-role-revoke"),
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
        LocalDateTime now = LocalDateTime.now();
        List<UserRole> userRoles = userRoleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(UserRoleTableDef.USER_ROLE.ABSTRACT_USER_ID.eq(userId))
                .and(UserRoleTableDef.USER_ROLE.TENANT_ID.eq(tenantId))
                .and(UserRoleTableDef.USER_ROLE.DELETE_FLAG.eq(0))
                .and(UserRoleTableDef.USER_ROLE.VALID_FROM.le(now).or(UserRoleTableDef.USER_ROLE.VALID_FROM.isNull()))
                .and(UserRoleTableDef.USER_ROLE.VALID_TO.ge(now).or(UserRoleTableDef.USER_ROLE.VALID_TO.isNull()))
        );

        // 批量预加载角色，避免 N+1 查询
        List<RoleSummary> summaries;
        if (userRoles.isEmpty()) {
            summaries = List.of();
        } else {
            Set<Long> targetIds = userRoles.stream()
                .map(UserRole::getTargetId)
                .collect(Collectors.toSet());

            List<AbstractRole> roles = abstractRoleMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(AbstractRoleTableDef.ABSTRACT_ROLE.ID.in(targetIds))
                    .and(AbstractRoleTableDef.ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                    .and(AbstractRoleTableDef.ABSTRACT_ROLE.DELETE_FLAG.eq(0))
            );

            Map<Long, AbstractRole> roleMap = roles.stream()
                .collect(Collectors.toMap(AbstractRole::getId, r -> r));

            summaries = userRoles.stream()
                .map(ur -> {
                    AbstractRole role = roleMap.get(ur.getTargetId());
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
        }

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
            .where(AbstractUserTableDef.ABSTRACT_USER.TENANT_ID.eq(tenantId))
            .and(AbstractUserTableDef.ABSTRACT_USER.DELETE_FLAG.eq(0));
        if (subjectTypeCode != null && !subjectTypeCode.isBlank()) {
            Integer userType = typeResolutionService.resolveTypeValue(tenantId, "user_type", subjectTypeCode);
            if (userType == null) {
                return queryWrapper.and(AbstractUserTableDef.ABSTRACT_USER.ID.eq(PermissionConstants.NONEXISTENT_ID));
            }
            queryWrapper.and(AbstractUserTableDef.ABSTRACT_USER.USER_TYPE.eq(userType));
        }
        if (keyword != null && !keyword.isBlank()) {
            String pattern = SqlUtil.likePattern(keyword);
            queryWrapper.and(
                AbstractUserTableDef.ABSTRACT_USER.NAME.like(pattern)
                    .or(AbstractUserTableDef.ABSTRACT_USER.EXTERNAL_ID.like(pattern))
            );
        }
        if (domainCode != null && !domainCode.isBlank()) {
            Long domainId = typeResolutionService.resolveDomainId(tenantId, domainCode);
            if (domainId == null) {
                return queryWrapper.and(AbstractUserTableDef.ABSTRACT_USER.ID.eq(PermissionConstants.NONEXISTENT_ID));
            }
            List<Long> roleIds = abstractRoleMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(AbstractRoleTableDef.ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                    .and(AbstractRoleTableDef.ABSTRACT_ROLE.DELETE_FLAG.eq(0))
                    .and(AbstractRoleTableDef.ABSTRACT_ROLE.BIZ_DOMAIN_ID.eq(domainId).or(AbstractRoleTableDef.ABSTRACT_ROLE.BIZ_DOMAIN_ID.isNull()))
            ).stream().map(AbstractRole::getId).toList();
            if (roleIds.isEmpty()) {
                return queryWrapper.and(AbstractUserTableDef.ABSTRACT_USER.ID.eq(PermissionConstants.NONEXISTENT_ID));
            }
            List<Long> userIds = userRoleMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(UserRoleTableDef.USER_ROLE.TENANT_ID.eq(tenantId))
                    .and(UserRoleTableDef.USER_ROLE.DELETE_FLAG.eq(0))
                    .and(UserRoleTableDef.USER_ROLE.TARGET_ID.in(roleIds))
            ).stream().map(UserRole::getAbstractUserId).distinct().toList();
            if (userIds.isEmpty()) {
                return queryWrapper.and(AbstractUserTableDef.ABSTRACT_USER.ID.eq(PermissionConstants.NONEXISTENT_ID));
            }
            queryWrapper.and(AbstractUserTableDef.ABSTRACT_USER.ID.in(userIds));
        }
        return queryWrapper;
    }

    private UserResp toUserResp(AbstractUser user) {
        return new UserResp(
            user.getId(), user.getTenantId(), typeResolutionService.resolveTypeCode(user.getTenantId(), "user_type", user.getUserType()),
            user.getExternalId(), user.getName(), user.getEnabled(),
            user.getExtra(), user.getCreatedAt(), user.getUpdatedAt()
        );
    }
}
