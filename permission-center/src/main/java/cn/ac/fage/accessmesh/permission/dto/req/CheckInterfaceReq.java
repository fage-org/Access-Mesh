package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Gateway callback: check permission by serviceCode + httpMethod + path.
 * tenantId is NOT in the body; it is read from X-Tenant-Id header.
 */
public record CheckInterfaceReq(
    @NotBlank String subjectTypeCode,
    @NotBlank String subjectExternalId,
    @NotBlank String serviceCode,
    @NotBlank String httpMethod,
    @NotBlank String path,
    Map<String, Object> context
) {}
