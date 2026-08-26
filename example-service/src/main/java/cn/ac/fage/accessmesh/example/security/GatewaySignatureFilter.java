package cn.ac.fage.accessmesh.example.security;

import cn.ac.fage.accessmesh.example.enums.ExampleErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Gateway 身份签名校验过滤器（接入示例：下游服务自证身份头未被伪造）。
 * <p>
 * Gateway 的 SignatureEnrichFilter 对注入的 {@code X-User-Id}/{@code X-Tenant-Id} 生成
 * HMAC-SHA256 签名：{@code HMAC(secret, userId + "|" + tenantId + "|" + timestamp)}，
 * 经 {@code X-User-Signature}/{@code X-Signature-Timestamp} 头透传。本过滤器按同一算法复算，
 * 携带身份头但签名缺失/不匹配/时间戳超窗的请求拒绝（信封 code=30003，HTTP 200——业务错误语义）。
 * </p>
 * <p>
 * 密钥与 Gateway 同源（环境变量 {@code ACCESSMESH_SIGNATURE_SECRET}）；未配置密钥时
 * fail-closed：凡携带身份头的请求一律拒绝（不静默放行）。信任边界的根本保障仍是网络隔离
 * （业务服务仅 Gateway 可达），签名校验是纵深防御/直连自证示例，不替代网络隔离。
 * </p>
 */
@Component
public class GatewaySignatureFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewaySignatureFilter.class);
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final String secret;
    /** 签名时效窗（秒），与 access-service perm.signature.valid-seconds 运维同调 */
    private final long validSeconds;
    private final ObjectMapper objectMapper;
    private volatile boolean secretConfigured;

    public GatewaySignatureFilter(
            @Value("${example.signature.secret:${ACCESSMESH_SIGNATURE_SECRET:}}") String secret,
            @Value("${example.signature.valid-seconds:300}") long validSeconds,
            ObjectMapper objectMapper) {
        this.secret = secret;
        this.validSeconds = validSeconds;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void verifyConfig() {
        secretConfigured = secret != null && !secret.isBlank();
        if (!secretConfigured) {
            log.error("签名密钥未配置（example.signature.secret / ACCESSMESH_SIGNATURE_SECRET）："
                + "携带身份头的请求将被一律拒绝（fail-closed）");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String userId = request.getHeader("X-User-Id");
        String tenantId = request.getHeader("X-Tenant-Id");
        if (userId == null && tenantId == null) {
            // 无身份头：不属本过滤器职责（未走 Gateway 链路由业务接口按 30002 拒绝）
            chain.doFilter(request, response);
            return;
        }
        if (!verify(userId, tenantId,
            request.getHeader("X-User-Signature"), request.getHeader("X-Signature-Timestamp"))) {
            log.warn("身份头签名校验失败：userId={}，拒绝请求 {} {}", userId, request.getMethod(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(objectMapper.writeValueAsString(
                cn.ac.fage.accessmesh.common.model.PermResult.error(
                    ExampleErrorCode.SIGNATURE_INVALID.getCode(),
                    ExampleErrorCode.SIGNATURE_INVALID.getMessage())));
            return;
        }
        chain.doFilter(request, response);
    }

    /** 复算并比对 HMAC 签名（常量时间比较）；时间戳超 300 秒视为过期 */
    private boolean verify(String userId, String tenantId, String signature, String timestamp) {
        if (!secretConfigured || signature == null || signature.isBlank()
            || timestamp == null || userId == null || tenantId == null) {
            return false;
        }
        final long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(System.currentTimeMillis() / 1000 - ts) > validSeconds) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] expected = mac.doFinal((userId + "|" + tenantId + "|" + ts).getBytes(StandardCharsets.UTF_8));
            byte[] provided = HexFormat.of().parseHex(signature.trim());
            return MessageDigest.isEqual(expected, provided);
        } catch (Exception e) {
            return false;
        }
    }
}
