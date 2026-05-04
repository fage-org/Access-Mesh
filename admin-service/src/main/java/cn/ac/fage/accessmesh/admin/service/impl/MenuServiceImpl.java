package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.MenuBatchCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.MenuCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.MenuUpdateReq;
import cn.ac.fage.accessmesh.admin.dto.resp.BatchResultResp;
import cn.ac.fage.accessmesh.admin.dto.resp.MenuResp;
import cn.ac.fage.accessmesh.admin.entity.SysMenu;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.mapper.SysMenuMapper;
import cn.ac.fage.accessmesh.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.admin.service.MenuService;
import cn.ac.fage.accessmesh.admin.service.domain.MenuDomainService;
import cn.ac.fage.accessmesh.admin.service.domain.MenuSyncHandler;
import cn.ac.fage.accessmesh.common.exception.BizException;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.admin.entity.table.SysMenuTableDef.SYS_MENU;

@Service
public class MenuServiceImpl implements MenuService {

    private static final Logger log = LoggerFactory.getLogger(MenuServiceImpl.class);

    private final SysMenuMapper menuMapper;
    private final cn.ac.fage.accessmesh.admin.service.RoleProxyService roleProxyService;
    private final MenuDomainService menuDomainService;
    private final MenuSyncHandler menuSyncHandler;
    private final AdminPermissionValidator permissionValidator;

    public MenuServiceImpl(SysMenuMapper menuMapper,
                           cn.ac.fage.accessmesh.admin.service.RoleProxyService roleProxyService,
                           MenuDomainService menuDomainService,
                           MenuSyncHandler menuSyncHandler,
                           AdminPermissionValidator permissionValidator) {
        this.menuMapper = menuMapper;
        this.roleProxyService = roleProxyService;
        this.menuDomainService = menuDomainService;
        this.menuSyncHandler = menuSyncHandler;
        this.permissionValidator = permissionValidator;
    }

