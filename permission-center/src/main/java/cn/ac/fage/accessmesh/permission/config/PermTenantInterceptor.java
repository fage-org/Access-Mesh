package cn.ac.fage.accessmesh.permission.config;

import cn.ac.fage.accessmesh.permission.util.SecurityEventType;
import cn.ac.fage.accessmesh.permission.util.SecurityLogUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 租户拦截器
 * <p>
 * 从请求头中读取X-Tenant-Id并存储到TenantContextHolder。
 * 请求完成后清理上下文，防止线程池环境下的泄漏。
 * </p>
 *
 * <p>安全要求：X-Tenant-Id请求头是必须的。缺少或无效的请求头将返回400错误。
 * </p>
 */
@Component
public class PermTenantInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(PermTenantInterceptor.class);
    private static final String HEADER_TENANT_ID = "X-Tenant-Id";

    /**
     * 请求预处理
     * <p>
     * 验证X-Tenant-Id请求头是否存在且格式有效。
     * 有效时设置租户上下文，无效时拒绝请求。
     * </p>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @param handler  处理器对象
     * @return 验证通过返回true，否则返回false
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String tenantIdStr = request.getHeader(HEADER_TENANT_ID);

        // 检查请求头是否存在
        if (tenantIdStr == null || tenantIdStr.isBlank()) {
            SecurityLogUtil.logSecurityEvent(
                SecurityEventType.BLOCKED_REQUEST,
                request,
                "缺少必要请求头: X-Tenant-Id",
                null,
                null
            );
            writeErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "缺少必要请求头: X-Tenant-Id");
            return false;
        }

        // 解析并设置租户ID
        try {
            Long tenantId = Long.parseLong(tenantIdStr.trim());
            TenantContextHolder.setTenantId(tenantId);
        } catch (NumberFormatException e) {
            // 使用结构化日志防止日志注入攻击
            SecurityLogUtil.logSecurityEvent(
                SecurityEventType.SUSPICIOUS_INPUT,
                request,
                "无效的X-Tenant-Id请求头格式",
                null,
                tenantIdStr
            );
            writeErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "无效的X-Tenant-Id请求头格式");
            return false;
        }

        return true;
    }

    /**
     * 请求完成后清理
     * <p>
     * 清理租户上下文，防止线程池环境下的租户ID泄漏。
     * </p>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @param handler  处理器对象
     * @param ex       异常对象
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        TenantContextHolder.clear();
    }

    /**
     * 写入错误响应
     * <p>
     * 以统一的JSON格式（PermResult格式）写入错误响应。
     * </p>
     *
     * @param response HTTP响应对象
     * @param status   HTTP状态码
     * @param message  错误消息
     */
    private void writeErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        // PermResult格式: {"code":400,"message":"...","data":null,"requestId":null,"traceId":null}
        String json = String.format("{\"code\":%d,\"message\":\"%s\",\"data\":null,\"requestId\":null,\"traceId\":null}", status, message);
        response.getWriter().write(json);
    }
}