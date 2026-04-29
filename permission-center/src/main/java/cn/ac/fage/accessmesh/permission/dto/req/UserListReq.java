package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * List users: tenantId + pagination.
 */
public record UserListReq(
    Integer offset,
    Integer limit
) {}
