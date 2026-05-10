package cn.ac.fage.accessmesh.gateway.filter;

import cn.ac.fage.accessmesh.gateway.config.GatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 签名增强过滤器
 * <p>
 * 为用户身份请求头生成HMAC-SHA256签名。
 * 签名用于后端服务验证Gateway注入的请求头未被篡改。
 * </p>
 *
 * <p>签名请求头：X-User-Id、X-Tenant-Id
 * <p>签名格式：HMAC-SHA256(secretKey, userId + "|" + tenantId + "|" + timestamp)
 *
 * <p>执行顺序：-35（在HeaderEnrichFilter之后、InternalSecretFilter之前）
 * </p>
 */
@Component
public class SignatureEnrichFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(SignatureEnrichFilter.class);
    private static final String SKIP_AUTH_ATTR = "skipAuth";
    private static final String USER_ID_ATTR = "userId";
    private static final String TENANT_ID_ATTR = "tenantId";
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final GatewayProperties.Signature signatureConfig;
    private final GatewayProperties.Header.Enrich headerEnrichConfig;

    /**
     * ThreadLocal Mac实例，用于线程安全的签名生成。
     * 每个线程拥有独立的Mac实例，避免同步开销。
     */
    private final ThreadLocal<Mac> macThreadLocal;

    /**
     * Mac初始化成功标志。
     */
    private volatile boolean macInitialized = false;

    public SignatureEnrichFilter(GatewayProperties gatewayProperties) {
        this.signatureConfig = gatewayProperties.getSignature();
        this.headerEnrichConfig = gatewayProperties.getHeader().getEnrich();
        this.macThreadLocal = initMac();
    }

    /**
     * 初始化Mac实例
     * <p>
     * 验证签名密钥配置并创建ThreadLocal Mac实例。
     * 未配置密钥时跳过签名生成。
     * </p>
     *
     * @return ThreadLocal Mac实例，未配置密钥时返回null
     */
    private ThreadLocal<Mac> initMac() {
        final String secret = signatureConfig.getSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("签名密钥未配置，将跳过签名生成。请配置gateway.signature.secret以启用签名功能。");
            return null;
        }

        try {
            // 预初始化Mac实例以验证配置
            Mac templateMac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            templateMac.init(keySpec);

            macInitialized = true;
            log.info("SignatureEnrichFilter已初始化，使用HMAC-SHA256算法");

            // 创建ThreadLocal，每个线程克隆Mac实例
            return ThreadLocal.withInitial(() -> {
                try {
                    Mac mac = Mac.getInstance(HMAC_SHA256);
                    mac.init(keySpec);
                    return mac;
                } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                    log.error("为线程创建Mac实例失败: {}", e.getMessage());
                    throw new RuntimeException("初始化Mac失败", e);
                }
            });
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("初始化HMAC-SHA256失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 执行过滤器逻辑
     * <p>
     * 为非白名单请求的用户身份请求头生成签名。
     * 签名注入到配置的签名请求头中，供后端服务验证。
     * </p>
     *
     * @param exchange 服务器Web交换对象
     * @param chain    过滤器链
     * @return Mono完成信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 签名生成是必须的 - 无启用检查
        // 仅在密钥未配置时跳过（启动验证应捕获此问题）
        if (!macInitialized || macThreadLocal == null) {
            log.warn("签名生成跳过 - 密钥未配置。生产环境不应出现此情况！");
            return chain.filter(exchange);
        }

        // 白名单请求跳过签名
        Boolean skipAuth = exchange.getAttribute(SKIP_AUTH_ATTR);
        if (Boolean.TRUE.equals(skipAuth)) {
            return chain.filter(exchange);
        }

        // 从交换属性获取用户身份（由AuthTokenFilter设置）
        Object userIdObj = exchange.getAttribute(USER_ID_ATTR);
        Object tenantIdObj = exchange.getAttribute(TENANT_ID_ATTR);

        if (userIdObj == null || tenantIdObj == null) {
            // 无用户身份 - 跳过签名（由下游认证处理）
            return chain.filter(exchange);
        }

        String userId = userIdObj.toString();
        String tenantId = tenantIdObj.toString();
        long timestamp = System.currentTimeMillis() / 1000; // Unix时间戳（秒）

        // 生成签名
        String signature = generateSignature(userId, tenantId, timestamp);

        if (signature == null) {
            log.warn("签名生成失败 userId={}, tenantId={}", userId, tenantId);
            return chain.filter(exchange);
        }

        // 注入签名请求头
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
            .headers(headers -> {
                headers.set(signatureConfig.getHeaderName(), signature);
                headers.set(signatureConfig.getTimestampHeaderName(), String.valueOf(timestamp));
            })
            .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    /**
     * 生成HMAC-SHA256签名
     * <p>
     * 签名格式：HMAC-SHA256(secret, userId + "|" + tenantId + "|" + timestamp)
     * 使用ThreadLocal Mac实例实现线程安全，避免同步开销。
     * </p>
     *
     * @param userId   用户ID
     * @param tenantId 租户ID
     * @param timestamp 时间戳（秒）
     * @return 签名十六进制字符串，生成失败返回null
     */
    private String generateSignature(String userId, String tenantId, long timestamp) {
        try {
            Mac mac = macThreadLocal.get();

            String payload = userId + "|" + tenantId + "|" + timestamp;
            byte[] signatureBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(signatureBytes);
        } catch (Exception e) {
            log.error("签名生成错误: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取过滤器执行顺序
     * <p>
     * 返回-35，确保在HeaderEnrichFilter(-50)之后、InternalSecretFilter(-40)之前执行。
     * </p>
     *
     * @return 过滤器顺序值
     */
    @Override
    public int getOrder() {
        return -35;
    }
}