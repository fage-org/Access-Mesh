package cn.ac.fage.accessmesh.admin.controller;

import cn.ac.fage.accessmesh.admin.annotation.AuditLog;
import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.admin.dto.auth.UserInfoResp;
import cn.ac.fage.accessmesh.admin.service.RoleProxyService;
import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.common.enums.DefaultOpCode;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(RoleController.class);

    private final RoleProxyService roleProxyService;

    public RoleController(RoleProxyService roleProxyService) {
        this.roleProxyService = roleProxyService;
    }

    /**
     * Create a role associated with an org.
     */
    @PostMapping("/create")
    @AuditLog(module = "角色管理", action = "创建", targetType = "ROLE")
    public PermResult<Long> createRole(@Valid @RequestBody CreateRoleReq req) {
        Long tenantId = TenantContextHolder.getTenantId();
        return PermResult.success(roleProxyService.createRoleForOrg(req.roleName(), req.orgId(), tenantId));
    }

    /**
     * Grant a menu permission to a role.
     */
    @PostMapping("/grant-menu")
    @AuditLog(module = "角色管理", action = "授权菜单", targetType = "ROLE")
    public PermResult<Void> grantMenu(@Valid @RequestBody RoleMenuReq req) {
        Long tenantId = TenantContextHolder.getTenantId();
        roleProxyService.grantMenuToRole(tenantId, req.roleId(), req.menuId(), DefaultOpCode.VIEW.getCode());
        return PermResult.success();
    }

    /**
     * Revoke a menu permission from a role.
     */
    @PostMapping("/revoke-menu")
    @AuditLog(module = "角色管理", action = "撤销菜单", targetType = "ROLE")
    public PermResult<Void> revokeMenu(@Valid @RequestBody RoleMenuReq req) {
        Long tenantId = TenantContextHolder.getTenantId();
        roleProxyService.revokeMenuFromRole(tenantId, req.roleId(), req.menuId());
        return PermResult.success();
    }

    /**
     * Get current user's roles and permissions.
     */
    @PostMapping("/my-info")
    public PermResult<UserInfoResp> getMyInfo() {
        return PermResult.success(roleProxyService.loadUserRolesAndPermissions(StpUtil.getLoginIdAsLong()));
    }

    public record CreateRoleReq(String roleName, Long orgId) {}

    public record RoleMenuReq(Long roleId, Long menuId) {}
}