package cn.ac.fage.accessmesh.access.permission.dto.resp;

import java.util.List;

/**
 * 接口权限检查响应体
 * <p>
 * Gateway回调的接口权限检查响应。
 * 返回是否允许访问、拒绝原因和匹配的资源信息。
 * </p>
 *
 * @param allowed         是否允许访问
 * @param reason          拒绝原因，允许时为null
 * @param matchedResources 匹配的资源列表
 * @param cacheTtlSeconds 缓存有效时间（秒）
 */
public record CheckInterfaceResp(
    boolean allowed,
    String reason,
    List<MatchedResource> matchedResources,
    int cacheTtlSeconds
) {
    /**
     * 创建允许的响应
     *
     * @param matchedResources 匹配的资源列表
     * @param cacheTtlSeconds  缓存有效时间（秒）
     * @return 允许的响应对象
     */
    public static CheckInterfaceResp allow(List<MatchedResource> matchedResources, int cacheTtlSeconds) {
        return new CheckInterfaceResp(true, null, matchedResources, cacheTtlSeconds);
    }

    /**
     * 创建拒绝的响应
     *
     * @param reason           拒绝原因
     * @param matchedResources 匹配的资源列表
     * @param cacheTtlSeconds  缓存有效时间（秒）
     * @return 拒绝的响应对象
     */
    public static CheckInterfaceResp deny(String reason, List<MatchedResource> matchedResources,
                                          int cacheTtlSeconds) {
        return new CheckInterfaceResp(false, reason, matchedResources, cacheTtlSeconds);
    }

    /**
     * 创建简化的拒绝响应
     * <p>
     * 使用默认缓存时间30秒。
     * </p>
     *
     * @param reason 拒绝原因
     * @return 拒绝的响应对象
     */
    public static CheckInterfaceResp deny(String reason) {
        return deny(reason, List.of(), 30);
    }

    /**
     * 匹配资源信息
     * <p>
     * 表示一次接口权限检查匹配的资源详情。
     * </p>
     *
     * @param resourceId         资源ID
     * @param resourceTypeCode   资源类型编码
     * @param resourceCode       资源编码
     * @param operationCode      操作编码
     * @param allowed            是否允许访问此资源
     * @param matchedRoleIds     匹配的角色ID列表
     * @param matchedPermissionIds 匹配的权限ID列表
     */
    public record MatchedResource(
        Long resourceId,
        String resourceTypeCode,
        String resourceCode,
        String operationCode,
        boolean allowed,
        List<Long> matchedRoleIds,
        List<Long> matchedPermissionIds
    ) {}
}