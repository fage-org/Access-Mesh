package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * List users: tenantId + pagination.
 */
public record UserListReq(
    @NotNull Long tenantId,
    Integer offset,
    Integer limit
) {}
