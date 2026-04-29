package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Reusable: get/delete endpoints needing single id (tenantId from X-Tenant-Id header).
 */
public record IdWithTenantReq(
    @NotNull Long id
) {}
