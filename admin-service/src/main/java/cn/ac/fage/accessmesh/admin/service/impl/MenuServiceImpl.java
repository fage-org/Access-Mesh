package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.admin.dto.req.MenuCreateReq;
import cn.ac.fage.accessmesh.admin.dto.req.MenuUpdateReq;
import cn.ac.fage.accessmesh.admin.dto.resp.MenuResp;
import cn.ac.fage.accessmesh.admin.entity.SysMenu;
import cn.ac.fage.accessmesh.admin.entity.table.SysMenuTableDef;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.mapper.SysMenuMapper;
import cn.ac.fage.accessmesh.admin.service.MenuService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.client.feign.PermissionFeignClient;
import cn.ac.fage.accessmesh.perm.common.dto.req.IdWithTenantReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.ResourceCreateReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.ResourceUpdateReq;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static cn.ac.fage.accessmesh.admin.entity.table.SysMenuTableDef.SYS_MENU;

@Service
public class MenuServiceImpl implements MenuService {

    private static final Logger log = LoggerFactory.getLogger(MenuServiceImpl.class);
    private static final int RESOURCE_TYPE_MENU = 1;

    private final SysMenuMapper menuMapper;
    private final PermissionFeignClient permissionFeignClient;
    private final cn.ac.fage.accessmesh.admin.service.RoleProxyService roleProxyService;

    public MenuServiceImpl(SysMenuMapper menuMapper,
                           PermissionFeignClient permissionFeignClient,
                           cn.ac.fage.accessmesh.admin.service.RoleProxyService roleProxyService) {
        this.menuMapper = menuMapper;
        this.permissionFeignClient = permissionFeignClient;
        this.roleProxyService = roleProxyService;
    }

