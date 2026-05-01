package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Shared: get/delete endpoints needing tenantId + single id.
 */
public record IdWithTenantReq(
    @NotNull Long id
) {}
