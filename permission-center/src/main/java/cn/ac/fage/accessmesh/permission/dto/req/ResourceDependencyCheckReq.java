package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * Check dependency cycle using stable business keys (consistent with batch-sync §6.9).
 */
public record ResourceDependencyCheckReq(
    @NotBlank String sourceResourceTypeCode,
    @NotBlank String sourceResourceCode,
    String sourceCodeType,
    @NotBlank String targetResourceTypeCode,
    @NotBlank String targetResourceCode,
    String targetCodeType
) {}
