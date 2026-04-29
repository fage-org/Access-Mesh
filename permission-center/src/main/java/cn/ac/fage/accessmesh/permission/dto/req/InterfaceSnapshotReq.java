package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * Interface snapshot request for Gateway \u2014 returns all allowed API entries for a subject.
 * tenantId is NOT in the body; it is read from X-Tenant-Id header.
 */
public record InterfaceSnapshotReq(
    @NotBlank String subjectTypeCode,
    @NotBlank String subjectExternalId,
    @NotBlank String serviceCode,
    Long permissionVersion
) {}
