package cn.ac.fage.accessmesh.common.cache;

import cn.ac.fage.accessmesh.common.cache.impl.CombinedL1L2Store;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * 基于 Redis RTopic 的普通 L1 跨实例失效广播实现（T-ACCESS-008）
 * <p>
 * 发布：evict / evictAll 时向 topic {@value #TOPIC} 广播
 * {@link CacheInvalidationMessage} JSON。
 * 订阅：收到消息后清理本实例 {@link CombinedL1L2Store} 中对应 catalog 的本地 L1
 * （不触碰 L2——发布方已清理共享 L2）。
 * </p>
 * <p>
 * 失败语义：发布/订阅处理失败仅记录 WARN 与 {@code cache.invalidate.failures} 指标，
 * 不抛异常、不影响已提交事务；广播丢失场景由各实例 L1 TTL 兜底（普通缓存，
 * 非授权 L2_ONLY 目录——后者无 L1）。
 * </p>
 */
public class RedissonCacheInvalidationBroadcaster implements CacheInvalidationBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(RedissonCacheInvalidationBroadcaster.class);

    public static final String TOPIC = "accessmesh:cache:l1-invalidate";

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final CombinedL1L2Store localL1L2Store;
    private final MeterRegistry meterRegistry;

    public RedissonCacheInvalidationBroadcaster(RedissonClient redissonClient,
                                                ObjectMapper objectMapper,
                                                CombinedL1L2Store localL1L2Store,
                                                MeterRegistry meterRegistry) {
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper;
        this.localL1L2Store = localL1L2Store;
        this.meterRegistry = meterRegistry;
        subscribe();
    }

    @Override
    public void broadcastEvict(String catalogCode, Long tenantId, Set<String> fullKeys) {
        publish(new CacheInvalidationMessage(catalogCode, tenantId, fullKeys, false));
    }

    @Override
    public void broadcastEvictAll(String catalogCode, Long tenantId) {
        publish(new CacheInvalidationMessage(catalogCode, tenantId, Set.of(), true));
    }

    private void publish(CacheInvalidationMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            redissonClient.getTopic(TOPIC).publish(json);
        } catch (Exception e) {
            log.warn("Failed to broadcast L1 invalidation (catalog={}, tenantId={}): {}",
                message.catalogCode(), message.tenantId(), e.getMessage());
            incrementFailure("broadcast", message.catalogCode());
        }
    }

    private void subscribe() {
        try {
            RTopic topic = redissonClient.getTopic(TOPIC);
            topic.addListener(String.class, (MessageListener<String>) (channel, message) ->
                handleInvalidationMessage(message));
            log.info("Subscribed to cache L1 invalidation topic {}", TOPIC);
        } catch (Exception e) {
            // 订阅失败不阻断启动：本实例退化为仅靠本地 evict 与 TTL，其他实例仍可广播
            log.warn("Failed to subscribe cache L1 invalidation topic {}: {}", TOPIC, e.getMessage());
            incrementFailure("subscribe", "unknown");
        }
    }

    private void handleInvalidationMessage(String message) {
        try {
            CacheInvalidationMessage parsed = objectMapper.readValue(message, CacheInvalidationMessage.class);
            if (parsed.all()) {
                localL1L2Store.invalidateLocalL1All(parsed.catalogCode(), parsed.tenantId());
            } else if (parsed.keys() != null && !parsed.keys().isEmpty()) {
                localL1L2Store.invalidateLocalL1(parsed.catalogCode(), parsed.keys());
            }
        } catch (Exception e) {
            log.warn("Failed to handle cache L1 invalidation message: {}", e.getMessage());
            incrementFailure("listen", "unknown");
        }
    }

    private void incrementFailure(String type, String catalogCode) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder("cache.invalidate.failures")
            .tag("type", type)
            .tag("catalog", catalogCode != null ? catalogCode : "unknown")
            .description("Cache invalidation failures (broadcast/subscribe/listen)")
            .register(meterRegistry)
            .increment();
    }
}
