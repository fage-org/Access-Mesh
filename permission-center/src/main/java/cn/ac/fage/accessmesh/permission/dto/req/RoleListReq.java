package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * List roles: tenantId + pagination.
 */
public record RoleListReq(
    @NotNull Long tenantId,
    Integer offset,
    Integer limit
) {}
