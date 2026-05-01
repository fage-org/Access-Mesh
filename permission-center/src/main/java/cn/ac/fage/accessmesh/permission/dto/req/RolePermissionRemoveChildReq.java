package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

public record RolePermissionRemoveChildReq(
    @NotNull Long permissionId
) {}
