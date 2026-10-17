package cn.ac.fage.accessmesh.access.audit.task;
import cn.ac.fage.accessmesh.access.audit.config.AuditLogSettings;
import cn.ac.fage.accessmesh.access.audit.service.AuditRetentionAppService;
import cn.ac.fage.accessmesh.access.audit.service.domain.AuditRetentionDomainService.Kind;
import cn.ac.fage.accessmesh.access.infrastructure.task.TaskExecutionContext;
import cn.ac.fage.accessmesh.access.infrastructure.task.JobInvocable;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import org.springframework.stereotype.Component;
@Component("auditRetentionTask")
public class AuditRetentionTask {
    private final AuditLogSettings settings;
    private final AuditRetentionAppService service;
    public AuditRetentionTask(AuditLogSettings settings, AuditRetentionAppService service) { this.settings = settings; this.service = service; }
    @JobInvocable
    public String cleanup(TaskExecutionContext context) {
        var caller = cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext.get();
        if (caller == null || caller.callerType() != cn.ac.fage.accessmesh.access.infrastructure.CallerType.TASK
                || context.scheduledTime() == null || !context.tenantId().equals(TenantContextHolder.getTenantId()))
            throw new IllegalArgumentException("Audit cleanup requires a scheduled tenant task");
        if (!settings.cleanupEnabled()) return "cleanup disabled";
        return "operation=" + clean(context, Kind.OPERATION, settings.operationDays())
            + ", change=" + clean(context, Kind.CHANGE, settings.changeDays())
            + ", login=" + clean(context, Kind.LOGIN, settings.loginDays());
    }
    private long clean(TaskExecutionContext context, Kind kind, Integer days) {
        if (days == null) return 0;
        var cutoff = context.scheduledTime().minusDays(days);
        long total = 0;
        int count;
        do { count = service.deleteBatch(context, kind, cutoff); total += count; } while (count == 500);
        return total;
    }
}
