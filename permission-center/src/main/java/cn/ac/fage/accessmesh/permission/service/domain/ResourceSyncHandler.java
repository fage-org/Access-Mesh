package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.service.domain.sync.SyncContext;
import cn.ac.fage.accessmesh.permission.service.domain.sync.SyncResourcesResult;

import java.util.Set;

/**
 * Handler for resource synchronization operations.
 */
public interface ResourceSyncHandler {

    /**
     * Sync resources based on the context.
     *
     * @param context the sync context
     * @return result containing created/updated counts and active resource IDs
     */
    SyncResourcesResult syncResources(SyncContext context);

    /**
     * Cleanup orphaned resources that have no remaining mappings.
     *
     * @param tenantId the tenant ID
     * @param serviceCode the service code
     * @param apiType the API resource type value
     * @param activeResourceIds the set of resource IDs that should remain active
     * @return the number of deleted resources
     */
    int cleanupOrphanedResources(Long tenantId, String serviceCode, Integer apiType, Set<Long> activeResourceIds);
}