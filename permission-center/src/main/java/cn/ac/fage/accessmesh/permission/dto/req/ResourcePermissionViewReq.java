package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * Get resource permissions view.
 */
public record ResourcePermissionViewReq(
    String domainCode,
    @NotBlank String resourceTypeCode,
    @NotBlank String resourceCode,
    String codeType
) {}
