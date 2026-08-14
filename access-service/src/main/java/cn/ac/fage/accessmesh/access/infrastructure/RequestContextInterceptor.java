package cn.ac.fage.accessmesh.access.infrastructure;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.UUID;

/**
 * 统一可信请求上下文拦截器（T-ACCESS-004，替换 admin TenantInterceptor 与
 * permission PermTenantInterceptor 双链）。
 * <p>
 * 是唯一可以绑定 {@link AccessRequestContext} 的 HTTP 入口：外部请求头必须经过
 * 会话（Sa-Token）、签名（{@link SignatureVerifier}）或服务凭证（X-Internal-Secret，
 * 由前置 InternalApiSecretInterceptor 写入 attribute）验证后才能绑定。执行顺序：
 * </p>
 * <ol>
 *   <li>公开路径（/auth/**、/actuator/**）→ ANONYMOUS</li>
 *   <li>内部凭证通过（attribute INTERNAL_AUTHENTICATED）→
 *       X-User-Id 存在（恒已验签，防御纵深再校验）→ USER（签名代理主体）；
 *       无 X-User-Id → SERVICE（serviceCode 绑定 X-Service-Code 头，凭证通过即可信）</li>
 *   <li>Sa-Token 会话 → USER（会话权威：operatorId=loginId、tenantId=session 租户；
 *       X-Tenant-Id / X-User-Id 头存在必须与会话一致，否则 403 拒绝伪造头）</li>
 *   <li>签名用户态（/api/** 路径，HeaderSignatureInterceptor 验签通过的 X-User-Id）→ USER</li>
 *   <li>无身份非公开路径 → 401（显式门禁，不依赖服务层隐式异常）</li>
 * </ol>
 * <p>
 * 同时注入日志 MDC（traceId / userId / tenantId / serviceCode，配合 log4j2 JsonLayout
 * properties=true 输出），afterCompletion 统一清理上下文与 MDC，防线程池泄漏。
 * </p>
 */
