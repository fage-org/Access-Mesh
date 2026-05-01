package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Create operation permission.
 */
public record OperationCreateReq(
    @NotBlank String resourceTypeCode,
    @NotBlank String code,
    @NotBlank String name,
    @NotNull Long binaryBit,
    Long inheritMask
) {}
