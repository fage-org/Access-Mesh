package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户有效角色查询请求体
 * <p>
 * 用于查询用户在特定业务域下的有效角色列表。
 * </p>
 *
 * @param subjectTypeCode   用户类型编码，必填
 * @param subjectExternalId 用户外部标识，必填
 * @param domainCode        业务域编码，可选
 */
public record UserEffectiveRolesReq(
    @NotBlank String subjectTypeCode,
    @NotBlank String subjectExternalId,
    String domainCode
) {}