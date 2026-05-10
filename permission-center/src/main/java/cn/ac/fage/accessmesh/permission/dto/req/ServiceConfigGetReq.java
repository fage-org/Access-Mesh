package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * 服务配置获取请求体
 * <p>
 * 用于根据服务编码查询服务配置详情。
 * 租户ID不在请求体中，从X-Tenant-Id请求头获取。
 * </p>
 *
 * @param serviceCode 服务编码，必填
 */
public record ServiceConfigGetReq(
    @NotNull String serviceCode
) {}