package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 权限版本查询请求体
 * <p>
 * 用于查询角色的权限版本号，支持缓存一致性检查。
 * 租户ID不在请求体中，从X-Tenant-Id请求头获取。
 * </p>
 *
 * @param roleTypeCode   角色类型编码，必填
 * @param roleExternalId 角色外部标识，必填
 * @param domainCode     业务域编码，可选
 */
public record PermissionVersionQueryReq(
    @NotBlank String roleTypeCode,
    @NotBlank String roleExternalId,
    String domainCode
) {}