package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.PermissionVersionQueryReq;
import cn.ac.fage.accessmesh.permission.dto.resp.PermissionVersionResp;
import cn.ac.fage.accessmesh.permission.service.PermissionVersionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Permission version API — used by Gateway to check if cached snapshots are stale.
 * tenantId is read from X-Tenant-Id header via TenantContextHolder.
 * All APIs: POST + JSON Body.
 */
@RestController
@RequestMapping("/api/perm/permission-version")
public class PermissionVersionController {

    private final PermissionVersionService permissionVersionService;

    public PermissionVersionController(PermissionVersionService permissionVersionService) {
        this.permissionVersionService = permissionVersionService;
    }

    @PostMapping("/query")
    public PermResult<PermissionVersionResp> queryVersion(@Valid @RequestBody PermissionVersionQueryReq req) {
        return PermResult.success(permissionVersionService.queryVersion(TenantContextHolder.getTenantId(), req));
    }
}
