package cn.ac.fage.accessmesh.access.admin.config;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 租户拦截器
 * <p>
 * 从请求头（X-Tenant-Id）或已认证用户上下文中提取租户ID，
 * 存储到TenantContextHolder供MyBatis-Flex自动租户过滤使用。
 * </p>
 *
 * <p>安全要求：所有非认证请求必须携带X-Tenant-Id请求头。
 * 缺少或无效的请求头将返回400错误。
 * 认证端点（/auth/）免除租户隔离。
 * </p>
 *
 * <p>安全验证：验证请求头租户ID与用户会话租户ID是否匹配。
 * </p>
 */
public class TenantInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantInterceptor.class);
    private static final String HEADER_TENANT_ID = "X-Tenant-Id";

    /**
     * 请求预处理
     * <p>
     * 提取并验证租户ID：
     * 1. 认证端点跳过租户隔离
     * 2. 优先使用显式请求头租户ID
     * 3. 安全验证请求头租户ID与会话租户ID匹配
     * 4. 其次从已登录用户会话获取租户ID
     * 5. 严格模式：非认证请求必须携带租户ID
     * </p>
     *
     * @param request  HTTP请求对象
     * @param response HTTP响应对象
     * @param handler  处理器对象
     * @return 验证通过返回true，否则返回false
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 认证端点和公共端点不需要租户隔离
        String uri = request.getRequestURI();
        if (uri.startsWith("/auth/")) {
            return true;
        }

        // 优先级1：显式请求头
        String tenantHeader = request.getHeader(HEADER_TENANT_ID);
        if (tenantHeader != null && !tenantHeader.isBlank()) {
            try {
                Long tenantIdFromHeader = Long.parseLong(tenantHeader.trim());

                // 安全验证 - 验证请求头租户ID与用户会话匹配
                if (StpUtil.isLogin()) {
                    SaSession session = StpUtil.getSession();
                    Long userTenantId = (Long) session.get("tenantId");
                    if (userTenantId != null && !userTenantId.equals(tenantIdFromHeader)) {
                        log.warn("租户ID不匹配: 请求头={}, 会话={}, 用户ID={}",
                            tenantIdFromHeader, userTenantId, StpUtil.getLoginIdAsLong());
                        writeErrorResponse(response, HttpServletResponse.SC_FORBIDDEN,
                            "租户ID与用户会话不匹配");
                        return false;
                    }
                }

                TenantContextHolder.setTenantId(tenantIdFromHeader);
                return true;
            } catch (NumberFormatException e) {
                log.warn("无效的X-Tenant-Id请求头: {}", tenantHeader);
                writeErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST,
                    "无效的X-Tenant-Id请求头格式");
                return false;
            }
        }

        // 优先级2：从已登录用户会话租户解析
        try {
            if (StpUtil.isLogin()) {
                SaSession session = StpUtil.getSession();
                Long userTenantId = (Long) session.get("tenantId");
                if (userTenantId != null) {
                    TenantContextHolder.setTenantId(userTenantId);
                    return true;
                }
                Long userId = StpUtil.getLoginIdAsLong();
                log.debug("用户 {} 会话中未找到租户ID", userId);
            }
        } catch (Exception e) {
            log.warn("从会话获取租户上下文失败: {}", e.getMessage());
            // 继续处理，但记录警告（租户上下文获取失败不阻止请求）
        }

        // 严格模式：所有非认证请求必须携带X-Tenant-Id
        log.warn("非认证请求缺少X-Tenant-Id请求头: {} {}",
            request.getMethod(), request.getRequestURI());
        writeErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST,
            "缺少必要请求头: X-Tenant-Id");
        return false;
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
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContextHolder.clear();
    }

    /**
     * 写入错误响应
     * <p>
     * 以统一的JSON格式写入错误响应。
     * </p>
     *
     * @param response HTTP响应对象
     * @param status   HTTP状态码
     * @param message  错误消息
     */
    private void writeErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        String json = String.format("{\"code\":%d,\"message\":\"%s\",\"data\":null,\"requestId\":null,\"traceId\":null}",
            status, message);
        response.getWriter().write(json);
    }
}