package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * List user-role relations by subject business keys (tenant from X-Tenant-Id).
 */
public record UserRoleListReq(
    @NotBlank String subjectTypeCode,
    @NotBlank String subjectExternalId
) {}
