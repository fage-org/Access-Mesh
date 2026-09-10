package cn.ac.fage.accessmesh.perm.common.dto.resp;

import java.util.List;

/**
 * 接口权限检查响应体
 * <p>
 * Gateway回调的接口权限检查响应。
 * 返回是否允许访问、拒绝原因和匹配的资源信息。
 * </p>
 * <p>
 * T-API-003（2026-09-09 定案，推翻 T-API-002 的 check 族裁剪）：匹配资源的结果记录
 * （resourceId=resource_entity 内部行 id、matchedRoleIds / matchedPermissionIds）
 * 全量回传——统一引擎消费方模型「调用方根据结果记录判定」需要记录在场。
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
     * @param resourceId           资源实体ID
     * @param resourceTypeCode     资源类型编码
     * @param resourceCode         资源编码
     * @param operationCode        操作编码
     * @param allowed              是否允许访问此资源
     * @param matchedRoleIds       匹配的角色ID列表
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
