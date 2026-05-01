package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Get resource tree with optional type filter.
 */
public record ResourceTreeReq(
    String resourceTypeCode
) {}
