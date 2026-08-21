package cn.ac.fage.accessmesh.common.cache;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * 统一缓存服务接口
 * <p>
 * 业务侧唯一缓存入口，提供：
 * - 单条/批量 get/put/evict
 * - evictAfterCommit（事务感知失效）
 * </p>
 *
 * <h3>业务使用模式：</h3>
 * <pre>{@code
 * // ① 查缓存（null = miss）
 * Set<Long> roles = cacheService.get(PermCacheCatalog.EFFECTIVE_ROLES, tenantId, userId);
 * // ② miss 后查 DB
 * if (roles == null) {
 *     roles = userRoleMapper.selectRoleIds(tenantId, userId);
 *     // ③ 回填缓存（value 为 null 时忽略）
 *     if (roles != null) {
 *         cacheService.put(PermCacheCatalog.EFFECTIVE_ROLES, tenantId, userId, roles);
 *     }
 * }
 * return roles;
 *
 * // ④ 事务提交后失效
 * cacheService.evictAfterCommit(PermCacheCatalog.EFFECTIVE_ROLES, tenantId, userId);
 * }</pre>
 *
 * <h3>核心约束：</h3>
 * <ul>
 *   <li>不提供带 loader 的 get API——业务侧负责 miss 后的数据加载</li>
 *   <li>put(null) 静默忽略——不缓存 null</li>
 *   <li>evictAfterCommit 内部感知事务状态</li>
 * </ul>
 */
public interface CacheService {

    /**
     * 单条查询缓存
     *
     * @param catalog   缓存目录
     * @param tenantId  租户ID
     * @param identifier 业务标识（如用户ID、角色ID）
     * @return 缓存值，不存在返回 null
     */
    <V> V get(CacheCatalogEntry<V> catalog, Long tenantId, Object identifier);

    /**
     * 批量查询缓存
     *
     * @param catalog    缓存目录
     * @param tenantId   租户ID
     * @param identifiers 业务标识集合
     * @return 键值映射（仅包含命中的键）
     */
    <K, V> Map<K, V> getBatch(CacheCatalogEntry<V> catalog, Long tenantId, Set<K> identifiers);

    /**
     * 单条写入缓存
     * <p>
     * value 为 null 时静默忽略，不缓存 null
     * </p>
     *
     * @param catalog    缓存目录
     * @param tenantId   租户ID
     * @param identifier 业务标识
     * @param value      缓存值
     */
    <V> void put(CacheCatalogEntry<V> catalog, Long tenantId, Object identifier, V value);

    /**
     * 单条写入缓存（单次有效 TTL）
     * <p>
     * effectiveTtl 为本次写入的有效 TTL，强制不超过 catalog 有效 TTL；
     * 剩余 TTL ≤ 0 时不写入。普通缓存默认仍使用 catalog TTL（调用无 TTL 重载）。
     * </p>
     *
     * @param catalog      缓存目录
     * @param tenantId     租户ID
     * @param identifier   业务标识
     * @param value        缓存值
     * @param effectiveTtl 单次有效 TTL
     */
    <V> void put(CacheCatalogEntry<V> catalog, Long tenantId, Object identifier, V value, Duration effectiveTtl);

    /**
     * 批量写入缓存
     * <p>
     * null 值会被静默忽略
     * </p>
     *
     * @param catalog 缓存目录
     * @param tenantId 租户ID
     * @param data    键值映射（identifier -> value）
     */
    <K, V> void putBatch(CacheCatalogEntry<V> catalog, Long tenantId, Map<K, V> data);

    /**
     * 批量写入缓存（单次有效 TTL）
     * <p>
     * 语义与 {@link #put(CacheCatalogEntry, Long, Object, Object, Duration)} 一致，
     * 单条与批量写入的 TTL 约束相同。
     * </p>
     *
     * @param catalog      缓存目录
     * @param tenantId     租户ID
     * @param data         键值映射（identifier -> value）
     * @param effectiveTtl 单次有效 TTL
     */
    <K, V> void putBatch(CacheCatalogEntry<V> catalog, Long tenantId, Map<K, V> data, Duration effectiveTtl);

