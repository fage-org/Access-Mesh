package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.mapper.OperationLogMapper;
import cn.ac.fage.accessmesh.access.permission.dto.resp.OperationLogResp;
import cn.ac.fage.accessmesh.access.permission.mapper.PermissionChangeLogMapper;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.infrastructure.entity.OperationLog;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 操作日志查询链路测试（T-PERM-025）：
 * 门禁切换 OPERATION_LOG:VIEW（审计分离回归锁）、action 字典、多维过滤参数透传与规整。
 */
@ExtendWith(MockitoExtension.class)
class LogQueryAppServiceImplOperationLogTest {

    @Mock private PermissionChangeLogMapper changeLogMapper;
    @Mock private OperationLogMapper operationLogMapper;
    @Mock private PermQueryEngine engine;
    @Mock private TypeResolutionService typeResolutionService;

    private LogQueryAppServiceImpl service() {
        return new LogQueryAppServiceImpl(changeLogMapper, operationLogMapper, engine,
            typeResolutionService);
    }

    @Test
    void shouldGateOperationLogQueriesOnOperationLogView() {
        // 回归锁：操作日志门禁为 OPERATION_LOG:VIEW（T-PERM-025 审计分离），旧实现为 SYSTEM_CONFIG:VIEW
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.OPERATION_LOG),
                isNull(), any())).thenReturn(false);

            assertThrows(SecurityException.class, () -> service().listActionOptions(1L, null));
            verify(engine).hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.OPERATION_LOG),
                isNull(), any());
        }
    }

    @Test
    void shouldListDistinctActionsWithModuleNormalized() {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(anyLong(), anyLong(), any(), any(), any())).thenReturn(true);
            when(operationLogMapper.selectDistinctActions(1L, "PERMISSION"))
                .thenReturn(List.of("ROLE_CREATE", "ROLE_UPDATE"));

            List<String> result = service().listActionOptions(1L, " PERMISSION ");

            assertEquals(List.of("ROLE_CREATE", "ROLE_UPDATE"), result);
            verify(operationLogMapper).selectDistinctActions(eq(1L), eq("PERMISSION"));
        }
    }

    @Test
    void shouldListOperationLogsWithExtendedFilters() {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(anyLong(), anyLong(), any(), any(), any())).thenReturn(true);
            OperationLog row = new OperationLog();
            row.setId(7L);
            row.setTenantId(1L);
            row.setModule("PERMISSION");
            row.setAction("ROLE_CREATE");
            LocalDateTime since = LocalDateTime.of(2026, 8, 1, 0, 0);
            LocalDateTime until = LocalDateTime.of(2026, 8, 28, 0, 0);
            when(operationLogMapper.selectPageByCondition(eq(1L), eq("PERMISSION"), eq("ROLE_CREATE"),
                eq(100L), eq(since), eq(until), isNull(), eq(0), eq(15)))
                .thenReturn(List.of(row));

            List<OperationLogResp> result = service().listOperationLogs(1L, " PERMISSION ", " ROLE_CREATE ",
                100L, since, until, "  ", 0, 15);

            assertEquals(1, result.size());
            assertEquals("ROLE_CREATE", result.get(0).action());
            // 空白 targetType 规整为 null；module/action trim 后透传
            verify(operationLogMapper).selectPageByCondition(eq(1L), eq("PERMISSION"), eq("ROLE_CREATE"),
                eq(100L), eq(since), eq(until), isNull(), eq(0), eq(15));
        }
    }

    @Test
    void shouldCountOperationLogsWithExtendedFilters() {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(anyLong(), anyLong(), any(), any(), any())).thenReturn(true);
            when(operationLogMapper.countByCondition(eq(1L), isNull(), isNull(),
                eq(100L), isNull(), isNull(), eq("ROLE"))).thenReturn(5L);

            long total = service().countOperationLogs(1L, "  ", null, 100L, null, null, " ROLE ");

            assertEquals(5L, total);
            verify(operationLogMapper).countByCondition(eq(1L), isNull(), isNull(),
                eq(100L), isNull(), isNull(), eq("ROLE"));
        }
    }
}
