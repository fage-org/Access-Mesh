package org.dromara.gateway.authz;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import lombok.extern.slf4j.Slf4j;
import org.dromara.authcenter.api.model.InterfacePermissionSnapshot;
import org.dromara.authcenter.api.model.PrincipalContext;
import org.dromara.gateway.config.properties.PermissionAuthzProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 权限快照本地缓存
 *
 * 使用 Caffeine 实现本地缓存，key = (tenantId, subjectKey, permissionVersion)
 * 版本变化后自动失效（新版本创建新缓存条目）
 *
 * @author RuoYi-Cloud-Plus
 */
@Slf4j
@Component
public class PermissionSnapshotCache {

    private final Cache<CacheKey, InterfacePermissionSnapshot> cache;
    private final PermissionSnapshotClient delegate;
    private final PermissionAuthzProperties properties;

    /**
     * 缓存键
     */
    record CacheKey(String tenantId, String subjectKey, String permissionVersion) {}

    public PermissionSnapshotCache(PermissionSnapshotClient delegate, PermissionAuthzProperties properties) {
        this.delegate = delegate;
        this.properties = properties;

        // 使用自定义过期策略：写入后 5 分钟过期
        this.cache = Caffeine.newBuilder()
            .maximumSize(properties.getCacheMaxSize())
            .expireAfter(new Expiry<CacheKey, InterfacePermissionSnapshot>() {
                @Override
                public long expireAfterCreate(CacheKey key, InterfacePermissionSnapshot value, long currentTime) {
                    // 写入后 5 分钟过期
                    return TimeUnit.MINUTES.toNanos(properties.getCacheExpireMinutes());
                }

                @Override
                public long expireAfterUpdate(CacheKey key, InterfacePermissionSnapshot value, long currentTime, long currentExpireTime) {
                    return currentExpireTime;
                }

                @Override
                public long expireAfterRead(CacheKey key, InterfacePermissionSnapshot value, long currentTime, long currentExpireTime) {
                    return currentExpireTime;
                }
            })
            .recordStats()
            .build();

        log.info("PermissionSnapshotCache initialized with maxSize={}, expireMinutes={}",
            properties.getCacheMaxSize(), properties.getCacheExpireMinutes());
    }

    /**
     * 获取快照
     *
     * 优先从缓存获取，缓存未命中时从 delegate 加载
     *
     * @param principalContext 主体上下文
     * @return 权限快照
     */
    public InterfacePermissionSnapshot getSnapshot(PrincipalContext principalContext) {
        CacheKey key = new CacheKey(
            principalContext.getTenantId(),
            principalContext.getSubjectKey(),
            principalContext.getPermissionVersion()
        );

        InterfacePermissionSnapshot snapshot = cache.getIfPresent(key);
        if (snapshot != null) {
            log.debug("Cache hit for tenant={}, subject={}, version={}",
                key.tenantId(), key.subjectKey(), key.permissionVersion());
            return snapshot;
        }

        // 缓存未命中，从 delegate 加载
        log.debug("Cache miss for tenant={}, subject={}, version={}",
            key.tenantId(), key.subjectKey(), key.permissionVersion());
        snapshot = delegate.loadSnapshot(principalContext);

        if (snapshot != null) {
            cache.put(key, snapshot);
        }

        return snapshot;
    }

    /**
     * 使指定主体的缓存失效
     *
     * @param tenantId 租户ID
     * @param subjectKey 主体键
     */
    public void invalidate(String tenantId, String subjectKey) {
        cache.asMap().keySet().removeIf(key ->
            key.tenantId().equals(tenantId) && key.subjectKey().equals(subjectKey));
        log.debug("Invalidated cache for tenant={}, subject={}", tenantId, subjectKey);
    }

    /**
     * 使指定租户的所有缓存失效
     *
     * @param tenantId 租户ID
     */
    public void invalidateByTenant(String tenantId) {
        cache.asMap().keySet().removeIf(key -> key.tenantId().equals(tenantId));
        log.debug("Invalidated cache for tenant={}", tenantId);
    }

    /**
     * 清空所有缓存
     */
    public void invalidateAll() {
        cache.invalidateAll();
        log.debug("Invalidated all cache entries");
    }

    /**
     * 获取缓存统计信息
     *
     * @return 统计信息字符串
     */
    public String getStats() {
        var stats = cache.stats();
        return String.format("hitRate=%.2f%%, hitCount=%d, missCount=%d, evictionCount=%d, size=%d",
            stats.hitRate() * 100,
            stats.hitCount(),
            stats.missCount(),
            stats.evictionCount(),
            cache.estimatedSize());
    }

    /**
     * 获取缓存大小
     */
    public long size() {
        return cache.estimatedSize();
    }
}
