package cn.ac.fage.accessmesh.access.infrastructure;

import cn.ac.fage.accessmesh.access.admin.entity.SysOauth2Client;
import cn.ac.fage.accessmesh.access.admin.service.domain.OAuth2ClientDomainService;
import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.jwt.SaJwtTemplate;
import cn.dev33.satoken.jwt.SaJwtUtil;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 *   <li>公开路径（/auth/** 公开子集：验证码/登录/令牌/撤销/登出；/actuator/**）→ ANONYMOUS</li>
 *   <li>内部凭证通过（attribute INTERNAL_AUTHENTICATED）→
 *       X-User-Id 存在（恒已验签，防御纵深再校验）→ USER（签名代理主体）；
 *       无 X-User-Id → SERVICE（serviceCode 绑定 X-Service-Code 头，凭证通过即可信）</li>
 *   <li>OAuth2 JWT（Bearer 三段式，仅显式配置的开放路径 access.oauth2.resource-paths，
 *       默认仅 /auth/oauth2/userinfo）→ 验签 + 撤销黑名单 + 客户端启用校验 + 路径门禁
 *       （clientIds/scope/audience，T-ACCESS-013 独立映射）→ USER + delegatedClientId；
 *       未配置路径上委托令牌默认拒绝（业务 API 逐项显式放开，不得通配 /auth/oauth2/**）</li>
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
public class RequestContextInterceptor implements AsyncHandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RequestContextInterceptor.class);

    /** 请求 ID 请求头（Gateway 注入）。 */
    private static final String HEADER_REQUEST_ID = "X-Request-Id";
    /** 服务编码请求头（perm-sdk / Gateway 注入，凭证通过后绑定）。 */
    private static final String HEADER_SERVICE_CODE = "X-Service-Code";

    /** MDC 键（log4j2 JsonLayout properties=true 输出）。 */
    private static final String MDC_TRACE_ID = "traceId";
    private static final String MDC_USER_ID = "userId";
    private static final String MDC_TENANT_ID = "tenantId";
    private static final String MDC_SERVICE_CODE = "serviceCode";

    /** 平台用户会话请求头（Bearer 前缀）。 */
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final SignatureVerifier signatureVerifier;
    private final StringRedisTemplate stringRedisTemplate;
    private final OAuth2ResourcePathProperties oauth2ResourcePaths;
    private final OAuth2ClientDomainService oauth2ClientDomainService;

    /** OAuth2 JWT 签发/验签密钥（sa-token.jwt-secret-key，T-ACCESS-003 权威配置）。 */
    @Value("${sa-token.jwt-secret-key:}")
    private String jwtSecretKey;

    public RequestContextInterceptor(SignatureVerifier signatureVerifier,
                                     StringRedisTemplate stringRedisTemplate,
                                     OAuth2ResourcePathProperties oauth2ResourcePaths,
                                     OAuth2ClientDomainService oauth2ClientDomainService) {
        this.signatureVerifier = signatureVerifier;
        this.stringRedisTemplate = stringRedisTemplate;
        this.oauth2ResourcePaths = oauth2ResourcePaths;
        this.oauth2ClientDomainService = oauth2ClientDomainService;
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

        // 1. 公开路径：匿名上下文（/auth/** 公开子集 + /actuator/**，评审 P1-1 精确化）
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

        // 2.5 OAuth2 JWT 认证（2026-08-14 实现；T-ACCESS-013 白名单配置化）：
        // 第三方 OAuth2 访问令牌（三段式 JWT，SaJwtUtil 独立签发）与平台 uuid 会话互斥。
        // 仅显式配置的开放路径（access.oauth2.resource-paths，默认仅 /auth/oauth2/userinfo）
        // 走本分支，其余路径委托令牌默认拒绝（落到会话分支 → 401）；
        // 开放路径不得覆盖 /auth/** 会话端点与 /api/perm/**（启动防护 fail-fast）。
        String bearerToken = extractBearerToken(request.getHeader(HEADER_AUTHORIZATION));
        if (bearerToken != null && bearerToken.indexOf('.') >= 0) {
            Optional<OAuth2ResourcePathProperties.ResourcePathRule> rule = oauth2ResourcePaths.match(uri);
            if (rule.isPresent()) {
                return authenticateOAuth2Jwt(request, response, bearerToken, rule.get());
            }
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
        clearContextAndMdc();
    }

    /**
     * Servlet 异步处理开始（评审 P2，2026-08-14）：原请求线程可能提前返回线程池，
     * 立即清理上下文与 MDC，防止线程复用串扰；异步处理线程需要上下文时
     * 必须显式 snapshot/restore（验收要求：异步路径显式建立/传递上下文）。
     */
    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request,
                                               HttpServletResponse response, Object handler) {
        clearContextAndMdc();
    }

    private static void clearContextAndMdc() {
        AccessRequestContext.clear();
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_USER_ID);
        MDC.remove(MDC_TENANT_ID);
        MDC.remove(MDC_SERVICE_CODE);
    }

    /**
     * 公开路径判定（评审 P1-1/P3，2026-08-14）。
     * <p>
     * 只匿名放行明确公开端点：/actuator/**（含精确根路径 /actuator，评审 P3）与
     * /auth/** 的公开子集（验证码/登录/令牌/撤销/登出）。其余 /auth/** 端点
     * （userinfo/user-menu/oauth2-authorize 需会话 → USER 分支；oauth2/userinfo
     * 需 OAuth2 JWT → JWT 认证分支，评审 P1）——修复登录用户查询无租户上下文
     * 与 OAuth2 授权码空租户问题。
     * </p>
     */
    private static boolean isPublicPath(String uri) {
        // 评审 P3（2026-08-14）：精确限定 /actuator 根路径与 /actuator/ 前缀，
        // 防止 /actuator-admin 等相邻命名空间被 startsWith 误判为匿名。
        if (uri.equals("/actuator") || uri.startsWith("/actuator/")) {
            return true;
        }
        if (uri.startsWith("/auth/")) {
            return isPublicAuthEndpoint(uri);
        }
        return false;
    }

    /**
     * 提取 Bearer 令牌；无 Authorization 头或非 Bearer 前缀返回 null。
     */
    private static String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * OAuth2 JWT 认证（2026-08-14 用户决策限定路径）：SaJwtUtil 验签
     * （HS256 + loginType 匹配 + 超时）→ 撤销黑名单检查 → 绑定 USER 上下文
     * （operatorId=JWT loginId、tenantId=JWT 载荷）。验签失败/黑名单命中 → 401。
     * 仅对显式配置的开放路径（access.oauth2.resource-paths，默认仅 /auth/oauth2/userinfo）
     * 生效；开放路径不得覆盖 /auth/** 会话端点与 /api/perm/**（启动防护 fail-fast）。
     * 委托令牌在其他路径默认拒绝；授权链（2026-08-22 用户决策，T-ACCESS-013）：
     * 验签 → 必填 claim（loginId/jti/client_id）→ 撤销黑名单 → 客户端启用动态校验
     * （sys_oauth2_client 唯一索引点查，不经缓存，禁用立即失效）→ 路径门禁
     * （clientIds 限定 / requiredScopes 子集校验 / audience 匹配，独立映射模型，
     * 不接入 PermQueryEngine）→ 绑定委托用户上下文（USER + delegatedClientId）。
     * 认证失败（验签/黑名单/客户端禁用）→ 401；授权不足（scope/audience/clientIds）→ 403。
     */
    private boolean authenticateOAuth2Jwt(HttpServletRequest request, HttpServletResponse response,
                                          String token,
                                          OAuth2ResourcePathProperties.ResourcePathRule rule)
        throws IOException {
        if (jwtSecretKey == null || jwtSecretKey.isBlank()) {
            logSecurity(request, "oauth2 jwt auth attempted without jwt secret configured");
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "认证失败");
            return false;
        }
        // 评审 P2：SaJwtUtil 返回 hutool JSONObject（LinkedHashMap 子类），
        // 一律以 Map 接收，hutool 类型不进入业务代码（AGENTS.md 禁止 Hutool）。
        Map<String, Object> payloads;
        try {
            // getPayloads 校验签名 + loginType + 超时，失败抛 SaTokenException
            payloads = SaJwtUtil.getPayloads(token, OAuth2JwtSupport.LOGIN_TYPE, jwtSecretKey);
        } catch (SaTokenException e) {
            logSecurity(request, "oauth2 jwt signature or timeout invalid");
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "认证失败");
            return false;
        }

        Object loginIdObj = payloads.get(SaJwtTemplate.LOGIN_ID);
        Object jtiObj = payloads.get(OAuth2JwtSupport.JTI_CLAIM);
        Object clientIdObj = payloads.get(OAuth2JwtSupport.CLIENT_ID_CLAIM);
        if (loginIdObj == null || jtiObj == null || clientIdObj == null
            || loginIdObj.toString().isBlank() || jtiObj.toString().isBlank()
            || clientIdObj.toString().isBlank()) {
            logSecurity(request, "oauth2 jwt missing loginId or jti or client_id");
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "认证失败");
            return false;
        }

        // 撤销检查：revoke 时写入 oauth2:blacklist:<jti>，TTL=令牌剩余有效期；
        // 黑名单对全部开放路径生效（含 userinfo 与业务路径），路径限定不产生绕过。
        Boolean revoked = stringRedisTemplate.hasKey(
            OAuth2JwtSupport.BLACKLIST_KEY_PREFIX + jtiObj);
        if (Boolean.TRUE.equals(revoked)) {
            logSecurity(request, "oauth2 jwt revoked");
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "认证失败");
            return false;
        }

        // 客户端启用动态校验（唯一索引点查，不经缓存保证禁用立即生效）：
        // 客户端被禁用/删除后已签发令牌立即失效（2026-08-22 用户决策）。
        String clientId = clientIdObj.toString();
        SysOauth2Client client = oauth2ClientDomainService.findActiveByClientId(clientId);
        if (client == null) {
            logSecurity(request, "oauth2 jwt client disabled or not found: " + clientId);
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "认证失败");
            return false;
        }

        // 路径门禁 1：clientIds 限定（可选；不满足 → 403 授权不足）
        if (!rule.getClientIds().isEmpty() && !rule.getClientIds().contains(clientId)) {
            logSecurity(request, "oauth2 jwt client not allowed for path " + rule.getPath());
            writeJson(response, HttpServletResponse.SC_FORBIDDEN, "拒绝访问");
            return false;
        }

        // 路径门禁 2：scope 子集校验（独立映射：令牌 scope ⊇ requiredScopes，2026-08-22 用户决策）
        if (!rule.getRequiredScopes().isEmpty()) {
            Set<String> tokenScopes = OAuth2JwtSupport.scopesOf(payloads);
            if (!tokenScopes.containsAll(rule.getRequiredScopes())) {
                logSecurity(request, "oauth2 jwt insufficient scope for path " + rule.getPath());
                writeJson(response, HttpServletResponse.SC_FORBIDDEN, "拒绝访问");
                return false;
            }
        }

        // 路径门禁 3：audience 匹配（业务开放路径强制；userinfo 默认豁免——rule.audience 为空不校验）
        if (rule.getAudience() != null && !rule.getAudience().isBlank()) {
            if (!OAuth2JwtSupport.audiencesOf(payloads).contains(rule.getAudience())) {
                logSecurity(request, "oauth2 jwt audience mismatch for path " + rule.getPath());
                writeJson(response, HttpServletResponse.SC_FORBIDDEN, "拒绝访问");
                return false;
            }
        }

        Long operatorId;
        try {
            operatorId = Long.parseLong(loginIdObj.toString());
        } catch (NumberFormatException e) {
            logSecurity(request, "oauth2 jwt invalid loginId");
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "认证失败");
            return false;
        }
        Long tenantId = OAuth2JwtSupport.tenantIdOf(payloads);

        AccessRequestContext.bind(RequestContext.delegatedUser(tenantId, operatorId, clientId));
        setMdc(request, String.valueOf(operatorId),
            tenantId == null ? null : String.valueOf(tenantId), null);
        return true;
    }

    /**
     * /auth/** 公开子集：验证码、登录（密码/短信）、OAuth2 令牌/刷新/撤销、登出。
     * logout 匿名放行保持未登录 200 幂等语义（无租户需求，StpUtil 自保护，用户决策）。
     */
    private static boolean isPublicAuthEndpoint(String uri) {
        return uri.equals("/auth/captcha")
            || uri.equals("/auth/login") || uri.equals("/auth/login/sms")
            || uri.equals("/auth/oauth2/token")
            || uri.equals("/auth/oauth2/refresh")
            || uri.equals("/auth/oauth2/revoke")
            || uri.equals("/auth/logout");
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
