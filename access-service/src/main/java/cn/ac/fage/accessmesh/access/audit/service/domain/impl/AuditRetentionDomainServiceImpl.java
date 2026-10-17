package cn.ac.fage.accessmesh.access.audit.service.domain.impl;
import cn.ac.fage.accessmesh.access.audit.mapper.AuditRetentionMapper;
import cn.ac.fage.accessmesh.access.audit.service.domain.AuditRetentionDomainService;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
@Service
public class AuditRetentionDomainServiceImpl implements AuditRetentionDomainService {
    private final AuditRetentionMapper mapper;
    public AuditRetentionDomainServiceImpl(AuditRetentionMapper mapper) { this.mapper = mapper; }
    public int deleteBatch(Long tenantId, Kind kind, LocalDateTime cutoff) {
        return switch (kind) {
            case OPERATION -> mapper.deleteOperations(tenantId, cutoff);
            case CHANGE -> mapper.deleteChanges(tenantId, cutoff);
            case LOGIN -> mapper.deleteLogins(tenantId, cutoff);
        };
    }
}
