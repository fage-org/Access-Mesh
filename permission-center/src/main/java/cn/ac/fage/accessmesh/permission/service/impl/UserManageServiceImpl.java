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
import cn.ac.fage.accessmesh.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.permission.service.domain.UserRoleDomainService;
import cn.ac.fage.accessmesh.permission.enums.DomainQueryMode;
import cn.ac.fage.accessmesh.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.permission.util.PermissionConstants;
import cn.ac.fage.accessmesh.permission.util.SqlUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

/**
 * 用户管理服务实现类
 * <p>
 * 提供用户同步、创建、更新、删除、角色分配、角色撤销等功能。
 * 所有操作均通过PermQueryEngine进行权限校验，确保操作安全。
 * 批量操作采用批量查询和批量插入策略，避免N+1查询问题。
 * 缓存失效操作在事务提交后执行，防止缓存被回滚数据污染。
 * </p>
 */
@Service
public class UserManageServiceImpl implements UserManageService {

    private static final Logger log = LoggerFactory.getLogger(UserManageServiceImpl.class);

    private final AbstractUserMapper abstractUserMapper;
    private final UserRoleMapper userRoleMapper;
    private final AbstractRoleMapper abstractRoleMapper;
    private final AbstractUserDomainService abstractUserDomainService;
    private final UserRoleDomainService userRoleDomainService;
    private final TypeResolutionService typeResolutionService;
    private final DomainClassifyService domainClassifyService;
    private final OperationLogDomainService operationLogDomainService;
    private final PermissionChangeDomainService permissionChangeDomainService;
    private final ObjectMapper objectMapper;
    private final AuthorizationService authorizationService;
    private final PermQueryEngine engine;

