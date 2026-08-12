package cn.ac.fage.accessmesh.access.admin.dto.resp;

import java.time.LocalDateTime;

/**
 * 系统配置响应记录类
 * <p>
 * 用于返回系统配置信息。
 * 包含配置名称、键、值、备注、创建时间、更新时间。
 * </p>
 *
 * @param id           配置ID
 * @param configName   配置名称
 * @param configKey    配置键（用于系统读取）
 * @param configValue  配置值
 * @param remark       备注
 * @param createdAt    创建时间
 * @param updatedAt    更新时间
 */
public record ConfigResp(
    /**
     * 配置ID
     */
    Long id,

    /**
     * 配置名称
     */
    String configName,

    /**
     * 配置键（用于系统读取）
     */
    String configKey,

    /**
     * 配置值
     */
    String configValue,

    /**
     * 备注
     */
    String remark,

    /**
     * 创建时间
     */
    LocalDateTime createdAt,

    /**
     * 更新时间
     */
    LocalDateTime updatedAt
) {}