package cn.ac.fage.accessmesh.permission.service.domain;

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
}
