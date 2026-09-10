package cn.ac.fage.accessmesh.access.infrastructure.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * HTTP 请求上下文工具（跨 admin/permission 域通用）。
 * <p>
 * 集中提取客户端 IP、请求 ID、User-Agent 等审计字段，并统一限长对齐列上限，
 * 避免 OperationLogAspect 与登录日志（AuthServiceImpl）各写一份重复逻辑且口径漂移。
 * </p>
 */
public final class HttpRequestUtils {

    /** 请求ID/客户端IP入库上限（对齐 operation_log.request_id / ip_address VARCHAR(64)） */
    public static final int REQUEST_ID_MAX_LEN = 64;

    /** IP地址入库上限（对齐 operation_log.ip_address VARCHAR(64)） */
    public static final int IP_ADDRESS_MAX_LEN = 64;

    /** User-Agent 入库上限（对齐 sys_login_log.user_agent VARCHAR(512)） */
    public static final int USER_AGENT_MAX_LEN = 512;

    private HttpRequestUtils() {
    }

    /**
     * 获取当前 HTTP 请求（无请求上下文时返回 null，如定时任务/异步调用触发）。
     */
    public static HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取客户端IP地址（优先 X-Forwarded-For 首段，否则远程地址），按列上限限长
     * （VARCHAR(64)，超长直接入库会触发插入失败丢失整条日志）。XFF 经 Gateway
     * 部署时为 Gateway 重建单值（值=Gateway 观测的 remoteAddr，T-GW-008，
     * 见 gateway.md §请求头清洗与客户端 IP 重建）；直连本服务时为请求方可伪造声明。
     *
     * @param request HTTP 请求；为 null 时返回 null
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            // 取首段（Gateway 链路下该值=Gateway 重建的 remoteAddr，T-GW-008）
            ip = ip.split(",")[0].trim();
        } else {
            ip = request.getRemoteAddr();
        }
        return truncate(ip, IP_ADDRESS_MAX_LEN);
    }

    /**
     * 从请求头获取请求ID（兼容网关透传 X-Request-Id），按列上限限长（VARCHAR(64)）。
     *
     * @param request HTTP 请求；为 null 时返回 null
     */
    public static String getRequestId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return truncate(request.getHeader("X-Request-Id"), REQUEST_ID_MAX_LEN);
    }

    /**
     * 获取 User-Agent（浏览器信息），按列上限限长（sys_login_log.user_agent VARCHAR(512)）。
     *
     * @param request HTTP 请求；为 null 时返回 null
     */
    public static String getUserAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return truncate(request.getHeader("User-Agent"), USER_AGENT_MAX_LEN);
    }

    /**
     * 限长工具：超过 {@code maxLen} 截断，否则原样返回；null 返回 null。
     */
    public static String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }
}
