package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

public record RoleMoveReq(
    @NotNull Long roleId,
    Long parentId
) {}
