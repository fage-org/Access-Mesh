package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Get user permissions view.
 */
public record UserPermissionViewReq(
    @NotNull Long userId
) {}
