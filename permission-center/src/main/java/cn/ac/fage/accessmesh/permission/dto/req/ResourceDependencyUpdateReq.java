package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

public record ResourceDependencyUpdateReq(
    @NotNull Long id,
    Long sourceOperationBits,
    Long requiredOperationBits,
    Boolean autoGrant,
    String description
) {}
