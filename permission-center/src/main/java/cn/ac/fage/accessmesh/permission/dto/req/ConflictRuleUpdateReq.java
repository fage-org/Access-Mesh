package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

public record ConflictRuleUpdateReq(
    @NotNull Long id,
    Long bizDomainId,
    String conflictType,
    Long firstOperationPermissionId,
    Long secondOperationPermissionId,
    Integer resourceTypeValue,
    Long firstAbstractRoleId,
    Long secondAbstractRoleId,
    String description
) {}