    @Override
    @Transactional
    public Long createMenu(MenuCreateReq req) {
        // Permission check - type-level CREATE
        permissionValidator.checkTypeLevel(AdminResourceType.MENU, AdminOperationCode.CREATE);

        Long tenantId = TenantContextHolder.getTenantId();

        // 使用 DomainService 检查权限标识重复
        if (req.perms() != null && !req.perms().isBlank()) {
            SysMenu existing = menuDomainService.findByPermCode(tenantId, req.perms());
            if (existing != null) {
                throw new BizException(AdminErrorCode.MENU_PERM_CODE_EXISTS.getCode(),
                    AdminErrorCode.MENU_PERM_CODE_EXISTS.getMessage());
            }
        }

        // 使用 DomainService 计算深度
        int depth = menuDomainService.calculateDepth(tenantId, req.parentId());
        if (depth > 5) {
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
        menuMapper.insert(menu);

        // 使用 SyncHandler 同步到权限中心
        Long permResourceId = menuSyncHandler.syncMenuToPermissionCenter(tenantId, menu);
        if (permResourceId != null) {
            menu.setPermResourceId(permResourceId);
            menuMapper.update(menu);
            log.info("Menu synced to permission-center: menuId={}, permResourceId={}", menu.getId(), permResourceId);
        }

        return menu.getId();
    }

    @Override
    @Transactional
    public void updateMenu(MenuUpdateReq req) {
        // Permission check - instance-level UPDATE
        permissionValidator.checkInstanceLevel(
            AdminResourceType.MENU,
            String.valueOf(req.id()),
            AdminOperationCode.UPDATE
        );

        Long tenantId = TenantContextHolder.getTenantId();

        // 使用 DomainService 获取菜单
        SysMenu menu = menuDomainService.selectValidById(tenantId, req.id());
        if (menu == null) {
            throw new BizException(AdminErrorCode.MENU_NOT_FOUND.getCode(), AdminErrorCode.MENU_NOT_FOUND.getMessage());
        }

        // 使用 DomainService 检查权限标识重复
        if (req.perms() != null && !req.perms().isBlank() && !req.perms().equals(menu.getPermCode())) {
            SysMenu existing = menuDomainService.findByPermCode(tenantId, req.perms());
            if (existing != null) {
                throw new BizException(AdminErrorCode.MENU_PERM_CODE_EXISTS.getCode(),
                    AdminErrorCode.MENU_PERM_CODE_EXISTS.getMessage());
            }
        }

        // 使用 DomainService 检查新父菜单深度
        if (req.parentId() != null && !req.parentId().equals(menu.getParentId())) {
            int depth = menuDomainService.calculateDepth(tenantId, req.parentId());
            if (depth > 5) {
                throw new BizException(AdminErrorCode.MENU_DEPTH_EXCEEDED.getCode(),
                    AdminErrorCode.MENU_DEPTH_EXCEEDED.getMessage());
            }
        }

        menu.setMenuType(req.menuType() != null ? String.valueOf(req.menuType()) : menu.getMenuType());
        menu.setName(req.menuName());
        menu.setParentId(req.parentId() != null ? req.parentId() : menu.getParentId());
        menu.setPath(req.path());
        menu.setComponent(req.component());
        menu.setPermCode(req.perms());
        menu.setIcon(req.icon());
        menu.setSortOrder(req.sort());
        menu.setVisible(req.visible() != null && req.visible() == 1);
        menu.setStatus(req.status() != null ? req.status() : menu.getStatus());
        menu.setUpdatedAt(LocalDateTime.now());
        menuMapper.update(menu);

        // 使用 SyncHandler 同步更新到权限中心
        if (menu.getPermResourceId() != null || (req.perms() != null && !req.perms().isBlank())) {
            menuSyncHandler.syncMenuToPermissionCenter(tenantId, menu);
        }
    }

    @Override
    @Transactional
    public void deleteMenu(Long id) {
        // Permission check - instance-level DELETE
        permissionValidator.checkInstanceLevel(
            AdminResourceType.MENU,
            String.valueOf(id),
            AdminOperationCode.DELETE
        );

        Long tenantId = TenantContextHolder.getTenantId();

        // 使用 DomainService 获取菜单
        SysMenu menu = menuDomainService.selectValidById(tenantId, id);
        if (menu == null) {
            throw new BizException(AdminErrorCode.MENU_NOT_FOUND.getCode(), AdminErrorCode.MENU_NOT_FOUND.getMessage());
        }

        // 使用 DomainService 检查是否有子菜单
        if (menuDomainService.hasChildren(tenantId, id)) {
            throw new BizException(AdminErrorCode.MENU_HAS_CHILDREN.getCode(), AdminErrorCode.MENU_HAS_CHILDREN.getMessage());
        }

        // 使用 SyncHandler 从权限中心删除
        if (menu.getPermResourceId() != null) {
            menuSyncHandler.deleteMenuFromPermissionCenter(tenantId, menu.getPermResourceId());
        }

        // 使用 DomainService 批量软删除
        menuDomainService.softDeleteBatch(tenantId, List.of(id));
    }

    @Override
    public MenuResp getMenu(Long id) {
        Long tenantId = TenantContextHolder.getTenantId();

        // 使用 DomainService 获取菜单
        SysMenu menu = menuDomainService.selectValidById(tenantId, id);
        if (menu == null) {
            throw new BizException(AdminErrorCode.MENU_NOT_FOUND.getCode(), AdminErrorCode.MENU_NOT_FOUND.getMessage());
        }
        return toResp(menu, List.of());
    }

    @Override
    public List<MenuResp> treeMenu() {
        List<SysMenu> all = menuMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SYS_MENU.DELETE_FLAG.eq(0))
                .orderBy(SYS_MENU.SORT_ORDER.asc(), SYS_MENU.CREATED_AT.asc())
        );
        return buildTree(all, 0L);
    }

    @Override
    public List<String> getUserPermissions(Long userId) {
        cn.ac.fage.accessmesh.admin.dto.auth.UserInfoResp info = roleProxyService.loadUserRolesAndPermissions(userId);
        return info != null ? info.permissions() : List.of();
    }

    @Override
    @Transactional
    public BatchResultResp batchCreateMenus(MenuBatchCreateReq req) {
        // Permission check - type-level CREATE
        permissionValidator.checkTypeLevel(AdminResourceType.MENU, AdminOperationCode.CREATE);

        Long tenantId = TenantContextHolder.getTenantId();
        List<Long> successIds = new ArrayList<>();
        List<String> failedMessages = new ArrayList<>();

        for (MenuCreateReq menuReq : req.menus()) {
            try {
                // 检查权限标识重复
                if (menuReq.perms() != null && !menuReq.perms().isBlank()) {
                    SysMenu existing = menuDomainService.findByPermCode(tenantId, menuReq.perms());
                    if (existing != null) {
                        failedMessages.add("权限标识已存在: " + menuReq.perms());
                        continue;
                    }
                }

                // 计算深度
                int depth = menuDomainService.calculateDepth(tenantId, menuReq.parentId());
                if (depth > 5) {
                    failedMessages.add("菜单层级超过限制: " + menuReq.menuName());
                    continue;
                }

                SysMenu menu = new SysMenu();
                menu.setTenantId(tenantId);
                menu.setParentId(menuReq.parentId() != null ? menuReq.parentId() : 0L);
                menu.setMenuType(String.valueOf(menuReq.menuType()));
                menu.setName(menuReq.menuName());
                menu.setPath(menuReq.path());
                menu.setComponent(menuReq.component());
                menu.setPermCode(menuReq.perms());
                menu.setIcon(menuReq.icon());
                menu.setSortOrder(menuReq.sort());
                menu.setVisible(menuReq.visible() != null && menuReq.visible() == 1);
                menu.setStatus(menuReq.status() != null ? menuReq.status() : 1);
                menu.setCreatedAt(LocalDateTime.now());
                menu.setUpdatedAt(LocalDateTime.now());
                menu.setDeleteFlag(0L);
                menuMapper.insert(menu);

                // 同步到权限中心
                menuSyncHandler.syncMenuToPermissionCenter(tenantId, menu);

                successIds.add(menu.getId());
            } catch (Exception e) {
                log.error("Failed to create menu: menuName={}", menuReq.menuName(), e);
                failedMessages.add("创建失败: " + menuReq.menuName() + " - " + e.getMessage());
            }
        }

        return BatchResultResp.partial(req.menus().size(), successIds.size(), successIds, failedMessages);
    }

    @Override
    @Transactional
    public void batchDeleteMenus(IdsReq req) {
        // Permission check - batch instance-level DELETE
        List<String> resourceCodes = req.ids().stream()
            .map(String::valueOf)
            .collect(Collectors.toList());
        permissionValidator.checkBatchInstanceLevel(AdminResourceType.MENU, resourceCodes, AdminOperationCode.DELETE);

        Long tenantId = TenantContextHolder.getTenantId();

        // 获取所有菜单及其子孙ID
        Set<Long> allIdsToDelete = new java.util.HashSet<>();
        for (Long menuId : req.ids()) {
            SysMenu menu = menuDomainService.selectValidById(tenantId, menuId);
            if (menu == null) continue;

            // 获取子孙ID
            List<Long> descendantIds = menuDomainService.getDescendantIdsIncludingSelf(tenantId, menuId);
            allIdsToDelete.addAll(descendantIds);

            // 从权限中心删除
            if (menu.getPermResourceId() != null) {
                menuSyncHandler.deleteMenuFromPermissionCenter(tenantId, menu.getPermResourceId());
            }
        }

        // 批量软删除
        if (!allIdsToDelete.isEmpty()) {
            menuDomainService.softDeleteBatch(tenantId, List.copyOf(allIdsToDelete));
        }
    }

    @Override
    public List<Long> getDescendantMenuIds(Long menuId) {
        Long tenantId = TenantContextHolder.getTenantId();
        return menuDomainService.getDescendantIdsIncludingSelf(tenantId, menuId);
    }

    // ========== Helpers ==========

    private MenuResp toResp(SysMenu menu, List<MenuResp> children) {
        return new MenuResp(
            menu.getId(), Integer.parseInt(menu.getMenuType()), menu.getName(),
            menu.getParentId(), menu.getPath(), menu.getComponent(), menu.getPermCode(),
            menu.getIcon(), menu.getSortOrder(), menu.getVisible() ? 1 : 0,
            menu.getStatus(), menu.getCreatedAt(), menu.getUpdatedAt(), children
        );
    }

    private List<MenuResp> buildTree(List<SysMenu> all, Long parentId) {
        return all.stream()
            .filter(m -> parentId.equals(m.getParentId()))
            .map(m -> new MenuResp(
                m.getId(), Integer.parseInt(m.getMenuType()), m.getName(),
                m.getParentId(), m.getPath(), m.getComponent(), m.getPermCode(),
                m.getIcon(), m.getSortOrder(), m.getVisible() ? 1 : 0,
                m.getStatus(), m.getCreatedAt(), m.getUpdatedAt(),
                buildTree(all, m.getId())
            ))
            .collect(Collectors.toList());
    }
}