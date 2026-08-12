package cn.ac.fage.accessmesh.access.admin.sync.scheduler;

import cn.ac.fage.accessmesh.access.admin.entity.SysSyncTask;
import cn.ac.fage.accessmesh.access.admin.service.SyncTaskDomainService;
import cn.ac.fage.accessmesh.access.admin.sync.handler.SyncTaskExecutionResult;
import cn.ac.fage.accessmesh.access.admin.sync.handler.SyncTaskHandler;
import cn.ac.fage.accessmesh.access.admin.sync.handler.SyncTaskHandlerRegistry;
import cn.ac.fage.accessmesh.access.admin.sync.model.SyncTaskClaimTimeouts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 {@link SyncTaskScheduler} 把 {@link SyncTaskExecutionResult} 路由到正确的 mark 方法，
 * 并按 {@link SyncSchedulerProperties} 计算退避。
 */
@ExtendWith(MockitoExtension.class)
class SyncTaskSchedulerTest {

    @Mock
    private SyncTaskDomainService domainService;

    @Mock
    private SyncTaskHandlerRegistry registry;

    @Mock
    private SyncTaskHandler handler;

    private SyncSchedulerProperties properties;
    private SyncTaskScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties = new SyncSchedulerProperties();
        scheduler = new SyncTaskScheduler(domainService, registry, properties);
    }

    private SysSyncTask task(int retryCount, int maxRetries) {
        SysSyncTask t = new SysSyncTask();
        t.setId(7L);
        t.setSyncAction("PERM_ABSTRACT_USER_SYNC");
        t.setRetryCount(retryCount);
        t.setMaxRetries(maxRetries);
        return t;
    }

    @Test
    void successOutcome_callsMarkSuccess() {
        SysSyncTask t = task(0, 5);
        when(registry.resolve("PERM_ABSTRACT_USER_SYNC")).thenReturn(handler);
        when(handler.execute(t)).thenReturn(SyncTaskExecutionResult.success());

        scheduler.processOne(t);

        verify(domainService).markSuccess(eq(7L), eq(scheduler.getWorkerId()));
        verify(domainService, never()).markFailed(any(), any(), any(), any());
        verify(domainService, never()).markRetryable(any(), any(), any(), any());
    }

    @Test
    void staleOutcome_callsMarkSuccess() {
        SysSyncTask t = task(0, 5);
        when(registry.resolve(anyString())).thenReturn(handler);
        when(handler.execute(t)).thenReturn(SyncTaskExecutionResult.stale("SYNC_VERSION_STALE"));

        scheduler.processOne(t);

        verify(domainService).markSuccess(eq(7L), anyString());
    }

    @Test
    void nonRetryableOutcome_callsMarkFailed() {
        SysSyncTask t = task(0, 5);
        when(registry.resolve(anyString())).thenReturn(handler);
        when(handler.execute(t)).thenReturn(SyncTaskExecutionResult.nonRetryable("BAD_PAYLOAD"));

        scheduler.processOne(t);

        verify(domainService).markFailed(eq(7L), anyString(),
            eq(SyncTaskExecutionResult.NON_RETRYABLE), eq("BAD_PAYLOAD"));
    }

    @Test
    void securityDeniedOutcome_callsMarkFailed() {
        SysSyncTask t = task(0, 5);
        when(registry.resolve(anyString())).thenReturn(handler);
        when(handler.execute(t)).thenReturn(SyncTaskExecutionResult.securityDenied("SIGN_FAIL"));

        scheduler.processOne(t);

        verify(domainService).markFailed(eq(7L), anyString(),
            eq(SyncTaskExecutionResult.SECURITY_DENIED), eq("SIGN_FAIL"));
    }

    @Test
    void retryableOutcome_callsMarkRetryable_withExponentialBackoff() {
        SysSyncTask t = task(2, 16);  // base=5, retry=2 -> 5 * 2^2 = 20s
        when(registry.resolve(anyString())).thenReturn(handler);
        when(handler.execute(t)).thenReturn(SyncTaskExecutionResult.retryable("io"));

        scheduler.processOne(t);

        ArgumentCaptor<Duration> backoff = ArgumentCaptor.forClass(Duration.class);
        verify(domainService).markRetryable(eq(7L), anyString(), anyString(), backoff.capture());
        assertThat(backoff.getValue().getSeconds()).isEqualTo(20);
    }

    @Test
    void retryableOutcome_capsAtMaxSeconds() {
        SysSyncTask t = task(20, 30);  // base=5, retry=20 -> would be huge, caps at 1800
        when(registry.resolve(anyString())).thenReturn(handler);
        when(handler.execute(t)).thenReturn(SyncTaskExecutionResult.retryable("x"));

        scheduler.processOne(t);

        ArgumentCaptor<Duration> backoff = ArgumentCaptor.forClass(Duration.class);
        verify(domainService).markRetryable(any(), anyString(), anyString(), backoff.capture());
        assertThat(backoff.getValue().getSeconds()).isEqualTo(1800);
    }

    @Test
    void dependencyMissingOutcome_usesDependencyBackoff() {
        SysSyncTask t = task(1, 16);  // base=5, retry=1 -> 10s, cap=30
        when(registry.resolve(anyString())).thenReturn(handler);
        when(handler.execute(t)).thenReturn(SyncTaskExecutionResult.dependencyMissing("dep"));

        scheduler.processOne(t);

        ArgumentCaptor<Duration> backoff = ArgumentCaptor.forClass(Duration.class);
        verify(domainService).markRetryable(any(), anyString(), anyString(), backoff.capture());
        assertThat(backoff.getValue().getSeconds()).isEqualTo(10);
    }

    @Test
    void dependencyMissingOutcome_capsAt30Seconds() {
        SysSyncTask t = task(10, 16);  // would be 5 * 1024 = 5120, caps at 30
        when(registry.resolve(anyString())).thenReturn(handler);
        when(handler.execute(t)).thenReturn(SyncTaskExecutionResult.dependencyMissing("dep"));

        scheduler.processOne(t);

        ArgumentCaptor<Duration> backoff = ArgumentCaptor.forClass(Duration.class);
        verify(domainService).markRetryable(any(), anyString(), anyString(), backoff.capture());
        assertThat(backoff.getValue().getSeconds()).isEqualTo(30);
    }

    @Test
    void retryableButOverMaxRetries_marksFailed() {
        SysSyncTask t = task(5, 5);  // already at max; next would exceed
        when(registry.resolve(anyString())).thenReturn(handler);
        when(handler.execute(t)).thenReturn(SyncTaskExecutionResult.retryable("io"));

        scheduler.processOne(t);

        verify(domainService).markFailed(eq(7L), anyString(),
            eq(SyncTaskExecutionResult.NON_RETRYABLE), org.mockito.ArgumentMatchers.contains("MAX_RETRIES_EXCEEDED"));
        verify(domainService, never()).markRetryable(any(), any(), any(), any());
    }

    @Test
    void handlerThrowsException_treatedAsRetryable() {
        SysSyncTask t = task(0, 5);
        when(registry.resolve(anyString())).thenReturn(handler);
        when(handler.execute(t)).thenThrow(new RuntimeException("boom"));

        scheduler.processOne(t);

        verify(domainService).markRetryable(eq(7L), anyString(), anyString(), any(Duration.class));
    }

    @Test
    void tick_processesEachClaimedTask() {
        SysSyncTask t1 = task(0, 5);
        t1.setId(11L);
        SysSyncTask t2 = task(0, 5);
        t2.setId(22L);
        when(domainService.claimDueTasks(eq(properties.getBatchSize()), anyString(), any(SyncTaskClaimTimeouts.class)))
            .thenReturn(java.util.List.of(t1, t2));
        when(registry.resolve(anyString())).thenReturn(handler);
        when(handler.execute(any())).thenReturn(SyncTaskExecutionResult.success());

        scheduler.tick();

        verify(domainService).markSuccess(eq(11L), anyString());
        verify(domainService).markSuccess(eq(22L), anyString());
        verify(handler, times(2)).execute(any());
    }

    @Test
    void tick_swallowsClaimException() {
        when(domainService.claimDueTasks(anyInt(), anyString(), any(SyncTaskClaimTimeouts.class)))
            .thenThrow(new RuntimeException("db down"));

        // 不应抛出异常
        scheduler.tick();

        verify(handler, never()).execute(any());
    }

    @Test
    void tick_passesClaimTimeoutPolicyIncludingFullSync() {
        properties.getStaleLockTimeout().put("PERM_USER_ROLE_SYNC", 120L);
        when(domainService.claimDueTasks(anyInt(), anyString(), any(SyncTaskClaimTimeouts.class)))
            .thenReturn(java.util.List.of());

        scheduler.tick();

        ArgumentCaptor<SyncTaskClaimTimeouts> timeoutCaptor = ArgumentCaptor.forClass(SyncTaskClaimTimeouts.class);
        verify(domainService).claimDueTasks(eq(properties.getBatchSize()), anyString(), timeoutCaptor.capture());
        SyncTaskClaimTimeouts timeouts = timeoutCaptor.getValue();
        assertThat(timeouts.defaultTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(timeouts.fullSyncTimeout()).isEqualTo(Duration.ofSeconds(300));
        assertThat(timeouts.timeoutForAction("PERM_USER_ROLE_SYNC")).isEqualTo(Duration.ofSeconds(120));
    }
}
