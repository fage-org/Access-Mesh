package cn.ac.fage.accessmesh.permission.service.domain;

/**
 * Strategy for resource-type-specific ID conversion in permission checks.
 * Only register when resourceId is NOT directly resource_entity.id.
 *
 * <p>Default behavior (no strategy registered):
 * <ul>
 *   <li>resourceId is treated as resource_entity.id directly (for Long/Number types)</li>
 *   <li>String resourceId is treated as resource_entity.code</li>
 * </ul>
 *
 * <p>When to register a strategy:
 * <ul>
 *   <li>SERVICE: resourceId is serviceCode (String), need to lookup resource_entity by code</li>
 *   <li>DOMAIN: resourceId is bizDomainId (Long), need bizDomain.code → resource_entity.code</li>
 *   <li>TYPE_DEFINITION: resourceId is typeDefId (Long), need typeDef.typeCode → resource_entity.code</li>
 * </ul>
 *
 * <p>Business rules (like isSystem cannot be deleted) should be handled in business layer, NOT here.
 *
 * @param <ID> the resource identifier type (Long, String, etc.)
 */
public interface ResourcePermissionStrategy<ID> {

    /**
     * Return the resource type code this strategy handles.
     * Example: "SERVICE", "DOMAIN", "TYPE_DEFINITION"
     */
    String getResourceTypeCode();

    /**
     * Convert business identifier to resource_entity.code.
     * The validator will then lookup resource_entity.id by this code.
     *
     * @param tenantId the tenant ID
     * @param businessId the business identifier (serviceCode, bizDomainId, typeDefId, etc.)
     * @return resource_entity.code for lookup, or null if not found
     */
    String toResourceEntityCode(Long tenantId, ID businessId);
}