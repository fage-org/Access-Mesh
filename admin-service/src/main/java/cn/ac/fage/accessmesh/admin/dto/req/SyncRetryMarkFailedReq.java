package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.NotNull;

public record SyncRetryMarkFailedReq(
    @NotNull Long id,
    String error
) {}
