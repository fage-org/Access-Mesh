package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Add API mapping to a resource — combines path params with mapping fields.
 */
public record ApiMappingAddReq(
    @NotNull Long tenantId,
    @NotNull Long resourceId,
    @NotBlank String serviceCode,
    @NotBlank String httpMethod,
    @NotBlank String pathPattern,
    Integer matchOrder,
    Boolean enabled,
    String extra
) {}
