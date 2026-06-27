package cn.ac.fage.accessmesh.gateway.cache;

import cn.ac.fage.accessmesh.perm.common.event.PermInvalidateEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import java.util.Set;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * PermInvalidationSubscriber 单元测试（T-PERM-006）
 */
class PermInvalidationSubscriberTest {

    private final ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final InterfaceSnapshotCacheInvalidator invalidator = mock(InterfaceSnapshotCacheInvalidator.class);
    private final PermInvalidationSubscriber subscriber =
        new PermInvalidationSubscriber(redisTemplate, objectMapper, invalidator);

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

        verify(invalidator, never()).evict(org.mockito.ArgumentMatchers.any());
    }
}
