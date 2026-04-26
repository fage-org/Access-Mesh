package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * List resource dependencies with optional filter.
 */
public record DependencyListReq(
    @NotNull Long tenantId,
    Long resourceEntityId
) {}