    /**
     * 开启一轮授权读取（剩余 TTL 回填辅助）
     * <p>
     * 在缓存 miss 后、开始数据库读取事务或快照查询<b>之前</b>调用；返回的令牌记录
     * 单调时钟起点。回填使用 {@link #put(CacheReadToken, Long, Object, Object)} /
     * {@link #putBatch(CacheReadToken, Long, Map)}，框架自动写入「读取起点 + catalog
     * 有效 TTL」扣除已耗时间后的剩余 TTL；剩余 ≤ 0 时不写入。
     * 单条、批量、并发合并和重试不得重新调用本方法重置起点。
     * </p>
     *
     * @param catalog 缓存目录
     * @return 读取令牌
     * @param <V> 缓存值类型
     */
    <V> CacheReadToken<V> beginRead(CacheCatalogEntry<V> catalog);

    /**
     * 使用读取令牌回填缓存（剩余 TTL）
     * <p>
     * 剩余 TTL = catalog 有效 TTL - (当前时刻 - 令牌起点)；强制不超过 catalog TTL，
     * 剩余 ≤ 0 时不写入。value 为 null 时静默忽略。
     * </p>
     *
     * @param token      读取令牌（beginRead 产生）
     * @param tenantId   租户ID
     * @param identifier 业务标识
     * @param value      缓存值
     */
    <V> void put(CacheReadToken<V> token, Long tenantId, Object identifier, V value);

    /**
     * 使用读取令牌批量回填缓存（共享同一读取起点，剩余 TTL）
     * <p>
     * 批量回填与单条使用同一令牌起点，不得因批量或重试重新获得完整 catalog TTL。
     * </p>
     *
     * @param token    读取令牌（beginRead 产生）
     * @param tenantId 租户ID
     * @param data     键值映射（identifier -> value）
     */
    <K, V> void putBatch(CacheReadToken<V> token, Long tenantId, Map<K, V> data);

    /**
     * 单条失效缓存
     *
     * @param catalog    缓存目录
     * @param tenantId   租户ID
     * @param identifier 业务标识
     */
    <V> void evict(CacheCatalogEntry<V> catalog, Long tenantId, Object identifier);

    /**
     * 批量失效缓存
     *
     * @param catalog     缓存目录
     * @param tenantId    租户ID
     * @param identifiers 业务标识集合
     */
    <K, V> void evictBatch(CacheCatalogEntry<V> catalog, Long tenantId, Set<K> identifiers);

    /**
     * 事务提交后失效缓存
     * <p>
     * 内部检查是否有活跃事务：
     * - 有事务：注册 TransactionSynchronization.afterCommit() 钩子
     * - 无事务：立即执行 evict
     * </p>
     *
     * @param catalog    缓存目录
     * @param tenantId   租户ID
     * @param identifier 业务标识
     */
    <V> void evictAfterCommit(CacheCatalogEntry<V> catalog, Long tenantId, Object identifier);

    /**
     * 批量事务提交后失效缓存
     *
     * @param catalog     缓存目录
     * @param tenantId    租户ID
     * @param identifiers 业务标识集合
     */
    <K, V> void evictBatchAfterCommit(CacheCatalogEntry<V> catalog, Long tenantId, Set<K> identifiers);

    /**
     * 清空指定目录的所有缓存
     *
     * @param catalog   缓存目录
     * @param tenantId 租户ID
     */
    <V> void evictAll(CacheCatalogEntry<V> catalog, Long tenantId);

    /**
     * 清空指定目录在<b>全部租户</b>下的缓存（catalog 级全量失效）
     * <p>
     * 供订阅重连全量清空等不依赖租户枚举来源的运维/恢复场景使用；
     * L2 使用 SCAN 模式删除，代价高于租户级 evictAll，禁止高频调用。
     * </p>
     *
     * @param catalog 缓存目录
     */
    <V> void evictAll(CacheCatalogEntry<V> catalog);
}