package cn.ac.fage.accessmesh.common.cache;

import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * 通用缓存管理器接口
 * <p>
 * 统一封装 L1(Caffeine) + L2(Redis) 双层缓存的读写和失效机制。
 * 所有需要缓存的业务数据应使用此接口，确保缓存一致性。
 * </p>
 *
 * <h3>缓存规范：</h3>
 * <ul>
 *   <li>缓存键格式：{@code namespace:tenantId:entityType:entityId}</li>
 *   <li>失效顺序：先失效 L2，再失效 L1（防止竞态条件）</li>
 *   <li>写入顺序：先写 L2，再写 L1（确保 Redis 优先）</li>
 *   <li>读取顺序：先查 L1，miss 则查 L2，miss 则查 DB</li>
 * </ul>
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 */
public interface GenericCacheManager<K, V> {

    /**
     * 获取缓存命名空间
     * <p>
     * 命名空间用于区分不同业务类型的缓存，避免键冲突。
     * 格式：业务域:数据类型（如 "perm:condition", "perm:user:roles"）
     * </p>
     *
     * @return 缓存命名空间
     */
    String getNamespace();

    /**
     * 获取 L1 缓存 TTL（分钟）
     *
     * @return L1 缓存过期时间
     */
    int getL1TtlMinutes();

    /**
     * 获取 L2 缓存 TTL（分钟）
     *
     * @return L2 缓存过期时间
     */
    int getL2TtlMinutes();

    /**
     * 获取 L1 缓存最大容量
     * <p>
     * 问题9：避免硬编码，由子类根据业务场景返回合适的值
     * </p>
     *
     * @return L1 缓存最大容量
     */
    long getL1MaximumSize();

    /**
     * 获取缓存值类型的 Class 对象
     * <p>
     * 问题2：解决泛型类型擦除问题，用于 JSON 反序列化
     * </p>
     *
     * @return 值类型的 Class 对象
     */
    Class<V> getValueClass();

    // ==================== 读取操作 ====================

    /**
     * 单条查询：先查 L1，miss 则查 L2，miss 则使用 loader 加载
     * <p>
     * 加载后会同时写入 L1 和 L2。
     * </p>
     *
     * @param tenantId 租户ID
     * @param key      缓存键（业务键，不含命名空间和租户ID）
     * @param loader   数据加载器（当缓存不存在时调用，接收 tenantId 和 key）
     * @return 缓存值，可能为 null
     */
    V get(Long tenantId, K key, BiFunction<Long, K, V> loader);

    /**
     * 批量查询：先查缓存，miss 的键使用 loader 批量加载
     * <p>
     * 批量加载后一次性写入缓存，避免逐条写入的性能问题。
     * </p>
     * <p>
     * 问题7：loader 改为 BiFunction，包含 tenantId 参数
     * </p>
     *
     * @param tenantId 租户ID
     * @param keys     缓存键集合
     * @param loader   批量数据加载器（输入 tenantId 和缺失的键集合，返回键值映射）
     * @return 键值映射（包含缓存命中和加载的数据）
     */
    Map<K, V> getBatch(Long tenantId, Set<K> keys,
                       BiFunction<Long, Set<K>, Map<K, V>> loader);

    /**
     * 仅查询缓存（不加载）
     * <p>
     * 用于检查缓存是否存在，或获取已缓存的数据。
     * </p>
     *
     * @param tenantId 租户ID
     * @param key      缓存键
     * @return 缓存值，不存在返回 null
     */
    V getOnly(Long tenantId, K key);

    // ==================== 写入操作 ====================

    /**
     * 单条写入：同时写入 L1 和 L2
     * <p>
     * 问题5：写入顺序为先 L2 后 L1
     * </p>
     *
     * @param tenantId 租户ID
     * @param key      缓存键
     * @param value    缓存值
     */
    void put(Long tenantId, K key, V value);

    /**
     * 批量写入：同时写入 L1 和 L2
     *
     * @param tenantId 租户ID
     * @param data     键值映射
     */
    void putBatch(Long tenantId, Map<K, V> data);

    // ==================== 失效操作 ====================

    /**
     * 单条失效：先失效 L2，再失效 L1
     * <p>
     * 注意顺序：必须先失效 L2 再失效 L1，防止竞态条件导致脏数据。
     * </p>
     *
     * @param tenantId 租户ID
     * @param key      缓存键
     */
    void evict(Long tenantId, K key);

    /**
     * 批量失效：先失效 L2，再失效 L1
     * <p>
     * 批量失效时 L2 使用 delete(keys) 一次网络往返，
     * L1 使用循环逐个失效（本地操作，开销小）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param keys     缓存键集合
     */
    void evictBatch(Long tenantId, Set<K> keys);

    /**
     * 全量失效：清除当前命名空间下的所有缓存
     * <p>
     * 用于数据批量更新后需要重置缓存的场景。
     * </p>
     * <p>
     * 问题1：使用 SCAN 命令分批删除，避免 KEYS 阻塞
     * </p>
     *
     * @param tenantId 租户ID
     */
    void evictAll(Long tenantId);

    // ==================== 辅助操作 ====================

    /**
     * 构建完整的缓存键
     * <p>
     * 格式：{@code namespace:tenantId:key}
     * </p>
     *
     * @param tenantId 租户ID
     * @param key      业务键
     * @return 完整缓存键
     */
    String buildCacheKey(Long tenantId, K key);
}