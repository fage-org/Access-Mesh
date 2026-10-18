package cn.ac.fage.accessmesh.access.infrastructure.task;

import cn.ac.fage.accessmesh.access.audit.service.AuditRetentionAppService;
import cn.ac.fage.accessmesh.access.platform.service.JobAppService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.time.Duration;
import static org.mockito.Mockito.*;

class JobScheduleReconcilerTest {
    private final AuditRetentionAppService retention = mock(AuditRetentionAppService.class);
    private final JobAppService jobs = mock(JobAppService.class);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withBean(AuditRetentionAppService.class, () -> retention)
        .withBean(JobAppService.class, () -> jobs)
        .withUserConfiguration(JobScheduleReconciler.class);

    @Test void shouldSynchronizeDefinitionsBeforeSchedulingAtStartup() {
        runner.run(context -> {
            context.publishEvent(new ApplicationReadyEvent(new SpringApplication(), new String[0],
                context.getSourceApplicationContext(), Duration.ZERO));
            var ordered = inOrder(retention, jobs);
            ordered.verify(retention).synchronizeJobs();
            ordered.verify(jobs).reconcileScheduledJobs();
            verifyNoMoreInteractions(retention, jobs);
        });
    }

    @Test void shouldKeepOrdinaryReconciliationAndRetryAfterDefinitionFailure() {
        doThrow(new IllegalStateException("definition unavailable")).doNothing().when(retention).synchronizeJobs();
        runner.run(context -> {
            var reconciler = context.getBean(JobScheduleReconciler.class);
            reconciler.reconcile();
            reconciler.reconcile();
            var ordered = inOrder(retention, jobs);
            ordered.verify(retention).synchronizeJobs();
            ordered.verify(jobs).reconcileScheduledJobs();
            ordered.verify(retention).synchronizeJobs();
            ordered.verify(jobs).reconcileScheduledJobs();
            verifyNoMoreInteractions(retention, jobs);
        });
    }
}
