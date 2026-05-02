package cn.ac.fage.accessmesh.permission.dto.req;

import java.util.Set;

/**
 * Request for batch permission check (internal use).
 * Checks permissions for multiple target IDs at once, returning results for each ID.
 */
public record PermissionCheckBatchReq(
    Long operatorId,
    String targetType,               // USER / ROLE / RESOURCE
    Set<Long> targetIds,             // Multiple target IDs to check
    Set<String> operationCodes       // VIEW / CREATE / EDIT / DELETE / MANAGE etc.
) {}