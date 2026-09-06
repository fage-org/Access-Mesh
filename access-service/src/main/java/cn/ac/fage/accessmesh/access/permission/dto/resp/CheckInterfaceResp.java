package cn.ac.fage.accessmesh.access.permission.dto.resp;

import java.util.List;

/**
 * 接口权限检查响应体
 * <p>
 * Gateway回调的接口权限检查响应。
 * 返回是否允许访问、拒绝原因和匹配的资源信息。
 * </p>
 * <p>
 * T-API-002（2026-09-06 定案，用户决策扩大裁剪面）：匹配资源的内部数据库 id 字段族
 * （resourceId=resource_entity 内部行 id、matchedRoleIds / matchedPermissionIds）
 * 裁剪，资源身份以业务键 resourceTypeCode + resourceCode 表达，与 core-flows §15
 * 「SDK 四件套不要求/不泄漏内部数据库 ID」口径对齐。线格式与 perm-common 副本
 * 保持同形（双副本形状由回归锁钉死）。
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
     * @param resourceTypeCode   资源类型编码
     * @param resourceCode       资源编码
     * @param operationCode      操作编码
     * @param allowed            是否允许访问此资源
     */
    public record MatchedResource(
        String resourceTypeCode,
        String resourceCode,
        String operationCode,
        boolean allowed
    ) {}
}
