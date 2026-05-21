package cn.ac.fage.accessmesh.permquery.infrastructure.adapter;

import cn.ac.fage.accessmesh.common.cache.CacheCatalogEntry;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.permission.cache.PermCacheCatalog;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 权限缓存适配器
 * <p>
 * 封装 CacheService，提供值对象友好的缓存操作。
 * 统一管理权限查询相关的缓存访问。
 * </p>
 */
@Component
public class PermCacheAdapter {

    private final CacheService cacheService;

    public PermCacheAdapter(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    // ===== 用户有效角色缓存 =====

    /**
     * 获取用户有效角色缓存
     */
    public Set<Long> getEffectiveRoles(Long tenantId, Long userId) {
        return cacheService.get(PermCacheCatalog.EFFECTIVE_ROLES, tenantId, userId);
    }

    /**
     * 批量获取用户有效角色缓存
     */
    public Map<Long, Set<Long>> getEffectiveRolesBatch(Long tenantId, Set<Long> userIds) {
        return cacheService.getBatch(PermCacheCatalog.EFFECTIVE_ROLES, tenantId, userIds);
    }

    /**
     * 写入用户有效角色缓存
     */
    public void putEffectiveRoles(Long tenantId, Long userId, Set<Long> roleIds) {
        if (roleIds != null && !roleIds.isEmpty()) {
            cacheService.put(PermCacheCatalog.EFFECTIVE_ROLES, tenantId, userId, roleIds);
        }
    }

    /**
     * 批量写入用户有效角色缓存
     */
    public void putEffectiveRolesBatch(Long tenantId, Map<Long, Set<Long>> data) {
        if (data != null && !data.isEmpty()) {
            cacheService.putBatch(PermCacheCatalog.EFFECTIVE_ROLES, tenantId, data);
        }
    }

    /**
     * 失效用户有效角色缓存（事务提交后）
     */
    public void evictEffectiveRoles(Long tenantId, Long userId) {
        cacheService.evictAfterCommit(PermCacheCatalog.EFFECTIVE_ROLES, tenantId, userId);
    }

    /**
     * 批量失效用户有效角色缓存（事务提交后）
     */
    public void evictEffectiveRolesBatch(Long tenantId, Set<Long> userIds) {
        cacheService.evictBatchAfterCommit(PermCacheCatalog.EFFECTIVE_ROLES, tenantId, userIds);
    }

    // ===== 操作权限缓存 =====

    /**
     * 获取操作权限缓存（按资源类型）
     */
    public Map<Long, OperationPermission> getOperationPermissionsByType(Long tenantId, Integer resourceType) {
        String cacheKey = "op_perm:" + resourceType;
        return cacheService.get(PermCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE, tenantId, cacheKey);
    }

    /**
     * 写入操作权限缓存（按资源类型）
     */
    public void putOperationPermissionsByType(Long tenantId, Integer resourceType,
                                                Map<Long, OperationPermission> opMap) {
        if (opMap != null && !opMap.isEmpty()) {
            String cacheKey = "op_perm:" + resourceType;
            cacheService.put(PermCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE, tenantId, cacheKey, opMap);
        }
    }

    /**
     * 失效操作权限缓存
     */
    public void evictOperationPermissionsByType(Long tenantId, Integer resourceType) {
        String cacheKey = "op_perm:" + resourceType;
        cacheService.evictAfterCommit(PermCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE, tenantId, cacheKey);
    }

    // ===== 条件规则缓存 =====

    /**
     * 获取条件规则缓存
     */
    public JsonNode getConditionRule(Long tenantId, Long conditionId) {
        return cacheService.get(PermCacheCatalog.CONDITION_RULES, tenantId, conditionId);
    }

    /**
     * 批量获取条件规则缓存
     */
    public Map<Long, JsonNode> getConditionRulesBatch(Long tenantId, Set<Long> conditionIds) {
        return cacheService.getBatch(PermCacheCatalog.CONDITION_RULES, tenantId, conditionIds);
    }

    /**
     * 写入条件规则缓存
     */
    public void putConditionRule(Long tenantId, Long conditionId, JsonNode rule) {
        if (rule != null) {
            cacheService.put(PermCacheCatalog.CONDITION_RULES, tenantId, conditionId, rule);
        }
    }

    /**
     * 批量写入条件规则缓存
     */
    public void putConditionRulesBatch(Long tenantId, Map<Long, JsonNode> data) {
        if (data != null && !data.isEmpty()) {
            cacheService.putBatch(PermCacheCatalog.CONDITION_RULES, tenantId, data);
        }
    }

    // ===== 类型值缓存 =====

    /**
     * 获取类型值缓存
     */
    public Map<String, Integer> getTypeValueCache(Long tenantId, String typeKey) {
        return cacheService.get(PermCacheCatalog.TYPE_VALUE, tenantId, typeKey);
    }

    /**
     * 写入类型值缓存
     */
    public void putTypeValueCache(Long tenantId, String typeKey, Map<String, Integer> typeValues) {
        if (typeValues != null && !typeValues.isEmpty()) {
            cacheService.put(PermCacheCatalog.TYPE_VALUE, tenantId, typeKey, typeValues);
        }
    }

    // ===== 通用方法 =====

    /**
     * 通用缓存获取
     */
    public <V> V get(CacheCatalogEntry<V> catalog, Long tenantId, Object key) {
        return cacheService.get(catalog, tenantId, key);
    }

    /**
     * 通用缓存批量获取
     */
    public <K, V> Map<K, V> getBatch(CacheCatalogEntry<V> catalog, Long tenantId, Set<K> keys) {
        return cacheService.getBatch(catalog, tenantId, keys);
    }

    /**
     * 通用缓存写入
     */
    public <V> void put(CacheCatalogEntry<V> catalog, Long tenantId, Object key, V value) {
        cacheService.put(catalog, tenantId, key, value);
    }

    /**
     * 通用缓存失效（事务提交后）
     */
    public <V> void evictAfterCommit(CacheCatalogEntry<V> catalog, Long tenantId, Object key) {
        cacheService.evictAfterCommit(catalog, tenantId, key);
    }
}