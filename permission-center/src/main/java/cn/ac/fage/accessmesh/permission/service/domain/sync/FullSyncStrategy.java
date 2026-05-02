package cn.ac.fage.accessmesh.permission.service.domain.sync;

import cn.ac.fage.accessmesh.permission.service.domain.ResourceSyncHandler;
import cn.ac.fage.accessmesh.permission.service.domain.MappingSyncHandler;
import org.springframework.stereotype.Component;

/**
 * Full synchronization strategy.
 * Performs complete synchronization of resources and mappings,
 * including cleanup of obsolete entries.
 */
@Component
public class FullSyncStrategy implements SyncModeStrategy {

    public static final String NAME = "FULL";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public SyncResult execute(SyncContext context, ResourceSyncHandler resourceHandler,
                               MappingSyncHandler mappingHandler) {
        SyncResult result = new SyncResult();

        // Sync resources
        SyncResourcesResult resourcesResult = resourceHandler.syncResources(context);
        result.setCreatedResources(resourcesResult.getCreatedCount());
        result.setUpdatedResources(resourcesResult.getUpdatedCount());

        // Sync mappings
        SyncMappingsResult mappingsResult = mappingHandler.syncMappings(context);
        result.setCreatedMappings(mappingsResult.getCreatedCount());
        result.setUpdatedMappings(mappingsResult.getUpdatedCount());

        // Cleanup obsolete mappings
        int deletedMappings = mappingHandler.cleanupObsoleteMappings(
            context.tenantId(),
            context.req().serviceCode(),
            mappingsResult.getIncomingKeys()
        );
        result.setDeletedMappings(deletedMappings);

        // Cleanup orphaned resources
        int deletedResources = resourceHandler.cleanupOrphanedResources(
            context.tenantId(),
            context.req().serviceCode(),
            context.apiType(),
            resourcesResult.getActiveResourceIds()
        );
        result.setDeletedResources(deletedResources);

        return result;
    }
}