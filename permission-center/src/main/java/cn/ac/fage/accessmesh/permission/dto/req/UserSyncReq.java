package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserSyncReq(
    @NotNull Long tenantId,
    @NotNull Integer userType,
    @NotBlank String externalId,
    String name,
    Boolean enabled,
    String extra,
    @NotBlank String version
) {}
