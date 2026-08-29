package cn.ac.fage.accessmesh.access.application.query;

import cn.ac.fage.accessmesh.access.admin.dto.auth.UserInfoResp;
import cn.ac.fage.accessmesh.access.admin.dto.auth.UserMenuResp;
import cn.ac.fage.accessmesh.access.application.query.impl.UserMenuQueryServiceImpl;
import cn.ac.fage.accessmesh.access.application.query.mapper.UserMenuQueryMapper;
import cn.ac.fage.accessmesh.access.application.query.mapper.UserRoleQueryMapper;
import cn.ac.fage.accessmesh.access.application.query.projection.MenuProjection;
import cn.ac.fage.accessmesh.access.application.query.projection.UserOrgProjection;
import cn.ac.fage.accessmesh.access.application.query.projection.UserRoleProjection;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.access.permission.service.PermissionViewAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.perm.common.dto.req.UserEffectivePermissionCodesReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.UserEffectivePermissionCodesResp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 用户菜单聚合查询服务（跨域只读）行为测试。
 * <p>
 * 覆盖 v3.5 §4.1 菜单可见性派生公式（业务菜单资源匹配 / scopeAll 全范围 / 纯展示全员可见 /
 * DIR 剪枝 / HIDDEN 排除）、菜单树构建默认值与过滤、角色/权限聚合容错。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class UserMenuQueryServiceImplTest {

    private static final Long TENANT = 1L;
    private static final Long USER = 100L;
    private static final String SUBJECT = LocalProjectionOwner.SUBJECT_LOCAL_USER;

    @Mock private UserMenuQueryMapper userMenuQueryMapper;
    @Mock private UserRoleQueryMapper userRoleQueryMapper;
    @Mock private PermissionViewAppService permissionViewAppService;
    @Mock private TypeResolutionService typeResolutionService;

    private UserMenuQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserMenuQueryServiceImpl(
            userMenuQueryMapper, userRoleQueryMapper, permissionViewAppService, typeResolutionService);
        TenantContextHolder.setTenantId(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("perm 串下发白名单包含前端消费的独立权限码（防再犯回归锁，T-PERM-025 P0 教训）")
    void effectivePermissionWhitelistCoversFrontendConsumedCodes() throws Exception {
        // 新增独立权限码若漏加 EFFECTIVE_PERMISSION_CODE_RESOURCE_TYPES，前端 hasPerms 永远拿不到该串
        //（页面按钮/路由静默消失，DB 授权真实存在也无效）——反射锁定关键成员防止再漏。
        var field = UserMenuQueryServiceImpl.class.getDeclaredField("EFFECTIVE_PERMISSION_CODE_RESOURCE_TYPES");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.List<String> whitelist = (java.util.List<String>) field.get(null);
        // 前端 grep 权限串全集（2026-08-28）：任一缺失即对应页面按钮真实链路隐藏
        org.assertj.core.api.Assertions.assertThat(whitelist).contains(
            "ORG", "USER", "ROLE", "TYPE_DEFINITION", "RESOURCE", "OPERATION", "CONDITION",
            "CONFLICT_RULE", "DEPENDENCY", "DOMAIN", "SERVICE", "SYSTEM_CONFIG", "OPERATION_LOG",
            "PERMISSION_CHANGE_LOG");
    }

    @Nested
    @DisplayName("loadUserRolesAndPermissions")
    class LoadUserRolesAndPermissions {

        @Test
        @DisplayName("聚合组织关系、角色名与权限码")
        void aggregatesUserContext() {
            when(userMenuQueryMapper.selectUserOrgsByUserIds(TENANT, List.of(USER)))
                .thenReturn(List.of(
                    new UserOrgProjection(USER, 10L, true),
                    new UserOrgProjection(USER, 20L, false)));
            when(typeResolutionService.resolveUserId(TENANT, SUBJECT, "100")).thenReturn(9000L);
            when(userRoleQueryMapper.selectUserRoleProjections(eq(TENANT), eq(9000L), any()))
                .thenReturn(List.of(new UserRoleProjection(null, "ROLE", 6, "r-1", "基础管理员", null, null, null)));
            when(permissionViewAppService.getEffectivePermissionCodes(eq(TENANT), any(UserEffectivePermissionCodesReq.class)))
                .thenReturn(new UserEffectivePermissionCodesResp(List.of("USER:VIEW", "ORG:VIEW")));

            UserInfoResp result = service.loadUserRolesAndPermissions(USER);

            assertThat(result.userId()).isEqualTo(USER);
            assertThat(result.orgs()).hasSize(2);
            assertThat(result.orgs().get(0).orgId()).isEqualTo(10L);
            assertThat(result.orgs().get(0).isPrimary()).isTrue();
            assertThat(result.roles()).extracting(UserInfoResp.RoleInfo::roleName)
                .containsExactly("基础管理员");
            assertThat(result.permissions()).containsExactly("USER:VIEW", "ORG:VIEW");
        }

        @Test
        @DisplayName("角色或权限加载失败降级为空列表（登录链路容错）")
        void roleOrPermissionFailure_degradesToEmpty() {
            when(userMenuQueryMapper.selectUserOrgsByUserIds(TENANT, List.of(USER)))
                .thenReturn(List.of());
            when(typeResolutionService.resolveUserId(TENANT, SUBJECT, "100"))
                .thenThrow(new RuntimeException("db down"));

            UserInfoResp result = service.loadUserRolesAndPermissions(USER);

            assertThat(result.roles()).isEmpty();
            assertThat(result.permissions()).isEmpty();
            assertThat(result.orgs()).isEmpty();
        }
    }

    @Nested
    @DisplayName("buildUserMenuTree（v3.5 §4.1 派生公式）")
    class BuildUserMenuTree {

        private void mockUserContext() {
            when(userMenuQueryMapper.selectUserOrgsByUserIds(TENANT, List.of(USER))).thenReturn(List.of());
            when(typeResolutionService.resolveUserId(TENANT, SUBJECT, "100")).thenReturn(9000L);
            when(userRoleQueryMapper.selectUserRoleProjections(eq(TENANT), eq(9000L), any()))
                .thenReturn(List.of());
            when(permissionViewAppService.getEffectivePermissionCodes(eq(TENANT), any(UserEffectivePermissionCodesReq.class)))
                .thenReturn(new UserEffectivePermissionCodesResp(List.of()));
        }

        @Test
        @DisplayName("业务菜单按资源实例匹配派生，纯展示全员可见，DIR 剪枝，HIDDEN 排除")
        void derivedVisibility() {
            mockUserContext();
            when(userMenuQueryMapper.selectMenus(TENANT)).thenReturn(List.of(
                new MenuProjection(1L, null, "DIR", "系统管理", "/system", "setting", 1, 1, null, null),
                new MenuProjection(2L, 1L, "MENU", "用户管理", "/system/user", null, 2, 1, "USER", "100"),
                new MenuProjection(3L, 1L, "MENU", "组织管理", "/system/org", null, 3, 1, "ORG", "200"),
                new MenuProjection(4L, null, "MENU", "纯展示菜单", "/plain", null, 4, 1, null, null),
                new MenuProjection(5L, null, "HIDDEN", "隐藏路由", "/hidden", null, 5, 1, "USER", "100"),
                new MenuProjection(6L, null, "MENU", "停用菜单", "/disabled", null, 6, 0, "USER", "100"),
                new MenuProjection(7L, 1L, "MENU", "无权限菜单", "/no-perm", null, 7, 1, "ORG", "300")));
            // 有效资源访问事实：USER:100 有权限（实例匹配），ORG 类型 scopeAll 全范围
            when(permissionViewAppService.getEffectiveResourceAccess(eq(TENANT), any(UserEffectivePermissionCodesReq.class)))
                .thenReturn(new PermissionViewAppService.EffectiveResourceAccess(
                    Set.of(2), Set.of(9001L))); // typeValue 2 = ORG（scopeAll），USER:100 → entity 9001
            when(typeResolutionService.batchResolveTypeValues(eq(TENANT), eq("resource_type"), anySet()))
                .thenReturn(Map.of("USER", 1, "ORG", 2));
            when(typeResolutionService.batchResolveResourceIds(eq(TENANT), anyList()))
                .thenReturn(Map.of(
                    new ResourceResolveKey("USER", "100", null, null), 9001L,
                    new ResourceResolveKey("ORG", "200", null, null), 9002L,
                    new ResourceResolveKey("ORG", "300", null, null), 9003L));

            UserMenuResp result = service.buildUserMenuTree(USER);

            // 可见：DIR(1)（子菜单可见 → 剪枝保留）；菜单 2（USER:100 实例匹配 9001）；
            // 菜单 3 与 7（ORG 类型 scopeAll 全范围 → 该类型所有资源可见）；菜单 4（纯展示）
            // 不可见：5（HIDDEN 不进 menus[]）、6（status=0）
            assertThat(result.menus()).hasSize(2);
            UserMenuResp.MenuRouteItem dir = result.menus().get(0);
            assertThat(dir.path()).isEqualTo("/system");
            assertThat(dir.name()).endsWith("Parent");
            assertThat(dir.component()).isNull();
            assertThat(dir.meta().title()).isEqualTo("系统管理");
            assertThat(dir.meta().showLink()).isTrue();
            assertThat(dir.meta().keepAlive()).isFalse();
            assertThat(dir.meta().auths()).isNull();
            assertThat(dir.children()).extracting(UserMenuResp.MenuRouteItem::path)
                .containsExactly("/system/user", "/system/org", "/no-perm");
            // 纯展示菜单在顶层
            assertThat(result.menus()).extracting(UserMenuResp.MenuRouteItem::path)
                .contains("/plain");
            assertThat(result.menus()).extracting(UserMenuResp.MenuRouteItem::path)
                .doesNotContain("/hidden", "/disabled");
        }

        @Test
        @DisplayName("全业务菜单：未解析资源且非 scopeAll → 不可见（fail-closed）")
        void unresolvedResource_failClosed() {
            mockUserContext();
            when(userMenuQueryMapper.selectMenus(TENANT)).thenReturn(List.of(
                new MenuProjection(1L, null, "MENU", "无投影资源", "/x", null, 1, 1, "USER", "999")));
            when(permissionViewAppService.getEffectiveResourceAccess(eq(TENANT), any(UserEffectivePermissionCodesReq.class)))
                .thenReturn(new PermissionViewAppService.EffectiveResourceAccess(Set.of(), Set.of()));
            when(typeResolutionService.batchResolveTypeValues(eq(TENANT), eq("resource_type"), anySet()))
                .thenReturn(Map.of("USER", 1));
            when(typeResolutionService.batchResolveResourceIds(eq(TENANT), anyList()))
                .thenReturn(Map.of()); // 资源无投影

            UserMenuResp result = service.buildUserMenuTree(USER);

            assertThat(result.menus()).isEmpty();
        }

        @Test
        @DisplayName("DIR 无可见子节点时剪枝（不渲染空目录）")
        void dirWithNoVisibleChild_pruned() {
            mockUserContext();
            when(userMenuQueryMapper.selectMenus(TENANT)).thenReturn(List.of(
                new MenuProjection(1L, null, "DIR", "空目录", "/empty", null, 1, 1, null, null),
                new MenuProjection(2L, 1L, "MENU", "不可见子", "/empty/x", null, 2, 1, "USER", "999")));
            when(permissionViewAppService.getEffectiveResourceAccess(eq(TENANT), any(UserEffectivePermissionCodesReq.class)))
                .thenReturn(new PermissionViewAppService.EffectiveResourceAccess(Set.of(), Set.of()));
            when(typeResolutionService.batchResolveTypeValues(eq(TENANT), eq("resource_type"), anySet()))
                .thenReturn(Map.of("USER", 1));
            when(typeResolutionService.batchResolveResourceIds(eq(TENANT), anyList()))
                .thenReturn(Map.of());

            UserMenuResp result = service.buildUserMenuTree(USER);

            assertThat(result.menus()).isEmpty();
        }

        @Test
        @DisplayName("角色/权限加载失败仍返回菜单树（登录链路容错）")
        void roleFailure_stillBuildsMenuTree() {
            when(userMenuQueryMapper.selectUserOrgsByUserIds(TENANT, List.of(USER)))
                .thenThrow(new RuntimeException("db down"));
            when(userMenuQueryMapper.selectMenus(TENANT)).thenReturn(List.of());

            UserMenuResp result = service.buildUserMenuTree(USER);

            assertThat(result.menus()).isEmpty();
            assertThat(result.roles()).isEmpty();
            assertThat(result.permissions()).isEmpty();
        }

        @Test
        @DisplayName("有效资源访问查询失败时降级为纯展示菜单（不抛异常）")
        void resourceAccessFailure_degradesToPlainMenus() {
            mockUserContext();
            when(userMenuQueryMapper.selectMenus(TENANT)).thenReturn(List.of(
                new MenuProjection(1L, null, "MENU", "业务菜单", "/biz", null, 1, 1, "USER", "100"),
                new MenuProjection(2L, null, "MENU", "纯展示", "/plain", null, 2, 1, null, null)));
            when(permissionViewAppService.getEffectiveResourceAccess(eq(TENANT), any(UserEffectivePermissionCodesReq.class)))
                .thenThrow(new RuntimeException("engine down"));

            UserMenuResp result = service.buildUserMenuTree(USER);

            // 纯展示菜单仍可见，业务菜单降级不可见
            assertThat(result.menus()).extracting(UserMenuResp.MenuRouteItem::path)
                .containsExactly("/plain");
        }

        @Test
        @DisplayName("半缺失资源链接（resource_type 非空、resource_code 为空）无 scopeAll → fail-closed 不可见")
        void halfMissingResourceLink_withoutScopeAll_failClosed() {
            mockUserContext();
            when(userMenuQueryMapper.selectMenus(TENANT)).thenReturn(List.of(
                new MenuProjection(1L, null, "MENU", "缺实例菜单", "/missing-instance", null, 1, 1, "USER", null)));
            // 该类型无 scopeAll，且 code 为空 resolve 不到实例
            when(permissionViewAppService.getEffectiveResourceAccess(eq(TENANT), any(UserEffectivePermissionCodesReq.class)))
                .thenReturn(new PermissionViewAppService.EffectiveResourceAccess(Set.of(), Set.of()));
            when(typeResolutionService.batchResolveTypeValues(eq(TENANT), eq("resource_type"), anySet()))
                .thenReturn(Map.of("USER", 1));
            when(typeResolutionService.batchResolveResourceIds(eq(TENANT), anyList()))
                .thenReturn(Map.of());

            UserMenuResp result = service.buildUserMenuTree(USER);

            // 不得按纯展示 fail-open 全员可见
            assertThat(result.menus()).isEmpty();
        }

        @Test
        @DisplayName("半缺失资源链接（resource_code 为空）但该资源类型有 scopeAll → 可见")
        void halfMissingResourceLink_withScopeAll_visible() {
            mockUserContext();
            when(userMenuQueryMapper.selectMenus(TENANT)).thenReturn(List.of(
                new MenuProjection(1L, null, "MENU", "类型级菜单", "/type-level", null, 1, 1, "ORG", null)));
            // ORG 类型 scopeAll 全范围授权（typeValue=2）
            when(permissionViewAppService.getEffectiveResourceAccess(eq(TENANT), any(UserEffectivePermissionCodesReq.class)))
                .thenReturn(new PermissionViewAppService.EffectiveResourceAccess(Set.of(2), Set.of()));
            when(typeResolutionService.batchResolveTypeValues(eq(TENANT), eq("resource_type"), anySet()))
                .thenReturn(Map.of("ORG", 2));
            when(typeResolutionService.batchResolveResourceIds(eq(TENANT), anyList()))
                .thenReturn(Map.of());

            UserMenuResp result = service.buildUserMenuTree(USER);

            assertThat(result.menus()).extracting(UserMenuResp.MenuRouteItem::path)
                .containsExactly("/type-level");
        }

        @Test
        @DisplayName("EXTERNAL/IFRAME 且 resource_type 为空 → fail-closed 不可见（不按纯展示放行）")
        void externalIframe_withoutResource_failClosed() {
            mockUserContext();
            when(userMenuQueryMapper.selectMenus(TENANT)).thenReturn(List.of(
                new MenuProjection(1L, null, "EXTERNAL", "外链", "https://x", null, 1, 1, null, null),
                new MenuProjection(2L, null, "IFRAME", "内嵌", "/iframe", null, 2, 1, null, null)));
            // 两个菜单 resource_type 均空 → 不进业务匹配、不入 visible（fail-closed）；
            // businessMenus 为空 → 提前返回，不触发 getEffectiveResourceAccess / 类型解析

            UserMenuResp result = service.buildUserMenuTree(USER);

            assertThat(result.menus()).isEmpty();
        }

        @Test
        @DisplayName("未知 menu_type + 关联可访问资源 → 仍不可见（fail-closed，不按业务菜单误放行）")
        void unknownMenuType_withAccessibleResource_failClosed() {
            mockUserContext();
            when(userMenuQueryMapper.selectMenus(TENANT)).thenReturn(List.of(
                new MenuProjection(1L, null, "GADGET", "未知类型", "/gadget", null, 1, 1, "USER", "100")));
            // 即便 USER:100 是用户可访问的资源实例，未知 menu_type 也不得进入业务匹配

            UserMenuResp result = service.buildUserMenuTree(USER);

            assertThat(result.menus()).isEmpty();
        }

        @Test
        @DisplayName("未知 menu_type + resource_type 为空 → 不可见（fail-closed，不按纯展示放行）")
        void unknownMenuType_withoutResource_failClosed() {
            mockUserContext();
            when(userMenuQueryMapper.selectMenus(TENANT)).thenReturn(List.of(
                new MenuProjection(1L, null, "GADGET", "未知类型", "/gadget", null, 1, 1, null, null)));
            // 未知类型既非纯展示 MENU 也非 DIR，不得全员可见

            UserMenuResp result = service.buildUserMenuTree(USER);

            assertThat(result.menus()).isEmpty();
        }

        @Test
        @DisplayName("EXTERNAL/IFRAME 派生同业务 MENU：资源实例匹配即可见，frameSrc 取 path")
        void externalIframe_withResourceMatch_visible() {
            mockUserContext();
            when(userMenuQueryMapper.selectMenus(TENANT)).thenReturn(List.of(
                new MenuProjection(1L, null, "EXTERNAL", "外链", "https://x", null, 1, 1, "USER", "100"),
                new MenuProjection(2L, null, "IFRAME", "内嵌", "/iframe", null, 2, 1, "USER", "100")));
            when(permissionViewAppService.getEffectiveResourceAccess(eq(TENANT), any(UserEffectivePermissionCodesReq.class)))
                .thenReturn(new PermissionViewAppService.EffectiveResourceAccess(Set.of(), Set.of(9001L)));
            when(typeResolutionService.batchResolveTypeValues(eq(TENANT), eq("resource_type"), anySet()))
                .thenReturn(Map.of("USER", 1));
            when(typeResolutionService.batchResolveResourceIds(eq(TENANT), anyList()))
                .thenReturn(Map.of(new ResourceResolveKey("USER", "100", null, null), 9001L));

            UserMenuResp result = service.buildUserMenuTree(USER);

            assertThat(result.menus()).hasSize(2);
            assertThat(result.menus()).extracting(UserMenuResp.MenuRouteItem::path)
                .containsExactly("https://x", "/iframe");
            // external/iframe 时 frameSrc = path（外链跳转 / iframe 嵌入地址）
            assertThat(result.menus()).extracting(m -> m.meta().frameSrc())
                .containsExactly("https://x", "/iframe");
        }

        @Test
        @DisplayName("DIR 带 resource_type 仍由子节点决定：自身资源不匹配不丢子树")
        void dirWithResourceType_keptWhenChildVisible() {
            mockUserContext();
            when(userMenuQueryMapper.selectMenus(TENANT)).thenReturn(List.of(
                // DIR 自身带 USER:999（无可访问资源），不得按业务菜单 fail-closed 丢整棵子树
                new MenuProjection(1L, null, "DIR", "目录", "/dir", null, 1, 1, "USER", "999"),
                new MenuProjection(2L, 1L, "MENU", "子菜单", "/dir/child", null, 2, 1, "USER", "100")));
            when(permissionViewAppService.getEffectiveResourceAccess(eq(TENANT), any(UserEffectivePermissionCodesReq.class)))
                .thenReturn(new PermissionViewAppService.EffectiveResourceAccess(Set.of(), Set.of(9001L)));
            when(typeResolutionService.batchResolveTypeValues(eq(TENANT), eq("resource_type"), anySet()))
                .thenReturn(Map.of("USER", 1));
            when(typeResolutionService.batchResolveResourceIds(eq(TENANT), anyList()))
                .thenReturn(Map.of(new ResourceResolveKey("USER", "100", null, null), 9001L));

            UserMenuResp result = service.buildUserMenuTree(USER);

            assertThat(result.menus()).hasSize(1);
            assertThat(result.menus().get(0).path()).isEqualTo("/dir");
            assertThat(result.menus().get(0).children()).extracting(UserMenuResp.MenuRouteItem::path)
                .containsExactly("/dir/child");
        }

        @Test
        @DisplayName("DIR 带 resource_type 但无可见子节点 → 仍剪枝（不因自身资源渲染空目录）")
        void dirWithResourceType_noVisibleChild_pruned() {
            mockUserContext();
            when(userMenuQueryMapper.selectMenus(TENANT)).thenReturn(List.of(
                new MenuProjection(1L, null, "DIR", "目录", "/dir", null, 1, 1, "USER", "999"),
                new MenuProjection(2L, 1L, "MENU", "子菜单", "/dir/child", null, 2, 1, "USER", "999")));
            when(permissionViewAppService.getEffectiveResourceAccess(eq(TENANT), any(UserEffectivePermissionCodesReq.class)))
                .thenReturn(new PermissionViewAppService.EffectiveResourceAccess(Set.of(), Set.of()));
            when(typeResolutionService.batchResolveTypeValues(eq(TENANT), eq("resource_type"), anySet()))
                .thenReturn(Map.of("USER", 1));
            when(typeResolutionService.batchResolveResourceIds(eq(TENANT), anyList()))
                .thenReturn(Map.of());

            UserMenuResp result = service.buildUserMenuTree(USER);

            assertThat(result.menus()).isEmpty();
        }
    }
}
