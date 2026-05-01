package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

/**
 * Query scope resources within a primary resource context.
 * tenantId is NOT in the body; it is read from X-Tenant-Id header.
 */
public record QueryScopesReq(
    @NotBlank String subjectTypeCode,
    @NotBlank String subjectExternalId,
    @NotBlank String parentResourceTypeCode,
    @NotBlank String parentResourceCode,
    String parentCodeType,
    @NotEmpty List<String> parentOperationCodes,
    @NotEmpty List<String> scopeResourceTypeCodes,
    @NotEmpty List<String> scopeOperationCodes,
    String scopeCodeType,
    String domainCode,
    Map<String, Object> context
) {}
