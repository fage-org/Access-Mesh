package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ResourceEntityDomainService {

    List<ResourceEntity> listByParentId(Long tenantId, Long parentId);

    List<ResourceEntity> listByType(Long tenantId, Integer resourceType);

    void deleteWithChildren(Long tenantId, Long resourceId);

    /**
     * Get all ancestor resource IDs (parent, grandparent, etc.) for a resource.
     * Walks up the parent chain until reaching root or deleted entity.
     */
    List<Long> getAncestorIds(Long tenantId, Long resourceEntityId);

    /**
     * Batch get ancestor IDs for multiple resources.
     * Returns a map of resourceEntityId -> list of ancestor IDs.
     *
     * @param tenantId     tenant ID
     * @param resourceIds  set of resource entity IDs
     * @return map of resourceId -> list of ancestor IDs (empty lists for resources with no ancestors)
     */
    Map<Long, List<Long>> batchGetAncestorIds(Long tenantId, Set<Long> resourceIds);

    /**
     * Get all descendant resource IDs (children, grandchildren, etc.) for a resource.
     * Uses PostgreSQL CTE recursive query for efficient single-query retrieval.
     *
     * @param tenantId        tenant ID
     * @param resourceEntityId the resource entity ID to find descendants for
     * @return list of descendant IDs (excluding the resource itself)
     */
    List<Long> getDescendantIds(Long tenantId, Long resourceEntityId);

    /**
     * Get all descendant resource IDs including self.
     * Uses PostgreSQL CTE recursive query for efficient single-query retrieval.
     *
     * @param tenantId        tenant ID
     * @param resourceEntityId the resource entity ID to find descendants for
     * @return list of descendant IDs including the resource itself
     */
    List<Long> getDescendantIdsIncludingSelf(Long tenantId, Long resourceEntityId);

    /**
     * Batch get descendant IDs for multiple resources.
     * Uses PostgreSQL CTE recursive query for efficient single-query retrieval.
     *
     * @param tenantId       tenant ID
     * @param resourceEntityIds set of resource entity IDs
     * @return map of resourceEntityId -> list of descendant IDs (excluding self)
     */
    Map<Long, List<Long>> batchGetDescendantIds(Long tenantId, Set<Long> resourceEntityIds);

    /**
     * Select a valid resource entity by ID with tenant and delete flag conditions.
     * Returns null if resource not found, deleted, or doesn't belong to the tenant.
     */
    ResourceEntity selectValidById(Long tenantId, Long resourceId);
}
