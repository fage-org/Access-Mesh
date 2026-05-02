package cn.ac.fage.accessmesh.permission.service.domain.sync;

import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigSyncReq;
import cn.ac.fage.accessmesh.permission.entity.ServiceConfig;

/**
 * Context object for service interface synchronization.
 * Contains all the data needed during the sync process.
 */
public record SyncContext(
    Long tenantId,
    ServiceConfig serviceConfig,
    ServiceConfigSyncReq req,
    Long operatorId,
    String basePath,
    Integer apiType
) {
    /**
     * Creates a SyncContext with the given parameters.
     */
    public static SyncContext of(Long tenantId, ServiceConfig config, ServiceConfigSyncReq req,
                                  Long operatorId, String basePath, Integer apiType) {
        return new SyncContext(tenantId, config, req, operatorId, basePath, apiType);
    }
}