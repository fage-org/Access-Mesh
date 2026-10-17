package cn.ac.fage.accessmesh.access.audit.task;
import cn.ac.fage.accessmesh.access.audit.service.AuditRetentionAppService;
import cn.ac.fage.accessmesh.access.platform.service.JobAppService;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
@Component
public class AuditRetentionRegistrar {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuditRetentionRegistrar.class);
    private final AuditRetentionAppService retention;
    private final JobAppService jobs;
    public AuditRetentionRegistrar(AuditRetentionAppService retention, JobAppService jobs) { this.retention = retention; this.jobs = jobs; }
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelayString = "PT60S", initialDelayString = "PT60S")
    public void reconcile() {
        try {
            retention.synchronizeJobs();
            jobs.reconcileScheduledJobs();
        } catch (RuntimeException exception) {
            // Runtime handlers also enforce the startup policy; retry registration on the next pass.
            log.error("Audit retention job registration failed; next reconcile will retry", exception);
        }
    }
}
