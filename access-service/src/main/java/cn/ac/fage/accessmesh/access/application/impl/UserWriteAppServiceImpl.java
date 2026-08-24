package cn.ac.fage.accessmesh.access.application.impl;

import cn.ac.fage.accessmesh.access.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.UserCreateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.UserUpdateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.UserUpdateStatusReq;
import cn.ac.fage.accessmesh.access.admin.dto.resp.UserCreateResp;
import cn.ac.fage.accessmesh.access.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.access.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.access.admin.entity.SysUser;
import cn.ac.fage.accessmesh.access.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;

import cn.ac.fage.accessmesh.access.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.access.application.query.OrgVisibilityQueryService;
import cn.ac.fage.accessmesh.access.application.UserWriteAppService;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLog;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChange;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChangeContext;
import cn.ac.fage.accessmesh.access.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.exception.SystemException;
import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户写编排：管理事实、权限投影、permission_change_log 同一事务。
 */
@Service
public class UserWriteAppServiceImpl implements UserWriteAppService {

    private static final Logger log = LoggerFactory.getLogger(UserWriteAppServiceImpl.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserDomainService userDomainService;
    private final UserOrgDomainService userOrgDomainService;
    private final OrgTreeConfigDomainService orgTreeConfigDomainService;
    private final OrgDomainService orgDomainService;
    private final AdminPermissionValidator permissionValidator;
    private final OrgVisibilityQueryService orgVisibilityQueryService;
    private final LocalProjectionDomainService localProjectionDomainService;
    private final AuditDomainService auditDomainService;
    private final ObjectMapper objectMapper;

    public UserWriteAppServiceImpl(UserDomainService userDomainService,
                                   UserOrgDomainService userOrgDomainService,
                                   OrgTreeConfigDomainService orgTreeConfigDomainService,
                                   OrgDomainService orgDomainService,
                                   AdminPermissionValidator permissionValidator,
                                   OrgVisibilityQueryService orgVisibilityQueryService,
                                   LocalProjectionDomainService localProjectionDomainService,
                                   AuditDomainService auditDomainService,
                                   ObjectMapper objectMapper) {
        this.userDomainService = userDomainService;
        this.userOrgDomainService = userOrgDomainService;
        this.orgTreeConfigDomainService = orgTreeConfigDomainService;
        this.orgDomainService = orgDomainService;
        this.permissionValidator = permissionValidator;
        this.orgVisibilityQueryService = orgVisibilityQueryService;
        this.localProjectionDomainService = localProjectionDomainService;
        this.auditDomainService = auditDomainService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "ACCESS", action = "USER_CREATE", targetType = "sys_user",
        targetId = "#result.id()", summary = "'create user ' + #req.username()")
    public UserCreateResp createUser(UserCreateReq req) {
        permissionValidator.checkTypeLevel(ResourceTypeCode.USER, AdminOperationCode.CREATE);
        Long tenantId = TenantContextHolder.getTenantId();

        if (userDomainService.existsByUsername(tenantId, req.username())) {
            throw new BizException(AdminErrorCode.USER_ALREADY_EXISTS.getCode(),
                AdminErrorCode.USER_ALREADY_EXISTS.getMessage());
        }
        if (req.phone() != null && userDomainService.existsByPhone(tenantId, req.phone())) {
            throw new BizException(AdminErrorCode.PHONE_ALREADY_EXISTS.getCode(),
                AdminErrorCode.PHONE_ALREADY_EXISTS.getMessage());
        }

        // T-ORG-001（architecture §12.2）：预取主体 ID N → 显式同 ID 写 abstract_user(external_id=N)
        // 与 sys_user(id=N)，两表共用 abstract_user.id 序列，与外部主体取号互不碰撞。
        // status 缺省解析一次、两侧同源（T-ACCESS-021 E2E 发现的缺陷修复：此前 sys_user 侧
        // 默认 1=启用而 isEnabled(null)=false，未传 status 时建成「启用+禁用」自相矛盾的主体，
        // 权限管线按禁用主体解析 → 快照恒空 → 全接口 403）。语义以 DDL 为准：1=启用，0=停用。
        Integer status = req.status() != null ? req.status() : 1;
        Long subjectId = localProjectionDomainService.createLocalUserSubject(
            tenantId, req.name(), isEnabled(status), extraUsername(req.username()));

        SysUser user = new SysUser();
        user.setId(subjectId);
        user.setTenantId(tenantId);
        user.setUsername(req.username());
        user.setName(req.name());
        user.setPhone(req.phone());
        user.setEmail(req.email());
        String initialPassword = generateRandomPassword();
        user.setPassword(BCrypt.hashpw(initialPassword));
        user.setStatus(status);
        // UserCreateReq 无 gender/user_type/force_reset_pwd 字段，显式 NULL 插入会绕过列默认值
        // 触发 NOT NULL 约束（DDL：gender 0=未知，user_type 1=人员，force_reset_pwd 随机初始密码须强制改密）
        user.setGender(0);
        user.setUserType(1);
        user.setForceResetPwd(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setDeleteFlag(0L);
        userDomainService.insert(user);

        recordProjectionChange(tenantId, "abstract_user", subjectId, "UPSERT",
            new Long[]{subjectId}, new Long[0]);
        PermissionChangeContext.markUsers(tenantId, Set.of(subjectId));

        if (req.orgId() != null) {
            validateOrgInDefaultTree(tenantId, req.orgId());
            permissionValidator.checkInstanceLevel(
                ResourceTypeCode.ORG, String.valueOf(req.orgId()), AdminOperationCode.UPDATE);

            SysUserOrg userOrg = new SysUserOrg();
            userOrg.setTenantId(tenantId);
            userOrg.setUserId(user.getId());
            userOrg.setOrgId(req.orgId());
            userOrg.setIsPrimary(req.primaryOrg() != null ? req.primaryOrg() : true);
            userOrg.setCreatedAt(LocalDateTime.now());
            userOrg.setUpdatedAt(LocalDateTime.now());
            userOrg.setDeleteFlag(0L);
            userOrgDomainService.insertBatch(List.of(userOrg));

            SysOrg targetOrg = orgDomainService.selectValidById(tenantId, req.orgId());
            String roleTypeCode = resolveOrgRoleTypeCode(targetOrg);
            // POSITION 绑定 relation 指向所属组织（岗位的 parentId）
            Long userRoleId = localProjectionDomainService.bindUserOrg(
                tenantId, user.getId(), req.orgId(), roleTypeCode,
                targetOrg != null ? targetOrg.getParentId() : null);
            recordProjectionChange(tenantId, "user_role", userRoleId, "BIND",
                new Long[]{subjectId}, new Long[0]);
        }

        log.info("Projected user create: userId={}, subjectId={}", user.getId(), subjectId);
        return new UserCreateResp(user.getId(), initialPassword);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "ACCESS", action = "USER_UPDATE", targetType = "sys_user",
        targetId = "#req.id()", summary = "'update user ' + #req.id()")
    public void updateUser(UserUpdateReq req) {
        Long currentUserId = currentOperatorId();
        if (!req.id().equals(currentUserId)) {
            permissionValidator.checkInstanceLevel(
                ResourceTypeCode.USER, String.valueOf(req.id()), AdminOperationCode.UPDATE);
        }
        Long tenantId = TenantContextHolder.getTenantId();
        SysUser user = userDomainService.selectValidById(tenantId, req.id());
        if (user == null) {
            throw new BizException(AdminErrorCode.USER_NOT_FOUND.getCode(),
                AdminErrorCode.USER_NOT_FOUND.getMessage());
        }
        if (req.phone() != null && !req.phone().equals(user.getPhone())
            && userDomainService.existsByPhone(tenantId, req.phone())) {
            throw new BizException(AdminErrorCode.PHONE_ALREADY_EXISTS.getCode(),
                AdminErrorCode.PHONE_ALREADY_EXISTS.getMessage());
        }
        // 可选字段仅更新提供的字段（null 跳过，保留原值）。
        // 内存对象保持旧值 → 投影 upsert 使用事实最新值，管理事实与投影一致。
        if (req.name() != null) {
            user.setName(req.name());
        }
        if (req.phone() != null) {
            user.setPhone(req.phone());
        }
        if (req.email() != null) {
            user.setEmail(req.email());
        }
        if (req.status() != null) {
            user.setStatus(req.status());
        }
        user.setUpdatedAt(LocalDateTime.now());
        userDomainService.update(user);

        Long abstractUserId = localProjectionDomainService.upsertAdminUser(
            tenantId, user.getId(), user.getName(), isEnabled(user.getStatus()), extraUsername(user.getUsername()));
        recordProjectionChange(tenantId, "abstract_user", abstractUserId, "UPSERT",
            new Long[]{abstractUserId}, new Long[0]);
        PermissionChangeContext.markUsers(tenantId, Set.of(abstractUserId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "ACCESS", action = "USER_LOCK", targetType = "sys_user",
        targetId = "#userId", summary = "'lock user ' + #userId")
    public void lockUser(Long tenantId, Long userId) {
        // 无权限门禁：登录失败自动锁定（匿名上下文无操作者）；写事实 + 投影同一事务
        userDomainService.batchUpdateStatus(tenantId, List.of(userId), 2);
        Long abstractUserId = localProjectionDomainService.findAdminUserId(tenantId, userId);
        localProjectionDomainService.disableAdminUser(tenantId, userId);
        if (abstractUserId != null) {
            auditDomainService.recordChangeLog(
                new AuditDomainService.ChangeLogContext(
                    tenantId, null, null, PermConstants.MaintainSource.MANUAL, "login-lock"),
                List.of(new AuditDomainService.ChangeLogEntry(
                    "abstract_user", abstractUserId, "DISABLE", null, null, null,
                    new Long[]{abstractUserId}, new Long[0])));
            PermissionChangeContext.markUsers(tenantId, Set.of(abstractUserId));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "ACCESS", action = "USER_DELETE", targetType = "sys_user",
        targetId = "", summary = "'batch delete users'")
    public void deleteUser(IdsReq req) {
        Long tenantId = TenantContextHolder.getTenantId();
        long currentUserId = currentOperatorId();
        for (Long id : req.ids()) {
            if (id.equals(currentUserId)) {
                throw new BizException(AdminErrorCode.CANNOT_DELETE_SELF.getCode(),
                    AdminErrorCode.CANNOT_DELETE_SELF.getMessage());
            }
        }
        permissionValidator.checkBatchInstanceLevel(
            ResourceTypeCode.USER,
            req.ids().stream().map(String::valueOf).collect(Collectors.toList()),
            AdminOperationCode.DELETE);
        validateUsersInDefaultTreeScope(tenantId, Set.copyOf(req.ids()));

        List<SysUser> users = userDomainService.selectValidByIds(tenantId, Set.copyOf(req.ids()));
        List<SysUserOrg> allUserOrgs = userOrgDomainService.findByUserIds(tenantId, req.ids());
        Set<Long> orgIds = allUserOrgs.stream().map(SysUserOrg::getOrgId).collect(Collectors.toSet());
        Map<Long, SysOrg> orgMap = orgIds.isEmpty()
            ? Map.of()
            : orgDomainService.batchSelectValidByIdsMap(tenantId, orgIds);

        // 批量解绑（一次批量加载 + 一次批量软删），替代循环单条 unbind N+1；
        // POSITION 成员 relation 指向所属组织（岗位的 parentId）
        List<LocalProjectionDomainService.UserOrgBindKey> unbindKeys = new java.util.ArrayList<>();
        for (SysUserOrg uo : allUserOrgs) {
            SysOrg org = orgMap.get(uo.getOrgId());
            if (org == null) {
                log.warn("Org not found when deleting user, skip unbind: userId={}, orgId={}",
                    uo.getUserId(), uo.getOrgId());
                continue;
            }
            unbindKeys.add(new LocalProjectionDomainService.UserOrgBindKey(
                uo.getUserId(), uo.getOrgId(), resolveOrgRoleTypeCode(org), org.getParentId()));
        }
        localProjectionDomainService.batchUnbindUserOrg(tenantId, unbindKeys);

        // 批量删除用户组织关系 + 批量软删用户（单条 SQL，替代循环单条删除）
        java.util.Set<Long> userIdSet = users.stream().map(SysUser::getId).collect(Collectors.toSet());
        userOrgDomainService.deleteByUserIds(tenantId, userIdSet);
        userDomainService.softDeleteBatch(tenantId, req.ids());

        // 批量解析 abstract_user.id + 批量软删投影（替代循环 deleteAdminUser）
        java.util.LinkedHashSet<Long> abstractUserIds = new java.util.LinkedHashSet<>();
        Map<Long, Long> abstractIdBySysId = localProjectionDomainService.batchFindAdminUserIds(tenantId, userIdSet);
        localProjectionDomainService.batchDeleteAdminUsers(tenantId, userIdSet);
        // 全部变更日志一次提交（单条 insertBatch，替代每用户一次 recordChangeLog 的 N 次写）
        java.util.List<AuditDomainService.ChangeLogEntry> deleteEntries = new java.util.ArrayList<>();
        for (SysUser user : users) {
            Long abstractUserId = abstractIdBySysId.get(user.getId());
            if (abstractUserId != null) {
                abstractUserIds.add(abstractUserId);
            }
            // entityId 用投影主键；投影缺失时记 null（不再冒用 sys_user.id）
            deleteEntries.add(new AuditDomainService.ChangeLogEntry(
                "abstract_user", abstractUserId, "DELETE", null, null, null,
                abstractUserId == null ? new Long[]{} : new Long[]{abstractUserId}, new Long[0]));
        }
        if (!deleteEntries.isEmpty()) {
            auditDomainService.recordChangeLog(
                new AuditDomainService.ChangeLogContext(
                    tenantId, currentOperatorId(), null, PermConstants.MaintainSource.MANUAL, "local-projection"),
                deleteEntries);
        }
        if (!abstractUserIds.isEmpty()) {
            PermissionChangeContext.markUsers(tenantId, abstractUserIds);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "ACCESS", action = "USER_ENABLE", targetType = "sys_user",
        targetId = "", summary = "'enable/disable users'")
    public void updateStatus(UserUpdateStatusReq req) {
        if (req.status() == null || (req.status() != 0 && req.status() != 1)) {
            throw new BizException(AdminErrorCode.INVALID_PARAM.getCode(), "状态值无效，必须为0(禁用)或1(启用)");
        }
        if (req.status() == 0) {
            long currentUserId = currentOperatorId();
            for (Long id : req.ids()) {
                if (id.equals(currentUserId)) {
                    throw new BizException(AdminErrorCode.CANNOT_DISABLE_SELF.getCode(),
                        AdminErrorCode.CANNOT_DISABLE_SELF.getMessage());
                }
            }
        }
        permissionValidator.checkBatchInstanceLevel(
            ResourceTypeCode.USER,
            req.ids().stream().map(String::valueOf).collect(Collectors.toList()),
            AdminOperationCode.ENABLE);
        Long tenantId = TenantContextHolder.getTenantId();
        validateUsersInDefaultTreeScope(tenantId, Set.copyOf(req.ids()));

        List<SysUser> existingUsers = userDomainService.selectValidByIds(tenantId, Set.copyOf(req.ids()));
        Set<Long> validIds = existingUsers.stream().map(SysUser::getId).collect(Collectors.toSet());
        if (validIds.isEmpty()) {
            return;
        }
        userDomainService.batchUpdateStatus(tenantId, List.copyOf(validIds), req.status());
        boolean enabled = req.status() == 1;
        // 批量投影 upsert/disable（单条 SQL 级），替代循环单条 upsertAdminUser/disableAdminUser
        Map<Long, Long> abstractIdBySysId;
        if (enabled) {
            List<LocalProjectionDomainService.UpsertUserKey> upsertKeys = existingUsers.stream()
                .map(u -> new LocalProjectionDomainService.UpsertUserKey(
                    u.getId(), u.getName(), true, extraUsername(u.getUsername())))
                .collect(Collectors.toList());
            abstractIdBySysId = localProjectionDomainService.batchUpsertAdminUsers(tenantId, upsertKeys);
        } else {
            abstractIdBySysId = localProjectionDomainService.batchFindAdminUserIds(tenantId, validIds);
            localProjectionDomainService.batchDisableAdminUsers(tenantId, validIds);
        }
        // 全部变更日志一次提交（单条 insertBatch，替代每用户一次 recordChangeLog 的 N 次写）
        java.util.List<AuditDomainService.ChangeLogEntry> statusEntries = new java.util.ArrayList<>();
        for (SysUser user : existingUsers) {
            user.setStatus(req.status());
            Long abstractUserId = abstractIdBySysId.get(user.getId());
            if (abstractUserId != null) {
                PermissionChangeContext.markUsers(tenantId, Set.of(abstractUserId));
            }
            // entityId 用投影主键；投影缺失时记 null（不再冒用 sys_user.id）
            statusEntries.add(new AuditDomainService.ChangeLogEntry(
                "abstract_user", abstractUserId, enabled ? "UPSERT" : "DISABLE", null, null, null,
                new Long[]{}, new Long[0]));
        }
        if (!statusEntries.isEmpty()) {
            auditDomainService.recordChangeLog(
                new AuditDomainService.ChangeLogContext(
                    tenantId, currentOperatorId(), null, PermConstants.MaintainSource.MANUAL, "local-projection"),
                statusEntries);
        }
    }

    private void recordProjectionChange(Long tenantId, String entityType, Long entityId, String operation,
                                        Long[] affectedUsers, Long[] affectedRoles) {
        auditDomainService.recordChangeLog(
            new AuditDomainService.ChangeLogContext(
                tenantId, currentOperatorId(), null, PermConstants.MaintainSource.MANUAL, "local-projection"),
            List.of(new AuditDomainService.ChangeLogEntry(
                entityType, entityId, operation, null, null, null, affectedUsers, affectedRoles)));
    }

    private void validateOrgInDefaultTree(Long tenantId, Long orgId) {
        List<SysOrgTreeConfig> defaultConfigs = orgTreeConfigDomainService.findDefaultConfigs(tenantId);
        if (defaultConfigs.isEmpty()) {
            throw new BizException(AdminErrorCode.ORG_NOT_IN_DEFAULT_TREE.getCode(),
                AdminErrorCode.ORG_NOT_IN_DEFAULT_TREE.getMessage());
        }
        Long rootOrgId = defaultConfigs.get(0).getRootOrgId();
        List<Long> subtreeIds = orgDomainService.getDescendantIdsIncludingSelf(tenantId, rootOrgId);
        if (!subtreeIds.contains(orgId)) {
            throw new BizException(AdminErrorCode.ORG_NOT_IN_DEFAULT_TREE.getCode(),
                AdminErrorCode.ORG_NOT_IN_DEFAULT_TREE.getMessage());
        }
    }

    private void validateUsersInDefaultTreeScope(Long tenantId, Set<Long> userIds) {
        List<SysOrgTreeConfig> defaultConfigs = orgTreeConfigDomainService.findDefaultConfigs(tenantId);
        if (defaultConfigs.isEmpty()) {
            return;
        }
        Long operatorId = currentOperatorId();
        Set<Long> visibleOrgIds = orgVisibilityQueryService.getOperatorVisibleDefaultTreeOrgIds(tenantId, operatorId);
        if (visibleOrgIds.isEmpty()) {
            throw new BizException(AdminErrorCode.USER_NOT_IN_OPERATOR_VISIBLE_SCOPE.getCode(),
                AdminErrorCode.USER_NOT_IN_OPERATOR_VISIBLE_SCOPE.getMessage());
        }
        List<SysUserOrg> visibleUserOrgs = userOrgDomainService.findByOrgIds(
            tenantId, List.copyOf(visibleOrgIds));
        Set<Long> usersInVisibleScope = visibleUserOrgs.stream()
            .map(SysUserOrg::getUserId)
            .collect(Collectors.toSet());
        for (Long userId : userIds) {
            if (!usersInVisibleScope.contains(userId)) {
                throw new BizException(AdminErrorCode.USER_NOT_IN_OPERATOR_VISIBLE_SCOPE.getCode(),
                    AdminErrorCode.USER_NOT_IN_OPERATOR_VISIBLE_SCOPE.getMessage());
            }
        }
    }

    private static String resolveOrgRoleTypeCode(SysOrg org) {
        if (org == null) {
            return "ORG";
        }
        String orgType = org.getOrgType();
        if ("2".equals(orgType) || "POSITION".equalsIgnoreCase(orgType)) {
            return "POSITION";
        }
        return "ORG";
    }

    private String extraUsername(String username) {
        try {
            return objectMapper.writeValueAsString(Map.of("username", username == null ? "" : username));
        } catch (JsonProcessingException e) {
            throw new SystemException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(),
                "serialize user extra failed", e);
        }
    }

    private static boolean isEnabled(Integer status) {
        return status != null && status == 1;
    }

    private static Long currentOperatorId() {
        Long operatorId = AccessRequestContext.getOperatorId();
        if (operatorId != null) {
            return operatorId;
        }
        return StpUtil.getLoginIdAsLong();
    }

    private static String generateRandomPassword() {
        String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lower = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String special = "!@#$%^&*";
        String allChars = upper + lower + digits + special;
        StringBuilder password = new StringBuilder();
        password.append(upper.charAt(SECURE_RANDOM.nextInt(upper.length())));
        password.append(lower.charAt(SECURE_RANDOM.nextInt(lower.length())));
        password.append(digits.charAt(SECURE_RANDOM.nextInt(digits.length())));
        password.append(special.charAt(SECURE_RANDOM.nextInt(special.length())));
        for (int i = 4; i < 12; i++) {
            password.append(allChars.charAt(SECURE_RANDOM.nextInt(allChars.length())));
        }
        return password.toString();
    }
}
