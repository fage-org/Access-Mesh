package cn.ac.fage.accessmesh.common.cache;

import java.util.Set;

/**
 * 普通 L1 跨实例失效广播器（T-ACCESS-008 多实例一致性）
 * <p>
 * L1_L2 目录失效时由 {@link DefaultCacheService} 调用：清理共享 L2 的同时广播失效事件，
 * 其他实例订阅后清理本地 Caffeine L1。实现必须保证不抛异常（广播失败仅记录日志与指标，
 * 由各实例 L1 TTL 兜底）；广播只发生在 evict 实际执行时（事务场景即提交后），回滚不失效。
 * </p>
 * <p>
 * 仅 Redisson 可用的部署装配本实现；Gateway 等无 Redisson 模块不广播（L1_ONLY 无跨实例语义）。
 * </p>
 */
public interface CacheInvalidationBroadcaster {

    /**
     * 广播按键失效（其他实例清理对应 catalog 的本地 L1 条目）
     *
     * @param catalogCode 缓存目录编码
     * @param tenantId 租户ID
     * @param fullKeys 完整缓存键集合
     */
    void broadcastEvict(String catalogCode, Long tenantId, Set<String> fullKeys);

    /**
     * 广播目录租户级全量失效（其他实例清理对应 catalog 在该租户下的全部本地 L1 条目）
     *
     * @param catalogCode 缓存目录编码
     * @param tenantId 租户ID
     */
    void broadcastEvictAll(String catalogCode, Long tenantId);
}
