package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

public record UserCreateReq(
    @NotBlank String subjectTypeCode,
    @NotBlank String externalId,
    String name,
    Boolean enabled,
    String extra
) {}
