package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.resp.ChangeLogResp;

import java.time.LocalDateTime;
import java.util.List;

public interface PermissionChangeLogService {

    /**
     * List change logs with pagination
     */
    List<ChangeLogResp> listChangeLogs(Long tenantId, String entityType, String entityCode, int offset, int limit);

    /**
     * Count change logs
     */
    long countChangeLogs(Long tenantId, String entityType, String entityCode);

    /**
     * List change logs for a specific user
     */
    List<ChangeLogResp> listChangeLogsForUser(Long tenantId, Long userId, int offset, int limit);

    /**
     * Count change logs for a specific user
     */
    long countChangeLogsForUser(Long tenantId, Long userId);

    /**
     * List change logs with filters
     */
    List<ChangeLogResp> listChangeLogsFiltered(Long tenantId, Long userId, Long roleId,
                                               LocalDateTime since, LocalDateTime until,
                                               List<String> eventTypes, int offset, int limit);

    /**
     * Count change logs with filters
     */
    long countChangeLogsFiltered(Long tenantId, Long userId, Long roleId,
                                 LocalDateTime since, LocalDateTime until,
                                 List<String> eventTypes);
}
