package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * List change logs with optional filters.
 */
public record ChangeLogListReq(
    String entityType,
    Long entityId,
    @NotNull Integer pageNum,
    @NotNull Integer pageSize
) {}
