package cn.ac.fage.accessmesh.access.platform.service.domain.impl;
import cn.ac.fage.accessmesh.access.platform.mapper.SysJobMapper;
import cn.ac.fage.accessmesh.access.platform.service.domain.DeploymentJobDomainService;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
public class DeploymentJobDomainServiceImpl implements DeploymentJobDomainService {
    private final SysJobMapper mapper;
    public DeploymentJobDomainServiceImpl(SysJobMapper mapper) { this.mapper = mapper; }
    public void synchronizeAuditRetention(List<Long> tenants, String cron) {
        if (!tenants.isEmpty()) mapper.upsertAuditRetentionJobs(tenants, cron);
    }
    public void disableAuditRetention() { mapper.disableAuditRetentionJobs(); }
}
