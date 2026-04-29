package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserAssignRoleReq(
    @NotNull Long abstractUserId,
    @NotBlank String targetType,
    @NotNull Long targetId,
    Long relationId,
    java.time.LocalDateTime validFrom,
    java.time.LocalDateTime validTo
) {}
