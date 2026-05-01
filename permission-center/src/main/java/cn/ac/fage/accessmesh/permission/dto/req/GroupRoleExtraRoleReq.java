package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * Add/remove an extra basic role for a group role (business keys, no internal role ids).
 */
public record GroupRoleExtraRoleReq(
    String groupDomainCode,
    @NotBlank String groupRoleTypeCode,
    @NotBlank String groupRoleExternalId,
    String basicDomainCode,
    @NotBlank String basicRoleTypeCode,
    @NotBlank String basicRoleExternalId
) {}
