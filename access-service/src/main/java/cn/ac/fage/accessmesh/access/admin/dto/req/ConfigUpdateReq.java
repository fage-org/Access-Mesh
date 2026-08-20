package cn.ac.fage.accessmesh.access.admin.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 系统配置更新请求记录类
 * <p>
 * 用于更新系统配置值的请求参数。
 * 配置键不可修改，仅可更新配置值和备注。
 * </p>
 *
 * @param id           配置ID（必填，用于定位配置）
 * @param configKey    配置键（可选，用于审计脱敏——跨字段规则据此判定 configValue 是否密钥类并以 {@code ***} 掩码，评审 P1#2）
 * @param configValue  配置值（可选）
 * @param remark       备注（可选）
 */
public record ConfigUpdateReq(
    /**
     * 配置ID
     */
    @NotNull(message = "配置ID不能为空")
    Long id,

    /**
     * 配置键
     */
    String configKey,

    /**
     * 配置值
     */
    String configValue,

    /**
     * 备注
     */
    String remark
) {}