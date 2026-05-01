package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;

public record DomainConfigReq(
    @NotBlank String domainCode,
    @NotBlank String configType,
    @NotBlank String extra
) {}
