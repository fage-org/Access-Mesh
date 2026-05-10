package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 服务配置保存请求体
 * <p>
 * 用于保存或更新服务配置，包括服务编码、名称、路径等。
 * </p>
 *
 * @param serviceCode 服务编码，必填，唯一标识
 * @param name        服务名称，必填，用于显示
 * @param basePath    服务基础路径，可选，用于API匹配
 * @param description 服务描述，可选
 * @param status      服务状态，可选，0=禁用，1=启用
 * @param extra       扩展属性JSON，可选
 */
public record ServiceConfigReq(
    @NotBlank String serviceCode,
    @NotBlank String name,
    String basePath,
    String description,
    Integer status,
    String extra
) {}