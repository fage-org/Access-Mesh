package cn.ac.fage.accessmesh.access.audit.service.domain;
import java.time.LocalDateTime;
public interface AuditRetentionDomainService {
    enum Kind { OPERATION, CHANGE, LOGIN }
    int deleteBatch(Long tenantId, Kind kind, LocalDateTime cutoff);
}
