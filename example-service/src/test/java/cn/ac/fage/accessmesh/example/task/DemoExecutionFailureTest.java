package cn.ac.fage.accessmesh.example.task;

import cn.ac.fage.accessmesh.example.task.service.impl.DemoExecutionAppServiceImpl;
import cn.ac.fage.accessmesh.example.task.domain.DemoExecutionDomainService;
import cn.ac.fage.accessmesh.example.task.entity.DemoExecution;
import cn.ac.fage.accessmesh.example.data.domain.DemoDataDomainService;
import cn.ac.fage.accessmesh.example.metadata.domain.DemoMetadataDomainService;
import cn.ac.fage.accessmesh.example.data.security.DemoDataPermissions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import tools.jackson.databind.json.JsonMapper;
import java.sql.SQLException;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class DemoExecutionFailureTest {
    @ParameterizedTest
    @CsvSource({"08006,PENDING,EXECUTION_ERROR","40001,PENDING,EXECUTION_ERROR","57014,FAILED,LIMIT_EXCEEDED","42P01,FAILED,EXECUTION_ERROR"})
    void shouldClassifyNestedSqlStateWithoutRetryingInvalidSqlOrBudgetTimeout(String sqlState,String status,String reason) {
        var transactions=mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(i->new SimpleTransactionStatus());
        var executions=mock(DemoExecutionDomainService.class);
        var data=mock(DemoDataDomainService.class);
        var e=new DemoExecution(); e.setTenantId(1L); e.setCreatedBy(42L); e.setExecutionKey(UUID.randomUUID());
        e.setAttempt(1); e.setStatus("RUNNING"); e.setRequestPayload("{\"reportCode\":\"r1\"}");
        when(executions.claim()).thenReturn(e);
        when(data.authorizeRead(any(),any())).thenThrow(new CannotGetJdbcConnectionException("unavailable",new SQLException("test",sqlState)));
        var tasks=mock(cn.ac.fage.accessmesh.example.task.domain.DemoTaskDomainService.class);
        when(tasks.executable(any())).thenReturn(true);
        var app=new DemoExecutionAppServiceImpl(executions,data,mock(DemoMetadataDomainService.class),mock(DemoDataPermissions.class),JsonMapper.builder().build(),transactions,tasks);
        assertTrue(app.runNext());
        verify(executions).finish(e);
        assertEquals(status,e.getStatus()); assertEquals(reason,e.getFailureReason()); assertNull(e.getResultBytes());
    }
}
