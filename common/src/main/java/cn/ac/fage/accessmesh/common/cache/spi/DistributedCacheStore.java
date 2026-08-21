package cn.ac.fage.accessmesh.common.cache.spi;

import cn.ac.fage.accessmesh.common.cache.CacheCatalogEntry;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * 分布式缓存存储 SPI 接口
 * <p>
 * 定义 L2 缓存操作抽象，支持：
 * - 单条/批量 get/put/evict
 * - SCAN 批量删除
 * - Pipeline 批量写入
 * </p>
 *
 * <h3>实现：</h3>
 * <ul>
 *   <li>{@code RedissonBucketStore}: L2_ONLY 模式（RBucket）</li>
 * </ul>
 *
 * <h3>注意：</h3>
 * <p>
 * L1_L2 模式由 LocalCacheStore 实现统一处理；本接口仅用于纯 L2 存储。
 * </p>
 */
public interface DistributedCacheStore {

    /**
     * 单条查询
     *
     * @param catalog 缓存目录
     * @param fullKey 完整缓存键
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
     * 单条写入（单次有效 TTL）
     * <p>
     * effectiveTtl 为本次写入的有效 TTL，强制不超过 catalog 有效 TTL；
     * 剩余 TTL ≤ 0 时不写入。单条与批量写入语义一致。
     * </p>
     *
     * @param catalog 缓存目录
     * @param fullKey 完整缓存键
     * @param value 缓存值（null 时忽略）
     * @param effectiveTtl 单次有效 TTL（null 视为使用 catalog TTL）
     */
    <V> void put(CacheCatalogEntry<V> catalog, String fullKey, V value, Duration effectiveTtl);

    /**
     * 批量写入（使用 Pipeline）
     *
     * @param catalog 缓存目录
     * @param data 键值映射（null 值会被忽略）
     */
    <V> void putBatch(CacheCatalogEntry<V> catalog, Map<String, V> data);

    /**
     * 批量写入（单次有效 TTL，使用 Pipeline）
     * <p>
     * 语义与 {@link #put(CacheCatalogEntry, String, Object, Duration)} 一致，
     * 单条与批量写入的 TTL 约束相同。
     * </p>
     *
     * @param catalog 缓存目录
     * @param data 键值映射（null 值会被忽略）
     * @param effectiveTtl 单次有效 TTL（null 视为使用 catalog TTL）
     */
    <V> void putBatch(CacheCatalogEntry<V> catalog, Map<String, V> data, Duration effectiveTtl);

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
     * 清空指定目录在全部租户下的缓存（catalog 级全量失效）
     *
     * @param catalog 缓存目录
     */
    default <V> void evictAll(CacheCatalogEntry<V> catalog) {
        throw new UnsupportedOperationException("catalog-wide evictAll is not supported by this store");
    }
}