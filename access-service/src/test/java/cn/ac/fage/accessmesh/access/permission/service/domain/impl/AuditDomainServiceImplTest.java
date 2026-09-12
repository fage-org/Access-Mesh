package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.access.infrastructure.entity.OperationLog;
import cn.ac.fage.accessmesh.access.permission.entity.PermissionChangeLog;
import cn.ac.fage.accessmesh.access.infrastructure.mapper.OperationLogMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.PermissionChangeLogMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditDomainServiceImplTest {

    @Mock private PermissionChangeLogMapper changeLogMapper;
    @Mock private OperationLogMapper operationLogMapper;

    private AuditDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuditDomainServiceImpl(changeLogMapper, operationLogMapper);
    }

    @Test
    void shouldInsertOperationLogWhenAsyncRecordLog() {
        service.asyncRecordLog(new AuditDomainService.OperationLogEntry(
            1L, "perm", "CREATE", "abstract_role", "10",
            "created role", 100L, "alice", "127.0.0.1", "req-1",
            "/api/roles", 200, 50));

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(captor.capture());
        OperationLog log = captor.getValue();

        assertEquals("perm", log.getModule());
        assertEquals("CREATE", log.getAction());
        assertEquals("abstract_role", log.getTargetType());
        assertEquals("10", log.getTargetId());
        assertEquals(100L, log.getOperatorId());
        assertEquals("alice", log.getOperatorName());
        assertEquals(1L, log.getTenantId());
        assertEquals("127.0.0.1", log.getIpAddress());
        assertEquals("req-1", log.getRequestId());
        assertEquals("/api/roles", log.getRequestUrl());
        // T-ACCESS-025：request_body 列停用，任何路径不再写入参数内容
        assertNull(log.getRequestBody());
        assertEquals(200, log.getResponseCode());
        assertEquals(50, log.getCostTime());
    }

    @Test
    void shouldPropagateExceptionWhenOperationLogInsertFails() {
        doThrow(new RuntimeException("db down"))
            .when(operationLogMapper).insert(any(OperationLog.class));

        // 方法体不吞异常：写入失败向上传播到 AsyncUncaughtExceptionHandler 统一告警，
        // 调用方兜底（如 AuthServiceImpl.safeRecordLoginLog）在入口处隔离影响。
        assertThrows(RuntimeException.class, () -> service.asyncRecordLog(
            new AuditDomainService.OperationLogEntry(
                1L, "perm", "CREATE", "abstract_role", "10",
                "created role", 100L, null, null, null, null, 200, 5)));
    }

    // ===== T-PERM-021 F1.d：request_id 兜底合成 + 上游透传（两列收紧 NOT NULL 的落库保证） =====

    @Test
    void shouldSynthesizeRequestIdWhenOperationLogEntryHasNull() {
        // 无请求上下文写入方（内部动态日志/异步任务）requestId 为 null → 落库前合成 UUID
        service.asyncRecordLog(new AuditDomainService.OperationLogEntry(
            1L, "perm", "CONFLICT_NOTIFY", "conflict_rule", "1",
            "mutex double-delete", null, null, null, null, null, 200, 5));

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(captor.capture());
        String requestId = captor.getValue().getRequestId();
        assertNotNull(requestId);
        assertDoesNotThrow(() -> java.util.UUID.fromString(requestId));
    }

    @Test
    void shouldSynthesizeRequestIdWhenChangeLogContextHasNull() {
        // ChangeLogContext.requestId 为 null（TASK/bootstrap 等无 HTTP 上下文）→ 落库前合成
        service.recordChangeLog(
            new AuditDomainService.ChangeLogContext(1L, 100L, null, "MANUAL", "local-projection"),
            List.of(new AuditDomainService.ChangeLogEntry(
                "resource_entity", 10L, "UPSERT", null, null, null, new Long[0], new Long[0])));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PermissionChangeLog>> captor = ArgumentCaptor.forClass(List.class);
        verify(changeLogMapper).insertBatch(captor.capture());
        String requestId = captor.getValue().get(0).getRequestId();
        assertNotNull(requestId);
        assertDoesNotThrow(() -> java.util.UUID.fromString(requestId));
    }

    @Test
    void shouldPassThroughUpstreamRequestIdUnchanged() {
        // HTTP 链路上游（RequestContext 第六要素）已带真实请求 ID → 原样透传不覆盖，
        // 与当次 operation_log.request_id 同值（JOIN 复原审计链的语义基础）
        service.recordChangeLog(
            new AuditDomainService.ChangeLogContext(1L, 100L, "req-from-context", "MANUAL", "apply-grant-plan"),
            List.of(new AuditDomainService.ChangeLogEntry(
                "role_resource_permission", 20L, "APPLY_GRANT_PLAN",
                null, null, "{}", null, new Long[]{20L})));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PermissionChangeLog>> captor = ArgumentCaptor.forClass(List.class);
        verify(changeLogMapper).insertBatch(captor.capture());
        assertEquals("req-from-context", captor.getValue().get(0).getRequestId());
    }

}
