package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.cache.AdminCacheCatalog;
import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.admin.dto.auth.UserInfoResp;
import cn.ac.fage.accessmesh.admin.entity.SysMenu;
import cn.ac.fage.accessmesh.admin.entity.SysOrg;
import cn.ac.fage.accessmesh.admin.entity.SysUserOrg;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.admin.service.RoleProxyService;
import cn.ac.fage.accessmesh.admin.service.domain.MenuDomainService;
import cn.ac.fage.accessmesh.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.admin.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.exception.SystemException;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.client.feign.PermissionFeignClient;
import cn.ac.fage.accessmesh.perm.common.dto.req.BatchRevokeReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.IdReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.OperationListReq;
import cn.ac.fage.accessmesh.admin.dto.resp.RoleListItemResp;
import cn.ac.fage.accessmesh.admin.dto.resp.UserRoleItemResp;
import cn.ac.fage.accessmesh.perm.common.dto.req.RoleCreateReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.RoleListReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.RoleGrantReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.UserPermissionViewReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.UserEffectivePermissionCodesReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.UserAssignRoleReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.UserRoleBatchRevokeReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.OperationPermissionResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PaginatedResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.PermissionEffectivePermissionsResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.UserEffectivePermissionCodesResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.RolePermissionItemsResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.RoleResp;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * 角色代理服务实现类
 * <p>
 * admin-service与permission-center之间的代理层，负责概念转换。
 * 将admin-domain概念（用户、组织、菜单）转换为permission-center概念（角色、资源、权限）。
 * 提供组织角色创建、菜单权限授予/撤销、用户角色和权限加载等功能。
 * 通过Feign调用permission-center服务，使用统一 CacheService 管理缓存。
 * 设计约束：组织/岗位角色类型使用 permission-center 的 ORG/POSITION。
 * 角色操作均使用业务键（roleTypeCode + externalId）定位。
 * </p>
 */
@Service
public class RoleProxyServiceImpl implements RoleProxyService {

    private static final Logger log = LoggerFactory.getLogger(RoleProxyServiceImpl.class);

    /** 功能角色类型编码（排除 ORG 和 POSITION） */
    private static final List<String> FUNCTIONAL_ROLE_TYPES = List.of("BASIC_ROLE", "GROUP_ROLE", "PERSONAL");

    /**
     * 角色类型编码到显示名的映射。
     * 组织/岗位角色类型为 ORG/POSITION。
     */
    private static final Map<String, String> ROLE_TYPE_LABELS = Map.of(
        "BASIC_ROLE", "基础角色",
        "GROUP_ROLE", "分组角色",
        "PERSONAL", "个人角色",
        "ORG", "组织角色",
        "POSITION", "岗位角色"
    );

    private static final int RESOURCE_TYPE_MENU = 1;

    /** 主体类型码：admin-service 的所有用户在权限中心都以 ADMIN_USER 为主体。 */
    private static final String SUBJECT_TYPE_ADMIN_USER = "ADMIN_USER";

    /**
     * 「有效权限码下发」查询的资源类型白名单（v1.4 双轨并行）。
     * <p>
     * 与 {@code AuthServiceImpl.EFFECTIVE_PERMISSION_CODE_RESOURCE_TYPES} 保持一致；产生前端 hasPerms perm 串
     * 的全部资源类型，不含 ADMIN_MENU（菜单可见性轨道独立）。
     */
    private static final List<String> EFFECTIVE_PERMISSION_CODE_RESOURCE_TYPES = List.of(
        AdminResourceType.ORG,
        AdminResourceType.USER,
        AdminResourceType.ROLE,
        AdminResourceType.NOTICE,
        AdminResourceType.JOB,
        AdminResourceType.DICT,
        AdminResourceType.DICT_DATA,
        AdminResourceType.CONFIG,
        AdminResourceType.OAUTH2_CLIENT,
        AdminResourceType.FILE,
        AdminResourceType.ORG_TREE_CONFIG,
        AdminResourceType.SYNC_TASK,
        // permission-center 内部 ROLE 资源类型，承载 C 区「分配功能角色给用户」 → ROLE:MANAGE
        "ROLE"
    );

