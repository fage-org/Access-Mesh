package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Enable/disable user.
 */
public record UserSetEnabledReq(
    @NotNull Long userId,
    @NotNull Boolean enabled
) {}
