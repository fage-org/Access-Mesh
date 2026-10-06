package cn.ac.fage.accessmesh.access.audit.service.impl;

import cn.ac.fage.accessmesh.access.audit.service.GatewayAuditAppService;
import cn.ac.fage.accessmesh.access.audit.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.CallerType;
import cn.ac.fage.accessmesh.common.model.GatewayDenialAuditReq;
import org.springframework.stereotype.Service;

/** 复用已有异步独立审计事务；仅接受无外部服务身份的平台代理内部互信调用。 */
@Service
public class GatewayAuditAppServiceImpl implements GatewayAuditAppService {
    private final AuditDomainService auditDomainService;

    public GatewayAuditAppServiceImpl(AuditDomainService auditDomainService) {
        this.auditDomainService = auditDomainService;
    }

    @Override
    public void recordDenial(GatewayDenialAuditReq request) {
        var context = AccessRequestContext.get();
        if (context == null || context.callerType() != CallerType.SERVICE
            || context.tenantId() == null || context.serviceCode() != null) {
            throw new SecurityException("仅允许平台代理内部互信写入拒绝审计");
        }
        String summary = "Gateway拒绝(reason=" + request.reason() + ") " + request.httpMethod() + " " + request.path();
        auditDomainService.asyncRecordLog(new AuditDomainService.OperationLogEntry(
            context.tenantId(), "ACCESS", "GATEWAY_PERMISSION_DENIED", "service_config", request.serviceCode(),
            summary.substring(0, Math.min(summary.length(), 512)), request.userId(), null,
            request.clientIp(), request.requestId(), request.path(), 403, null));
    }
}
