package cn.ac.fage.accessmesh.common.cache;

import cn.ac.fage.accessmesh.common.cache.impl.RedissonBucketStore;
import cn.ac.fage.accessmesh.common.cache.impl.CombinedL1L2Store;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Redisson 缓存自动配置类
 * <p>
 * 仅当 RedissonClient 类存在时加载（类级别 @ConditionalOnClass）。
 * 提供：
 * <ul>
 *   <li>CombinedL1L2Store: Caffeine L1 + RBucket L2，支持条目级 TTL</li>
 *   <li>RedissonBucketStore: 纯 L2 分布式缓存</li>
 * </ul>
 * </p>
 *
 * <h3>为什么用类级别条件：</h3>
 * <p>
 * 方法级别 @ConditionalOnClass 无法阻止 Spring 反射解析方法签名时的类加载。
 * Gateway 等模块不依赖 Redisson，方法签名中的 RedissonClient 类型会导致 NoClassDefFoundError。
 * </p>
 *
 * <h3>职责边界：</h3>
 * <p>
 * 本配置只负责提供 Redisson 相关 store bean；统一 CacheService 始终由
 * CacheAutoConfiguration 创建，从而避免基础版与增强版之间的 bean 竞争。
 * </p>
 */
@AutoConfiguration
@ConditionalOnClass(RedissonClient.class)
@ConditionalOnProperty(prefix = "accessmesh.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CacheProperties.class)
public class RedissonCacheAutoConfiguration {

    /**
     * L1_L2 组合存储
     * <p>
     * 使用 Caffeine (L1) + RBucket (L2) 组合：
     * - L1 本地缓存，TTL 由 Caffeine 控制
     * - L2 Redis 缓存，TTL 由 RBucket per-key 控制
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean
    public CombinedL1L2Store combinedL1L2Store(
            RedissonClient redissonClient,
            ObjectMapper cacheObjectMapper,
            @Autowired(required = false) MeterRegistry meterRegistry,
            CacheProperties cacheProperties) {
        return new CombinedL1L2Store(redissonClient, cacheObjectMapper, meterRegistry, cacheProperties);
    }

    /**
     * Redisson Bucket 存储（L2_ONLY 模式）
     * <p>
     * 纯 Redis 分布式缓存，per-key TTL
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean
    public RedissonBucketStore redissonBucketStore(
            RedissonClient redissonClient,
            ObjectMapper cacheObjectMapper,
            @Autowired(required = false) MeterRegistry meterRegistry,
            CacheProperties cacheProperties) {
        return new RedissonBucketStore(redissonClient, cacheObjectMapper, meterRegistry, cacheProperties);
    }

    /**
     * 普通 L1 跨实例失效广播器（T-ACCESS-008）
     * <p>
     * L1_L2 目录失效时经 RTopic 广播，各实例订阅后清理本地 Caffeine L1；
     * 广播失败不抛异常，由各实例 L1 TTL 兜底。构造时即订阅 topic。
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean
    public RedissonCacheInvalidationBroadcaster redissonCacheInvalidationBroadcaster(
            RedissonClient redissonClient,
            ObjectMapper cacheObjectMapper,
            CombinedL1L2Store combinedL1L2Store,
            @Autowired(required = false) MeterRegistry meterRegistry) {
        return new RedissonCacheInvalidationBroadcaster(redissonClient, cacheObjectMapper,
            combinedL1L2Store, meterRegistry);
    }

}