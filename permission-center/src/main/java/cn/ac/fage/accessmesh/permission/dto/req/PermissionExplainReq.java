package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

public record PermissionExplainReq(
    @NotBlank String targetType,
    String subjectTypeCode,
    String subjectExternalId,
    String roleTypeCode,
    String roleExternalId,
    String domainCode,
    @NotBlank String resourceTypeCode,
    @NotBlank String resourceCode,
    String codeType,
    @NotBlank String operationCode,
    Boolean includeSourceRoles,
    Boolean includeRecentChanges,
    Integer recentDays
) {}
