package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Shared: list operations with optional resourceType filter.
 */
public record OperationListReq(
    @NotNull Long tenantId,
    Integer resourceType
) {}
