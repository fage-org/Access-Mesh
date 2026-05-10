package cn.ac.fage.accessmesh.permission.service;

import cn.ac.fage.accessmesh.permission.dto.resp.OperationLogResp;

import java.util.List;

/**
 * 操作日志查询服务接口
 * <p>
 * 提供操作日志的查询功能，用于审计和追踪。
 * 操作日志记录用户的操作行为，包括模块、操作、目标等。
 * </p>
 */
public interface OperationLogQueryService {

    /**
     * 查询操作日志列表（分页）
     * <p>
     * 查询指定租户的操作日志，支持按模块和操作过滤，支持分页。
     * </p>
     *
     * @param tenantId 租户ID
     * @param module   模块过滤，可选
     * @param action   操作过滤，可选
     * @param offset   分页偏移量
     * @param limit    每页条数
     * @return 操作日志列表
     */
    List<OperationLogResp> listOperationLogs(Long tenantId, String module, String action, int offset, int limit);

    /**
     * 统计操作日志数量
     * <p>
     * 统计满足条件的操作日志总数，用于分页计算。
     * </p>
     *
     * @param tenantId 租户ID
     * @param module   模块过滤，可选
     * @param action   操作过滤，可选
     * @return 操作日志数量
     */
    long countOperationLogs(Long tenantId, String module, String action);
}