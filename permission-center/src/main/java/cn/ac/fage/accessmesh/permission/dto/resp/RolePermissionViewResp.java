package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

/**
 * 角色权限视图响应体
 * <p>
 * 返回角色的权限配置视图，包括角色信息和权限列表。
 * 用于角色权限配置查询接口的响应。
 * </p>
 *
 * @param roleId     角色ID
 * @param roleName   角色名称
 * @param roleTypeCode 角色类型编码
 * @param permissions 权限列表
 */
public record RolePermissionViewResp(
    Long roleId,
    String roleName,
    String roleTypeCode,
    List<PermissionItem> permissions
) {
    /**
     * 权限条目信息
     * <p>
     * 表示角色拥有的单个权限配置信息。
     * </p>
     *
     * @param id               权限配置ID
     * @param resourceEntityId 资源实体ID
     * @param resourceCode     资源编码
     * @param resourceName     资源名称
     * @param resourceTypeCode 资源类型编码
     * @param operationPermissionId 操作权限ID
     * @param operationCode    操作编码
     * @param operationName    操作名称
     * @param dependOn         依赖的权限ID，无依赖时为null
     * @param conditionId      条件ID，无条件时为null
     * @param canGrant         是否可授予他人
     * @param grantSource      授权来源编码
     */
    public record PermissionItem(
        Long id,
        Long resourceEntityId,
        String resourceCode,
        String resourceName,
        String resourceTypeCode,
        Long operationPermissionId,
        String operationCode,
        String operationName,
        Long dependOn,
        Long conditionId,
        Boolean canGrant,
        String grantSource
    ) {}
}