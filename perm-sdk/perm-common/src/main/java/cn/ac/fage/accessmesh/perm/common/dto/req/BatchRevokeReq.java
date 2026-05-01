package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Shared: batch revoke permissions from a role.
 */
public record BatchRevokeReq(
    String domainCode,
    @NotBlank String roleTypeCode,
    @NotBlank String roleExternalId,
    @NotEmpty List<Long> permissionIds
) {}
