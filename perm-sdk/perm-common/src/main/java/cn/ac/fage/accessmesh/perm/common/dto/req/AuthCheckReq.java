package cn.ac.fage.accessmesh.perm.common.dto.req;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Single permission check request.
 * Uses stable business keys (subjectTypeCode, subjectExternalId, resourceTypeCode, resourceCode, operationCode).
 * Tenant ID is passed via X-Tenant-Id header, not in the request body.
 */
public record AuthCheckReq(
    @NotBlank String subjectTypeCode,    // e.g., "ADMIN_USER"
    @NotBlank String subjectExternalId,  // userId.toString() or external user ID
    @NotBlank String resourceTypeCode,   // e.g., "ADMIN_USER", "ADMIN_ORG"
    String resourceCode,                 // null for type-level (CREATE), specific code for instance-level
    @NotBlank String operationCode,      // e.g., "CREATE", "UPDATE", "DELETE"
    String domainCode,                   // optional domain scope
    String codeType,                     // optional code type filter
    String inheritMode,                  // optional: "PARENT", "CHILDREN", "BOTH"
    Map<String, Object> context          // optional condition evaluation context
) {}