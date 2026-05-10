package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 业务域创建请求体
 * <p>
 * 用于创建新的业务域，包括编码、名称和描述。
 * </p>
 *
 * @param code        业务域编码，必填，唯一标识
 * @param name        业务域名称，必填，用于显示
 * @param description 业务域描述，可选
 */
public record BizDomainCreateReq(
    @NotBlank String code,
    @NotBlank String name,
    String description
) {}