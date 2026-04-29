package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Get system config by tenantId + configKey.
 */
public record SystemConfigGetReq(
    @NotNull String configKey
) {}
