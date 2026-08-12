package cn.ac.fage.accessmesh.access.admin.aspect;

import cn.ac.fage.accessmesh.access.admin.annotation.AuditLog;
import cn.ac.fage.accessmesh.access.infrastructure.entity.OperationLog;
import cn.ac.fage.accessmesh.access.infrastructure.mapper.OperationLogMapper;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 审计日志切面
 * <p>
 * 自动拦截带有 @AuditLog 注解的方法，记录操作审计日志。
 * 包括请求信息、用户信息、执行时间和响应状态等。
 * </p>
 */
@Aspect
@Component
public class AuditLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAspect.class);

    private final OperationLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    /**
     * 构造函数
     *
     * @param auditLogMapper 审计日志Mapper（T-ACCESS-002 归并后为 OperationLogMapper）
     * @param objectMapper   JSON序列化工具
     */
    public AuditLogAspect(OperationLogMapper auditLogMapper, ObjectMapper objectMapper) {
        this.auditLogMapper = auditLogMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 环绕通知，拦截带有 @AuditLog 注解的方法
     * <p>
     * 记录方法执行的完整审计信息，包括请求参数、执行时间、响应状态等。
     * </p>
     *
     * @param joinPoint 切点
     * @param auditLog  审计日志注解
     * @return 方法执行结果
     * @throws Throwable 方法执行异常
     */
    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        long start = System.currentTimeMillis();

        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        OperationLog entry = new OperationLog();
        entry.setModule(auditLog.module());
        entry.setAction(auditLog.action());
        entry.setTargetType(auditLog.targetType());
        entry.setRequestUrl(request.getRequestURI());
        entry.setIpAddress(getClientIp(request));

        try {
            entry.setRequestBody(truncate(objectMapper.writeValueAsString(joinPoint.getArgs()), 2000));
        } catch (Exception e) {
            entry.setRequestBody("[unserializable]");
        }

        try {
            if (StpUtil.isLogin()) {
                entry.setUserId(StpUtil.getLoginIdAsLong());
                entry.setUsername(StpUtil.getLoginIdAsString());
            }
        } catch (Exception e) {
            log.warn("获取审计日志登录信息失败: {}", e.getMessage());
        }

        Object result = null;
        try {
            result = joinPoint.proceed();
            entry.setResponseCode(200);
        } catch (Exception e) {
            entry.setResponseCode(500);
            throw e;
        } finally {
            entry.setCostTime((int) (System.currentTimeMillis() - start));
            entry.setCreatedAt(java.time.LocalDateTime.now());
        }

        try {
            auditLogMapper.insert(entry);
        } catch (Exception e) {
            log.warn("保存审计日志失败: {}", e.getMessage());
        }

        return result;
    }

    /**
     * 获取客户端IP地址
     * <p>
     * 优先从 X-Forwarded-For 请求头获取，否则使用远程地址。
     * </p>
     *
     * @param request HTTP请求
     * @return 客户端IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    /**
     * 截断字符串到指定长度
     * <p>
     * 超过最大长度时添加省略号后缀。
     * </p>
     *
     * @param s      原始字符串
     * @param maxLen 最大长度
     * @return 截断后的字符串
     */
    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
