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
     * Recursively collects all nested children.
     */
    List<Long> getDescendantIds(Long tenantId, Long resourceEntityId);
}
