package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.Map;

/**
 * Response for unified permission check (internal use).
 * Returns boolean result for each operation code.
 */
public record PermissionCheckResp(
    Map<String, Boolean> results  // key=operationCode, value=hasPermission
) {}