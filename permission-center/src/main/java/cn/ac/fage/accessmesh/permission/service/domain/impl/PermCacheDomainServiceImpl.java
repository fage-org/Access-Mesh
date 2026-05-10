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
 * 实现双层缓存架构：L1 Caffeine本地缓存 + L2 Redis分布式缓存。
 * 统一使用buildCacheKey方法构建缓存键，确保命名空间隔离。
 * </p>
 */
@Service
public class PermCacheDomainServiceImpl implements PermCacheDomainService {

    // 命名空间常量，统一管理缓存键命名
    private static final String NAMESPACE_EFFECTIVE_ROLES = "perm:user:effective-roles";
    private static final String NAMESPACE_ROLE_PERMS = "perm:role:perms";
    private static final String NAMESPACE_PERM_VERSION = "perm:permission-version:role";
    private static final String NAMESPACE_INTERFACE_SNAPSHOT = "perm:gateway:interface-snapshot";

    private final RedisTemplate<String, Object> redisTemplate;
    private final PermCacheProperties permCacheProperties;
    private Cache<String, Object> l1Cache;

    /**
     * 构造函数注入依赖
     *
     * @param redisTemplate      Redis操作模板
     * @param permCacheProperties 缓存配置属性
     */
    public PermCacheDomainServiceImpl(RedisTemplate<String, Object> redisTemplate,
                                       PermCacheProperties permCacheProperties) {
        this.redisTemplate = redisTemplate;
        this.permCacheProperties = permCacheProperties;
    }

    /**
     * 初始化L1 Caffeine缓存
     * <p>
     * 在Bean构造后根据配置属性初始化本地缓存
     * </p>
     */
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
     * 统一的缓存键构建方法
     * <p>
     * 格式：namespace:tenantId:key
     * </p>
     *
     * @param namespace 命名空间
     * @param tenantId  租户ID
     * @param key       业务键
     * @return 完整的缓存键
     */
    private String buildCacheKey(String namespace, Long tenantId, Object key) {
        return namespace + ":" + tenantId + ":" + key;
    }

    // ==================== 有效角色缓存 ====================

    /**
     * 获取用户的有效角色集合
     * <p>
     * 仅从L1 Caffeine缓存读取，L2 Redis缓存由UserRoleDomainService管理
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @return 有效角色ID集合
     */
    @Override
    public Optional<Set<Long>> getEffectiveRoles(Long tenantId, Long userId) {
        String key = buildCacheKey(NAMESPACE_EFFECTIVE_ROLES, tenantId, userId);
        @SuppressWarnings("unchecked")
        Set<Long> cached = (Set<Long>) l1Cache.getIfPresent(key);
        return Optional.ofNullable(cached);
    }

    /**
     * 设置用户的有效角色集合
     * <p>
     * 仅写入L1 Caffeine缓存
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @param roleIds  有效角色ID集合
     */
    @Override
    public void setEffectiveRoles(Long tenantId, Long userId, Set<Long> roleIds) {
        String key = buildCacheKey(NAMESPACE_EFFECTIVE_ROLES, tenantId, userId);
        l1Cache.put(key, roleIds);
    }

    /**
     * 失效用户的有效角色缓存
     * <p>
     * 同时失效L1和L2缓存，顺序：先L2后L1
     * </p>
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     */
    @Override
    public void evictEffectiveRoles(Long tenantId, Long userId) {
        String key = buildCacheKey(NAMESPACE_EFFECTIVE_ROLES, tenantId, userId);
        // 失效顺序：先L2后L1
        redisTemplate.delete(key);
        l1Cache.invalidate(key);
    }

    // ==================== 角色权限快照缓存 ====================

    /**
     * 获取角色权限快照
     * <p>
     * 先查L1，未命中再查L2
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @return 角色权限快照
     */
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

