package cn.ac.fage.accessmesh.gateway.cache;

import cn.ac.fage.accessmesh.perm.common.event.PermInvalidateEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

/**
 * 权限失效广播订阅器（T-PERM-006）
 * <p>
 * 订阅 Redis pub/sub topic {@code perm:invalidate}，收到 permission-center afterCommit 发布的事件后，
 * 按 tenant + serviceCodes / userIds 清理 Gateway 本地 {@code INTERFACE_SNAPSHOT} 缓存。
 * </p>
 */
@Component
public class PermInvalidationSubscriber implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(PermInvalidationSubscriber.class);

    public static final String TOPIC = "perm:invalidate";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final InterfaceSnapshotCacheInvalidator invalidator;

    private volatile boolean running;
    private Disposable subscription;

    public PermInvalidationSubscriber(ReactiveStringRedisTemplate redisTemplate,
                                      ObjectMapper objectMapper,
                                      InterfaceSnapshotCacheInvalidator invalidator) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.invalidator = invalidator;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        subscription = redisTemplate.listenTo(ChannelTopic.of(TOPIC))
            .map(ReactiveSubscription.Message::getMessage)
            .subscribe(this::handleMessage,
                e -> log.warn("Gateway perm invalidation subscription stopped: {}", e.getMessage()));
        running = true;
        log.info("Gateway subscribed to Redis topic {} for interface snapshot invalidation", TOPIC);
    }

    @Override
    public void stop() {
        if (subscription != null) {
            subscription.dispose();
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    void handleMessage(String message) {
        try {
            PermInvalidateEvent event = objectMapper.readValue(message, PermInvalidateEvent.class);
            long evicted = invalidator.evict(event);
            log.debug("Handled perm invalidation event (tenantId={}, evicted={})", event.tenantId(), evicted);
        } catch (JsonProcessingException e) {
            log.warn("Skip malformed perm invalidation message: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to handle perm invalidation message: {}", e.getMessage(), e);
        }
    }
}
