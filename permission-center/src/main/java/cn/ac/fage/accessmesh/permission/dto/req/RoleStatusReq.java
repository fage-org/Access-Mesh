package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Role status change: tenantId + roleId + status (0=disabled, 1=enabled).
 */
public record RoleStatusReq(
    @NotNull Long roleId,
    @NotNull Integer status
) {}
