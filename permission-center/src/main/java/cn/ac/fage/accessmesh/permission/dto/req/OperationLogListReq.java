package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * List operation logs with optional filters.
 */
public record OperationLogListReq(
    String module,
    String action,
    @NotNull Integer pageNum,
    @NotNull Integer pageSize
) {}
