package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.dto.req.*;
import cn.ac.fage.accessmesh.permission.dto.resp.ApiMappingResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ResourceResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ResourceTreeResp;
import cn.ac.fage.accessmesh.permission.service.ResourceManageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Resource management API.
 * All APIs: POST + JSON Body.
 */
@RestController
@RequestMapping("/api/resource")
public class ResourceManageController {

    private final ResourceManageService resourceManageService;

    public ResourceManageController(ResourceManageService resourceManageService) {
        this.resourceManageService = resourceManageService;
    }

    /**
     * Create a resource.
     */
    @PostMapping("/create")
    public PermResult<ResourceResp> createResource(@Valid @RequestBody ResourceCreateReq req) {
        return PermResult.success(resourceManageService.createResource(req, null));
    }

    /**
     * Get resource by ID.
     */
    @PostMapping("/get")
    public PermResult<ResourceResp> getResource(@Valid @RequestBody IdWithTenantReq req) {
        return PermResult.success(resourceManageService.getResource(req.tenantId(), req.id()));
    }

    /**
     * Update resource.
     */
    @PostMapping("/update")
    public PermResult<ResourceResp> updateResource(@Valid @RequestBody ResourceUpdateReq req) {
        return PermResult.success(resourceManageService.updateResource(req, null));
    }

    /**
     * Delete resource (soft, cascade delete children).
     */
    @PostMapping("/delete")
    public PermResult<Void> deleteResource(@Valid @RequestBody IdWithTenantReq req) {
        resourceManageService.deleteResource(req.tenantId(), req.id(), null);
        return PermResult.success();
    }

    /**
     * Get resource tree.
     */
    @PostMapping("/tree")
    public PermResult<List<ResourceTreeResp>> getResourceTree(@Valid @RequestBody ResourceTreeReq req) {
        return PermResult.success(resourceManageService.getResourceTree(req.tenantId(), req.resourceType()));
    }

    /**
     * List resources (flat, paginated).
     */
    @PostMapping("/list")
    public PermResult<List<ResourceResp>> listResources(@Valid @RequestBody ResourceListReq req) {
        return PermResult.success(resourceManageService.listResources(
                req.tenantId(), req.resourceType(),
                req.offset() != null ? req.offset() : 0,
                req.limit() != null ? req.limit() : 20));
    }

    /**
     * Add API mapping to a resource.
     */
    @PostMapping("/api-mapping/add")
    public PermResult<Void> addApiMapping(@Valid @RequestBody ApiMappingAddReq req) {
        ApiMappingReq mappingReq = new ApiMappingReq(
                req.tenantId(), req.resourceId(), req.serviceCode(), req.httpMethod(),
                req.pathPattern(), req.matchOrder(), req.enabled(), req.extra());
        resourceManageService.addApiMapping(req.tenantId(), req.resourceId(), mappingReq);
        return PermResult.success();
    }

    /**
     * Remove API mapping.
     */
    @PostMapping("/api-mapping/remove")
    public PermResult<Void> removeApiMapping(@Valid @RequestBody ApiMappingRemoveReq req) {
        resourceManageService.removeApiMapping(req.tenantId(), req.resourceId(), req.mappingId(), null);
        return PermResult.success();
    }

    /**
     * List API mappings for a resource.
     */
    @PostMapping("/api-mapping/list")
    public PermResult<List<ApiMappingResp>> listApiMappings(@Valid @RequestBody ApiMappingListReq req) {
        return PermResult.success(resourceManageService.listApiMappings(req.tenantId(), req.resourceId()));
    }

    /**
     * Update API mapping.
     */
    @PostMapping("/api-mapping/update")
    public PermResult<Void> updateApiMapping(@Valid @RequestBody ApiMappingUpdateReq req) {
        ApiMappingReq mappingReq = new ApiMappingReq(
                null, null, null, req.method(), req.apiPath(), null, null, req.description());
        resourceManageService.updateApiMapping(req.tenantId(), req.resourceId(), req.mappingId(), mappingReq);
        return PermResult.success();
    }
}
