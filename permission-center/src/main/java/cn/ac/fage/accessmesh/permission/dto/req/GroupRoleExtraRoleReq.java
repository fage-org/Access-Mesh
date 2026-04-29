package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotNull;

/**
 * Add/remove extra role for a group role.
 */
public record GroupRoleExtraRoleReq(
    @NotNull Long groupId,
    @NotNull Long basicRoleId
) {}
