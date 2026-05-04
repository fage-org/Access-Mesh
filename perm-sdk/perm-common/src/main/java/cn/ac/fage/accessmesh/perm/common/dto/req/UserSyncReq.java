package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * Shared: sync a user from admin-service to permission-center abstract_user.
 */
public record UserSyncReq(
    @NotBlank String subjectTypeCode,
    @NotBlank String externalId,
    String name,
    Boolean enabled,
    String extra,
    @NotBlank String version
) {}