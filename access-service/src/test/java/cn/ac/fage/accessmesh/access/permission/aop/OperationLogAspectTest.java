package cn.ac.fage.accessmesh.access.permission.aop;

import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLog;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ServiceConfigSyncResp;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService.OperationLogEntry;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * OperationLogAspect 单元测试
 * <p>
 * 测试 SpEL 表达式求值（参数型、结果型）、异常路径、operatorId 采样、
 * 运行时覆盖与请求体脱敏序列化。
 * 无 HTTP 请求上下文时 ipAddress/requestUrl/requestId 为 null，请求体仍序列化。
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
        aspect = new OperationLogAspect(auditDomainService, new ObjectMapper());
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
     * 捕获切面提交的操作日志条目
     */
    private OperationLogEntry captureEntry() {
        ArgumentCaptor<OperationLogEntry> captor = ArgumentCaptor.forClass(OperationLogEntry.class);
        verify(auditDomainService).asyncRecordLog(captor.capture());
        return captor.getValue();
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

    /**
     * 测试用 SpEL bean — 含敏感参数（校验请求体脱敏）
     */
    @OperationLog(module = "test", action = "login-attempt", targetType = "SINGLE", targetId = "", summary = "'login attempt'")
    public void secretMethod(String username, String password) {
        // 仅用于 AOP 拦截测试
    }

    /**
     * 测试用 SpEL bean — 大对象参数（校验序列化前替换为元数据）
     */
    @OperationLog(module = "test", action = "upload", targetType = "file", targetId = "", summary = "'upload'")
    public void uploadMethod(byte[] content, MultipartFile file, String filename) {
        // 仅用于 AOP 拦截测试
    }

    // ===== 测试用例 =====

    /**
     * 测试 1: 参数型摘要 — 验证 auditDomainService 收到正确的 summary
     */
    @Test
    void shouldEvaluateParamBasedSummary() throws Throwable {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class);
             MockedStatic<TenantContextHolder> tenant = mockStatic(TenantContextHolder.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            tenant.when(TenantContextHolder::getTenantId).thenReturn(1L);

            List<Long> ids = List.of(1L, 2L, 3L);
            setupJoinPoint(getClass(), "paramBasedMethod", new Object[]{ids, 100L},
                new String[]{"ids", "operatorId"}, null);

            OperationLog opLog = getClass().getDeclaredMethod("paramBasedMethod", List.class, Long.class)
                .getAnnotation(OperationLog.class);

            aspect.around(joinPoint, opLog);

            OperationLogEntry entry = captureEntry();
            assertEquals("soft-deleted 3 row(s)", entry.summary());
            assertEquals("test", entry.module());
            assertEquals("batch-delete", entry.action());
            assertNotNull(entry.requestBody(), "方法参数应序列化为请求体");
            assertEquals(200, entry.responseCode());
        }
    }

    /**
     * 测试 2: 结果型摘要 — 验证 #result 字段可用
     */
    @Test
    void shouldEvaluateResultBasedSummary() throws Throwable {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class);
             MockedStatic<TenantContextHolder> tenant = mockStatic(TenantContextHolder.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            tenant.when(TenantContextHolder::getTenantId).thenReturn(1L);

            ServiceConfigSyncResp expectedResult = new ServiceConfigSyncResp(5, 0, 3, 1, 2, 1);
            setupJoinPoint(getClass(), "resultBasedMethod", new Object[]{"my-svc"},
                new String[]{"serviceCode"}, expectedResult);

            OperationLog opLog = getClass().getDeclaredMethod("resultBasedMethod", String.class)
                .getAnnotation(OperationLog.class);

            aspect.around(joinPoint, opLog);

            OperationLogEntry entry = captureEntry();
            assertEquals("sync result: created=5, mappings=3", entry.summary());
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
        verify(auditDomainService, never()).asyncRecordLog(any());
    }

    /**
     * 测试 4: operatorId 采样 — 验证日志中 operatorId 正确且 HTTP 字段在无请求上下文时为空
     */
    @Test
    void shouldSampleOperatorIdCorrectly() throws Throwable {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class);
             MockedStatic<TenantContextHolder> tenant = mockStatic(TenantContextHolder.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(999L);
            tenant.when(TenantContextHolder::getTenantId).thenReturn(1L);

            setupJoinPoint(getClass(), "operatorIdMethod", new Object[]{42L, 999L},
                new String[]{"targetId", "operatorId"}, null);

            OperationLog opLog = getClass().getDeclaredMethod("operatorIdMethod", Long.class, Long.class)
                .getAnnotation(OperationLog.class);

            aspect.around(joinPoint, opLog);

            OperationLogEntry entry = captureEntry();
            assertEquals("test", entry.module());
            assertEquals("manage", entry.action());
            assertEquals("SINGLE", entry.targetType());
            assertEquals("42", entry.targetId());
            assertEquals("managed id=42", entry.summary());
            assertEquals(999L, entry.operatorId());
            assertEquals(200, entry.responseCode());
            assertNotNull(entry.costTime());
            // 无 HTTP 请求上下文：ip/requestUrl/requestId 为 null
            assertNull(entry.ipAddress());
            assertNull(entry.requestUrl());
            assertNull(entry.requestId());
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

            verify(auditDomainService, never()).asyncRecordLog(any());
        }
    }

    @Test
    void shouldUseRuntimeOverridesWhenProvided() throws Throwable {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class);
             MockedStatic<TenantContextHolder> tenant = mockStatic(TenantContextHolder.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(999L);
            tenant.when(TenantContextHolder::getTenantId).thenReturn(1L);

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

            OperationLogEntry entry = captureEntry();
            assertEquals("test", entry.module());
            assertEquals("manage", entry.action());
            assertEquals("ROLE", entry.targetType());
            assertEquals("77", entry.targetId());
            assertEquals("actual managed id=77", entry.summary());
            assertEquals(999L, entry.operatorId());
        }
    }

    @Test
    void shouldMaskSensitiveArgsInRequestBody() throws Throwable {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class);
             MockedStatic<TenantContextHolder> tenant = mockStatic(TenantContextHolder.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            tenant.when(TenantContextHolder::getTenantId).thenReturn(1L);

            setupJoinPoint(getClass(), "secretMethod", new Object[]{"alice", "p@ss"},
                new String[]{"username", "password"}, null);

            OperationLog opLog = getClass().getDeclaredMethod("secretMethod", String.class, String.class)
                .getAnnotation(OperationLog.class);

            aspect.around(joinPoint, opLog);

            OperationLogEntry entry = captureEntry();
            assertNotNull(entry.requestBody(), "方法参数应序列化为请求体");
            // T-ACCESS-007 §8.2：密码等敏感字段值脱敏后入库，明文不得进入日志
            assertFalse(entry.requestBody().contains("p@ss"), "请求体不得含明文密码");
            assertTrue(entry.requestBody().contains("\"password\":\"***\""));
            assertTrue(entry.requestBody().contains("\"username\":\"alice\""), "非敏感字段保留");
        }
    }

    @Test
    void shouldClearRuntimeContextAfterInvocation() throws Throwable {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class);
             MockedStatic<TenantContextHolder> tenant = mockStatic(TenantContextHolder.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            tenant.when(TenantContextHolder::getTenantId).thenReturn(1L);

            List<Long> ids = List.of(1L, 2L);

            when(joinPoint.proceed()).thenAnswer(_invocation -> {
                OperationLogRuntimeContext.markSkip();
                return null;
            });
            OperationLog opLog = getClass().getDeclaredMethod("paramBasedMethod", List.class, Long.class)
                .getAnnotation(OperationLog.class);

            aspect.around(joinPoint, opLog);
            verify(auditDomainService, never()).asyncRecordLog(any());

            clearInvocations(auditDomainService);
            reset(joinPoint, methodSignature);
            setupJoinPoint(getClass(), "paramBasedMethod", new Object[]{ids, 100L},
                new String[]{"ids", "operatorId"}, null);
            aspect.around(joinPoint, opLog);

            OperationLogEntry entry = captureEntry();
            assertEquals("test", entry.module());
            assertEquals("batch-delete", entry.action());
            assertEquals("BATCH", entry.targetType());
            assertEquals("", entry.targetId());
            assertEquals("soft-deleted 2 row(s)", entry.summary());
            assertEquals(100L, entry.operatorId());
        }
    }

    /**
     * 测试: operatorName 会话回填 — 登录会话存在时读取用户名（评审 P2/任务 #12）。
     */
    @Test
    void shouldReadOperatorNameFromSession() throws Throwable {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class);
             MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class);
             MockedStatic<TenantContextHolder> tenant = mockStatic(TenantContextHolder.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            tenant.when(TenantContextHolder::getTenantId).thenReturn(1L);
            SaSession session = mock(SaSession.class);
            when(session.get("operatorName")).thenReturn("alice");
            stp.when(StpUtil::getSession).thenReturn(session);

            setupJoinPoint(getClass(), "operatorIdMethod", new Object[]{42L, 999L},
                new String[]{"targetId", "operatorId"}, null);
            OperationLog opLog = getClass().getDeclaredMethod("operatorIdMethod", Long.class, Long.class)
                .getAnnotation(OperationLog.class);

            aspect.around(joinPoint, opLog);

            OperationLogEntry entry = captureEntry();
            assertEquals("alice", entry.operatorName());
            assertEquals(100L, entry.operatorId());
        }
    }

    /**
     * 测试: 无会话（SERVICE/TASK/ANONYMOUS 调用）→ operatorName 为 null，不阻断日志记录。
     */
    @Test
    void shouldReadNullOperatorNameWhenNoSession() throws Throwable {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class);
             MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class);
             MockedStatic<TenantContextHolder> tenant = mockStatic(TenantContextHolder.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            tenant.when(TenantContextHolder::getTenantId).thenReturn(1L);
            stp.when(StpUtil::getSession).thenThrow(new IllegalStateException("no session"));

            setupJoinPoint(getClass(), "operatorIdMethod", new Object[]{42L, 999L},
                new String[]{"targetId", "operatorId"}, null);
            OperationLog opLog = getClass().getDeclaredMethod("operatorIdMethod", Long.class, Long.class)
                .getAnnotation(OperationLog.class);

            aspect.around(joinPoint, opLog);

            OperationLogEntry entry = captureEntry();
            assertNull(entry.operatorName());
            assertEquals(100L, entry.operatorId());
        }
    }

    /**
     * 测试: 大对象参数在序列化前替换为元数据（评审 P1-2）。
     * <p>byte[]/MultipartFile 不写入实际内容（Base64/文件内容），仅保留 type/name/length/size 元数据。</p>
     */
    @Test
    void shouldSerializeLargeObjectsAsMetadata() throws Throwable {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class);
             MockedStatic<TenantContextHolder> tenant = mockStatic(TenantContextHolder.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            tenant.when(TenantContextHolder::getTenantId).thenReturn(1L);

            byte[] content = new byte[]{1, 2, 3};
            MultipartFile file = mock(MultipartFile.class);
            when(file.getName()).thenReturn("file");
            when(file.getOriginalFilename()).thenReturn("secret.bin");
            when(file.getSize()).thenReturn(1234L);

            setupJoinPoint(getClass(), "uploadMethod", new Object[]{content, file, "myfile"},
                new String[]{"content", "file", "filename"}, null);
            OperationLog opLog = getClass().getDeclaredMethod("uploadMethod", byte[].class, MultipartFile.class, String.class)
                .getAnnotation(OperationLog.class);

            aspect.around(joinPoint, opLog);

            OperationLogEntry entry = captureEntry();
            assertNotNull(entry.requestBody(), "方法参数应序列化为请求体");
            assertTrue(entry.requestBody().contains("\"type\":\"byte-array\""));
            assertTrue(entry.requestBody().contains("\"length\":3"));
            assertTrue(entry.requestBody().contains("\"type\":\"multipart-file\""));
            assertTrue(entry.requestBody().contains("\"size\":1234"));
            assertTrue(entry.requestBody().contains("\"originalFilename\":\"secret.bin\""), "文件名作为元数据展示");
            assertFalse(entry.requestBody().contains("AQID"), "byte[] 内容不得以 Base64 形式写入审计字段");
        }
    }

    /**
     * 测试: 超长请求头限长 + XFF 取代理链首地址（评审 P1-4）。
     * <p>operation_log.request_id/ip_address 均为 VARCHAR(64)，超长直接入库会触发插入失败丢失整条日志。</p>
     */
    @Test
    void shouldTruncateRequestIdAndIpHeader() throws Throwable {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class);
             MockedStatic<TenantContextHolder> tenant = mockStatic(TenantContextHolder.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            tenant.when(TenantContextHolder::getTenantId).thenReturn(1L);

            String longRequestId = "x".repeat(80);
            String longClientIp = "2001:db8::" + "a".repeat(70);
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.addHeader("X-Request-Id", longRequestId);
            req.addHeader("X-Forwarded-For", "  " + longClientIp + "  , 10.0.0.2");
            req.setRemoteAddr("192.168.1.1");
            req.setRequestURI("/api/roles");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
            try {
                setupJoinPoint(getClass(), "operatorIdMethod", new Object[]{42L, 999L},
                    new String[]{"targetId", "operatorId"}, null);
                OperationLog opLog = getClass().getDeclaredMethod("operatorIdMethod", Long.class, Long.class)
                    .getAnnotation(OperationLog.class);

                aspect.around(joinPoint, opLog);

                OperationLogEntry entry = captureEntry();
                assertEquals(longRequestId.substring(0, 64), entry.requestId(), "超长请求ID截断到列上限 64");
                assertEquals(64, entry.requestId().length());
                assertEquals(longClientIp.substring(0, 64), entry.ipAddress(), "XFF 取代理链第一个地址并限长 64");
                assertEquals("/api/roles", entry.requestUrl());
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        }
    }

    /**
     * 测试: 显式定序到事务切面外层（评审 P2/任务 #13）。
     */
    @Test
    void shouldOrderOutsideTransactionAspect() {
        Order order = OperationLogAspect.class.getAnnotation(Order.class);
        assertNotNull(order, "OperationLogAspect 必须有 @Order 显式定序");
        assertEquals(Ordered.LOWEST_PRECEDENCE - 1, order.value(),
            "位于事务切面（默认 LOWEST_PRECEDENCE）外层，主事务提交后再记录日志");
    }

    // ===== 匿名安全写租户解析（T-ACCESS-007 第三轮 P1#1）=====

    /** 含 tenantId 参数的方法（模拟 lockUser 登录失败自动锁定） */
    @OperationLog(module = "perm", action = "USER_LOCK", targetType = "sys_user",
        targetId = "#userId", summary = "'lock user ' + #userId")
    public void lockMethod(Long tenantId, Long userId) {
    }

    /** 无 tenantId 参数的方法（模拟 revokeToken / 匿名派生端点） */
    @OperationLog(module = "admin", action = "OAUTH2_TOKEN_REVOKE", targetType = "oauth2_token",
        targetId = "", summary = "'oauth2 revoke token'")
    public void anonymousMethod(String accessToken) {
    }

    @Test
    void shouldResolveTenantIdFromMethodParamWhenContextNull() throws Throwable {
        // 上下文为 null（匿名锁定链），但方法参数携带 tenantId → 优先取参数并正常记录
        try (MockedStatic<TenantContextHolder> tenant = mockStatic(TenantContextHolder.class)) {
            tenant.when(TenantContextHolder::getTenantId).thenReturn(null);

            setupJoinPoint(getClass(), "lockMethod", new Object[]{7L, 42L},
                new String[]{"tenantId", "userId"}, null);
            OperationLog opLog = getClass().getDeclaredMethod("lockMethod", Long.class, Long.class)
                .getAnnotation(OperationLog.class);

            aspect.around(joinPoint, opLog);

            OperationLogEntry entry = captureEntry();
            assertEquals(7L, entry.tenantId(), "应从方法参数 tenantId 解析租户，而非跳过日志");
        }
    }

    @Test
    void shouldSkipOperationLogWhenTenantUnresolvable() throws Throwable {
        // 参数无 tenantId 且上下文为 null（revokeToken 匿名派生端点）→ 跳过该条日志（不写 tenant_id=null）
        try (MockedStatic<TenantContextHolder> tenant = mockStatic(TenantContextHolder.class)) {
            tenant.when(TenantContextHolder::getTenantId).thenReturn(null);

            setupJoinPoint(getClass(), "anonymousMethod", new Object[]{"jwt-token"},
                new String[]{"accessToken"}, null);
            OperationLog opLog = getClass().getDeclaredMethod("anonymousMethod", String.class)
                .getAnnotation(OperationLog.class);

            aspect.around(joinPoint, opLog);

            verify(auditDomainService, never()).asyncRecordLog(any());
        }
    }

    /** 超长 targetId/summary 的方法（验证列上限截断，评审 P2#8） */
    @OperationLog(module = "perm", action = "BATCH_GRANT", targetType = "abstract_role",
        targetId = "#longId", summary = "'granted ' + #longId")
    public void truncateMethod(String longId) {
    }

    @Test
    void shouldTruncateTargetIdAndSummaryToColumnLimit() throws Throwable {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class);
             MockedStatic<TenantContextHolder> tenant = mockStatic(TenantContextHolder.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            tenant.when(TenantContextHolder::getTenantId).thenReturn(1L);

            String longId = "x".repeat(600);
            setupJoinPoint(getClass(), "truncateMethod", new Object[]{longId},
                new String[]{"longId"}, null);
            OperationLog opLog = getClass().getDeclaredMethod("truncateMethod", String.class)
                .getAnnotation(OperationLog.class);

            aspect.around(joinPoint, opLog);

            OperationLogEntry entry = captureEntry();
            assertEquals(256, entry.targetId().length(), "target_id 截断到 VARCHAR(256)");
            assertEquals(512, entry.summary().length(), "summary 截断到 VARCHAR(512)");
        }
    }
}
