package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Reusable: list endpoint with tenantId + optional filter.
 */
public record ListWithTenantReq(
    @NotNull Long tenantId,
    Integer filterValue
) {}
