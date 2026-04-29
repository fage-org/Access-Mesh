package cn.ac.fage.accessmesh.permission.dto.resp;

/**
 * Single auth check response.
 */
public record AuthCheckResp(
    boolean allowed,
    String reason,
    Long matchedRoleId,
    Long matchedPermissionId,
    boolean conditionEvaluated
) {
    public static AuthCheckResp allow(Long matchedRoleId, Long matchedPermissionId, boolean conditionEvaluated) {
        return new AuthCheckResp(true, null, matchedRoleId, matchedPermissionId, conditionEvaluated);
    }

    public static AuthCheckResp allow() {
        return new AuthCheckResp(true, null, null, null, false);
    }

    public static AuthCheckResp deny(String reason) {
        return new AuthCheckResp(false, reason, null, null, false);
    }
}
