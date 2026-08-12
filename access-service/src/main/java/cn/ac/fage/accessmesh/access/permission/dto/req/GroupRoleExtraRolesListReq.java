package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 分组角色额外角色列表查询请求体
 * <p>
 * 用于查询分组角色关联的额外基础角色列表。
 * 使用业务键标识分组角色。
 * </p>
 *
 * @param domainCode        业务域编码，可选
 * @param groupRoleTypeCode 分组角色类型编码，必填
 * @param groupRoleExternalId 分组角色外部标识，必填
 */
public record GroupRoleExtraRolesListReq(
    String domainCode,
    @NotBlank String groupRoleTypeCode,
    @NotBlank String groupRoleExternalId
) {}