package cn.ac.fage.accessmesh.access.application;

import cn.ac.fage.accessmesh.access.admin.dto.req.MenuCreateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.MenuUpdateReq;
import cn.ac.fage.accessmesh.access.admin.entity.SysMenu;
import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.admin.service.domain.MenuDomainService;
import cn.ac.fage.accessmesh.access.application.impl.MenuWriteAppServiceImpl;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-ACCESS-015：菜单写链路 v3.5 终态语义。
 * <p>
 * 覆盖：五值枚举全量投影（无 BUTTON 短路）、path/resource 唯一性预查错误码、
 * 可选字段部分更新（null 跳过）、sourceService 缺省与不可改、
 * 删除日志 entityId 使用投影主键。
 * </p>
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
    @Mock private TreeWriteLockSupport treeWriteLockSupport;

    private MenuWriteAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MenuWriteAppServiceImpl(
            menuDomainService,
            permissionValidator,
            localProjectionDomainService,
            auditDomainService,
            treeWriteLockSupport
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
        menu.setDisplayName("菜单X");
        menu.setPath("/x");
        menu.setIcon("x");
        menu.setSortOrder(3);
        menu.setStatus(1);
        menu.setResourceType("USER");
        menu.setResourceCode("5");
        menu.setSourceService("access-service");
        return menu;
    }

    @Test
    @DisplayName("创建：五值枚举（含 HIDDEN/DIR）全部投影 MENU，无 BUTTON 短路")
    void createProjectsAllMenuTypes() {
        when(menuDomainService.calculateDepth(eq(TENANT), isNull())).thenReturn(1);
        when(localProjectionDomainService.upsertAdminMenu(
            eq(TENANT), isNull(), eq("菜单X"), eq(0L), eq(1), eq(0))).thenReturn(400L);

        service.createMenu(new MenuCreateReq("HIDDEN", "菜单X", null, "/hidden/x",
            null, null, null, null, null, null));

        verify(localProjectionDomainService).upsertAdminMenu(
            anyLong(), isNull(), eq("菜单X"), anyLong(), any(), any());
        verify(localProjectionDomainService, never()).deleteAdminMenu(anyLong(), anyLong());
        verify(auditDomainService).recordChangeLog(any(), any());
    }

    @Test
    @DisplayName("创建：path 冲突预查抛 MENU_PATH_EXISTS(10205)")
    void createPathConflictRejected() {
        when(menuDomainService.pathExists(TENANT, "/x", null)).thenReturn(true);

        assertThatThrownBy(() -> service.createMenu(new MenuCreateReq("MENU", "菜单X", null, "/x",
            null, null, null, null, null, null)))
            .isInstanceOf(BizException.class)
            .extracting(e -> ((BizException) e).getErrorCode())
            .isEqualTo(AdminErrorCode.MENU_PATH_EXISTS.getCode());
        verify(menuDomainService, never()).insert(any());
    }

    @Test
    @DisplayName("创建：资源关联冲突预查抛 MENU_RESOURCE_EXISTS(10206)")
    void createResourceConflictRejected() {
        when(menuDomainService.resourceExists(TENANT, "USER", "5", null)).thenReturn(true);

        assertThatThrownBy(() -> service.createMenu(new MenuCreateReq("MENU", "菜单X", null, null,
            null, null, null, "USER", "5", null)))
            .isInstanceOf(BizException.class)
            .extracting(e -> ((BizException) e).getErrorCode())
            .isEqualTo(AdminErrorCode.MENU_RESOURCE_EXISTS.getCode());
        verify(menuDomainService, never()).insert(any());
    }

    @Test
    @DisplayName("更新：菜单类型在五值内切换（MENU→DIR）仅投影不删除")
    void typeSwitchWithinFiveValuesUpsertsProjection() {
        when(menuDomainService.selectValidById(TENANT, MENU_ID)).thenReturn(menu("MENU"));
        when(menuDomainService.pathExists(eq(TENANT), eq("/x"), eq(MENU_ID))).thenReturn(false);
        when(menuDomainService.resourceExists(eq(TENANT), eq("USER"), eq("5"), eq(MENU_ID)))
            .thenReturn(false);
        when(localProjectionDomainService.upsertAdminMenu(
            eq(TENANT), eq(MENU_ID), eq("菜单X"), eq(0L), eq(1), eq(3))).thenReturn(400L);

        service.updateMenu(new MenuUpdateReq(MENU_ID, "DIR", null, null, null, null, null, null, null, null));

        verify(localProjectionDomainService).upsertAdminMenu(anyLong(), anyLong(), anyString(), anyLong(), any(), any());
        verify(localProjectionDomainService, never()).deleteAdminMenu(anyLong(), anyLong());
    }

    @Test
    @DisplayName("部分更新：省略 displayName/path/icon/sortOrder/status/resource 时保留原值")
    void partialUpdateKeepsUnchangedFields() {
        when(menuDomainService.selectValidById(TENANT, MENU_ID)).thenReturn(menu("MENU"));
        when(menuDomainService.pathExists(eq(TENANT), eq("/x"), eq(MENU_ID))).thenReturn(false);
        when(menuDomainService.resourceExists(eq(TENANT), eq("USER"), eq("5"), eq(MENU_ID)))
            .thenReturn(false);

        service.updateMenu(new MenuUpdateReq(MENU_ID, null, null, null, null, null, null, null, null, null));

        ArgumentCaptor<SysMenu> captor = ArgumentCaptor.forClass(SysMenu.class);
        verify(menuDomainService).update(captor.capture());
        SysMenu updated = captor.getValue();
        assertThat(updated.getDisplayName()).isEqualTo("菜单X");
        assertThat(updated.getPath()).isEqualTo("/x");
        assertThat(updated.getIcon()).isEqualTo("x");
        assertThat(updated.getSortOrder()).isEqualTo(3);
        assertThat(updated.getStatus()).isEqualTo(1);
        assertThat(updated.getResourceType()).isEqualTo("USER");
        assertThat(updated.getResourceCode()).isEqualTo("5");
        assertThat(updated.getSourceService()).isEqualTo("access-service"); // 不可改
        // 投影按更新后事实同步（名称保持旧值）
        verify(localProjectionDomainService).upsertAdminMenu(
            eq(TENANT), eq(MENU_ID), eq("菜单X"), eq(0L), eq(1), eq(3));
    }

    @Test
    @DisplayName("部分更新：提供 displayName/sortOrder/status 时更新对应字段")
    void partialUpdateAppliesProvidedFields() {
        when(menuDomainService.selectValidById(TENANT, MENU_ID)).thenReturn(menu("MENU"));
        when(menuDomainService.pathExists(eq(TENANT), eq("/x"), eq(MENU_ID))).thenReturn(false);
        when(menuDomainService.resourceExists(eq(TENANT), eq("USER"), eq("5"), eq(MENU_ID)))
            .thenReturn(false);

        service.updateMenu(new MenuUpdateReq(MENU_ID, null, "新名称", null, null, null, 9, 0, null, null));

        ArgumentCaptor<SysMenu> captor = ArgumentCaptor.forClass(SysMenu.class);
        verify(menuDomainService).update(captor.capture());
        SysMenu updated = captor.getValue();
        assertThat(updated.getDisplayName()).isEqualTo("新名称");
        assertThat(updated.getSortOrder()).isEqualTo(9);
        assertThat(updated.getStatus()).isEqualTo(0);  // 0=DISABLED（对齐 DDL）
        assertThat(updated.getPath()).isEqualTo("/x"); // 未提供 → 保留
    }

    @Test
    @DisplayName("更新：path 换成其他菜单已占用值抛 MENU_PATH_EXISTS(10205)")
    void updatePathConflictRejected() {
        when(menuDomainService.selectValidById(TENANT, MENU_ID)).thenReturn(menu("MENU"));
        when(menuDomainService.pathExists(TENANT, "/y", MENU_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.updateMenu(
            new MenuUpdateReq(MENU_ID, null, null, null, "/y", null, null, null, null, null)))
            .isInstanceOf(BizException.class)
            .extracting(e -> ((BizException) e).getErrorCode())
            .isEqualTo(AdminErrorCode.MENU_PATH_EXISTS.getCode());
        verify(menuDomainService, never()).update(any());
    }

    @Test
    @DisplayName("更新：保留自身 path/resource 不视为冲突（排除自身预查）")
    void updateKeepsOwnPathWithoutConflict() {
        when(menuDomainService.selectValidById(TENANT, MENU_ID)).thenReturn(menu("MENU"));
        // pathExists 排除自身后返回 false（未占用）
        when(menuDomainService.pathExists(eq(TENANT), eq("/x"), eq(MENU_ID))).thenReturn(false);
        when(menuDomainService.resourceExists(eq(TENANT), eq("USER"), eq("5"), eq(MENU_ID)))
            .thenReturn(false);
        when(localProjectionDomainService.upsertAdminMenu(
            eq(TENANT), eq(MENU_ID), eq("新名称"), eq(0L), eq(1), eq(3))).thenReturn(400L);

        service.updateMenu(new MenuUpdateReq(MENU_ID, null, "新名称", null, null, null, null, null, null, null));

        verify(menuDomainService).update(any(SysMenu.class));
    }

    @Test
    @DisplayName("创建：可选字符串字段空白规范化为 null（空串不落库、不参与唯一性判定）")
    void createNormalizesBlankStringsToNull() {
        when(menuDomainService.calculateDepth(eq(TENANT), isNull())).thenReturn(1);
        when(localProjectionDomainService.upsertAdminMenu(
            anyLong(), isNull(), eq("菜单X"), eq(0L), eq(1), eq(0))).thenReturn(400L);

        service.createMenu(new MenuCreateReq("MENU", "菜单X", null, "  ",
            " ", null, null, "", "", "  "));

        ArgumentCaptor<SysMenu> captor = ArgumentCaptor.forClass(SysMenu.class);
        verify(menuDomainService).insert(captor.capture());
        SysMenu inserted = captor.getValue();
        assertThat(inserted.getPath()).isNull();           // "  " → NULL（部分唯一索引不命中）
        assertThat(inserted.getIcon()).isNull();           // " " → NULL
        assertThat(inserted.getResourceType()).isNull();    // "" → NULL（读链路判纯展示）
        assertThat(inserted.getResourceCode()).isNull();
        assertThat(inserted.getSourceService()).isEqualTo("access-service"); // 空白 → 缺省
        // 空白不参与唯一性预查（不视为冲突值）
        verify(menuDomainService, never()).pathExists(anyLong(), anyString(), any());
        verify(menuDomainService, never()).resourceExists(anyLong(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("更新：path 传空白字符串等同未提供（跳过，保留原值）")
    void updateBlankPathKeepsOriginalValue() {
        when(menuDomainService.selectValidById(TENANT, MENU_ID)).thenReturn(menu("MENU"));
        when(menuDomainService.resourceExists(eq(TENANT), eq("USER"), eq("5"), eq(MENU_ID)))
            .thenReturn(false);
        when(localProjectionDomainService.upsertAdminMenu(
            eq(TENANT), eq(MENU_ID), eq("菜单X"), eq(0L), eq(1), eq(3))).thenReturn(400L);

        service.updateMenu(new MenuUpdateReq(MENU_ID, null, null, null, "  ", null, null, null, null, null));

        ArgumentCaptor<SysMenu> captor = ArgumentCaptor.forClass(SysMenu.class);
        verify(menuDomainService).update(captor.capture());
        assertThat(captor.getValue().getPath()).isEqualTo("/x"); // 保留原值
    }

    @Test
    @DisplayName("深度边界：父节点在第 5 层（calculateDepth=5）时创建被拒（新节点为第 6 层，10203）")
    void createUnderFifthLevelParentRejected() {
        when(menuDomainService.selectValidById(TENANT, 50L)).thenReturn(menu("MENU"));
        when(menuDomainService.calculateDepth(TENANT, 50L)).thenReturn(5);

        assertThatThrownBy(() -> service.createMenu(new MenuCreateReq("MENU", "第6层", 50L, "/lv6",
            null, null, null, null, null, null)))
            .isInstanceOf(BizException.class)
            .extracting(e -> ((BizException) e).getErrorCode())
            .isEqualTo(AdminErrorCode.MENU_DEPTH_EXCEEDED.getCode());
        verify(menuDomainService, never()).insert(any());
    }

    @Test
    @DisplayName("深度边界：父节点在第 4 层（calculateDepth=4）时创建通过（新节点为第 5 层）")
    void createUnderFourthLevelParentAllowed() {
        when(menuDomainService.selectValidById(TENANT, 40L)).thenReturn(menu("MENU"));
        when(menuDomainService.calculateDepth(TENANT, 40L)).thenReturn(4);
        when(localProjectionDomainService.upsertAdminMenu(
            anyLong(), isNull(), eq("第5层"), eq(40L), eq(1), eq(0))).thenReturn(400L);

        service.createMenu(new MenuCreateReq("MENU", "第5层", 40L, "/lv5",
            null, null, null, null, null, null));

        verify(menuDomainService).insert(any(SysMenu.class));
    }

    @Test
    @DisplayName("创建：正数 parentId 不存在（他租户/已删/不存在）抛 MENU_NOT_FOUND(10201)")
    void createWithNonexistentParentRejected() {
        when(menuDomainService.selectValidById(TENANT, 999L)).thenReturn(null);

        assertThatThrownBy(() -> service.createMenu(new MenuCreateReq("MENU", "孤儿", 999L, "/orphan",
            null, null, null, null, null, null)))
            .isInstanceOf(BizException.class)
            .extracting(e -> ((BizException) e).getErrorCode())
            .isEqualTo(AdminErrorCode.MENU_NOT_FOUND.getCode());
        verify(menuDomainService, never()).insert(any());
    }

    @Test
    @DisplayName("换父：目标父为菜单自身抛 MENU_PARENT_INVALID(10207)")
    void moveUnderSelfRejected() {
        when(menuDomainService.selectValidById(TENANT, MENU_ID)).thenReturn(menu("MENU"));
        when(menuDomainService.getDescendantIdsIncludingSelf(TENANT, MENU_ID))
            .thenReturn(java.util.List.of(MENU_ID));

        assertThatThrownBy(() -> service.updateMenu(
            new MenuUpdateReq(MENU_ID, null, null, MENU_ID, null, null, null, null, null, null)))
            .isInstanceOf(BizException.class)
            .extracting(e -> ((BizException) e).getErrorCode())
            .isEqualTo(AdminErrorCode.MENU_PARENT_INVALID.getCode());
        verify(menuDomainService, never()).update(any());
    }

    @Test
    @DisplayName("换父：目标父为菜单后代抛 MENU_PARENT_INVALID(10207)")
    void moveUnderDescendantRejected() {
        when(menuDomainService.selectValidById(TENANT, MENU_ID)).thenReturn(menu("MENU"));
        when(menuDomainService.selectValidById(TENANT, 21L)).thenReturn(menu("MENU"));
        when(menuDomainService.getDescendantIdsIncludingSelf(TENANT, MENU_ID))
            .thenReturn(java.util.List.of(MENU_ID, 20L, 21L));

        assertThatThrownBy(() -> service.updateMenu(
            new MenuUpdateReq(MENU_ID, null, null, 21L, null, null, null, null, null, null)))
            .isInstanceOf(BizException.class)
            .extracting(e -> ((BizException) e).getErrorCode())
            .isEqualTo(AdminErrorCode.MENU_PARENT_INVALID.getCode());
        verify(menuDomainService, never()).update(any());
    }

    @Test
    @DisplayName("换父：目标父不存在抛 MENU_NOT_FOUND(10201)")
    void moveToNonexistentParentRejected() {
        when(menuDomainService.selectValidById(TENANT, MENU_ID)).thenReturn(menu("MENU"));
        when(menuDomainService.selectValidById(TENANT, 999L)).thenReturn(null);

        assertThatThrownBy(() -> service.updateMenu(
            new MenuUpdateReq(MENU_ID, null, null, 999L, null, null, null, null, null, null)))
            .isInstanceOf(BizException.class)
            .extracting(e -> ((BizException) e).getErrorCode())
            .isEqualTo(AdminErrorCode.MENU_NOT_FOUND.getCode());
        verify(menuDomainService, never()).update(any());
    }

    @Test
    @DisplayName("换父子树边界：带一层子菜单（高度2）移到第 4 层父下，子将达第 6 层 → 拒绝 10203")
    void moveSubtreeBreakingDepthLimitRejected() {
        when(menuDomainService.selectValidById(TENANT, MENU_ID)).thenReturn(menu("MENU"));
        when(menuDomainService.selectValidById(TENANT, 40L)).thenReturn(menu("MENU"));
        when(menuDomainService.getDescendantIdsIncludingSelf(TENANT, MENU_ID))
            .thenReturn(java.util.List.of(MENU_ID, 20L));
        when(menuDomainService.calculateDepth(TENANT, 40L)).thenReturn(4);
        when(menuDomainService.subtreeHeight(TENANT, MENU_ID)).thenReturn(2);

        assertThatThrownBy(() -> service.updateMenu(
            new MenuUpdateReq(MENU_ID, null, null, 40L, null, null, null, null, null, null)))
            .isInstanceOf(BizException.class)
            .extracting(e -> ((BizException) e).getErrorCode())
            .isEqualTo(AdminErrorCode.MENU_DEPTH_EXCEEDED.getCode());
        verify(menuDomainService, never()).update(any());
    }

    @Test
    @DisplayName("换父子树边界：带一层子菜单（高度2）移到第 3 层父下，子恰为第 5 层 → 通过")
    void moveSubtreeExactlyAtDepthLimitAllowed() {
        SysMenu existing = menu("MENU");
        existing.setParentId(0L);
        when(menuDomainService.selectValidById(TENANT, MENU_ID)).thenReturn(existing);
        when(menuDomainService.selectValidById(TENANT, 30L)).thenReturn(menu("MENU"));
        when(menuDomainService.getDescendantIdsIncludingSelf(TENANT, MENU_ID))
            .thenReturn(java.util.List.of(MENU_ID, 20L));
        when(menuDomainService.calculateDepth(TENANT, 30L)).thenReturn(3);
        when(menuDomainService.subtreeHeight(TENANT, MENU_ID)).thenReturn(2);
        when(menuDomainService.pathExists(eq(TENANT), eq("/x"), eq(MENU_ID))).thenReturn(false);
        when(menuDomainService.resourceExists(eq(TENANT), eq("USER"), eq("5"), eq(MENU_ID)))
            .thenReturn(false);
        when(localProjectionDomainService.upsertAdminMenu(
            eq(TENANT), eq(MENU_ID), eq("菜单X"), eq(30L), eq(1), eq(3))).thenReturn(400L);

        service.updateMenu(new MenuUpdateReq(MENU_ID, null, null, 30L, null, null, null, null, null, null));

        verify(menuDomainService).update(any(SysMenu.class));
    }

    @Test
    @DisplayName("换父子树边界：高度 5 子树移到顶级（parentId=0）→ 最深恰为第 5 层，允许（父深度按 0）")
    void moveFullHeightSubtreeToTopLevelAllowed() {
        SysMenu existing = menu("MENU");
        existing.setParentId(30L);
        when(menuDomainService.selectValidById(TENANT, MENU_ID)).thenReturn(existing);
        when(menuDomainService.subtreeHeight(TENANT, MENU_ID)).thenReturn(5);
        when(menuDomainService.pathExists(eq(TENANT), eq("/x"), eq(MENU_ID))).thenReturn(false);
        when(menuDomainService.resourceExists(eq(TENANT), eq("USER"), eq("5"), eq(MENU_ID)))
            .thenReturn(false);
        when(localProjectionDomainService.upsertAdminMenu(
            eq(TENANT), eq(MENU_ID), eq("菜单X"), eq(0L), eq(1), eq(3))).thenReturn(400L);

        service.updateMenu(new MenuUpdateReq(MENU_ID, null, null, 0L, null, null, null, null, null, null));

        verify(menuDomainService).update(any(SysMenu.class));
        // 顶级目标不调用 calculateDepth（0 会把不存在的父层多算一层）
        verify(menuDomainService, never()).calculateDepth(anyLong(), any());
    }

    @Test
    @DisplayName("换父子树边界：高度 5 子树移到第 1 层父下 → 最深将达第 6 层，拒绝 10203")
    void moveFullHeightSubtreeUnderTopNodeRejected() {
        SysMenu existing = menu("MENU");
        existing.setParentId(30L);
        when(menuDomainService.selectValidById(TENANT, MENU_ID)).thenReturn(existing);
        when(menuDomainService.selectValidById(TENANT, 11L)).thenReturn(menu("MENU"));
        when(menuDomainService.getDescendantIdsIncludingSelf(TENANT, MENU_ID))
            .thenReturn(java.util.List.of(MENU_ID));
        when(menuDomainService.calculateDepth(TENANT, 11L)).thenReturn(1);
        when(menuDomainService.subtreeHeight(TENANT, MENU_ID)).thenReturn(5);

        assertThatThrownBy(() -> service.updateMenu(
            new MenuUpdateReq(MENU_ID, null, null, 11L, null, null, null, null, null, null)))
            .isInstanceOf(BizException.class)
            .extracting(e -> ((BizException) e).getErrorCode())
            .isEqualTo(AdminErrorCode.MENU_DEPTH_EXCEEDED.getCode());
        verify(menuDomainService, never()).update(any());
    }

    @Test
    @DisplayName("删除：清理投影并记日志，entityId 用投影主键")
    void deleteCleansProjectionWithResourceId() {
        when(menuDomainService.selectValidById(TENANT, MENU_ID)).thenReturn(menu("HIDDEN"));
        when(menuDomainService.hasChildren(TENANT, MENU_ID)).thenReturn(false);
        when(localProjectionDomainService.findAdminMenuResourceId(TENANT, MENU_ID)).thenReturn(300L);

        service.deleteMenu(MENU_ID);

        verify(menuDomainService).softDeleteBatch(TENANT, java.util.List.of(MENU_ID));
        verify(localProjectionDomainService).deleteAdminMenu(TENANT, MENU_ID);
        verify(auditDomainService).recordChangeLog(any(), any());
    }
}
