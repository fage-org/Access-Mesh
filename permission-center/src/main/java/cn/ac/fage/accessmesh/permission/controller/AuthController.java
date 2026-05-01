package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.permission.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.permission.dto.req.CheckInterfaceReq;
import cn.ac.fage.accessmesh.permission.dto.req.InterfaceSnapshotReq;
import cn.ac.fage.accessmesh.permission.dto.req.QueryResourcesReq;
import cn.ac.fage.accessmesh.permission.dto.req.QueryScopesReq;
import cn.ac.fage.accessmesh.permission.dto.resp.AuthCheckResp;
import cn.ac.fage.accessmesh.permission.dto.resp.BatchAuthCheckResp;
import cn.ac.fage.accessmesh.permission.dto.resp.CheckInterfaceResp;
import cn.ac.fage.accessmesh.permission.dto.resp.InterfaceSnapshotResp;
import cn.ac.fage.accessmesh.permission.dto.resp.QueryResourcesResp;
import cn.ac.fage.accessmesh.permission.dto.resp.QueryScopesResp;
import cn.ac.fage.accessmesh.permission.service.PermissionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth check API — consumed by Gateway and SDK.
 * tenantId is read from X-Tenant-Id header via TenantContextHolder.
 * All APIs: POST + JSON Body.
 */
@RestController
@RequestMapping("/api/perm/auth")
public class AuthController {

    private final PermissionService permissionService;

    public AuthController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @PostMapping("/check")
    public PermResult<AuthCheckResp> check(@Valid @RequestBody AuthCheckReq req) {
        return PermResult.success(permissionService.check(TenantContextHolder.getTenantId(), req));
    }

    @PostMapping("/batch-check")
    public PermResult<BatchAuthCheckResp> batchCheck(@Valid @RequestBody BatchAuthCheckReq req) {
        return PermResult.success(permissionService.batchCheck(TenantContextHolder.getTenantId(), req));
    }

    @PostMapping("/check-interface")
    public PermResult<CheckInterfaceResp> checkInterface(@Valid @RequestBody CheckInterfaceReq req) {
        return PermResult.success(permissionService.checkInterface(TenantContextHolder.getTenantId(), req));
    }

    @PostMapping("/query-resources")
    public PermResult<QueryResourcesResp> queryResources(@Valid @RequestBody QueryResourcesReq req) {
        return PermResult.success(permissionService.queryResources(TenantContextHolder.getTenantId(), req));
    }

    @PostMapping("/query-scopes")
    public PermResult<QueryScopesResp> queryScopes(@Valid @RequestBody QueryScopesReq req) {
        return PermResult.success(permissionService.queryScopes(TenantContextHolder.getTenantId(), req));
    }

    @PostMapping("/interface-snapshot")
    public PermResult<InterfaceSnapshotResp> interfaceSnapshot(@Valid @RequestBody InterfaceSnapshotReq req) {
        return PermResult.success(permissionService.interfaceSnapshot(TenantContextHolder.getTenantId(), req));
    }
}
