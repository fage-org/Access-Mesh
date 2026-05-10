package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 系统配置获取请求体
 * <p>
 * 用于根据配置键查询系统配置详情。
 * 租户ID不在请求体中，从X-Tenant-Id请求头获取。
 * </p>
 *
 * @param configKey 配置键，必填
 */
public record SystemConfigGetReq(
    @NotNull String configKey
) {}