package cn.ac.fage.accessmesh.permission.service.domain;

import cn.ac.fage.accessmesh.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceResolveRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 类型解析上下文
 * <p>
 * 统一管理类型解析的缓存，避免同一查询链路中的重复解析。
 * 支持批量预解析和单次获取，批量预解析优先使用批量查询方法。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * ResolveContext ctx = new ResolveContext(tenantId, typeResolutionService);
 * ctx.prepareResourceTypes(Set.of("USER", "ROLE", "RESOURCE"));
 * ctx.prepareOperations("USER", Set.of("VIEW", "MANAGE"));
 *
 * Integer userTypeValue = ctx.getResourceTypeValue("USER");
 * Long viewOpId = ctx.getOperationId("USER", "VIEW");
 * }</pre>
 */
public class ResolveContext {

    private final Long tenantId;
    private final TypeResolutionService typeResolutionService;

    /** resourceTypeCode → resourceTypeValue 缓存 */
    private Map<String, Integer> resourceTypeValueCache;

    /** typeKey:typeValue → typeCode 缓存 */
    private Map<String, String> resourceTypeCodeCache;

    /** domainCode → domainId 缓存 */
    private Map<String, Long> domainIdCache;

    /** resourceTypeCode:operationCode → operationId 缓存 */
    private Map<String, Long> operationIdCache;

    /** ResourceResolveKey → resourceId 缓存 */
    private Map<ResourceResolveKey, Long> resourceIdCache;

    /**
     * 构造解析上下文
     *
     * @param tenantId             租户ID
     * @param typeResolutionService 类型解析服务
     */
    public ResolveContext(Long tenantId, TypeResolutionService typeResolutionService) {
        this.tenantId = tenantId;
        this.typeResolutionService = typeResolutionService;
        this.resourceTypeValueCache = new HashMap<>();
        this.resourceTypeCodeCache = new HashMap<>();
        this.domainIdCache = new HashMap<>();
        this.operationIdCache = new HashMap<>();
        this.resourceIdCache = new HashMap<>();
    }

    // ===== 预解析方法（批量加载）=====

    /**
     * 批量预解析资源类型值
     * <p>
     * 使用 batchResolveTypeValues 方法一次性加载，避免 N 次单查询。
     * </p>
     *
     * @param typeCodes 资源类型编码集合
     */
    public void prepareResourceTypes(Set<String> typeCodes) {
        if (typeCodes == null || typeCodes.isEmpty()) {
            return;
        }
        // 过滤已缓存的
        Set<String> toResolve = new HashSet<>();
        for (String code : typeCodes) {
            if (!resourceTypeValueCache.containsKey(code)) {
                toResolve.add(code);
            }
        }
        if (toResolve.isEmpty()) {
            return;
        }
        Map<String, Integer> resolved = typeResolutionService.batchResolveTypeValues(
            tenantId, "resource_type", toResolve);
        resourceTypeValueCache.putAll(resolved);
    }

    /**
     * 批量预解析资源类型编码（值→码）
     *
     * @param typeValues 资源类型值集合
     */
    public void prepareResourceTypeCodes(Set<Integer> typeValues) {
        if (typeValues == null || typeValues.isEmpty()) {
            return;
        }
        Set<Integer> toResolve = new HashSet<>();
        for (Integer value : typeValues) {
            String cacheKey = "resource_type:" + value;
            if (!resourceTypeCodeCache.containsKey(cacheKey)) {
                toResolve.add(value);
            }
        }
        if (toResolve.isEmpty()) {
            return;
        }
        Map<Integer, String> resolved = typeResolutionService.batchResolveTypeCodes(
            tenantId, "resource_type", toResolve);
        for (Map.Entry<Integer, String> entry : resolved.entrySet()) {
            resourceTypeCodeCache.put("resource_type:" + entry.getKey(), entry.getValue());
        }
    }

    /**
     * 批量预解析域ID
     *
     * @param domainCodes 域编码集合
     */
    public void prepareDomainIds(Set<String> domainCodes) {
        if (domainCodes == null || domainCodes.isEmpty()) {
            return;
        }
        Set<String> toResolve = new HashSet<>();
        for (String code : domainCodes) {
            if (!domainIdCache.containsKey(code)) {
                toResolve.add(code);
            }
        }
        if (toResolve.isEmpty()) {
            return;
        }
        Map<String, Long> resolved = typeResolutionService.batchResolveDomainIds(tenantId, toResolve);
        domainIdCache.putAll(resolved);
    }

