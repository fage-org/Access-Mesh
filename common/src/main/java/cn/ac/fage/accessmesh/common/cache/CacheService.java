package cn.ac.fage.accessmesh.common.cache;

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
     * @param tenantId  租户ID
     */
    <V> void evictAll(CacheCatalogEntry<V> catalog, Long tenantId);
}