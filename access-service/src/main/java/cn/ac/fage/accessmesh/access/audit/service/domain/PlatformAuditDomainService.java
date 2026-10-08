package cn.ac.fage.accessmesh.access.audit.service.domain;

import cn.ac.fage.accessmesh.access.audit.entity.PlatformAuditLog;
import cn.ac.fage.accessmesh.access.auth.security.PlatformActor;
import java.util.List;

public interface PlatformAuditDomainService {
    /** 与调用方的关键数据库变更共享事务，不开启独立事务或异步写入。 */
    void record(PlatformActor actor, Long targetTenantId, String targetType, String targetId,
                String action, String outcome, String summary);
    /** 主写入回滚后记录失败尝试；不能让审计异常覆盖原拒绝原因。 */
    void recordAttempt(PlatformActor actor, Long targetTenantId, String targetType, String targetId,
                       String action, String outcome, String summary);
    List<PlatformAuditLog> page(Long tenantId, int offset, int limit);
    long count(Long tenantId);
}
