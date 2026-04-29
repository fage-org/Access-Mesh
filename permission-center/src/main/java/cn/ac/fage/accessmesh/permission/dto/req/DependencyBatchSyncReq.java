package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Batch sync resource dependencies.
 */
public record DependencyBatchSyncReq(
    @NotNull Long roleId
) {}
