package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 角色权限视图查询请求体
 * <p>
 * 用于查询角色的权限配置视图，可选展开子节点权限。
 * </p>
 *
 * @param domainCode     业务域编码，可选
 * @param roleTypeCode   角色类型编码，必填
 * @param roleExternalId 角色外部标识，必填
 * @param expandSub      是否展开子节点权限，可选
 */
public record RolePermissionViewReq(
    String domainCode,
    @NotBlank String roleTypeCode,
    @NotBlank String roleExternalId,
    Boolean expandSub
) {}