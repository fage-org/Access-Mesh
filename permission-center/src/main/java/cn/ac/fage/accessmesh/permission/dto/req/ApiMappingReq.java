package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ApiMappingReq(
    @NotNull Long tenantId,
    @NotNull Long resourceEntityId,
    @NotBlank String serviceCode,
    @NotBlank String httpMethod,
    @NotBlank String pathPattern,
    Integer matchOrder,
    Boolean enabled,
    String extra
) {}
