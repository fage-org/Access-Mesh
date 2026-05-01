package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

public record ResourceDependencyCheckReq(
    @NotNull Long resourceEntityId,
    @NotNull Long dependsOnResourceEntityId
) {}
