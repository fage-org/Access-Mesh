package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * Query resources accessible to a subject for a given resource type and operation.
 * tenantId is NOT in the body; it is read from X-Tenant-Id header.
 */
public record QueryResourcesReq(
    @NotBlank String subjectTypeCode,
    @NotBlank String subjectExternalId,
    @NotBlank String resourceTypeCode,
    @NotBlank String operationCode,
    String domainCode,
    boolean canManageOnly
) {}
