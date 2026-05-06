package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.UserBatchCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.UserCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.UserPageReq;
import cn.ac.fage.accessmesh.admin.dto.req.UserUpdateReq;
import cn.ac.fage.accessmesh.admin.dto.resp.BatchResultResp;
import cn.ac.fage.accessmesh.admin.dto.resp.UserPageItemResp;
import cn.ac.fage.accessmesh.admin.dto.resp.UserResp;
import cn.ac.fage.accessmesh.admin.entity.SysUser;
import cn.ac.fage.accessmesh.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.mapper.SysUserMapper;
import cn.ac.fage.accessmesh.admin.mapper.SysUserOrgMapper;
import cn.ac.fage.accessmesh.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.admin.service.SyncRetryService;
import cn.ac.fage.accessmesh.admin.service.UserService;
import cn.ac.fage.accessmesh.admin.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.admin.service.domain.UserSyncHandler;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import cn.ac.fage.accessmesh.admin.entity.table.SysUserTableDef;
import cn.ac.fage.accessmesh.admin.entity.table.SysUserOrgTableDef;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SysUserMapper userMapper;
    private final SysUserOrgMapper userOrgMapper;
    private final UserDomainService userDomainService;
    private final UserSyncHandler userSyncHandler;
    private final SyncRetryService syncRetryService;
    private final ObjectMapper objectMapper;
    private final AdminPermissionValidator permissionValidator;

    // TODO: 构造函数依赖过多(7个)，建议抽离同步和重试逻辑到独立服务
    // 优先级：P3（低优先级，可关注但不强制整改）
    public UserServiceImpl(SysUserMapper userMapper, SysUserOrgMapper userOrgMapper,
                           UserDomainService userDomainService, UserSyncHandler userSyncHandler,
                           SyncRetryService syncRetryService,
                           ObjectMapper objectMapper, AdminPermissionValidator permissionValidator) {
        this.userMapper = userMapper;
        this.userOrgMapper = userOrgMapper;
        this.userDomainService = userDomainService;
        this.userSyncHandler = userSyncHandler;
        this.syncRetryService = syncRetryService;
        this.objectMapper = objectMapper;
        this.permissionValidator = permissionValidator;
    }

    @Override
    @Transactional
    public Long createUser(UserCreateReq req) {
        // Permission check - type-level CREATE
        permissionValidator.checkTypeLevel(AdminResourceType.USER, AdminOperationCode.CREATE);

        Long tenantId = TenantContextHolder.getTenantId();

        // 使用 DomainService 检查用户名重复
        if (userDomainService.existsByUsername(tenantId, req.username())) {
            throw new BizException(AdminErrorCode.USER_ALREADY_EXISTS.getCode(), AdminErrorCode.USER_ALREADY_EXISTS.getMessage());
        }

        // 使用 DomainService 检查手机号重复
        if (req.phone() != null && userDomainService.existsByPhone(tenantId, req.phone())) {
            throw new BizException(AdminErrorCode.PHONE_ALREADY_EXISTS.getCode(), AdminErrorCode.PHONE_ALREADY_EXISTS.getMessage());
        }

        SysUser user = new SysUser();
        user.setTenantId(tenantId);
        user.setUsername(req.username());
        user.setName(req.name());
        user.setPhone(req.phone());
        user.setEmail(req.email());
        String initialPassword = generateRandomPassword();
        user.setPassword(BCrypt.hashpw(initialPassword));
        log.info("Generated initial password for user: username={}, userId={}", req.username(), user.getId());
        user.setStatus(req.status() != null ? req.status() : 1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setDeleteFlag(0L);

        // 事务内：插入用户 + 记录同步任务（原子性，Outbox Pattern）
        userMapper.insert(user);

        // 同一事务内记录同步任务，确保用户创建与任务记录原子性
        String payload = objectMapper.writeValueAsString(Map.of(
            "userId", user.getId(),
            "username", user.getUsername(),
            "tenantId", tenantId
        ));
        syncRetryService.recordSyncFailure(
            "user:create:" + user.getId(),
            "permission-center",
            "abstract_user",
            String.valueOf(user.getId()),
            "create",
            payload,
            null
        );
        log.info("Recorded sync task for user creation: userId={}", user.getId());

        return user.getId();
    }

    @Override
    @Transactional
    public void updateUser(UserUpdateReq req) {
        Long currentUserId = StpUtil.getLoginIdAsLong();

        // Self-modification exemption: user can update own info without permission check
        if (!req.id().equals(currentUserId)) {
            permissionValidator.checkInstanceLevel(
                AdminResourceType.USER,
                String.valueOf(req.id()),
                AdminOperationCode.UPDATE
            );
        }

        Long tenantId = TenantContextHolder.getTenantId();

        // 使用 DomainService 获取用户
        SysUser user = userDomainService.selectValidById(tenantId, req.id());
        if (user == null) {
            throw new BizException(AdminErrorCode.USER_NOT_FOUND.getCode(), AdminErrorCode.USER_NOT_FOUND.getMessage());
        }

        // 如果修改了手机号，检查新手机号是否重复
        if (req.phone() != null && !req.phone().equals(user.getPhone())) {
            if (userDomainService.existsByPhone(tenantId, req.phone())) {
                throw new BizException(AdminErrorCode.PHONE_ALREADY_EXISTS.getCode(), AdminErrorCode.PHONE_ALREADY_EXISTS.getMessage());
            }
        }

        user.setName(req.name());
        user.setPhone(req.phone());
        user.setEmail(req.email());
        user.setStatus(req.status());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.update(user);

        // TODO: 跨服务数据一致性改进 - 更新操作改为异步同步
        // 同步更新到权限中心 - 记录同步任务
        if (user.getPermUserId() != null) {
            String payload = objectMapper.writeValueAsString(Map.of(
                "permUserId", user.getPermUserId(),
                "name", user.getName(),
                "phone", user.getPhone(),
                "email", user.getEmail(),
                "status", user.getStatus(),
                "enabled", user.getStatus() != null && user.getStatus() == 1
            ));
            syncRetryService.recordSyncFailure(
                "user:update:" + user.getId(),
                "permission-center",
                "abstract_user",
                String.valueOf(user.getPermUserId()),
                "update",
                payload,
                null
            );
            log.info("Recorded update sync task for user: userId={}", user.getId());
        }
    }

    @Override
    @Transactional
    public void deleteUser(IdsReq req) {
        Long tenantId = TenantContextHolder.getTenantId();
        long currentUserId = StpUtil.getLoginIdAsLong();

        // Check cannot delete self (business rule)
        for (Long id : req.ids()) {
            if (id.equals(currentUserId)) {
                throw new BizException(AdminErrorCode.CANNOT_DELETE_SELF.getCode(), AdminErrorCode.CANNOT_DELETE_SELF.getMessage());
            }
        }

        // Permission check - batch instance-level DELETE
        List<String> resourceCodes = req.ids().stream()
            .map(String::valueOf)
            .collect(Collectors.toList());
        permissionValidator.checkBatchInstanceLevel(AdminResourceType.USER, resourceCodes, AdminOperationCode.DELETE);

        // TODO: 跨服务数据一致性改进
        // 当前采用"先本地软删除，后记录同步任务"模式，确保本地数据优先删除
        // 建议：完整方案应使用消息队列 + 补偿机制，参见 plan/architecture.md 分布式事务章节
        // 优先级：P1（架构债务）

        // 批量获取用户
        List<SysUser> users = userDomainService.selectValidByIds(tenantId, Set.copyOf(req.ids()));

        // 1. 先执行本地软删除
        userDomainService.softDeleteBatch(tenantId, req.ids());

        // 2. 记录删除同步任务
        for (SysUser user : users) {
            syncRetryService.recordSyncFailure(
                "user:delete:" + user.getId(),
                "permission-center",
                "abstract_user",
                String.valueOf(user.getId()),
                "delete",
                null,
                null
            );
            log.info("Recorded delete sync task for user: userId={}", user.getId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enableUser(IdsReq req) {
        // Permission check - batch instance-level ENABLE
        List<String> resourceCodes = req.ids().stream()
            .map(String::valueOf)
            .collect(Collectors.toList());
        permissionValidator.checkBatchInstanceLevel(AdminResourceType.USER, resourceCodes, AdminOperationCode.ENABLE);

        Long tenantId = TenantContextHolder.getTenantId();

        // 使用批量查询验证有效ID（避免N+1问题）
        List<SysUser> existingUsers = userDomainService.selectValidByIds(tenantId, Set.copyOf(req.ids()));
        Set<Long> validIds = existingUsers.stream().map(SysUser::getId).collect(Collectors.toSet());

        if (!validIds.isEmpty()) {
            userDomainService.batchUpdateStatus(tenantId, List.copyOf(validIds), 1);

            // TODO: 跨服务数据一致性改进 - 状态变更需同步到权限中心
            // 同步启用状态到权限中心 - 记录同步任务
            for (SysUser user : existingUsers) {
                if (user.getPermUserId() != null) {
                    String payload = objectMapper.writeValueAsString(Map.of(
                        "permUserId", user.getPermUserId(),
                        "enabled", true
                    ));
                    syncRetryService.recordSyncFailure(
                        "user:enable:" + user.getId(),
                        "permission-center",
                        "abstract_user",
                        String.valueOf(user.getPermUserId()),
                        "update",
                        payload,
                        null
                    );
                    log.info("Recorded enable sync task for user: userId={}", user.getId());
                }
            }
        }
    }

    @Override
    public UserResp getUser(Long id) {
        Long tenantId = TenantContextHolder.getTenantId();

        // 使用 DomainService 获取用户
        SysUser user = userDomainService.selectValidById(tenantId, id);
        if (user == null) {
            throw new BizException(AdminErrorCode.USER_NOT_FOUND.getCode(), AdminErrorCode.USER_NOT_FOUND.getMessage());
        }

        List<UserResp.OrgBrief> orgs = getUserOrgs(id);
        return new UserResp(
            user.getId(), user.getUsername(), user.getName(), user.getPhone(),
            user.getEmail(), user.getStatus(), orgs, user.getCreatedAt(), user.getUpdatedAt()
        );
    }

    @Override
    public PaginatedResult<UserPageItemResp> pageUsers(UserPageReq req) {
        // FIX #4: Add tenantId filter for security
        Long tenantId = TenantContextHolder.getTenantId();
        QueryWrapper qw = QueryWrapper.create()
            .where(SysUserTableDef.SYS_USER.TENANT_ID.eq(tenantId))
            .and(SysUserTableDef.SYS_USER.DELETE_FLAG.eq(0));

        if (req.username() != null) qw.and(SysUserTableDef.SYS_USER.USERNAME.like(req.username()));
        if (req.name() != null) qw.and(SysUserTableDef.SYS_USER.NAME.like(req.name()));
        if (req.phone() != null) qw.and(SysUserTableDef.SYS_USER.PHONE.eq(req.phone()));
        if (req.email() != null) qw.and(SysUserTableDef.SYS_USER.EMAIL.eq(req.email()));
        if (req.status() != null) qw.and(SysUserTableDef.SYS_USER.STATUS.eq(req.status()));

        qw.orderBy(SysUserTableDef.SYS_USER.CREATED_AT.desc());

        Page<SysUser> page = Page.of(req.getPageNum(), req.getPageSize());
        Page<SysUser> result = userMapper.paginate(page, qw);

        // 批量获取用户组织关联，避免 N+1
        Set<Long> userIds = result.getRecords().stream()
            .map(SysUser::getId)
            .collect(Collectors.toSet());

        // 批量查询用户组织关系
        List<SysUserOrg> allUserOrgs = userOrgMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SysUserOrgTableDef.SYS_USER_ORG.USER_ID.in(userIds))
                .and(SysUserOrgTableDef.SYS_USER_ORG.DELETE_FLAG.eq(0))
        );

        // 按 userId 分组
        Map<Long, List<SysUserOrg>> userOrgMap = allUserOrgs.stream()
            .collect(Collectors.groupingBy(SysUserOrg::getUserId));

        List<UserPageItemResp> items = result.getRecords().stream()
            .map(u -> {
                List<SysUserOrg> userOrgs = userOrgMap.getOrDefault(u.getId(), List.of());
                List<UserPageItemResp.OrgBrief> orgs = userOrgs.stream()
                    .map(uo -> new UserPageItemResp.OrgBrief(uo.getOrgId(), null, null, Boolean.TRUE.equals(uo.getIsPrimary())))
                    .collect(Collectors.toList());
                return new UserPageItemResp(
                    u.getId(), u.getUsername(), u.getName(), u.getPhone(),
                    u.getEmail(), u.getStatus(), orgs, u.getCreatedAt()
                );
            })
            .collect(Collectors.toList());

        long totalPages = (result.getTotalRow() + req.pageSize() - 1) / req.pageSize();
        return new PaginatedResult<>(
            items,
            new PaginatedResult.PaginationMeta(result.getTotalRow(), req.pageNum(), req.pageSize(), (int) totalPages)
        );
    }

    @Override
    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        Long currentUserId = StpUtil.getLoginIdAsLong();

        // Self-modification exemption: user can reset own password without permission check
        if (!userId.equals(currentUserId)) {
            permissionValidator.checkInstanceLevel(
                AdminResourceType.USER,
                String.valueOf(userId),
                AdminOperationCode.RESET_PASSWORD
            );
        }

        Long tenantId = TenantContextHolder.getTenantId();

        // 使用 DomainService 获取用户
        SysUser user = userDomainService.selectValidById(tenantId, userId);
        if (user == null) {
            throw new BizException(AdminErrorCode.USER_NOT_FOUND.getCode(), AdminErrorCode.USER_NOT_FOUND.getMessage());
        }

        user.setPassword(BCrypt.hashpw(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.update(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchResultResp batchCreateUsers(UserBatchCreateReq req) {
        // TODO: 跨服务数据一致性风险
        // 问题：本地事务与远程 Feign 调用无法协调，可能导致数据不一致
        // 建议：采用"本地事务 + 异步同步 + 补偿机制"模式
        // 参考：plan/architecture.md 分布式事务章节
        // 优先级：P1（架构债务）

        Long tenantId = TenantContextHolder.getTenantId();
        List<Long> successIds = new ArrayList<>();
        List<String> failedMessages = new ArrayList<>();

        // 1. 批量收集所有用户名和手机号
        Set<String> allUsernames = req.users().stream()
            .map(UserCreateReq::username)
            .collect(Collectors.toSet());
        Set<String> allPhones = req.users().stream()
            .map(UserCreateReq::phone)
            .filter(p -> p != null && !p.isBlank())
            .collect(Collectors.toSet());

        // 2. 批量查询已存在的用户名和手机号（优化：2次数据库查询替代N次）
        Set<String> existingUsernames = userDomainService.findExistingUsernames(tenantId, allUsernames);
        Set<String> existingPhones = userDomainService.findExistingPhones(tenantId, allPhones);

        // 3. 构建待插入的用户列表
        List<SysUser> usersToInsert = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (UserCreateReq userReq : req.users()) {
            // 检查用户名重复（使用批量查询结果）
            if (existingUsernames.contains(userReq.username())) {
                failedMessages.add("用户名已存在: " + userReq.username());
                continue;
            }
            // 检查手机号重复（使用批量查询结果）
            if (userReq.phone() != null && !userReq.phone().isBlank() && existingPhones.contains(userReq.phone())) {
                failedMessages.add("手机号已存在: " + userReq.phone());
                continue;
            }

            // 构建用户实体
            SysUser user = new SysUser();
            user.setTenantId(tenantId);
            user.setUsername(userReq.username());
            user.setName(userReq.name());
            user.setPhone(userReq.phone());
            user.setEmail(userReq.email());
            String initialPassword = generateRandomPassword();
            user.setPassword(BCrypt.hashpw(initialPassword));
            log.info("Generated initial password for batch user: username={}", userReq.username());
            user.setStatus(userReq.status() != null ? userReq.status() : 1);
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            user.setDeleteFlag(0L);
            usersToInsert.add(user);
        }

        // 4. 批量插入（优化：1次数据库操作替代N次）
        if (!usersToInsert.isEmpty()) {
            userDomainService.insertBatch(usersToInsert);

            // 5. 记录同步任务（Outbox Pattern：确保原子性）
            for (SysUser user : usersToInsert) {
                try {
                    String payload = objectMapper.writeValueAsString(Map.of(
                        "userId", user.getId(),
                        "username", user.getUsername(),
                        "tenantId", tenantId
                    ));
                    syncRetryService.recordSyncFailure(
                        "user:create:" + user.getId(),
                        "permission-center",
                        "abstract_user",
                        String.valueOf(user.getId()),
                        "create",
                        payload,
                        null
                    );
                    successIds.add(user.getId());
                } catch (Exception syncEx) {
                    log.error("Failed to record sync task for batch user creation: userId={}, error={}",
                        user.getId(), syncEx.getMessage());
                    failedMessages.add("同步任务记录失败: " + user.getUsername());
                }
            }
        }

        return BatchResultResp.partial(req.users().size(), successIds.size(), successIds, failedMessages);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disableUser(IdsReq req) {
        // Permission check - batch instance-level DISABLE
        List<String> resourceCodes = req.ids().stream()
            .map(String::valueOf)
            .collect(Collectors.toList());
        permissionValidator.checkBatchInstanceLevel(AdminResourceType.USER, resourceCodes, AdminOperationCode.DISABLE);

        Long tenantId = TenantContextHolder.getTenantId();

        // 使用批量查询验证有效ID（避免N+1问题）
        List<SysUser> existingUsers = userDomainService.selectValidByIds(tenantId, Set.copyOf(req.ids()));
        Set<Long> validIds = existingUsers.stream().map(SysUser::getId).collect(Collectors.toSet());

        if (!validIds.isEmpty()) {
            userDomainService.batchUpdateStatus(tenantId, List.copyOf(validIds), 0);

            // TODO: 跨服务数据一致性改进 - 状态变更需同步到权限中心
            // 同步禁用状态到权限中心 - 记录同步任务
            for (SysUser user : existingUsers) {
                if (user.getPermUserId() != null) {
                    String payload = objectMapper.writeValueAsString(Map.of(
                        "permUserId", user.getPermUserId(),
                        "enabled", false
                    ));
                    syncRetryService.recordSyncFailure(
                        "user:disable:" + user.getId(),
                        "permission-center",
                        "abstract_user",
                        String.valueOf(user.getPermUserId()),
                        "update",
                        payload,
                        null
                    );
                    log.info("Recorded disable sync task for user: userId={}", user.getId());
                }
            }
        }
    }

    @Override
    @Transactional
    public void batchResetPassword(IdsReq req, String newPassword) {
        // Permission check - batch instance-level RESET_PASSWORD
        List<String> resourceCodes = req.ids().stream()
            .map(String::valueOf)
            .collect(Collectors.toList());
        permissionValidator.checkBatchInstanceLevel(AdminResourceType.USER, resourceCodes, AdminOperationCode.RESET_PASSWORD);

        Long tenantId = TenantContextHolder.getTenantId();

        List<SysUser> users = userDomainService.selectValidByIds(tenantId, Set.copyOf(req.ids()));
        if (users.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        String hashedPassword = BCrypt.hashpw(newPassword);

        List<Long> userIds = users.stream().map(SysUser::getId).toList();
        userMapper.batchUpdatePassword(tenantId, userIds, hashedPassword, now);
    }

    private List<UserResp.OrgBrief> getUserOrgs(Long userId) {
        // FIX #5: Add tenantId filter for security
        Long tenantId = TenantContextHolder.getTenantId();
        return userOrgMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SysUserOrgTableDef.SYS_USER_ORG.TENANT_ID.eq(tenantId))
                .and(SysUserOrgTableDef.SYS_USER_ORG.USER_ID.eq(userId))
                .and(SysUserOrgTableDef.SYS_USER_ORG.DELETE_FLAG.eq(0))
        ).stream()
            .map(uo -> new UserResp.OrgBrief(uo.getOrgId(), null, null, Boolean.TRUE.equals(uo.getIsPrimary())))
            .collect(Collectors.toList());
    }

    /**
     * 生成随机强密码（12位，包含大小写字母、数字和特殊字符）
     * 使用 SecureRandom 硋保密码学安全。
     */
    private String generateRandomPassword() {
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