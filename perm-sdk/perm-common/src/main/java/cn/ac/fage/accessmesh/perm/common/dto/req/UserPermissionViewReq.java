package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Shared: get user permissions view.
 */
public record UserPermissionViewReq(
    @NotNull Long tenantId,
    @NotNull Long userId
) {}
