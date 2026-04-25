package cn.ac.fage.accessmesh.perm.common.model;

/**
 * Permission context passed between services via SDK.
 */
public record PermContext(
    Long userId,
    String username,
    String token,
    java.util.Set<String> permissions,
    java.util.Set<String> roles
) {
    public static PermContext empty() {
        return new PermContext(null, null, null, java.util.Set.of(), java.util.Set.of());
    }
}
