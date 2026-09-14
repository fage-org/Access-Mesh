package cn.ac.fage.accessmesh.access.platform.dto.resp;

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
 * @param isSystem    是否系统内置（内置行仅走种子，save 拒改 20064；前端据此隐藏编辑入口）
 * @param updatedAt   更新时间
 */
public record SystemConfigResp(
    Long id,
    Long tenantId,
    String configKey,
    String configValue,
    String description,
    Boolean isSystem,
    LocalDateTime updatedAt
) {}