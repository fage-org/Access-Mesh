package cn.ac.fage.accessmesh.access.audit.service.impl;

import cn.ac.fage.accessmesh.access.audit.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.common.model.GatewayDenialAuditReq;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GatewayAuditAppServiceImplTest {
    private final AuditDomainService audit = mock(AuditDomainService.class);
    private final GatewayAuditAppServiceImpl service = new GatewayAuditAppServiceImpl(audit);
    private final GatewayDenialAuditReq request = new GatewayDenialAuditReq(9L, "example-service", "POST",
        "/api/example/demo/hello", "NO_CANDIDATE", "10.0.0.1", "gateway-denied-test");
    @AfterEach void clear() { AccessRequestContext.clear(); }

    @Test void internalTrustWritesTenantFromContext() {
        AccessRequestContext.bind(RequestContext.service(1L, null));
        service.recordDenial(request);
        var captor = org.mockito.ArgumentCaptor.forClass(AuditDomainService.OperationLogEntry.class);
        verify(audit).asyncRecordLog(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(1L);
        assertThat(captor.getValue().operatorId()).isEqualTo(9L);
        assertThat(captor.getValue().responseCode()).isEqualTo(403);
        assertThat(captor.getValue().requestId()).isEqualTo("gateway-denied-test");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"user", "external-service", "anonymous"})
    void otherCallersCannotForgeGatewayAudit(String caller) {
        AccessRequestContext.bind(switch (caller) {
            case "user" -> RequestContext.user(1L, 9L);
            case "external-service" -> RequestContext.service(1L, "external");
            default -> RequestContext.anonymous();
        });
        assertThatThrownBy(() -> service.recordDenial(request)).isInstanceOf(SecurityException.class);
        verifyNoInteractions(audit);
    }
}