    public UserManageServiceImpl(AbstractUserMapper abstractUserMapper,
                                 UserRoleMapper userRoleMapper,
                                 AbstractRoleMapper abstractRoleMapper,
                                 AbstractUserDomainService abstractUserDomainService,
                                 UserRoleDomainService userRoleDomainService,
                                 TypeResolutionService typeResolutionService,
                                 DomainClassifyService domainClassifyService,
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
        this.domainClassifyService = domainClassifyService;
        this.operationLogDomainService = operationLogDomainService;
        this.permissionChangeDomainService = permissionChangeDomainService;
        this.objectMapper = objectMapper;
        this.authorizationService = authorizationService;
        this.engine = engine;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResp syncUser(Long tenantId, UserSyncReq req) {
        Long operatorId = OperatorContext.getOperatorId();
        if (!engine.hasPermission(tenantId, operatorId, ResourceTypeCode.USER, null, OperationCodeConstants.SYNC)) {
            throw new SecurityException("No permission to sync user");
        }

        Integer userType = typeResolutionService.resolveTypeValue(tenantId, "user_type", req.subjectTypeCode());
        if (userType == null) {
            throw new IllegalArgumentException("Unknown subjectTypeCode: " + req.subjectTypeCode());
        }
        AbstractUser existing = abstractUserMapper.selectByTypeAndExternalId(tenantId, userType, req.externalId());

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
        AbstractUser existing = abstractUserMapper.selectByTypeAndExternalId(tenantId, userType, req.externalId());
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
        AbstractUser user = abstractUserMapper.selectValidById(userId, tenantId);
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

        Set<Long> userIdsSet = new LinkedHashSet<>(userIds);
        List<AbstractUser> users = abstractUserMapper.selectValidByIds(tenantId, userIdsSet);

        if (users.isEmpty()) {
            return;
        }

        Set<Long> existingUserIds = users.stream().map(AbstractUser::getId).collect(Collectors.toSet());

        Set<Long> nonSelfUserIds = existingUserIds.stream()
            .filter(id -> !operatorId.equals(id))
            .collect(Collectors.toSet());

        if (!nonSelfUserIds.isEmpty()) {
            Set<Long> deniedIds = engine.getDeniedIds(tenantId, operatorId, ResourceTypeCode.USER, nonSelfUserIds, OperationCodeConstants.MANAGE);
            if (!deniedIds.isEmpty()) {
                throw new SecurityException("No permission to delete users: " + deniedIds);
            }
        }

        abstractUserMapper.softDeleteBatch(tenantId, existingUserIds.stream().toList(), now);

        List<UserRole> userRoles = userRoleMapper.selectValidByUserIds(tenantId, existingUserIds);
        List<Long> userRoleIds = userRoles.stream().map(UserRole::getId).toList();
        if (!userRoleIds.isEmpty()) {
            userRoleMapper.softDeleteBatch(tenantId, userRoleIds, now);
        }

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

        Set<Long> targetRoleIds = new LinkedHashSet<>();
        for (UserAssignRoleReq.AssignItem item : req.items()) {
            String roleKey = item.roleTypeCode() + ":" + (item.domainCode() != null ? item.domainCode() : "") + ":" + item.roleExternalId();
            Long targetRoleId = roleIdMap.get(roleKey);
            if (targetRoleId != null) {
                targetRoleIds.add(targetRoleId);
            }
        }

        Set<Long> deniedRoleIds = engine.getDeniedIds(tenantId, operatorId, ResourceTypeCode.ROLE, targetRoleIds, OperationCodeConstants.MANAGE);

        Set<Long> allUserIds = new LinkedHashSet<>();
        for (UserAssignRoleReq.AssignItem item : req.items()) {
            String userKey = item.subjectTypeCode() + ":" + item.subjectExternalId();
            Long userId = userIdMap.get(userKey);
            if (userId != null) {
                allUserIds.add(userId);
            }
        }

        Map<String, UserRole> existingRelationMap = new HashMap<>();
        if (!allUserIds.isEmpty() && !targetRoleIds.isEmpty()) {
            List<UserRole> existingRelations = userRoleMapper.selectValidByUserIdsAndTargetIds(tenantId, allUserIds, targetRoleIds, ResourceTypeCode.ROLE);
            for (UserRole ur : existingRelations) {
                String key = ur.getAbstractUserId() + ":" + ur.getTargetId();
                existingRelationMap.put(key, ur);
            }
        }

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

            if (deniedRoleIds.contains(targetRoleId)) {
                errors.add("No permission to manage role: " + item.roleTypeCode() + "/" + item.roleExternalId());
                continue;
            }

            String relationKey = abstractUserId + ":" + targetRoleId;
            if (existingRelationMap.containsKey(relationKey)) {
                continue;
            }

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

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }

        if (!toInsert.isEmpty()) {
            userRoleMapper.insertBatch(toInsert);
        }

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

        Map<String, Long> userIdMap = typeResolutionService.batchResolveUserIds(
            tenantId, req.subjectTypeCode(), new LinkedHashSet<>(req.subjectExternalIds()));
        Map<String, Long> fullUserIdMap = userIdMap.entrySet().stream()
            .collect(Collectors.toMap(
                e -> req.subjectTypeCode() + ":" + e.getKey(),
                Map.Entry::getValue
            ));

        Long targetRoleId = typeResolutionService.resolveRoleId(
            tenantId, req.roleTypeCode(), req.roleExternalId(), req.domainCode());
        if (targetRoleId == null) {
            throw new IllegalArgumentException("Role not found: " + req.roleTypeCode() + "/" + req.roleExternalId());
        }

        Set<Long> deniedRoleIds = engine.getDeniedIds(
            tenantId, operatorId, ResourceTypeCode.ROLE, Set.of(targetRoleId), OperationCodeConstants.MANAGE);
        if (deniedRoleIds.contains(targetRoleId)) {
            throw new SecurityException("No permission to manage role: " + req.roleTypeCode() + "/" + req.roleExternalId());
        }

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

        Map<String, UserRole> existingRelationMap = new HashMap<>();
        if (!allUserIds.isEmpty()) {
            List<UserRole> existingRelations = userRoleMapper.selectValidByUserIdsAndTargetId(tenantId, allUserIds, targetRoleId, ResourceTypeCode.ROLE);
            for (UserRole ur : existingRelations) {
                String key = ur.getAbstractUserId() + ":" + ur.getTargetId();
                existingRelationMap.put(key, ur);
            }
        }

        List<UserRole> toInsert = new ArrayList<>();
        Set<Long> affectedUserIds = new LinkedHashSet<>();
        LocalDateTime now = LocalDateTime.now();

        for (String subjectExternalId : req.subjectExternalIds()) {
            String userKey = req.subjectTypeCode() + ":" + subjectExternalId;
            Long abstractUserId = fullUserIdMap.get(userKey);
            if (abstractUserId == null) {
                continue;
            }

            String relationKey = abstractUserId + ":" + targetRoleId;
            if (existingRelationMap.containsKey(relationKey)) {
                continue;
            }

            UserRole ur = new UserRole();
            ur.setTenantId(tenantId);
            ur.setAbstractUserId(abstractUserId);
            ur.setTargetType(ResourceTypeCode.ROLE);
            ur.setTargetId(targetRoleId);
            ur.setRelationId(req.relationId());
            ur.setValidFrom(null);
            ur.setValidTo(null);
            ur.setCreatedAt(now);
            ur.setUpdatedAt(now);
            ur.setDeleteFlag(0L);
            toInsert.add(ur);
            affectedUserIds.add(abstractUserId);
        }

        if (!toInsert.isEmpty()) {
            userRoleMapper.insertBatch(toInsert);
        }

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

        Set<String> roleExternalIds = req.items().stream()
            .map(UserRoleBatchRevokeReq.RevokeItem::roleExternalId)
            .filter(id -> id != null && !id.isBlank())
            .collect(Collectors.toSet());
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

        Set<Long> targetRoleIds = new LinkedHashSet<>();
        for (UserRoleBatchRevokeReq.RevokeItem item : req.items()) {
            String roleKey = item.roleTypeCode() + ":" + (item.domainCode() != null ? item.domainCode() : "") + ":" + item.roleExternalId();
            Long targetRoleId = roleIdMap.get(roleKey);
            if (targetRoleId != null) {
                targetRoleIds.add(targetRoleId);
            }
        }

        Set<Long> deniedIds = engine.getDeniedIds(tenantId, operatorId, ResourceTypeCode.ROLE, targetRoleIds, OperationCodeConstants.MANAGE);

        Map<Long, AbstractRole> roleMap = targetRoleIds.isEmpty() ? Map.of()
            : abstractRoleMapper.selectValidByIds(tenantId, targetRoleIds)
                .stream().collect(Collectors.toMap(AbstractRole::getId, r -> r));

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

        Map<String, UserRole> userRoleMap = new HashMap<>();
        if (!allUserIds.isEmpty() && !allRoleIds.isEmpty()) {
            List<UserRole> userRoles = userRoleMapper.selectValidByUserIdsTypeAndTargetIds(tenantId, allUserIds, ResourceTypeCode.ROLE, allRoleIds);
            for (UserRole ur : userRoles) {
                String key = ur.getAbstractUserId() + ":" + ur.getTargetId() + ":" + (ur.getRelationId() != null ? ur.getRelationId() : "null");
                userRoleMap.put(key, ur);
            }
        }

        List<Long> affectedUserIds = new ArrayList<>();
        List<Long> affectedRoleIds = new ArrayList<>();
        ArrayNode itemsJson = objectMapper.createArrayNode();
        int revoked = 0;
        List<String> deniedItems = new ArrayList<>();
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

            if (deniedIds.contains(targetRoleId)) {
                deniedItems.add(item.subjectTypeCode() + "/" + item.subjectExternalId() + " -> " + item.roleTypeCode() + "/" + item.roleExternalId());
                continue;
            }

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
        if (!idsToDelete.isEmpty()) {
            userRoleMapper.softDeleteBatch(tenantId, idsToDelete, now);
        }

        if (!deniedItems.isEmpty()) {
            log.info("Operator {} denied to revoke roles for items: {}", operatorId, deniedItems);
        }

        Set<Long> uniqueUsers = new LinkedHashSet<>(affectedUserIds);
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
            new PermissionChangeDomainService.ChangeLogContext(tenantId, operatorId, null, PermConstants.MaintainSource.MANUAL, "user-role-revoke"),
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
        List<UserRole> userRoles = userRoleMapper.selectValidByUserIdWithValidity(userId, tenantId, now);

        List<RoleSummary> summaries;
        if (userRoles.isEmpty()) {
            summaries = List.of();
        } else {
            Set<Long> targetIds = userRoles.stream()
                .map(UserRole::getTargetId)
                .collect(Collectors.toSet());

            List<AbstractRole> roles = abstractRoleMapper.selectValidByIds(tenantId, targetIds);

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
        Integer userType = null;
        if (subjectTypeCode != null && !subjectTypeCode.isBlank()) {
            userType = typeResolutionService.resolveTypeValue(tenantId, "user_type", subjectTypeCode);
        }
        boolean matchNone = false;
        if (domainCode != null && !domainCode.isBlank()) {
            matchNone = !domainClassifyService.matchesTypeCode(tenantId, DomainQueryMode.GLOBAL_PLUS, domainCode, ResourceTypeCode.USER);
        }
        if (!matchNone && subjectTypeCode != null && !subjectTypeCode.isBlank() && userType == null) {
            matchNone = true;
        }
        return abstractUserMapper.selectUserListPaged(tenantId, userType, keyword, matchNone, offset, limit)
            .stream().map(this::toUserResp).collect(Collectors.toList());
    }

    @Override
    public long countUsers(Long tenantId, String subjectTypeCode, String domainCode, String keyword) {
        Integer userType = null;
        if (subjectTypeCode != null && !subjectTypeCode.isBlank()) {
            userType = typeResolutionService.resolveTypeValue(tenantId, "user_type", subjectTypeCode);
        }
        boolean matchNone = false;
        if (domainCode != null && !domainCode.isBlank()) {
            matchNone = !domainClassifyService.matchesTypeCode(tenantId, DomainQueryMode.GLOBAL_PLUS, domainCode, ResourceTypeCode.USER);
        }
        if (!matchNone && subjectTypeCode != null && !subjectTypeCode.isBlank() && userType == null) {
            matchNone = true;
        }
        return abstractUserMapper.selectUserListCount(tenantId, userType, keyword, matchNone);
    }

    private UserResp toUserResp(AbstractUser user) {
        return new UserResp(
            user.getId(), user.getTenantId(), typeResolutionService.resolveTypeCode(user.getTenantId(), "user_type", user.getUserType()),
            user.getExternalId(), user.getName(), user.getEnabled(),
            user.getExtra(), user.getCreatedAt(), user.getUpdatedAt()
        );
    }
}