package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.BatchRevokeReq;
import cn.ac.fage.accessmesh.permission.dto.req.RoleGrantReq;
import cn.ac.fage.accessmesh.permission.service.PermissionGrantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role-resource permission management API.
 * All APIs: POST + JSON Body.
 */
@RestController
@RequestMapping("/api/perm/role-resource-permission")
public class PermissionGrantController {

    private final PermissionGrantService permissionGrantService;

    public PermissionGrantController(PermissionGrantService permissionGrantService) {
        this.permissionGrantService = permissionGrantService;
    }

    @PostMapping("/save")
    public PermResult<Void> batchGrant(@RequestBody RoleGrantReq req) {
        permissionGrantService.batchGrant(TenantContextHolder.getTenantId(), req.abstractRoleId(), req);
        return PermResult.success();
    }

    @PostMapping("/revoke")
    public PermResult<Void> batchRevoke(@Valid @RequestBody BatchRevokeReq req) {
        permissionGrantService.batchRevoke(TenantContextHolder.getTenantId(), req.roleId(), req.permissionIds());
        return PermResult.success();
    }
}

