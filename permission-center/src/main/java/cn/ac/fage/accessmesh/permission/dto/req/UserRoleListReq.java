package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户角色列表查询请求体
 * <p>
 * 用于查询用户拥有的角色关联关系列表，使用用户业务键标识。
 * 租户ID不在请求体中，从X-Tenant-Id请求头获取。
 * </p>
 *
 * @param subjectTypeCode   用户类型编码，必填
 * @param subjectExternalId 用户外部标识，必填
 */
public record UserRoleListReq(
    @NotBlank String subjectTypeCode,
    @NotBlank String subjectExternalId
) {}