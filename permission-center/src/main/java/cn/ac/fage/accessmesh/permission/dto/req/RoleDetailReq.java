package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 角色详情查询请求体
 * <p>
 * 用于查询角色的详细信息，使用稳定的业务键标识角色。
 * </p>
 *
 * @param domainCode     业务域编码，可选，最大64字符
 * @param roleTypeCode   角色类型编码，必填，最大64字符
 * @param roleExternalId 角色外部标识，必填，最大128字符
 */
public record RoleDetailReq(
    @Size(max = 64) String domainCode,
    @NotBlank(message = "角色类型编码不能为空") @Size(max = 64) String roleTypeCode,
    @NotBlank(message = "角色外部标识不能为空") @Size(max = 128) String roleExternalId
) {}