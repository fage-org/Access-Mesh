package cn.ac.fage.accessmesh.perm.common.dto.resp;

import java.util.List;

/**
 * Single permission check response.
 */
public record AuthCheckResp(
    boolean allowed,
    String reason,                       // Deny reason: NO_PERMISSION, USER_NOT_FOUND, etc.
    List<Long> matchedRoleIds,
    List<Long> matchedPermissionIds,
    boolean conditionEvaluated
) {

    public static AuthCheckResp allow(List<Long> matchedRoleIds, List<Long> matchedPermissionIds, boolean conditionEvaluated) {
        return new AuthCheckResp(true, null, matchedRoleIds, matchedPermissionIds, conditionEvaluated);
    }

    public static AuthCheckResp deny(String reason) {
        return new AuthCheckResp(false, reason, List.of(), List.of(), false);
    }
}