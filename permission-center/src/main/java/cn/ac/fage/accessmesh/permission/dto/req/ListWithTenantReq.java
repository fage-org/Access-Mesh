package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Reusable: list endpoint with optional filter (tenantId from X-Tenant-Id header).
 */
public record ListWithTenantReq(
    Integer filterValue
) {}
