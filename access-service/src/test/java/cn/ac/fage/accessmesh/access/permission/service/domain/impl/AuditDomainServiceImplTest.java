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

    @Test
    void shouldQueryRecentChangesWithFilters() {
        PermissionChangeLog log = new PermissionChangeLog();
        log.setId(1L);
        log.setEntityType("role_resource_permission");
        log.setOperation("INSERT");
        log.setTenantId(1L);
        when(changeLogMapper.selectFiltered(
            eq(1L), eq(100L), eq(20L), any(), any(), any(), eq(0), eq(10)))
            .thenReturn(List.of(log));

        List<PermissionChangeLog> results = service.queryRecentChanges(1L, 100L, 20L,
            LocalDateTime.now().minusDays(7), LocalDateTime.now(),
            List.of("INSERT"), 0, 10);

        assertEquals(1, results.size());
    }

    @Test
    void shouldCountRecentChanges() {
        when(changeLogMapper.countFiltered(
            eq(1L), eq(100L), eq(20L), any(), any(), any()))
            .thenReturn(5L);

        long count = service.countRecentChanges(1L, 100L, 20L,
            LocalDateTime.now().minusDays(7), LocalDateTime.now(),
            List.of("INSERT"));

        assertEquals(5L, count);
    }
}
