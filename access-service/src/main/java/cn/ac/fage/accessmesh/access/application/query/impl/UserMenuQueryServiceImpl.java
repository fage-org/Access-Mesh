package cn.ac.fage.accessmesh.access.application.query.impl;

import cn.ac.fage.accessmesh.access.admin.dto.auth.UserInfoResp;
import cn.ac.fage.accessmesh.access.admin.dto.auth.UserMenuResp;
import cn.ac.fage.accessmesh.access.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.access.application.query.UserMenuQueryService;
import cn.ac.fage.accessmesh.access.application.query.mapper.UserMenuQueryMapper;
import cn.ac.fage.accessmesh.access.application.query.mapper.UserRoleQueryMapper;
import cn.ac.fage.accessmesh.access.application.query.projection.MenuProjection;
import cn.ac.fage.accessmesh.access.application.query.projection.UserOrgProjection;
import cn.ac.fage.accessmesh.access.application.query.projection.UserRoleProjection;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveRequest;
import cn.ac.fage.accessmesh.access.permission.service.PermissionViewAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.perm.common.dto.req.UserEffectivePermissionCodesReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.UserEffectivePermissionCodesResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户菜单聚合查询实现（跨域只读）。
 * <p>
 * 聚合 admin 域（sys_user_org、sys_menu）与 permission 域（角色、有效权限码、资源实例访问事实）。
 * 数据读取经 {@link UserMenuQueryMapper} / {@link UserRoleQueryMapper}；
 * 权限事实经 {@link PermissionViewAppService}（管理查询入口，引擎封装在 permission 域）。
 * </p>
 * <p>
 * 菜单可见性按 adopted v3.5 §4.1 派生公式：
 * MENU(业务)（resource_type 非空）→ 用户对关联资源有任意有效操作码（含 scopeAll 全范围授权）即可见，
 * 无 scopeAll 时 resource_code 为空或资源实例无法解析则不可见（fail-closed）；
 * MENU(纯展示)（resource_type IS NULL）→ 全员可见；DIR → 存在可见子节点（树构建剪枝）；
 * HIDDEN → 派生同 MENU(业务) 但响应无 hiddenRoutes 字段，整体不进 menus[]；
 * EXTERNAL/IFRAME → 派生同 MENU(业务)。
 * </p>
 * <p>
 * 菜单树构建按权威 schema（access-service.sql）：sys_menu 已收敛为 UI 路由元数据 + 资源 link
 * （display_name/DIR-MENU 枚举），无 component/visible/perm_code 等旧列，缺失字段统一取默认值
 * （component=null、showLink=true、keepAlive=false、auths=null）。存量 DDL-实体漂移（菜单 CRUD
 * 写路径仍使用旧实体字段）登记于 T-ACCESS-006 完成记录，由 T-ACCESS-012 统一收口。
 * </p>
 */
@Service
public class UserMenuQueryServiceImpl implements UserMenuQueryService {

    private static final Logger log = LoggerFactory.getLogger(UserMenuQueryServiceImpl.class);

