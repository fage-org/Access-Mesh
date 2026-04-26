package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Reusable: list endpoints that only need tenantId.
 */
public record TenantIdReq(
    @NotNull Long tenantId
) {}
