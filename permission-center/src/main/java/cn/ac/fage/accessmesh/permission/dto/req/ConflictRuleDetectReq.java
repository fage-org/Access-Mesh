package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

public record ConflictRuleDetectReq(
    @NotNull Long firstOperationPermissionId,
    @NotNull Long secondOperationPermissionId,
    Integer resourceTypeValue
) {}
