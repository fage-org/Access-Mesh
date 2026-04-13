package org.dromara.permission.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.system.api.event.PermissionVersionChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 权限版本变更 MQ 发布器
 *
 * 通过 Spring 事件机制发布版本变更事件，支持跨服务传播
 *
 * @author RuoYi-Cloud-Plus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionVersionMqPublisher {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 发布版本变更事件
     *
     * @param event 版本变更事件
     */
    public void publishVersionChange(PermissionVersionChangedEvent event) {
        try {
            log.info("Publishing permission version change event: tenant={}, version={}",
                event.getTenantId(), event.getVersionNo());

            // 使用 Spring 事件机制发布
            // 配合 Spring Cloud Bus 可通过 RabbitMQ/Kafka 传播到其他服务
            eventPublisher.publishEvent(event);

            log.debug("Permission version change event published successfully");
        } catch (Exception e) {
            log.error("Failed to publish permission version change event: {}", e.getMessage(), e);
        }
    }

    /**
     * 发布版本变更事件（简化版）
     *
     * @param tenantId    租户ID
     * @param versionNo   版本号
     * @param versionToken 版本标识
     */
    public void publishVersionChange(String tenantId, Long versionNo, String versionToken) {
        PermissionVersionChangedEvent event = PermissionVersionChangedEvent.of(tenantId, versionNo, versionToken);
        publishVersionChange(event);
    }
}
