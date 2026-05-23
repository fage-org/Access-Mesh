package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.resp.ChangeLogResp;
import cn.ac.fage.accessmesh.permission.dto.resp.OperationLogResp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 日志查询服务接口
 * <p>
 * 提供变更日志和操作日志的查询功能。
 * 只读服务，用于日志检索和审计。
 * </p>
 */
public interface LogQueryService {

    // ===== ChangeLog =====

    /**
     * 查询变更日志列表
     *
     * @param tenantId   租户ID
     * @param entityType 实体类型，可选
     * @param entityId   实体ID，可选
     * @param offset     分页偏移量
     * @param limit      每页条数
     * @return 变更日志列表
     */
    List<ChangeLogResp> listChangeLogs(Long tenantId, String entityType, Long entityId, int offset, int limit);

    /**
     * 统计变更日志数量
     *
     * @param tenantId   租户ID
     * @param entityType 实体类型，可选
     * @param entityId   实体ID，可选
     * @return 变更日志数量
     */
    long countChangeLogs(Long tenantId, String entityType, Long entityId);

    /**
     * 筛选查询变更日志列表
     *
     * @param tenantId   租户ID
     * @param userId     受影响的用户ID，可选
     * @param roleId     涉及的角色ID，可选
     * @param since      开始时间，可选
     * @param until      结束时间，可选
     * @param eventTypes 事件类型列表，可选
     * @param offset     分页偏移量
     * @param limit      每页条数
     * @return 变更日志列表
     */
    List<ChangeLogResp> listChangeLogsFiltered(Long tenantId, Long userId, Long roleId,
                                                LocalDateTime since, LocalDateTime until,
                                                List<String> eventTypes, int offset, int limit);

    /**
     * 筛选统计变更日志数量
     *
     * @param tenantId   租户ID
     * @param userId     受影响的用户ID，可选
     * @param roleId     涉及的角色ID，可选
     * @param since      开始时间，可选
     * @param until      结束时间，可选
     * @param eventTypes 事件类型列表，可选
     * @return 变更日志数量
     */
    long countChangeLogsFiltered(Long tenantId, Long userId, Long roleId,
                                  LocalDateTime since, LocalDateTime until,
                                  List<String> eventTypes);

    // ===== OperationLog =====

    /**
     * 查询操作日志列表
     *
     * @param tenantId 租户ID
     * @param module   模块，可选
     * @param action   操作，可选
     * @param offset   分页偏移量
     * @param limit    每页条数
     * @return 操作日志列表
     */
    List<OperationLogResp> listOperationLogs(Long tenantId, String module, String action, int offset, int limit);

    /**
     * 统计操作日志数量
     *
     * @param tenantId 租户ID
     * @param module   模块，可选
     * @param action   操作，可选
     * @return 操作日志数量
     */
    long countOperationLogs(Long tenantId, String module, String action);
}