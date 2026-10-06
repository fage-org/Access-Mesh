package cn.ac.fage.accessmesh.access.audit.service.impl;

import cn.ac.fage.accessmesh.access.audit.dto.resp.LoginLogResp;
import cn.ac.fage.accessmesh.access.audit.entity.OperationLog;
import cn.ac.fage.accessmesh.access.audit.entity.PermissionChangeLog;
import cn.ac.fage.accessmesh.access.audit.entity.SysLoginLog;
import cn.ac.fage.accessmesh.access.audit.mapper.OperationLogMapper;
import cn.ac.fage.accessmesh.access.audit.mapper.PermissionChangeLogMapper;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.engine.query.QueryGate;
import cn.ac.fage.accessmesh.access.infrastructure.util.OperatorContext;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditReadMaskingTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void loginLogReadRequiresOperationLogViewBeforeDatabaseAccess(boolean allowed) {
        var mapper = mock(cn.ac.fage.accessmesh.access.audit.mapper.SysLoginLogMapper.class);
        var gate = mock(QueryGate.class);
        // 门禁主体经投影解析（2026-10-06 逐任务评审改）：桩解析 9→9（admin 同 ID 特例形态）
        var typeResolution = mock(cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService.class);
        org.mockito.Mockito.when(typeResolution.resolveUserId(1L,
                cn.ac.fage.accessmesh.access.sync.guard.LocalProjectionOwner.SUBJECT_LOCAL_USER, "9"))
            .thenReturn(9L);
        var service = new LoginLogAppServiceImpl(mapper, gate, typeResolution);
        cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext.bind(
            cn.ac.fage.accessmesh.access.infrastructure.RequestContext.user(1L, 9L));
        try {
            when(gate.hasPermissionByCode(1L, 9L, "OPERATION_LOG", null, "VIEW")).thenReturn(allowed);
            if (allowed) {
                assertThat(service.pageLoginLogs(cn.ac.fage.accessmesh.common.model.PageReq.defaults()).items()).isEmpty();
                verify(mapper).countByTenantId(1L);
            } else {
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.pageLoginLogs(
                    cn.ac.fage.accessmesh.common.model.PageReq.defaults())).isInstanceOf(SecurityException.class);
                verifyNoInteractions(mapper);
            }
        } finally {
            cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext.clear();
        }
    }
    @Test
    void loginReadMasksPhoneAndEmailButKeepsOrdinaryIdentityAndIp() {
        var row = new SysLoginLog();
        row.setUsername("13812345678");
        row.setIpAddress("10.0.0.12");
        row.setFailReason("alice@example.com 登录失败");
        var masked = LoginLogResp.from(row);
        assertThat(masked.username()).doesNotContain("13812345678");
        assertThat(masked.failReason()).doesNotContain("alice@example.com");
        assertThat(masked.ipAddress()).isEqualTo("10.0.0.12");
        assertThat(row.getUsername()).isEqualTo("13812345678");
        row.setUsername("admin");
        assertThat(LoginLogResp.from(row).username()).isEqualTo("admin");
    }

    @Test
    void operationAndChangeReadsMaskSensitiveValuesWithoutChangingStoredSnapshots() {
        var operations = mock(OperationLogMapper.class);
        var changes = mock(PermissionChangeLogMapper.class);
        var gate = mock(QueryGate.class);
        when(gate.hasPermissionByCode(anyLong(), anyLong(), any(), any(), any())).thenReturn(true);
        var service = new LogQueryAppServiceImpl(changes, operations, gate, mock(TypeResolutionService.class));
        var operation = new OperationLog();
        operation.setSummary("更新 13812345678 alice@example.com");
        operation.setOperatorName("admin");
        operation.setIpAddress("10.0.0.12");
        when(operations.selectPageByCondition(1L, null, null, 7L, null, null, null, 0, 20, null)).thenReturn(List.of(operation));
        var change = new PermissionChangeLog();
        String raw = "{\"extra\":{\"phone\":\"13812345678\",\"email\":\"alice@example.com\",\"secret\":\"credential-secret\",\"name\":\"keep\"}}";
        change.setDiffSnapshot(raw);
        when(changes.selectPageByCondition(1L, null, null, null, null, null, null, null, null, 0, 20)).thenReturn(List.of(change));
        try (var operator = mockStatic(OperatorContext.class)) {
            operator.when(OperatorContext::getOperatorId).thenReturn(7L);
            var operationResp = service.listOperationLogs(1L, null, null, 7L, null, null, null, 0, 20, null).getFirst();
            assertThat(operationResp.summary()).doesNotContain("13812345678", "alice@example.com");
            assertThat(operationResp.operatorName()).isEqualTo("admin");
            assertThat(operationResp.ipAddress()).isEqualTo("10.0.0.12");
            var changeResp = service.listChangeLogs(1L, null, null, null, null, null, null, null, null, 0, 20).getFirst();
            assertThat(changeResp.diffSnapshot()).doesNotContain("13812345678", "alice@example.com", "credential-secret").contains("keep");
            assertThat(change.getDiffSnapshot()).isEqualTo(raw);
            assertThat(operation.getSummary()).contains("13812345678", "alice@example.com");
        }
    }
    @Test
    void snapshotMaskingPreservesJsonNumbersAndKeysIncludingEmbeddedJson() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var nested = mapper.createObjectNode().put("id", 13812345678L)
            .put("phone", "13812345678");
        var source = mapper.createObjectNode().put("id", 13812345678L)
            .put("13812345678", "contact alice@example.com")
            .put("nested", nested.toString());
        source.set("texts", mapper.createArrayNode().add("alice@example.com").add(13812345678L));
        var masked = mapper.readTree(cn.ac.fage.accessmesh.access.infrastructure.util.SensitiveDataUtils
            .maskAuditSnapshot(source.toString()));
        assertThat(masked.path("id").asLong()).isEqualTo(13812345678L);
        assertThat(masked.path("13812345678").asText()).isEqualTo("contact ***");
        assertThat(masked.path("texts").get(0).asText()).isEqualTo("***");
        assertThat(masked.path("texts").get(1).asLong()).isEqualTo(13812345678L);
        assertThat(mapper.readTree(cn.ac.fage.accessmesh.access.infrastructure.util.SensitiveDataUtils
            .maskAuditSnapshot(mapper.writeValueAsString("alice@example.com"))).asText()).isEqualTo("***");
        var embedded = mapper.readTree(masked.path("nested").asText());
        assertThat(embedded.path("id").asLong()).isEqualTo(13812345678L);
        assertThat(embedded.path("phone").asText()).isEqualTo("***");
        assertThat(source.path("nested").asText()).contains("13812345678");
    }

}
