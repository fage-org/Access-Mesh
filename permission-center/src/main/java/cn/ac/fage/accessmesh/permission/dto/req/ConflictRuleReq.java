package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConflictRuleReq(
    Long bizDomainId,
    @NotBlank String conflictType,
    Long firstOperationPermissionId,
    Long secondOperationPermissionId,
    Integer resourceTypeValue,
    Long firstAbstractRoleId,
    Long secondAbstractRoleId,
    String description
) {}
