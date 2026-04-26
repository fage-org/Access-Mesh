package cn.ac.fage.accessmesh.permission.controller;

import cn.ac.fage.accessmesh.common.model.PermResult;
import cn.ac.fage.accessmesh.permission.dto.req.ApiMappingReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceUpdateReq;
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
    public PermResult<ResourceResp> getResource(@RequestParam Long tenantId, @RequestParam Long resourceId) {
        return PermResult.success(resourceManageService.getResource(tenantId, resourceId));
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
    public PermResult<Void> deleteResource(@RequestParam Long tenantId, @RequestParam Long resourceId) {
        resourceManageService.deleteResource(tenantId, resourceId, null);
        return PermResult.success();
    }

    /**
     * Get resource tree.
     */
    @PostMapping("/tree")
    public PermResult<List<ResourceTreeResp>> getResourceTree(@RequestParam Long tenantId,
                                                               @RequestParam(required = false) Integer resourceType) {
        return PermResult.success(resourceManageService.getResourceTree(tenantId, resourceType));
    }

    /**
     * List resources (flat, paginated).
     */
    @PostMapping("/list")
    public PermResult<List<ResourceResp>> listResources(@RequestParam Long tenantId,
                                                         @RequestParam(required = false) Integer resourceType,
                                                         @RequestParam(defaultValue = "0") int offset,
                                                         @RequestParam(defaultValue = "20") int limit) {
        return PermResult.success(resourceManageService.listResources(tenantId, resourceType, offset, limit));
    }

    /**
     * Add API mapping to a resource.
     */
    @PostMapping("/api-mapping/add")
    public PermResult<Void> addApiMapping(@RequestParam Long tenantId,
                                           @RequestParam Long resourceId,
                                           @Valid @RequestBody ApiMappingReq req) {
        resourceManageService.addApiMapping(tenantId, resourceId, req);
        return PermResult.success();
    }

    /**
     * Remove API mapping.
     */
    @PostMapping("/api-mapping/remove")
    public PermResult<Void> removeApiMapping(@RequestParam Long tenantId,
                                              @RequestParam Long resourceId,
                                              @RequestParam Long mappingId) {
        resourceManageService.removeApiMapping(tenantId, resourceId, mappingId, null);
        return PermResult.success();
    }

    /**
     * List API mappings for a resource.
     */
    @PostMapping("/api-mapping/list")
    public PermResult<List<ApiMappingResp>> listApiMappings(@RequestParam Long tenantId,
                                                             @RequestParam Long resourceId) {
        return PermResult.success(resourceManageService.listApiMappings(tenantId, resourceId));
    }

    /**
     * Update API mapping.
     */
    @PostMapping("/api-mapping/update")
    public PermResult<Void> updateApiMapping(@RequestParam Long tenantId,
                                              @RequestParam Long resourceId,
                                              @RequestParam Long mappingId,
                                              @Valid @RequestBody ApiMappingReq req) {
        resourceManageService.updateApiMapping(tenantId, resourceId, mappingId, req);
        return PermResult.success();
    }
}
