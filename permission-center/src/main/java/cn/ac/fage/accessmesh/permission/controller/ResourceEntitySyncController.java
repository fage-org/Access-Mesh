package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceEntityFullSyncReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceEntitySyncReq;
import cn.ac.fage.accessmesh.permission.service.ResourceEntitySyncAppService;
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
@RequestMapping("/api/perm/resource-entity")
public class ResourceEntitySyncController {

    private final ResourceEntitySyncAppService resourceEntitySyncAppService;

    public ResourceEntitySyncController(ResourceEntitySyncAppService resourceEntitySyncAppService) {
        this.resourceEntitySyncAppService = resourceEntitySyncAppService;
    }

    @PostMapping("/sync")
    public PermResult<SyncResultResp> sync(@Valid @RequestBody ResourceEntitySyncReq req,
                                            HttpServletRequest httpRequest) {
        Long tenantId = TenantContextHolder.getTenantId();
        return PermResult.success(resourceEntitySyncAppService.sync(tenantId, req, httpRequest));
    }

    @PostMapping("/full-sync")
    public PermResult<SyncResultResp> fullSync(@Valid @RequestBody ResourceEntityFullSyncReq req,
                                               HttpServletRequest httpRequest) {
        Long tenantId = TenantContextHolder.getTenantId();
        return PermResult.success(resourceEntitySyncAppService.fullSync(tenantId, req, httpRequest));
    }
}
