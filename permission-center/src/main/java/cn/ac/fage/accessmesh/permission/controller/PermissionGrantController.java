package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.BatchRevokeReq;
import cn.ac.fage.accessmesh.permission.dto.req.RolePermissionAddChildReq;
import cn.ac.fage.accessmesh.permission.dto.req.RolePermissionChildrenReq;
import cn.ac.fage.accessmesh.permission.dto.req.RolePermissionListReq;
import cn.ac.fage.accessmesh.permission.dto.req.RolePermissionRemoveChildReq;
import cn.ac.fage.accessmesh.permission.dto.req.RoleGrantReq;
import cn.ac.fage.accessmesh.permission.dto.resp.RolePermissionItemResp;
import cn.ac.fage.accessmesh.permission.dto.resp.RolePermissionItemsResp;
import cn.ac.fage.accessmesh.permission.service.PermissionGrantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

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
    public PermResult<RolePermissionItemsResp> batchGrant(@Valid @RequestBody RoleGrantReq req) {
        List<RolePermissionItemResp> items = permissionGrantService.batchGrant(TenantContextHolder.getTenantId(), req);
        return PermResult.success(new RolePermissionItemsResp(items));
    }

    @PostMapping("/revoke")
    public PermResult<Void> batchRevoke(@Valid @RequestBody BatchRevokeReq req) {
        permissionGrantService.batchRevoke(TenantContextHolder.getTenantId(), req);
        return PermResult.success();
    }

    @PostMapping("/list")
    public PermResult<RolePermissionItemsResp> list(@Valid @RequestBody RolePermissionListReq req) {
        List<RolePermissionItemResp> items = permissionGrantService.listPermissions(TenantContextHolder.getTenantId(), req);
        return PermResult.success(new RolePermissionItemsResp(items));
    }

    @PostMapping("/children")
    public PermResult<RolePermissionItemsResp> children(@Valid @RequestBody RolePermissionChildrenReq req) {
        List<RolePermissionItemResp> items = permissionGrantService.listChildren(TenantContextHolder.getTenantId(), req);
        return PermResult.success(new RolePermissionItemsResp(items));
    }

    @PostMapping("/add-child")
    public PermResult<RolePermissionItemsResp> addChild(@Valid @RequestBody RolePermissionAddChildReq req) {
        List<RolePermissionItemResp> items = permissionGrantService.addChildren(TenantContextHolder.getTenantId(), req);
        return PermResult.success(new RolePermissionItemsResp(items));
    }

    @PostMapping("/remove-child")
    public PermResult<Void> removeChild(@Valid @RequestBody RolePermissionRemoveChildReq req) {
        permissionGrantService.removeChild(TenantContextHolder.getTenantId(), req);
        return PermResult.success();
    }
}

