package cn.ac.fage.accessmesh.permission.cache;

import cn.ac.fage.accessmesh.perm.common.event.PermInvalidateEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * PermInvalidationPublisher 单元测试（T-PERM-006）
 */
class PermInvalidationPublisherTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PermInvalidationPublisher publisher = new PermInvalidationPublisher(redisTemplate, objectMapper);

    @Test
    void shouldPublishJsonMessageToPermInvalidateTopic() throws Exception {
        publisher.publish(1L, Set.of(200L), Set.of(10L), Set.of("admin-service"));

        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq(PermInvalidationPublisher.TOPIC), captor.capture());

        PermInvalidateEvent event = objectMapper.readValue(captor.getValue(), PermInvalidateEvent.class);
        assertThat(event.tenantId()).isEqualTo(1L);
        assertThat(event.roleIds()).containsExactly(200L);
        assertThat(event.userIds()).containsExactly(10L);
        assertThat(event.serviceCodes()).containsExactly("admin-service");
    }

    @Test
    void shouldSkipPublish_whenTenantIdIsNull() {
        publisher.publish(null, Set.of(200L), Set.of(10L), Set.of("admin-service"));

        verifyNoInteractions(redisTemplate);
    }
}
