package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.common.model.PermCheckReq;
import cn.ac.fage.accessmesh.perm.common.model.PermCheckResp;
import cn.ac.fage.accessmesh.permission.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.permission.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.permission.dto.req.CheckInterfaceReq;
import cn.ac.fage.accessmesh.permission.dto.resp.AuthCheckResp;
import cn.ac.fage.accessmesh.permission.dto.resp.BatchAuthCheckResp;
import cn.ac.fage.accessmesh.permission.dto.resp.CheckInterfaceResp;
import cn.ac.fage.accessmesh.permission.service.PermissionService;
import org.springframework.web.bind.annotation.*;

/**
 * Internal permission check API — consumed by Gateway and SDK.
 * All APIs: POST + JSON Body.
 */
@RestController
@RequestMapping("/internal/perm")
public class InternalPermissionController {

    private final PermissionService permissionService;

    public InternalPermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /**
     * SDK-style permission check (simple allowed/denied).
     */
    @PostMapping("/check")
    public PermResult<PermCheckResp> check(@RequestBody PermCheckReq req) {
        return PermResult.success(permissionService.checkPermission(req));
    }

    /**
     * Detailed auth check with full chain and deny reason.
     */
    @PostMapping("/auth/check")
    public PermResult<AuthCheckResp> authCheck(@RequestBody AuthCheckReq req) {
        return PermResult.success(permissionService.check(req));
    }

    /**
     * Batch auth check for multiple resource+operation combinations.
     */
    @PostMapping("/auth/batch-check")
    public PermResult<BatchAuthCheckResp> batchCheck(@RequestBody BatchAuthCheckReq req) {
        return PermResult.success(permissionService.batchCheck(req));
    }

    /**
     * Gateway callback: check by serviceCode + httpMethod + path.
     * Resolves API mapping → resource entity → operation permission → full auth chain.
     */
    @PostMapping("/auth/check-interface")
    public PermResult<CheckInterfaceResp> checkInterface(@RequestBody CheckInterfaceReq req) {
        return PermResult.success(permissionService.checkInterface(req));
    }
}
