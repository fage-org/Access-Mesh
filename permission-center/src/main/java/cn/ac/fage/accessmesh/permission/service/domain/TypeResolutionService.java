package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceResolveRequest;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves external stable business keys (typeCode, externalId, code) to internal database IDs.
 * Used by auth check and management APIs to translate the contract layer to the data layer.
 */
public interface TypeResolutionService {

    /**
     * Resolve a type_code to its internal type_value for the given type_key.
     * For example: typeKey='user_type', typeCode='USER' -> 1
     *
     * @param tenantId  the tenant
     * @param typeKey   e.g. "user_type", "role_type", "resource_type"
     * @param typeCode  the external stable code, e.g. "USER", "MENU", "BASIC_ROLE"
     * @return the type_value integer, or null if not found
     */
    Integer resolveTypeValue(Long tenantId, String typeKey, String typeCode);

    /**
     * Batch resolve type_codes to type_values for the given type_key.
     *
     * @param tenantId the tenant
     * @param typeKey  e.g. "user_type", "role_type", "resource_type"
     * @param codes    set of type codes to resolve
     * @return map of typeCode -> typeValue, empty map if codes is empty
     */
    Map<String, Integer> batchResolveTypeValues(Long tenantId, String typeKey, Set<String> codes);

    /**
     * Resolve an internal type_value back to stable type_code.
     */
    String resolveTypeCode(Long tenantId, String typeKey, Integer typeValue);

    /**
     * Batch resolve type_values back to type_codes for the given type_key.
     *
     * @param tenantId the tenant
     * @param typeKey  e.g. "user_type", "role_type", "resource_type"
     * @param values   set of type values to resolve
     * @return map of typeValue -> typeCode, empty map if values is empty
     */
    Map<Integer, String> batchResolveTypeCodes(Long tenantId, String typeKey, Set<Integer> values);

    /**
     * Resolve subjectTypeCode + subjectExternalId -> abstract_user.id.
     *
     * @param tenantId          the tenant
     * @param subjectTypeCode   maps to type_definition(type_key='user_type').type_code
     * @param subjectExternalId abstract_user.external_id
     * @return abstract_user.id, or null if not found
     */
    Long resolveUserId(Long tenantId, String subjectTypeCode, String subjectExternalId);

    /**
     * Resolve resourceTypeCode + resourceCode -> resource_entity.id.
     *
     * @param tenantId         the tenant
     * @param resourceTypeCode maps to type_definition(type_key='resource_type').type_code
     * @param resourceCode     resource_entity.code
     * @param codeType         resource_entity.code_type, defaults to "default" when null
     * @param domainCode       biz_domain.code, null means global scope
     * @return resource_entity.id, or null if not found
     */
    Long resolveResourceId(Long tenantId, String resourceTypeCode, String resourceCode,
                           String codeType, String domainCode);

    /**
     * Resolve operationCode + resourceTypeCode -> operation_permission.id.
     *
     * @param tenantId         the tenant
     * @param operationCode    operation_permission.code
     * @param resourceTypeCode the resource type to narrow the search scope
     * @return operation_permission.id, or null if not found
     */
    Long resolveOperationId(Long tenantId, String operationCode, String resourceTypeCode);

    /**
     * Resolve domainCode -> biz_domain.id.
     *
     * @param tenantId   the tenant
     * @param domainCode biz_domain.code, null returns null
     * @return biz_domain.id, or null
     */
    Long resolveDomainId(Long tenantId, String domainCode);

    /**
     * Resolve roleTypeCode + roleExternalId -> abstract_role.id.
     *
     * @param tenantId       the tenant
     * @param roleTypeCode   maps to type_definition(type_key='role_type').type_code
     * @param roleExternalId abstract_role.external_id
     * @param domainCode     biz_domain.code, null means global
     * @return abstract_role.id, or null if not found
     */
    Long resolveRoleId(Long tenantId, String roleTypeCode, String roleExternalId, String domainCode);

    // ===== Batch resolution methods (avoid N+1 queries) =====

    /**
     * Batch resolve domainCodes -> domainIds.
     *
     * @param tenantId    the tenant
     * @param domainCodes set of domain codes to resolve, null/empty returns empty map
     * @return map of domainCode -> domainId, empty map if input is empty
     */
    Map<String, Long> batchResolveDomainIds(Long tenantId, Set<String> domainCodes);

    /**
     * Batch resolve operationCodes -> operationIds for a specific resource type.
     *
     * @param tenantId         the tenant
     * @param resourceTypeCode the resource type code to narrow the search scope
     * @param operationCodes   set of operation codes to resolve, null/empty returns empty map
     * @return map of operationCode -> operationId, empty map if input is empty
     */
    Map<String, Long> batchResolveOperationIds(Long tenantId, String resourceTypeCode, Set<String> operationCodes);

    /**
     * Batch resolve resources by their business keys.
     *
     * @param tenantId the tenant
     * @param requests list of ResourceResolveRequest containing resourceTypeCode, resourceCode, codeType, domainCode
     * @return map of ResourceResolveKey -> resourceId, empty map if input is empty
     */
    Map<ResourceResolveKey, Long> batchResolveResourceIds(Long tenantId, List<ResourceResolveRequest> requests);

    /**
     * Batch resolve user externalIds -> userIds for a specific subject type.
     *
     * @param tenantId        the tenant
     * @param subjectTypeCode the user type code
     * @param externalIds     set of external IDs to resolve, null/empty returns empty map
     * @return map of externalId -> userId, empty map if input is empty
     */
    Map<String, Long> batchResolveUserIds(Long tenantId, String subjectTypeCode, Set<String> externalIds);

    /**
     * Batch resolve role externalIds -> roleIds for a specific role type and domain.
     *
     * @param tenantId       the tenant
     * @param roleTypeCode   the role type code
     * @param externalIds    set of external IDs to resolve, null/empty returns empty map
     * @param domainCode     the domain code, null means global
     * @return map of externalId -> roleId, empty map if input is empty
     */
    Map<String, Long> batchResolveRoleIds(Long tenantId, String roleTypeCode, Set<String> externalIds, String domainCode);

    /**
     * Check if a type definition is system-preset (cannot be deleted).
     * Moved from TypeDefPermissionStrategy.isSystemType().
     */
    boolean isSystemType(Long tenantId, Long typeDefId);
}
