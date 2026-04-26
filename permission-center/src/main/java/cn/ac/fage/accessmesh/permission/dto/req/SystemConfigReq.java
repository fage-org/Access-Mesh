package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SystemConfigReq(
    @NotNull Long tenantId,
    @NotBlank String configKey,
    @NotBlank String configValue,
    String description
) {}
