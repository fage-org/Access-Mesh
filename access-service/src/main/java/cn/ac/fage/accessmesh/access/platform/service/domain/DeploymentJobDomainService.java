package cn.ac.fage.accessmesh.access.platform.service.domain;
import java.util.List;
public interface DeploymentJobDomainService {
    void synchronizeAuditRetention(List<Long> tenantIds, String cron);
    void disableAuditRetention();
}
