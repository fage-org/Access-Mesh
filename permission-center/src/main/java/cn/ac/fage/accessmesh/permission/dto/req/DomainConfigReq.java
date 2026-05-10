package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 域配置保存请求体
 * <p>
 * 用于保存或更新域配置，包括业务域编码、配置类型和配置值。
 * </p>
 *
 * @param domainCode 业务域编码，必填
 * @param configType 配置类型编码，必填
 * @param extra      配置值JSON，必填
 */
public record DomainConfigReq(
    @NotBlank String domainCode,
    @NotBlank String configType,
    @NotBlank String extra
) {}