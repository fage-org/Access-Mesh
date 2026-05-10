package cn.ac.fage.accessmesh.perm.common.model;

/**
 * 权限上下文
 * <p>
 * 通过SDK在各服务之间传递的权限上下文信息。
 * 包含用户身份、权限和角色信息，用于跨服务权限校验。
 * </p>
 */
public record PermContext(
    Long userId,
    String username,
    String token,
    java.util.Set<String> permissions,
    java.util.Set<String> roles
) {
    /**
     * 创建空的权限上下文
     * <p>
     * 用于未登录或无权限信息的场景。
     * </p>
     *
     * @return 空的权限上下文对象
     */
    public static PermContext empty() {
        return new PermContext(null, null, null, java.util.Set.of(), java.util.Set.of());
    }
}
