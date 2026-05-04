package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Shared: batch create resources in permission-center.
 */
public record ResourceBatchCreateReq(
    @NotEmpty List<ResourceCreateReq> items
) {}