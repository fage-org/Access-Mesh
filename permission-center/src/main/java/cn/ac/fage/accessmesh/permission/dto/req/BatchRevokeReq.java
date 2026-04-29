package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Batch revoke permissions from a role.
 */
public record BatchRevokeReq(
    @NotNull Long roleId,
    @NotEmpty java.util.List<Long> permissionIds
) {}
