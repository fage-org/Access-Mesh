package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotBlank;

public record UserRoleListReq(
    @NotBlank String subjectTypeCode,
    @NotBlank String subjectExternalId
) {}
