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

@Service
public class PermCacheDomainServiceImpl implements PermCacheDomainService {

    private static final String EFFECTIVE_ROLES_KEY = "perm:user:effective-roles:";
    private static final String ROLE_PERMS_KEY = "perm:role:perms:";
    private static final String PERM_VERSION_KEY = "perm:permission-version:role:";
    private static final String INTERFACE_SNAPSHOT_KEY = "perm:gateway:interface-snapshot:";

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

    @Override
    public Optional<Set<Long>> getEffectiveRoles(Long tenantId, Long userId) {
        String key = EFFECTIVE_ROLES_KEY + tenantId + ":" + userId;
        @SuppressWarnings("unchecked")
        Set<Long> cached = (Set<Long>) l1Cache.getIfPresent(key);
        return Optional.ofNullable(cached);
    }

    @Override
    public void setEffectiveRoles(Long tenantId, Long userId, Set<Long> roleIds) {
        String key = EFFECTIVE_ROLES_KEY + tenantId + ":" + userId;
        l1Cache.put(key, roleIds);
    }

    @Override
    public void evictEffectiveRoles(Long tenantId, Long userId) {
        String key = EFFECTIVE_ROLES_KEY + tenantId + ":" + userId;
        redisTemplate.delete(key);
        l1Cache.invalidate(key);
    }

    @Override
    public Optional<RolePermSnapshot> getRolePermSnapshot(Long tenantId, Long roleId) {
        String key = ROLE_PERMS_KEY + tenantId + ":" + roleId;
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
        String key = ROLE_PERMS_KEY + tenantId + ":" + roleId;
        l1Cache.put(key, snapshot);
        redisTemplate.opsForValue().set(key, snapshot, permCacheProperties.getL2().getTtlMinutes(), TimeUnit.MINUTES);
    }

    @Override
    public void evictRolePermSnapshot(Long tenantId, Long roleId) {
        String key = ROLE_PERMS_KEY + tenantId + ":" + roleId;
        redisTemplate.delete(key);
        l1Cache.invalidate(key);
    }

    @Override
    public Optional<Long> getPermVersion(Long tenantId, Long roleId) {
        String key = PERM_VERSION_KEY + tenantId + ":" + roleId;
        @SuppressWarnings("unchecked")
        Long cached = (Long) l1Cache.getIfPresent(key);
        return Optional.ofNullable(cached);
    }

    @Override
    public void setPermVersion(Long tenantId, Long roleId, long version) {
        String key = PERM_VERSION_KEY + tenantId + ":" + roleId;
        l1Cache.put(key, version);
    }

    @Override
    public Optional<InterfaceSnapshot> getInterfaceSnapshot(Long tenantId, String serviceCode) {
        String key = INTERFACE_SNAPSHOT_KEY + tenantId + ":" + serviceCode;
        @SuppressWarnings("unchecked")
        InterfaceSnapshot cached = (InterfaceSnapshot) l1Cache.getIfPresent(key);
        return Optional.ofNullable(cached);
    }

    @Override
    public void setInterfaceSnapshot(Long tenantId, String serviceCode, InterfaceSnapshot snapshot) {
        String key = INTERFACE_SNAPSHOT_KEY + tenantId + ":" + serviceCode;
        l1Cache.put(key, snapshot);
        redisTemplate.opsForValue().set(key, snapshot, permCacheProperties.getL2().getTtlMinutes(), TimeUnit.MINUTES);
    }

    @Override
    public void evictInterfaceSnapshot(Long tenantId, String serviceCode) {
        String key = INTERFACE_SNAPSHOT_KEY + tenantId + ":" + serviceCode;
        redisTemplate.delete(key);
        l1Cache.invalidate(key);
    }
}
