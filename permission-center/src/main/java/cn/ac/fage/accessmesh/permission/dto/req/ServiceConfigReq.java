package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ServiceConfigReq(
    @NotBlank String serviceCode,
    @NotBlank String name,
    String basePath,
    String description,
    Integer status,
    String extra
) {}
