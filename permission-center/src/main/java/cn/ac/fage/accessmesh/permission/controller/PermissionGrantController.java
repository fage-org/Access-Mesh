package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.dto.req.BatchRevokeReq;
import cn.ac.fage.accessmesh.permission.dto.req.RoleGrantReq;
import cn.ac.fage.accessmesh.permission.service.PermissionGrantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * Permission grant/revoke management API.
 * All APIs: POST + JSON Body.
 */
@RestController
@RequestMapping("/api/role-permission")
public class PermissionGrantController {

    private final PermissionGrantService permissionGrantService;

    public PermissionGrantController(PermissionGrantService permissionGrantService) {
        this.permissionGrantService = permissionGrantService;
    }

    /**
     * Batch grant permissions to a role.
     */
    @PostMapping("/batch-grant")
    public PermResult<Void> batchGrant(@RequestBody RoleGrantReq req) {
        permissionGrantService.batchGrant(req.tenantId(), req.abstractRoleId(), req);
        return PermResult.success();
    }

    /**
     * Batch revoke permissions from a role.
     */
    @PostMapping("/batch-revoke")
    public PermResult<Void> batchRevoke(@Valid @RequestBody BatchRevokeReq req) {
        permissionGrantService.batchRevoke(req.tenantId(), req.roleId(), req.permissionIds());
        return PermResult.success();
    }
}