    /**
     * 批量预解析操作ID（单个资源类型）
     *
     * @param resourceTypeCode 资源类型编码
     * @param operationCodes   操作编码集合
     */
    public void prepareOperations(String resourceTypeCode, Set<String> operationCodes) {
        if (resourceTypeCode == null || operationCodes == null || operationCodes.isEmpty()) {
            return;
        }
        String cachePrefix = resourceTypeCode + ":";
        Set<String> toResolve = new HashSet<>();
        for (String opCode : operationCodes) {
            String cacheKey = cachePrefix + opCode;
            if (!operationIdCache.containsKey(cacheKey)) {
                toResolve.add(opCode);
            }
        }
        if (toResolve.isEmpty()) {
            return;
        }
        Map<String, Long> resolved = typeResolutionService.batchResolveOperationIds(
            tenantId, resourceTypeCode, toResolve);
        for (Map.Entry<String, Long> entry : resolved.entrySet()) {
            operationIdCache.put(cachePrefix + entry.getKey(), entry.getValue());
        }
    }

    /**
     * 批量预解析操作ID（多个资源类型）
     * <p>
     * 遍历所有资源类型，逐个批量解析操作ID。
     * </p>
     *
     * @param resourceTypeCodes 资源类型编码集合
     * @param operationCodes    操作编码集合
     */
    public void prepareOperations(Set<String> resourceTypeCodes, Set<String> operationCodes) {
        if (resourceTypeCodes == null || resourceTypeCodes.isEmpty()
            || operationCodes == null || operationCodes.isEmpty()) {
            return;
        }
        for (String rtCode : resourceTypeCodes) {
            prepareOperations(rtCode, operationCodes);
        }
    }

    /**
     * 批量预解析资源ID
     *
     * @param requests 资源解析请求列表
     */
    public void prepareResources(List<ResourceResolveRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        List<ResourceResolveRequest> toResolve = new ArrayList<>();
        for (ResourceResolveRequest req : requests) {
            ResourceResolveKey key = req.toKey();
            if (!resourceIdCache.containsKey(key)) {
                toResolve.add(req);
            }
        }
        if (toResolve.isEmpty()) {
            return;
        }
        Map<ResourceResolveKey, Long> resolved = typeResolutionService.batchResolveResourceIds(
            tenantId, toResolve);
        resourceIdCache.putAll(resolved);
    }

    // ===== 获取方法（从缓存取，无则单次解析）=====

    /**
     * 获取资源类型值
     *
     * @param typeCode 资源类型编码
     * @return 资源类型值，未找到返回 null
     */
    public Integer getResourceTypeValue(String typeCode) {
        if (typeCode == null) {
            return null;
        }
        if (resourceTypeValueCache.containsKey(typeCode)) {
            return resourceTypeValueCache.get(typeCode);
        }
        // 单次解析并缓存
        Integer value = typeResolutionService.resolveTypeValue(tenantId, "resource_type", typeCode);
        if (value != null) {
            resourceTypeValueCache.put(typeCode, value);
        }
        return value;
    }

    /**
     * 获取资源类型编码
     *
     * @param typeValue 资源类型值
     * @return 资源类型编码，未找到返回 null
     */
    public String getResourceTypeCode(Integer typeValue) {
        if (typeValue == null) {
            return null;
        }
        String cacheKey = "resource_type:" + typeValue;
        if (resourceTypeCodeCache.containsKey(cacheKey)) {
            return resourceTypeCodeCache.get(cacheKey);
        }
        String code = typeResolutionService.resolveTypeCode(tenantId, "resource_type", typeValue);
        if (code != null) {
            resourceTypeCodeCache.put(cacheKey, code);
        }
        return code;
    }

    /**
     * 获取域ID
     *
     * @param domainCode 域编码
     * @return 域ID，未找到返回 null
     */
    public Long getDomainId(String domainCode) {
        if (domainCode == null || domainCode.isBlank()) {
            return null;
        }
        if (domainIdCache.containsKey(domainCode)) {
            return domainIdCache.get(domainCode);
        }
        Long id = typeResolutionService.resolveDomainId(tenantId, domainCode);
        if (id != null) {
            domainIdCache.put(domainCode, id);
        }
        return id;
    }

