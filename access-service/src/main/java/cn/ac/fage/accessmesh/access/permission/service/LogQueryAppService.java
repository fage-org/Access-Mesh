package cn.ac.fage.accessmesh.access.permission.service;

import cn.ac.fage.accessmesh.access.permission.dto.req.PermissionRecentChangesReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ChangeLogResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.OperationLogResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.PermissionRecentChangesResp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 日志查询服务接口
 * <p>
 * 提供变更日志和操作日志的查询功能。
 * 只读服务，用于日志检索和审计。
 * </p>
 */
public interface LogQueryAppService {

    // ===== ChangeLog =====

    /**
     * 查询变更日志列表（变更日志页，T-PERM-032 筛选全集）
     *
     * @param tenantId       租户ID
     * @param entityType     实体类型，可选
     * @param entityId       实体ID，可选
     * @param eventType      diff_snapshot.eventType，可选（单选）
     * @param changeSource   变更来源（MANUAL/SERVICE_SYNC），可选
     * @param affectedUserId 受影响用户ID，可选
     * @param affectedRoleId 受影响角色ID，可选
     * @param since          创建时间下界（含），可选
     * @param until          创建时间上界（含），可选
     * @param offset         分页偏移量
     * @param limit          分页大小
     * @return 变更日志列表
     * @throws SecurityException 无 PERMISSION_CHANGE_LOG:VIEW 权限时抛出
     */
    List<ChangeLogResp> listChangeLogs(Long tenantId, String entityType, Long entityId,
                                       String eventType, String changeSource,
                                       Long affectedUserId, Long affectedRoleId,
                                       LocalDateTime since, LocalDateTime until,
                                       int offset, int limit);

    /**
     * 统计变更日志数量（条件与 {@link #listChangeLogs} 一致）
     *
     * @return 变更日志数量
     * @throws SecurityException 无 PERMISSION_CHANGE_LOG:VIEW 权限时抛出
     */
    long countChangeLogs(Long tenantId, String entityType, Long entityId,
                         String eventType, String changeSource,
                         Long affectedUserId, Long affectedRoleId,
                         LocalDateTime since, LocalDateTime until);

    // ===== OperationLog =====

    /**
     * 查询操作日志列表（多维过滤，T-PERM-025 扩展）
     *
     * @param tenantId   租户ID
     * @param module     模块，可选
     * @param action     操作，可选（精确匹配，字典见 listActionOptions）
     * @param operatorId 操作者用户ID，可选
     * @param since      创建时间下界（含），可选
     * @param until      创建时间上界（含），可选
     * @param targetType 目标类型，可选
     * @param offset     分页偏移量
     * @param limit      分页条数
     * @return 操作日志列表
     */
    List<OperationLogResp> listOperationLogs(Long tenantId, String module, String action,
                                              Long operatorId, LocalDateTime since, LocalDateTime until,
                                              String targetType, int offset, int limit);

    /**
     * 统计操作日志数量（多维过滤，T-PERM-025 扩展）
     *
     * @param tenantId   租户ID
     * @param module     模块，可选
     * @param action     操作，可选（精确匹配）
     * @param operatorId 操作者用户ID，可选
     * @param since      创建时间下界（含），可选
     * @param until      创建时间上界（含），可选
     * @param targetType 目标类型，可选
     * @return 操作日志数量
     */
    long countOperationLogs(Long tenantId, String module, String action,
                             Long operatorId, LocalDateTime since, LocalDateTime until, String targetType);

    /**
     * 查询操作日志 action 字典（当前实际存在的去重集合，T-PERM-025）
     *
     * @param tenantId 租户ID
     * @param module   模块，可选过滤
     * @return 去重 action 集合（字典序）
     */
    List<String> listActionOptions(Long tenantId, String module);

    // ===== 最近变更查询 =====

    /**
     * 获取权限最近变更列表（permission-view/recent-changes 端点）
     * <p>
     * 门禁（T-PERM-033 设计定案）：被查目标实例 USER:VIEW / ROLE:VIEW——
     * 查谁就要对谁有 VIEW（ROLE 未解析时类型级兜底；USER 未解析返回空）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      最近变更查询请求
     * @return 权限最近变更响应
     */
    PermissionRecentChangesResp getRecentChanges(Long tenantId, PermissionRecentChangesReq req);
}