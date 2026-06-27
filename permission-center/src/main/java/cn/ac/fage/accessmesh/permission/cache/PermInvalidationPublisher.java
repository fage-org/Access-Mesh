package cn.ac.fage.accessmesh.permission.cache;

import cn.ac.fage.accessmesh.perm.common.event.PermInvalidateEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 权限失效广播发布端
 * <p>
 * 通过 Redis pub/sub topic {@code perm:invalidate} 广播 {@link PermInvalidateEvent}。
 * Gateway 订阅器（T-PERM-006 范围）接收后 evict 本地 INTERFACE_SNAPSHOT 缓存。
 * </p>
 * <p>
 * 失败兜底（v3.5 §7.2）：发布失败仅记录 WARN，不影响已提交事务；订阅端丢失事件时靠
 * TTL（30-60s）自然过期最终一致。
 * </p>
 */
@Component
public class PermInvalidationPublisher {

    private static final Logger log = LoggerFactory.getLogger(PermInvalidationPublisher.class);

    /** 广播 topic（IR-1.4：tenant_id 作为事件载荷分区，topic 全局共享） */
    public static final String TOPIC = "perm:invalidate";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public PermInvalidationPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 广播失效事件。tenantId 为 null 时跳过（防御性）。
     *
     * @param tenantId     租户ID
     * @param roleIds      受影响角色ID集合
     * @param userIds      受影响用户ID集合
     * @param serviceCodes 受影响服务编码集合（API mapping/资源/sync 变更触发，Gateway 据此清本地快照）
     */
    public void publish(Long tenantId, Set<Long> roleIds, Set<Long> userIds, Set<String> serviceCodes) {
        if (tenantId == null) {
            log.debug("Skip perm invalidation broadcast: tenantId is null");
            return;
        }
        try {
            String message = objectMapper.writeValueAsString(new PermInvalidateEvent(tenantId, roleIds, userIds, serviceCodes));
            redisTemplate.convertAndSend(TOPIC, message);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize PermInvalidateEvent (tenantId={}, roleIds={}, userIds={}, serviceCodes={}): {}",
                tenantId, roleIds, userIds, serviceCodes, e.getMessage());
        } catch (Exception e) {
            // 广播失败不抛异常，不影响已提交事务；订阅端靠 TTL 兜底
            log.warn("Failed to publish PermInvalidateEvent (tenantId={}, roleIds={}, userIds={}, serviceCodes={}): {}",
                tenantId, roleIds, userIds, serviceCodes, e.getMessage());
        }
    }
}
