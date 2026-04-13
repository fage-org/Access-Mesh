package org.dromara.gateway.authz;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.system.api.event.PermissionVersionChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 权限版本变更事件监听器
 *
 * 监听版本变更事件，刷新本地缓存
 *
 * @author RuoYi-Cloud-Plus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionVersionEventListener {

    private final PermissionSnapshotCache permissionSnapshotCache;

    /**
     * 处理版本变更事件
     *
     * @param event 版本变更事件
     */
    @EventListener
    public void onVersionChange(PermissionVersionChangedEvent event) {
        log.info("Received permission version change event: tenant={}, version={}",
            event.getTenantId(), event.getVersionNo());

        try {
            // 刷新指定租户的缓存
            permissionSnapshotCache.invalidateByTenant(event.getTenantId());

            log.info("Permission cache invalidated for tenant: {}", event.getTenantId());
        } catch (Exception e) {
            log.error("Failed to handle permission version change event: {}", e.getMessage(), e);
        }
    }
}
