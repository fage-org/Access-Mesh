package cn.ac.fage.accessmesh.permission.dto.req;

import java.util.Set;

/**
 * Request for unified permission check (internal use).
 * Used by permission-center internal services to check if operator has permissions on a target.
 */
public record PermissionCheckReq(
    Long operatorId,
    String targetType,           // USER / ROLE / RESOURCE
    Long targetId,
    Set<String> operationCodes   // VIEW / CREATE / EDIT / DELETE / MANAGE etc.
) {}