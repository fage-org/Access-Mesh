package cn.ac.fage.accessmesh.access.permission.aop;

import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLog;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLogRuntimeContext;
import cn.ac.fage.accessmesh.access.infrastructure.util.HttpRequestUtils;
import cn.ac.fage.accessmesh.access.infrastructure.util.SensitiveDataUtils;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 操作日志切面
 * <p>
 * 拦截标注了 {@link OperationLog} 的 AppService 方法，
 * 自动调用 AuditDomainService.asyncRecordLog 记录入口级操作日志。
 * 在同步线程采集 HTTP 上下文（requestUrl/ipAddress）、序列化并脱敏限长请求体，
 * 记录响应码与耗时；日志写入由异步线程池承担（独立短事务，失败不影响主业务）。
 * 内部动态日志（diff 快照、冲突通知）仍由 AuditDomainService 显式调用。
 * </p>
 * <p>
 * <b>切面定序</b>：{@code @Order(Ordered.LOWEST_PRECEDENCE - 1)} 使本切面位于
 * 事务切面（默认 LOWEST_PRECEDENCE）之外层——目标方法（含其事务提交）完全返回后才
 * 记录日志：主事务提交失败时 {@code joinPoint.proceed()} 抛异常走 finally 分支不记录，
 * 避免残留 responseCode=200 的虚假操作日志；主事务提交成功后记录，日志反映真实已提交操作。
 * </p>
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);

    /** 登录会话中操作者名称键（AuthServiceImpl 登录成功写入，本切面读取回填） */
    private static final String OPERATOR_NAME_SESSION_KEY = "operatorName";

    /** operation_log.target_id 列上限（VARCHAR(256)） */
    private static final int TARGET_ID_MAX_LEN = 256;
    /** operation_log.summary 列上限（VARCHAR(512)） */
    private static final int SUMMARY_MAX_LEN = 512;
    /** operation_log.operator_name 列上限（VARCHAR(256)） */
    private static final int OPERATOR_NAME_MAX_LEN = 256;

    private final AuditDomainService auditDomainService;
    private final ObjectMapper objectMapper;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public OperationLogAspect(AuditDomainService auditDomainService, ObjectMapper objectMapper) {
        this.auditDomainService = auditDomainService;
        this.objectMapper = objectMapper;
    }

    /**
     * 环绕拦截 @OperationLog 方法
     * <p>
     * 方法先执行再记录日志，因此 #result 在 SpEL 中可用。
     * 方法抛异常时不做日志记录，异常直接向上传播。
     * </p>
     */
    @Around("@annotation(opLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog opLog) throws Throwable {
        OperationLogRuntimeContext.clear();
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            try {
                recordLog(opLog, joinPoint, result, (int) (System.currentTimeMillis() - start));
            } catch (Exception e) {
                log.warn("Failed to record operation log for {}.{}: {}",
                    opLog.module(), opLog.action(), e.getMessage());
            }
            return result;
        } finally {
            OperationLogRuntimeContext.clear();
        }
    }

    /**
     * 记录操作日志
     */
    private void recordLog(OperationLog opLog, ProceedingJoinPoint joinPoint, Object result, int costTime) {
        OperationLogRuntimeContext.Snapshot runtimeSnapshot = OperationLogRuntimeContext.snapshot();
        if (runtimeSnapshot.skip()) {
            return;
        }

        Long tenantId = runtimeSnapshot.tenantIdOverride() != null
            ? runtimeSnapshot.tenantIdOverride()
            : resolveTenantId(joinPoint);
        if (tenantId == null) {
            log.warn("operation log for {}.{} skipped: no tenant context (module={}, action={})",
                opLog.module(), opLog.action(), opLog.module(), opLog.action());
            return;
        }

        Long operatorId;
        try {
            operatorId = OperatorContext.getOperatorId();
        } catch (Exception e) {
            operatorId = null;
        }
        // 操作者名称从登录会话读取（AuthServiceImpl 登录成功写入）；未登录/无会话调用为 null
        String operatorName = resolveOperatorName();

        EvaluationContext ctx = buildEvaluationContext(joinPoint, result);

        String targetType = runtimeSnapshot.targetTypeOverride() != null
            ? runtimeSnapshot.targetTypeOverride()
            : parseSpelOrDefault(opLog.targetType(), ctx, opLog.targetType());
        String targetId = runtimeSnapshot.targetIdOverride() != null
            ? runtimeSnapshot.targetIdOverride()
            : parseSpelOrDefault(opLog.targetId(), ctx, "");
        String summary = runtimeSnapshot.summaryOverride() != null
            ? runtimeSnapshot.summaryOverride()
            : buildSummary(opLog, ctx, result);
        // 对齐 operation_log 列上限截断（target_id VARCHAR(256)/summary VARCHAR(512)/operator_name VARCHAR(256)），
        // 防止超长 SpEL 结果或会话名触发插入失败丢失整条审计日志（T-ACCESS-007）
        targetId = truncate(targetId, TARGET_ID_MAX_LEN);
        summary = truncate(summary, SUMMARY_MAX_LEN);
        operatorName = truncate(operatorName, OPERATOR_NAME_MAX_LEN);

        HttpServletRequest request = HttpRequestUtils.currentRequest();
        String ipAddress = HttpRequestUtils.getClientIp(request);

        auditDomainService.asyncRecordLog(new AuditDomainService.OperationLogEntry(
            tenantId,
            opLog.module(),
            opLog.action(),
            targetType,
            targetId,
            summary,
            operatorId,
            operatorName,
            ipAddress,
            HttpRequestUtils.getRequestId(request),
            request != null ? request.getRequestURI() : null,
            maskRequestBody(joinPoint),
            200,
            costTime
        ));
    }

    /**
     * 序列化方法参数为请求体并脱敏限长（T-ACCESS-007 §8.2）。
     * <p>
     * 将方法参数按参数名包装为 {@code Map} 后序列化，使敏感字段名
     * （password/pwd/secret/token/…）在 JSON 中可被 {@link SensitiveDataUtils}
     * 按字段名匹配识别（Jackson 树遍历脱敏）——位置参数直接序列化会退化为
     * JSON 数组，数组元素无字段名，明文密码将无法脱敏入库。参数名不可用时降级为数组序列化。
     * 密码/验证码/Token/密钥等敏感字段值替换为掩码，超长时截断追加省略号；
     * 序列化失败返回 null（不阻断日志记录）。
     * 文件/流/二进制等大对象参数在序列化前替换为元数据（见 {@link #toSafeSerializableValue}），
     * 避免完整序列化文件内容并写入审计字段。
     * </p>
     */
    private String maskRequestBody(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return null;
        }
        try {
            Object body = args;
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
            if (paramNames != null && paramNames.length == args.length) {
                Map<String, Object> named = new LinkedHashMap<>();
                for (int i = 0; i < args.length; i++) {
                    named.put(paramNames[i], toSafeSerializableValue(args[i]));
                }
                body = named;
            } else {
                Object[] safeArgs = new Object[args.length];
                for (int i = 0; i < args.length; i++) {
                    safeArgs[i] = toSafeSerializableValue(args[i]);
                }
                body = safeArgs;
            }
            return SensitiveDataUtils.maskRequestBody(
                objectMapper.writeValueAsString(body), SensitiveDataUtils.REQUEST_BODY_MAX_LEN);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 将可能含大对象/流的参数替换为安全元数据，仅保留描述性信息。
     * <p>
     * MultipartFile/Part（文件上传）、byte[]（二进制）、InputStream/OutputStream/Reader/Writer、
     * File/Resource、HttpServletRequest/Response/Session 均不序列化实际内容：
     * 文件内容可能高达数十 MB，完整序列化会（a）在请求线程上构造大字节数组/Base64 造成性能问题，
     * （b）把文件开头内容写入审计字段。替换为包含类名/名称/大小的元数据 Map。
     * </p>
     */
    private Object toSafeSerializableValue(Object arg) {
        if (arg == null) {
            return null;
        }
        if (arg instanceof MultipartFile mf) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("type", "multipart-file");
            meta.put("name", mf.getName());
            meta.put("originalFilename", mf.getOriginalFilename());
            meta.put("size", mf.getSize());
            return meta;
        }
        if (arg instanceof Part part) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("type", "part");
            meta.put("name", part.getName());
            meta.put("size", part.getSize());
            return meta;
        }
        if (arg instanceof byte[] bytes) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("type", "byte-array");
            meta.put("length", bytes.length);
            return meta;
        }
        if (arg instanceof InputStream || arg instanceof OutputStream
            || arg instanceof Reader || arg instanceof Writer
            || arg instanceof File || arg instanceof Resource
            || arg instanceof HttpServletRequest || arg instanceof HttpServletResponse
            || arg instanceof HttpSession) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("type", arg.getClass().getSimpleName());
            return meta;
        }
        return arg;
    }

    /**
     * 从登录会话读取操作者名称（AuthServiceImpl 登录成功写入 {@link #OPERATOR_NAME_SESSION_KEY}）。
     * 未登录/无会话（SERVICE/TASK/ANONYMOUS 调用）返回 null，不阻断日志记录。
     */
    private String resolveOperatorName() {
        try {
            SaSession session = StpUtil.getSession();
            Object name = session.get(OPERATOR_NAME_SESSION_KEY);
            return name != null ? name.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 按列上限截断字符串；null 或未超长原样返回。
     */
    private static String truncate(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }

    /**
     * 构建 SpEL 求值上下文，包含方法参数和返回值
     */
    private EvaluationContext buildEvaluationContext(ProceedingJoinPoint joinPoint, Object result) {
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
            Object[] args = joinPoint.getArgs();

            if (paramNames != null) {
                for (int i = 0; i < paramNames.length && i < args.length; i++) {
                    ctx.setVariable(paramNames[i], args[i]);
                }
            }
            ctx.setVariable("result", result);
        } catch (Exception e) {
            log.debug("Failed to build evaluation context: {}", e.getMessage());
        }
        return ctx;
    }

    /**
     * 构建摘要：如果表达式引用 #result 但 result 为 null（void 方法），降级为空字符串
     */
    private String buildSummary(OperationLog opLog, EvaluationContext ctx, Object result) {
        String summaryExpr = opLog.summary();
        if (summaryExpr == null || summaryExpr.isBlank()) {
            return opLog.module() + "." + opLog.action();
        }

        // void 方法中 #result 不可用，引用 #result 时降级为空字符串
        if (result == null && summaryExpr.contains("#result")) {
            return "";
        }

        return parseSpelOrDefault(summaryExpr, ctx, opLog.module() + "." + opLog.action());
    }

    /**
     * 解析目标操作所属租户 ID（T-ACCESS-007）。
     * <p>
     * 匿名安全写链路（登录失败自动锁定等）在拦截器仅绑定 ANONYMOUS 上下文，
     * TenantContextHolder.getTenantId() 为 null，但方法参数携带真实 tenantId。
     * 解析顺序：
     * </p>
     * <ol>
     *   <li>方法参数中命名为 {@code tenantId} 的 {@code Long}（或数值可强转）实参；</li>
     *   <li>回退 {@link TenantContextHolder#getTenantId()}。</li>
     * </ol>
     * 两者皆空（真正匿名派生端点如 OAuth2 token/revoke）时调用方跳过该条日志并告警。
     *
     * @param joinPoint 被拦截方法
     * @return 租户 ID；不可得返回 null
     */
    private Long resolveTenantId(ProceedingJoinPoint joinPoint) {
        Long fromParam = resolveTenantIdFromParam(joinPoint);
        if (fromParam != null) {
            return fromParam;
        }
        return TenantContextHolder.getTenantId();
    }

    /**
     * 从方法参数中按参数名提取 {@code tenantId} 实参。
     * <p>
     * 参数名不可用（未编译 -parameters）或类型不可解析为数值时返回 null。
     * </p>
     */
    private Long resolveTenantIdFromParam(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return null;
        }
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
            if (paramNames == null) {
                return null;
            }
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                if (!"tenantId".equals(paramNames[i]) || args[i] == null) {
                    continue;
                }
                Object value = args[i];
                if (value instanceof Long l) {
                    return l;
                }
                if (value instanceof Number n) {
                    return n.longValue();
                }
                if (value instanceof String s && !s.isBlank()) {
                    try {
                        return Long.parseLong(s);
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to resolve tenantId from method param: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 解析 SpEL 表达式，失败时返回默认值
     */
    private String parseSpelOrDefault(String spel, EvaluationContext context, String defaultValue) {
        if (spel == null || spel.isBlank()) {
            return defaultValue;
        }
        try {
            Expression expression = parser.parseExpression(spel);
            Object value = expression.getValue(context);
            return value != null ? value.toString() : defaultValue;
        } catch (Exception e) {
            log.debug("Failed to evaluate SpEL expression '{}': {}", spel, e.getMessage());
            return defaultValue;
        }
    }

}
