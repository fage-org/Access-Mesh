package cn.ac.fage.accessmesh.access.application.query;

import cn.ac.fage.accessmesh.access.admin.dto.auth.UserInfoResp;
import cn.ac.fage.accessmesh.access.admin.dto.auth.UserMenuResp;
import cn.ac.fage.accessmesh.access.application.query.impl.UserMenuQueryServiceImpl;
import cn.ac.fage.accessmesh.access.application.query.mapper.UserMenuQueryMapper;
import cn.ac.fage.accessmesh.access.application.query.projection.MenuProjection;
import cn.ac.fage.accessmesh.access.application.query.projection.UserOrgProjection;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleListReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.UserRolesResp;
import cn.ac.fage.accessmesh.access.permission.service.PermissionViewAppService;
import cn.ac.fage.accessmesh.access.permission.service.UserManageAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
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
 * 覆盖菜单树构建（HIDDEN 排除、祖先链补全、status 过滤、排序、schema 收敛默认值）、
 * 菜单可见性批量过滤（业务键解析 + engine 判定映射）与角色/权限聚合容错。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class UserMenuQueryServiceImplTest {

    private static final Long TENANT = 1L;
    private static final Long USER = 100L;

    @Mock private UserMenuQueryMapper userMenuQueryMapper;
    @Mock private UserManageAppService userManageAppService;
    @Mock private PermissionViewAppService permissionViewAppService;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private PermQueryEngine engine;

    private UserMenuQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserMenuQueryServiceImpl(
            userMenuQueryMapper, userManageAppService, permissionViewAppService,
            typeResolutionService, engine);
        TenantContextHolder.setTenantId(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Nested
    @DisplayName("loadUserRolesAndPermissions")
    class LoadUserRolesAndPermissions {

        @Test
        @DisplayName("聚合组织关系、角色名与权限码")
        void aggregatesUserContext() {
            when(userMenuQueryMapper.selectUserOrgsByUserIds(TENANT, List.of(USER)))
                .thenReturn(List.of(
                    new UserOrgProjection(10L, true),
                    new UserOrgProjection(20L, false)));
            when(userManageAppService.getUserRoles(eq(TENANT), any(UserRoleListReq.class)))
                .thenReturn(new UserRolesResp(LocalProjectionOwner.SUBJECT_ADMIN_USER, "100",
                    List.of(new UserRolesResp.RoleSummary("r-1", "基础管理员", "BASIC_ROLE",
                        "BASIC_ROLE", null, null, null, null))));
            when(permissionViewAppService.getEffectivePermissionCodes(eq(TENANT), any(UserEffectivePermissionCodesReq.class)))
                .thenReturn(new UserEffectivePermissionCodesResp(List.of("ADMIN_USER:VIEW", "ADMIN_ORG:VIEW")));

            UserInfoResp result = service.loadUserRolesAndPermissions(USER);

            assertThat(result.userId()).isEqualTo(USER);
            assertThat(result.orgs()).hasSize(2);
            assertThat(result.orgs().get(0).orgId()).isEqualTo(10L);
            assertThat(result.orgs().get(0).isPrimary()).isTrue();
            assertThat(result.roles()).extracting(UserInfoResp.RoleInfo::roleName)
                .containsExactly("基础管理员");
            assertThat(result.permissions()).containsExactly("ADMIN_USER:VIEW", "ADMIN_ORG:VIEW");
        }

        @Test
        @DisplayName("角色或权限加载失败降级为空列表（登录链路容错）")
        void roleOrPermissionFailure_degradesToEmpty() {
            when(userMenuQueryMapper.selectUserOrgsByUserIds(TENANT, List.of(USER)))
                .thenReturn(List.of());
            when(userManageAppService.getUserRoles(eq(TENANT), any(UserRoleListReq.class)))
                .thenThrow(new RuntimeException("perm service down"));

            UserInfoResp result = service.loadUserRolesAndPermissions(USER);

            assertThat(result.roles()).isEmpty();
            assertThat(result.permissions()).isEmpty();
            assertThat(result.orgs()).isEmpty();
        }
    }

    @Nested
    @DisplayName("buildUserMenuTree")
    class BuildUserMenuTree {

        @Test
        @DisplayName("构建菜单树：DIR 父 + 子菜单 + 祖先链补全 + 默认值 + status/sort 过滤")
        void buildsMenuTree() {
            when(userMenuQueryMapper.selectUserOrgsByUserIds(TENANT, List.of(USER))).thenReturn(List.of());
            when(userManageAppService.getUserRoles(eq(TENANT), any(UserRoleListReq.class)))
                .thenReturn(new UserRolesResp(LocalProjectionOwner.SUBJECT_ADMIN_USER, "100", List.of()));
            when(permissionViewAppService.getEffectivePermissionCodes(eq(TENANT), any(UserEffectivePermissionCodesReq.class)))
                .thenReturn(new UserEffectivePermissionCodesResp(List.of()));
            when(userMenuQueryMapper.selectMenus(TENANT)).thenReturn(List.of(
                new MenuProjection(1L, null, "DIR", "系统管理", "/system", "setting", 1, 1, null, null),
                new MenuProjection(2L, 1L, "MENU", "用户管理", "/system/user", null, 2, 1, null, null),
                new MenuProjection(3L, 1L, "HIDDEN", "隐藏路由", "/hidden", null, 3, 1, null, null),
                new MenuProjection(4L, null, "MENU", "停用菜单", "/disabled", null, 4, 0, null, null),
                new MenuProjection(5L, null, "EXTERNAL", "外链", "https://example.com", null, 5, 1, null, null)));
            when(typeResolutionService.resolveUserId(TENANT, LocalProjectionOwner.SUBJECT_ADMIN_USER, "100"))
                .thenReturn(9000L);
            when(typeResolutionService.batchResolveResourceIds(eq(TENANT), anyList()))
                .thenReturn(Map.of(
                    new cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveKey(
                        "ADMIN_MENU", "1", null, null), 1001L,
                    new cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveKey(
                        "ADMIN_MENU", "2", null, null), 1002L,
                    new cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveKey(
                        "ADMIN_MENU", "5", null, null), 1005L));
            when(engine.getDeniedIds(eq(TENANT), eq(9000L), eq("ADMIN_MENU"), anySet(), eq("VIEW")))
                .thenReturn(new java.util.LinkedHashSet<>(List.of(1005L))); // 5 → denied

            UserMenuResp result = service.buildUserMenuTree(USER);

            assertThat(result.roles()).isEmpty();
            // 顶层：DIR(1) 可见（子 2 有权限 → 父链补全）；停用(4)被过滤；外链(5)被权限拒绝
            assertThat(result.menus()).hasSize(1);
            UserMenuResp.MenuRouteItem dir = result.menus().get(0);
            assertThat(dir.path()).isEqualTo("/system");
            assertThat(dir.name()).endsWith("Parent"); // DIR 路由名 Parent 后缀
            assertThat(dir.component()).isNull();       // schema 收敛默认值
            assertThat(dir.meta().title()).isEqualTo("系统管理");
            assertThat(dir.meta().showLink()).isTrue();
            assertThat(dir.meta().keepAlive()).isFalse();
            assertThat(dir.meta().auths()).isNull();
            assertThat(dir.children()).hasSize(1);
            assertThat(dir.children().get(0).path()).isEqualTo("/system/user");
            // HIDDEN(3) 不进 menus[]（含祖先链）
            assertThat(result.menus().stream().flatMap(m -> m.children().stream()))
                .noneMatch(c -> "/hidden".equals(c.path()));
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
        @DisplayName("菜单权限过滤失败降级为空菜单树（不抛异常）")
        void permissionFilterFailure_degradesToEmptyMenus() {
            when(userMenuQueryMapper.selectUserOrgsByUserIds(TENANT, List.of(USER))).thenReturn(List.of());
            when(userManageAppService.getUserRoles(eq(TENANT), any(UserRoleListReq.class)))
                .thenReturn(new UserRolesResp(LocalProjectionOwner.SUBJECT_ADMIN_USER, "100", List.of()));
            when(permissionViewAppService.getEffectivePermissionCodes(eq(TENANT), any(UserEffectivePermissionCodesReq.class)))
                .thenReturn(new UserEffectivePermissionCodesResp(List.of()));
            when(userMenuQueryMapper.selectMenus(TENANT)).thenReturn(List.of(
                new MenuProjection(1L, null, "MENU", "用户管理", "/system/user", null, 1, 1, null, null)));
            when(typeResolutionService.resolveUserId(TENANT, LocalProjectionOwner.SUBJECT_ADMIN_USER, "100"))
                .thenReturn(9000L);
            when(typeResolutionService.batchResolveResourceIds(eq(TENANT), anyList()))
                .thenThrow(new RuntimeException("engine down"));

            UserMenuResp result = service.buildUserMenuTree(USER);

            assertThat(result.menus()).isEmpty();
        }
    }
}
