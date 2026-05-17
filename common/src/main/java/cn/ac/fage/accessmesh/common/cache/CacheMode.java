package cn.ac.fage.accessmesh.common.cache;

/**
 * 缓存模式枚举
 * <p>
 * 定义三种缓存模式：
 * <ul>
 *   <li>L1_L2: 本地缓存 + Redis 分布式缓存（使用 CombinedL1L2Store）</li>
 *   <li>L2_ONLY: 仅 Redis 分布式缓存（使用 Redisson RBucket）</li>
 *   <li>L1_ONLY: 仅本地缓存（使用 Caffeine，无 Redis 依赖）</li>
 * </ul>
 * </p>
 */
public enum CacheMode {

    /**
     * L1 + L2 双层缓存模式
     * <p>
        * 使用 CombinedL1L2Store 实现：
     * - 本地 Caffeine 缓存作为 L1
        * - Redis RBucket 作为 L2 后端
        * - L2 TTL 按条目生效
     * </p>
     */
    L1_L2,

    /**
     * 仅 L2 分布式缓存模式
     * <p>
     * 使用 Redisson RBucket 实现：
     * - 纯 Redis 存储，无本地缓存
     * - 适用于需要跨节点一致性的数据
     * </p>
     */
    L2_ONLY,

    /**
     * 仅 L1 本地缓存模式
     * <p>
     * 使用 Caffeine 实现：
     * - 纯本地缓存，无 Redis 依赖
     * - 适用于 Gateway 等无需分布式同步的场景
     * </p>
     */
    L1_ONLY
}