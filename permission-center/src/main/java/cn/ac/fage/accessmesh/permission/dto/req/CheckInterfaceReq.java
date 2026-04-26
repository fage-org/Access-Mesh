package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Gateway callback: check permission by serviceCode + httpMethod + path.
 */
public record CheckInterfaceReq(
    @NotNull Long tenantId,
    @NotNull Long userId,
    @NotBlank String serviceCode,
    @NotBlank String httpMethod,
    @NotBlank String path,
    String clientIp
) {}
