package org.dromara.permission.kernel.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.dromara.authcenter.api.model.InterfacePermissionSnapshot;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 接口权限快照缓存
 *
 * 以版本号为主要失效依据，防止同一用户重复高成本组装。
 * 缓存 key = (tenantId, abstractUserId, permissionVersion)
 *
 * @author RuoYi-Cloud-Plus
 */
@Component
public class InterfaceSnapshotCache {

    private final Cache<String, InterfacePermissionSnapshot> cache;

    public InterfaceSnapshotCache() {
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(10000)
            .build();
    }

    /**
     * 生成缓存 key
     */
    public String buildKey(Long tenantId, Long abstractUserId, String permissionVersion) {
        return tenantId + ":" + abstractUserId + ":" + permissionVersion;
    }

    /**
     * 获取缓存的快照
     */
    public InterfacePermissionSnapshot get(Long tenantId, Long abstractUserId, String permissionVersion) {
        return cache.getIfPresent(buildKey(tenantId, abstractUserId, permissionVersion));
    }

    /**
     * 缓存快照
     */
    public void put(Long tenantId, Long abstractUserId, String permissionVersion, InterfacePermissionSnapshot snapshot) {
        cache.put(buildKey(tenantId, abstractUserId, permissionVersion), snapshot);
    }

    /**
     * 使指定用户的缓存失效
     */
    public void invalidate(Long tenantId, Long abstractUserId) {
        // 由于 key 包含版本号，无法精确删除，但可以通过前缀匹配
        // 这里采用惰性清理：版本变化后，旧缓存会在 5 分钟后自动过期
        // 新版本的请求会创建新的缓存条目
    }

    /**
     * 清空所有缓存
     */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    /**
     * 获取缓存统计信息
     */
    public CacheStats getStats() {
        var stats = cache.stats();
        return new CacheStats(
            cache.estimatedSize(),
            stats.hitCount(),
            stats.missCount(),
            stats.evictionCount()
        );
    }

    public record CacheStats(long size, long hitCount, long missCount, long evictionCount) {}
}
