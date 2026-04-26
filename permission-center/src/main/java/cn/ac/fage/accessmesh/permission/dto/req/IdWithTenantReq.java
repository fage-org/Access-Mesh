package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Reusable: get/delete endpoints needing tenantId + single id.
 */
public record IdWithTenantReq(
    @NotNull Long tenantId,
    @NotNull Long id
) {}