    /**
     * 获取操作ID
     *
     * @param resourceTypeCode 资源类型编码
     * @param operationCode    操作编码
     * @return 操作ID，未找到返回 null
     */
    public Long getOperationId(String resourceTypeCode, String operationCode) {
        if (resourceTypeCode == null || operationCode == null) {
            return null;
        }
        String cacheKey = resourceTypeCode + ":" + operationCode;
        if (operationIdCache.containsKey(cacheKey)) {
            return operationIdCache.get(cacheKey);
        }
        Long id = typeResolutionService.resolveOperationId(tenantId, operationCode, resourceTypeCode);
        if (id != null) {
            operationIdCache.put(cacheKey, id);
        }
        return id;
    }

    /**
     * 获取资源ID
     *
     * @param resourceTypeCode 资源类型编码
     * @param resourceCode     资源编码
     * @param codeType         编码类型
     * @param domainCode       域编码（可选）
     * @return 资源ID，未找到返回 null
     */
    public Long getResourceId(String resourceTypeCode, String resourceCode,
                              String codeType, String domainCode) {
        if (resourceTypeCode == null || resourceCode == null) {
            return null;
        }
        String effectiveCodeType = (codeType != null && !codeType.isBlank()) ? codeType : "default";
        String effectiveDomainCode = (domainCode != null && !domainCode.isBlank()) ? domainCode : "";
        ResourceResolveKey key = new ResourceResolveKey(resourceTypeCode, resourceCode, effectiveCodeType, effectiveDomainCode);
        if (resourceIdCache.containsKey(key)) {
            return resourceIdCache.get(key);
        }
        Long id = typeResolutionService.resolveResourceId(
            tenantId, resourceTypeCode, resourceCode, codeType, domainCode);
        if (id != null) {
            resourceIdCache.put(key, id);
        }
        return id;
    }

    // ===== 批量获取方法 =====

    /**
     * 批量获取资源类型值
     *
     * @param typeCodes 资源类型编码集合
     * @return typeCode → typeValue 映射
     */
    public Map<String, Integer> getResourceTypeValues(Set<String> typeCodes) {
        if (typeCodes == null || typeCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        prepareResourceTypes(typeCodes);
        Map<String, Integer> result = new HashMap<>();
        for (String code : typeCodes) {
            Integer value = resourceTypeValueCache.get(code);
            if (value != null) {
                result.put(code, value);
            }
        }
        return result;
    }

    /**
     * 批量获取操作ID
     *
     * @param resourceTypeCode 资源类型编码
     * @param operationCodes   操作编码集合
     * @return operationCode → operationId 映射
     */
    public Map<String, Long> getOperationIds(String resourceTypeCode, Set<String> operationCodes) {
        if (resourceTypeCode == null || operationCodes == null || operationCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        prepareOperations(resourceTypeCode, operationCodes);
        Map<String, Long> result = new HashMap<>();
        String cachePrefix = resourceTypeCode + ":";
        for (String opCode : operationCodes) {
            Long id = operationIdCache.get(cachePrefix + opCode);
            if (id != null) {
                result.put(opCode, id);
            }
        }
        return result;
    }

    /**
     * 批量获取操作ID（多资源类型）
     *
     * @param resourceTypeCodes 资源类型编码集合
     * @param operationCodes    操作编码集合
     * @return operationId 集合（去重）
     */
    public Set<Long> getOperationIds(Set<String> resourceTypeCodes, Set<String> operationCodes) {
        if (resourceTypeCodes == null || resourceTypeCodes.isEmpty()
            || operationCodes == null || operationCodes.isEmpty()) {
            return Collections.emptySet();
        }
        prepareOperations(resourceTypeCodes, operationCodes);
        Set<Long> result = new HashSet<>();
        for (String rtCode : resourceTypeCodes) {
            String cachePrefix = rtCode + ":";
            for (String opCode : operationCodes) {
                Long id = operationIdCache.get(cachePrefix + opCode);
                if (id != null) {
                    result.add(id);
                }
            }
        }
        return result;
    }

    // ===== 辅助方法 =====

    /**
     * 获取租户ID
     *
     * @return 租户ID
     */
    public Long getTenantId() {
        return tenantId;
    }
}