package cn.ac.fage.accessmesh.permquery.infrastructure.adapter;

import cn.ac.fage.accessmesh.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceResolveRequest;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 类型解析适配器
 * <p>
 * 封装 TypeResolutionService，提供类型解析操作。
 * 用于将业务编码转换为内部ID。
 * </p>
 */
@Component
public class TypeResolutionAdapter {

    private final TypeResolutionService typeResolutionService;

    public TypeResolutionAdapter(TypeResolutionService typeResolutionService) {
        this.typeResolutionService = typeResolutionService;
    }

    // ===== 资源类型解析 =====

    /**
     * 批量解析资源类型编码为类型值
     */
    public Map<String, Integer> batchResolveResourceTypes(Long tenantId, Set<String> typeCodes) {
        if (typeCodes == null || typeCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        return typeResolutionService.batchResolveTypeValues(tenantId, "resource_type", typeCodes);
    }

    /**
     * 解析单个资源类型编码
     */
    public Integer resolveResourceType(Long tenantId, String typeCode) {
        if (typeCode == null) {
            return null;
        }
        return typeResolutionService.resolveTypeValue(tenantId, "resource_type", typeCode);
    }

    /**
     * 批量解析资源类型值为编码
     */
    public Map<Integer, String> batchResolveResourceTypeCodes(Long tenantId, Set<Integer> typeValues) {
        if (typeValues == null || typeValues.isEmpty()) {
            return Collections.emptyMap();
        }
        return typeResolutionService.batchResolveTypeCodes(tenantId, "resource_type", typeValues);
    }

    // ===== 操作ID解析 =====

    /**
     * 批量解析操作编码为操作ID
     */
    public Map<String, Long> batchResolveOperations(Long tenantId, String resourceTypeCode,
                                                      Set<String> operationCodes) {
        if (operationCodes == null || operationCodes.isEmpty() || resourceTypeCode == null) {
            return Collections.emptyMap();
        }
        return typeResolutionService.batchResolveOperationIds(tenantId, resourceTypeCode, operationCodes);
    }

    /**
     * 解析单个操作编码为操作ID
     */
    public Long resolveOperationId(Long tenantId, String operationCode, String resourceTypeCode) {
        if (operationCode == null || resourceTypeCode == null) {
            return null;
        }
        return typeResolutionService.resolveOperationId(tenantId, operationCode, resourceTypeCode);
    }

    // ===== 资源实体ID解析 =====

    /**
     * 批量解析资源编码为资源实体ID
     */
    public Map<ResourceResolveKey, Long> batchResolveResources(Long tenantId,
                                                                 List<ResourceResolveRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return Collections.emptyMap();
        }
        return typeResolutionService.batchResolveResourceIds(tenantId, requests);
    }

    /**
     * 解析单个资源编码为资源实体ID
     */
    public Long resolveResourceId(Long tenantId, String resourceTypeCode, String resourceCode,
                                   String codeType, String domainCode) {
        if (resourceTypeCode == null || resourceCode == null) {
            return null;
        }
        return typeResolutionService.resolveResourceId(tenantId, resourceTypeCode, resourceCode,
            codeType, domainCode);
    }

    // ===== 域ID解析 =====

    /**
     * 批量解析域编码为域ID
     */
    public Map<String, Long> batchResolveDomainIds(Long tenantId, Set<String> domainCodes) {
        if (domainCodes == null || domainCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        return typeResolutionService.batchResolveDomainIds(tenantId, domainCodes);
    }

    /**
     * 解析单个域编码为域ID
     */
    public Long resolveDomainId(Long tenantId, String domainCode) {
        if (domainCode == null) {
            return null;
        }
        return typeResolutionService.resolveDomainId(tenantId, domainCode);
    }

    // ===== 用户ID解析 =====

    /**
     * 批量解析用户外部ID为用户ID
     */
    public Map<String, Long> batchResolveUserIds(Long tenantId, String subjectTypeCode,
                                                   Set<String> externalIds) {
        if (externalIds == null || externalIds.isEmpty() || subjectTypeCode == null) {
            return Collections.emptyMap();
        }
        return typeResolutionService.batchResolveUserIds(tenantId, subjectTypeCode, externalIds);
    }

    // ===== 角色ID解析 =====

    /**
     * 批量解析角色外部ID为角色ID
     */
    public Map<String, Long> batchResolveRoleIds(Long tenantId, String roleTypeCode,
                                                   Set<String> externalIds, String domainCode) {
        if (externalIds == null || externalIds.isEmpty() || roleTypeCode == null) {
            return Collections.emptyMap();
        }
        return typeResolutionService.batchResolveRoleIds(tenantId, roleTypeCode, externalIds, domainCode);
    }
}