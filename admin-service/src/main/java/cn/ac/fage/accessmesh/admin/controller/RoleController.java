package cn.ac.fage.accessmesh.admin.controller;

import cn.ac.fage.accessmesh.admin.annotation.AuditLog;
import cn.ac.fage.accessmesh.admin.dto.auth.UserInfoResp;
import cn.ac.fage.accessmesh.admin.entity.SysMenu;
import cn.ac.fage.accessmesh.admin.mapper.SysMenuMapper;
import cn.ac.fage.accessmesh.admin.service.RoleProxyService;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.common.enums.DefaultOpCode;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role management API — proxy to permission-center for role-resource operations.
 * All APIs: POST + JSON Body.
 */
@RestController
@RequestMapping("/role")
public class RoleController {

    private final RoleProxyService roleProxyService;
    private final SysMenuMapper menuMapper;

    public RoleController(RoleProxyService roleProxyService, SysMenuMapper menuMapper) {
        this.roleProxyService = roleProxyService;
        this.menuMapper = menuMapper;
    }

    /**
     * Create a role associated with an org.
     */
    @PostMapping("/create")
    @AuditLog(module = "角色管理", action = "创建", targetType = "ROLE")
    public PermResult<Long> createRole(@RequestBody CreateRoleReq req) {
        return PermResult.success(roleProxyService.createRoleForOrg(req.roleName(), req.orgId(), req.tenantId()));
    }

    /**
     * Grant a menu permission to a role.
     */
    @PostMapping("/grant-menu")
    @AuditLog(module = "角色管理", action = "授权菜单", targetType = "ROLE")
    public PermResult<Void> grantMenu(@Valid @RequestBody RoleMenuReq req) {
        SysMenu menu = menuMapper.selectOneById(req.menuId());
        if (menu == null || menu.getDeleteFlag() != 0L) {
            throw new BizException(AdminErrorCode.MENU_NOT_FOUND.getCode(), AdminErrorCode.MENU_NOT_FOUND.getMessage());
        }
        Long resourceId = menu.getPermResourceId();
        if (resourceId == null) {
            throw new BizException(AdminErrorCode.MENU_NOT_FOUND.getCode(), "菜单尚未同步到权限中心");
        }
        roleProxyService.grantResourceToRole(req.tenantId(), req.roleId(), resourceId, DefaultOpCode.VIEW.getCode());
        return PermResult.success();
    }

    /**
     * Revoke a menu permission from a role.
     */
    @PostMapping("/revoke-menu")
    @AuditLog(module = "角色管理", action = "撤销菜单", targetType = "ROLE")
    public PermResult<Void> revokeMenu(@Valid @RequestBody RoleMenuReq req) {
        SysMenu menu = menuMapper.selectOneById(req.menuId());
        if (menu == null || menu.getDeleteFlag() != 0L) {
            throw new BizException(AdminErrorCode.MENU_NOT_FOUND.getCode(), AdminErrorCode.MENU_NOT_FOUND.getMessage());
        }
        Long resourceId = menu.getPermResourceId();
        if (resourceId == null) {
            throw new BizException(AdminErrorCode.MENU_NOT_FOUND.getCode(), "菜单尚未同步到权限中心");
        }
        roleProxyService.revokeResourceFromRole(req.tenantId(), req.roleId(), resourceId);
        return PermResult.success();
    }

    /**
     * Get current user's roles and permissions.
     */
    @PostMapping("/my-info")
    public PermResult<UserInfoResp> getMyInfo() {
        return PermResult.success(roleProxyService.loadUserRolesAndPermissions(StpUtil.getLoginIdAsLong()));
    }

    public record CreateRoleReq(String roleName, Long orgId, Long tenantId) {}

    public record RoleMenuReq(Long roleId, Long menuId, Long tenantId) {}
}
