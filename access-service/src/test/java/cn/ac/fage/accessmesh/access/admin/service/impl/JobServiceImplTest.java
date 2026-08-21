package cn.ac.fage.accessmesh.access.admin.service.impl;

import cn.ac.fage.accessmesh.access.admin.entity.SysJob;
import cn.ac.fage.accessmesh.access.admin.mapper.SysJobLogMapper;
import cn.ac.fage.accessmesh.access.admin.mapper.SysJobMapper;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.admin.service.domain.JobInvokeDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.JobLogDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.TaskExecutionDomainService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link JobServiceImpl} 执行编排单元测试（T-ACCESS-009 AI 复评修复）。
 * <p>
 * 覆盖：执行器拒绝时停续租并写回 FAILED（拒绝必须以 TaskRejectedException
 * 通知提交方，否则续租永续、任务永久 RUNNING）；计划触发时任务已删除/停用
 * 跳过执行；多实例配置对账的 diff 重调度（cron 未变不重建 / cron 变更替换）、
 * 停用取消，以及加载失败时保持现有调度不变。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class JobServiceImplTest {

    private static final Long TENANT_ID = 10L;
    private static final LocalDateTime SCHEDULED_TIME = LocalDateTime.of(2026, 8, 21, 12, 0, 0);
    private static final String EXECUTION_KEY = "job:1:20260821T120000";

    @Mock
    private SysJobMapper jobMapper;
    @Mock
    private SysJobLogMapper jobLogMapper;
    @Mock
    private TaskScheduler taskScheduler;
    @Mock
    private TaskScheduler leaseRenewalScheduler;
    @Mock
    private AdminPermissionValidator permissionValidator;
    @Mock
    private TaskExecutionDomainService taskExecutionDomainService;
    @Mock
    private JobInvokeDomainService jobInvokeDomainService;
    @Mock
    private JobLogDomainService jobLogDomainService;
    @Mock
    private TaskExecutor taskExecutor;

    private JobServiceImpl newService() {
        return new JobServiceImpl(jobMapper, jobLogMapper, taskScheduler, leaseRenewalScheduler,
            permissionValidator, taskExecutionDomainService,
            jobInvokeDomainService, jobLogDomainService, taskExecutor);
    }

    private SysJob enabledJob(Long id, Long tenantId, String cron) {
        SysJob job = new SysJob();
        job.setId(id);
        job.setTenantId(tenantId);
        job.setJobName("test-job");
        job.setInvokeTarget("someBean.run");
        job.setCronExpression(cron);
        job.setStatus(1);
        return job;
    }

    @Test
    void executorRejectionCancelsRenewalAndWritesFailed() {
        SysJob job = enabledJob(1L, TENANT_ID, "0 0 0 * * *");
        when(jobMapper.selectValidById(TENANT_ID, 1L)).thenReturn(job);
        when(taskExecutionDomainService.buildScheduledKey(1L, SCHEDULED_TIME)).thenReturn(EXECUTION_KEY);
        when(taskExecutionDomainService.tryClaim(eq(TENANT_ID), eq(EXECUTION_KEY), anyString()))
            .thenReturn(1);
        // 续租走专用调度器（与共享调度器隔离）
        ScheduledFuture<?> renewal = mock(ScheduledFuture.class);
        org.mockito.Mockito.doReturn(renewal).when(leaseRenewalScheduler)
            .scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class));
        org.mockito.Mockito.doThrow(new TaskRejectedException("pool full"))
            .when(taskExecutor).execute(any(Runnable.class));

        newService().executeJob(job, SCHEDULED_TIME);

        // 拒绝必须通知提交方：停续租 + 写回 FAILED（未超限时由接管扫描重试）
        verify(renewal).cancel(false);
        verify(taskExecutionDomainService).complete(eq(TENANT_ID), eq(EXECUTION_KEY),
            anyString(), eq(1), eq(false), contains("rejected"));
        verify(jobInvokeDomainService, never()).invoke(anyString(), any());
    }

    @Test
    void scheduledExecutionSkipsDeletedJob() {
        SysJob job = enabledJob(1L, TENANT_ID, "0 0 0 * * *");
        when(jobMapper.selectValidById(TENANT_ID, 1L)).thenReturn(null);

        newService().executeJob(job, SCHEDULED_TIME);

        verifyNoInteractions(taskExecutionDomainService);
        verifyNoInteractions(taskExecutor);
    }

    @Test
    void scheduledExecutionSkipsDisabledJob() {
        SysJob job = enabledJob(1L, TENANT_ID, "0 0 0 * * *");
        SysJob disabled = enabledJob(1L, TENANT_ID, "0 0 0 * * *");
        disabled.setStatus(0);
        when(jobMapper.selectValidById(TENANT_ID, 1L)).thenReturn(disabled);

        newService().executeJob(job, SCHEDULED_TIME);

        verifyNoInteractions(taskExecutionDomainService);
        verifyNoInteractions(taskExecutor);
    }

    @Test
    void reconcileSkipsRebuildWhenCronUnchanged() {
        SysJob job = enabledJob(1L, TENANT_ID, "0 0 0 * * *");
        when(jobMapper.selectAllEnabledJobs()).thenReturn(List.of(job));
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        org.mockito.Mockito.doReturn(future).when(taskScheduler)
            .schedule(any(Runnable.class), any(Trigger.class));

        JobServiceImpl service = newService();
        service.reconcileScheduledJobs();
        // cron 未变化的第二轮对账：不取消/重建调度（真 diff）
        service.reconcileScheduledJobs();

        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Trigger.class));
        verify(future, never()).cancel(anyBoolean());
    }

    @Test
    void reconcileReschedulesOnCronChange() {
        SysJob cronA = enabledJob(1L, TENANT_ID, "0 0 0 * * *");
        when(jobMapper.selectAllEnabledJobs()).thenReturn(List.of(cronA));
        ScheduledFuture<?> firstFuture = mock(ScheduledFuture.class);
        ScheduledFuture<?> secondFuture = mock(ScheduledFuture.class);
        org.mockito.Mockito.doReturn(firstFuture).doReturn(secondFuture).when(taskScheduler)
            .schedule(any(Runnable.class), any(Trigger.class));

        JobServiceImpl service = newService();
        service.reconcileScheduledJobs();

        // 数据库侧 cron 变更后对账：取消旧调度并按新 cron 重建（替换分支）
        SysJob cronB = enabledJob(1L, TENANT_ID, "0 0 12 * * *");
        when(jobMapper.selectAllEnabledJobs()).thenReturn(List.of(cronB));
        service.reconcileScheduledJobs();

        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));
        verify(firstFuture).cancel(false);
        verify(secondFuture, never()).cancel(anyBoolean());
    }

    @Test
    void reconcileCancelsRemovedJob() {
        SysJob job = enabledJob(1L, TENANT_ID, "0 0 0 * * *");
        when(jobMapper.selectAllEnabledJobs()).thenReturn(List.of(job));
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        org.mockito.Mockito.doReturn(future).when(taskScheduler)
            .schedule(any(Runnable.class), any(Trigger.class));

        JobServiceImpl service = newService();
        service.reconcileScheduledJobs();

        // 数据库侧停用/删除后对账：取消本实例旧调度
        when(jobMapper.selectAllEnabledJobs()).thenReturn(List.of());
        service.reconcileScheduledJobs();
        verify(future).cancel(false);
    }

    @Test
    void reconcileLoadFailureKeepsAllSchedules() {
        SysJob job1 = enabledJob(1L, TENANT_ID, "0 0 0 * * *");
        SysJob job2 = enabledJob(2L, 20L, "0 0 6 * * *");
        when(jobMapper.selectAllEnabledJobs()).thenReturn(List.of(job1, job2));
        ScheduledFuture<?> future1 = mock(ScheduledFuture.class);
        ScheduledFuture<?> future2 = mock(ScheduledFuture.class);
        org.mockito.Mockito.doReturn(future1).doReturn(future2).when(taskScheduler)
            .schedule(any(Runnable.class), any(Trigger.class));

        JobServiceImpl service = newService();
        service.reconcileScheduledJobs();
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));

        // 批量加载失败 = 全部状态未知：不做任何调度变更（含删除判定）
        when(jobMapper.selectAllEnabledJobs()).thenThrow(new RuntimeException("db down"));
        service.reconcileScheduledJobs();

        verify(future1, never()).cancel(anyBoolean());
        verify(future2, never()).cancel(anyBoolean());
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));
    }
}
