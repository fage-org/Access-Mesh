package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * Query scope resources within a primary resource context.
 * tenantId is NOT in the body; it is read from X-Tenant-Id header.
 */
public record QueryScopesReq(
    @NotBlank String subjectTypeCode,
    @NotBlank String subjectExternalId,
    @NotBlank String resourceTypeCode,
    @NotBlank String resourceCode,
    @NotBlank String operationCode,
    String domainCode,
    String codeType
) {}