    private static final String MENU_TYPE_DIR = "DIR";
    private static final String MENU_TYPE_MENU = "MENU";
    private static final String MENU_TYPE_HIDDEN = "HIDDEN";
    private static final String MENU_TYPE_EXTERNAL = "EXTERNAL";
    private static final String MENU_TYPE_IFRAME = "IFRAME";
    private static final String RESOURCE_TYPE_KEY = "resource_type";

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
        "ROLE"
    );

    private final UserMenuQueryMapper userMenuQueryMapper;
    private final UserRoleQueryMapper userRoleQueryMapper;
    private final PermissionViewAppService permissionViewAppService;
    private final TypeResolutionService typeResolutionService;

    public UserMenuQueryServiceImpl(UserMenuQueryMapper userMenuQueryMapper,
                                    UserRoleQueryMapper userRoleQueryMapper,
                                    PermissionViewAppService permissionViewAppService,
                                    TypeResolutionService typeResolutionService) {
        this.userMenuQueryMapper = userMenuQueryMapper;
        this.userRoleQueryMapper = userRoleQueryMapper;
        this.permissionViewAppService = permissionViewAppService;
        this.typeResolutionService = typeResolutionService;
    }

    @Override
    @Transactional(readOnly = true)
    public UserInfoResp loadUserRolesAndPermissions(Long userId) {
        Long tenantId = TenantContextHolder.getTenantId();
        List<UserOrgProjection> userOrgs = userMenuQueryMapper.selectUserOrgsByUserIds(tenantId, List.of(userId));
        List<UserInfoResp.OrgInfo> orgInfos = userOrgs.stream()
            .map(uo -> new UserInfoResp.OrgInfo(uo.orgId(), null, null, Boolean.TRUE.equals(uo.isPrimary())))
            .collect(Collectors.toList());
        return new UserInfoResp(userId, null, null, null, null, null, null,
            fetchUserRoles(tenantId, userId), fetchUserPermissions(tenantId, userId), orgInfos);
    }

    @Override
    @Transactional(readOnly = true)
    public UserMenuResp buildUserMenuTree(Long userId) {
        Long tenantId = TenantContextHolder.getTenantId();
        // 角色/权限加载失败时降级为空（登录链路容错：菜单树仍可构建，原 getUserMenu 语义）
        UserInfoResp rolePermInfo;
        try {
            rolePermInfo = loadUserRolesAndPermissions(userId);
        } catch (Exception e) {
            log.warn("Failed to load user roles/permissions for tenant={}, userId={}", tenantId, userId, e);
            rolePermInfo = null;
        }
        List<String> roles = rolePermInfo != null && rolePermInfo.roles() != null
            ? rolePermInfo.roles().stream()
                .map(UserInfoResp.RoleInfo::roleName)
                .collect(Collectors.toList())
            : List.of();
        List<String> permissions = rolePermInfo != null && rolePermInfo.permissions() != null
            ? new ArrayList<>(rolePermInfo.permissions())
            : List.of();
        List<MenuProjection> allMenus = userMenuQueryMapper.selectMenus(tenantId);
        // v3.5 §4.1 派生公式：计算用户可见菜单 ID（DIR 由树构建剪枝，HIDDEN 不进 menus[]）
        Set<Long> visibleMenuIds = deriveVisibleMenuIds(tenantId, userId, allMenus);
        List<UserMenuResp.MenuRouteItem> menus = buildMenuTree(allMenus, visibleMenuIds);
        return new UserMenuResp(menus, roles, permissions);
    }

    /**
     * v3.5 §4.1 派生公式：判定用户可见的菜单 ID 集合。
     * <p>
     * 先按 {@code menu_type} 分支（见实现）：DIR 恒候选可见；MENU(纯展示) 全员可见；
     * 业务菜单类型显式限定为 MENU/HIDDEN/EXTERNAL/IFRAME（未知类型默认 fail-closed 不可见），
     * 且 resource_type 非空时经 permission 域有效资源访问事实匹配：资源类型有 scopeAll 全范围授权、
     * 或解析后的资源实例 ID 在用户有任意有效操作码的集合中；无 scopeAll 时 resource_code 为空或
     * 资源实例无法解析（无投影）视为不可见（fail-closed）。
     * </p>
     */
    private Set<Long> deriveVisibleMenuIds(Long tenantId, Long userId, List<MenuProjection> allMenus) {
        // v3.5 §4.1 按 menu_type 分支：
        //   DIR               → 恒候选可见（是否渲染由树构建剪枝决定：有可见子节点才渲染），不参与资源判定；
        //   MENU(纯展示)      → resource_type IS NULL → 全员可见；
        //   MENU/HIDDEN/EXTERNAL/IFRAME(业务) → resource_type 非空 → 有效资源访问事实匹配
        //                        （先 scopeAll 全范围授权；无 scopeAll 时 resource_code 为空或
        //                          资源实例解析失败 fail-closed，DDL 无两列成对约束）；
        //   resource_type 为空 → 无匹配条件 → fail-closed 不可见；
        //   未知 menu_type     → fail-closed 不可见（DDL 无 CHECK 约束，显式枚举防脏数据误放行）。
        Set<Long> visible = new LinkedHashSet<>();
        List<MenuProjection> businessMenus = new ArrayList<>();
        for (MenuProjection menu : allMenus) {
            if (MENU_TYPE_DIR.equals(menu.menuType())) {
                visible.add(menu.id());
            } else if (MENU_TYPE_MENU.equals(menu.menuType()) && menu.resourceType() == null) {
                visible.add(menu.id());
            } else if (isBusinessMenuType(menu.menuType()) && menu.resourceType() != null) {
                businessMenus.add(menu);
            }
            // 其余（HIDDEN/EXTERNAL/IFRAME 且 resource_type 为空、未知 menu_type）不入 visible → fail-closed
        }
        if (businessMenus.isEmpty()) {
            return visible;
        }
        // 有效资源访问事实（白名单 = 菜单涉及的资源类型码，全范围/实例粒度）
        Set<String> menuTypeCodes = businessMenus.stream()
            .map(MenuProjection::resourceType)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        PermissionViewAppService.EffectiveResourceAccess access;
        try {
            access = permissionViewAppService.getEffectiveResourceAccess(tenantId,
                new UserEffectivePermissionCodesReq(
                    LocalProjectionOwner.SUBJECT_ADMIN_USER, String.valueOf(userId), List.copyOf(menuTypeCodes)));
        } catch (Exception e) {
            log.warn("Failed to load effective resource access for tenant={}, userId={}", tenantId, userId, e);
            return visible;
        }
        // 菜单资源类型码 → 值（scopeAll 匹配需要 int 值）；资源业务键 → 资源实例 ID。
        // 解析失败降级为业务菜单不可见（fail-closed），仅保留 DIR/纯展示菜单，不中断登录（评审 P2 修复）。
        Map<String, Integer> typeValueByCode;
        Map<ResourceResolveKey, Long> resolved;
        try {
            typeValueByCode = typeResolutionService.batchResolveTypeValues(
                tenantId, RESOURCE_TYPE_KEY, menuTypeCodes);
            List<ResourceResolveRequest> requests = businessMenus.stream()
                .map(m -> new ResourceResolveRequest(m.resourceType(), m.resourceCode(), null, null))
                .toList();
            resolved = typeResolutionService.batchResolveResourceIds(tenantId, requests);
        } catch (Exception e) {
            log.warn("Failed to resolve menu resource types/ids for tenant={}, userId={}", tenantId, userId, e);
            return visible;
        }
        for (MenuProjection menu : businessMenus) {
            Integer typeValue = typeValueByCode.get(menu.resourceType());
            if (typeValue != null && access.allScopeTypes().contains(typeValue)) {
                visible.add(menu.id());
                continue;
            }
            Long entityId = resolved.get(
                new ResourceResolveKey(menu.resourceType(), menu.resourceCode(), null, null));
            if (entityId != null && access.resourceEntityIds().contains(entityId)) {
                visible.add(menu.id());
            }
        }
        return visible;
    }

    /**
     * 业务菜单类型显式枚举（v3.5 §4.1：MENU/HIDDEN/EXTERNAL/IFRAME 派生方式一致）。
     * <p>未知 {@code menu_type} 一律返回 false，由调用方按 fail-closed 处理（不入可见集合）。
     * DB 中 menu_type 为 VARCHAR 无 CHECK 约束，显式枚举防脏数据被误放行。</p>
     */
    private boolean isBusinessMenuType(String menuType) {
        return MENU_TYPE_MENU.equals(menuType)
            || MENU_TYPE_HIDDEN.equals(menuType)
            || MENU_TYPE_EXTERNAL.equals(menuType)
            || MENU_TYPE_IFRAME.equals(menuType);
    }

    /**
     * 构建菜单树（前端路由格式）。仅包含用户可见、启用的菜单，按排序字段升序。
     * <p>
     * 线性化实现：先按 parentId 预建 children 映射，再逐层递归构建，
     * 避免每递归一个节点都全量扫描菜单列表（每个节点只被访问一次）。
     * DIR 剪枝：无可见子节点的目录不渲染（v3.5 §4.1）；HIDDEN 不进 menus[]（响应无 hiddenRoutes，
     * 隐藏路由整体丢弃）；visited 防脏数据环（parent 链重复/成环时停止递归，避免栈溢出）。
     * schema 收敛后缺失字段统一取默认值（见类注释）。
     * </p>
     */
    private List<UserMenuResp.MenuRouteItem> buildMenuTree(List<MenuProjection> allMenus, Set<Long> visibleIds) {
        Map<Long, List<MenuProjection>> childrenByParent = new LinkedHashMap<>();
        for (MenuProjection menu : allMenus) {
            Long parent = menu.parentId() != null ? menu.parentId() : 0L;
            childrenByParent.computeIfAbsent(parent, k -> new ArrayList<>()).add(menu);
        }
        return buildMenuChildren(childrenByParent, visibleIds, 0L, new LinkedHashSet<>());
    }

    /**
     * 递归构建某一父节点下的可见菜单子节点。
     * <p>
     * 已知限制（登记，接受风险）：递归深度等于菜单层级（parent 链长度）。sys_menu 权威 schema
     * 未对层级设置上限，极端深度（超线程栈深）存在 {@link StackOverflowError} 风险；{@code visited}
     * 仅防护脏数据环，不改变深度本身。菜单配置为受控管理数据，此风险接受，不做迭代改写。
     * </p>
     */
    private List<UserMenuResp.MenuRouteItem> buildMenuChildren(Map<Long, List<MenuProjection>> childrenByParent,
                                                               Set<Long> visibleIds, Long parentId, Set<Long> visited) {
        List<UserMenuResp.MenuRouteItem> items = new ArrayList<>();
        for (MenuProjection menu : childrenByParent.getOrDefault(parentId, List.of())) {
            if (MENU_TYPE_HIDDEN.equals(menu.menuType())
                || !visibleIds.contains(menu.id())
                || menu.status() == null || menu.status() != 1
                || !visited.add(menu.id())) {
                continue;
            }
            List<UserMenuResp.MenuRouteItem> children =
                buildMenuChildren(childrenByParent, visibleIds, menu.id(), visited);
            if (MENU_TYPE_DIR.equals(menu.menuType()) && children.isEmpty()) {
                continue; // DIR 剪枝：无可见子节点不渲染
            }
            boolean external = MENU_TYPE_EXTERNAL.equals(menu.menuType()) || MENU_TYPE_IFRAME.equals(menu.menuType());
            UserMenuResp.MetaInfo meta = new UserMenuResp.MetaInfo(
                menu.name(),
                menu.icon(),
                menu.sortOrder(),
                true,   // showLink：schema 无 visible 列，默认显示
                false,  // keepAlive：schema 无 is_cache 列，默认不缓存
                external ? menu.path() : null,
                null,   // roles 由前端根据用户角色判断
                null    // auths：schema 无 perm_code 列，默认空
            );
            items.add(new UserMenuResp.MenuRouteItem(
                menu.path(),
                generateRouteName(menu),
                null,   // component：schema 无 component 列，由前端按 path 约定解析
                !children.isEmpty() ? children.get(0).path() : null,
                meta,
                children
            ));
        }
        items.sort(Comparator.comparingInt(m -> m.meta().rank() != null ? m.meta().rank() : 0));
        return items;
    }

    private String generateRouteName(MenuProjection menu) {
        String path = menu.path();
        if (path == null || path.isBlank()) {
            return "Menu" + menu.id();
        }
        if (MENU_TYPE_DIR.equals(menu.menuType())) {
            // 目录类型：路径转下划线并加 Parent 后缀
            return path.replace("/", "_").replaceAll("^_", "") + "Parent";
        }
        // 菜单类型：路径转驼峰命名
        StringBuilder name = new StringBuilder();
        for (String part : path.split("/")) {
            if (!part.isBlank()) {
                if (name.isEmpty()) {
                    name.append(part);
                } else {
                    name.append(Character.toUpperCase(part.charAt(0)));
                    if (part.length() > 1) {
                        name.append(part.substring(1));
                    }
                }
            }
        }
        return name.toString();
    }

    private List<UserInfoResp.RoleInfo> fetchUserRoles(Long tenantId, Long userId) {
        try {
            Long abstractUserId = typeResolutionService.resolveUserId(
                tenantId, LocalProjectionOwner.SUBJECT_ADMIN_USER, String.valueOf(userId));
            if (abstractUserId == null) {
                return List.of();
            }
            List<UserRoleProjection> projections =
                userRoleQueryMapper.selectUserRoleProjections(tenantId, abstractUserId, java.time.LocalDateTime.now());
            return projections.stream()
                .filter(p -> p.roleName() != null)
                .map(p -> new UserInfoResp.RoleInfo(null, p.roleName()))
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to fetch user roles for tenant={}, userId={}", tenantId, userId, e);
        }
        return List.of();
    }

    private List<String> fetchUserPermissions(Long tenantId, Long userId) {
        try {
            UserEffectivePermissionCodesReq req = new UserEffectivePermissionCodesReq(
                LocalProjectionOwner.SUBJECT_ADMIN_USER,
                String.valueOf(userId),
                EFFECTIVE_PERMISSION_CODE_RESOURCE_TYPES
            );
            UserEffectivePermissionCodesResp result =
                permissionViewAppService.getEffectivePermissionCodes(tenantId, req);
            if (result != null && result.permissions() != null) {
                return new ArrayList<>(result.permissions());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch user permissions for tenant={}, userId={}", tenantId, userId, e);
        }
        return List.of();
    }
}
