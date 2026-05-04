package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.config.PermCacheProperties;
import cn.ac.fage.accessmesh.permission.service.domain.PermCacheDomainService;
import cn.ac.fage.accessmesh.permission.vo.InterfaceSnapshot;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 权限缓存领域服务实现
 * <p>
 * 问题10：统一使用 buildCacheKey 方法构建缓存键，确保命名空间隔离
 * </p>
 */
@Service
public class PermCacheDomainServiceImpl implements PermCacheDomainService {

    // 问题10：定义命名空间常量，统一管理
    private static final String NAMESPACE_EFFECTIVE_ROLES = "perm:user:effective-roles";
    private static final String NAMESPACE_ROLE_PERMS = "perm:role:perms";
    private static final String NAMESPACE_PERM_VERSION = "perm:permission-version:role";
    private static final String NAMESPACE_INTERFACE_SNAPSHOT = "perm:gateway:interface-snapshot";

    private final RedisTemplate<String, Object> redisTemplate;
    private final PermCacheProperties permCacheProperties;
    private Cache<String, Object> l1Cache;

    public PermCacheDomainServiceImpl(RedisTemplate<String, Object> redisTemplate,
                                       PermCacheProperties permCacheProperties) {
        this.redisTemplate = redisTemplate;
        this.permCacheProperties = permCacheProperties;
    }

    @PostConstruct
    public void init() {
        PermCacheProperties.L1Config l1 = permCacheProperties.getL1();
        l1Cache = Caffeine.newBuilder()
            .maximumSize(l1.getMaximumSize())
            .expireAfterWrite(l1.getExpireMinutes(), TimeUnit.MINUTES)
            .build();
    }

    // ==================== 缓存键构建方法 ====================

    /**
     * 问题10：统一的缓存键构建方法
     * 格式：namespace:tenantId:key
     */
    private String buildCacheKey(String namespace, Long tenantId, Object key) {
        return namespace + ":" + tenantId + ":" + key;
    }

    // ==================== 有效角色缓存 ====================

    @Override
    public Optional<Set<Long>> getEffectiveRoles(Long tenantId, Long userId) {
        String key = buildCacheKey(NAMESPACE_EFFECTIVE_ROLES, tenantId, userId);
        @SuppressWarnings("unchecked")
        Set<Long> cached = (Set<Long>) l1Cache.getIfPresent(key);
        return Optional.ofNullable(cached);
    }

    @Override
    public void setEffectiveRoles(Long tenantId, Long userId, Set<Long> roleIds) {
        String key = buildCacheKey(NAMESPACE_EFFECTIVE_ROLES, tenantId, userId);
        l1Cache.put(key, roleIds);
    }

    @Override
    public void evictEffectiveRoles(Long tenantId, Long userId) {
        String key = buildCacheKey(NAMESPACE_EFFECTIVE_ROLES, tenantId, userId);
        // 失效顺序：先 L2 后 L1
        redisTemplate.delete(key);
        l1Cache.invalidate(key);
    }

    // ==================== 角色权限快照缓存 ====================

    @Override
    public Optional<RolePermSnapshot> getRolePermSnapshot(Long tenantId, Long roleId) {
        String key = buildCacheKey(NAMESPACE_ROLE_PERMS, tenantId, roleId);
        @SuppressWarnings("unchecked")
        RolePermSnapshot cached = (RolePermSnapshot) l1Cache.getIfPresent(key);
        if (cached != null) return Optional.of(cached);
        Object l2Val = redisTemplate.opsForValue().get(key);
        if (l2Val instanceof RolePermSnapshot) {
            return Optional.of((RolePermSnapshot) l2Val);
        }
        return Optional.empty();
    }

    @Override
    public void setRolePermSnapshot(Long tenantId, Long roleId, RolePermSnapshot snapshot) {
        String key = buildCacheKey(NAMESPACE_ROLE_PERMS, tenantId, roleId);
        // 问题5：写入顺序先 L2 后 L1
        redisTemplate.opsForValue().set(key, snapshot, permCacheProperties.getL2().getTtlMinutes(), TimeUnit.MINUTES);
        l1Cache.put(key, snapshot);
    }

    @Override
    public void evictRolePermSnapshot(Long tenantId, Long roleId) {
        String key = buildCacheKey(NAMESPACE_ROLE_PERMS, tenantId, roleId);
        // 失效顺序：先 L2 后 L1
        redisTemplate.delete(key);
        l1Cache.invalidate(key);
    }

    // ==================== 权限版本缓存 ====================

    @Override
    public Optional<Long> getPermVersion(Long tenantId, Long roleId) {
        String key = buildCacheKey(NAMESPACE_PERM_VERSION, tenantId, roleId);
        @SuppressWarnings("unchecked")
        Long cached = (Long) l1Cache.getIfPresent(key);
        return Optional.ofNullable(cached);
    }

    @Override
    public void setPermVersion(Long tenantId, Long roleId, long version) {
        String key = buildCacheKey(NAMESPACE_PERM_VERSION, tenantId, roleId);
        l1Cache.put(key, version);
    }

    // ==================== 接口快照缓存 ====================

    @Override
    public Optional<InterfaceSnapshot> getInterfaceSnapshot(Long tenantId, String serviceCode) {
        String key = buildCacheKey(NAMESPACE_INTERFACE_SNAPSHOT, tenantId, serviceCode);
        @SuppressWarnings("unchecked")
        InterfaceSnapshot cached = (InterfaceSnapshot) l1Cache.getIfPresent(key);
        return Optional.ofNullable(cached);
    }

    @Override
    public void setInterfaceSnapshot(Long tenantId, String serviceCode, InterfaceSnapshot snapshot) {
        String key = buildCacheKey(NAMESPACE_INTERFACE_SNAPSHOT, tenantId, serviceCode);
        // 问题5：写入顺序先 L2 后 L1
        redisTemplate.opsForValue().set(key, snapshot, permCacheProperties.getL2().getTtlMinutes(), TimeUnit.MINUTES);
        l1Cache.put(key, snapshot);
    }

    @Override
    public void evictInterfaceSnapshot(Long tenantId, String serviceCode) {
        String key = buildCacheKey(NAMESPACE_INTERFACE_SNAPSHOT, tenantId, serviceCode);
        // 失效顺序：先 L2 后 L1
        redisTemplate.delete(key);
        l1Cache.invalidate(key);
    }
}