    private final PermissionFeignClient permissionFeignClient;
    private final UserOrgDomainService userOrgDomainService;
    private final OrgDomainService orgDomainService;
    private final MenuDomainService menuDomainService;
    private final AdminPermissionValidator permissionValidator;
    private final CacheService cacheService;

    /**
     * 构造函数注入依赖
     *
     * @param permissionFeignClient 权限中心Feign客户端
     * @param userOrgDomainService 用户组织关联领域服务
     * @param menuDomainService 菜单领域服务
     * @param permissionValidator 权限校验器
     * @param cacheService 统一缓存服务
     */
    public RoleProxyServiceImpl(PermissionFeignClient permissionFeignClient,
                            UserOrgDomainService userOrgDomainService,
                            OrgDomainService orgDomainService,
                            MenuDomainService menuDomainService,
                            AdminPermissionValidator permissionValidator,
                            CacheService cacheService) {
        this.permissionFeignClient = permissionFeignClient;
        this.userOrgDomainService = userOrgDomainService;
        this.orgDomainService = orgDomainService;
        this.menuDomainService = menuDomainService;
        this.permissionValidator = permissionValidator;
        this.cacheService = cacheService;
    }

    /**
     * 查询功能角色列表
     * <p>
     * 从permission-center查询指定类型的角色，转换为前端展示格式。
     * 默认仅返回功能角色（BASIC_ROLE/GROUP_ROLE/PERSONAL）。
     * </p>
     *
     * @param roleTypeCodes 角色类型编码列表（可选，为空则返回功能角色）
     * @return 角色列表项
     */
    @Override
    public List<RoleListItemResp> listRoles(List<String> roleTypeCodes) {
        // 门禁：ROLE:VIEW 类型级
        permissionValidator.checkTypeLevel(AdminResourceType.ROLE, AdminOperationCode.VIEW);

        List<String> typeCodes = (roleTypeCodes != null && !roleTypeCodes.isEmpty())
            ? roleTypeCodes
            : FUNCTIONAL_ROLE_TYPES;

        RoleListReq req = new RoleListReq(null, null, typeCodes, null, 1, 200, null);
        PermResult<PaginatedResp<RoleResp>> result = permissionFeignClient.listRoles(req);

        if (result == null || result.getCode() != 200 || result.getData() == null) {
            log.warn("Failed to list roles from permission-center");
            return List.of();
        }

        List<RoleResp> roles = result.getData().items();
        if (roles == null) {
            return List.of();
        }

        return roles.stream()
            .map(r -> new RoleListItemResp(
                r.roleTypeCode(),
                r.externalId(),
                r.name(),
                ROLE_TYPE_LABELS.getOrDefault(r.roleTypeCode(), r.roleTypeCode())
            ))
            .collect(Collectors.toList());
    }

