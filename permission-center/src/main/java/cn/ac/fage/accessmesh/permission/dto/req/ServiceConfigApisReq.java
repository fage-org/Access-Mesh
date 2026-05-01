package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

public record ServiceConfigApisReq(
    @NotBlank String serviceCode
) {}
