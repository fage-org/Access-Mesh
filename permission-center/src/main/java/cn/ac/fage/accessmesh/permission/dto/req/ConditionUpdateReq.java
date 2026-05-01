package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

public record ConditionUpdateReq(
    @NotNull Long conditionId,
    String name,
    String conditionRules,
    Boolean enabled,
    String description
) {}
