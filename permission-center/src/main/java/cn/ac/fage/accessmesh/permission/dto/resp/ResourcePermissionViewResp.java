package cn.ac.fage.accessmesh.permission.dto.resp;

import java.util.List;

/**
 * 资源权限视图响应体
 * <p>
 * 返回资源被哪些角色拥有权限的视图。
 * 用于资源权限配置查询接口的响应。
 * </p>
 *
 * @param resourceEntityId 资源实体ID
 * @param resourceCode     资源编码
 * @param resourceName     资源名称
 * @param roles            角色授权信息列表
 */
public record ResourcePermissionViewResp(
    Long resourceEntityId,
    String resourceCode,
    String resourceName,
    List<RoleGrantInfo> roles
) {
    /**
     * 角色授权信息
     * <p>
     * 表示拥有此资源权限的角色信息，包括操作权限和授权来源。
     * </p>
     *
     * @param roleId     角色ID
     * @param roleName   角色名称
     * @param roleTypeCode 角色类型编码
     * @param operations 操作权限列表
     * @param grantSource 授权来源编码
     */
    public record RoleGrantInfo(
        Long roleId,
        String roleName,
        String roleTypeCode,
        List<String> operations,
        String grantSource
    ) {}
}