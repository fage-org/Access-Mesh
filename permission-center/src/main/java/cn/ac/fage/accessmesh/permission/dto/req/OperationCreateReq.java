package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Create operation permission.
 */
public record OperationCreateReq(
    @NotNull Long tenantId,
    @NotNull Integer resourceType,
    @NotNull String code,
    @NotNull String name,
    @NotNull Long binaryBit,
    Long inheritMask
) {}
