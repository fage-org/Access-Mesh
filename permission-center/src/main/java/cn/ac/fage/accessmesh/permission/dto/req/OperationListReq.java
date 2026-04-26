package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * List operations with optional resourceType filter.
 */
public record OperationListReq(
    @NotNull Long tenantId,
    Integer resourceType
) {}
