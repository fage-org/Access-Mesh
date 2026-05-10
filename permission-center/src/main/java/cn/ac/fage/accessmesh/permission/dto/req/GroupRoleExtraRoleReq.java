package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 分组角色额外角色请求体
 * <p>
 * 用于为分组角色添加或移除额外的基础角色关联。
 * 使用业务键标识角色，不使用内部角色ID。
 * </p>
 *
 * @param groupDomainCode     分组角色业务域编码，可选
 * @param groupRoleTypeCode   分组角色类型编码，必填
 * @param groupRoleExternalId 分组角色外部标识，必填
 * @param basicDomainCode     基础角色业务域编码，可选
 * @param basicRoleTypeCode   基础角色类型编码，必填
 * @param basicRoleExternalId 基础角色外部标识，必填
 */
public record GroupRoleExtraRoleReq(
    String groupDomainCode,
    @NotBlank String groupRoleTypeCode,
    @NotBlank String groupRoleExternalId,
    String basicDomainCode,
    @NotBlank String basicRoleTypeCode,
    @NotBlank String basicRoleExternalId
) {}