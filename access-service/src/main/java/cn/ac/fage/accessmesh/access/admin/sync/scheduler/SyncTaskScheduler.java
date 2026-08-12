package cn.ac.fage.accessmesh.access.admin.sync.scheduler;

import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.admin.entity.SysSyncTask;
import cn.ac.fage.accessmesh.access.admin.service.SyncTaskDomainService;
import cn.ac.fage.accessmesh.access.admin.sync.SyncTaskBuilder;
import cn.ac.fage.accessmesh.access.admin.sync.handler.SyncTaskExecutionResult;
import cn.ac.fage.accessmesh.access.admin.sync.handler.SyncTaskHandler;
import cn.ac.fage.accessmesh.access.admin.sync.handler.SyncTaskHandlerRegistry;
import cn.ac.fage.accessmesh.access.admin.sync.model.SyncTaskClaimTimeouts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sync task scheduler.
 */
@Component
@ConditionalOnProperty(name = "accessmesh.sync.scheduler.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SyncSchedulerProperties.class)
public class SyncTaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncTaskScheduler.class);

    private final SyncTaskDomainService syncTaskDomainService;
    private final SyncTaskHandlerRegistry handlerRegistry;
    private final SyncSchedulerProperties properties;
    private final String workerId;

    public SyncTaskScheduler(SyncTaskDomainService syncTaskDomainService,
                             SyncTaskHandlerRegistry handlerRegistry,
                             SyncSchedulerProperties properties) {
        this.syncTaskDomainService = syncTaskDomainService;
        this.handlerRegistry = handlerRegistry;
        this.properties = properties;
        this.workerId = computeWorkerId();
    }

    private static String computeWorkerId() {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostname = "unknown";
        }
        return hostname + "-" + UUID.randomUUID();
    }

    public String getWorkerId() {
        return workerId;
    }

    @Scheduled(fixedDelayString = "${accessmesh.sync.scheduler.fixed-delay-ms:5000}")
    public void tick() {
        try {
            List<SysSyncTask> tasks = syncTaskDomainService.claimDueTasks(
                properties.getBatchSize(),
                workerId,
                buildClaimTimeouts()
            );
            if (tasks == null || tasks.isEmpty()) {
                return;
            }
            log.info("SyncTaskScheduler tick start: workerId={}, claimed={}", workerId, tasks.size());
            for (SysSyncTask task : tasks) {
                processOne(task);
            }
            log.info("SyncTaskScheduler tick end: workerId={}, processed={}", workerId, tasks.size());
        } catch (Exception e) {
            log.error("SyncTaskScheduler tick failed", e);
        }
    }

    private SyncTaskClaimTimeouts buildClaimTimeouts() {
        Map<String, Duration> actionTimeouts = new LinkedHashMap<>();
        actionTimeouts.put(SyncTaskBuilder.ACTION_ABSTRACT_USER_SYNC,
            properties.getTimeoutFor(SyncTaskBuilder.ACTION_ABSTRACT_USER_SYNC));
        actionTimeouts.put(SyncTaskBuilder.ACTION_ABSTRACT_ROLE_SYNC,
            properties.getTimeoutFor(SyncTaskBuilder.ACTION_ABSTRACT_ROLE_SYNC));
        actionTimeouts.put(SyncTaskBuilder.ACTION_USER_ROLE_SYNC,
            properties.getTimeoutFor(SyncTaskBuilder.ACTION_USER_ROLE_SYNC));
        actionTimeouts.put(SyncTaskBuilder.ACTION_RESOURCE_ENTITY_SYNC,
            properties.getTimeoutFor(SyncTaskBuilder.ACTION_RESOURCE_ENTITY_SYNC));
        return new SyncTaskClaimTimeouts(
            properties.getTimeoutFor(SyncSchedulerProperties.DEFAULT_KEY),
            properties.getTimeoutFor(SyncSchedulerProperties.FULL_SYNC_KEY),
            actionTimeouts
        );
    }

    public void processOne(SysSyncTask task) {
        Long previousTenant = TenantContextHolder.getTenantId();
        TenantContextHolder.setTenantId(task.getTenantId());
        try {
            log.info("SyncTaskScheduler processOne start: taskId={}, tenantId={}, syncAction={}, batchKey={}, phase={}, retryCount={}, workerId={}",
                task.getId(), task.getTenantId(), task.getSyncAction(), task.getBatchKey(), task.getPhase(), task.getRetryCount(), workerId);
            SyncTaskExecutionResult result;
            try {
                SyncTaskHandler handler = handlerRegistry.resolve(task.getSyncAction());
                result = handler.execute(task);
                if (result == null) {
                    result = SyncTaskExecutionResult.retryable("NULL_RESULT");
                }
            } catch (Exception ex) {
                log.warn("handler threw, treat as RETRYABLE: taskId={}, tenantId={}, action={}, msg={}",
                    task.getId(), task.getTenantId(), task.getSyncAction(), ex.getMessage());
                result = SyncTaskExecutionResult.retryable(ex.getClass().getSimpleName() + ":" + ex.getMessage());
            }
            log.info("SyncTaskScheduler processOne end: taskId={}, tenantId={}, syncAction={}, batchKey={}, phase={}, outcome={}, reason={}",
                task.getId(), task.getTenantId(), task.getSyncAction(), task.getBatchKey(), task.getPhase(),
                result.outcome(), result.reason());
            applyOutcome(task, result);
        } finally {
            if (previousTenant != null) {
                TenantContextHolder.setTenantId(previousTenant);
            } else {
                TenantContextHolder.clear();
            }
        }
    }

    private void applyOutcome(SysSyncTask task, SyncTaskExecutionResult result) {
        String outcome = result.outcome();
        switch (outcome) {
            case SyncTaskExecutionResult.SUCCESS:
            case SyncTaskExecutionResult.STALE_VERSION:
                syncTaskDomainService.markSuccess(task.getId(), workerId);
                return;
            case SyncTaskExecutionResult.NON_RETRYABLE:
            case SyncTaskExecutionResult.SECURITY_DENIED:
                syncTaskDomainService.markFailed(task.getId(), workerId, outcome, result.reason());
                return;
            case SyncTaskExecutionResult.RETRYABLE:
            case SyncTaskExecutionResult.DEPENDENCY_MISSING:
                handleRetry(task, outcome, result.reason());
                return;
            default:
                log.warn("unknown outcome {} for taskId={}, treat as RETRYABLE", outcome, task.getId());
                handleRetry(task, SyncTaskExecutionResult.RETRYABLE, "UNKNOWN_OUTCOME:" + outcome);
        }
    }

    private void handleRetry(SysSyncTask task, String outcome, String reason) {
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        int maxRetries = task.getMaxRetries() == null ? 0 : task.getMaxRetries();
        if (retryCount + 1 > maxRetries) {
            syncTaskDomainService.markFailed(
                task.getId(), workerId,
                SyncTaskExecutionResult.NON_RETRYABLE,
                "MAX_RETRIES_EXCEEDED:" + reason
            );
            return;
        }
        Duration backoff = computeBackoff(outcome, retryCount);
        syncTaskDomainService.markRetryable(task.getId(), workerId, outcome + ":" + reason, backoff);
    }

    Duration computeBackoff(String outcome, int retryCount) {
        long base;
        long cap;
        if (SyncTaskExecutionResult.DEPENDENCY_MISSING.equals(outcome)) {
            base = properties.getDependencyMissingBaseSeconds();
            cap = properties.getDependencyMissingMaxSeconds();
        } else {
            base = properties.getRetryableBaseSeconds();
            cap = properties.getRetryableMaxSeconds();
        }
        int safeRetry = Math.max(0, retryCount);
        int shift = Math.min(safeRetry, 30);
        long seconds;
        try {
            seconds = Math.multiplyExact(base, 1L << shift);
        } catch (ArithmeticException e) {
            seconds = cap;
        }
        if (seconds > cap) {
            seconds = cap;
        }
        return Duration.ofSeconds(seconds);
    }
}
