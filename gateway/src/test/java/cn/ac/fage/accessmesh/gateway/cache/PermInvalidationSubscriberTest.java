package cn.ac.fage.accessmesh.gateway.cache;

import cn.ac.fage.accessmesh.perm.common.event.PermInvalidateEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * PermInvalidationSubscriber 单元测试（T-PERM-006 / T-PERM-008）
 * <p>
 * 覆盖消息解析、断线恢复→clearAll 路径。
 * </p>
 */
class PermInvalidationSubscriberTest {

    private ReactiveStringRedisTemplate redisTemplate;
    private ObjectMapper objectMapper;
    private InterfaceSnapshotCacheInvalidator invalidator;
    private PermInvalidationSubscriber subscriber;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        objectMapper = new ObjectMapper();
        invalidator = mock(InterfaceSnapshotCacheInvalidator.class);
        subscriber = new PermInvalidationSubscriber(redisTemplate, objectMapper, invalidator);
    }

    @AfterEach
    void tearDown() {
        // 确保每个测试结束后停止 subscriber，避免后台重连循环持续运行串扰后续用例
        subscriber.stop();
    }

    // ─── 消息解析 ───

    @Nested
    class MessageParsing {

        @Test
        void shouldDeserializeMessageAndEvictLocalSnapshotCache() throws Exception {
            String message = objectMapper.writeValueAsString(
                new PermInvalidateEvent(1L, Set.of(200L), Set.of(10L), Set.of("admin-service"))
            );

            subscriber.handleMessage(message);

            verify(invalidator).evict(argThat(event -> event.tenantId().equals(1L)
                && event.userIds().contains(10L)
                && event.serviceCodes().contains("admin-service")));
        }

        @Test
        void shouldSkipMalformedMessage() {
            subscriber.handleMessage("not-json");

            verify(invalidator, never()).evict(any());
        }
    }

    // ─── 断线恢复 / clearAll ───

    @Nested
    class ReconnectAndClearAll {

        @Test
        void shouldClearAllAndRebuild_whenSubscriptionCompletes() {
            // 模拟 Redis 订阅在发出 onComplete 信号后停止
            @SuppressWarnings("unchecked")
            Flux<ReactiveSubscription.Message<String, String>> messageFlux = Flux.empty();
            // 使用 doReturn 绕过通配符泛型匹配问题
            doReturn(messageFlux).when(redisTemplate).listenTo(any(ChannelTopic.class));

            // 启动 subscriber，触发首次订阅
            subscriber.start();
            assertThat(subscriber.isRunning()).isTrue();

            // 首次订阅不会触发 clearAll（recovered=false）
            verify(invalidator, never()).clearAll();

            // 等待 reconnect delay (5s) + subscribe(true) 执行
            Mono.delay(Duration.ofSeconds(7)).block(Duration.ofSeconds(15));

            // 重建后应调用 clearAll
            verify(invalidator).clearAll();
        }

        @Test
        void shouldClearAllAndRebuild_whenSubscriptionErrors() {
            // 模拟 Redis 订阅立即报错
            @SuppressWarnings("unchecked")
            Flux<ReactiveSubscription.Message<String, String>> errorFlux =
                Flux.error(new RuntimeException("Redis connection lost"));
            doReturn(errorFlux).when(redisTemplate).listenTo(any(ChannelTopic.class));

            subscriber.start();
            assertThat(subscriber.isRunning()).isTrue();

            // 等待 reconnect delay + subscribe(true)
            Mono.delay(Duration.ofSeconds(7)).block(Duration.ofSeconds(15));

            // 重建后应调用 clearAll
            verify(invalidator).clearAll();
        }

        @Test
        void shouldNotReconnect_whenSubscriberStopped() {
            @SuppressWarnings("unchecked")
            Flux<ReactiveSubscription.Message<String, String>> errorFlux =
                Flux.error(new RuntimeException("Redis connection lost"));
            doReturn(errorFlux).when(redisTemplate).listenTo(any(ChannelTopic.class));

            subscriber.start();
            // 立即停止
            subscriber.stop();
            assertThat(subscriber.isRunning()).isFalse();

            // 等待足够长时间
            Mono.delay(Duration.ofSeconds(7)).block(Duration.ofSeconds(15));

            // 停止后不应触发 clearAll
            verify(invalidator, never()).clearAll();
        }
    }
}
