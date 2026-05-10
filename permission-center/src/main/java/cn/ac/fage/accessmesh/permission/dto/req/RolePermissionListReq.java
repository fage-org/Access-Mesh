package cn.ac.fage.accessmesh.permission.dto.req;

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
 */
public record RolePermissionListReq(
    String domainCode,
    @NotBlank String roleTypeCode,
    @NotBlank String roleExternalId
) {}