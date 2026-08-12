package cn.ac.fage.accessmesh.access.permission.aop;

import cn.ac.fage.accessmesh.access.permission.dto.resp.ServiceConfigSyncResp;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OperationLogAspect 单元测试
 * <p>
 * 测试 SpEL 表达式求值（参数型、结果型）、异常路径和 operatorId 采样。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class OperationLogAspectTest {

    @Mock
    private AuditDomainService auditDomainService;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    private OperationLogAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new OperationLogAspect(auditDomainService);
    }

    // ===== 辅助方法 =====

    /**
     * 配置 joinPoint 模拟方法签名和参数
     */
    private void setupJoinPoint(Class<?> clazz, String methodName, Object[] args, String[] paramNames, Object result) throws Throwable {
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getArgs()).thenReturn(args);
        when(joinPoint.proceed()).thenReturn(result);

        // 查找目标方法
        Method method = null;
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                Class<?>[] paramTypes = m.getParameterTypes();
                if (paramTypes.length == args.length) {
                    method = m;
                    break;
                }
            }
        }
        if (method == null) {
            throw new IllegalArgumentException("Method not found: " + methodName);
        }
        when(methodSignature.getMethod()).thenReturn(method);
    }

    /**
     * 测试用 SpEL bean — 参数型摘要
     */
    @OperationLog(module = "test", action = "batch-delete", targetType = "BATCH", targetId = "", summary = "'soft-deleted ' + #ids.size() + ' row(s)'")
    public void paramBasedMethod(List<Long> ids, Long operatorId) {
        // 仅用于 AOP 拦截测试
    }

    /**
     * 测试用 SpEL bean — 结果型摘要
     */
    @OperationLog(module = "test", action = "sync", targetType = "BATCH", targetId = "", summary = "'sync result: created=' + #result.createdResources() + ', mappings=' + #result.createdMappings()")
    public ServiceConfigSyncResp resultBasedMethod(String serviceCode) {
        return new ServiceConfigSyncResp(5, 0, 3, 1, 2, 1);
    }

    /**
     * 测试用 SpEL bean — 有 operatorId 参数
     */
    @OperationLog(module = "test", action = "manage", targetType = "SINGLE", targetId = "#targetId", summary = "'managed id=' + #targetId")
    public void operatorIdMethod(Long targetId, Long operatorId) {
        // 仅用于 AOP 拦截测试
    }

    // ===== 测试用例 =====

    /**
     * 测试 1: 参数型摘要 — 验证 auditDomainService 收到正确的 summary
     */
    @Test
    void shouldEvaluateParamBasedSummary() throws Throwable {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);

            List<Long> ids = List.of(1L, 2L, 3L);
            setupJoinPoint(getClass(), "paramBasedMethod", new Object[]{ids, 100L},
                new String[]{"ids", "operatorId"}, null);

            OperationLog opLog = getClass().getDeclaredMethod("paramBasedMethod", List.class, Long.class)
                .getAnnotation(OperationLog.class);

            aspect.around(joinPoint, opLog);

            ArgumentCaptor<String> summaryCaptor = ArgumentCaptor.forClass(String.class);
            verify(auditDomainService).asyncRecordLog(
                eq("test"), eq("batch-delete"), anyString(), any(), summaryCaptor.capture(),
                any(), any(), any(), any()
            );

            assertEquals("soft-deleted 3 row(s)", summaryCaptor.getValue());
        }
    }

    /**
     * 测试 2: 结果型摘要 — 验证 #result 字段可用
     */
    @Test
    void shouldEvaluateResultBasedSummary() throws Throwable {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);

            ServiceConfigSyncResp expectedResult = new ServiceConfigSyncResp(5, 0, 3, 1, 2, 1);
            setupJoinPoint(getClass(), "resultBasedMethod", new Object[]{"my-svc"},
                new String[]{"serviceCode"}, expectedResult);

            OperationLog opLog = getClass().getDeclaredMethod("resultBasedMethod", String.class)
                .getAnnotation(OperationLog.class);

            aspect.around(joinPoint, opLog);

            ArgumentCaptor<String> summaryCaptor = ArgumentCaptor.forClass(String.class);
            verify(auditDomainService).asyncRecordLog(
                eq("test"), eq("sync"), anyString(), any(), summaryCaptor.capture(),
                any(), any(), any(), any()
            );

            assertEquals("sync result: created=5, mappings=3", summaryCaptor.getValue());
        }
    }

    /**
     * 测试 3: 异常路径 — 方法抛 RuntimeException，验证 auditDomainService 不被调用
     */
    @Test
    void shouldNotRecordWhenMethodThrows() throws Throwable {
        when(joinPoint.proceed()).thenThrow(new RuntimeException("test exception"));

        OperationLog opLog = mock(OperationLog.class);
        lenient().when(opLog.module()).thenReturn("test");
        lenient().when(opLog.action()).thenReturn("action");

        try {
            aspect.around(joinPoint, opLog);
            fail("Expected RuntimeException");
        } catch (RuntimeException ignored) {
            // expected
        }

        // 异常后不应记录正常日志
        verify(auditDomainService, never()).asyncRecordLog(anyString(), anyString(), anyString(),
            any(), anyString(), any(), any(), any(), any());
    }

    /**
     * 测试 4: operatorId 采样 — 验证日志中 operatorId 正确
     */
    @Test
    void shouldSampleOperatorIdCorrectly() throws Throwable {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(999L);

            setupJoinPoint(getClass(), "operatorIdMethod", new Object[]{42L, 999L},
                new String[]{"targetId", "operatorId"}, null);

            OperationLog opLog = getClass().getDeclaredMethod("operatorIdMethod", Long.class, Long.class)
                .getAnnotation(OperationLog.class);

            aspect.around(joinPoint, opLog);

            verify(auditDomainService).asyncRecordLog(
                eq("test"), eq("manage"), eq("SINGLE"), eq(42L), eq("managed id=42"),
                eq(999L), isNull(), isNull(), isNull()
            );
        }
    }

    @Test
    void shouldSkipWhenRuntimeContextMarked() throws Throwable {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);

            when(joinPoint.proceed()).thenAnswer(_invocation -> {
                OperationLogRuntimeContext.markSkip();
                return null;
            });

            OperationLog opLog = getClass().getDeclaredMethod("paramBasedMethod", List.class, Long.class)
                .getAnnotation(OperationLog.class);

            aspect.around(joinPoint, opLog);

            verify(auditDomainService, never()).asyncRecordLog(anyString(), anyString(), anyString(),
                any(), anyString(), any(), any(), any(), any());
        }
    }

    @Test
    void shouldUseRuntimeOverridesWhenProvided() throws Throwable {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(999L);

            setupJoinPoint(getClass(), "operatorIdMethod", new Object[]{42L, 999L},
                new String[]{"targetId", "operatorId"}, null);

            when(joinPoint.proceed()).thenAnswer(_invocation -> {
                OperationLogRuntimeContext.setSummary("actual managed id=77");
                OperationLogRuntimeContext.setTargetType("ROLE");
                OperationLogRuntimeContext.setTargetId(77L);
                return null;
            });

            OperationLog opLog = getClass().getDeclaredMethod("operatorIdMethod", Long.class, Long.class)
                .getAnnotation(OperationLog.class);

            aspect.around(joinPoint, opLog);

            verify(auditDomainService).asyncRecordLog(
                eq("test"), eq("manage"), eq("ROLE"), eq(77L), eq("actual managed id=77"),
                eq(999L), isNull(), isNull(), isNull()
            );
        }
    }

    @Test
    void shouldClearRuntimeContextAfterInvocation() throws Throwable {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);

            List<Long> ids = List.of(1L, 2L);

            when(joinPoint.proceed()).thenAnswer(_invocation -> {
                OperationLogRuntimeContext.markSkip();
                return null;
            });
            OperationLog opLog = getClass().getDeclaredMethod("paramBasedMethod", List.class, Long.class)
                .getAnnotation(OperationLog.class);

            aspect.around(joinPoint, opLog);
            verify(auditDomainService, never()).asyncRecordLog(anyString(), anyString(), anyString(),
                any(), anyString(), any(), any(), any(), any());

            clearInvocations(auditDomainService);
            reset(joinPoint, methodSignature);
            setupJoinPoint(getClass(), "paramBasedMethod", new Object[]{ids, 100L},
                new String[]{"ids", "operatorId"}, null);
            aspect.around(joinPoint, opLog);

            verify(auditDomainService).asyncRecordLog(
                eq("test"), eq("batch-delete"), eq("BATCH"), isNull(), eq("soft-deleted 2 row(s)"),
                eq(100L), isNull(), isNull(), isNull()
            );
        }
    }
}
