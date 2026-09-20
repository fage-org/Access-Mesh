package cn.ac.fage.accessmesh.access.sync.controller;

import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.resource.service.PermissionManifestAppService;
import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 已认证服务独立发布依赖；不接受请求体提供的服务身份。 */
@RestController
@RequestMapping("/api/access/integration/permission-manifest")
public class PermissionManifestController {
    private final PermissionManifestAppService service;
    public PermissionManifestController(PermissionManifestAppService service) { this.service = service; }

    @PostMapping("/full-sync")
    public R<SyncResultResp> fullSync(@Valid @RequestBody PermissionManifestReq request) {
        return R.ok(service.fullSync(TenantContextHolder.getTenantId(), request));
    }
}
