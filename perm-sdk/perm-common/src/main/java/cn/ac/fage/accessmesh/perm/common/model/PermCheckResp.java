package cn.ac.fage.accessmesh.perm.common.model;

/**
 * 远程权限校验响应
 * <p>
 * 权限中心返回的权限校验结果。
 * 包含是否允许和拒绝原因等信息。
 * </p>
 */
public record PermCheckResp(
    boolean permitted,
    String reason
) {
    /**
     * 创建允许通过的响应
     *
     * @return 允许通过的权限校验响应
     */
    public static PermCheckResp allow() {
        return new PermCheckResp(true, null);
    }

    /**
     * 创建拒绝通过的响应
     *
     * @param reason 拒绝原因
     * @return 拒绝通过的权限校验响应
     */
    public static PermCheckResp deny(String reason) {
        return new PermCheckResp(false, reason);
    }
}
