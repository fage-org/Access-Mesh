package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BizDomainCreateReq(
    @NotNull Long tenantId,
    @NotBlank String code,
    @NotBlank String name,
    String description
) {}
