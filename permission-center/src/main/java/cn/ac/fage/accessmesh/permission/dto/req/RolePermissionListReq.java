package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

public record RolePermissionListReq(
    String domainCode,
    @NotBlank String roleTypeCode,
    @NotBlank String roleExternalId
) {}
