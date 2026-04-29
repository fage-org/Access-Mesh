package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Get role permissions view with optional sub-node expansion.
 */
public record RolePermissionViewReq(
    @NotNull Long roleId,
    Boolean expandSub
) {}
