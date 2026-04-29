package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DomainConfigReq(
    @NotNull Long bizDomainId,
    @NotBlank String configType,
    @NotBlank String extra
) {}
