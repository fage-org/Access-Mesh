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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S6 阶段闸门测试 — 验证调度器在 SQL 层（mapper claimDueTasks）已经实施阶段闸门时，
 * 不会跨 phase 同时认领任务，并且失败传播时同 batchKey 后续 phase 不再被认领。
 *
 * <p>SQL 层的具体过滤行为由 mapper 实现承担；本测试用 mock {@link SyncTaskDomainService#claimDueTasks}
 * 模拟 SQL 返回结果，验证调度器对返回结果的处理是否正确（不会越权 claim）。</p>
 */
@ExtendWith(MockitoExtension.class)
class SyncFullSyncSchedulerGatingTest {

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

    private SysSyncTask phaseTask(long id, String phase, String action) {
        SysSyncTask t = new SysSyncTask();
        t.setId(id);
        t.setSyncAction(action);
        t.setPhase(phase);
        t.setBatchKey("sourceService=admin-service&runId=test-batch");
        t.setRetryCount(0);
        t.setMaxRetries(3);
        return t;
    }

    @Test
    void shouldOnlyProcessReturnedPhaseTasks_whenSqlGatingFiltersByPhase() {
        // 模拟：SQL 阶段闸门只返回当前 phase（USER_SUBJECT）任务，下游 phase 不应出现。
        SysSyncTask userSubject = phaseTask(1L, "USER_SUBJECT", "PERM_ABSTRACT_USER_SYNC");
        when(domainService.claimDueTasks(anyInt(), anyString(), any(SyncTaskClaimTimeouts.class)))
            .thenReturn(List.of(userSubject));
        when(registry.resolve(anyString())).thenReturn(handler);
        when(handler.execute(any())).thenReturn(SyncTaskExecutionResult.success());

        scheduler.tick();

        verify(handler, times(1)).execute(userSubject);
        verify(domainService).markSuccess(any(), anyString());
    }

    @Test
    void shouldNotProcessAnyTasks_whenSqlGatingReturnsEmptyDueToFailedPhase() {
        // 模拟：同 batchKey 已有 FAILED 任务时，SQL 闸门返回空 — 调度器整体不处理。
        when(domainService.claimDueTasks(anyInt(), anyString(), any(SyncTaskClaimTimeouts.class)))
            .thenReturn(Collections.emptyList());

        scheduler.tick();

        verify(handler, never()).execute(any());
        verify(domainService, never()).markSuccess(any(), anyString());
        verify(domainService, never()).markFailed(any(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldProcessSinglePhaseOnly_whenMultipleBatchesHaveDifferentPhases() {
        // 模拟：claimDueTasks 仅返回符合阶段闸门的两条同 phase 任务（来自不同 batch）。
        SysSyncTask t1 = phaseTask(10L, "ORG_RESOURCE", "PERM_RESOURCE_ENTITY_SYNC");
        SysSyncTask t2 = phaseTask(11L, "ORG_RESOURCE", "PERM_RESOURCE_ENTITY_SYNC");
        when(domainService.claimDueTasks(anyInt(), anyString(), any(SyncTaskClaimTimeouts.class)))
            .thenReturn(List.of(t1, t2));
        when(registry.resolve(anyString())).thenReturn(handler);
        when(handler.execute(any())).thenReturn(SyncTaskExecutionResult.success());

        scheduler.tick();

        verify(handler, times(2)).execute(any());
        // 全部当前 phase，全部 SUCCESS；不会有越权调用
        verify(domainService, times(2)).markSuccess(any(), anyString());
    }

    @Test
    void shouldHandleException_whenTickClaimsThrows() {
        // tick 顶层 try/catch 兜底 — 即使 claim 抛异常也不应中断调度器。
        when(domainService.claimDueTasks(anyInt(), anyString(), any(SyncTaskClaimTimeouts.class)))
            .thenThrow(new RuntimeException("db down"));

        scheduler.tick();

        verify(handler, never()).execute(any());
    }

    @Test
    void shouldCarryPhaseAndBatchKey_inClaimedTasks() {
        // 验证调度器透传 phase/batchKey（不会丢失或修改字段）。
        SysSyncTask task = phaseTask(99L, "USER_ROLE", "PERM_USER_ROLE_SYNC");
        when(domainService.claimDueTasks(anyInt(), anyString(), any(SyncTaskClaimTimeouts.class)))
            .thenReturn(List.of(task));
        when(registry.resolve(anyString())).thenReturn(handler);
        when(handler.execute(any())).thenReturn(SyncTaskExecutionResult.success());

        scheduler.tick();

        // 通过 ArgumentCaptor 不必要 — 直接验证 handler 收到带 phase 与 batchKey 的同一对象
        verify(handler).execute(task);
        assertThat(task.getPhase()).isEqualTo("USER_ROLE");
        assertThat(task.getBatchKey()).startsWith("sourceService=admin-service&runId=");
    }
}
