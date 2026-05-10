package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

/**
 * 资源查询响应体
 * <p>
 * 返回用户可访问的资源列表，包括权限版本和缓存信息。
 * 用于资源查询接口的响应。
 * </p>
 *
 * @param items            资源条目列表
 * @param permissionVersion 权限版本号
 * @param cacheTtlSeconds  缓存有效时间（秒）
 */
public record QueryResourcesResp(
    List<ResourceEntry> items,
    String permissionVersion,
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
        List<String> operations,
        List<Long> matchedRoleIds,
        List<Long> matchedPermissionIds,
        List<String> grantSources
    ) {}
}