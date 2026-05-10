package cn.ac.fage.accessmesh.permission.dto.resp;

import java.time.LocalDateTime;

/**
 * 服务配置响应体
 * <p>
 * 返回服务配置的详细信息，包括服务编码、名称、路径等。
 * 用于服务配置查询接口的响应。
 * </p>
 *
 * @param id          服务配置ID
 * @param tenantId    租户ID
 * @param serviceCode 服务编码，唯一标识
 * @param name        服务名称，用于显示
 * @param basePath    服务基础路径，用于API匹配
 * @param description 服务描述
 * @param status      服务状态，0=禁用，1=启用
 * @param extra       扩展属性JSON
 * @param createdAt   创建时间
 */
public record ServiceConfigResp(
    Long id,
    Long tenantId,
    String serviceCode,
    String name,
    String basePath,
    String description,
    Integer status,
    String extra,
    LocalDateTime createdAt
) {}