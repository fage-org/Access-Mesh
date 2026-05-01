package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.req.ApiMappingAddReq;
import cn.ac.fage.accessmesh.permission.dto.req.ApiMappingUpdateReq;
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
    ResourceResp createResource(Long tenantId, ResourceCreateReq req, Long operatorId);

    List<ResourceResp> batchCreateResources(Long tenantId, List<ResourceCreateReq> reqs, Long operatorId);

    /**
     * Get resource by ID.
     */
    ResourceResp getResource(Long tenantId, Long resourceId);

    /**
     * Update a resource.
     */
    ResourceResp updateResource(Long tenantId, ResourceUpdateReq req, Long operatorId);

    void moveResource(Long tenantId, Long resourceId, Long parentId, Long operatorId);

    /**
     * Delete (soft) a resource and cascade-delete children.
     */
    void deleteResource(Long tenantId, Long resourceId, Long operatorId);

    void deleteResources(Long tenantId, List<Long> resourceIds, Long operatorId);

    /**
     * Get resource tree for a tenant (optionally scoped to bizDomainId).
     */
    List<ResourceTreeResp> getResourceTree(Long tenantId, String resourceTypeCode, String domainCode);

    /**
     * List resources by tenant and type (flat list), optionally filtered by domain.
     */
    List<ResourceResp> listResources(Long tenantId, String resourceTypeCode, String domainCode, int offset, int limit);

    long countResources(Long tenantId, String resourceTypeCode, String domainCode);

    /**
     * Add an API mapping to a resource.
     */
    ApiMappingResp addApiMapping(Long tenantId, ApiMappingAddReq req);

    /**
     * Remove API mappings by mapping row ids (tenant-scoped soft delete).
     */
    void removeApiMappingsByIds(Long tenantId, List<Long> mappingIds, Long operatorId);

    /**
     * List API mappings, filtered by resourceId and/or serviceCode (AND semantics).
     */
    List<ApiMappingResp> listApiMappings(Long tenantId, Long resourceId, String serviceCode);

    /**
     * Update an API mapping.
     */
    ApiMappingResp updateApiMapping(Long tenantId, ApiMappingUpdateReq req);
}
