package cn.ac.fage.accessmesh.access.permission.service.domain;

import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveRequest;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 类型解析服务接口
 * <p>
 * 将外部稳定的业务键（typeCode、externalId、code）解析为内部数据库ID。
 * 用于权限校验和管理API，将契约层转换为数据层。
 * </p>
 */
public interface TypeResolutionService {

    /**
     * 解析type_code到内部type_value
     * <p>
     * 例如：typeKey='user_type', typeCode='USER' -> 1
     * </p>
     *
     * @param tenantId 租户ID
     * @param typeKey  类型键，如"user_type"、"role_type"、"resource_type"
     * @param typeCode 外部稳定编码，如"USER"、"MENU"、"BASIC_ROLE"
     * @return type_value整数值，未找到时返回null
     */
    Integer resolveTypeValue(Long tenantId, String typeKey, String typeCode);

    /**
     * 批量解析type_codes到type_values
     *
     * @param tenantId 租户ID
     * @param typeKey  类型键，如"user_type"、"role_type"、"resource_type"
     * @param codes    待解析的类型编码集合
     * @return typeCode到typeValue的映射，codes为空时返回空Map
     */
    Map<String, Integer> batchResolveTypeValues(Long tenantId, String typeKey, Set<String> codes);

    /**
     * 解析内部type_value回稳定的type_code
     *
     * @param tenantId  租户ID
     * @param typeKey   类型键
     * @param typeValue 类型值
     * @return 类型编码，未找到时返回null
     */
    String resolveTypeCode(Long tenantId, String typeKey, Integer typeValue);

    /**
     * 批量解析type_values回type_codes
     *
     * @param tenantId 租户ID
     * @param typeKey  类型键，如"user_type"、"role_type"、"resource_type"
     * @param values   待解析的类型值集合
     * @return typeValue到typeCode的映射，values为空时返回空Map
     */
    Map<Integer, String> batchResolveTypeCodes(Long tenantId, String typeKey, Set<Integer> values);

    /**
     * 解析subjectTypeCode + subjectExternalId -> abstract_user.id
     *
     * @param tenantId          租户ID
     * @param subjectTypeCode   映射到type_definition(type_key='user_type').type_code
     * @param subjectExternalId abstract_user.external_id
     * @return abstract_user.id，未找到时返回null
     */
    Long resolveUserId(Long tenantId, String subjectTypeCode, String subjectExternalId);

    /**
     * 解析resourceTypeCode + resourceCode -> resource_entity.id
     * <p>
     * access-service 内部鉴权和写入操作依赖该解析将业务键映射为内部 ID。
     * 调用方通过业务键定位实体时，access-service 内部自动调用此方法，
     * 调用方无需直接使用。
     * </p>
     *
     * @param tenantId         租户ID
     * @param resourceTypeCode 映射到type_definition(type_key='resource_type').type_code
     * @param resourceCode     resource_entity.code
     * @param codeType         resource_entity.code_type，null时默认为"default"
     * @param domainCode       biz_domain.code，null表示全局范围
     * @return resource_entity.id，未找到时返回null
     */
    Long resolveResourceId(Long tenantId, String resourceTypeCode, String resourceCode,
                           String codeType, String domainCode);

    /**
     * 解析operationCode + resourceTypeCode -> operation_permission.id
     *
     * @param tenantId         租户ID
     * @param operationCode    operation_permission.code
     * @param resourceTypeCode 资源类型编码，用于缩小搜索范围
     * @return operation_permission.id，未找到时返回null
     */
    Long resolveOperationId(Long tenantId, String operationCode, String resourceTypeCode);

    /**
     * 解析domainCode -> biz_domain.id
     *
     * @param tenantId   租户ID
     * @param domainCode biz_domain.code，null时返回null
     * @return biz_domain.id，未找到时返回null
     */
    Long resolveDomainId(Long tenantId, String domainCode);

    /**
     * 解析roleTypeCode + roleExternalId -> abstract_role.id
     *
     * @param tenantId       租户ID
     * @param roleTypeCode   映射到type_definition(type_key='role_type').type_code
     * @param roleExternalId abstract_role.external_id
     * @param domainCode     biz_domain.code，null表示全局范围
     * @return abstract_role.id，未找到时返回null
     */
    Long resolveRoleId(Long tenantId, String roleTypeCode, String roleExternalId, String domainCode);

    // ===== 批量解析方法（避免N+1查询）=====

    /**
     * 批量解析operationCodes -> operationIds（指定资源类型）
     *
     * @param tenantId         租户ID
     * @param resourceTypeCode 资源类型编码，用于缩小搜索范围
     * @param operationCodes   待解析的操作编码集合，null/空时返回空Map
     * @return operationCode到operationId的映射，输入为空时返回空Map
     */
    Map<String, Long> batchResolveOperationIds(Long tenantId, String resourceTypeCode, Set<String> operationCodes);

    /**
     * 批量解析资源业务键
     *
     * @param tenantId 租户ID
     * @param requests 资源解析请求列表，包含resourceTypeCode、resourceCode、codeType、domainCode
     * @return ResourceResolveKey到resourceId的映射，输入为空时返回空Map
     */
    Map<ResourceResolveKey, Long> batchResolveResourceIds(Long tenantId, List<ResourceResolveRequest> requests);

    /**
     * 批量解析用户externalIds -> userIds（指定用户类型）
     *
     * @param tenantId        租户ID
     * @param subjectTypeCode 用户类型编码
     * @param externalIds     待解析的外部ID集合，null/空时返回空Map
     * @return externalId到userId的映射，输入为空时返回空Map
     */
    Map<String, Long> batchResolveUserIds(Long tenantId, String subjectTypeCode, Set<String> externalIds);

    /**
     * 批量解析角色externalIds -> roleIds（指定角色类型和域）
     *
     * @param tenantId       租户ID
     * @param roleTypeCode   角色类型编码
     * @param externalIds    待解析的外部ID集合，null/空时返回空Map
     * @param domainCode     域编码，null表示全局范围
     * @return externalId到roleId的映射，输入为空时返回空Map
     */
    Map<String, Long> batchResolveRoleIds(Long tenantId, String roleTypeCode, Set<String> externalIds, String domainCode);

    }
