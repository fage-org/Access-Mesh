package cn.ac.fage.accessmesh.common.cache.spi;

import cn.ac.fage.accessmesh.common.cache.CacheCatalogEntry;

import java.util.Map;
import java.util.Set;

/**
 * 本地缓存存储 SPI 接口
 * <p>
 * 定义 L1 缓存操作抽象，支持：
 * - 单条/批量 get/put/evict
 * - 基于 CacheCatalogEntry 的配置
 * </p>
 *
 * <h3>实现：</h3>
 * <ul>
 *   <li>{@code CombinedL1L2Store}: L1_L2 模式（Caffeine + RBucket）</li>
 *   <li>{@code CaffeineLocalCacheStore}: L1_ONLY 模式（纯本地 Caffeine）</li>
 * </ul>
 */
public interface LocalCacheStore {

    /**
     * 单条查询
     *
     * @param catalog 缓存目录
     * @param fullKey 完整缓存键（已包含 tenantId + catalogCode + identifier）
     * @return 缓存值，不存在返回 null
     */
    <V> V get(CacheCatalogEntry<V> catalog, String fullKey);

    /**
     * 批量查询
     *
     * @param catalog 缓存目录
     * @param fullKeys 完整缓存键集合
     * @return 键值映射（仅包含命中的键）
     */
    <V> Map<String, V> getBatch(CacheCatalogEntry<V> catalog, Set<String> fullKeys);

    /**
     * 单条写入
     *
     * @param catalog 缓存目录
     * @param fullKey 完整缓存键
     * @param value 缓存值（null 时忽略）
     */
    <V> void put(CacheCatalogEntry<V> catalog, String fullKey, V value);

    /**
     * 批量写入
     *
     * @param catalog 缓存目录
     * @param data 键值映射（null 值会被忽略）
     */
    <V> void putBatch(CacheCatalogEntry<V> catalog, Map<String, V> data);

    /**
     * 单条失效
     *
     * @param catalog 缓存目录
     * @param fullKey 完整缓存键
     */
    <V> void evict(CacheCatalogEntry<V> catalog, String fullKey);

    /**
     * 批量失效
     *
     * @param catalog 缓存目录
     * @param fullKeys 完整缓存键集合
     */
    default <V> void evictBatch(CacheCatalogEntry<V> catalog, Set<String> fullKeys) {
        for (String key : fullKeys) {
            evict(catalog, key);
        }
    }

    /**
     * 清空指定目录的所有缓存
     * <p>
     * 使用 SCAN 分批删除，避免 KEYS 阻塞
     * </p>
     *
     * @param catalog 缓存目录
     * @param tenantId 租户ID
     */
    <V> void evictAll(CacheCatalogEntry<V> catalog, Long tenantId);

    /**
     * 获取缓存大小估算
     *
     * @param catalog 缓存目录
     * @return 缓存条目数量估算
     */
    <V> long estimatedSize(CacheCatalogEntry<V> catalog);
}