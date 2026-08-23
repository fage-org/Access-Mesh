package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLog;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLogRuntimeContext;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChange;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChangeContext;
import cn.ac.fage.accessmesh.access.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserAssignRoleReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserCreateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleBatchAssignReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleBatchRevokeReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleListReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.UserResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.UserRolesResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.UserRolesResp.RoleSummary;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractUser;
import cn.ac.fage.accessmesh.access.permission.entity.UserRole;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.UserRoleMapper;
import cn.ac.fage.accessmesh.access.permission.service.UserManageAppService;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionGuard;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.access.permission.enums.DomainQueryMode;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.access.permission.util.PermissionConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

/**
 * 用户管理服务实现类
 * <p>
 * 提供用户同步、创建、更新、删除、角色分配、角色撤销等功能。
 * 所有操作均通过PermQueryEngine进行权限校验，确保操作安全。
 * 批量操作采用批量查询和批量插入策略，避免N+1查询问题。
 * 缓存失效操作在事务提交后执行，防止缓存被回滚数据污染。
 * 注意：这里管理的是 permission 域（access-service）的 abstract_user 主体事实，
 * 主体写路径（创建/更新/删除）在同一事务维护 resource_entity(USER) 投影
 * （code = subjectId，architecture §12.3，T-ACCESS-019）；LOCAL_USER 主体的
 * 事实与投影归 admin 域写链路（UserWriteAppService），本入口按保留业务键拒绝。
 * </p>
 */
@Service
public class UserManageAppServiceImpl implements UserManageAppService {

    private static final Logger log = LoggerFactory.getLogger(UserManageAppServiceImpl.class);

    private static final Set<String> ROLE_TYPES_REQUIRING_DOMAIN = Set.of("ORG", "POSITION");

    private final AbstractUserMapper abstractUserMapper;
    private final UserRoleMapper userRoleMapper;
    private final AbstractRoleMapper abstractRoleMapper;
    private final SubjectDomainService subjectDomainService;
    private final TypeResolutionService typeResolutionService;
    private final DomainClassifyService domainClassifyService;
    private final AuditDomainService auditDomainService;
    private final LocalProjectionGuard localProjectionGuard;
    private final LocalProjectionDomainService localProjectionDomainService;
    private final ObjectMapper objectMapper;
    private final PermQueryEngine engine;

    /**
     * Feature flag：是否启用 ORG/POSITION 角色的 domainCode 必填校验。
     * 上线时先设 false 观察一轮，确认存量无脏数据后再改为 true。
     */
    @org.springframework.beans.factory.annotation.Value("${permission.assign.strict-domain-check:true}")
    private boolean strictDomainCheck;

    /**
     * 构造函数注入依赖
     *
     * @param abstractUserMapper    抽象用户数据访问层
     * @param userRoleMapper        用户角色数据访问层
     * @param abstractRoleMapper    抽象角色数据访问层
     * @param subjectDomainService  主体领域服务
     * @param typeResolutionService 类型解析服务
     * @param domainClassifyService 域分类服务
     * @param auditDomainService    审计领域服务
     * @param objectMapper          JSON解析器
     * @param engine                权限查询引擎
     */
    public UserManageAppServiceImpl(AbstractUserMapper abstractUserMapper,
                                 UserRoleMapper userRoleMapper,
                                 AbstractRoleMapper abstractRoleMapper,
                                 SubjectDomainService subjectDomainService,
                                 TypeResolutionService typeResolutionService,
                                 DomainClassifyService domainClassifyService,
                                 AuditDomainService auditDomainService,
                                 LocalProjectionGuard localProjectionGuard,
                                 LocalProjectionDomainService localProjectionDomainService,
                                 ObjectMapper objectMapper,
                                 PermQueryEngine engine) {
        this.abstractUserMapper = abstractUserMapper;
        this.userRoleMapper = userRoleMapper;
        this.abstractRoleMapper = abstractRoleMapper;
        this.subjectDomainService = subjectDomainService;
        this.typeResolutionService = typeResolutionService;
        this.domainClassifyService = domainClassifyService;
        this.auditDomainService = auditDomainService;
        this.localProjectionGuard = localProjectionGuard;
        this.localProjectionDomainService = localProjectionDomainService;
        this.objectMapper = objectMapper;
        this.engine = engine;
    }

