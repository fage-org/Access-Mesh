package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.entity.OperationLog;
import cn.ac.fage.accessmesh.permission.entity.PermissionChangeLog;
import cn.ac.fage.accessmesh.permission.mapper.OperationLogMapper;
import cn.ac.fage.accessmesh.permission.mapper.PermissionChangeLogMapper;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
        service.asyncRecordLog("perm", "CREATE", "abstract_role", 10L,
            "created role", 100L, "127.0.0.1", "req-1", 1L);

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(captor.capture());
        OperationLog log = captor.getValue();

        assertEquals("perm", log.getModule());
        assertEquals("CREATE", log.getAction());
        assertEquals("abstract_role", log.getTargetType());
        assertEquals(10L, log.getTargetId());
        assertEquals(100L, log.getOperatorId());
        assertEquals(1L, log.getTenantId());
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
