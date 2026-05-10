package cn.ac.fage.accessmesh.permission.dto.resp;

import java.time.LocalDateTime;

/**
 * 系统配置响应体
 * <p>
 * 返回系统配置的详细信息，包括配置键、配置值等。
 * 用于系统配置查询接口的响应。
 * </p>
 *
 * @param id          配置ID
 * @param tenantId    租户ID
 * @param configKey   配置键，唯一标识
 * @param configValue 配置值
 * @param description 配置描述
 * @param updatedAt   更新时间
 */
public record SystemConfigResp(
    Long id,
    Long tenantId,
    String configKey,
    String configValue,
    String description,
    LocalDateTime updatedAt
) {}