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
 * Generates HMAC-SHA256 signature for user identity headers.
 * This signature allows downstream services to verify that headers
 * injected by the gateway have not been tampered with.
 *
 * <p>Signed headers: X-User-Id, X-Tenant-Id
 * <p>Signature format: HMAC-SHA256(secretKey, userId + "|" + tenantId + "|" + timestamp)
 *
 * <p>Order: -35 (after HeaderEnrichFilter at -50, before InternalSecretFilter at -40)
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
     * ThreadLocal Mac instance for thread-safe signature generation.
     * Each thread gets its own Mac instance, avoiding synchronization overhead.
     */
    private final ThreadLocal<Mac> macThreadLocal;
    
    /**
     * Flag indicating whether the Mac initialization was successful.
     */
    private volatile boolean macInitialized = false;

    public SignatureEnrichFilter(GatewayProperties gatewayProperties) {
        this.signatureConfig = gatewayProperties.getSignature();
        this.headerEnrichConfig = gatewayProperties.getHeader().getEnrich();
        this.macThreadLocal = initMac();
    }

    private ThreadLocal<Mac> initMac() {
        final String secret = signatureConfig.getSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("Signature secret not configured, signature generation will be skipped. " +
                "Configure gateway.signature.secret to enable.");
            return null;
        }

        try {
            // Pre-initialize Mac instance to verify configuration
            Mac templateMac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            templateMac.init(keySpec);
            
            macInitialized = true;
            log.info("SignatureEnrichFilter initialized with HMAC-SHA256");
            
            // Create ThreadLocal with initial Mac clone for each thread
            return ThreadLocal.withInitial(() -> {
                try {
                    Mac mac = Mac.getInstance(HMAC_SHA256);
                    mac.init(keySpec);
                    return mac;
                } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                    log.error("Failed to create Mac for thread: {}", e.getMessage());
                    throw new RuntimeException("Failed to initialize Mac", e);
                }
            });
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to initialize HMAC-SHA256: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // FIX #6: Signature generation is mandatory - no enabled check
        // Only skip if secret not configured (startup validation should catch this)
        if (!macInitialized || macThreadLocal == null) {
            log.warn("Signature generation skipped - secret not configured. This should not happen in production!");
            return chain.filter(exchange);
        }

        // Skip for whitelist requests
        Boolean skipAuth = exchange.getAttribute(SKIP_AUTH_ATTR);
        if (Boolean.TRUE.equals(skipAuth)) {
            return chain.filter(exchange);
        }

        // Get user identity from exchange attributes (set by AuthTokenFilter)
        Object userIdObj = exchange.getAttribute(USER_ID_ATTR);
        Object tenantIdObj = exchange.getAttribute(TENANT_ID_ATTR);

        if (userIdObj == null || tenantIdObj == null) {
            // No user identity - skip signature (will be handled by downstream auth)
            return chain.filter(exchange);
        }

        String userId = userIdObj.toString();
        String tenantId = tenantIdObj.toString();
        long timestamp = System.currentTimeMillis() / 1000; // Unix timestamp in seconds

        // Generate signature
        String signature = generateSignature(userId, tenantId, timestamp);

        if (signature == null) {
            log.warn("Failed to generate signature for userId={}, tenantId={}", userId, tenantId);
            return chain.filter(exchange);
        }

        // Inject signature headers
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
            .headers(headers -> {
                headers.set(signatureConfig.getHeaderName(), signature);
                headers.set(signatureConfig.getTimestampHeaderName(), String.valueOf(timestamp));
            })
            .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    /**
     * Generate HMAC-SHA256 signature.
     * Format: HMAC-SHA256(secret, userId + "|" + tenantId + "|" + timestamp)
     * 
     * Uses ThreadLocal Mac instance for thread-safety without synchronization overhead.
     */
    private String generateSignature(String userId, String tenantId, long timestamp) {
        try {
            Mac mac = macThreadLocal.get();
            
            String payload = userId + "|" + tenantId + "|" + timestamp;
            byte[] signatureBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(signatureBytes);
        } catch (Exception e) {
            log.error("Error generating signature: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public int getOrder() {
        return -35;
    }
}
