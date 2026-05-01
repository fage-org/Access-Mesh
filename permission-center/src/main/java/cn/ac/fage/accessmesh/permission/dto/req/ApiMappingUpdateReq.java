package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Update API mapping — field names consistent with ApiMappingAddReq.
 */
public record ApiMappingUpdateReq(
    @NotNull Long resourceId,
    @NotNull Long mappingId,
    String httpMethod,
    String pathPattern,
    Integer matchOrder,
    Boolean enabled,
    String extra
) {}
