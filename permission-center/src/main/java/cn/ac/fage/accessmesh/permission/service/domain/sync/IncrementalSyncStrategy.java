package cn.ac.fage.accessmesh.permission.service.domain.sync;

import cn.ac.fage.accessmesh.permission.service.domain.ResourceSyncHandler;
import cn.ac.fage.accessmesh.permission.service.domain.MappingSyncHandler;
import org.springframework.stereotype.Component;

/**
 * Incremental synchronization strategy.
 * Only adds/updates new entries without removing existing ones.
 * Currently a placeholder for future implementation.
 */
@Component
public class IncrementalSyncStrategy implements SyncModeStrategy {

    public static final String NAME = "INCREMENTAL";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public SyncResult execute(SyncContext context, ResourceSyncHandler resourceHandler,
                               MappingSyncHandler mappingHandler) {
        SyncResult result = new SyncResult();

        // Sync resources (incremental - no cleanup)
        SyncResourcesResult resourcesResult = resourceHandler.syncResources(context);
        result.setCreatedResources(resourcesResult.getCreatedCount());
        result.setUpdatedResources(resourcesResult.getUpdatedCount());

        // Sync mappings (incremental - no cleanup)
        SyncMappingsResult mappingsResult = mappingHandler.syncMappings(context);
        result.setCreatedMappings(mappingsResult.getCreatedCount());
        result.setUpdatedMappings(mappingsResult.getUpdatedCount());

        // No cleanup in incremental mode
        result.setDeletedMappings(0);
        result.setDeletedResources(0);

        return result;
    }
}