package cn.ac.fage.accessmesh.perm.common.model;

/**
 * Remote permission check response from permission-center.
 */
public record PermCheckResp(
    boolean permitted,
    String reason
) {
    public static PermCheckResp allow() {
        return new PermCheckResp(true, null);
    }

    public static PermCheckResp deny(String reason) {
        return new PermCheckResp(false, reason);
    }
}
