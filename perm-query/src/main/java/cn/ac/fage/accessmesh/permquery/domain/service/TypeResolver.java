package cn.ac.fage.accessmesh.permquery.domain.service;

import cn.ac.fage.accessmesh.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceResolveRequest;
import cn.ac.fage.accessmesh.permquery.domain.model.valueobject.PermissionQuery;
import cn.ac.fage.accessmesh.permquery.infrastructure.adapter.TypeResolutionAdapter;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 类型解析领域服务
 * <p>
 * 负责将业务编码转换为内部ID，包括资源类型、操作、资源实体等。
 * </p>
 */
@Service
public class TypeResolver {

    private final TypeResolutionAdapter typeResolutionAdapter;

    public TypeResolver(TypeResolutionAdapter typeResolutionAdapter) {
        this.typeResolutionAdapter = typeResolutionAdapter;
    }

    /**
     * 解析资源类型编码为类型值
     *
     * @param tenantId   租户ID
     * @param typeCodes  资源类型编码集合
     * @return 类型编码 → 类型值 映射
     */
    public Map<String, Integer> resolveResourceTypes(Long tenantId, Set<String> typeCodes) {
        if (tenantId == null || typeCodes == null || typeCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        return typeResolutionAdapter.batchResolveResourceTypes(tenantId, typeCodes);
    }

    /**
     * 从查询参数解析资源类型值集合
     *
     * @param query 权限查询参数
     * @return 资源类型值集合
     */
    public Set<Integer> resolveResourceTypes(PermissionQuery query) {
        if (query.resourceTypeCodes() == null || query.resourceTypeCodes().isEmpty()) {
            return Collections.emptySet();
        }
        Map<String, Integer> typeValues = resolveResourceTypes(query.tenantId(), query.resourceTypeCodes());
        return new HashSet<>(typeValues.values());
    }

    /**
     * 解析操作编码为操作ID
     *
     * @param tenantId        租户ID
     * @param resourceTypeCode 资源类型编码
     * @param operationCodes  操作编码集合
     * @return 操作编码 → 操作ID 映射
     */
    public Map<String, Long> resolveOperations(Long tenantId, String resourceTypeCode, Set<String> operationCodes) {
        if (tenantId == null || resourceTypeCode == null
            || operationCodes == null || operationCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        return typeResolutionAdapter.batchResolveOperations(tenantId, resourceTypeCode, operationCodes);
    }

    /**
     * 从查询参数解析操作ID集合
     * <p>
     * 需要遍历所有 resourceTypeCode，合并结果。
     * </p>
     *
     * @param query 权限查询参数
     * @return 操作ID集合
     */
    public Set<Long> resolveOperations(PermissionQuery query) {
        if (query.operationCodes() == null || query.operationCodes().isEmpty()) {
            return Collections.emptySet();
        }
        if (query.resourceTypeCodes() == null || query.resourceTypeCodes().isEmpty()) {
            return Collections.emptySet();
        }

        Set<Long> result = new HashSet<>();
        for (String rtCode : query.resourceTypeCodes()) {
            Map<String, Long> opIds = resolveOperations(query.tenantId(), rtCode, query.operationCodes());
            result.addAll(opIds.values());
        }
        return result;
    }

    /**
     * 解析资源编码为资源实体ID
     *
     * @param tenantId 租户ID
     * @param requests 资源解析请求列表
     * @return 解析键 → 资源实体ID 映射
     */
    public Map<ResourceResolveKey, Long> resolveResources(Long tenantId, List<ResourceResolveRequest> requests) {
        if (tenantId == null || requests == null || requests.isEmpty()) {
            return Collections.emptyMap();
        }
        return typeResolutionAdapter.batchResolveResources(tenantId, requests);
    }

    /**
     * 从查询参数解析资源实体ID集合
     *
     * @param query 权限查询参数
     * @return 资源实体ID集合
     */
    public Set<Long> resolveResources(PermissionQuery query) {
        // 如果已有实体ID，直接返回
        if (query.resourceEntityIds() != null && !query.resourceEntityIds().isEmpty()) {
            return new HashSet<>(query.resourceEntityIds());
        }

        // 如果没有资源编码，返回空集合
        if (query.resourceCodes() == null || query.resourceCodes().isEmpty()) {
            return Collections.emptySet();
        }
        if (query.resourceTypeCodes() == null || query.resourceTypeCodes().isEmpty()) {
            return Collections.emptySet();
        }

        // 批量解析：遍历所有 resourceTypeCode
        Set<Long> allResolved = new HashSet<>();
        for (String rtCode : query.resourceTypeCodes()) {
            List<ResourceResolveRequest> requests = query.resourceCodes().stream()
                .map(code -> new ResourceResolveRequest(rtCode, code, "CODE", null))
                .toList();

            Map<ResourceResolveKey, Long> resolved = resolveResources(query.tenantId(), requests);
            allResolved.addAll(resolved.values());
        }

        return allResolved;
    }

    /**
     * 解析域编码为域ID
     *
     * @param tenantId   租户ID
     * @param domainCodes 域编码集合
     * @return 域编码 → 域ID 映射
     */
    public Map<String, Long> resolveDomains(Long tenantId, Set<String> domainCodes) {
        if (tenantId == null || domainCodes == null || domainCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        return typeResolutionAdapter.batchResolveDomainIds(tenantId, domainCodes);
    }

    /**
     * 解析类型值为类型编码（反向解析）
     *
     * @param tenantId  租户ID
     * @param typeValues 类型值集合
     * @return 类型值 → 类型编码 映射
     */
    public Map<Integer, String> resolveResourceTypeCodes(Long tenantId, Set<Integer> typeValues) {
        if (tenantId == null || typeValues == null || typeValues.isEmpty()) {
            return Collections.emptyMap();
        }
        return typeResolutionAdapter.batchResolveResourceTypeCodes(tenantId, typeValues);
    }

    /**
     * 解析单个资源类型编码
     *
     * @param tenantId 租户ID
     * @param typeCode 资源类型编码
     * @return 类型值
     */
    public Integer resolveResourceType(Long tenantId, String typeCode) {
        if (tenantId == null || typeCode == null) {
            return null;
        }
        return typeResolutionAdapter.resolveResourceType(tenantId, typeCode);
    }
}