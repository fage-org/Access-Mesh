package cn.ac.fage.accessmesh.permission.util;

import cn.ac.fage.accessmesh.permission.service.domain.OperationLogDomainService;

import java.util.List;

/**
 * 批量操作工具类
 * <p>
 * 提供批量删除操作的通用工具方法。
 * 封装批量软删除的执行逻辑和操作日志记录。
 * </p>
 */
public final class BatchOperationUtil {

    /**
     * 私有构造函数
     * <p>
     * 工具类不允许实例化。
     * </p>
     */
    private BatchOperationUtil() {
        // 工具类，不允许实例化
    }

    /**
     * 执行批量软删除并记录操作日志
     * <p>
     * 对指定的实体ID列表逐个执行软删除操作，并记录操作日志。
     * 跳过null值ID，统计实际删除数量。
     * </p>
     *
     * @param tenantId              租户ID
     * @param ids                   待删除的实体ID列表
     * @param operatorId            操作者ID
     * @param deleteFunc            删除单个实体的函数，返回是否删除成功
     * @param logModule             日志模块（如"perm"）
     * @param logAction             日志动作（如"type-definition-remove"）
     * @param entityName            实体名称，用于日志消息
     * @param operationLogDomainService 操作日志服务
     */
    public static void executeBatchDelete(Long tenantId, List<Long> ids, Long operatorId,
                                          TriFunction<Long, Long, Long, Boolean> deleteFunc,
                                          String logModule, String logAction, String entityName,
                                          OperationLogDomainService operationLogDomainService) {

        if (ids == null || ids.isEmpty()) {
            return;
        }
        int n = 0;
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            if (deleteFunc.apply(tenantId, id, operatorId)) {
                n++;
            }
        }
        if (n > 0) {
            operationLogDomainService.asyncRecord(
                logModule, logAction, "BATCH", tenantId,
                "批量软删除 " + entityName + "，数量=" + n + "，IDs=" + ids,
                operatorId, null, null, tenantId
            );
        }
    }
}