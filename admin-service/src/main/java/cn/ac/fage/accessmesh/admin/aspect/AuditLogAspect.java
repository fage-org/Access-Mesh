package cn.ac.fage.accessmesh.admin.aspect;

import cn.ac.fage.accessmesh.admin.annotation.AuditLog;
import cn.ac.fage.accessmesh.admin.entity.SysAuditLog;
import cn.ac.fage.accessmesh.admin.mapper.SysAuditLogMapper;
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

@Aspect
@Component
public class AuditLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAspect.class);

    private final SysAuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    public AuditLogAspect(SysAuditLogMapper auditLogMapper, ObjectMapper objectMapper) {
        this.auditLogMapper = auditLogMapper;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        long start = System.currentTimeMillis();

        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        SysAuditLog entry = new SysAuditLog();
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
        } catch (Exception ignored) {
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
            log.warn("Failed to persist audit log: {}", e.getMessage());
        }

        return result;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
