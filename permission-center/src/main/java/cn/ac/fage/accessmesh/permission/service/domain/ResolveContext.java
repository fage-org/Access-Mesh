package cn.ac.fage.accessmesh.permission.service.domain;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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

    /** resourceTypeCode:operationCode → operationId 缓存 */
    private Map<String, Long> operationIdCache;

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
        this.operationIdCache = new HashMap<>();
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

}