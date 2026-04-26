package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Shared: batch revoke permissions from a role.
 */
public record BatchRevokeReq(
    @NotNull Long tenantId,
    @NotNull Long roleId,
    @NotEmpty List<Long> permissionIds
) {}
