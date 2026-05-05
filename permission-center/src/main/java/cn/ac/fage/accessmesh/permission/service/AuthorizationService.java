package cn.ac.fage.accessmesh.permission.service;

import java.util.Map;
import java.util.Set;

/**
 * Service for specialized authorization checks.
 *
 * <p>General permission checks should use {@link cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine} directly:
 * <pre>
 * engine.hasPermission(tenantId, operatorId, ResourceTypeCode.ROLE, roleId, OperationCodeConstants.MANAGE);
 * engine.validate(tenantId, operatorId, ResourceTypeCode.RESOURCE, resourceId, OperationCodeConstants.MANAGE);
 * </pre>
 *
 * <p>This service only provides canGrant permission checks for delegation validation.
 */
public interface AuthorizationService {

    /**
     * Check if operator can grant a specific permission to others.
     * Operator must:
     * 1. Have the same permission (resourceType + resource/scopeAll + operation)
     * 2. Have canGrant=true on that permission
     *
     * This is used in the permission granting flow to validate delegation.
     *
     * @param tenantId the tenant ID
     * @param operatorId the operator's user ID
     * @param resourceTypeCode the resource type code
     * @param resourceCode the resource code (null for scopeAll=true)
     * @param operationCode the operation code
     * @param scopeAll whether the permission is for all resources of this type
     * @param domainCode the domain code (optional, for resource resolution)
     * @return true if operator has the permission AND canGrant=true
     */
    boolean canGrantPermission(Long tenantId, Long operatorId, String resourceTypeCode,
                               String resourceCode, String operationCode, boolean scopeAll, String domainCode);

    /**
     * Batch check if operator can grant multiple permissions.
     * Returns detailed results for each permission key.
     *
     * @param tenantId the tenant ID
     * @param operatorId the operator's user ID
     * @param permissions the permissions to check (resourceTypeCode, resourceCode, operationCode, scopeAll)
     * @param domainCode the domain code (optional)
     * @return Map of permission key to GrantCheckResult (canGrant, reason if not)
     */
    Map<String, GrantCheckResult> checkGrantPermissionsBatch(Long tenantId, Long operatorId,
                                                              Set<GrantCheckKey> permissions, String domainCode);

    /**
     * Key for grant permission check.
     */
    record GrantCheckKey(
        String resourceTypeCode,
        String resourceCode,
        String operationCode,
        boolean scopeAll
    ) {}

    /**
     * Result of grant permission check.
     */
    record GrantCheckResult(
        boolean canGrant,
        String reason  // null if canGrant=true, otherwise explains why not
    ) {}
}