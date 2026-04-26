package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Get resource tree with optional type filter.
 */
public record ResourceTreeReq(
    @NotNull Long tenantId,
    Integer resourceType
) {}
