package cn.ac.fage.accessmesh.permission.aop;

import cn.ac.fage.accessmesh.permission.config.TenantContextHolder;
import cn.ac.fage.accessmesh.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.permission.util.OperatorContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 操作日志切面
 * <p>
 * 拦截标注了 {@link OperationLog} 的 AppService 方法，
 * 自动调用 AuditDomainService.asyncRecordLog 记录入口级操作日志。
 * 内部动态日志（diff 快照、冲突通知）仍由 AuditDomainService 显式调用。
 * </p>
 */
@Aspect
@Component
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);

    private final AuditDomainService auditDomainService;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public OperationLogAspect(AuditDomainService auditDomainService) {
        this.auditDomainService = auditDomainService;
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
        try {
            Object result = joinPoint.proceed();
            try {
                recordLog(opLog, joinPoint, result);
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
    private void recordLog(OperationLog opLog, ProceedingJoinPoint joinPoint, Object result) {
        OperationLogRuntimeContext.Snapshot runtimeSnapshot = OperationLogRuntimeContext.snapshot();
        if (runtimeSnapshot.skip()) {
            return;
        }

        Long tenantId = resolveTenantId(joinPoint);
        if (tenantId == null) {
            tenantId = TenantContextHolder.getTenantId();
        }

        Long operatorId;
        try {
            operatorId = OperatorContext.getOperatorId();
        } catch (Exception e) {
            operatorId = null;
        }

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

        auditDomainService.asyncRecordLog(
            opLog.module(),
            opLog.action(),
            targetType,
            resolveTargetIdAsLong(targetId),
            summary,
            operatorId,
            null,
            null,
            tenantId
        );
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
     * 从 TenantContextHolder 获取租户ID
     */
    private Long resolveTenantId(ProceedingJoinPoint joinPoint) {
        return TenantContextHolder.getTenantId();
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

    /**
     * 将 targetId 字符串转换为 Long（用于 targetId 参数），失败返回 null
     */
    private Long resolveTargetIdAsLong(String targetId) {
        if (targetId == null || targetId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(targetId);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