    /**
     * 为组织创建角色
     * <p>
     * 在permission-center创建组织/岗位专属角色。
     * 执行类型级权限校验(CREATE)。
     * 角色外部ID为组织ID，用于关联组织与角色。
     * 角色类型由 sys_org.org_type 映射为 ORG/POSITION，externalId=sys_org.id。
     * </p>
     *
     * @param roleName 角色名称
     * @param orgId 组织ID
     * @param tenantId 租户ID
     * @return 创建的角色ID
     * @throws SystemException 创建角色失败
     */
    @Override
    public Long createRoleForOrg(String roleName, Long orgId, Long tenantId) {
        // 权限检查 — ADMIN_ROLE 类型级 CREATE
        permissionValidator.checkTypeLevel(AdminResourceType.ROLE, AdminOperationCode.CREATE);

        SysOrg org = orgDomainService.selectValidById(tenantId, orgId);
        if (org == null) {
            throw new BizException(AdminErrorCode.ORG_NOT_FOUND.getCode(), AdminErrorCode.ORG_NOT_FOUND.getMessage());
        }
        String roleTypeCode = resolveOrgRoleTypeCode(org);

        RoleCreateReq req = new RoleCreateReq(
            null, // parentId
            roleTypeCode,
            String.valueOf(orgId), // externalId
            roleName,
            null, // sortOrder
            null // extra
        );
        PermResult<RoleResp> result = permissionFeignClient.createRole(req);
        if (result == null || result.getData() == null) {
            throw new SystemException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(),
                "Failed to create role for org: " + orgId);
        }
        return result.getData().id();
    }

    /**
     * 为角色授予菜单权限
     * <p>
     * 将指定菜单的指定操作权限授予组织角色。
     * 执行实例级权限校验(GRANT)。
     * 通过MenuDomainService获取菜单，调用permission-center批量授权接口。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId 角色ID
     * @param menuId 菜单ID
     * @param opCode 操作码（如VIEW）
     * @throws BizException 菜单不存在或未同步到权限中心
     * @throws SystemException 授权失败
     */
    @Override
    public void grantMenuToRole(Long tenantId, Long roleId, Long menuId, String opCode) {
        // 权限检查 — ADMIN_ROLE 实例级 GRANT
        permissionValidator.checkInstanceLevel(
            AdminResourceType.ROLE,
            String.valueOf(roleId),
            AdminOperationCode.GRANT
        );
        RoleRef roleRef = resolveRoleRef(roleId);

        // 获取菜单信息（通过 DomainService，符合分层规范）
        SysMenu menu = menuDomainService.selectValidById(tenantId, menuId);
        if (menu == null) {
            throw new BizException(AdminErrorCode.MENU_NOT_FOUND.getCode(), AdminErrorCode.MENU_NOT_FOUND.getMessage());
        }

        Long resourceId = menu.getPermResourceId();
        if (resourceId == null) {
            throw new BizException(AdminErrorCode.MENU_NOT_FOUND.getCode(), "菜单尚未同步到权限中心");
        }

        // 调用 permission-center 授权
        // 使用正确的 RoleGrantReq 结构
        RoleGrantReq.GrantAddItem addItem = new RoleGrantReq.GrantAddItem(
            "MENU",           // resourceTypeCode
            String.valueOf(menuId), // resourceCode
            "ID",             // codeType
            opCode,           // operationCode
            ScopeMode.INSTANCE, // scopeMode
            Boolean.FALSE,    // canGrant
            null              // conditionCode
        );

        RoleGrantReq req = new RoleGrantReq(
            null,             // domainCode
            roleRef.roleTypeCode(),
            roleRef.externalId(),
            List.of(addItem), // add
            List.of(),        // update
            List.of()         // remove
        );

        try {
            PermResult<RolePermissionItemsResp> result = permissionFeignClient.batchGrant(req);
            if (result == null || result.getCode() != 200) {
                log.warn("Failed to grant menu to role: roleId={}, menuId={}, opCode={}", roleId, menuId, opCode);
                throw new SystemException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(),
                    "Failed to grant menu permission");
            }
            log.info("Granted menu to role: roleId={}, menuId={}, opCode={}", roleId, menuId, opCode);
        } catch (Exception e) {
            log.error("Error granting menu to role: roleId={}, menuId={}, error={}", roleId, menuId, e.getMessage());
            throw new SystemException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(),
                "Failed to grant menu permission: " + e.getMessage());
        }
    }

    /**
     * 从角色撤销菜单权限
     * <p>
     * 撤销组织角色对指定菜单的所有权限。
     * 执行实例级权限校验(REVOKE)。
     * 先查询角色现有权限，过滤出匹配菜单的权限项，再调用批量撤销接口。
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId 角色ID
     * @param menuId 菜单ID
     * @throws BizException 菜单不存在或未同步到权限中心
     * @throws SystemException 撤销失败
     */
    @Override
    public void revokeMenuFromRole(Long tenantId, Long roleId, Long menuId) {
        // 权限检查 — ADMIN_ROLE 实例级 REVOKE
        permissionValidator.checkInstanceLevel(
            AdminResourceType.ROLE,
            String.valueOf(roleId),
            AdminOperationCode.REVOKE
        );
        RoleRef roleRef = resolveRoleRef(roleId);

        // 获取菜单信息（通过 DomainService，符合分层规范）
        SysMenu menu = menuDomainService.selectValidById(tenantId, menuId);
        if (menu == null) {
            throw new BizException(AdminErrorCode.MENU_NOT_FOUND.getCode(), AdminErrorCode.MENU_NOT_FOUND.getMessage());
        }

        Long resourceId = menu.getPermResourceId();
        if (resourceId == null) {
            throw new BizException(AdminErrorCode.MENU_NOT_FOUND.getCode(), "菜单尚未同步到权限中心");
        }

        // 调用 permission-center 撤销权限
        try {
            // 1. 查询角色的现有权限
            UserPermissionViewReq viewReq = new UserPermissionViewReq(
                "ROLE",            // targetType
                roleRef.roleTypeCode(),
                roleRef.externalId(),
                null,              // domainCode
                null,              // roleTypeCode
                null,              // roleExternalId
                List.of("MENU"),   // resourceTypeCodes
                null,              // operationCodes
                null,              // resourceKeyword
                null,              // sourceRoleExternalId
                Boolean.FALSE,     // includeScopes
                Boolean.FALSE,     // includeApiResources
                Boolean.FALSE,     // includeSourceRoles
                null,              // sourceRoleLimit
                1,                 // pageNum
                100                // pageSize
            );

            PermResult<PermissionEffectivePermissionsResp> viewResult =
                permissionFeignClient.getEffectivePermissions(viewReq);

            if (viewResult == null || viewResult.getData() == null) {
                log.warn("Failed to query permissions for role: roleId={}, menuId={}", roleId, menuId);
                throw new SystemException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(),
                    "Failed to query existing permissions");
            }

            // 2. 从结果中过滤出匹配 menuId 的权限项，并提取 permissionIds
            List<Long> permissionIds = new ArrayList<>();
            String targetResourceCode = String.valueOf(menuId);

            PermissionEffectivePermissionsResp respData = viewResult.getData();
            if (respData.items() != null) {
                for (var item : respData.items()) {
                    if (targetResourceCode.equals(item.resourceCode())) {
                        permissionIds.addAll(item.matchedPermissionIds());
                    }
                }
            }

            // 3. 如果没有权限需要撤销，直接返回
            if (permissionIds.isEmpty()) {
                log.info("No permissions found to revoke for menu: roleId={}, menuId={}", roleId, menuId);
                return;
            }

            // 4. 调用 permission-center 的批量撤销接口
            BatchRevokeReq revokeReq = new BatchRevokeReq(
                null,                      // domainCode
                roleRef.roleTypeCode(),
                roleRef.externalId(),
                permissionIds              // permissionIds
            );

            PermResult<Void> result = permissionFeignClient.batchRevoke(revokeReq);
            if (result == null || result.getCode() != 200) {
                log.warn("Failed to revoke menu from role: roleId={}, menuId={}, permissionCount={}",
                    roleId, menuId, permissionIds.size());
                throw new SystemException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(),
                    "Failed to revoke menu permission");
            }

            log.info("Revoked menu from role: roleId={}, menuId={}, permissionCount={}",
                roleId, menuId, permissionIds.size());

        } catch (BizException | SystemException e) {
            // 重新抛出已知异常
            throw e;
        } catch (Exception e) {
            log.error("Error revoking menu from role: roleId={}, menuId={}, error={}", roleId, menuId, e.getMessage());
            throw new SystemException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(),
                "Failed to revoke menu permission: " + e.getMessage());
        }
    }

    /**
     * 加载用户角色和权限（v1.4「双轨并行」权限码下发轨道复用）。
     * <p>
     * 与 {@code AuthServiceImpl.getUserMenu} 共享同一鉴权下发链路：
     * <ul>
     *   <li>角色：经 Feign 查 permission-center {@code /api/user/roles}</li>
     *   <li>权限：经 Feign 查 permission-center 有效权限码（按 {@link #EFFECTIVE_PERMISSION_CODE_RESOURCE_TYPES} 限定），
     *       拼成 {@code "资源类型:操作码"}（如 {@code "ADMIN_ORG:CREATE_POSITION"}）作为前端 hasPerms 的 perm 串</li>
     *   <li>组织：经本地 {@code userOrgDomainService} 查询</li>
     * </ul>
     * 失败时各项独立降级为空 List，不阻断整体响应。
     *
     * @param userId 用户ID
     * @return 用户信息响应（角色/权限/组织三部分）
     */
    @Override
    public UserInfoResp loadUserRolesAndPermissions(Long userId) {
        Long tenantId = TenantContextHolder.getTenantId();

        List<SysUserOrg> userOrgs = userOrgDomainService.findByUserId(tenantId, userId);
        List<UserInfoResp.OrgInfo> orgInfos = userOrgs.stream()
            .map(uo -> new UserInfoResp.OrgInfo(uo.getOrgId(), null, null, Boolean.TRUE.equals(uo.getIsPrimary())))
            .collect(Collectors.toList());

        List<UserInfoResp.RoleInfo> roles = fetchUserRoles(tenantId, userId);
        List<String> permissions = fetchUserPermissions(tenantId, userId);

        return new UserInfoResp(userId, null, null, null, null, null, null, roles, permissions, orgInfos);
    }

    /**
     * 通过 Feign 查询用户角色，转为 {@link UserInfoResp.RoleInfo}。
     * <p>
     * 使用业务键标识角色；前端仅显示 roleName，不依赖内部 ID。
     */
    private List<UserInfoResp.RoleInfo> fetchUserRoles(Long tenantId, Long userId) {
        try {
            cn.ac.fage.accessmesh.perm.common.dto.req.UserRoleListReq req =
                new cn.ac.fage.accessmesh.perm.common.dto.req.UserRoleListReq(
                    SUBJECT_TYPE_ADMIN_USER,
                    String.valueOf(userId)
                );
            PermResult<cn.ac.fage.accessmesh.perm.common.dto.resp.UserRolesResp> result =
                permissionFeignClient.getUserRoles(req);
            if (result != null && result.getData() != null && result.getData().roles() != null) {
                return result.getData().roles().stream()
                    .map(r -> new UserInfoResp.RoleInfo(null, r.roleName()))
                    .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch user roles for tenant={}, userId={}", tenantId, userId, e);
        }
        return List.of();
    }

    /**
     * 通过 Feign 查询用户在白名单资源类型上的有效权限码，拼成 {@code "类型:操作码"} perm 串。
     * <p>
     * 与 {@code AuthServiceImpl.getUserPermissions} 同语义；两端都返回相同格式，前端 hasPerms 通用。
     */
    private List<String> fetchUserPermissions(Long tenantId, Long userId) {
        try {
            // v1.4：切换到 /effective-permission-codes 专用聚合接口（不分页、扁平 perm 串），
            // 避免 effective-permissions 的 page=1, size=500 模式在大权限用户上被截断。
            UserEffectivePermissionCodesReq req = new UserEffectivePermissionCodesReq(
                SUBJECT_TYPE_ADMIN_USER,
                String.valueOf(userId),
                EFFECTIVE_PERMISSION_CODE_RESOURCE_TYPES
            );
            PermResult<UserEffectivePermissionCodesResp> result = permissionFeignClient.getEffectivePermissionCodes(req);
            if (result != null && result.getData() != null && result.getData().permissions() != null) {
                return new ArrayList<>(result.getData().permissions());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch user permissions for tenant={}, userId={}", tenantId, userId, e);
        }
        return List.of();
    }

    private RoleRef resolveRoleRef(Long roleId) {
        PermResult<RoleResp> result = permissionFeignClient.getRole(new IdReq(roleId));
        if (result == null || result.getCode() != 200 || result.getData() == null) {
            throw new SystemException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(),
                "Failed to query role detail: " + roleId);
        }

        RoleResp role = result.getData();
        if (!hasText(role.roleTypeCode()) || !hasText(role.externalId())) {
            throw new SystemException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(),
                "Role detail missing business key: " + roleId);
        }
        return new RoleRef(role.roleTypeCode(), role.externalId());
    }

    private String resolveOrgRoleTypeCode(SysOrg org) {
        String orgType = org.getOrgType();
        if ("2".equals(orgType) || "POSITION".equalsIgnoreCase(orgType)) {
            return "POSITION";
        }
        if ("1".equals(orgType) || "ORG".equalsIgnoreCase(orgType)) {
            return "ORG";
        }
        throw new BizException(AdminErrorCode.INVALID_PARAM.getCode(), "Invalid org type: " + orgType);
    }

    /**
     * 查询用户角色列表（admin 代理 permission-center）。
     * <p>
     * 门禁：ADMIN_USER:VIEW@userId。
     * 代理 permission-center /api/perm/user-role/list（业务键 subjectTypeCode=ADMIN_USER），
     * 再通过本地 sys_org 补 relationOrgName。
     */
    @Override
    public List<UserRoleItemResp> listUserRoles(Long userId) {
        // 门禁
        permissionValidator.checkInstanceLevel(AdminResourceType.USER,
            String.valueOf(userId), AdminOperationCode.VIEW);

        try {
            cn.ac.fage.accessmesh.perm.common.dto.req.UserRoleListReq req =
                new cn.ac.fage.accessmesh.perm.common.dto.req.UserRoleListReq(
                    SUBJECT_TYPE_ADMIN_USER,
                    String.valueOf(userId)
                );
            PermResult<cn.ac.fage.accessmesh.perm.common.dto.resp.UserRolesResp> result =
                permissionFeignClient.getUserRoles(req);
            if (result == null || result.getData() == null || result.getData().roles() == null) {
                return List.of();
            }

            // 收集需要补 relationOrgName 的关联组织业务键（relationExternalId = sys_org.id 字符串）
            // P2-1 修复：relationId 是 permission-center 的 abstract_role.id（内部主键），不能直接查 sys_org；
            // 改用 permission-center 返回的 relationExternalId（= sys_org.id）解析组织名
            List<Long> orgIdsToLookup = result.getData().roles().stream()
                .filter(r -> "POSITION".equals(r.roleTypeCode()) && r.relationExternalId() != null)
                .map(r -> Long.valueOf(r.relationExternalId()))
                .collect(Collectors.toList());
            Map<Long, SysOrg> orgMap = orgIdsToLookup.isEmpty()
                ? Map.of()
                : orgDomainService.batchSelectValidByIdsMap(
                    TenantContextHolder.getTenantId(), new java.util.HashSet<>(orgIdsToLookup));

            return result.getData().roles().stream()
                .map(r -> {
                    String relationOrgName = null;
                    if ("POSITION".equals(r.roleTypeCode()) && r.relationExternalId() != null) {
                        SysOrg org = orgMap.get(Long.valueOf(r.relationExternalId()));
                        relationOrgName = org != null ? org.getName() : null;
                    }
                    return new UserRoleItemResp(
                        r.roleTypeCode(),
                        r.roleExternalId(),
                        r.roleName(),
                        ROLE_TYPE_LABELS.getOrDefault(r.roleTypeCode(), r.roleTypeCode()),
                        r.targetType(),
                        r.relationId(),
                        relationOrgName,
                        r.validFrom(),
                        r.validTo()
                    );
                })
                .collect(Collectors.toList());
        } catch (BizException | SystemException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to list user roles for userId={}", userId, e);
            return List.of();
        }
    }

    /**
     * 为用户分配功能角色（admin 代理 permission-center）。
     * <p>
    /**
     * 分配用户功能角色（admin 代理 permission-center）。
     * <p>
     * 对目标角色做实例级 ROLE:MANAGE 门禁（与 org-user-permission-contract.md §5 备注³ 对齐）。
     * 仅允许分配功能角色（BASIC_ROLE/GROUP_ROLE/PERSONAL），ORG/POSITION 走 /user-org/*。
     * <p>
     * 契约依据：{@code docs/design/services/admin-service-api-contract.md} §4.4.2
     */
    @Override
    public void assignRole(Long userId, String roleTypeCode, String roleExternalId,
                           java.time.LocalDateTime validFrom, java.time.LocalDateTime validTo) {
        // 1. 校验角色类型为功能角色（ORG/POSITION 走 /user-org/*）
        if ("ORG".equals(roleTypeCode) || "POSITION".equals(roleTypeCode)) {
            throw new BizException(AdminErrorCode.INVALID_PARAM.getCode(),
                "ORG/POSITION 角色请通过组织归属接口分配，不支持直接分配角色");
        }

        // 2. 调用 permission-center 分配角色
        // ROLE:MANAGE 实例级门禁由 permission-center UserManageAppServiceImpl.assignRole 兜底
        // （用正确的 abstract_role.id 校验，admin 层不再重复预检——P1-1 修复：
        //  admin 层原预检把 roleExternalId 当 ROLE resource_entity.code 传 auth/check，
        //  而 ROLE 权限实际挂 abstract_role.id 维度，预检语义错位且会误拒）
        try {
            UserAssignRoleReq.AssignItem assignItem = new UserAssignRoleReq.AssignItem(
                SUBJECT_TYPE_ADMIN_USER,
                String.valueOf(userId),
                null, // domainCode
                roleTypeCode,
                roleExternalId,
                null, // relationId
                validFrom,
                validTo
            );
            UserAssignRoleReq req = new UserAssignRoleReq(List.of(assignItem));
            PermResult<Void> result = permissionFeignClient.assignRole(req);
            if (result == null || result.getCode() != 200) {
                log.warn("Failed to assign role to user: userId={}, roleTypeCode={}, roleExternalId={}",
                    userId, roleTypeCode, roleExternalId);
                throw new SystemException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(),
                    "Failed to assign role to user");
            }
            log.info("Assigned role to user: userId={}, roleTypeCode={}, roleExternalId={}",
                userId, roleTypeCode, roleExternalId);
        } catch (BizException | SystemException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error assigning role to user: userId={}, roleTypeCode={}, roleExternalId={}, error={}",
                userId, roleTypeCode, roleExternalId, e.getMessage());
            throw new SystemException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(),
                "Failed to assign role to user: " + e.getMessage());
        }
    }

    /**
     * 回收用户功能角色（admin 代理 permission-center）。
     * <p>
    /**
     * 回收用户功能角色（admin 代理 permission-center）。
     * <p>
     * 对目标角色做实例级 ROLE:MANAGE 门禁（与 org-user-permission-contract.md §5 备注³ 对齐）。
     * 仅允许回收功能角色（BASIC_ROLE/GROUP_ROLE/PERSONAL），ORG/POSITION 走 /user-org/*。
     * <p>
     * 契约依据：{@code docs/design/services/admin-service-api-contract.md} §4.4.3
     */
    @Override
    public void revokeRole(Long userId, String roleTypeCode, String roleExternalId) {
        // 1. 校验角色类型为功能角色（ORG/POSITION 走 /user-org/*）
        if ("ORG".equals(roleTypeCode) || "POSITION".equals(roleTypeCode)) {
            throw new BizException(AdminErrorCode.INVALID_PARAM.getCode(),
                "ORG/POSITION 角色请通过组织归属接口回收，不支持直接回收角色");
        }

        // 2. 调用 permission-center 回收角色
        // ROLE:MANAGE 实例级门禁由 permission-center 兜底（同 assignRole，P1-1 修复）
        try {
            UserRoleBatchRevokeReq.RevokeItem revokeItem = new UserRoleBatchRevokeReq.RevokeItem(
                SUBJECT_TYPE_ADMIN_USER,
                String.valueOf(userId),
                null, // domainCode
                roleTypeCode,
                roleExternalId,
                null  // relationId
            );
            UserRoleBatchRevokeReq req = new UserRoleBatchRevokeReq(List.of(revokeItem));
            PermResult<Void> result = permissionFeignClient.revokeRoles(req);
            if (result == null || result.getCode() != 200) {
                log.warn("Failed to revoke role from user: userId={}, roleTypeCode={}, roleExternalId={}",
                    userId, roleTypeCode, roleExternalId);
                throw new SystemException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(),
                    "Failed to revoke role from user");
            }
            log.info("Revoked role from user: userId={}, roleTypeCode={}, roleExternalId={}",
                userId, roleTypeCode, roleExternalId);
        } catch (BizException | SystemException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error revoking role from user: userId={}, roleTypeCode={}, roleExternalId={}, error={}",
                userId, roleTypeCode, roleExternalId, e.getMessage());
            throw new SystemException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(),
                "Failed to revoke role from user: " + e.getMessage());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record RoleRef(String roleTypeCode, String externalId) {
    }

    /**
     * 解析操作码为操作权限ID
     * <p>
     * 将操作码（如VIEW）转换为permission-center的操作权限ID。
     * 使用统一 CacheService 管理缓存。
     * </p>
     *
     * @param tenantId 租户ID
     * @param opCode 操作码
     * @return 操作权限ID
     * @throws IllegalStateException 操作码不存在
     */
    private Long resolveOperationPermissionId(Long tenantId, String opCode) {
        // ① 查缓存
        Map<String, Long> ops = cacheService.get(AdminCacheCatalog.OPERATION_CODE, tenantId, tenantId);

        // ② miss 后查 DB (通过 Feign)
        if (ops == null) {
            ops = loadOperations(tenantId);
            // ③ 回填缓存
            if (ops != null) {
                cacheService.put(AdminCacheCatalog.OPERATION_CODE, tenantId, tenantId, ops);
            }
        }

        Long opId = ops != null ? ops.get(opCode) : null;
        if (opId == null) {
            throw new IllegalStateException("Operation code '" + opCode + "' not found for tenant " + tenantId);
        }
        return opId;
    }

    /**
     * 从permission-center加载操作码列表
     * <p>
     * 通过Feign调用permission-center获取MENU资源类型的操作列表。
     * 返回操作码到操作权限ID的映射。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 操作码到ID的映射
     * @throws IllegalStateException 加载失败
     */
    private Map<String, Long> loadOperations(Long tenantId) {
        OperationListReq req = new OperationListReq("MENU"); // resourceTypeCode
        PermResult<ItemsResp<OperationPermissionResp>> result = permissionFeignClient.listOperations(req);
        if (result == null || result.getData() == null) {
            throw new SystemException(AdminErrorCode.EXTERNAL_SERVICE_ERROR.getCode(), "Failed to list operations for tenant " + tenantId);
        }
        Map<String, Long> ops = new HashMap<>();
        List<OperationPermissionResp> items = result.getData().items();
        if (items != null) {
            for (OperationPermissionResp op : items) {
                ops.put(op.code(), op.id());
            }
        }
        return ops;
    }
}
