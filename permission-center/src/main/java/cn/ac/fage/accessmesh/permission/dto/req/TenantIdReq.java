package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Reusable: list endpoints where tenantId comes from X-Tenant-Id header.
 * Empty request body.
 */
public record TenantIdReq() {}
