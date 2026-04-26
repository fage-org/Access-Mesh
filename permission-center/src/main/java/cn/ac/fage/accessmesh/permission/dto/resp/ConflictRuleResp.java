package cn.ac.fage.accessmesh.permission.dto.resp;

import java.time.LocalDateTime;

public record ConflictRuleResp(
    Long id,
    Long tenantId,
    Long bizDomainId,
    String conflictType,
    Long firstOperationPermissionId,
    Long secondOperationPermissionId,
    Integer resourceTypeValue,
    Long firstAbstractRoleId,
    Long secondAbstractRoleId,
    String description,
    LocalDateTime createdAt
) {}
