package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Reusable: paginated query (tenantId from X-Tenant-Id header).
 */
public record PageWithTenantReq(
    @NotNull Integer pageNum,
    @NotNull Integer pageSize
) {}
