package cn.ac.fage.accessmesh.perm.common.model;

/**
 * 远程权限校验请求
 * <p>
 * 下游服务向权限中心发起的权限校验请求。
 * 包含用户ID、权限键、资源类型和资源ID等信息。
 * </p>
 */
public record PermCheckReq(
    Long userId,
    String permissionKey,
    String resourceType,
    String resourceId
) {
}
