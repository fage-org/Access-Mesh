package cn.ac.fage.accessmesh.gateway.cache;

import cn.ac.fage.accessmesh.perm.common.event.PermInvalidateEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;
import reactor.core.publisher.Sinks;
import reactor.test.scheduler.VirtualTimeScheduler;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

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
    private VirtualTimeScheduler clock;

    @BeforeEach
    void setUp() {
        clock = VirtualTimeScheduler.getOrSet();
        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        objectMapper = new ObjectMapper();
        invalidator = mock(InterfaceSnapshotCacheInvalidator.class);
        subscriber = new PermInvalidationSubscriber(redisTemplate, objectMapper, invalidator);
    }

    @AfterEach
    void tearDown() {
        try {
            subscriber.stop();
        } finally {
            VirtualTimeScheduler.reset();
            clock.dispose();
        }
        assertThat(VirtualTimeScheduler.isFactoryEnabled()).isFalse();
    }

    // ─── 消息解析 ───

    @Nested
    class MessageParsing {

        @Test
        void shouldDeserializeMessageAndEvictLocalSnapshotCache() throws Exception {
            String message = objectMapper.writeValueAsString(
                new PermInvalidateEvent(1L, Set.of(200L), Set.of(10L), Set.of("example-service"))
            );

            subscriber.handleMessage(message);

            verify(invalidator).evict(argThat(event -> event.tenantId().equals(1L)
                && event.userIds().contains(10L)
                && event.serviceCodes().contains("example-service")));
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

        @ParameterizedTest(name = "{0}: 5 秒到期重连并清空快照")
        @EnumSource(value = SignalType.class, names = {"ON_COMPLETE", "ON_ERROR"})
        void shouldClearAllAndRebuild_whenSubscriptionStops(SignalType signal) {
            Sinks.Many<ReactiveSubscription.Message<String, String>> messages = Sinks.many().unicast().onBackpressureBuffer();
            doReturn(messages.asFlux(), Flux.never()).when(redisTemplate).listenTo(any(ChannelTopic.class));
            subscriber.start();
            assertThat(subscriber.isRunning()).isTrue();
            assertThat(messages.currentSubscriberCount()).isEqualTo(1);

            if (signal == SignalType.ON_COMPLETE) {
                assertThat(messages.tryEmitComplete()).isEqualTo(Sinks.EmitResult.OK);
            } else {
                assertThat(messages.tryEmitError(new IllegalStateException("Redis connection lost"))).isEqualTo(Sinks.EmitResult.OK);
            }
            clock.advanceTimeBy(Duration.ofMillis(4999));
            verify(redisTemplate, times(1)).listenTo(any(ChannelTopic.class));
            verify(invalidator, never()).clearAll();

            clock.advanceTimeBy(Duration.ofMillis(1));
            verify(redisTemplate, times(2)).listenTo(any(ChannelTopic.class));
            verify(invalidator).clearAll();
        }

        @Test
        void shouldNotReconnect_whenSubscriberStopped() {
            doReturn(Flux.error(new IllegalStateException("Redis connection lost")))
                .when(redisTemplate).listenTo(any(ChannelTopic.class));
            subscriber.start();
            subscriber.stop();
            assertThat(subscriber.isRunning()).isFalse();

            clock.advanceTimeBy(Duration.ofSeconds(10));
            verify(redisTemplate, times(1)).listenTo(any(ChannelTopic.class));
            verify(invalidator, never()).clearAll();
        }
    }
}