    @Override
    @Transactional
    public Long createMenu(MenuCreateReq req) {
        if (req.perms() != null && !req.perms().isBlank()) {
            SysMenu existing = menuMapper.selectOneByQuery(
                QueryWrapper.create()
                    .where(SYS_MENU.PERM_CODE.eq(req.perms()))
                    .and(SYS_MENU.DELETE_FLAG.eq(0))
            );
            if (existing != null) {
                throw new BizException(AdminErrorCode.MENU_PERM_CODE_EXISTS.getCode(),
                    AdminErrorCode.MENU_PERM_CODE_EXISTS.getMessage());
            }
        }

        int depth = calculateDepth(req.parentId());
        if (depth > 5) {
            throw new BizException(AdminErrorCode.MENU_DEPTH_EXCEEDED.getCode(),
                AdminErrorCode.MENU_DEPTH_EXCEEDED.getMessage());
        }

        Long tenantId = TenantContextHolder.getTenantId();

        // Sync to permission-center as a MENU resource
        Long permResourceId = null;
        if (req.perms() != null && !req.perms().isBlank()) {
            permResourceId = syncResourceToPermissionCenter(tenantId, null, req.parentId(), req.perms(), req.menuName(), null);
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
        menu.setPermResourceId(permResourceId);
        menu.setCreatedAt(LocalDateTime.now());
        menu.setUpdatedAt(LocalDateTime.now());
        menu.setDeleteFlag(0L);
        menuMapper.insert(menu);
        return menu.getId();
    }

    @Override
    @Transactional
    public void updateMenu(MenuUpdateReq req) {
        SysMenu menu = menuMapper.selectOneById(req.id());
        if (menu == null || menu.getDeleteFlag() != 0L) {
            throw new BizException(AdminErrorCode.MENU_NOT_FOUND.getCode(), AdminErrorCode.MENU_NOT_FOUND.getMessage());
        }
        if (req.perms() != null && !req.perms().isBlank() && !req.perms().equals(menu.getPermCode())) {
            SysMenu existing = menuMapper.selectOneByQuery(
                QueryWrapper.create()
                    .where(SYS_MENU.PERM_CODE.eq(req.perms()))
                    .and(SYS_MENU.DELETE_FLAG.eq(0))
                    .and(SYS_MENU.ID.ne(req.id()))
            );
            if (existing != null) {
                throw new BizException(AdminErrorCode.MENU_PERM_CODE_EXISTS.getCode(),
                    AdminErrorCode.MENU_PERM_CODE_EXISTS.getMessage());
            }
        }
        if (req.parentId() != null && !req.parentId().equals(menu.getParentId())) {
            int depth = calculateDepth(req.parentId());
            if (depth > 5) {
                throw new BizException(AdminErrorCode.MENU_DEPTH_EXCEEDED.getCode(),
                    AdminErrorCode.MENU_DEPTH_EXCEEDED.getMessage());
            }
        }

        // Sync to permission-center
        if (req.perms() != null && !req.perms().isBlank()
            && (!req.perms().equals(menu.getPermCode()) || !req.menuName().equals(menu.getName()))) {
            updateResourceInPermissionCenter(menu, req.menuName(), req.perms());
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
    }

    @Override
    @Transactional
    public void deleteMenu(Long id) {
        SysMenu menu = menuMapper.selectOneById(id);
        if (menu == null || menu.getDeleteFlag() != 0L) {
            throw new BizException(AdminErrorCode.MENU_NOT_FOUND.getCode(), AdminErrorCode.MENU_NOT_FOUND.getMessage());
        }
        long childCount = menuMapper.selectCountByQuery(
            QueryWrapper.create()
                .where(SYS_MENU.PARENT_ID.eq(id))
                .and(SYS_MENU.DELETE_FLAG.eq(0))
        );
        if (childCount > 0) {
            throw new BizException(AdminErrorCode.MENU_HAS_CHILDREN.getCode(), AdminErrorCode.MENU_HAS_CHILDREN.getMessage());
        }

        // Delete from permission-center
        if (menu.getPermResourceId() != null) {
            deleteResourceFromPermissionCenter(menu.getTenantId(), menu.getPermResourceId());
        }

        menu.setDeleteFlag(1L);
        menu.setDeletedAt(LocalDateTime.now());
        menuMapper.update(menu);
    }

    @Override
    public MenuResp getMenu(Long id) {
        SysMenu menu = menuMapper.selectOneById(id);
        if (menu == null || menu.getDeleteFlag() != 0L) {
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

    // ========== Permission Center Sync ==========

    private Long syncResourceToPermissionCenter(Long tenantId, Long resourceId, Long parentId, String code, String name, String path) {
        ResourceCreateReq req = new ResourceCreateReq(
            tenantId, null,
            parentId != null && parentId > 0 ? parentId : 0L,
            RESOURCE_TYPE_MENU, code, null, name, path, 1, 0, null
        );
        PermResult<Long> result = permissionFeignClient.createResource(req);
        if (result == null || result.data() == null) {
            log.warn("Failed to sync resource to permission-center: code={}, name={}", code, name);
            return null;
        }
        return result.data();
    }

    private void updateResourceInPermissionCenter(SysMenu menu, String name, String code) {
        if (menu.getPermResourceId() == null) {
            return;
        }
        ResourceUpdateReq req = new ResourceUpdateReq(
            menu.getPermResourceId(), menu.getTenantId(),
            code, name, menu.getPath(), menu.getStatus(), menu.getSortOrder(), menu.getExtra()
        );
        PermResult<Long> result = permissionFeignClient.updateResource(req);
        if (result == null) {
            log.warn("Failed to update resource in permission-center: resourceId={}", menu.getPermResourceId());
        }
    }

    private void deleteResourceFromPermissionCenter(Long tenantId, Long permResourceId) {
        IdWithTenantReq req = new IdWithTenantReq(tenantId, permResourceId);
        PermResult<Void> result = permissionFeignClient.deleteResource(req);
        if (result == null) {
            log.warn("Failed to delete resource from permission-center: resourceId={}", permResourceId);
        }
    }

    // ========== Helpers ==========

    private int calculateDepth(Long parentId) {
        if (parentId == null || parentId == 0L) return 1;
        int depth = 0;
        Long current = parentId;
        while (current != null && current != 0L) {
            SysMenu menu = menuMapper.selectOneById(current);
            if (menu == null) break;
            depth++;
            current = menu.getParentId();
        }
        return depth + 1;
    }

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
