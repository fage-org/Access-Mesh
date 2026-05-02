package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.service.domain.sync.SyncContext;
import cn.ac.fage.accessmesh.permission.service.domain.sync.SyncMappingsResult;

import java.util.Set;

/**
 * Handler for API mapping synchronization operations.
 */
public interface MappingSyncHandler {

    /**
     * Sync mappings based on the context.
     *
     * @param context the sync context
     * @return result containing created/updated counts and incoming keys
     */
    SyncMappingsResult syncMappings(SyncContext context);

    /**
     * Cleanup obsolete mappings that are no longer in the incoming set.
     *
     * @param tenantId the tenant ID
     * @param serviceCode the service code
     * @param incomingKeys the set of keys that should remain (httpMethod|path|resourceCode)
     * @return the number of deleted mappings
     */
    int cleanupObsoleteMappings(Long tenantId, String serviceCode, Set<String> incomingKeys);
}