package cn.ac.fage.accessmesh.access.admin.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLog;
import cn.ac.fage.accessmesh.access.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.MemberCandidatesReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.UserCreateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.UserPageReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.UserUpdateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.UserUpdateStatusReq;
import cn.ac.fage.accessmesh.access.admin.dto.resp.MemberCandidateItemResp;
import cn.ac.fage.accessmesh.access.admin.dto.resp.ResetPasswordResp;
import cn.ac.fage.accessmesh.access.admin.dto.resp.UserCreateResp;
import cn.ac.fage.accessmesh.access.admin.dto.resp.UserPageItemResp;
import cn.ac.fage.accessmesh.access.admin.dto.resp.UserResp;
import cn.ac.fage.accessmesh.access.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.access.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.access.admin.entity.SysUser;
import cn.ac.fage.accessmesh.access.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.access.admin.mapper.SysUserMapper;
import cn.ac.fage.accessmesh.access.admin.mapper.SysUserOrgMapper;
import cn.ac.fage.accessmesh.access.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.access.admin.service.UserService;
import cn.ac.fage.accessmesh.access.application.UserWriteAppService;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.application.query.OrgVisibilityQueryService;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
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
 * 写操作委托 {@code UserWriteAppService} 同一事务维护本地权限投影（管理事实、投影与
 * permission_change_log 同一事务，不再有跨服务同步任务）。
 * 用户修改自己的信息无需权限校验，其他操作需要相应权限。
 * 使用BCrypt进行密码哈希，SecureRandom生成随机密码。
 *
 * @implNote v1.4 起所有读接口（{@link #getUser}、{@link #pageUsers}）必须经过
 *           {@code permissionValidator.checkTypeLevel(USER, VIEW)} 门禁。
 *           前端隐藏不是安全边界，禁止在新增读接口时省略。
 *           契约依据：{@code docs/design/org-user-permission-contract.md} v1.4 §4 B 区。
 *
 * @see docs/design/default-org-tree-user-lifecycle.md
 * </p>
 */
@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SysUserMapper userMapper;
    private final SysUserOrgMapper userOrgMapper;
    private final UserDomainService userDomainService;
    private final UserOrgDomainService userOrgDomainService;
    private final OrgTreeConfigDomainService orgTreeConfigDomainService;
    private final OrgDomainService orgDomainService;
    private final UserWriteAppService userWriteAppService;
    private final AdminPermissionValidator permissionValidator;
    private final OrgVisibilityQueryService orgVisibilityQueryService;

    /**
     * 构造函数注入依赖
     *
     * @param userMapper 用户数据访问Mapper
     * @param userOrgMapper 用户组织关联Mapper
     * @param userDomainService 用户领域服务，处理用户数据查询和批量操作
     * @param userOrgDomainService 用户组织关联领域服务，处理组织分配
     * @param orgTreeConfigDomainService 组织树配置领域服务，校验默认树归属
     * @param orgDomainService 组织领域服务，校验组织是否属于默认树
     * @param permissionValidator 权限校验器，校验用户操作权限
     */
    public UserServiceImpl(SysUserMapper userMapper, SysUserOrgMapper userOrgMapper,
                           UserDomainService userDomainService,
                           UserOrgDomainService userOrgDomainService,
                           OrgTreeConfigDomainService orgTreeConfigDomainService,
                           OrgDomainService orgDomainService,
                           UserWriteAppService userWriteAppService,
                           AdminPermissionValidator permissionValidator,
                           OrgVisibilityQueryService orgVisibilityQueryService) {
        this.userMapper = userMapper;
        this.userOrgMapper = userOrgMapper;
        this.userDomainService = userDomainService;
        this.userOrgDomainService = userOrgDomainService;
        this.orgTreeConfigDomainService = orgTreeConfigDomainService;
        this.orgDomainService = orgDomainService;
        this.userWriteAppService = userWriteAppService;
        this.permissionValidator = permissionValidator;
        this.orgVisibilityQueryService = orgVisibilityQueryService;
    }

    /**
     * 创建用户
     * <p>
     * 创建新用户并生成随机初始密码（BCrypt哈希存储）。
     * 支持创建时一步完成默认组织树分配（orgId）。
     * 校验用户名和手机号唯一性；权限投影（abstract_user + ADMIN_USER 资源 + 成员关系）
     * 由 {@code UserWriteAppService} 同一事务维护。
     * </p>
     *
     * @param req 用户创建请求，包含用户名、姓名、手机号、邮箱、可选orgId等
     * @return 用户创建响应，包含用户ID和初始密码
     * @throws BizException 用户名已存在、手机号已存在等
     */
    @Override
    public UserCreateResp createUser(UserCreateReq req) {
        return userWriteAppService.createUser(req);
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
    public void updateUser(UserUpdateReq req) {
        userWriteAppService.updateUser(req);
    }

    /**
     * 批量删除用户（高危身份目录操作）
     * <p>
     * 软删除多个用户，不允许删除自己。
     * 执行批量实例级权限校验 + 默认树边界二次校验。
     * 先记录每条 user-org 的 UNBIND 同步任务，再本地软删除，最后入队 DELETE 同步。
     * <p>
     * 契约依据：{@code docs/design/services/admin-service-api-contract.md} §4.1.5
     *
     * @param req ID集合请求，包含待删除的用户ID列表
     * @throws BizException 不能删除自己、不在默认树可管范围、同步任务记录失败等
     */
    @Override
    public void deleteUser(IdsReq req) {
        userWriteAppService.deleteUser(req);
    }

    /**
     * 批量启用/禁用用户（启停一体，高危生命周期操作）
     * <p>
     * 根据请求中的 status 字段批量启用或禁用用户账号。
     * status=1 启用，status=0 禁用。
     * 执行批量实例级权限校验 + 默认树边界二次校验。
     * 不允许禁用操作者本人。
     * <p>
     * 契约依据：{@code docs/design/services/admin-service-api-contract.md} §4.1.6
     *
     * @param req 用户状态变更请求，包含用户ID列表和目标状态
     * @throws BizException 状态参数无效、不能禁用自己、不在默认树范围、同步任务记录失败等
     */
    @Override
    public void updateStatus(UserUpdateStatusReq req) {
        userWriteAppService.updateStatus(req);
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
        // v1.4 类型级 VIEW 门禁：前端隐藏不是安全边界
        permissionValidator.checkTypeLevel(AdminResourceType.USER, AdminOperationCode.VIEW);

        Long tenantId = TenantContextHolder.getTenantId();

        // P1-2 修复：组织可见性裁剪。pageUsers 已按 OrgVisibilityQueryService 限制可见用户，
        // getUser 须复用同等范围校验，否则知道 ID 即可读列表不可见范围内的用户（越权读取）。
        // 决策：拒绝读取无组织关系的用户（正常不会有此类用户）。
        validateUsersInDefaultTreeScope(tenantId, Set.of(id));

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
     * 分页查询用户列表（默认组织树身份目录视角）
     * <p>
     * 支持按用户名、姓名、手机号、邮箱、状态过滤。
     * 批量查询用户组织关联避免N+1问题。
     * 按创建时间倒序排列。
     * <p>
     * v1.4 契约对齐：
     * <ul>
     *   <li>语义收敛为"默认组织树身份目录查询"；组织成员列表和添加成员候选集需独立接口</li>
     *   <li>{@code orgId} 非空时必须属于默认组织树，否则抛 {@code BizException(ORG_NOT_IN_DEFAULT_TREE)}</li>
     *   <li>OrgBrief 填充 orgName/orgType（便于前端区分组织与岗位）</li>
     * </ul>
     *
     * @param req 分页查询请求，包含分页参数和过滤条件
     * @return 分页用户列表结果
     */
    @Override
    public PaginatedResult<UserPageItemResp> pageUsers(UserPageReq req) {
        // v1.4 类型级 VIEW 门禁：前端隐藏不是安全边界
        permissionValidator.checkTypeLevel(AdminResourceType.USER, AdminOperationCode.VIEW);

        Long tenantId = TenantContextHolder.getTenantId();

        int pageNum = req.getPageNum();
        int pageSize = req.getPageSize();
        Set<Long> orgIds = null;
        if (req.orgId() != null) {
            // 契约 §4.1.1：orgId 必须属于默认组织树（本接口只服务身份目录视图）
            validateOrgInDefaultTree(tenantId, req.orgId());

            // EXT-3 修复：验证操作者对该 orgId 有 ADMIN_ORG:VIEW 权限
            Long operatorId = StpUtil.getLoginIdAsLong();
            Set<Long> visibleOrgIds = orgVisibilityQueryService.getOperatorVisibleDefaultTreeOrgIds(tenantId, operatorId);
            if (!visibleOrgIds.contains(req.orgId())) {
                throw new BizException(AdminErrorCode.ORG_NOT_FOUND.getCode(),
                    AdminErrorCode.ORG_NOT_FOUND.getMessage());
            }

            List<Long> subtreeIds = orgDomainService.getDescendantIdsIncludingSelf(tenantId, req.orgId());
            // 裁剪子树到操作者可见范围
            Set<Long> visibleSubtree = subtreeIds.stream()
                .filter(visibleOrgIds::contains)
                .collect(Collectors.toSet());
            if (visibleSubtree.isEmpty()) {
                return new PaginatedResult<>(
                    List.of(),
                    new PaginatedResult.PaginationMeta(0, pageNum, pageSize, 0)
                );
            }
            orgIds = visibleSubtree;
        } else {
            // EXT-3 修复：orgId 为空时也按操作者可见默认树裁剪，不再返回全量
            Long operatorId = StpUtil.getLoginIdAsLong();
            Set<Long> visibleOrgIds = orgVisibilityQueryService.getOperatorVisibleDefaultTreeOrgIds(tenantId, operatorId);
            if (visibleOrgIds.isEmpty()) {
                return new PaginatedResult<>(
                    List.of(),
                    new PaginatedResult.PaginationMeta(0, pageNum, pageSize, 0)
                );
            }
            orgIds = visibleOrgIds;
        }

        Page<SysUser> page = Page.of(pageNum, pageSize);
        Page<SysUser> result = userMapper.paginateUsers(page, tenantId,
            req.username(), req.name(), req.phone(), req.email(), req.status(), orgIds);

        // 批量获取用户组织关联，避免 N+1
        Set<Long> userIds = result.getRecords().stream()
            .map(SysUser::getId)
            .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return new PaginatedResult<>(
                List.of(),
                new PaginatedResult.PaginationMeta(result.getTotalRow(), pageNum, pageSize, 0)
            );
        }

        // 批量查询用户组织关系
        List<SysUserOrg> allUserOrgs = userOrgMapper.selectByUserIdsAndTenant(tenantId, List.copyOf(userIds));

        // 收集所有 orgId，批量查询 org 信息以填充 orgName/orgType
        Set<Long> allOrgIds = allUserOrgs.stream()
            .map(SysUserOrg::getOrgId)
            .collect(Collectors.toSet());
        Map<Long, SysOrg> orgMap = allOrgIds.isEmpty()
            ? Map.of()
            : orgDomainService.batchSelectValidByIdsMap(tenantId, allOrgIds);

        // 按 userId 分组
        Map<Long, List<SysUserOrg>> userOrgMap = allUserOrgs.stream()
            .collect(Collectors.groupingBy(SysUserOrg::getUserId));

        List<UserPageItemResp> items = result.getRecords().stream()
            .map(u -> {
                List<SysUserOrg> userOrgs = userOrgMap.getOrDefault(u.getId(), List.of());
                List<UserPageItemResp.OrgBrief> orgs = userOrgs.stream()
                    .map(uo -> {
                        SysOrg org = orgMap.get(uo.getOrgId());
                        return new UserPageItemResp.OrgBrief(
                            uo.getOrgId(),
                            org != null ? org.getName() : null,
                            org != null ? org.getOrgType() : null,
                            Boolean.TRUE.equals(uo.getIsPrimary())
                        );
                    })
                    .collect(Collectors.toList());
                return new UserPageItemResp(
                    u.getId(), u.getUsername(), u.getName(), u.getPhone(),
                    u.getEmail(), u.getStatus(), orgs, u.getCreatedAt()
                );
            })
            .collect(Collectors.toList());

        long totalPages = (result.getTotalRow() + pageSize - 1) / pageSize;
        return new PaginatedResult<>(
            items,
            new PaginatedResult.PaginationMeta(result.getTotalRow(), pageNum, pageSize, (int) totalPages)
        );
    }

    /**
     * 重置用户密码（高危生命周期操作）
     * <p>
     * 管理员重置用户密码，需默认树边界二次校验。
     * 用户重置自己的密码无需权限校验（自我修改豁免）。
     * 如果 newPassword 为空，系统自动生成随机密码。
     * 使用BCrypt哈希后更新，响应中返回生效的密码明文。
     * <p>
     * 契约依据：{@code docs/design/services/admin-service-api-contract.md} §4.1.7
     *
     * @param userId      用户ID
     * @param newPassword 新密码（可为空，空时自动生成）
     * @return 重置密码响应，包含生效的密码
     * @throws BizException 用户不存在、不在默认树范围
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "ADMIN", action = "USER_PASSWORD_RESET", targetType = "sys_user",
        targetId = "#userId", summary = "'reset password for user ' + #userId")
    public ResetPasswordResp resetPassword(Long userId, String newPassword) {
        Long currentUserId = StpUtil.getLoginIdAsLong();

        // 自我修改豁免：用户可重置自己的密码无需权限检查
        if (!userId.equals(currentUserId)) {
            permissionValidator.checkInstanceLevel(
                AdminResourceType.USER,
                String.valueOf(userId),
                AdminOperationCode.RESET_PASSWORD
            );

            // 默认树边界二次校验（非自我修改时）
            Long tenantId = TenantContextHolder.getTenantId();
            validateUsersInDefaultTreeScope(tenantId, Set.of(userId));
        }

        Long tenantId = TenantContextHolder.getTenantId();

        // 使用 DomainService 获取用户
        SysUser user = userDomainService.selectValidById(tenantId, userId);
        if (user == null) {
            throw new BizException(AdminErrorCode.USER_NOT_FOUND.getCode(), AdminErrorCode.USER_NOT_FOUND.getMessage());
        }

        // 如果未指定新密码，自动生成随机密码
        String effectivePassword = (newPassword != null && !newPassword.isBlank())
            ? newPassword
            : generateRandomPassword();

        user.setPassword(BCrypt.hashpw(effectivePassword));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.update(user);

        return new ResetPasswordResp(effectivePassword);
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

    // ==================== 候选用户查询（§4.1.2） ====================

    /**
     * 查询候选用户（添加组织/岗位成员时使用）。
     * <p>
     * 候选范围 = 默认组织树中操作者可见 ∩ 排除目标组织已有成员。
     * 门禁：ADMIN_ORG:UPDATE@targetOrgId（校验能管理目标组织成员）。
     * <p>
     * 契约依据：{@code docs/design/services/admin-service-api-contract.md} §4.1.2
     *
     * @param req 候选用户查询请求（含 targetOrgId）
     * @return 分页候选用户列表
     */
    @Override
    public PaginatedResult<MemberCandidateItemResp> memberCandidates(MemberCandidatesReq req) {
        Long tenantId = TenantContextHolder.getTenantId();

        // 门禁：ADMIN_ORG:UPDATE@targetOrgId（校验能管理目标组织成员）
        permissionValidator.checkInstanceLevel(
            AdminResourceType.ORG,
            String.valueOf(req.targetOrgId()),
            AdminOperationCode.UPDATE
        );

        // 校验目标组织存在
        SysOrg targetOrg = orgDomainService.selectValidById(tenantId, req.targetOrgId());
        if (targetOrg == null) {
            throw new BizException(AdminErrorCode.ORG_NOT_FOUND.getCode(),
                AdminErrorCode.ORG_NOT_FOUND.getMessage());
        }

        // 1. 确定默认组织树中操作者可见的组织范围（P1-D 修复：按 ADMIN_ORG:VIEW 裁剪）
        Long operatorId = StpUtil.getLoginIdAsLong();
        Set<Long> visibleOrgIds = orgVisibilityQueryService.getOperatorVisibleDefaultTreeOrgIds(tenantId, operatorId);
        if (visibleOrgIds.isEmpty()) {
            return emptyMemberCandidates(req);
        }

        // 2. 收集默认树可见范围内的用户 ID
        Set<Long> defaultTreeOrgIdSet = visibleOrgIds;
        List<SysUserOrg> defaultTreeUserOrgs = userOrgMapper.selectByOrgIdsAndTenant(
            tenantId, List.copyOf(defaultTreeOrgIdSet));
        Set<Long> candidateUserIds = defaultTreeUserOrgs.stream()
            .map(SysUserOrg::getUserId)
            .collect(Collectors.toSet());
        if (candidateUserIds.isEmpty()) {
            return emptyMemberCandidates(req);
        }

        // 3. 排除目标组织已有成员
        List<SysUserOrg> targetOrgUserOrgs = userOrgMapper.selectByOrgIdsAndTenant(
            tenantId, List.of(req.targetOrgId()));
        Set<Long> existingMemberIds = targetOrgUserOrgs.stream()
            .map(SysUserOrg::getUserId)
            .collect(Collectors.toSet());
        candidateUserIds.removeAll(existingMemberIds);
        if (candidateUserIds.isEmpty()) {
            return emptyMemberCandidates(req);
        }

        // 4. 分页查询候选用户
        int pageNum = req.getPageNum();
        int pageSize = req.getPageSize();
        Page<SysUser> page = Page.of(pageNum, pageSize);
        Page<SysUser> result = userMapper.paginateUsersByIdsAndKeyword(
            page, tenantId, List.copyOf(candidateUserIds), req.keyword());

        // 5. 批量获取用户的主组织名（默认树主归属）
        Set<Long> resultUserIds = result.getRecords().stream()
            .map(SysUser::getId)
            .collect(Collectors.toSet());
        if (resultUserIds.isEmpty()) {
            return emptyMemberCandidates(req);
        }

        // 查用户主组织关系
        List<SysUserOrg> primaryOrgs = userOrgMapper.selectByUserIdsAndTenant(tenantId, List.copyOf(resultUserIds));
        // 过滤主组织 & 在默认树范围内
        Map<Long, Long> userPrimaryOrgIdMap = primaryOrgs.stream()
            .filter(uo -> Boolean.TRUE.equals(uo.getIsPrimary()) && defaultTreeOrgIdSet.contains(uo.getOrgId()))
            .collect(Collectors.toMap(SysUserOrg::getUserId, SysUserOrg::getOrgId, (a, b) -> a));
        // 批量查组织名
        Map<Long, SysOrg> primaryOrgMap = userPrimaryOrgIdMap.isEmpty()
            ? Map.of()
            : orgDomainService.batchSelectValidByIdsMap(tenantId, new java.util.HashSet<>(userPrimaryOrgIdMap.values()));

        List<MemberCandidateItemResp> items = result.getRecords().stream()
            .map(u -> {
                Long primaryOrgId = userPrimaryOrgIdMap.get(u.getId());
                SysOrg primaryOrg = primaryOrgId != null ? primaryOrgMap.get(primaryOrgId) : null;
                return new MemberCandidateItemResp(
                    u.getId(),
                    u.getUsername(),
                    u.getName(),
                    null, // avatar 暂不填充
                    primaryOrg != null ? primaryOrg.getName() : null,
                    false // 服务端已过滤，固定 false
                );
            })
            .collect(Collectors.toList());

        long totalPages = (result.getTotalRow() + pageSize - 1) / pageSize;
        return new PaginatedResult<>(
            items,
            new PaginatedResult.PaginationMeta(result.getTotalRow(), pageNum, pageSize, (int) totalPages)
        );
    }

    private PaginatedResult<MemberCandidateItemResp> emptyMemberCandidates(MemberCandidatesReq req) {
        return new PaginatedResult<>(
            List.of(),
            new PaginatedResult.PaginationMeta(0, req.getPageNum(), req.getPageSize(), 0)
        );
    }

    // ==================== 辅助方法 ====================

    /**
     * 校验组织是否属于默认组织树。不属于时抛 {@code BizException(ORG_NOT_IN_DEFAULT_TREE)}。
     */
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

    /**
     * 从 SysOrg.orgType 推导权限中心角色类型码（ORG / POSITION）。
     * 与跨域查询服务（UserRoleQueryServiceImpl）的组织/岗位角色类型映射同语义。
     */
    private String resolveOrgRoleTypeCode(SysOrg org) {
        if (org == null) return "ORG";
        String orgType = org.getOrgType();
        if ("2".equals(orgType) || "POSITION".equalsIgnoreCase(orgType)) return "POSITION";
        return "ORG";
    }

    /**
     * 默认树边界 + 操作者可见范围二次校验（EXT-4 修复）。
     * <p>
     * 验证每个目标用户在操作者 ADMIN_ORG:VIEW 可见的默认树组织范围内有至少一个归属关系。
     * 若任一用户不在操作者可见范围内，整批操作拒绝。
     * <p>
     * 契约依据：{@code docs/design/services/admin-service-api-contract.md} §2 门禁规范
     *
     * @param tenantId   租户 ID
     * @param userIds    目标用户 ID 集合
     * @throws BizException 任一用户不在操作者可见范围
     */
    private void validateUsersInDefaultTreeScope(Long tenantId, Set<Long> userIds) {
        List<SysOrgTreeConfig> defaultConfigs = orgTreeConfigDomainService.findDefaultConfigs(tenantId);
        if (defaultConfigs.isEmpty()) {
            // 无默认树配置，无法验证，放行（由门禁层兜底）
            return;
        }

        // EXT-4 修复：使用操作者可见范围替代全量默认树后代
        Long operatorId = StpUtil.getLoginIdAsLong();
        Set<Long> visibleOrgIds = orgVisibilityQueryService.getOperatorVisibleDefaultTreeOrgIds(tenantId, operatorId);
        if (visibleOrgIds.isEmpty()) {
            throw new BizException(AdminErrorCode.USER_NOT_IN_OPERATOR_VISIBLE_SCOPE.getCode(),
                AdminErrorCode.USER_NOT_IN_OPERATOR_VISIBLE_SCOPE.getMessage());
        }

        // 查操作者可见范围内的用户组织关系
        List<SysUserOrg> visibleUserOrgs = userOrgMapper.selectByOrgIdsAndTenant(
            tenantId, List.copyOf(visibleOrgIds));
        Set<Long> usersInVisibleScope = visibleUserOrgs.stream()
            .map(SysUserOrg::getUserId)
            .collect(Collectors.toSet());

        // 任一目标用户不在操作者可见范围内则拒绝
        for (Long userId : userIds) {
            if (!usersInVisibleScope.contains(userId)) {
                throw new BizException(AdminErrorCode.USER_NOT_IN_OPERATOR_VISIBLE_SCOPE.getCode(),
                    AdminErrorCode.USER_NOT_IN_OPERATOR_VISIBLE_SCOPE.getMessage());
            }
        }
    }
}
