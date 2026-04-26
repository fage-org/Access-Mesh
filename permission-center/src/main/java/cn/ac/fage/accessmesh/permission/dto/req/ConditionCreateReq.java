package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConditionCreateReq(
    @NotNull Long tenantId,
    @NotBlank String code,
    @NotBlank String name,
    @NotBlank String conditionRules,
    Boolean enabled,
    String description
) {}
