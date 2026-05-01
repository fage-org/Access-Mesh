package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Batch revoke permissions from a role.
 */
public record BatchRevokeReq(
    String domainCode,
    @NotBlank String roleTypeCode,
    @NotBlank String roleExternalId,
    List<Long> permissionIds
) {}
