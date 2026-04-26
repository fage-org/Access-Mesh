package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.dto.req.RolePermissionViewReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourcePermissionViewReq;
import cn.ac.fage.accessmesh.permission.dto.req.UserPermissionViewReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ResourcePermissionViewResp;
import cn.ac.fage.accessmesh.permission.dto.resp.RolePermissionViewResp;
import cn.ac.fage.accessmesh.permission.dto.resp.UserPermissionViewResp;
import cn.ac.fage.accessmesh.permission.service.PermissionViewService;
import jakarta.validation.Valid;
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
    public PermResult<UserPermissionViewResp> getUserPermissions(@Valid @RequestBody UserPermissionViewReq req) {
        return PermResult.success(permissionViewService.getUserPermissions(req.tenantId(), req.userId()));
    }

    /**
     * View which roles have permissions on a resource.
     */
    @PostMapping("/resource")
    public PermResult<ResourcePermissionViewResp> getResourcePermissions(@Valid @RequestBody ResourcePermissionViewReq req) {
        return PermResult.success(permissionViewService.getResourcePermissions(req.tenantId(), req.resourceEntityId()));
    }

    /**
     * View a role's permissions (flat list).
     */
    @PostMapping("/role")
    public PermResult<RolePermissionViewResp> getRolePermissions(@Valid @RequestBody RolePermissionViewReq req) {
        return PermResult.success(permissionViewService.getRolePermissions(req.tenantId(), req.roleId(), req.expandSub() != null && req.expandSub()));
    }
}
