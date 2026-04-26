package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Update API mapping: path params in body + mapping fields.
 */
public record ApiMappingUpdateReq(
    @NotNull Long tenantId,
    @NotNull Long resourceId,
    @NotNull Long mappingId,
    @NotNull String apiPath,
    @NotNull String method,
    String description
) {}
