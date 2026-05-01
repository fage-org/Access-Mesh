package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

public record UserEffectiveRolesReq(
    @NotBlank String subjectTypeCode,
    @NotBlank String subjectExternalId,
    String domainCode
) {}
