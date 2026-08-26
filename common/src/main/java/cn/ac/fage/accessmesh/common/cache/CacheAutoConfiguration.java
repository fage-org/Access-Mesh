package cn.ac.fage.accessmesh.common.cache;

import cn.ac.fage.accessmesh.common.cache.spi.DistributedCacheStore;
import cn.ac.fage.accessmesh.common.cache.spi.LocalCacheStore;
import cn.ac.fage.accessmesh.common.cache.impl.CaffeineLocalCacheStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Map;
import java.util.TimeZone;

/**
 * 基础缓存自动配置类
 * <p>
 * 不依赖 Redisson，总是加载。提供：
 * <ul>
 *   <li>ObjectMapper for JSON 序列化</li>
 *   <li>CaffeineLocalCacheStore for L1_ONLY 模式</li>
 *   <li>唯一 CacheService，根据上下文中可用的 store 自动路由</li>
 * </ul>
 * </p>
 *
 * <h3>Redisson 相关配置：</h3>
 * <p>
 * 由 {@link RedissonCacheAutoConfiguration} 提供，仅当 RedissonClient 类存在时加载。
 * Gateway 等无 Redisson 依赖的模块可正常启动，仅支持 L1_ONLY 模式。
 * </p>
 *
 * <h3>统一 CacheService：</h3>
 * <p>
 * 始终只创建一个 CacheService bean：
 * - 无 Redisson store：仅支持 L1_ONLY
 * - 有 Redisson store：自动接入 L1_L2 与 L2_ONLY 能力
 * </p>
 * <p>
 * 基础配置仅依赖 store SPI 接口，不直接引用 Redisson 类型，
 * 以保证 Gateway 等无 Redisson 依赖的模块仍可安全启动。
 * </p>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "accessmesh.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CacheProperties.class)
public class CacheAutoConfiguration {

    /**
     * 提供 ObjectMapper 用于 JSON 序列化
     * <p>
     * 如果用户已配置 ObjectMapper，则使用用户的配置。
     * 时间序列化统一 UTC（T-ACCESS-024）：{@code LocalDateTime} 本身无时区
     * （ISO-8601 无偏移、契约语义=UTC 墙钟），{@code setTimeZone(UTC)} 是对
     * 未来可能引入的 {@code java.util.Date} 等带时区类型的防御性兜底，现状无消费方。
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper cacheObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setTimeZone(TimeZone.getTimeZone("UTC"));
        return mapper;
    }

    /**
     * Caffeine 本地缓存存储（L1_ONLY 模式）
     * <p>
     * 总是创建，用于纯本地缓存场景。
     * MeterRegistry 可选注入，无 actuator 时传 null。
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean
    public CaffeineLocalCacheStore caffeineLocalCacheStore(
            ObjectMapper cacheObjectMapper,
            @Autowired(required = false) MeterRegistry meterRegistry,
            CacheProperties cacheProperties) {
        return new CaffeineLocalCacheStore(cacheObjectMapper, meterRegistry, cacheProperties);
    }

    /**
     * 统一 CacheService
     * <p>
     * Redisson 相关 store 由独立自动配置按需贡献；这里始终只装配一个
     * CacheService，避免基础版与增强版之间的 bean 竞争。
     * 普通 L1 跨实例失效广播器（Redisson 可用时提供）可选注入，
     * Gateway 等无 Redisson 模块不广播（L1_ONLY 无跨实例语义）。
     * </p>
     */
    @Bean
    @ConditionalOnMissingBean
    public CacheService cacheService(
            CaffeineLocalCacheStore l1OnlyStore,
            Map<String, LocalCacheStore> localStores,
            Map<String, DistributedCacheStore> distributedStores,
            CacheProperties cacheProperties,
            @Autowired(required = false) CacheInvalidationBroadcaster invalidationBroadcaster) {
        LocalCacheStore l1L2Store = localStores.get("combinedL1L2Store");
        DistributedCacheStore l2OnlyStore = distributedStores.get("redissonBucketStore");
        return new DefaultCacheService(l1L2Store, l2OnlyStore, l1OnlyStore,
            cacheProperties, invalidationBroadcaster);
    }
}