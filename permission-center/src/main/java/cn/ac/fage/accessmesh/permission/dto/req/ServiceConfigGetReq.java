package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Get service config by tenantId + serviceCode.
 */
public record ServiceConfigGetReq(
    @NotNull String serviceCode
) {}
