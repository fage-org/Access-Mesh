package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

public record RolePermissionChildrenReq(
    @NotNull Long permissionId
) {}
