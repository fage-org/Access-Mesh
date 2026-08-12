package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 角色权限列表查询请求体
 * <p>
 * 用于查询角色的权限配置列表。
 * </p>
 *
 * @param domainCode     业务域编码，可选
 * @param roleTypeCode   角色类型编码，必填
 * @param roleExternalId 角色外部标识，必填
 * @param resourceTypeCode 可选的主权限资源类型过滤
 * @param includeChildren 是否返回命中主权限的直接子权限，默认 true
 */
public record RolePermissionListReq(
    String domainCode,
    @NotBlank String roleTypeCode,
    @NotBlank String roleExternalId,
    String resourceTypeCode,
    Boolean includeChildren
) {
    public boolean shouldIncludeChildren() {
        return includeChildren == null || includeChildren;
    }
}
