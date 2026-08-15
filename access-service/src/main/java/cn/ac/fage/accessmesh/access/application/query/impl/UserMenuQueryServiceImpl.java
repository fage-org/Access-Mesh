package cn.ac.fage.accessmesh.access.application.query.impl;

import cn.ac.fage.accessmesh.access.admin.dto.auth.UserInfoResp;
import cn.ac.fage.accessmesh.access.admin.dto.auth.UserMenuResp;
import cn.ac.fage.accessmesh.access.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.access.application.query.UserMenuQueryService;
import cn.ac.fage.accessmesh.access.application.query.mapper.UserMenuQueryMapper;
import cn.ac.fage.accessmesh.access.application.query.projection.MenuProjection;
import cn.ac.fage.accessmesh.access.application.query.projection.UserOrgProjection;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveRequest;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleListReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.UserRolesResp;
import cn.ac.fage.accessmesh.access.permission.service.PermissionViewAppService;
import cn.ac.fage.accessmesh.access.permission.service.UserManageAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.perm.common.dto.req.UserEffectivePermissionCodesReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.UserEffectivePermissionCodesResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户菜单聚合查询实现（跨域只读）。
 * <p>
 * 聚合 admin 域（sys_user_org、sys_menu）与 permission 域（角色、有效权限码、菜单可见性判定），
 * 数据读取经 {@link UserMenuQueryMapper}，权限判定经 {@link PermQueryEngine}。
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
    private static final String MENU_TYPE_HIDDEN = "HIDDEN";
    private static final String MENU_TYPE_EXTERNAL = "EXTERNAL";
    private static final String MENU_TYPE_IFRAME = "IFRAME";
    private static final String OPERATION_VIEW = "VIEW";

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
    private final UserManageAppService userManageAppService;
    private final PermissionViewAppService permissionViewAppService;
    private final TypeResolutionService typeResolutionService;
    private final PermQueryEngine engine;

    public UserMenuQueryServiceImpl(UserMenuQueryMapper userMenuQueryMapper,
                                    UserManageAppService userManageAppService,
                                    PermissionViewAppService permissionViewAppService,
                                    TypeResolutionService typeResolutionService,
                                    PermQueryEngine engine) {
        this.userMenuQueryMapper = userMenuQueryMapper;
        this.userManageAppService = userManageAppService;
        this.permissionViewAppService = permissionViewAppService;
        this.typeResolutionService = typeResolutionService;
        this.engine = engine;
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
        Set<Long> allowedMenuIds = filterAllowedMenus(tenantId, userId, allMenus);
        List<UserMenuResp.MenuRouteItem> menus = buildMenuTree(allMenus, allowedMenuIds, 0L);
        return new UserMenuResp(menus, roles, permissions);
    }

    /**
     * 过滤用户有 ADMIN_MENU:VIEW 的菜单 ID（登录菜单树用）。
     * 先批量解析菜单业务键 → 资源投影 ID，denied 结果映射回菜单 ID；未解析视为不可见。
     */
    private Set<Long> filterAllowedMenuIds(Long tenantId, Long userId, java.util.Collection<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return Set.of();
        }
        Long abstractUserId = typeResolutionService.resolveUserId(
            tenantId, LocalProjectionOwner.SUBJECT_ADMIN_USER, String.valueOf(userId));
        if (abstractUserId == null) {
            return Set.of();
        }
        List<ResourceResolveRequest> requests = menuIds.stream()
            .map(menuId -> new ResourceResolveRequest(AdminResourceType.MENU, String.valueOf(menuId), null, null))
            .toList();
        Map<ResourceResolveKey, Long> resolved = typeResolutionService.batchResolveResourceIds(tenantId, requests);
        Map<Long, Long> entityIdByMenuId = new LinkedHashMap<>();
        for (Long menuId : menuIds) {
            Long entityId = resolved.get(
                new ResourceResolveKey(AdminResourceType.MENU, String.valueOf(menuId), null, null));
            if (entityId != null) {
                entityIdByMenuId.put(menuId, entityId);
            }
        }
        if (entityIdByMenuId.isEmpty()) {
            return Set.of();
        }
        Set<Long> deniedEntityIds = engine.getDeniedIds(
            tenantId, abstractUserId, AdminResourceType.MENU,
            new LinkedHashSet<>(entityIdByMenuId.values()), OPERATION_VIEW);
        Set<Long> allowed = new LinkedHashSet<>();
        for (Map.Entry<Long, Long> entry : entityIdByMenuId.entrySet()) {
            if (!deniedEntityIds.contains(entry.getValue())) {
                allowed.add(entry.getKey());
            }
        }
        return allowed;
    }

    /**
     * 过滤用户有权限的菜单并补充祖先链，保证子菜单有权限时父菜单也显示。
     * 候选排除 HIDDEN（schema 枚举：隐藏路由不进 menus[]；UserMenuResp 无 hiddenRoutes 字段，
     * 隐藏路由整体丢弃，后续如需下发 hiddenRoutes 再扩展响应结构）。
     */
    private Set<Long> filterAllowedMenus(Long tenantId, Long userId, List<MenuProjection> allMenus) {
        if (allMenus.isEmpty()) {
            return Set.of();
        }
        List<Long> candidateIds = allMenus.stream()
            .filter(m -> m.menuType() != null && !MENU_TYPE_HIDDEN.equals(m.menuType()))
            .map(MenuProjection::id)
            .collect(Collectors.toList());
        if (candidateIds.isEmpty()) {
            return Set.of();
        }
        try {
            Set<Long> allowed = filterAllowedMenuIds(tenantId, userId, candidateIds);
            Set<Long> withParents = new HashSet<>(allowed);
            for (MenuProjection menu : allMenus) {
                if (allowed.contains(menu.id()) && menu.parentId() != null && menu.parentId() > 0) {
                    addParentMenus(allMenus, menu.parentId(), withParents);
                }
            }
            return withParents;
        } catch (Exception e) {
            log.warn("Failed to filter allowed menus for tenant={}, userId={}", tenantId, userId, e);
        }
        return Set.of();
    }

    private void addParentMenus(List<MenuProjection> allMenus, Long parentId, Set<Long> withParents) {
        for (MenuProjection menu : allMenus) {
            if (menu.id().equals(parentId)) {
                // HIDDEN 不进 menus[]（权威 schema 契约）：祖先链补全同样跳过 HIDDEN 节点
                if (MENU_TYPE_HIDDEN.equals(menu.menuType())) {
                    break;
                }
                withParents.add(menu.id());
                if (menu.parentId() != null && menu.parentId() > 0) {
                    addParentMenus(allMenus, menu.parentId(), withParents);
                }
                break;
            }
        }
    }

    /**
     * 构建菜单树（前端路由格式）。仅包含有权限、启用的菜单，按排序字段升序。
     * schema 收敛后缺失字段统一取默认值（见类注释）。
     */
    private List<UserMenuResp.MenuRouteItem> buildMenuTree(List<MenuProjection> allMenus, Set<Long> allowedIds, Long parentId) {
        return allMenus.stream()
            .filter(m -> parentId.equals(m.parentId() != null ? m.parentId() : 0L))
            .filter(m -> allowedIds.contains(m.id()))
            .filter(m -> m.status() != null && m.status() == 1)
            .sorted(Comparator.comparingInt(m -> m.sortOrder() != null ? m.sortOrder() : 0))
            .map(m -> {
                List<UserMenuResp.MenuRouteItem> children = buildMenuTree(allMenus, allowedIds, m.id());
                boolean external = MENU_TYPE_EXTERNAL.equals(m.menuType()) || MENU_TYPE_IFRAME.equals(m.menuType());
                UserMenuResp.MetaInfo meta = new UserMenuResp.MetaInfo(
                    m.name(),
                    m.icon(),
                    m.sortOrder(),
                    true,   // showLink：schema 无 visible 列，默认显示
                    false,  // keepAlive：schema 无 is_cache 列，默认不缓存
                    external ? m.path() : null,
                    null,   // roles 由前端根据用户角色判断
                    null    // auths：schema 无 perm_code 列，默认空
                );
                return new UserMenuResp.MenuRouteItem(
                    m.path(),
                    generateRouteName(m),
                    null,   // component：schema 无 component 列，由前端按 path 约定解析
                    !children.isEmpty() ? children.get(0).path() : null,
                    meta,
                    children
                );
            })
            .collect(Collectors.toList());
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
            UserRolesResp result = userManageAppService.getUserRoles(
                tenantId, new UserRoleListReq(LocalProjectionOwner.SUBJECT_ADMIN_USER, String.valueOf(userId)));
            if (result != null && result.roles() != null) {
                return result.roles().stream()
                    .map(r -> new UserInfoResp.RoleInfo(null, r.roleName()))
                    .collect(Collectors.toList());
            }
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
