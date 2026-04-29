package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

public record ResourceDependencyCreateReq(
    @NotNull Long resourceEntityId,
    @NotNull Long dependsOnResourceEntityId,
    Long sourceOperationBits,
    @NotNull Long requiredOperationBits,
    Boolean autoGrant,
    String description
) {}
