package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Enable/disable a permission condition.
 */
public record ConditionSetEnabledReq(
    @NotNull Long tenantId,
    @NotNull Long conditionId,
    @NotNull Boolean enabled
) {}
