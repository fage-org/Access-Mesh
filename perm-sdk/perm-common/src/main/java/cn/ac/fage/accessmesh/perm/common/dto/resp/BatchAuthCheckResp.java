package cn.ac.fage.accessmesh.perm.common.dto.resp;

import java.util.List;

/**
 * Batch permission check response.
 */
public record BatchAuthCheckResp(
    List<AuthCheckItemResult> items
) {

    /**
     * Result for single item in batch check.
     */
    public record AuthCheckItemResult(
        String resourceTypeCode,
        String resourceCode,
        String operationCode,
        boolean allowed,
        String reason,
        List<Long> matchedRoleIds,
        List<Long> matchedPermissionIds
    ) {}
}