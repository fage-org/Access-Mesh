package cn.ac.fage.accessmesh.admin.sync.orchestrator;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 全量校准触发器配置（S6）。
 *
 * @param cronEnabled    是否启用 cron 触发
 * @param cron           cron 表达式（默认每天 4 点）
 * @param autoOnStartup  是否在应用启动时自动触发一次
 */
@ConfigurationProperties(prefix = "accessmesh.sync.full-sync")
public record SyncFullSyncTriggerProperties(
    boolean cronEnabled,
    String cron,
    boolean autoOnStartup
) {

    public SyncFullSyncTriggerProperties {
        if (cron == null || cron.isBlank()) {
            cron = "0 0 4 * * ?";
        }
    }
}
