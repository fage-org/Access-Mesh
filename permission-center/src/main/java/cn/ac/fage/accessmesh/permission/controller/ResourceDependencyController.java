package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.DependencyBatchSyncReq;
import cn.ac.fage.accessmesh.permission.dto.req.DependencyListReq;
import cn.ac.fage.accessmesh.permission.dto.req.IdsReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceDependencyCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceDependencyCheckReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceDependencyUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.DependencyCycleCheckResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ItemsResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ResourceDependencyResp;
import cn.ac.fage.accessmesh.permission.service.DependencyManageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Resource dependency management API.
 */
@RestController
@RequestMapping("/api/perm/resource-dependency")
public class ResourceDependencyController {

    private final DependencyManageService dependencyManageService;

    public ResourceDependencyController(DependencyManageService dependencyManageService) {
        this.dependencyManageService = dependencyManageService;
    }

    @PostMapping("/create")
    public PermResult<ResourceDependencyResp> createDependency(@Valid @RequestBody ResourceDependencyCreateReq req) {
        return PermResult.success(dependencyManageService.createDependency(TenantContextHolder.getTenantId(), req, null));
    }

    @PostMapping("/list")
    public PermResult<ItemsResp<ResourceDependencyResp>> listDependencies(@Valid @RequestBody DependencyListReq req) {
        return PermResult.success(new ItemsResp<>(
            dependencyManageService.listDependencies(TenantContextHolder.getTenantId(), req.resourceEntityId())
        ));
    }

    @PostMapping("/remove")
    public PermResult<Void> deleteDependency(@Valid @RequestBody IdsReq req) {
        dependencyManageService.deleteDependencies(TenantContextHolder.getTenantId(), req.ids(), null);
        return PermResult.success();
    }

    @PostMapping("/batch-sync")
    public PermResult<Void> batchSyncDependencies(@Valid @RequestBody DependencyBatchSyncReq req) {
        dependencyManageService.batchSyncDependencies(TenantContextHolder.getTenantId(), req, null);
        return PermResult.success();
    }

    @PostMapping("/update")
    public PermResult<ResourceDependencyResp> updateDependency(@Valid @RequestBody ResourceDependencyUpdateReq req) {
        return PermResult.success(dependencyManageService.updateDependency(TenantContextHolder.getTenantId(), req, null));
    }

    @PostMapping("/graph")
    public PermResult<ItemsResp<ResourceDependencyResp>> graph(@Valid @RequestBody DependencyListReq req) {
        Long tenantId = TenantContextHolder.getTenantId();
        List<ResourceDependencyResp> items = req.resourceEntityId() != null
            ? dependencyManageService.listDependencies(tenantId, req.resourceEntityId())
            : dependencyManageService.listAllDependencies(tenantId);
        return PermResult.success(new ItemsResp<>(items));
    }

    @PostMapping("/check")
    public PermResult<DependencyCycleCheckResp> check(@Valid @RequestBody ResourceDependencyCheckReq req) {
        boolean hasCycle = dependencyManageService.hasDependencyCycle(TenantContextHolder.getTenantId(), req);
        return PermResult.success(new DependencyCycleCheckResp(
            hasCycle, req.sourceResourceTypeCode(), req.sourceResourceCode(),
            req.targetResourceTypeCode(), req.targetResourceCode()));
    }
}