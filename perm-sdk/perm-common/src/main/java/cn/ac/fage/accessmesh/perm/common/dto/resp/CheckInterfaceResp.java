package cn.ac.fage.accessmesh.perm.common.dto.resp;

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
 * 「SDK 四件套不要求/不泄漏内部数据库 ID」口径对齐。
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
