package cn.ac.fage.accessmesh.permission.service.domain.sync;

import cn.ac.fage.accessmesh.permission.service.domain.ResourceSyncHandler;
import cn.ac.fage.accessmesh.permission.service.domain.MappingSyncHandler;

/**
 * Strategy interface for different sync modes.
 * Supports FULL and INCREMENTAL synchronization modes.
 */
public interface SyncModeStrategy {

    /**
     * Execute the synchronization strategy.
     *
     * @param context the sync context containing all required data
     * @param resourceHandler the handler for resource synchronization
     * @param mappingHandler the handler for mapping synchronization
     * @return the result of the synchronization
     */
    SyncResult execute(SyncContext context, ResourceSyncHandler resourceHandler, MappingSyncHandler mappingHandler);

    /**
     * Returns the name of this strategy.
     */
    String getName();
}