    /**
     * 跨字段业务校验：ORG/POSITION 角色必须指定 domainCode。
     * 由 Feature flag {@code permission.assign.strict-domain-check} 控制开关。
     */
    private void validateDomainCodeForOrgPosition(String roleTypeCode, String domainCode) {
        if (strictDomainCheck
            && ROLE_TYPES_REQUIRING_DOMAIN.contains(roleTypeCode)
            && (domainCode == null || domainCode.isBlank())) {
            throw new BizException(PermissionErrorCode.VALIDATION_FAILED.getCode(),
                "ORG/POSITION 角色必须指定 domainCode");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "PERMISSION", action = "ABSTRACT_USER_CREATE", targetType = "abstract_user", targetId = "#result.id()", summary = "'create user ' + #req.externalId()")
    public UserResp createUser(Long tenantId, UserCreateReq req) {
        Long operatorId = OperatorContext.getOperatorId();

        if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.USER, null, OperationCodeConstants.CREATE)) {
            throw new SecurityException("No permission to create user");
        }
        localProjectionGuard.rejectReservedSubjectType(req.subjectTypeCode());

        Integer userType = typeResolutionService.resolveTypeValue(tenantId, "user_type", req.subjectTypeCode());
        if (userType == null) {
            throw new BizException(PermissionErrorCode.TYPE_CODE_NOT_FOUND.getCode(), "Unknown subjectTypeCode: " + req.subjectTypeCode());
        }
        AbstractUser existing = abstractUserMapper.selectByTypeAndExternalId(tenantId, userType, req.externalId());
        if (existing != null) {
            throw new BizException(PermissionErrorCode.USER_ALREADY_EXISTS.getCode(), "User already exists");
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

        // T-ACCESS-019：USER 资源投影与主体事实同事务（code=subjectId，§12.3）
        boolean enabled = Boolean.TRUE.equals(user.getEnabled());
        localProjectionDomainService.upsertUserResource(tenantId, user.getId(), user.getName(), enabled);
        recordProjectionChange(tenantId, user.getId(), "UPSERT");
        PermissionChangeContext.markUsers(tenantId, Set.of(user.getId()));
        return toUserResp(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "PERMISSION", action = "ABSTRACT_USER_UPDATE", targetType = "abstract_user", targetId = "#req.userId()", summary = "'update user ' + #req.userId()")
    public UserResp updateUser(Long tenantId, UserUpdateReq req) {
        Long operatorId = OperatorContext.getOperatorId();

        AbstractUser existing = subjectDomainService.selectValidUserById(tenantId, req.userId());
        if (existing == null) {
            throw new BizException(PermissionErrorCode.USER_NOT_FOUND.getCode(), "User not found: " + req.userId());
        }
        localProjectionGuard.rejectIfLocalUser(existing);

        if (!operatorId.equals(req.userId())) {
            // T-ACCESS-019：升实例级门禁（resource_entity(USER).code = subjectId），与 deleteUsers/updateRole 对齐
            if (!engine.hasPermissionByCode(tenantId, operatorId, ResourceTypeCode.USER, String.valueOf(req.userId()), OperationCodeConstants.MANAGE)) {
                throw new SecurityException("Permission denied: MANAGE on USER:" + req.userId());
            }
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

        // T-ACCESS-019：USER 资源投影同事务镜像 name/enabled；enabled 变更影响授权可用性，
        // 登记 markUsers 失效主体有效角色缓存（afterCommit 由 @PermissionChange AOP 处理）
        localProjectionDomainService.upsertUserResource(
            tenantId, existing.getId(), existing.getName(), Boolean.TRUE.equals(existing.getEnabled()));
        recordProjectionChange(tenantId, existing.getId(), "UPSERT");
        PermissionChangeContext.markUsers(tenantId, Set.of(existing.getId()));
        return toUserResp(existing);
    }

    @Override
    public UserResp getUser(Long tenantId, Long userId) {
        AbstractUser user = abstractUserMapper.selectValidById(userId, tenantId);
        return user != null ? toUserResp(user) : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "ABSTRACT_USER_REMOVE", targetType = "abstract_user", targetId = "", summary = "'batch delete users'")
    @PermissionChange
    public void deleteUsers(Long tenantId, List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        Long operatorId = OperatorContext.getOperatorId();
        LocalDateTime now = LocalDateTime.now();

        Set<Long> userIdsSet = new LinkedHashSet<>(userIds);
        List<AbstractUser> users = abstractUserMapper.selectValidByIds(tenantId, userIdsSet);

        if (users.isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }
        users.forEach(localProjectionGuard::rejectIfLocalUser);

        Set<Long> existingUserIds = users.stream().map(AbstractUser::getId).collect(Collectors.toSet());

        Set<Long> nonSelfUserIds = existingUserIds.stream()
            .filter(id -> !operatorId.equals(id))
            .collect(Collectors.toSet());

        if (!nonSelfUserIds.isEmpty()) {
            // T-PERM-042：USER 实例门禁改业务编码语义（resource_entity(USER).code = subjectId），
            // 不再把 abstract_user.id 当 resource_entity.id 直查
            Set<String> nonSelfUserCodes = nonSelfUserIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toSet());
            Set<String> deniedCodes = engine.getDeniedResourceCodes(
                tenantId, operatorId, ResourceTypeCode.USER, nonSelfUserCodes, OperationCodeConstants.MANAGE);
            if (!deniedCodes.isEmpty()) {
                throw new SecurityException("No permission to delete users: " + deniedCodes);
            }
        }

        abstractUserMapper.softDeleteBatch(tenantId, existingUserIds.stream().toList(), now);

        List<UserRole> userRoles = userRoleMapper.selectValidByUserIds(tenantId, existingUserIds);
        List<Long> userRoleIds = userRoles.stream().map(UserRole::getId).toList();
        if (!userRoleIds.isEmpty()) {
            userRoleMapper.softDeleteBatch(tenantId, userRoleIds, now);
        }

        // T-ACCESS-019：USER 资源投影同事务软删，实例授权目标随之不可解析（fail-closed）
        localProjectionDomainService.softDeleteUserResources(tenantId, existingUserIds);

        OperationLogRuntimeContext.setSummary("Deleted " + existingUserIds.size() + " users");

        // 投影删除变更日志（T-ACCESS-019，一次 insertBatch，与 UserWriteAppServiceImpl 删除路径同模式）
        List<AuditDomainService.ChangeLogEntry> deleteEntries = existingUserIds.stream()
            .map(userId -> new AuditDomainService.ChangeLogEntry(
                "abstract_user", userId, "DELETE", null, null, null,
                new Long[]{userId}, new Long[0]))
            .collect(Collectors.toList());
        auditDomainService.recordChangeLog(
            new AuditDomainService.ChangeLogContext(
                tenantId, operatorId, null, PermConstants.MaintainSource.MANUAL, "local-projection"),
            deleteEntries);

        // 登记受影响用户，afterCommit 失效与广播由 @PermissionChange AOP 统一处理（铁律 P1-B）
        PermissionChangeContext.markUsers(tenantId, existingUserIds);

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "USER_ROLE_ASSIGN", targetType = "user_role", targetId = "", summary = "'assign user roles'")
    @PermissionChange
    public void assignRole(Long tenantId, UserAssignRoleReq req) {
        Long operatorId = OperatorContext.getOperatorId();

        if (req.items() == null || req.items().isEmpty()) {
            throw new BizException(PermissionErrorCode.REQUEST_ITEMS_EMPTY.getCode(), "items must not be empty");
        }

        // M2: 跨字段业务校验 — ORG/POSITION 必带 domainCode
        for (UserAssignRoleReq.AssignItem item : req.items()) {
            localProjectionGuard.rejectReservedRoleType(item.roleTypeCode());
            validateDomainCodeForOrgPosition(item.roleTypeCode(), item.domainCode());
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

        // T-PERM-042：ROLE 实例门禁改业务编码语义（resource_entity(ROLE).code = roleId）
        Set<String> deniedRoleCodes = engine.getDeniedResourceCodes(
            tenantId, operatorId, ResourceTypeCode.ROLE,
            targetRoleIds.stream().map(String::valueOf).collect(Collectors.toSet()),
            OperationCodeConstants.MANAGE);

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

            if (deniedRoleCodes.contains(String.valueOf(targetRoleId))) {
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
            throw new BizException(PermissionErrorCode.VALIDATION_FAILED.getCode(), String.join("; ", errors));
        }

        if (!toInsert.isEmpty()) {
            userRoleMapper.insertBatch(toInsert);
            OperationLogRuntimeContext.setSummary("assigned " + toInsert.size() + " user-role relation(s)");
        } else {
            OperationLogRuntimeContext.markSkip();
        }

        // 登记受影响用户，afterCommit 失效与广播由 @PermissionChange AOP 统一处理（铁律 P1-B）
        if (!affectedUserIds.isEmpty()) {
            PermissionChangeContext.markUsers(tenantId, affectedUserIds);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "USER_ROLE_BATCH_ASSIGN", targetType = "abstract_role", targetId = "#req.roleExternalId()", summary = "'batch assign role ' + #req.roleExternalId()")
    @PermissionChange
    public void assignRolesBatch(Long tenantId, UserRoleBatchAssignReq req) {
        if (req.subjectExternalIds() == null || req.subjectExternalIds().isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        localProjectionGuard.rejectReservedRoleType(req.roleTypeCode());
        // M2: 跨字段业务校验 — ORG/POSITION 必带 domainCode
        validateDomainCodeForOrgPosition(req.roleTypeCode(), req.domainCode());

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
            throw new BizException(PermissionErrorCode.ROLE_NOT_FOUND.getCode(), "Role not found: " + req.roleTypeCode() + "/" + req.roleExternalId());
        }

        Set<String> deniedRoleCodes = engine.getDeniedResourceCodes(
            tenantId, operatorId, ResourceTypeCode.ROLE,
            Set.of(String.valueOf(targetRoleId)), OperationCodeConstants.MANAGE);
        if (!deniedRoleCodes.isEmpty()) {
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
            throw new BizException(PermissionErrorCode.USER_NOT_FOUND.getCode(), String.join("; ", userNotFoundErrors));
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
            OperationLogRuntimeContext.setSummary(
                "assigned " + toInsert.size() + " user-role relation(s) to role " + req.roleExternalId()
            );
        } else {
            OperationLogRuntimeContext.markSkip();
        }

        // 登记受影响用户，afterCommit 失效与广播由 @PermissionChange AOP 统一处理（铁律 P1-B）
        if (!affectedUserIds.isEmpty()) {
            PermissionChangeContext.markUsers(tenantId, affectedUserIds);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "USER_ROLE_REVOKE", targetType = "user_role", targetId = "", summary = "'batch revoke user-role relations'")
    @PermissionChange
    public void revokeRolesBatch(Long tenantId, UserRoleBatchRevokeReq req) {
        Long operatorId = OperatorContext.getOperatorId();

        if (req.items() == null || req.items().isEmpty()) {
            OperationLogRuntimeContext.markSkip();
            return;
        }

        // M2: 跨字段业务校验 — ORG/POSITION 必带 domainCode
        for (UserRoleBatchRevokeReq.RevokeItem item : req.items()) {
            localProjectionGuard.rejectReservedRoleType(item.roleTypeCode());
            validateDomainCodeForOrgPosition(item.roleTypeCode(), item.domainCode());
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

        // T-PERM-042：ROLE 实例门禁改业务编码语义（resource_entity(ROLE).code = roleId）
        Set<String> deniedRoleCodes = engine.getDeniedResourceCodes(
            tenantId, operatorId, ResourceTypeCode.ROLE,
            targetRoleIds.stream().map(String::valueOf).collect(Collectors.toSet()),
            OperationCodeConstants.MANAGE);

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
                throw new BizException(PermissionErrorCode.USER_NOT_FOUND.getCode(), "User not found: " + item.subjectTypeCode() + "/" + item.subjectExternalId());
            }
            String roleKey = item.roleTypeCode() + ":" + (item.domainCode() != null ? item.domainCode() : "") + ":" + item.roleExternalId();
            Long targetRoleId = roleIdMap.get(roleKey);
            if (targetRoleId == null) {
                throw new BizException(PermissionErrorCode.ROLE_NOT_FOUND.getCode(), "Role not found: " + item.roleTypeCode() + "/" + item.roleExternalId());
            }

            if (deniedRoleCodes.contains(String.valueOf(targetRoleId))) {
                deniedItems.add(item.subjectTypeCode() + "/" + item.subjectExternalId() + " -> " + item.roleTypeCode() + "/" + item.roleExternalId());
                continue;
            }

            String urKey = abstractUserId + ":" + targetRoleId + ":" + (item.relationId() != null ? item.relationId() : "null");
            UserRole ur = userRoleMap.get(urKey);
            if (ur == null) {
                throw new BizException(PermissionErrorCode.USER_ROLE_RELATION_NOT_FOUND.getCode(), "User-role relation not found for item");
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

        OperationLogRuntimeContext.setSummary(
            "revoked " + revoked + " user-role relation(s), denied=" + deniedItems.size()
        );

        Set<Long> uniqueUsers = new LinkedHashSet<>(affectedUserIds);
        // 登记受影响用户，afterCommit 失效与广播由 @PermissionChange AOP 统一处理（铁律 P1-B）
        PermissionChangeContext.markUsers(tenantId, uniqueUsers);
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
        auditDomainService.recordChangeLog(
            new AuditDomainService.ChangeLogContext(tenantId, operatorId, null, PermConstants.MaintainSource.MANUAL, "user-role-revoke"),
            List.of(new AuditDomainService.ChangeLogEntry(
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

            // 批量解析 relationId（关联组织角色 abstract_role.id）→ externalId，供 admin 解析组织名
            // relationId 与 targetId 是不同角色（targetId=用户持有的角色，relationId=关联组织角色），
            // 单独收集批量查询，避免 admin 层用内部主键错查 sys_org（P2-1 修复）
            Set<Long> relationRoleIds = userRoles.stream()
                .map(UserRole::getRelationId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
            Map<Long, String> relationExternalIdMap;
            if (relationRoleIds.isEmpty()) {
                relationExternalIdMap = java.util.Map.of();
            } else {
                // 复用已查的 roleMap，再补查未命中的 relationId
                Set<Long> missing = new java.util.HashSet<>(relationRoleIds);
                missing.removeAll(roleMap.keySet());
                if (!missing.isEmpty()) {
                    abstractRoleMapper.selectValidByIds(tenantId, missing)
                        .forEach(r -> roleMap.put(r.getId(), r));
                }
                relationExternalIdMap = new java.util.HashMap<>();
                for (Long rid : relationRoleIds) {
                    AbstractRole r = roleMap.get(rid);
                    if (r != null) {
                        relationExternalIdMap.put(rid, r.getExternalId());
                    }
                }
            }

            final Map<Long, String> finalRelationExternalIdMap = relationExternalIdMap;
            summaries = userRoles.stream()
                .map(ur -> {
                    AbstractRole role = roleMap.get(ur.getTargetId());
                    return new RoleSummary(
                        role != null ? role.getExternalId() : null,
                        role != null ? role.getName() : null,
                        role != null ? typeResolutionService.resolveTypeCode(tenantId, "role_type", role.getRoleType()) : null,
                        ur.getTargetType(),
                        ur.getRelationId(),
                        ur.getRelationId() != null ? finalRelationExternalIdMap.get(ur.getRelationId()) : null,
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

    /** 投影写变更日志（T-ACCESS-019：USER 投影 UPSERT 与主体事实同事务登记） */
    private void recordProjectionChange(Long tenantId, Long userId, String operation) {
        auditDomainService.recordChangeLog(
            new AuditDomainService.ChangeLogContext(
                tenantId, OperatorContext.getOperatorId(), null, PermConstants.MaintainSource.MANUAL, "local-projection"),
            List.of(new AuditDomainService.ChangeLogEntry(
                "abstract_user", userId, operation, null, null, null,
                new Long[]{userId}, new Long[0])));
    }

    private UserResp toUserResp(AbstractUser user) {
        return new UserResp(
            user.getId(), user.getTenantId(), typeResolutionService.resolveTypeCode(user.getTenantId(), "user_type", user.getUserType()),
            user.getExternalId(), user.getName(), user.getEnabled(),
            user.getExtra(), user.getCreatedAt(), user.getUpdatedAt()
        );
    }
}
