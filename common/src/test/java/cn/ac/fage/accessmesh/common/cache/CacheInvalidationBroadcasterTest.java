package cn.ac.fage.accessmesh.common.cache;

import cn.ac.fage.accessmesh.common.cache.impl.CombinedL1L2Store;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 普通 L1 跨实例失效广播测试（T-ACCESS-008 多实例一致性）。
 * <p>
 * 验证：广播消息 JSON 契约、订阅端收到消息清理本地 L1、发布失败不抛异常并计入
 * cache.invalidate.failures 指标。
 * </p>
 */
class CacheInvalidationBroadcasterTest {

    private static final Long TENANT_ID = 1L;

    private RedissonClient redissonClient;
    private RTopic topic;
    private CombinedL1L2Store combinedStore;
    private RedissonCacheInvalidationBroadcaster broadcaster;
    private SimpleMeterRegistry meterRegistry;
    private MessageListener<String> capturedListener;

    private final CacheCatalogEntry<String> catalog =
        CacheCatalogEntry.<String>builder()
            .code("test:l1l2")
            .mode(CacheMode.L1_L2)
            .l1Ttl(Duration.ofMinutes(10))
            .l1MaxSize(100)
            .l2Ttl(Duration.ofMinutes(30))
            .valueType(new TypeRef<String>() {})
            .build();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redissonClient = mock(RedissonClient.class);
        topic = mock(RTopic.class);
        when(redissonClient.getTopic(RedissonCacheInvalidationBroadcaster.TOPIC)).thenReturn(topic);

        // 捕获订阅 listener 以模拟其他实例收到广播
        org.mockito.Mockito.doAnswer(invocation -> {
            capturedListener = invocation.getArgument(1);
            return 1;
        }).when(topic).addListener(eq(String.class), any(MessageListener.class));

        ObjectMapper objectMapper = new ObjectMapper();
        meterRegistry = new SimpleMeterRegistry();
        combinedStore = new CombinedL1L2Store(redissonClient, objectMapper, meterRegistry,
            new CacheProperties());
        broadcaster = new RedissonCacheInvalidationBroadcaster(redissonClient, objectMapper,
            combinedStore, meterRegistry);
    }

    private String fullKey(Object id) {
        return CacheKeyUtil.build(TENANT_ID, catalog.getCode(), id);
    }

    @Test
    void broadcastEvict_shouldPublishJsonMessageWithKeys() throws Exception {
        broadcaster.broadcastEvict(catalog.getCode(), TENANT_ID, Set.of(fullKey("a"), fullKey("b")));

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(topic).publish(messageCaptor.capture());
        CacheInvalidationMessage message = parse(messageCaptor.getValue());
        assertThat(message.catalogCode()).isEqualTo(catalog.getCode());
        assertThat(message.tenantId()).isEqualTo(TENANT_ID);
        assertThat(message.all()).isFalse();
        assertThat(message.keys()).containsExactlyInAnyOrder(fullKey("a"), fullKey("b"));
    }

    @Test
    void broadcastEvictAll_shouldPublishAllMessage() throws Exception {
        broadcaster.broadcastEvictAll(catalog.getCode(), TENANT_ID);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(topic).publish(messageCaptor.capture());
        CacheInvalidationMessage message = parse(messageCaptor.getValue());
        assertThat(message.all()).isTrue();
        assertThat(message.keys()).isEmpty();
    }

    @Test
    void receivedMessage_shouldClearLocalL1Only() throws Exception {
        // 本实例 L1 放入条目（L2 写走 mock RBucket）
        String keyA = fullKey("a");
        combinedStore.put(catalog, keyA, "v1");
        assertThat(combinedStore.get(catalog, keyA)).isEqualTo("v1");

        // 模拟收到其他实例广播：清理本地 L1
        String json = new ObjectMapper().writeValueAsString(
            new CacheInvalidationMessage(catalog.getCode(), TENANT_ID, Set.of(keyA), false));
        capturedListener.onMessage(RedissonCacheInvalidationBroadcaster.TOPIC, json);

        // L1 已清（get 走 L2 mock 返回 null，证明 L1 未命中）
        assertThat(combinedStore.get(catalog, keyA)).isNull();
    }

    @Test
    void receivedAllMessage_shouldClearTenantLocalL1() throws Exception {
        String keyA = fullKey("a");
        combinedStore.put(catalog, keyA, "v1");

        String json = new ObjectMapper().writeValueAsString(
            new CacheInvalidationMessage(catalog.getCode(), TENANT_ID, Set.of(), true));
        capturedListener.onMessage(RedissonCacheInvalidationBroadcaster.TOPIC, json);

        assertThat(combinedStore.get(catalog, keyA)).isNull();
    }

    @Test
    void publishFailure_shouldNotThrowAndCountMetric() {
        when(topic.publish(anyString())).thenThrow(new IllegalStateException("redis down"));

        assertThatCode(() ->
            broadcaster.broadcastEvict(catalog.getCode(), TENANT_ID, Set.of(fullKey("a"))))
            .doesNotThrowAnyException();

        assertThat(meterRegistry.counter("cache.invalidate.failures",
            "type", "broadcast", "catalog", catalog.getCode()).count()).isEqualTo(1.0);
    }

    @Test
    void malformedMessage_shouldNotThrowAndCountMetric() {
        assertThatCode(() ->
            capturedListener.onMessage(RedissonCacheInvalidationBroadcaster.TOPIC, "not-json"))
            .doesNotThrowAnyException();
        assertThat(meterRegistry.counter("cache.invalidate.failures",
            "type", "listen", "catalog", "unknown").count()).isEqualTo(1.0);
    }

    private CacheInvalidationMessage parse(String json) throws Exception {
        return new ObjectMapper().readValue(json, CacheInvalidationMessage.class);
    }
}
