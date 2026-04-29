package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.DependencyBatchSyncReq;
import cn.ac.fage.accessmesh.permission.dto.req.DependencyListReq;
import cn.ac.fage.accessmesh.permission.dto.req.IdWithTenantReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceDependencyCreateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ResourceDependencyResp;
import cn.ac.fage.accessmesh.permission.service.AdvancedFeatureService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Resource dependency management API.
 * All APIs: POST + JSON Body.
 */
@RestController
@RequestMapping("/api/perm/resource-dependency")
public class ResourceDependencyController {

    private final AdvancedFeatureService advancedFeatureService;

    public ResourceDependencyController(AdvancedFeatureService advancedFeatureService) {
        this.advancedFeatureService = advancedFeatureService;
    }

    @PostMapping("/create")
    public PermResult<ResourceDependencyResp> createDependency(@Valid @RequestBody ResourceDependencyCreateReq req) {
        return PermResult.success(advancedFeatureService.createDependency(TenantContextHolder.getTenantId(), req, null));
    }

    @PostMapping("/list")
    public PermResult<List<ResourceDependencyResp>> listDependencies(@Valid @RequestBody DependencyListReq req) {
        return PermResult.success(advancedFeatureService.listDependencies(TenantContextHolder.getTenantId(), req.resourceEntityId()));
    }

    @PostMapping("/remove")
    public PermResult<Void> deleteDependency(@Valid @RequestBody IdWithTenantReq req) {
        advancedFeatureService.deleteDependency(TenantContextHolder.getTenantId(), req.id(), null);
        return PermResult.success();
    }

    @PostMapping("/batch-sync")
    public PermResult<Void> batchSyncDependencies(@Valid @RequestBody DependencyBatchSyncReq req) {
        advancedFeatureService.batchSyncDependencies(TenantContextHolder.getTenantId(), req.roleId(), null);
        return PermResult.success();
    }
}
