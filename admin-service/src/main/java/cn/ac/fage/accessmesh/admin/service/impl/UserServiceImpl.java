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
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.admin.entity.table.SysUserTableDef.SYS_USER;
import static cn.ac.fage.accessmesh.admin.entity.table.SysUserOrgTableDef.SYS_USER_ORG;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SysUserMapper userMapper;
    private final SysUserOrgMapper userOrgMapper;
    private final UserDomainService userDomainService;
    private final UserSyncHandler userSyncHandler;
    private final TransactionTemplate transactionTemplate;
    private final SyncRetryService syncRetryService;
    private final ObjectMapper objectMapper;
    private final AdminPermissionValidator permissionValidator;

    // TODO: 构造函数依赖过多(8个)，建议抽离同步和重试逻辑到独立服务
    // 优先级：P3（低优先级，可关注但不强制整改）
    public UserServiceImpl(SysUserMapper userMapper, SysUserOrgMapper userOrgMapper,
                           UserDomainService userDomainService, UserSyncHandler userSyncHandler,
                           TransactionTemplate transactionTemplate, SyncRetryService syncRetryService,
                           ObjectMapper objectMapper, AdminPermissionValidator permissionValidator) {
        this.userMapper = userMapper;
        this.userOrgMapper = userOrgMapper;
        this.userDomainService = userDomainService;
        this.userSyncHandler = userSyncHandler;
        this.transactionTemplate = transactionTemplate;
        this.syncRetryService = syncRetryService;
        this.objectMapper = objectMapper;
        this.permissionValidator = permissionValidator;
    }

    @Override
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

        // DB操作在事务内
        transactionTemplate.executeWithoutResult(status -> {
            userMapper.insert(user);
        });

        // Feign调用在事务外 - 同步到权限中心
        try {
            Long permUserId = userSyncHandler.syncUserToPermissionCenter(tenantId, user);
            if (permUserId != null) {
                user.setPermUserId(permUserId);
                userMapper.update(user);
                log.info("User synced to permission-center: userId={}, permUserId={}", user.getId(), permUserId);
            }
        } catch (Exception e) {
            log.warn("Failed to sync user to permission-center: userId={}, error={}", user.getId(), e.getMessage());
            // 记录到 SysSyncRetry 表待重试
            try {
                String payload = objectMapper.writeValueAsString(Map.of(
                    "userId", user.getId(),
                    "username", user.getUsername(),
                    "tenantId", tenantId
                ));
                syncRetryService.recordSyncFailure(
                    "user-sync-" + user.getId(),
                    "permission-center",
                    "user",
                    String.valueOf(user.getId()),
                    "sync",
                    payload,
                    e.getMessage()
                );
            } catch (Exception jsonEx) {
                log.error("Failed to record sync retry: {}", jsonEx.getMessage());
            }
        }

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

        // 同步更新到权限中心
        if (user.getPermUserId() != null) {
            userSyncHandler.syncUserToPermissionCenter(tenantId, user);
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

        // 批量获取用户，先删除权限中心数据
        List<SysUser> users = userDomainService.selectValidByIds(tenantId, Set.copyOf(req.ids()));
        for (SysUser user : users) {
            if (user.getPermUserId() != null) {
                userSyncHandler.deleteUserFromPermissionCenter(tenantId, user.getPermUserId());
            }
        }

        // 使用 DomainService 批量软删除（解决 N+1 问题）
        userDomainService.softDeleteBatch(tenantId, req.ids());
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
            .where(SYS_USER.TENANT_ID.eq(tenantId))
            .and(SYS_USER.DELETE_FLAG.eq(0));

        if (req.username() != null) qw.and(SYS_USER.USERNAME.like(req.username()));
        if (req.name() != null) qw.and(SYS_USER.NAME.like(req.name()));
        if (req.phone() != null) qw.and(SYS_USER.PHONE.eq(req.phone()));
        if (req.email() != null) qw.and(SYS_USER.EMAIL.eq(req.email()));
        if (req.status() != null) qw.and(SYS_USER.STATUS.eq(req.status()));

        qw.orderBy(SYS_USER.CREATED_AT.desc());

        Page<SysUser> page = Page.of(req.getPageNum(), req.getPageSize());
        Page<SysUser> result = userMapper.paginate(page, qw);

        // 批量获取用户组织关联，避免 N+1
        Set<Long> userIds = result.getRecords().stream()
            .map(SysUser::getId)
            .collect(Collectors.toSet());

        // 批量查询用户组织关系
        List<SysUserOrg> allUserOrgs = userOrgMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SYS_USER_ORG.USER_ID.in(userIds))
                .and(SYS_USER_ORG.DELETE_FLAG.eq(0))
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
    @Transactional
    public BatchResultResp batchCreateUsers(UserBatchCreateReq req) {
        Long tenantId = TenantContextHolder.getTenantId();
        List<Long> successIds = new ArrayList<>();
        List<String> failedMessages = new ArrayList<>();

        for (UserCreateReq userReq : req.users()) {
            try {
                // 检查用户名重复
                if (userDomainService.existsByUsername(tenantId, userReq.username())) {
                    failedMessages.add("用户名已存在: " + userReq.username());
                    continue;
                }

                // 检查手机号重复
                if (userReq.phone() != null && userDomainService.existsByPhone(tenantId, userReq.phone())) {
                    failedMessages.add("手机号已存在: " + userReq.phone());
                    continue;
                }

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
                user.setCreatedAt(LocalDateTime.now());
                user.setUpdatedAt(LocalDateTime.now());
                user.setDeleteFlag(0L);
                userMapper.insert(user);

                // 同步到权限中心
                userSyncHandler.syncUserToPermissionCenter(tenantId, user);

                successIds.add(user.getId());
            } catch (Exception e) {
                log.error("Failed to create user: username={}", userReq.username(), e);
                failedMessages.add("创建失败: " + userReq.username() + " - " + e.getMessage());
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
                .where(SYS_USER_ORG.TENANT_ID.eq(tenantId))
                .and(SYS_USER_ORG.USER_ID.eq(userId))
                .and(SYS_USER_ORG.DELETE_FLAG.eq(0))
        ).stream()
            .map(uo -> new UserResp.OrgBrief(uo.getOrgId(), null, null, Boolean.TRUE.equals(uo.getIsPrimary())))
            .collect(Collectors.toList());
    }

    /**
     * 生成随机强密码（12位，包含大小写字母、数字和特殊字符）
     * 使用 SecureRandom 确保密码学安全。
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