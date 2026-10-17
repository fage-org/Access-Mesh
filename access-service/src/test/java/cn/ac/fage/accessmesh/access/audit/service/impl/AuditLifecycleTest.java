package cn.ac.fage.accessmesh.access.audit.service.impl;
import cn.ac.fage.accessmesh.access.audit.config.AuditLogSettings;
import cn.ac.fage.accessmesh.access.audit.dto.req.*;
import cn.ac.fage.accessmesh.access.audit.service.domain.*;
import cn.ac.fage.accessmesh.access.audit.service.AuditRetentionAppService;
import cn.ac.fage.accessmesh.access.audit.task.AuditRetentionTask;
import cn.ac.fage.accessmesh.access.engine.query.QueryGate;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.task.*;
import org.springframework.mock.env.MockEnvironment;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class AuditLifecycleTest {
    @Test void loginPagingRetainsThePreviouslyAcceptedSortField() {
        var json = tools.jackson.databind.json.JsonMapper.builder().configureForJackson2().build();
        assertThatCode(() -> {
            var request = json.readValue("{\"pageNum\":2,\"pageSize\":20,\"sort\":\"loginAt,desc\",\"since\":\"2026-09-01T00:00:00\"}", LoginLogListReq.class);
            assertThat(request.getPageNum()).isEqualTo(2);
            assertThat(request.since()).isEqualTo(LocalDateTime.of(2026,9,1,0,0));
        }).doesNotThrowAnyException();
    }
    @Test void startupSnapshotDefaultsToNoDeletionAndDoesNotHotReload() {
        var env = new MockEnvironment();
        var defaults = new AuditLogSettings(env);
        assertThat(defaults.cleanupEnabled()).isFalse();
        assertThat(defaults.operationDays()).isNull();
        env.setProperty("access.audit.retention.enabled", "true");
        env.setProperty("access.audit.retention.operation-days", "30");
        assertThat(defaults.cleanupEnabled()).isFalse();
        var configured = new AuditLogSettings(env);
        assertThat(configured.cleanupEnabled()).isTrue();
        assertThat(configured.changeDays()).isNull();
        env.setProperty("access.audit.retention.login-days", "0");
        assertThatThrownBy(() -> new AuditLogSettings(env)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void allExportsDenyBeforeReadingAndLoginUsesItsExistingSubjectResolution() {
        var domain = mock(AuditExportDomainService.class);
        var gate = mock(QueryGate.class);
        var types = mock(TypeResolutionService.class);
        when(types.resolveUserId(1L, "LOCAL_USER", "9")).thenReturn(19L);
        var service = new AuditExportAppServiceImpl(domain, gate, types, new AuditLogSettings(new MockEnvironment()));
        AccessRequestContext.bind(RequestContext.user(1L, 9L));
        try {
            assertThatThrownBy(() -> service.operations(new OperationLogExportReq(null,null,null,null,null,null,null))).isInstanceOf(SecurityException.class);
            assertThatThrownBy(() -> service.changes(new ChangeLogExportReq(null,null,null,null,null,null,null,null))).isInstanceOf(SecurityException.class);
            assertThatThrownBy(() -> service.logins(new LoginLogExportReq(null,null))).isInstanceOf(SecurityException.class);
            verify(gate).hasPermissionByCode(1L, 19L, "OPERATION_LOG", null, "VIEW");
            verifyNoInteractions(domain);
        } finally { AccessRequestContext.clear(); }
    }
    @Test void cleanupUsesOriginalScheduleCutoffAndOnlyConfiguredCategories() {
        var settings = new AuditLogSettings(new MockEnvironment().withProperty("access.audit.retention.enabled", "true")
            .withProperty("access.audit.retention.operation-days", "30"));
        var app = mock(AuditRetentionAppService.class);
        var time = LocalDateTime.of(2026,10,17,18,0);
        var context = new TaskExecutionContext(1L, 2L, "job:2:20261017T180000", 2, time);
        when(app.deleteBatch(context, AuditRetentionDomainService.Kind.OPERATION, time.minusDays(30))).thenReturn(500, 7);
        AccessRequestContext.bind(RequestContext.task(1L));
        try {
            assertThat(new AuditRetentionTask(settings, app).cleanup(context)).isEqualTo("operation=507, change=0, login=0");
            verify(app, times(2)).deleteBatch(context, AuditRetentionDomainService.Kind.OPERATION, time.minusDays(30));
            verifyNoMoreInteractions(app);
        } finally { AccessRequestContext.clear(); }
    }
}
