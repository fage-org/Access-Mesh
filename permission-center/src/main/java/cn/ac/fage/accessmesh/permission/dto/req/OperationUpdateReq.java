package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Update operation: tenantId + operationId + optional fields.
 */
public record OperationUpdateReq(
    @NotNull Long operationId,
    String name,
    Long binaryBit,
    Long inheritMask
) {}
