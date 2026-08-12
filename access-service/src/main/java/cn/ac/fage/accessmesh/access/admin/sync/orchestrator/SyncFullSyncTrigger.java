package cn.ac.fage.accessmesh.access.admin.sync.orchestrator;

import cn.ac.fage.accessmesh.common.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 全量校准 cron 触发器（S6）。
 * <p>
 * 启用条件：{@code accessmesh.sync.full-sync.cron-enabled=true}。
 * 周期触发 {@link SyncFullSyncOrchestrator#startFullSyncRun}，若已有活跃批次则
 * 静默跳过（业务异常被忽略）。
 * </p>
 */
@Component
@ConditionalOnProperty(name = "accessmesh.sync.full-sync.cron-enabled", havingValue = "true")
@EnableConfigurationProperties(SyncFullSyncTriggerProperties.class)
public class SyncFullSyncTrigger {

    private static final Logger log = LoggerFactory.getLogger(SyncFullSyncTrigger.class);

    /** 默认租户（单租户场景固定 1L）。 */
    private static final long DEFAULT_TENANT_ID = 1L;
    private static final String SOURCE_SERVICE = "admin-service";

    private final SyncFullSyncOrchestrator orchestrator;

    public SyncFullSyncTrigger(SyncFullSyncOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * 周期任务入口；cron 表达式由 {@link SyncFullSyncTriggerProperties#cron()} 提供。
     */
    @Scheduled(cron = "${accessmesh.sync.full-sync.cron:0 0 4 * * ?}")
    public void runScheduled() {
        try {
            String batchKey = orchestrator.startFullSyncRun(DEFAULT_TENANT_ID, SOURCE_SERVICE, "cron");
            log.info("SyncFullSyncTrigger started batchKey={}", batchKey);
        } catch (BizException e) {
            log.info("SyncFullSyncTrigger skipped: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("SyncFullSyncTrigger run failed", e);
        }
    }
}
