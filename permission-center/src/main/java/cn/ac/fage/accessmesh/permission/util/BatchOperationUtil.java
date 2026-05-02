package cn.ac.fage.accessmesh.permission.util;

import cn.ac.fage.accessmesh.permission.service.domain.OperationLogDomainService;

import java.util.List;

/**
 * Utility class for common batch operations.
 */
public final class BatchOperationUtil {

    private BatchOperationUtil() {
        // Utility class, no instantiation
    }

    /**
     * Execute batch soft-delete with operation logging.
     *
     * @param tenantId              tenant ID
     * @param ids                   list of entity IDs to delete
     * @param operatorId            operator ID
     * @param deleteFunc            function to delete single entity, returns true if deleted
     * @param logModule             module for operation log (e.g., "perm")
     * @param logAction             action for operation log (e.g., "type-definition-remove")
     * @param entityName            entity name for log message
     * @param operationLogDomainService the operation log service
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
                "batch soft-delete " + entityName + ", count=" + n + ", ids=" + ids,
                operatorId, null, null, tenantId
            );
        }
    }
}
