package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.MemberCandidatesReq;
import cn.ac.fage.accessmesh.admin.dto.req.UserCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.UserPageReq;
import cn.ac.fage.accessmesh.admin.dto.req.UserUpdateReq;
import cn.ac.fage.accessmesh.admin.dto.req.UserUpdateStatusReq;
import cn.ac.fage.accessmesh.admin.dto.resp.MemberCandidateItemResp;
import cn.ac.fage.accessmesh.admin.dto.resp.ResetPasswordResp;
import cn.ac.fage.accessmesh.admin.dto.resp.UserCreateResp;
import cn.ac.fage.accessmesh.admin.dto.resp.UserPageItemResp;
import cn.ac.fage.accessmesh.admin.dto.resp.UserResp;
import cn.ac.fage.accessmesh.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.admin.entity.SysOrgTreeConfig;
import cn.ac.fage.accessmesh.admin.entity.SysUser;
import cn.ac.fage.accessmesh.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.mapper.SysUserMapper;
import cn.ac.fage.accessmesh.admin.mapper.SysUserOrgMapper;
import cn.ac.fage.accessmesh.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.admin.service.SyncTaskDomainService;
import cn.ac.fage.accessmesh.admin.service.UserService;
import cn.ac.fage.accessmesh.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.admin.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.admin.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.admin.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.admin.support.UserOrgKeys;
import cn.ac.fage.accessmesh.admin.sync.SyncTaskBuilder;
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
 * 实现跨服务数据同步机制，通过Outbox Pattern确保用户创建与同步任务记录原子性。
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
    private final SyncTaskDomainService syncTaskDomainService;
    private final SyncTaskBuilder syncTaskBuilder;
    private final AdminPermissionValidator permissionValidator;

    /**
     * 构造函数注入依赖
     *
     * @param userMapper 用户数据访问Mapper
     * @param userOrgMapper 用户组织关联Mapper
     * @param userDomainService 用户领域服务，处理用户数据查询和批量操作
     * @param userOrgDomainService 用户组织关联领域服务，处理组织分配
     * @param orgTreeConfigDomainService 组织树配置领域服务，校验默认树归属
     * @param orgDomainService 组织领域服务，校验组织是否属于默认树
     * @param syncTaskDomainService 同步任务领域服务，S4 任务生产器写入 sys_sync_task（Outbox Pattern）
     * @param syncTaskBuilder 同步任务 envelope 构造器
     * @param permissionValidator 权限校验器，校验用户操作权限
     */
    public UserServiceImpl(SysUserMapper userMapper, SysUserOrgMapper userOrgMapper,
                           UserDomainService userDomainService,
                           UserOrgDomainService userOrgDomainService,
                           OrgTreeConfigDomainService orgTreeConfigDomainService,
                           OrgDomainService orgDomainService,
                           SyncTaskDomainService syncTaskDomainService,
                           SyncTaskBuilder syncTaskBuilder,
                           AdminPermissionValidator permissionValidator) {
        this.userMapper = userMapper;
        this.userOrgMapper = userOrgMapper;
        this.userDomainService = userDomainService;
        this.userOrgDomainService = userOrgDomainService;
        this.orgTreeConfigDomainService = orgTreeConfigDomainService;
        this.orgDomainService = orgDomainService;
        this.syncTaskDomainService = syncTaskDomainService;
        this.syncTaskBuilder = syncTaskBuilder;
        this.permissionValidator = permissionValidator;
    }

    /**
     * 创建用户
     * <p>
     * 创建新用户并生成随机初始密码（BCrypt哈希存储）。
     * 支持创建时一步完成默认组织树分配（orgId）。
     * 同一事务内记录同步任务（Outbox Pattern），确保原子性。
     * 校验用户名和手机号唯一性。
     * 目标同步闭环要求同时落地 abstract_user 和 ADMIN_USER resource_entity，
     * 均使用业务键定位；当前实现仅记录 abstract_user 同步任务，后续需补齐用户管理资源同步。
     * </p>
     *
     * @param req 用户创建请求，包含用户名、姓名、手机号、邮箱、可选orgId等
     * @return 用户创建响应，包含用户ID和初始密码
     * @throws BizException 用户名已存在、手机号已存在、同步任务记录失败等
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserCreateResp createUser(UserCreateReq req) {
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

        // 同一事务内入队同步任务（abstract_user + ADMIN_USER resource_entity 双 envelope）
        syncTaskDomainService.enqueueAll(tenantId, syncTaskBuilder.userUpsert(user));
        log.info("Enqueued user upsert sync envelopes: userId={}", user.getId());

        // 创建用户是身份目录操作，orgId 必须属于默认组织树。
        if (req.orgId() != null) {
            // 校验组织是否属于默认组织树
            List<SysOrgTreeConfig> defaultConfigs = orgTreeConfigDomainService.findDefaultConfigs(tenantId);
            boolean belongsToDefaultTree = false;
            if (!defaultConfigs.isEmpty()) {
                Long rootOrgId = defaultConfigs.get(0).getRootOrgId();
                List<Long> subtreeIds = orgDomainService.getDescendantIdsIncludingSelf(tenantId, rootOrgId);
                belongsToDefaultTree = subtreeIds.contains(req.orgId());
            }
            if (!belongsToDefaultTree) {
                throw new BizException(AdminErrorCode.ORG_NOT_IN_DEFAULT_TREE.getCode(),
                    AdminErrorCode.ORG_NOT_IN_DEFAULT_TREE.getMessage());
            }

            // 带 orgId 时还需 ADMIN_ORG:UPDATE@orgId 门禁
            permissionValidator.checkInstanceLevel(
                AdminResourceType.ORG,
                String.valueOf(req.orgId()),
                AdminOperationCode.UPDATE
            );

            SysUserOrg userOrg = new SysUserOrg();
            userOrg.setTenantId(tenantId);
            userOrg.setUserId(user.getId());
            userOrg.setOrgId(req.orgId());
            userOrg.setIsPrimary(req.primaryOrg() != null ? req.primaryOrg() : true);
            userOrg.setCreatedAt(LocalDateTime.now());
            userOrg.setUpdatedAt(LocalDateTime.now());
            userOrg.setDeleteFlag(0L);
            userOrgDomainService.insertBatch(List.of(userOrg));
            log.info("Assigned user to org on creation: userId={}, orgId={}, isPrimary={}",
                user.getId(), req.orgId(), userOrg.getIsPrimary());

            // 契约 §4.1.3 同步动作 4：带 orgId 时入队 PERM_USER_ROLE_SYNC (BIND)
            SysOrg targetOrg = orgDomainService.selectValidById(tenantId, req.orgId());
            String roleTypeCode = resolveOrgRoleTypeCode(targetOrg);
            String relationKey = UserOrgKeys.relationKey(req.orgId());
            // 组织树根 externalId：通过 resolver 精确解析，避免多默认树 / 岗位场景错配
            String treeRootExternalId = orgTreeConfigDomainService.resolveTreeRootExternalId(
                tenantId, req.orgId());
            syncTaskDomainService.enqueueAll(tenantId,
                List.of(syncTaskBuilder.userOrgBind(
                    user.getId(),
                    req.orgId(),
                    roleTypeCode,
                    relationKey,
                    treeRootExternalId
                )));
            log.info("Enqueued user-org bind sync: userId={}, orgId={}, roleTypeCode={}",
                user.getId(), req.orgId(), roleTypeCode);
        }

        return new UserCreateResp(user.getId(), initialPassword);
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

        // 同步更新到权限中心 — 事务内入队 abstract_user + ADMIN_USER resource_entity 双 envelope
        syncTaskDomainService.enqueueAll(tenantId, syncTaskBuilder.userUpsert(user));
        log.info("Enqueued user update sync envelopes: userId={}", user.getId());
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

        // 默认树边界二次校验：每个目标用户必须在操作者默认树可管范围内
        validateUsersInDefaultTreeScope(tenantId, Set.copyOf(req.ids()));

        // 批量获取用户
        List<SysUser> users = userDomainService.selectValidByIds(tenantId, Set.copyOf(req.ids()));

        // 1. 先入队每条 user-org 的 UNBIND 同步任务（必须在软删前查，否则关系被清）
        List<SysUserOrg> allUserOrgs = userOrgMapper.selectByUserIdsAndTenant(tenantId, req.ids());
        // 批量解析 treeRootExternalId（避免循环单条调用，EXT-5 修复）
        Set<Long> orgIds = allUserOrgs.stream()
            .map(SysUserOrg::getOrgId)
            .collect(Collectors.toSet());
        Map<Long, String> rootExternalIdMap = orgIds.isEmpty()
            ? Map.of()
            : orgTreeConfigDomainService.resolveTreeRootExternalIds(tenantId, orgIds);

        for (SysUserOrg uo : allUserOrgs) {
            SysOrg org = orgDomainService.selectValidById(tenantId, uo.getOrgId());
            String roleTypeCode = resolveOrgRoleTypeCode(org);
            String relationKey = UserOrgKeys.relationKey(uo.getOrgId());
            String treeRootExternalId = rootExternalIdMap.get(uo.getOrgId());
            syncTaskDomainService.enqueueAll(tenantId,
                List.of(syncTaskBuilder.userOrgUnbind(
                    uo.getUserId(), uo.getOrgId(), roleTypeCode, relationKey, treeRootExternalId)));
        }
        log.info("Enqueued {} user-org unbind sync envelopes for delete batch", allUserOrgs.size());

        // 2. 执行本地软删除（含级联清理 sys_user_org）
        userDomainService.softDeleteBatch(tenantId, req.ids());

        // 3. 入队删除同步任务（abstract_user + ADMIN_USER resource_entity 双 envelope）
        for (SysUser user : users) {
            syncTaskDomainService.enqueueAll(tenantId,
                syncTaskBuilder.userDelete(user.getId(), String.valueOf(user.getId())));
            log.info("Enqueued user delete sync envelopes: userId={}", user.getId());
        }
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
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(UserUpdateStatusReq req) {
        // 状态参数校验
        if (req.status() == null || (req.status() != 0 && req.status() != 1)) {
            throw new BizException(AdminErrorCode.INVALID_PARAM.getCode(), "状态值无效，必须为0(禁用)或1(启用)");
        }

        // 禁用时不允许禁用自己
        if (req.status() == 0) {
            long currentUserId = StpUtil.getLoginIdAsLong();
            for (Long id : req.ids()) {
                if (id.equals(currentUserId)) {
                    throw new BizException(AdminErrorCode.CANNOT_DISABLE_SELF.getCode(),
                        AdminErrorCode.CANNOT_DISABLE_SELF.getMessage());
                }
            }
        }

        // 权限检查 — 批量实例级，启用与禁用共用 ENABLE（toggle 语义，v1.4 合并）
        String operationCode = AdminOperationCode.ENABLE;
        List<String> resourceCodes = req.ids().stream()
            .map(String::valueOf)
            .collect(Collectors.toList());
        permissionValidator.checkBatchInstanceLevel(AdminResourceType.USER, resourceCodes, operationCode);

        Long tenantId = TenantContextHolder.getTenantId();

        // 默认树边界二次校验
        validateUsersInDefaultTreeScope(tenantId, Set.copyOf(req.ids()));

        // 使用批量查询验证有效ID（避免N+1问题）
        List<SysUser> existingUsers = userDomainService.selectValidByIds(tenantId, Set.copyOf(req.ids()));
        Set<Long> validIds = existingUsers.stream().map(SysUser::getId).collect(Collectors.toSet());

        if (!validIds.isEmpty()) {
            userDomainService.batchUpdateStatus(tenantId, List.copyOf(validIds), req.status());

            // 同步状态变更到权限中心 — 事务内入队双 envelope
            boolean enabled = req.status() == 1;
            for (SysUser user : existingUsers) {
                // 反映最新 status 给 builder
                user.setStatus(req.status());
                List<cn.ac.fage.accessmesh.admin.sync.model.SyncTaskEnvelope> envelopes =
                    enabled ? syncTaskBuilder.userEnable(user) : syncTaskBuilder.userDisable(user);
                syncTaskDomainService.enqueueAll(tenantId, envelopes);
                log.info("Enqueued user {} sync envelopes: userId={}",
                    enabled ? "enable" : "disable", user.getId());
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
        // v1.4 类型级 VIEW 门禁：前端隐藏不是安全边界
        permissionValidator.checkTypeLevel(AdminResourceType.USER, AdminOperationCode.VIEW);

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

            List<Long> subtreeIds = orgDomainService.getDescendantIdsIncludingSelf(tenantId, req.orgId());
            if (subtreeIds.isEmpty()) {
                return new PaginatedResult<>(
                    List.of(),
                    new PaginatedResult.PaginationMeta(0, pageNum, pageSize, 0)
                );
            }
            orgIds = Set.copyOf(subtreeIds);
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

        // 1. 确定默认组织树中操作者可见的组织范围
        List<SysOrgTreeConfig> defaultConfigs = orgTreeConfigDomainService.findDefaultConfigs(tenantId);
        if (defaultConfigs.isEmpty()) {
            return emptyMemberCandidates(req);
        }
        Long defaultRootOrgId = defaultConfigs.get(0).getRootOrgId();
        List<Long> visibleOrgIds = orgDomainService.getDescendantIdsIncludingSelf(tenantId, defaultRootOrgId);
        if (visibleOrgIds.isEmpty()) {
            return emptyMemberCandidates(req);
        }

        // 2. 收集默认树可见范围内的用户 ID
        Set<Long> defaultTreeOrgIdSet = Set.copyOf(visibleOrgIds);
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
     * 与 {@code RoleProxyServiceImpl#resolveOrgRoleTypeCode} 同语义。
     */
    private String resolveOrgRoleTypeCode(SysOrg org) {
        if (org == null) return "ORG";
        String orgType = org.getOrgType();
        if ("2".equals(orgType) || "POSITION".equalsIgnoreCase(orgType)) return "POSITION";
        return "ORG";
    }

    /**
     * 默认树边界二次校验：校验目标用户是否在操作者默认树可管范围内。
     * <p>
     * 实现策略：验证每个目标用户在默认组织树中至少有一个组织关系（sys_user_org）。
     * 若任一用户不在默认树范围内，整批操作拒绝。
     * <p>
     * 契约依据：{@code docs/design/services/admin-service-api-contract.md} §2 门禁规范
     *
     * @param tenantId 租户 ID
     * @param userIds  目标用户 ID 集合
     * @throws BizException 任一用户不在默认树可管范围
     */
    private void validateUsersInDefaultTreeScope(Long tenantId, Set<Long> userIds) {
        List<SysOrgTreeConfig> defaultConfigs = orgTreeConfigDomainService.findDefaultConfigs(tenantId);
        if (defaultConfigs.isEmpty()) {
            // 无默认树配置，无法验证，放行（由门禁层兜底）
            return;
        }
        Long defaultRootOrgId = defaultConfigs.get(0).getRootOrgId();
        List<Long> defaultTreeOrgIds = orgDomainService.getDescendantIdsIncludingSelf(tenantId, defaultRootOrgId);
        if (defaultTreeOrgIds.isEmpty()) {
            throw new BizException(AdminErrorCode.USER_NOT_IN_DEFAULT_TREE_SCOPE.getCode(),
                AdminErrorCode.USER_NOT_IN_DEFAULT_TREE_SCOPE.getMessage());
        }

        // 查默认树范围内的用户组织关系
        List<SysUserOrg> defaultTreeUserOrgs = userOrgMapper.selectByOrgIdsAndTenant(
            tenantId, List.copyOf(defaultTreeOrgIds));
        Set<Long> usersInDefaultTree = defaultTreeUserOrgs.stream()
            .map(SysUserOrg::getUserId)
            .collect(Collectors.toSet());

        // 任一目标用户不在默认树范围内则拒绝
        for (Long userId : userIds) {
            if (!usersInDefaultTree.contains(userId)) {
                throw new BizException(AdminErrorCode.USER_NOT_IN_DEFAULT_TREE_SCOPE.getCode(),
                    AdminErrorCode.USER_NOT_IN_DEFAULT_TREE_SCOPE.getMessage());
            }
        }
    }
}
