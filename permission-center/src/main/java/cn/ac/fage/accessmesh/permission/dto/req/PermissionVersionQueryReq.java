package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * Permission version query request.
 * tenantId is NOT in the body; it is read from X-Tenant-Id header.
 */
public record PermissionVersionQueryReq(
    @NotBlank String roleTypeCode,
    @NotBlank String roleExternalId,
    String domainCode
) {}