    /**
     * 设置角色权限快照
     * <p>
     * 同时写入L1和L2缓存，顺序：先L2后L1
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @param snapshot 权限快照
     */
    @Override
    public void setRolePermSnapshot(Long tenantId, Long roleId, RolePermSnapshot snapshot) {
        String key = buildCacheKey(NAMESPACE_ROLE_PERMS, tenantId, roleId);
        // 写入顺序：先L2后L1
        redisTemplate.opsForValue().set(key, snapshot, permCacheProperties.getL2().getTtlMinutes(), TimeUnit.MINUTES);
        l1Cache.put(key, snapshot);
    }

    /**
     * 失效角色权限快照缓存
     * <p>
     * 同时失效L1和L2缓存，顺序：先L2后L1
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     */
    @Override
    public void evictRolePermSnapshot(Long tenantId, Long roleId) {
        String key = buildCacheKey(NAMESPACE_ROLE_PERMS, tenantId, roleId);
        // 失效顺序：先L2后L1
        redisTemplate.delete(key);
        l1Cache.invalidate(key);
    }

    // ==================== 权限版本缓存 ====================

    /**
     * 获取权限版本号
     * <p>
     * 仅从L1缓存读取
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @return 权限版本号
     */
    @Override
    public Optional<Long> getPermVersion(Long tenantId, Long roleId) {
        String key = buildCacheKey(NAMESPACE_PERM_VERSION, tenantId, roleId);
        @SuppressWarnings("unchecked")
        Long cached = (Long) l1Cache.getIfPresent(key);
        return Optional.ofNullable(cached);
    }

    /**
     * 设置权限版本号
     * <p>
     * 仅写入L1缓存
     * </p>
     *
     * @param tenantId 租户ID
     * @param roleId   角色ID
     * @param version  版本号
     */
    @Override
    public void setPermVersion(Long tenantId, Long roleId, long version) {
        String key = buildCacheKey(NAMESPACE_PERM_VERSION, tenantId, roleId);
        l1Cache.put(key, version);
    }

    // ==================== 接口快照缓存 ====================

    /**
     * 获取接口权限快照
     * <p>
     * 仅从L1缓存读取
     * </p>
     *
     * @param tenantId   租户ID
     * @param serviceCode 服务编码
     * @return 接口快照
     */
    @Override
    public Optional<InterfaceSnapshot> getInterfaceSnapshot(Long tenantId, String serviceCode) {
        String key = buildCacheKey(NAMESPACE_INTERFACE_SNAPSHOT, tenantId, serviceCode);
        @SuppressWarnings("unchecked")
        InterfaceSnapshot cached = (InterfaceSnapshot) l1Cache.getIfPresent(key);
        return Optional.ofNullable(cached);
    }

    /**
     * 设置接口权限快照
     * <p>
     * 同时写入L1和L2缓存，顺序：先L2后L1
     * </p>
     *
     * @param tenantId   租户ID
     * @param serviceCode 服务编码
     * @param snapshot   接口快照
     */
    @Override
    public void setInterfaceSnapshot(Long tenantId, String serviceCode, InterfaceSnapshot snapshot) {
        String key = buildCacheKey(NAMESPACE_INTERFACE_SNAPSHOT, tenantId, serviceCode);
        // 写入顺序：先L2后L1
        redisTemplate.opsForValue().set(key, snapshot, permCacheProperties.getL2().getTtlMinutes(), TimeUnit.MINUTES);
        l1Cache.put(key, snapshot);
    }

    /**
     * 失效接口权限快照缓存
     * <p>
     * 同时失效L1和L2缓存，顺序：先L2后L1
     * </p>
     *
     * @param tenantId   租户ID
     * @param serviceCode 服务编码
     */
    @Override
    public void evictInterfaceSnapshot(Long tenantId, String serviceCode) {
        String key = buildCacheKey(NAMESPACE_INTERFACE_SNAPSHOT, tenantId, serviceCode);
        // 失效顺序：先L2后L1
        redisTemplate.delete(key);
        l1Cache.invalidate(key);
    }
}