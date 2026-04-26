package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Remove API mapping.
 */
public record ApiMappingRemoveReq(
    @NotNull Long tenantId,
    @NotNull Long resourceId,
    @NotNull Long mappingId
) {}
