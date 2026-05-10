package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.resp.ChangeLogResp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 权限变更日志服务接口
 * <p>
 * 提供权限变更日志的查询功能，用于审计和溯源。
 * 变更日志记录权限配置的变更历史，包括变更类型、前后快照、影响范围等。
 * </p>
 */
public interface PermissionChangeLogService {

    /**
     * 查询变更日志列表（分页）
     * <p>
     * 查询指定实体类型的变更日志，支持分页。
     * </p>
     *
     * @param tenantId   租户ID
     * @param entityType 实体类型，可选
     * @param entityCode 实体编码，可选
     * @param offset     分页偏移量
     * @param limit      每页条数
     * @return 变更日志列表
     */
    List<ChangeLogResp> listChangeLogs(Long tenantId, String entityType, String entityCode, int offset, int limit);

    /**
     * 统计变更日志数量
     * <p>
     * 统计满足条件的变更日志总数，用于分页计算。
     * </p>
     *
     * @param tenantId   租户ID
     * @param entityType 实体类型，可选
     * @param entityCode 实体编码，可选
     * @return 变更日志数量
     */
    long countChangeLogs(Long tenantId, String entityType, String entityCode);

    /**
     * 查询指定用户的变更日志列表
     * <p>
     * 查询影响指定用户的权限变更记录，支持分页。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   受影响的用户ID
     * @param offset   分页偏移量
     * @param limit    每页条数
     * @return 变更日志列表
     */
    List<ChangeLogResp> listChangeLogsForUser(Long tenantId, Long userId, int offset, int limit);

    /**
     * 统计指定用户的变更日志数量
     * <p>
     * 统计影响指定用户的权限变更记录总数，用于分页计算。
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   受影响的用户ID
     * @return 变更日志数量
     */
    long countChangeLogsForUser(Long tenantId, Long userId);

    /**
     * 筛选查询变更日志列表
     * <p>
     * 支持多条件筛选：用户、角色、时间范围、事件类型。
     * </p>
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
     * <p>
     * 统计满足筛选条件的变更记录总数，用于分页计算。
     * </p>
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
}