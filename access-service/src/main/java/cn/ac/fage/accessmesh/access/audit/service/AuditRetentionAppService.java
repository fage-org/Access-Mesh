package cn.ac.fage.accessmesh.access.audit.service;
import cn.ac.fage.accessmesh.access.audit.service.domain.AuditRetentionDomainService.Kind;
import cn.ac.fage.accessmesh.access.infrastructure.task.TaskExecutionContext;
import java.time.LocalDateTime;
public interface AuditRetentionAppService {
    void synchronizeJobs();
    int deleteBatch(TaskExecutionContext context, Kind kind, LocalDateTime cutoff);
}
