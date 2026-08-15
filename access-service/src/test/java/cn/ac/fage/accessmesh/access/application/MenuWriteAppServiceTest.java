package cn.ac.fage.accessmesh.access.application;

import cn.ac.fage.accessmesh.access.admin.dto.req.MenuUpdateReq;
import cn.ac.fage.accessmesh.access.admin.entity.SysMenu;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.admin.service.domain.MenuDomainService;
import cn.ac.fage.accessmesh.access.application.impl.MenuWriteAppServiceImpl;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-ACCESS-005 评审 P1/P2 修复：菜单类型转换的投影清理（非按钮→按钮删除旧 ADMIN_MENU）、
 * 可选字段部分更新语义（null 跳过）、删除日志 entityId 使用投影主键。
 */
@ExtendWith(MockitoExtension.class)
class MenuWriteAppServiceTest {

    private static final Long TENANT = 1L;
    private static final Long OPERATOR = 9L;
    private static final Long MENU_ID = 10L;

    @Mock private MenuDomainService menuDomainService;
    @Mock private AdminPermissionValidator permissionValidator;
    @Mock private LocalProjectionDomainService localProjectionDomainService;
    @Mock private AuditDomainService auditDomainService;

    private MenuWriteAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MenuWriteAppServiceImpl(
            menuDomainService,
            permissionValidator,
            localProjectionDomainService,
            auditDomainService
        );
        TenantContextHolder.setTenantId(TENANT);
        AccessRequestContext.bind(RequestContext.user(TENANT, OPERATOR));
    }

    @AfterEach
    void tearDown() {
        AccessRequestContext.clear();
        TenantContextHolder.clear();
    }

    private SysMenu menu(String menuType) {
        SysMenu menu = new SysMenu();
        menu.setId(MENU_ID);
        menu.setTenantId(TENANT);
        menu.setParentId(0L);
        menu.setMenuType(menuType);
        menu.setName("菜单X");
        menu.setPath("/x");
        menu.setComponent("x/index");
        menu.setPermCode("X:VIEW");
        menu.setIcon("x");
        menu.setSortOrder(3);
        menu.setVisible(true);
        menu.setStatus(1);
        return menu;
    }

    @Test
    @DisplayName("非按钮 → 按钮：删除旧 ADMIN_MENU 投影（旧授权不残留），不再 upsert")
    void menuToButtonDeletesProjection() {
        when(menuDomainService.selectValidById(TENANT, MENU_ID)).thenReturn(menu("1"));
        when(localProjectionDomainService.findAdminMenuResourceId(TENANT, MENU_ID)).thenReturn(300L);

        service.updateMenu(new MenuUpdateReq(MENU_ID, 3, null, null, null, null, null, null, null, null, null));

        verify(localProjectionDomainService).deleteAdminMenu(TENANT, MENU_ID);
        verify(localProjectionDomainService, never())
            .upsertAdminMenu(anyLong(), anyLong(), anyString(), anyLong(), any(), any());
        // 删除日志 entityId 用投影主键
        verify(auditDomainService).recordChangeLog(any(), any());
    }

    @Test
    @DisplayName("按钮 → 非按钮：upsert 投影（原无投影，直接创建）")
    void buttonToMenuUpsertsProjection() {
        when(menuDomainService.selectValidById(TENANT, MENU_ID)).thenReturn(menu("3"));
        when(localProjectionDomainService.upsertAdminMenu(
            eq(TENANT), eq(MENU_ID), eq("菜单X"), eq(0L), eq(1), eq(3))).thenReturn(400L);

        service.updateMenu(new MenuUpdateReq(MENU_ID, 1, null, null, null, null, null, null, null, null, null));

        verify(localProjectionDomainService).upsertAdminMenu(anyLong(), anyLong(), anyString(), anyLong(), any(), any());
        verify(localProjectionDomainService, never()).deleteAdminMenu(anyLong(), anyLong());
    }

    @Test
    @DisplayName("菜单类型不变：正常 upsert 投影，不删除")
    void sameTypeKeepsUpsert() {
        when(menuDomainService.selectValidById(TENANT, MENU_ID)).thenReturn(menu("1"));

        service.updateMenu(new MenuUpdateReq(MENU_ID, 1, null, null, null, null, null, null, null, null, null));

        verify(localProjectionDomainService).upsertAdminMenu(anyLong(), anyLong(), anyString(), anyLong(), any(), any());
        verify(localProjectionDomainService, never()).deleteAdminMenu(anyLong(), anyLong());
    }

    @Test
    @DisplayName("部分更新：省略 name/path/component/perms/icon/sort/visible 时保留原值")
    void partialUpdateKeepsUnchangedFields() {
        when(menuDomainService.selectValidById(TENANT, MENU_ID)).thenReturn(menu("1"));

        service.updateMenu(new MenuUpdateReq(MENU_ID, null, null, null, null, null, null, null, null, null, null));

        ArgumentCaptor<SysMenu> captor = ArgumentCaptor.forClass(SysMenu.class);
        verify(menuDomainService).update(captor.capture());
        SysMenu updated = captor.getValue();
        assertThat(updated.getName()).isEqualTo("菜单X");   // 省略 name → 保留（原实现清空）
        assertThat(updated.getPath()).isEqualTo("/x");       // 省略 path → 保留（原实现清空）
        assertThat(updated.getComponent()).isEqualTo("x/index");
        assertThat(updated.getPermCode()).isEqualTo("X:VIEW");
        assertThat(updated.getIcon()).isEqualTo("x");
        assertThat(updated.getSortOrder()).isEqualTo(3);
        assertThat(updated.getVisible()).isTrue();           // 省略 visible → 保留（原实现强制 false）
        assertThat(updated.getStatus()).isEqualTo(1);
        // 投影按更新后事实同步（名称保持旧值）
        verify(localProjectionDomainService).upsertAdminMenu(
            eq(TENANT), eq(MENU_ID), eq("菜单X"), eq(0L), eq(1), eq(3));
    }

    @Test
    @DisplayName("部分更新：提供 name/visible/sort 时更新对应字段")
    void partialUpdateAppliesProvidedFields() {
        when(menuDomainService.selectValidById(TENANT, MENU_ID)).thenReturn(menu("1"));

        service.updateMenu(new MenuUpdateReq(MENU_ID, null, "新名称", null, null, null, null, null, 9, 0, null));

        ArgumentCaptor<SysMenu> captor = ArgumentCaptor.forClass(SysMenu.class);
        verify(menuDomainService).update(captor.capture());
        SysMenu updated = captor.getValue();
        assertThat(updated.getName()).isEqualTo("新名称");
        assertThat(updated.getSortOrder()).isEqualTo(9);
        assertThat(updated.getVisible()).isFalse();  // visible=0 → 隐藏
        assertThat(updated.getPath()).isEqualTo("/x"); // 未提供 → 保留
    }
}
