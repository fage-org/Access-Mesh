package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BizDomainCreateReq(
    @NotBlank String code,
    @NotBlank String name,
    String description
) {}
