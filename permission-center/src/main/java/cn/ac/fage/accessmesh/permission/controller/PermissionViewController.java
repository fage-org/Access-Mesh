package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.dto.resp.ResourcePermissionViewResp;
import cn.ac.fage.accessmesh.permission.dto.resp.RolePermissionViewResp;
import cn.ac.fage.accessmesh.permission.dto.resp.UserPermissionViewResp;
import cn.ac.fage.accessmesh.permission.service.PermissionViewService;
import org.springframework.web.bind.annotation.*;

/**
 * Permission View APIs — read-only visibility for user/resource/role permissions.
 * All APIs: POST + JSON Body.
 */
@RestController
@RequestMapping("/api/permission-view")
public class PermissionViewController {

    private final PermissionViewService permissionViewService;

    public PermissionViewController(PermissionViewService permissionViewService) {
        this.permissionViewService = permissionViewService;
    }

    /**
     * View a user's effective permissions (grouped by resource).
     */
    @PostMapping("/user")
    public PermResult<UserPermissionViewResp> getUserPermissions(
            @RequestParam Long tenantId, @RequestParam Long userId) {
        return PermResult.success(permissionViewService.getUserPermissions(tenantId, userId));
    }

    /**
     * View which roles have permissions on a resource.
     */
    @PostMapping("/resource")
    public PermResult<ResourcePermissionViewResp> getResourcePermissions(
            @RequestParam Long tenantId, @RequestParam Long resourceEntityId) {
        return PermResult.success(permissionViewService.getResourcePermissions(tenantId, resourceEntityId));
    }

    /**
     * View a role's permissions (flat list).
     */
    @PostMapping("/role")
    public PermResult<RolePermissionViewResp> getRolePermissions(
            @RequestParam Long tenantId,
            @RequestParam Long roleId,
            @RequestParam(defaultValue = "false") boolean expandSub) {
        return PermResult.success(permissionViewService.getRolePermissions(tenantId, roleId, expandSub));
    }
}
