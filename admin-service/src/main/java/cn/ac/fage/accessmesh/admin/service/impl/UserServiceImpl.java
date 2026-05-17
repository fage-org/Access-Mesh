package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.UserCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.UserPageReq;
import cn.ac.fage.accessmesh.admin.dto.req.UserUpdateReq;
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
 * 用户管理服务实现类
 * <p>
 * 提供用户的CRUD操作、批量操作、密码重置、状态管理等功能。
 * 实现跨服务数据同步机制，通过Outbox Pattern确保用户创建与同步任务记录原子性。
 * 用户修改自己的信息无需权限校验，其他操作需要相应权限。
 * 使用BCrypt进行密码哈希，SecureRandom生成随机密码。
 * </p>
 */
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

    /**
     * 构造函数注入依赖
     * <p>
     * 注意：构造函数依赖较多(7个)，建议后续重构抽离同步和重试逻辑到独立服务。
     * </p>
     *
     * @param userMapper 用户数据访问Mapper
     * @param userOrgMapper 用户组织关联Mapper
     * @param userDomainService 用户领域服务，处理用户数据查询和批量操作
     * @param userSyncHandler 用户同步处理器，同步用户数据到permission-center
     * @param syncRetryService 同步重试服务，记录同步失败任务
     * @param objectMapper JSON序列化工具
     * @param permissionValidator 权限校验器，校验用户操作权限
     */
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

    /**
     * 创建用户
     * <p>
     * 创建新用户并生成随机初始密码（BCrypt哈希存储）。
     * 同一事务内记录同步任务（Outbox Pattern），确保原子性。
     * 校验用户名和手机号唯一性。
     * </p>
     *
     * @param req 用户创建请求，包含用户名、姓名、手机号、邮箱等
     * @return 新用户ID
     * @throws BizException 用户名已存在、手机号已存在、同步任务记录失败等
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createUser(UserCreateReq req) {
        // 权限检查 — 类型级 CREATE
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
            log.info("Recorded sync task for user creation: userId={}", user.getId());
        } catch (Exception e) {
            log.error("Failed to record sync task for user creation: userId={}, error={}", user.getId(), e.getMessage());
            throw new BizException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(), "用户同步任务记录失败");
        }

        return user.getId();
    }

    /**
     * 更新用户信息
     * <p>
     * 更新用户的姓名、手机号、邮箱、状态等信息。
     * 用户修改自己的信息无需权限校验（自我修改豁免）。
     * 修改手机号时校验新手机号唯一性。
     * 如果用户已同步到permission-center，记录更新同步任务。
     * </p>
     *
     * @param req 用户更新请求，包含用户ID和新属性值
     * @throws BizException 用户不存在、手机号已存在、同步任务记录失败等
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(UserUpdateReq req) {
        Long currentUserId = StpUtil.getLoginIdAsLong();

        // 自我修改豁免：用户可更新自己的信息无需权限检查
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

        // 同步更新到权限中心 - 记录同步任务
        if (user.getPermUserId() != null) {
            try {
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
            } catch (Exception e) {
                log.error("Failed to record update sync task for user: userId={}, error={}", user.getId(), e.getMessage());
                throw new BizException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(), "用户同步任务记录失败");
            }
        }
    }

    /**
     * 批量删除用户
     * <p>
     * 软删除多个用户，不允许删除自己。
     * 执行批量实例级权限校验，先本地软删除再记录同步任务。
     * </p>
     *
     * @param req ID集合请求，包含待删除的用户ID列表
     * @throws BizException 不能删除自己、用户不存在、同步任务记录失败等
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(IdsReq req) {
        Long tenantId = TenantContextHolder.getTenantId();
        long currentUserId = StpUtil.getLoginIdAsLong();

        // 检查不能删除自己（业务规则）
        for (Long id : req.ids()) {
            if (id.equals(currentUserId)) {
                throw new BizException(AdminErrorCode.CANNOT_DELETE_SELF.getCode(), AdminErrorCode.CANNOT_DELETE_SELF.getMessage());
            }
        }

        // 权限检查 — 批量实例级 DELETE
        List<String> resourceCodes = req.ids().stream()
            .map(String::valueOf)
            .collect(Collectors.toList());
        permissionValidator.checkBatchInstanceLevel(AdminResourceType.USER, resourceCodes, AdminOperationCode.DELETE);

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

    /**
     * 批量启用用户
     * <p>
     * 将多个用户状态设置为启用(1)。
     * 执行批量实例级权限校验，更新后记录同步任务到permission-center。
     * </p>
     *
     * @param req ID集合请求，包含待启用的用户ID列表
     * @throws BizException 用户不存在、同步任务记录失败等
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enableUser(IdsReq req) {
        // 权限检查 — 批量实例级 ENABLE
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

            // 同步启用状态到权限中心 - 记录同步任务
            for (SysUser user : existingUsers) {
                if (user.getPermUserId() != null) {
                    try {
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
                    } catch (Exception e) {
                        log.error("Failed to record enable sync task for user: userId={}, error={}", user.getId(), e.getMessage());
                        throw new BizException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(), "用户同步任务记录失败");
                    }
                }
            }
        }
    }

    /**
     * 获取用户详情
     * <p>
     * 根据用户ID查询用户完整信息，包括关联的组织列表。
     * </p>
     *
     * @param id 用户ID
     * @return 用户详情响应
     * @throws BizException 用户不存在
     */
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

    /**
     * 分页查询用户列表
     * <p>
     * 支持按用户名、姓名、手机号、邮箱、状态过滤。
     * 批量查询用户组织关联避免N+1问题。
     * 按创建时间倒序排列。
     * </p>
     *
     * @param req 分页查询请求，包含分页参数和过滤条件
     * @return 分页用户列表结果
     */
    @Override
    public PaginatedResult<UserPageItemResp> pageUsers(UserPageReq req) {
        Long tenantId = TenantContextHolder.getTenantId();

        Page<SysUser> page = Page.of(req.getPageNum(), req.getPageSize());
        Page<SysUser> result = userMapper.paginateUsers(page, tenantId,
            req.username(), req.name(), req.phone(), req.email(), req.status());

        // 批量获取用户组织关联，避免 N+1
        Set<Long> userIds = result.getRecords().stream()
            .map(SysUser::getId)
            .collect(Collectors.toSet());

        // 批量查询用户组织关系
        List<SysUserOrg> allUserOrgs = userOrgMapper.selectByUserIdsAndTenant(tenantId, List.copyOf(userIds));

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

    /**
     * 重置用户密码
     * <p>
     * 用户重置自己的密码无需权限校验（自我修改豁免）。
     * 使用BCrypt哈希新密码后更新。
     * </p>
     *
     * @param userId 用户ID
     * @param newPassword 新密码（明文）
     * @throws BizException 用户不存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long userId, String newPassword) {
        Long currentUserId = StpUtil.getLoginIdAsLong();

        // 自我修改豁免：用户可重置自己的密码无需权限检查
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

    /**
     * 获取用户关联的组织列表
     * <p>
     * 查询用户关联的所有组织，标记主组织。
     * </p>
     *
     * @param userId 用户ID
     * @return 组织简要信息列表
     */
    private List<UserResp.OrgBrief> getUserOrgs(Long userId) {
        Long tenantId = TenantContextHolder.getTenantId();
        return userOrgMapper.selectByUserIdAndTenant(tenantId, userId).stream()
            .map(uo -> new UserResp.OrgBrief(uo.getOrgId(), null, null, Boolean.TRUE.equals(uo.getIsPrimary())))
            .collect(Collectors.toList());
    }

    /**
     * 生成随机强密码
     * <p>
     * 生成12位密码，包含大小写字母、数字和特殊字符。
     * 使用SecureRandom确保密码学安全。
     * </p>
     *
     * @return 随机密码字符串
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