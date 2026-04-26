package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Delete service config by tenantId + serviceCode.
 */
public record ServiceConfigDeleteReq(
    @NotNull Long tenantId,
    @NotNull String serviceCode
) {}
