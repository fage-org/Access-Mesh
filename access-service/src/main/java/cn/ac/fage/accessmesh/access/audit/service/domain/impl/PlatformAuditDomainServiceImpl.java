package cn.ac.fage.accessmesh.access.audit.service.domain.impl;

import cn.ac.fage.accessmesh.access.audit.entity.PlatformAuditLog;
import cn.ac.fage.accessmesh.access.audit.mapper.PlatformAuditLogMapper;
import cn.ac.fage.accessmesh.access.audit.service.domain.PlatformAuditDomainService;
import cn.ac.fage.accessmesh.access.auth.security.PlatformActor;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.util.HttpRequestUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.UUID;

@Service
public class PlatformAuditDomainServiceImpl implements PlatformAuditDomainService {
    private final PlatformAuditLogMapper mapper;

    public PlatformAuditDomainServiceImpl(PlatformAuditLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void record(PlatformActor actor, Long tenantId, String targetType, String targetId,
                       String action, String outcome, String summary) {
        PlatformAuditLog row = new PlatformAuditLog();
        row.setOperatorId(actor == null ? null : actor.id());
        row.setOperatorName(actor == null ? null : actor.username());
        row.setTargetTenantId(tenantId);
        row.setTargetType(targetType);
        row.setTargetId(targetId);
        row.setAction(action);
        row.setOutcome(outcome);
        row.setSummary(summary);
        String requestId = AccessRequestContext.getRequestId();
        row.setRequestId(requestId == null ? UUID.randomUUID().toString() : requestId);
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            row.setRequestUrl(attributes.getRequest().getRequestURI());
            row.setIpAddress(HttpRequestUtils.getClientIp(attributes.getRequest()));
        }
        mapper.insert(row);
    }

    /** 独立新事务：失败尝试的审计不随已回滚的业务事务消失（切面在主事务结束后调用）。 */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAttempt(PlatformActor actor, Long tenantId, String targetType, String targetId,
                              String action, String outcome, String summary) {
        record(actor, tenantId, targetType, targetId, action, outcome, summary);
    }

    @Override
    public List<PlatformAuditLog> page(Long tenantId, int offset, int limit) {
        return mapper.page(tenantId, offset, limit);
    }

    @Override
    public long count(Long tenantId) {
        return mapper.count(tenantId);
    }
}
