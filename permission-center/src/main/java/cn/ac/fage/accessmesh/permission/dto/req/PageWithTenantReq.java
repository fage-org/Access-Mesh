package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Reusable: paginated query with tenantId.
 */
public record PageWithTenantReq(
    @NotNull Long tenantId,
    @NotNull Integer pageNum,
    @NotNull Integer pageSize
) {}
