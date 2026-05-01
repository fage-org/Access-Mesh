package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

public record UserUpdateReq(
    @NotNull Long userId,
    String name,
    Boolean enabled,
    String extra
) {}
