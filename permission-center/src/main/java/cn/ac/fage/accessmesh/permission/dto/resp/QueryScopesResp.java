package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

/**
 * 权限范围查询响应体
 * <p>
 * 返回用户可访问的资源范围，包括匹配的操作和来源信息。
 * 用于权限范围查询接口的响应。
 * </p>
 *
 * @param allowed              是否允许访问
 * @param reason               拒绝原因，允许时为null
 * @param matchedParentOperations 匹配的父操作列表
 * @param parentPermissionIds  父权限ID列表
 * @param items                范围条目列表
 * @param mergeMode            合并模式（UNION/INTERSECT）
 * @param permissionVersion    权限版本号
 * @param cacheTtlSeconds      缓存有效时间（秒）
 */
public record QueryScopesResp(
    boolean allowed,
    String reason,
    List<String> matchedParentOperations,
    List<Long> parentPermissionIds,
    List<ScopeEntry> items,
    String mergeMode,
    String permissionVersion,
    int cacheTtlSeconds
) {
    /**
     * 范围条目
     * <p>
     * 表示用户可访问的单个资源范围，包括资源和操作信息。
     * </p>
     *
     * @param resourceTypeCode   资源类型编码
     * @param resourceCode       资源编码
     * @param codeType           编码类型
     * @param resourceName       资源名称
     * @param scopeAll           是否范围全部
     * @param operations         操作权限列表
     * @param sources            权限来源列表
     * @param matchedRoleIds     匹配的角色ID列表
     * @param matchedPermissionIds 匹配的权限ID列表
     * @param dependOnPermissionIds 依赖的权限ID列表
     */
    public record ScopeEntry(
        String resourceTypeCode,
        String resourceCode,
        String codeType,
        String resourceName,
        boolean scopeAll,
        List<String> operations,
        List<String> sources,
        List<Long> matchedRoleIds,
        List<Long> matchedPermissionIds,
        List<Long> dependOnPermissionIds
    ) {}
}