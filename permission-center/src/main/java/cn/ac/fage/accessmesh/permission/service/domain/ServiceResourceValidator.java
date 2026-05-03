package cn.ac.fage.accessmesh.permission.service.domain;

import java.util.Set;

/**
 * Validator for service-level permission checks.
 * Used to verify operator's permission on SERVICE resources before modifying API mappings.
 */
public interface ServiceResourceValidator {

    /**
     * Validate that the operator has permission to manage API mappings for the specified service.
     *
     * @param tenantId     the tenant ID
     * @param operatorId   the operator's user ID (from X-User-Id header)
     * @param serviceCode  the service code (e.g., "admin-service", "example-service")
     * @throws IllegalArgumentException if the service resource is not found
     * @throws SecurityException if the operator lacks MANAGE_API_MAPPING permission
     */
    void validateApiMappingPermission(Long tenantId, Long operatorId, String serviceCode);

    /**
     * Validate that the operator has permission to sync interfaces for the specified service.
     *
     * @param tenantId     the tenant ID
     * @param operatorId   the operator's user ID (from X-User-Id header)
     * @param serviceCode  the service code
     * @throws IllegalArgumentException if the service resource is not found
     * @throws SecurityException if the operator lacks SYNC_INTERFACE permission
     */
    void validateInterfaceSyncPermission(Long tenantId, Long operatorId, String serviceCode);

    /**
     * Batch validate that the operator has permission to manage API mappings for the specified services.
     * This method optimizes database queries by batching all lookups.
     *
     * @param tenantId      the tenant ID
     * @param operatorId    the operator's user ID (from X-User-Id header)
     * @param serviceCodes  the set of service codes to validate
     * @throws IllegalArgumentException if any service resource is not found
     * @throws SecurityException if the operator lacks MANAGE_API_MAPPING permission on any service
     */
    void validateApiMappingPermissionBatch(Long tenantId, Long operatorId, Set<String> serviceCodes);
}
