package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户创建请求体
 * <p>
 * 用于创建新的抽象用户，包括用户类型、外部标识和名称。
 * </p>
 *
 * @param subjectTypeCode 用户类型编码，必填
 * @param externalId      外部标识，必填，用于与外部系统关联
 * @param name            用户名称，可选
 * @param enabled         是否启用，可选
 * @param extra           扩展属性JSON，可选
 */
public record UserCreateReq(
    @NotBlank String subjectTypeCode,
    @NotBlank String externalId,
    String name,
    Boolean enabled,
    String extra
) {}