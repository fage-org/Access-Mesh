package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

/**
 * Single auth check response.
 */
public record AuthCheckResp(
    boolean allowed,
    String reason,
    List<Long> matchedRoleIds,
    List<Long> matchedPermissionIds,
    boolean conditionEvaluated
) {
    public static AuthCheckResp allow(List<Long> matchedRoleIds, List<Long> matchedPermissionIds, boolean conditionEvaluated) {
        return new AuthCheckResp(true, null, matchedRoleIds, matchedPermissionIds, conditionEvaluated);
    }

    public static AuthCheckResp allow() {
        return new AuthCheckResp(true, null, List.of(), List.of(), false);
    }

    public static AuthCheckResp deny(String reason) {
        return new AuthCheckResp(false, reason, List.of(), List.of(), false);
    }
}
