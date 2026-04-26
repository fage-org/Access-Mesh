package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

public record PageReq(
    @NotNull Integer pageNum,
    @NotNull Integer pageSize
) {}
