package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.req.ApiMappingReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceCreateReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceUpdateReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ApiMappingResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ResourceResp;
import cn.ac.fage.accessmesh.permission.dto.resp.ResourceTreeResp;

import java.util.List;

/**
 * Resource management service — CRUD, tree structure, API mappings.
 */
public interface ResourceManageService {

    /**
     * Create a resource entity.
     */
    ResourceResp createResource(ResourceCreateReq req, Long operatorId);

    /**
     * Get resource by ID.
     */
    ResourceResp getResource(Long tenantId, Long resourceId);

    /**
     * Update a resource.
     */
    ResourceResp updateResource(ResourceUpdateReq req, Long operatorId);

    /**
     * Delete (soft) a resource and cascade-delete children.
     */
    void deleteResource(Long tenantId, Long resourceId, Long operatorId);

    /**
     * Get resource tree for a tenant (optionally scoped to bizDomainId).
     */
    List<ResourceTreeResp> getResourceTree(Long tenantId, Integer resourceType);

    /**
     * List resources by tenant and type (flat list).
     */
    List<ResourceResp> listResources(Long tenantId, Integer resourceType, int offset, int limit);

    /**
     * Add an API mapping to a resource.
     */
    void addApiMapping(Long tenantId, Long resourceId, ApiMappingReq req);

    /**
     * Remove an API mapping.
     */
    void removeApiMapping(Long tenantId, Long resourceId, Long mappingId, Long operatorId);

    /**
     * List API mappings for a resource.
     */
    List<ApiMappingResp> listApiMappings(Long tenantId, Long resourceId);

    /**
     * Update an API mapping.
     */
    void updateApiMapping(Long tenantId, Long resourceId, Long mappingId, ApiMappingReq req);
}
