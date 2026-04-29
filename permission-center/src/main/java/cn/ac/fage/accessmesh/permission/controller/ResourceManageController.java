package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.dto.req.*;
import cn.ac.fage.accessmesh.permission.dto.resp.ResourceResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ResourceTreeResp;
import cn.ac.fage.accessmesh.permission.service.ResourceManageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Resource entity management API.
 * All APIs: POST + JSON Body.
 */
@RestController
@RequestMapping("/api/perm/resource-entity")
public class ResourceManageController {

    private final ResourceManageService resourceManageService;

    public ResourceManageController(ResourceManageService resourceManageService) {
        this.resourceManageService = resourceManageService;
    }

    @PostMapping("/create")
    public PermResult<ResourceResp> createResource(@Valid @RequestBody ResourceCreateReq req) {
        return PermResult.success(resourceManageService.createResource(TenantContextHolder.getTenantId(), req, null));
    }

    @PostMapping("/detail")
    public PermResult<ResourceResp> getResource(@Valid @RequestBody IdWithTenantReq req) {
        return PermResult.success(resourceManageService.getResource(TenantContextHolder.getTenantId(), req.id()));
    }

    @PostMapping("/update")
    public PermResult<ResourceResp> updateResource(@Valid @RequestBody ResourceUpdateReq req) {
        return PermResult.success(resourceManageService.updateResource(TenantContextHolder.getTenantId(), req, null));
    }

    @PostMapping("/remove")
    public PermResult<Void> deleteResource(@Valid @RequestBody IdWithTenantReq req) {
        resourceManageService.deleteResource(TenantContextHolder.getTenantId(), req.id(), null);
        return PermResult.success();
    }

    @PostMapping("/tree")
    public PermResult<List<ResourceTreeResp>> getResourceTree(@Valid @RequestBody ResourceTreeReq req) {
        return PermResult.success(resourceManageService.getResourceTree(TenantContextHolder.getTenantId(), req.resourceType()));
    }

    @PostMapping("/list")
    public PermResult<List<ResourceResp>> listResources(@Valid @RequestBody ResourceListReq req) {
        return PermResult.success(resourceManageService.listResources(
                TenantContextHolder.getTenantId(), req.resourceType(),
                req.offset() != null ? req.offset() : 0,
                req.limit() != null ? req.limit() : 20));
    }
}
