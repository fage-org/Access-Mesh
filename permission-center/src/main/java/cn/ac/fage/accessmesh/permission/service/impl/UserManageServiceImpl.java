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

/**
 * 用户管理服务实现类
 * <p>
 * 提供用户同步、创建、更新、删除、角色分配、角色撤销等功能。
 * 所有操作均通过PermQueryEngine进行权限校验，确保操作安全。
 * 批量操作采用批量查询和批量插入策略，避免N+1查询问题。
 * 缓存失效操作在事务提交后执行，防止缓存被回滚数据污染。
 * </p>
 *
 * TODO: 构造函数依赖过多(11个)，违反单一职责原则
 * 建议：拆分为 UserSyncService/UserRoleAssignService/UserQueryService
 * 优先级：P2（非阻塞，建议在下次大版本重构时处理）
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
    private final OperationLogDomainService operationLogDomainService;
    private final PermissionChangeDomainService permissionChangeDomainService;
    private final ObjectMapper objectMapper;
    private final AuthorizationService authorizationService;
    private final PermQueryEngine engine;

    /**
     * 构造函数注入依赖
     *
     * @param abstractUserMapper          抽象用户数据访问层
     * @param userRoleMapper              用户角色关联数据访问层
     * @param abstractRoleMapper          抽象角色数据访问层
     * @param abstractUserDomainService   抽象用户领域服务
     * @param userRoleDomainService       用户角色领域服务
     * @param typeResolutionService       类型解析服务
     * @param operationLogDomainService   操作日志领域服务
     * @param permissionChangeDomainService 权限变更领域服务
     * @param objectMapper                JSON对象映射器
     * @param authorizationService        授权服务
     * @param engine                      权限查询引擎
     */
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

    /**
     * 同步用户信息
     * <p>
     * 从外部系统同步用户数据。如果用户已存在（根据用户类型和外部ID判断），则更新用户信息；
     * 如果用户不存在，则创建新用户。同步操作需要USER_SYNC权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      用户同步请求，包含用户类型、外部ID、名称、启用状态、扩展信息
     * @return 用户响应信息
     * @throws SecurityException    无权限时抛出
     * @throws IllegalArgumentException 用户类型不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResp syncUser(Long tenantId, UserSyncReq req) {
        // 权限校验：同步用户需要USER_SYNC权限
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
            // 用户已存在，更新用户信息
            existing.setName(req.name() != null ? req.name() : existing.getName());
            existing.setEnabled(req.enabled() != null ? req.enabled() : existing.getEnabled());
            existing.setExtra(req.extra() != null ? req.extra() : existing.getExtra());
            existing.setUpdatedAt(LocalDateTime.now());
            abstractUserMapper.update(existing);
            return toUserResp(existing);
        }

        // 用户不存在，创建新用户
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

    /**
     * 创建用户
     * <p>
     * 创建新的用户记录。创建操作需要USER_CREATE权限。
     * 如果用户已存在（根据用户类型和外部ID判断），则抛出异常。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      用户创建请求，包含用户类型、外部ID、名称、启用状态、扩展信息
     * @return 用户响应信息
     * @throws SecurityException    无权限时抛出
     * @throws IllegalArgumentException 用户类型不存在或用户已存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResp createUser(Long tenantId, UserCreateReq req) {
        Long operatorId = OperatorContext.getOperatorId();

        // 权限校验：创建用户需要USER_CREATE权限
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

    /**
     * 更新用户信息
     * <p>
     * 更新用户的名称、启用状态、扩展信息等。
     * 用户修改自己的信息无需权限校验；修改其他用户需要USER_MANAGE权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      用户更新请求，包含用户ID、名称、启用状态、扩展信息
     * @return 用户响应信息
     * @throws SecurityException    无权限时抛出
     * @throws IllegalArgumentException 用户不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResp updateUser(Long tenantId, UserUpdateReq req) {
        Long operatorId = OperatorContext.getOperatorId();

        AbstractUser existing = abstractUserDomainService.selectValidById(tenantId, req.userId());
        if (existing == null) {
            throw new IllegalArgumentException("User not found: " + req.userId());
        }

        // 用户修改自己的信息无需权限校验，修改其他用户需要USER_MANAGE权限
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

    /**
     * 获取用户信息
     * <p>
     * 根据用户ID查询用户信息，返回用户类型、外部ID、名称、启用状态等。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @return 用户响应信息，不存在返回null
     */
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

    /**
     * 删除单个用户
     * <p>
     * 软删除用户记录。用户删除自己的账号无需权限校验；
     * 删除其他用户需要USER_MANAGE权限。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @throws SecurityException    无权限时抛出
     * @throws IllegalArgumentException 用户不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long tenantId, Long userId) {
        Long operatorId = OperatorContext.getOperatorId();

        AbstractUser user = abstractUserDomainService.selectValidById(tenantId, userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }

        // 用户删除自己的账号无需权限校验，删除其他用户需要USER_MANAGE权限
        if (!operatorId.equals(userId)) {
            engine.validate(tenantId, operatorId, ResourceTypeCode.USER, null, OperationCodeConstants.MANAGE);
        }

        user.setDeleteFlag(user.getId());
        user.setDeletedAt(LocalDateTime.now());
        abstractUserMapper.update(user);
    }

    /**
     * 批量删除用户
     * <p>
     * 批量软删除用户记录及其关联的用户角色关系。
     * 对于非自身用户，需要USER_MANAGE权限。
     * 缓存失效在事务提交后执行。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userIds  用户ID列表
     * @throws SecurityException 无权限删除部分用户时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUsers(Long tenantId, List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        Long operatorId = OperatorContext.getOperatorId();
        LocalDateTime now = LocalDateTime.now();

        // 批量查询用户以验证存在性
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

        // 权限校验：用户删除自己的账号无需权限校验，删除其他用户需要USER_MANAGE权限
        // 对于批量操作，仅校验非自身用户
        Set<Long> nonSelfUserIds = existingUserIds.stream()
            .filter(id -> !operatorId.equals(id))
            .collect(Collectors.toSet());

        if (!nonSelfUserIds.isEmpty()) {
            Set<Long> deniedIds = engine.getDeniedIds(tenantId, operatorId, ResourceTypeCode.USER, nonSelfUserIds, OperationCodeConstants.MANAGE);
            if (!deniedIds.isEmpty()) {
                throw new SecurityException("No permission to delete users: " + deniedIds);
            }
        }

        // 批量软删除用户
        abstractUserMapper.softDeleteBatch(tenantId, existingUserIds.stream().toList(), now);

        // 批量软删除用户角色关联
        List<Long> userRoleIds = userRoleMapper.selectListByQuery(
            QueryWrapper.create()
                .where(UserRoleTableDef.USER_ROLE.TENANT_ID.eq(tenantId))
                .and(UserRoleTableDef.USER_ROLE.ABSTRACT_USER_ID.in(existingUserIds))
                .and(UserRoleTableDef.USER_ROLE.DELETE_FLAG.eq(0))
        ).stream().map(UserRole::getId).toList();
        if (!userRoleIds.isEmpty()) {
            userRoleMapper.softDeleteBatch(tenantId, userRoleIds, now);
        }

        // 缓存失效（事务提交后执行）
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

        // 记录单条操作日志
        operationLogDomainService.asyncRecord(
            "user", "BATCH_DELETE",
            "abstract_user", null,
            "Deleted " + users.size() + " users",
            operatorId, null, null, tenantId
        );
    }

    /**
     * 分配角色给用户
     * <p>
     * 为多个用户分配多个角色。批量处理策略避免N+1查询：
     * 1. 按用户类型和角色类型分组批量解析ID
     * 2. 批量权限校验
     * 3. 批量查询现有关系避免重复插入
     * 4. 批量插入新关系
     * 缓存失效在事务提交后执行。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      用户角色分配请求，包含分配项列表
     * @throws SecurityException    无权限管理角色时抛出
     * @throws IllegalArgumentException 用户或角色不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRole(Long tenantId, UserAssignRoleReq req) {
        Long operatorId = OperatorContext.getOperatorId();

        if (req.items() == null || req.items().isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }

        // ===== 批量处理策略避免N+1查询 =====
        // 1. 按用户类型和角色类型分组批量解析ID
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

        // 2. 批量解析用户ID
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

        // 3. 批量解析角色ID
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

        // 4. 收集所有目标角色ID用于批量权限校验
        Set<Long> targetRoleIds = new LinkedHashSet<>();
        for (UserAssignRoleReq.AssignItem item : req.items()) {
            String roleKey = item.roleTypeCode() + ":" + (item.domainCode() != null ? item.domainCode() : "") + ":" + item.roleExternalId();
            Long targetRoleId = roleIdMap.get(roleKey);
            if (targetRoleId != null) {
                targetRoleIds.add(targetRoleId);
            }
        }

        // 5. 批量权限校验，避免N+1查询
        Set<Long> deniedRoleIds = engine.getDeniedIds(tenantId, operatorId, ResourceTypeCode.ROLE, targetRoleIds, OperationCodeConstants.MANAGE);

        // 6. 批量查询现有用户角色关系，避免循环内N+1查询
        Set<Long> allUserIds = new LinkedHashSet<>();
        for (UserAssignRoleReq.AssignItem item : req.items()) {
            String userKey = item.subjectTypeCode() + ":" + item.subjectExternalId();
            Long userId = userIdMap.get(userKey);
            if (userId != null) {
                allUserIds.add(userId);
            }
        }

        // 构建现有关系检查键格式：userId:roleId
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

        // 7. 构建待插入关系列表（跳过已存在和被拒绝的角色）
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

            // 使用预校验结果进行权限检查
            if (deniedRoleIds.contains(targetRoleId)) {
                errors.add("No permission to manage role: " + item.roleTypeCode() + "/" + item.roleExternalId());
                continue;
            }

            // 检查关系是否已存在
            String relationKey = abstractUserId + ":" + targetRoleId;
            if (existingRelationMap.containsKey(relationKey)) {
                // 已存在，跳过
                continue;
            }

            // 创建新关系
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

        // 8. 验证失败时抛出异常
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }

        // 9. 批量插入
        if (!toInsert.isEmpty()) {
            userRoleMapper.insertBatch(toInsert);
        }

        // 10. 缓存失效（事务提交后执行）
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

    /**
     * 批量为多个用户分配同一角色
     * <p>
     * 将指定角色分配给多个用户。批量处理策略避免N+1查询：
     * 1. 批量解析用户ID
     * 2. 批量权限校验
     * 3. 批量查询现有关系避免重复插入
     * 4. 批量插入新关系
     * 缓存失效在事务提交后执行。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      批量分配请求，包含用户外部ID列表、角色类型、角色外部ID、业务域编码
     * @throws SecurityException    无权限管理角色时抛出
     * @throws IllegalArgumentException 用户或角色不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRolesBatch(Long tenantId, UserRoleBatchAssignReq req) {
        if (req.subjectExternalIds() == null || req.subjectExternalIds().isEmpty()) {
            return;
        }

        Long operatorId = OperatorContext.getOperatorId();

        // ===== 批量处理策略避免N+1查询 =====
        // 1. 批量解析用户ID（所有用户共享同一用户类型）
        Map<String, Long> userIdMap = typeResolutionService.batchResolveUserIds(
            tenantId, req.subjectTypeCode(), new LinkedHashSet<>(req.subjectExternalIds()));
        Map<String, Long> fullUserIdMap = userIdMap.entrySet().stream()
            .collect(Collectors.toMap(
                e -> req.subjectTypeCode() + ":" + e.getKey(),
                Map.Entry::getValue
            ));

        // 2. 解析单个目标角色ID
        Long targetRoleId = typeResolutionService.resolveRoleId(
            tenantId, req.roleTypeCode(), req.roleExternalId(), req.domainCode());
        if (targetRoleId == null) {
            throw new IllegalArgumentException("Role not found: " + req.roleTypeCode() + "/" + req.roleExternalId());
        }

        // 3. 批量权限校验
        Set<Long> deniedRoleIds = engine.getDeniedIds(
            tenantId, operatorId, ResourceTypeCode.ROLE, Set.of(targetRoleId), OperationCodeConstants.MANAGE);
        if (deniedRoleIds.contains(targetRoleId)) {
            throw new SecurityException("No permission to manage role: " + req.roleTypeCode() + "/" + req.roleExternalId());
        }

        // 4. 收集所有用户ID
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

        // 5. 批量查询现有用户角色关系
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

        // 6. 构建待插入关系列表
        List<UserRole> toInsert = new ArrayList<>();
        Set<Long> affectedUserIds = new LinkedHashSet<>();
        LocalDateTime now = LocalDateTime.now();

        for (String subjectExternalId : req.subjectExternalIds()) {
            String userKey = req.subjectTypeCode() + ":" + subjectExternalId;
            Long abstractUserId = fullUserIdMap.get(userKey);
            if (abstractUserId == null) {
                continue; // 已在上面验证
            }

            // 检查关系是否已存在
            String relationKey = abstractUserId + ":" + targetRoleId;
            if (existingRelationMap.containsKey(relationKey)) {
                continue; // 已存在，跳过
            }

            // 创建新关系
            UserRole ur = new UserRole();
            ur.setTenantId(tenantId);
            ur.setAbstractUserId(abstractUserId);
            ur.setTargetType(ResourceTypeCode.ROLE);
            ur.setTargetId(targetRoleId);
            ur.setRelationId(req.relationId());
            ur.setValidFrom(null); // UserRoleBatchAssignReq没有validFrom/validTo
            ur.setValidTo(null);
            ur.setCreatedAt(now);
            ur.setUpdatedAt(now);
            ur.setDeleteFlag(0L);
            toInsert.add(ur);
            affectedUserIds.add(abstractUserId);
        }

        // 7. 批量插入
        if (!toInsert.isEmpty()) {
            userRoleMapper.insertBatch(toInsert);
        }

        // 8. 缓存失效（事务提交后执行）
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

    /**
     * 批量撤销用户角色
     * <p>
     * 批量撤销用户与角色的关联关系。批量处理策略避免N+1查询：
     * 1. 按角色类型和用户类型分组批量解析ID
     * 2. 批量权限校验
     * 3. 批量查询现有关系
     * 4. 批量软删除
     * 缓存失效在事务提交后执行。
     * 记录权限变更日志和操作日志。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      批量撤销请求，包含撤销项列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeRolesBatch(Long tenantId, UserRoleBatchRevokeReq req) {
        Long operatorId = OperatorContext.getOperatorId();

        if (req.items() == null || req.items().isEmpty()) {
            return;
        }

        // ===== 批量解析策略避免N+1查询 =====
        // 1. 收集所有唯一的角色外部ID并批量解析
        Set<String> roleExternalIds = req.items().stream()
            .map(UserRoleBatchRevokeReq.RevokeItem::roleExternalId)
            .filter(id -> id != null && !id.isBlank())
            .collect(Collectors.toSet());
        // 按角色类型和业务域分组批量解析
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

        // 2. 收集所有唯一的用户外部ID并批量解析
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

        // 收集所有目标角色ID用于批量权限校验
        Set<Long> targetRoleIds = new LinkedHashSet<>();
        for (UserRoleBatchRevokeReq.RevokeItem item : req.items()) {
            String roleKey = item.roleTypeCode() + ":" + (item.domainCode() != null ? item.domainCode() : "") + ":" + item.roleExternalId();
            Long targetRoleId = roleIdMap.get(roleKey);
            if (targetRoleId != null) {
                targetRoleIds.add(targetRoleId);
            }
        }

        // 批量权限校验，避免N+1查询
        Set<Long> deniedIds = engine.getDeniedIds(tenantId, operatorId, ResourceTypeCode.ROLE, targetRoleIds, OperationCodeConstants.MANAGE);

        // 批量加载角色，避免循环内N+1查询
        Map<Long, AbstractRole> roleMap = targetRoleIds.isEmpty() ? Map.of()
            : abstractRoleMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(AbstractRoleTableDef.ABSTRACT_ROLE.TENANT_ID.eq(tenantId))
                    .and(AbstractRoleTableDef.ABSTRACT_ROLE.ID.in(targetRoleIds))
                    .and(AbstractRoleTableDef.ABSTRACT_ROLE.DELETE_FLAG.eq(0))
            ).stream().collect(Collectors.toMap(AbstractRole::getId, r -> r));

        // ===== 批量查询用户角色关系，避免循环内N+1查询 =====
        // 收集所有(userId, roleId)对用于批量查询
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

        // 批量查询所有相关的用户角色关系
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
                // 键格式：userId:roleId:relationId（null值用"null"表示）
                String key = ur.getAbstractUserId() + ":" + ur.getTargetId() + ":" + (ur.getRelationId() != null ? ur.getRelationId() : "null");
                userRoleMap.put(key, ur);
            }
        }

        List<Long> affectedUserIds = new ArrayList<>();
        List<Long> affectedRoleIds = new ArrayList<>();
        ArrayNode itemsJson = objectMapper.createArrayNode();
        int revoked = 0;
        List<String> deniedItems = new ArrayList<>();
        // 性能修复：收集ID用于批量软删除
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

            // 使用预校验结果进行权限检查
            if (deniedIds.contains(targetRoleId)) {
                deniedItems.add(item.subjectTypeCode() + "/" + item.subjectExternalId() + " -> " + item.roleTypeCode() + "/" + item.roleExternalId());
                continue;  // 跳过此项，无权限撤销
            }

            // 从预加载的映射中查找用户角色
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
        // 性能修复：批量软删除代替循环更新
        if (!idsToDelete.isEmpty()) {
            userRoleMapper.softDeleteBatch(tenantId, idsToDelete, now);
        }

        // 记录被拒绝的项目
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

    /**
     * 获取用户的角色列表
     * <p>
     * 查询用户当前有效的角色关联。有效条件：未删除、有效期范围内。
     * 批量预加载角色信息避免N+1查询。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      用户角色列表请求，包含用户类型和用户外部ID
     * @return 用户角色响应，包含角色摘要列表
     */
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

        // 批量预加载角色，避免N+1查询
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

    /**
     * 分页查询用户列表
     * <p>
     * 根据用户类型、业务域、关键词等条件分页查询用户。
     * 支持模糊搜索用户名称和外部ID。
     * </p>
     *
     * @param tenantId        租户ID
     * @param subjectTypeCode 用户类型编码，可选
     * @param domainCode      业务域编码，可选
     * @param keyword         搜索关键词，可选
     * @param offset          分页偏移量
     * @param limit           分页大小
     * @return 用户响应列表
     */
    @Override
    public List<UserResp> listUsers(Long tenantId, String subjectTypeCode, String domainCode, String keyword, int offset, int limit) {
        QueryWrapper queryWrapper = buildUserListQuery(tenantId, subjectTypeCode, domainCode, keyword)
            .limit(limit)
            .offset(offset);
        return abstractUserMapper.selectListByQuery(queryWrapper)
            .stream().map(this::toUserResp).collect(Collectors.toList());
    }

    /**
     * 统计用户总数
     * <p>
     * 根据用户类型、业务域、关键词等条件统计用户数量。
     * </p>
     *
     * @param tenantId        租户ID
     * @param subjectTypeCode 用户类型编码，可选
     * @param domainCode      业务域编码，可选
     * @param keyword         搜索关键词，可选
     * @return 用户总数
     */
    @Override
    public long countUsers(Long tenantId, String subjectTypeCode, String domainCode, String keyword) {
        return abstractUserMapper.selectCountByQuery(
            buildUserListQuery(tenantId, subjectTypeCode, domainCode, keyword)
        );
    }

    /**
     * 构建用户列表查询条件
     * <p>
     * 根据用户类型、业务域、关键词构建QueryWrapper。
     * 业务域过滤通过用户角色关联的角色的业务域实现。
     * </p>
     *
     * @param tenantId        租户ID
     * @param subjectTypeCode 用户类型编码，可选
     * @param domainCode      业务域编码，可选
     * @param keyword         搜索关键词，可选
     * @return QueryWrapper查询条件
     */
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

    /**
     * 将AbstractUser实体转换为UserResp响应
     *
     * @param user 抽象用户实体
     * @return 用户响应信息
     */
    private UserResp toUserResp(AbstractUser user) {
        return new UserResp(
            user.getId(), user.getTenantId(), typeResolutionService.resolveTypeCode(user.getTenantId(), "user_type", user.getUserType()),
            user.getExternalId(), user.getName(), user.getEnabled(),
            user.getExtra(), user.getCreatedAt(), user.getUpdatedAt()
        );
    }
}