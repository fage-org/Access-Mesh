package cn.ac.fage.accessmesh.access.sync.controller;

import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntityFullSyncReq;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntitySyncReq;
import cn.ac.fage.accessmesh.access.resource.service.ResourceEntitySyncAppService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 资源实体同步控制器。
 */
@RestController
@RequestMapping("/api/access/resource-entity")
public class ResourceEntitySyncController {

    private final ResourceEntitySyncAppService resourceEntitySyncAppService;

    public ResourceEntitySyncController(ResourceEntitySyncAppService resourceEntitySyncAppService) {
        this.resourceEntitySyncAppService = resourceEntitySyncAppService;
    }

    @PostMapping("/sync")
    public R<SyncResultResp> sync(@Valid @RequestBody ResourceEntitySyncReq req,
                                            HttpServletRequest httpRequest) {
        Long tenantId = TenantContextHolder.getTenantId();
        return R.ok(resourceEntitySyncAppService.sync(tenantId, req, httpRequest));
    }

    @PostMapping("/full-sync")
    public R<SyncResultResp> fullSync(@Valid @RequestBody ResourceEntityFullSyncReq req,
                                               HttpServletRequest httpRequest) {
        Long tenantId = TenantContextHolder.getTenantId();
        return R.ok(resourceEntitySyncAppService.fullSync(tenantId, req, httpRequest));
    }
}
