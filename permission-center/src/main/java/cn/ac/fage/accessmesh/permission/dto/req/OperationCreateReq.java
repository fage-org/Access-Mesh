package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Create operation permission.
 */
public record OperationCreateReq(
    @NotNull Integer resourceType,
    @NotNull String code,
    @NotNull String name,
    @NotNull Long binaryBit,
    Long inheritMask
) {}
