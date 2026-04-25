package cn.ac.fage.accessmesh.perm.common.model;

/**
 * Remote permission check request from a downstream service to permission-center.
 */
public record PermCheckReq(
    Long userId,
    String permissionKey,
    String resourceType,
    String resourceId
) {
}