@Component
public class RequestContextInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RequestContextInterceptor.class);

    /** 公开路径前缀（匿名可访问）。 */
    private static final String[] PUBLIC_PREFIXES = {"/auth/", "/actuator/"};

    /** 请求 ID 请求头（Gateway 注入）。 */
    private static final String HEADER_REQUEST_ID = "X-Request-Id";
    /** 服务编码请求头（perm-sdk / Gateway 注入，凭证通过后绑定）。 */
    private static final String HEADER_SERVICE_CODE = "X-Service-Code";

    /** MDC 键（log4j2 JsonLayout properties=true 输出）。 */
    private static final String MDC_TRACE_ID = "traceId";
    private static final String MDC_USER_ID = "userId";
    private static final String MDC_TENANT_ID = "tenantId";
    private static final String MDC_SERVICE_CODE = "serviceCode";

    private final SignatureVerifier signatureVerifier;

    public RequestContextInterceptor(SignatureVerifier signatureVerifier) {
        this.signatureVerifier = signatureVerifier;
    }

    /**
     * 按安全策略矩阵建立可信上下文；验证失败时写错误响应并返回 false。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws IOException {
        // 评审 P1-1（2026-08-14）：ERROR dispatch（转发 /error）不做身份判定与上下文绑定，
        // 避免真实错误（404/400/500）被 401 掩蔽、安全日志被伪告警污染。
        // 清理语义：未处理异常路径下正常 dispatch 的 afterCompletion 不执行，
        // 但 ERROR dispatch 的 preHandle(true) → 链走完 → afterCompletion 必然清理；
        // 修复前 401 分支 preHandle=false 导致两次 dispatch 均不清理、上下文残留池线程。
        if (request.getDispatcherType() == jakarta.servlet.DispatcherType.ERROR) {
            return true;
        }

        String uri = request.getRequestURI();

        // 1. 公开路径：匿名上下文（登录/验证码/OAuth2/actuator 健康检查）
        if (isPublicPath(uri)) {
            AccessRequestContext.bind(RequestContext.anonymous());
            setMdc(request, null, null, null);
            return true;
        }

        boolean internalAuthenticated = Boolean.TRUE.equals(
            request.getAttribute(SecurityAttributes.ATTR_INTERNAL_AUTHENTICATED));
        boolean signatureVerified = Boolean.TRUE.equals(
            request.getAttribute(SecurityAttributes.ATTR_SIGNATURE_VERIFIED));
        String userIdHeader = request.getHeader(SignatureVerifier.HEADER_USER_ID);
        boolean hasUserIdHeader = userIdHeader != null && !userIdHeader.isBlank();

        // 2. 内部凭证路径（仅 /api/perm/**，InternalApiSecretInterceptor 验证通过后）
        if (internalAuthenticated) {
            if (hasUserIdHeader) {
                // 凭证 + 用户身份头：必须已验签（HeaderSignature 已拦截未验签者；纵深校验防链序绕过）
                if (!signatureVerified) {
                    logSecurity(request, "internal-authenticated request carries X-User-Id without valid signature");
                    writeJson(response, HttpServletResponse.SC_FORBIDDEN, "拒绝访问");
                    return false;
                }
                Long operatorId = signatureVerifier.parseUserId(request);
                Long tenantId = signatureVerifier.parseTenantId(request);
                if (operatorId == null || tenantId == null) {
                    logSecurity(request, "invalid X-User-Id / X-Tenant-Id header format");
                    writeJson(response, HttpServletResponse.SC_BAD_REQUEST, "无效的请求头格式");
                    return false;
                }
                AccessRequestContext.bind(RequestContext.user(tenantId, operatorId));
                setMdc(request, String.valueOf(operatorId), String.valueOf(tenantId), null);
                return true;
            }
            // 纯服务调用：serviceCode 在凭证通过后绑定（防无凭证外部伪造）
            String serviceCode = request.getHeader(HEADER_SERVICE_CODE);
            Long tenantId = signatureVerifier.parseTenantId(request);
            if (tenantId == null) {
                logSecurity(request, "service call missing X-Tenant-Id");
                writeJson(response, HttpServletResponse.SC_BAD_REQUEST, "缺少必要请求头: X-Tenant-Id");
                return false;
            }
            AccessRequestContext.bind(RequestContext.service(tenantId, serviceCode));
            setMdc(request, null, String.valueOf(tenantId), serviceCode);
            return true;
        }

        // 3. Sa-Token 会话权威（admin 域接口 / 直连用户态）
        if (StpUtil.isLogin()) {
            // 评审 P2-5（2026-08-14）：isLogin 与读取之间会话可能失效（Redis 过期/双端失效），
            // 竞态导致的 NotLoginException 视为未登录 → 401（显式门禁），不产生 500；
            // 仅收窄捕获该竞态异常，基础设施故障（Redis 异常等）不伪装为 401。
            Long loginId;
            Long sessionTenantId;
            try {
                loginId = StpUtil.getLoginIdAsLong();
                sessionTenantId = sessionTenantId();
            } catch (cn.dev33.satoken.exception.NotLoginException e) {
                logSecurity(request, "session invalidated during read");
                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "未登录或会话已过期");
                return false;
            }

            // X-Tenant-Id 头存在必须与会话租户一致（伪造头拒绝）；
            // 格式非法 → 400（评审 P2-2：与旧 TenantInterceptor 语义一致），合法但不一致 → 403
            String tenantHeader = request.getHeader(SignatureVerifier.HEADER_TENANT_ID);
            if (tenantHeader != null && !tenantHeader.isBlank()) {
                Long headerTenantId = signatureVerifier.parseTenantId(request);
                if (headerTenantId == null) {
                    logSecurity(request, "invalid X-Tenant-Id header format");
                    writeJson(response, HttpServletResponse.SC_BAD_REQUEST, "无效的X-Tenant-Id请求头格式");
                    return false;
                }
                if (!headerTenantId.equals(sessionTenantId)) {
                    logSecurity(request, "tenant mismatch: header vs session");
                    writeJson(response, HttpServletResponse.SC_FORBIDDEN, "租户ID与用户会话不匹配");
                    return false;
                }
            } else if (sessionTenantId == null) {
                // 头缺失且会话无租户：无法确定租户（现状严格模式语义）
                logSecurity(request, "no tenant from header or session");
                writeJson(response, HttpServletResponse.SC_BAD_REQUEST, "缺少必要请求头: X-Tenant-Id");
                return false;
            }

            // X-User-Id 头存在必须与会话登录用户一致（伪造头拒绝）；格式非法 → 400
            if (hasUserIdHeader) {
                Long headerUserId = signatureVerifier.parseUserId(request);
                if (headerUserId == null) {
                    logSecurity(request, "invalid X-User-Id header format");
                    writeJson(response, HttpServletResponse.SC_BAD_REQUEST, "无效的X-User-Id请求头格式");
                    return false;
                }
                if (!headerUserId.equals(loginId)) {
                    logSecurity(request, "user mismatch: header vs session");
                    writeJson(response, HttpServletResponse.SC_FORBIDDEN, "用户ID与用户会话不匹配");
                    return false;
                }
            }

            AccessRequestContext.bind(RequestContext.user(sessionTenantId, loginId));
            setMdc(request, String.valueOf(loginId),
                sessionTenantId == null ? null : String.valueOf(sessionTenantId), null);
            return true;
        }

        // 4. 签名用户态（无会话：业务服务替用户调 /api/perm/auth/** 等，
        //    HeaderSignatureInterceptor 已验签；admin 路径无验签保障不会到达此分支）
        if (hasUserIdHeader && signatureVerified) {
            Long operatorId = signatureVerifier.parseUserId(request);
            Long tenantId = signatureVerifier.parseTenantId(request);
            if (operatorId == null || tenantId == null) {
                logSecurity(request, "invalid X-User-Id / X-Tenant-Id header format");
                writeJson(response, HttpServletResponse.SC_BAD_REQUEST, "无效的请求头格式");
                return false;
            }
            AccessRequestContext.bind(RequestContext.user(tenantId, operatorId));
            setMdc(request, String.valueOf(operatorId), String.valueOf(tenantId), null);
            return true;
        }

        // 5. 无身份非公开路径：显式门禁（不依赖服务层隐式异常）
        logSecurity(request, "unauthenticated request to protected path");
        writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "未登录或会话已过期");
        return false;
    }

    /**
     * 请求完成（含异常路径）后统一清理上下文与 MDC，防线程池泄漏。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AccessRequestContext.clear();
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_USER_ID);
        MDC.remove(MDC_TENANT_ID);
        MDC.remove(MDC_SERVICE_CODE);
    }

    private static boolean isPublicPath(String uri) {
        for (String prefix : PUBLIC_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 读取会话租户键（login 时写入 user.getTenantId()）。
     */
    private static Long sessionTenantId() {
        Object tenantId = StpUtil.getSession().get("tenantId");
        return tenantId instanceof Long l ? l : null;
    }

    /**
     * 注入日志 MDC（traceId 取 X-Request-Id，无则生成 UUID）。
     * 评审 P2-4（2026-08-14）：外部可控值（X-Request-Id / X-Service-Code）写入前截断 64 字符，
     * 防止日志膨胀与伪造 traceId 干扰日志关联。
     */
    private static void setMdc(HttpServletRequest request, String userId, String tenantId,
                               String serviceCode) {
        String requestId = request.getHeader(HEADER_REQUEST_ID);
        String traceId = requestId != null && !requestId.isBlank() ? requestId
            : UUID.randomUUID().toString();
        MDC.put(MDC_TRACE_ID, truncate(traceId));
        if (userId != null) {
            MDC.put(MDC_USER_ID, userId);
        }
        if (tenantId != null) {
            MDC.put(MDC_TENANT_ID, tenantId);
        }
        if (serviceCode != null) {
            MDC.put(MDC_SERVICE_CODE, truncate(serviceCode));
        }
    }

    private static String truncate(String value) {
        return value.length() > 64 ? value.substring(0, 64) : value;
    }

    /**
     * 拒绝路径的结构化安全日志（固定文案 + 数字过滤后的标识，防日志注入）。
     */
    private static void logSecurity(HttpServletRequest request, String detail) {
        log.warn("security: {} | method={} | uri={} | ip={}", detail, request.getMethod(),
            request.getRequestURI(), request.getRemoteAddr());
    }

    /**
     * 以统一 JSON 格式写入错误响应。
     */
    private static void writeJson(HttpServletResponse response, int status, String message)
        throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        String json = String.format("{\"code\":%d,\"message\":\"%s\",\"data\":null,\"requestId\":null,\"traceId\":null}",
            status, message);
        response.getWriter().write(json);
    }
}
