package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Role update: tenantId + roleId + optional fields.
 */
public record RoleUpdateReq(
    @NotNull Long roleId,
    String name,
    Integer sortOrder,
    String extra
) {}
