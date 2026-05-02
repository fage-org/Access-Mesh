package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigSyncReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ServiceConfigSyncResp;

/**
 * Domain service for service interface synchronization.
 * Coordinates the sync process using strategy pattern.
 */
public interface ServiceInterfaceSyncService {

    /**
     * Sync service interfaces.
     *
     * @param tenantId the tenant ID
     * @param req the sync request
     * @param operatorId the operator ID
     * @return the sync response with statistics
     */
    ServiceConfigSyncResp syncInterfaces(Long tenantId, ServiceConfigSyncReq req, Long operatorId);
}