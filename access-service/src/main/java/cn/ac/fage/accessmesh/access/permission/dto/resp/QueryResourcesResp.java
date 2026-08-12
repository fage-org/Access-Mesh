package cn.ac.fage.accessmesh.access.permission.dto.resp;

import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;

import java.util.List;

/**
 * 资源查询响应体
 * <p>
 * 返回用户可访问的资源列表，包括缓存信息。
 * 用于资源查询接口的响应。
 * </p>
 * <p>
 * T-PERM-013：scopeAll(boolean) → scopeMode(ScopeMode)，对外协议统一使用枚举。
 * 资源条目仅使用 INSTANCE / ALL 两态。
 * </p>
 *
 * @param items           资源条目列表
 * @param cacheTtlSeconds 缓存有效时间（秒）
 */
public record QueryResourcesResp(
    List<ResourceEntry> items,
    int cacheTtlSeconds
) {
    /**
     * 资源条目
     * <p>
     * 表示用户可访问的单个资源信息，包括操作权限和来源信息。
     * </p>
     *
     * @param resourceTypeCode   资源类型编码
     * @param resourceCode       资源编码
     * @param codeType           编码类型
     * @param resourceName       资源名称
     * @param canGrant           是否可授予他人
     * @param scopeMode          范围模式（T-PERM-013）：INSTANCE=具体实例，ALL=全量范围
     * @param operations         操作权限列表
     * @param matchedRoleIds     匹配的角色ID列表
     * @param matchedPermissionIds 匹配的权限ID列表
     * @param grantSources       授权来源列表
     */
    public record ResourceEntry(
        String resourceTypeCode,
        String resourceCode,
        String codeType,
        String resourceName,
        boolean canGrant,
        ScopeMode scopeMode,
        List<String> operations,
        List<Long> matchedRoleIds,
        List<Long> matchedPermissionIds,
        List<String> grantSources
    ) {}
}