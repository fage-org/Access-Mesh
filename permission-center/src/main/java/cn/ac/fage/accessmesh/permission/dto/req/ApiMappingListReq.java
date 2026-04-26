package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * List API mappings.
 */
public record ApiMappingListReq(
    @NotNull Long tenantId,
    @NotNull Long resourceId
) {}
