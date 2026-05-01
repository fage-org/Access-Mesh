package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * List extra basic roles attached to a group role (business keys).
 */
public record GroupRoleExtraRolesListReq(
    String domainCode,
    @NotBlank String groupRoleTypeCode,
    @NotBlank String groupRoleExternalId
) {}
