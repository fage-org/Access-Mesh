package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Revoke role from user.
 */
public record UserRevokeRoleReq(
    @NotNull Long userId,
    @NotNull Long userRoleId
) {}
