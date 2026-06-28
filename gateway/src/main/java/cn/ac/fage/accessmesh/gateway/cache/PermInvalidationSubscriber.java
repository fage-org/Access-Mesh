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
import reactor.core.publisher.Mono;

import java.time.Duration;

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
    private Disposable reconnectTask;

    public PermInvalidationSubscriber(ReactiveStringRedisTemplate redisTemplate,
                                      ObjectMapper objectMapper,
                                      InterfaceSnapshotCacheInvalidator invalidator) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.invalidator = invalidator;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        subscribe(false);
    }

    private synchronized void subscribe(boolean recovered) {
        if (!running) {
            return;
        }
        if (subscription != null) {
            subscription.dispose();
        }
        subscription = redisTemplate.listenTo(ChannelTopic.of(TOPIC))
            .map(ReactiveSubscription.Message::getMessage)
            .subscribe(this::handleMessage,
                this::handleSubscriptionStopped,
                () -> handleSubscriptionStopped(null));
        if (recovered) {
            invalidator.clearAll();
            log.warn("Gateway perm invalidation subscription rebuilt, cleared local interface snapshots");
        }
        log.info("Gateway subscribed to Redis topic {} for interface snapshot invalidation", TOPIC);
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (subscription != null) {
            subscription.dispose();
        }
        if (reconnectTask != null) {
            reconnectTask.dispose();
        }
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

    private void handleSubscriptionStopped(Throwable e) {
        if (!running) {
            return;
        }
        if (e == null) {
            log.warn("Gateway perm invalidation subscription completed, scheduling rebuild");
        } else {
            log.warn("Gateway perm invalidation subscription stopped, scheduling rebuild: {}", e.getMessage());
        }
        scheduleReconnect();
    }

    private synchronized void scheduleReconnect() {
        if (!running) {
            return;
        }
        if (reconnectTask != null && !reconnectTask.isDisposed()) {
            return;
        }
        reconnectTask = Mono.delay(Duration.ofSeconds(5))
            .subscribe(ignored -> {
                synchronized (PermInvalidationSubscriber.this) {
                    reconnectTask = null;
                }
                subscribe(true);
            });
    }
}
