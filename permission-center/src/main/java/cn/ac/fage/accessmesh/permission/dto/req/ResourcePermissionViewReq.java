package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Get resource permissions view.
 */
public record ResourcePermissionViewReq(
    @NotNull Long tenantId,
    @NotNull Long resourceEntityId
) {}
