package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Gateway callback: check permission by serviceCode + httpMethod + path.
 * tenantId is NOT in the body; it is read from X-Tenant-Id header.
 */
public record CheckInterfaceReq(
    @NotNull Long userId,
    @NotBlank String serviceCode,
    @NotBlank String httpMethod,
    @NotBlank String path,
    Map<String, Object> context
) {}
