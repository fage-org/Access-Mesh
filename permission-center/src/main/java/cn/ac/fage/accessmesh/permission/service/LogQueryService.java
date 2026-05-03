package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.resp.ChangeLogResp;
import cn.ac.fage.accessmesh.permission.dto.resp.OperationLogResp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Log query service - combines change log and operation log queries.
 * Read-only service for log retrieval.
 */
public interface LogQueryService {

    // ===== ChangeLog =====

    List<ChangeLogResp> listChangeLogs(Long tenantId, String entityType, Long entityId, int offset, int limit);

    long countChangeLogs(Long tenantId, String entityType, Long entityId);

    List<ChangeLogResp> listChangeLogsForUser(Long tenantId, Long userId, int offset, int limit);

    long countChangeLogsForUser(Long tenantId, Long userId);

    List<ChangeLogResp> listChangeLogsFiltered(Long tenantId, Long userId, Long roleId,
                                                LocalDateTime since, LocalDateTime until,
                                                List<String> eventTypes, int offset, int limit);

    long countChangeLogsFiltered(Long tenantId, Long userId, Long roleId,
                                  LocalDateTime since, LocalDateTime until,
                                  List<String> eventTypes);

    // ===== OperationLog =====

    List<OperationLogResp> listOperationLogs(Long tenantId, String module, String action, int offset, int limit);

    long countOperationLogs(Long tenantId, String module, String action);
}