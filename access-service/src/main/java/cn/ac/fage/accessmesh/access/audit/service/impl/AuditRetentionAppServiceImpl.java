package cn.ac.fage.accessmesh.access.audit.service.impl;
import cn.ac.fage.accessmesh.access.audit.config.AuditLogSettings;
import cn.ac.fage.accessmesh.access.audit.service.AuditRetentionAppService;
import cn.ac.fage.accessmesh.access.audit.service.domain.AuditRetentionDomainService;
import cn.ac.fage.accessmesh.access.audit.service.domain.AuditRetentionDomainService.Kind;
import cn.ac.fage.accessmesh.access.infrastructure.task.TaskExecutionContext;
import cn.ac.fage.accessmesh.access.infrastructure.task.TaskExecutionDomainService;
import cn.ac.fage.accessmesh.access.tenant.service.domain.TenantDomainService;
import cn.ac.fage.accessmesh.access.tenant.service.TenantAccessGuard;
import cn.ac.fage.accessmesh.access.platform.service.domain.DeploymentJobDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
@Service
public class AuditRetentionAppServiceImpl implements AuditRetentionAppService {
    private final AuditLogSettings settings;
    private final TenantDomainService tenants;
    private final TenantAccessGuard tenantAccess;
    private final DeploymentJobDomainService jobs;
    private final TaskExecutionDomainService executions;
    private final AuditRetentionDomainService retention;
    public AuditRetentionAppServiceImpl(AuditLogSettings settings, TenantDomainService tenants, TenantAccessGuard tenantAccess,
        DeploymentJobDomainService jobs, TaskExecutionDomainService executions, AuditRetentionDomainService retention) {
        this.settings = settings; this.tenants = tenants; this.tenantAccess = tenantAccess;
        this.jobs = jobs; this.executions = executions; this.retention = retention;
    }
    public void synchronizeJobs() {
        if (!settings.cleanupEnabled()) { jobs.disableAuditRetention(); return; }
        long after = 0;
        while (true) {
            var page = tenants.scan(after, 200);
            if (page.isEmpty()) return;
            jobs.synchronizeAuditRetention(page.stream().map(t -> t.getId()).toList(), settings.cron());
            after = page.getLast().getId();
        }
    }
    @Transactional(rollbackFor = Exception.class, timeout = 20)
    public int deleteBatch(TaskExecutionContext context, Kind kind, LocalDateTime cutoff) {
        if (!settings.cleanupEnabled()) return 0;
        tenants.lock(context.tenantId());
        var tenant = tenants.findById(context.tenantId());
        if (tenant == null || !Integer.valueOf(1).equals(tenant.getStatus())) return 0;
        tenantAccess.requireEnabled(context.tenantId());
        executions.lockActiveAttempt(context);
        return retention.deleteBatch(context.tenantId(), kind, cutoff);
    }
}
