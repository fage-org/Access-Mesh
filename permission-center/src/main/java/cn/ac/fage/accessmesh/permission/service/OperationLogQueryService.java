package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.resp.OperationLogResp;

import java.util.List;

/**
 * Operation log query service.
 */
public interface OperationLogQueryService {

    /**
     * List operation logs with pagination.
     *
     * @param tenantId tenant ID
     * @param module   module filter (optional)
     * @param action   action filter (optional)
     * @param offset   pagination offset
     * @param limit    pagination limit
     * @return list of operation logs
     */
    List<OperationLogResp> listOperationLogs(Long tenantId, String module, String action, int offset, int limit);

    /**
     * Count operation logs.
     *
     * @param tenantId tenant ID
     * @param module   module filter (optional)
     * @param action   action filter (optional)
     * @return total count
     */
    long countOperationLogs(Long tenantId, String module, String action);
}