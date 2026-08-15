package cn.ac.fage.accessmesh.access.application.impl;

import cn.ac.fage.accessmesh.access.admin.dto.req.MenuCreateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.MenuUpdateReq;
import cn.ac.fage.accessmesh.access.admin.entity.SysMenu;
import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.access.admin.service.domain.MenuDomainService;
import cn.ac.fage.accessmesh.access.application.MenuWriteAppService;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.aop.OperationLog;
import cn.ac.fage.accessmesh.access.permission.aop.PermissionChange;
import cn.ac.fage.accessmesh.access.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MenuWriteAppServiceImpl implements MenuWriteAppService {

    private static final String MENU_TYPE_BUTTON = "3";

    private final MenuDomainService menuDomainService;
    private final AdminPermissionValidator permissionValidator;
    private final LocalProjectionDomainService localProjectionDomainService;
    private final AuditDomainService auditDomainService;

    public MenuWriteAppServiceImpl(MenuDomainService menuDomainService,
                                   AdminPermissionValidator permissionValidator,
                                   LocalProjectionDomainService localProjectionDomainService,
                                   AuditDomainService auditDomainService) {
        this.menuDomainService = menuDomainService;
        this.permissionValidator = permissionValidator;
        this.localProjectionDomainService = localProjectionDomainService;
        this.auditDomainService = auditDomainService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "ADMIN", action = "MENU_CREATE", targetType = "sys_menu",
        targetId = "#result", summary = "'create menu ' + #req.menuName()")
    public Long createMenu(MenuCreateReq req) {
        permissionValidator.checkTypeLevel(AdminResourceType.MENU, AdminOperationCode.CREATE);
        Long tenantId = TenantContextHolder.getTenantId();
        if (req.perms() != null && !req.perms().isBlank()
            && menuDomainService.findByPermCode(tenantId, req.perms()) != null) {
            throw new BizException(AdminErrorCode.MENU_PERM_CODE_EXISTS.getCode(),
                AdminErrorCode.MENU_PERM_CODE_EXISTS.getMessage());
        }
        if (menuDomainService.calculateDepth(tenantId, req.parentId()) > 5) {
            throw new BizException(AdminErrorCode.MENU_DEPTH_EXCEEDED.getCode(),
                AdminErrorCode.MENU_DEPTH_EXCEEDED.getMessage());
        }
        SysMenu menu = new SysMenu();
        menu.setTenantId(tenantId);
        menu.setParentId(req.parentId() != null ? req.parentId() : 0L);
        menu.setMenuType(String.valueOf(req.menuType()));
        menu.setName(req.menuName());
        menu.setPath(req.path());
        menu.setComponent(req.component());
        menu.setPermCode(req.perms());
        menu.setIcon(req.icon());
        menu.setSortOrder(req.sort());
        menu.setVisible(req.visible() != null && req.visible() == 1);
        menu.setStatus(req.status() != null ? req.status() : 1);
        menu.setCreatedAt(LocalDateTime.now());
        menu.setUpdatedAt(LocalDateTime.now());
        menu.setDeleteFlag(0L);
        menuDomainService.insert(menu);
        projectMenuIfNeeded(tenantId, menu, "UPSERT");
        return menu.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "ADMIN", action = "MENU_UPDATE", targetType = "sys_menu",
        targetId = "#req.id()", summary = "'update menu ' + #req.id()")
    public void updateMenu(MenuUpdateReq req) {
        permissionValidator.checkInstanceLevel(
            AdminResourceType.MENU, String.valueOf(req.id()), AdminOperationCode.UPDATE);
        Long tenantId = TenantContextHolder.getTenantId();
        SysMenu menu = menuDomainService.selectValidById(tenantId, req.id());
        if (menu == null) {
            throw new BizException(AdminErrorCode.MENU_NOT_FOUND.getCode(),
                AdminErrorCode.MENU_NOT_FOUND.getMessage());
        }
        if (req.perms() != null && !req.perms().isBlank() && !req.perms().equals(menu.getPermCode())
            && menuDomainService.findByPermCode(tenantId, req.perms()) != null) {
            throw new BizException(AdminErrorCode.MENU_PERM_CODE_EXISTS.getCode(),
                AdminErrorCode.MENU_PERM_CODE_EXISTS.getMessage());
        }
        if (req.parentId() != null && !req.parentId().equals(menu.getParentId())
            && menuDomainService.calculateDepth(tenantId, req.parentId()) > 5) {
            throw new BizException(AdminErrorCode.MENU_DEPTH_EXCEEDED.getCode(),
                AdminErrorCode.MENU_DEPTH_EXCEEDED.getMessage());
        }
        String oldMenuType = menu.getMenuType();
        menu.setMenuType(req.menuType() != null ? String.valueOf(req.menuType()) : menu.getMenuType());
        // T-ACCESS-005 评审 P1：可选字段仅更新提供的字段（null 跳过，保留原值）
        if (req.menuName() != null) {
            menu.setName(req.menuName());
        }
        menu.setParentId(req.parentId() != null ? req.parentId() : menu.getParentId());
        if (req.path() != null) {
            menu.setPath(req.path());
        }
        if (req.component() != null) {
            menu.setComponent(req.component());
        }
        if (req.perms() != null) {
            menu.setPermCode(req.perms());
        }
        if (req.icon() != null) {
            menu.setIcon(req.icon());
        }
        if (req.sort() != null) {
            menu.setSortOrder(req.sort());
        }
        if (req.visible() != null) {
            menu.setVisible(req.visible() == 1);
        }
        if (req.status() != null) {
            menu.setStatus(req.status());
        }
        menu.setUpdatedAt(LocalDateTime.now());
        menuDomainService.update(menu);
        // T-ACCESS-005 评审 P1：非按钮 → 按钮时删除旧 ADMIN_MENU 投影，避免旧授权残留
        if (!MENU_TYPE_BUTTON.equals(oldMenuType) && MENU_TYPE_BUTTON.equals(menu.getMenuType())) {
            Long resourceId = localProjectionDomainService.findAdminMenuResourceId(tenantId, menu.getId());
            localProjectionDomainService.deleteAdminMenu(tenantId, menu.getId());
            auditDomainService.recordChangeLog(
                new AuditDomainService.ChangeLogContext(
                    tenantId, operatorId(), null, PermConstants.MaintainSource.MANUAL, "local-projection"),
                List.of(new AuditDomainService.ChangeLogEntry(
                    "resource_entity", resourceId, "DELETE", null, null, null, new Long[0], new Long[0])));
        } else {
            projectMenuIfNeeded(tenantId, menu, "UPSERT");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @PermissionChange
    @OperationLog(module = "ADMIN", action = "MENU_DELETE", targetType = "sys_menu",
        targetId = "#id", summary = "'delete menu ' + #id")
    public void deleteMenu(Long id) {
        permissionValidator.checkInstanceLevel(
            AdminResourceType.MENU, String.valueOf(id), AdminOperationCode.DELETE);
        Long tenantId = TenantContextHolder.getTenantId();
        SysMenu menu = menuDomainService.selectValidById(tenantId, id);
        if (menu == null) {
            throw new BizException(AdminErrorCode.MENU_NOT_FOUND.getCode(),
                AdminErrorCode.MENU_NOT_FOUND.getMessage());
        }
        if (menuDomainService.hasChildren(tenantId, id)) {
            throw new BizException(AdminErrorCode.MENU_HAS_CHILDREN.getCode(),
                AdminErrorCode.MENU_HAS_CHILDREN.getMessage());
        }
        menuDomainService.softDeleteBatch(tenantId, List.of(id));
        if (!MENU_TYPE_BUTTON.equals(menu.getMenuType())) {
            // T-ACCESS-005 评审 P2：entityId 用 ADMIN_MENU 投影主键（resource_entity.id），不再用 sys_menu.id
            Long resourceId = localProjectionDomainService.findAdminMenuResourceId(tenantId, id);
            localProjectionDomainService.deleteAdminMenu(tenantId, id);
            auditDomainService.recordChangeLog(
                new AuditDomainService.ChangeLogContext(
                    tenantId, operatorId(), null, PermConstants.MaintainSource.MANUAL, "local-projection"),
                List.of(new AuditDomainService.ChangeLogEntry(
                    "resource_entity", resourceId, "DELETE", null, null, null, new Long[0], new Long[0])));
        }
    }

    private void projectMenuIfNeeded(Long tenantId, SysMenu menu, String operation) {
        if (MENU_TYPE_BUTTON.equals(menu.getMenuType())) {
            return;
        }
        Long resourceId = localProjectionDomainService.upsertAdminMenu(
            tenantId, menu.getId(), menu.getName(), menu.getParentId(), menu.getStatus(), menu.getSortOrder());
        auditDomainService.recordChangeLog(
            new AuditDomainService.ChangeLogContext(
                tenantId, operatorId(), null, PermConstants.MaintainSource.MANUAL, "local-projection"),
            List.of(new AuditDomainService.ChangeLogEntry(
                "resource_entity", resourceId, operation, null, null, null, new Long[0], new Long[0])));
    }

    private static Long operatorId() {
        Long id = AccessRequestContext.getOperatorId();
        return id != null ? id : StpUtil.getLoginIdAsLong();
    }
}
