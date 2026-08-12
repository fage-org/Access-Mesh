package cn.ac.fage.accessmesh.access.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 系统配置保存请求体
 * <p>
 * 用于保存或更新系统配置，包括配置键、配置值和描述。
 * </p>
 *
 * @param configKey   配置键，必填，唯一标识
 * @param configValue 配置值，必填
 * @param description 配置描述，可选
 */
public record SystemConfigReq(
    @NotBlank String configKey,
    @NotBlank String configValue,
    String description
) {}