package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * Get role permissions view with optional sub-node expansion.
 */
public record RolePermissionViewReq(
    String domainCode,
    @NotBlank String roleTypeCode,
    @NotBlank String roleExternalId,
    Boolean expandSub
) {}
