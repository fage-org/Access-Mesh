package cn.ac.fage.accessmesh.permission.dto.req;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

/**
 * Query resources accessible to a subject for a given resource type and operation.
 * tenantId is NOT in the body; it is read from X-Tenant-Id header.
 */
public record QueryResourcesReq(
    @NotBlank String subjectTypeCode,
    @NotBlank String subjectExternalId,
    @NotEmpty List<String> resourceTypeCodes,
    @NotEmpty List<String> operationCodes,
    String domainCode,
    String codeType,
    Boolean includeInherited,
    Boolean includeChildren,
    Boolean treeMode,
    Map<String, Object> context
) {}
