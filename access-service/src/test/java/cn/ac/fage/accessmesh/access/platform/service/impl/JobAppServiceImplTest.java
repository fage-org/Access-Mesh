package cn.ac.fage.accessmesh.access.platform.service.impl;

import cn.ac.fage.accessmesh.access.platform.controller.JobController;
import cn.ac.fage.accessmesh.access.platform.entity.SysJob;
import cn.ac.fage.accessmesh.access.platform.mapper.SysJobLogMapper;
import cn.ac.fage.accessmesh.access.platform.mapper.SysJobMapper;
import cn.ac.fage.accessmesh.access.platform.dto.req.JobLogPageReq;
import cn.ac.fage.accessmesh.access.platform.dto.req.JobUpdateReq;
import cn.ac.fage.accessmesh.access.engine.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.engine.constant.OperationCode;
import cn.ac.fage.accessmesh.access.type.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.access.infrastructure.task.JobInvokeDomainService;
import cn.ac.fage.accessmesh.access.platform.service.domain.JobLogDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.task.TaskExecutionDomainService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link JobAppServiceImpl} 执行编排单元测试（T-ACCESS-009 AI 复评修复）。
 * <p>
 * 覆盖：执行器拒绝时停续租并写回 FAILED（拒绝必须以 TaskRejectedException
 * 通知提交方，否则续租永续、任务永久 RUNNING）；计划触发时任务已删除/停用
 * 跳过执行；多实例配置对账的 diff 重调度（cron 未变不重建 / cron 变更替换）、
 * 停用取消，以及加载失败时保持现有调度不变。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class JobAppServiceImplTest {

    private static ValidatorFactory validatorFactory;

    @BeforeAll
    static void initValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidator() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    private final Validator validator = validatorFactory.getValidator();

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

    private JobAppServiceImpl newService() {
        return new JobAppServiceImpl(jobMapper, jobLogMapper, taskScheduler, leaseRenewalScheduler,
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

        JobAppServiceImpl service = newService();
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

        JobAppServiceImpl service = newService();
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

        JobAppServiceImpl service = newService();
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

        JobAppServiceImpl service = newService();
        service.reconcileScheduledJobs();
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));

        // 批量加载失败 = 全部状态未知：不做任何调度变更（含删除判定）
        when(jobMapper.selectAllEnabledJobs()).thenThrow(new RuntimeException("db down"));
        service.reconcileScheduledJobs();

        verify(future1, never()).cancel(anyBoolean());
        verify(future2, never()).cancel(anyBoolean());
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));
    }

    /**
     * T-ACCESS-054：三个读端点（detail/page/log-page）此前零权限门禁——任意租户登录用户
     * 可翻任务配置（invokeTarget/cron）与执行日志。门禁=ADMIN_JOB:VIEW 类型级；
     * 旧实现下本用例红（checkTypeLevel 从未被调用）。
     */
    @Test
    void readEndpointsShouldGateOnAdminJobView() {
        TenantContextHolder.setTenantId(TENANT_ID);
        try {
            when(jobMapper.selectValidById(TENANT_ID, 1L)).thenReturn(enabledJob(1L, TENANT_ID, "0 0 0 * * *"));
            when(jobMapper.countJobsByCondition(TENANT_ID, null)).thenReturn(0L);
            when(jobLogMapper.countJobLogsByCondition(TENANT_ID, null)).thenReturn(0L);

            newService().getJob(1L);
            newService().pageJobs(new PageReq(1, 20, null), null);
            newService().pageJobLogs(new JobLogPageReq(1, 20, null, null), null);

            verify(permissionValidator, times(3))
                .checkTypeLevel(ResourceTypeCode.ADMIN_JOB, OperationCode.VIEW);
        } finally {
            // clear() 而非 setTenantId(null)：后者在已有上下文时仅置空租户、保留 TASK 壳，
            // 会泄漏到同 JVM 后续测试的上下文断言（CI SecurityMatrixIT 防泄漏断言实证）
            TenantContextHolder.clear();
        }
    }

    /**
     * T-ACCESS-054 双轨评审处置（P3-2）：写端点 update/toggle 先门禁后存在——无权限时
     * 403 先于任何存在性查询（不泄露任务 id 存在性；与 trigger 及 T-ADMIN-029 公告族同形态）。
     * 旧实现（先查存在后判权）下无权限即走 selectValidById，本用例红。
     */
    @Test
    void writeEndpointsShouldDenyBeforeExistenceCheck() {
        doThrow(new SecurityException("Permission denied"))
            .when(permissionValidator).checkInstanceLevel(any(), any(), any());
        TenantContextHolder.setTenantId(TENANT_ID);
        try {
            assertThatThrownBy(() -> newService().updateJob(new JobUpdateReq(1L, null, null, null, null, null, null, null)))
                .isInstanceOf(SecurityException.class);
            assertThatThrownBy(() -> newService().toggleJobStatus(1L, 0))
                .isInstanceOf(SecurityException.class);

            verify(jobMapper, never()).selectValidById(anyLong(), anyLong());
        } finally {
            TenantContextHolder.clear();
        }
    }

    /**
     * T-ACCESS-054：无 ADMIN_JOB:VIEW 拒绝发生在任何读取之前（fail-closed，不泄露任务存在性）。
     */
    @Test
    void readEndpointsShouldDenyWithoutAdminJobView() {
        doThrow(new SecurityException("Permission denied: VIEW on ADMIN_JOB"))
            .when(permissionValidator).checkTypeLevel(any(), any());
        TenantContextHolder.setTenantId(TENANT_ID);
        try {
            assertThatThrownBy(() -> newService().getJob(1L)).isInstanceOf(SecurityException.class);
            assertThatThrownBy(() -> newService().pageJobs(new PageReq(1, 20, null), null))
                .isInstanceOf(SecurityException.class);
            assertThatThrownBy(() -> newService().pageJobLogs(new JobLogPageReq(1, 20, null, null), null))
                .isInstanceOf(SecurityException.class);

            verify(jobMapper, never()).selectValidById(anyLong(), anyLong());
            verify(jobMapper, never()).countJobsByCondition(anyLong(), any());
            verify(jobMapper, never()).selectJobsByCondition(anyLong(), any(), anyInt(), anyInt());
            verify(jobLogMapper, never()).countJobLogsByCondition(anyLong(), any());
            verify(jobLogMapper, never()).selectJobLogsByCondition(anyLong(), any(), anyInt(), anyInt());
        } finally {
            TenantContextHolder.clear();
        }
    }

    /**
     * T-ACCESS-054（claude 外评 P3 处置）：toggle 是 job 族唯一无校验请求体——门禁移序
     * （先门禁后存在）后 id 为 null 会在 id.toString() 处 NPE 500（原实现 null 等值查库
     * 不命中走 JOB_NOT_FOUND 业务拒绝）；@NotNull+@Valid 将拒绝前移到 400。
     * 旧实现（ToggleJobReq 无注解）下本用例红。
     */
    @Test
    void toggleJobReqShouldRejectNullIdAndStatus() {
        assertThat(validator.validate(new JobController.ToggleJobReq(null, 1))).hasSize(1);
        assertThat(validator.validate(new JobController.ToggleJobReq(1L, null))).hasSize(1);
        assertThat(validator.validate(new JobController.ToggleJobReq(1L, 0))).isEmpty();
    }
}
