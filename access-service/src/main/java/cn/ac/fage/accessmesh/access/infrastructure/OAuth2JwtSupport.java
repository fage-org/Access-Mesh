package cn.ac.fage.accessmesh.access.infrastructure;

import cn.ac.fage.accessmesh.common.enums.GlobalErrorCode;
import cn.ac.fage.accessmesh.common.exception.SystemException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * OAuth2 JWT 签发、验证与共享载荷提取。
 * <p>
 * OAuth2 访问令牌由 Nimbus（HS256）独立签发，与平台用户会话（uuid 模式）无关。
 * 签发与验签必须使用相同的 loginType 与密钥（jwt-secret-key）：
 * </p>
 * <ul>
 *   <li>{@link #LOGIN_TYPE}：JWT 的 loginType claim（签发/验签一致，任意固定值）</li>
 *   <li>{@link #BLACKLIST_KEY_PREFIX}：撤销令牌黑名单键前缀（revoke 写入，拦截器校验）</li>
 *   <li>{@link #TENANT_CLAIM} / {@link #JTI_CLAIM} / {@link #CLIENT_ID_CLAIM} /
 *       {@link #SCOPE_CLAIM} / {@link #AUD_CLAIM}：签发时写入的扩展载荷键</li>
 * </ul>
 */
public final class OAuth2JwtSupport {

    private static final JsonMapper JSON = JsonMapper.builder().configureForJackson2().build();
    private static final String LOGIN_TYPE_CLAIM = "loginType";
    public static final String LOGIN_ID_CLAIM = "loginId";
    private static final String EXPIRY_CLAIM = "eff";

    /** JWT loginType claim。 */
    public static final String LOGIN_TYPE = "oauth2";

    /** 保持既有 JWT 协议：eff 是毫秒时间戳，可信额外载荷可截短授权链期限。 */
    public static String createToken(String loginType, Object loginId, String device, long timeoutSeconds,
                                     Map<String, Object> extraData, String secret) {
        validateHmacSecretStrength(secret);
        Map<String, Object> payloads = new LinkedHashMap<>();
        payloads.put(LOGIN_TYPE_CLAIM, loginType);
        payloads.put(LOGIN_ID_CLAIM, loginId);
        payloads.put("device", device);
        payloads.put(EXPIRY_CLAIM, timeoutSeconds == -1 ? -1L
                : Math.addExact(System.currentTimeMillis(), Math.multiplyExact(timeoutSeconds, 1000L)));
        payloads.put("rnStr", UUID.randomUUID().toString().replace("-", ""));
        if (extraData != null) payloads.putAll(extraData);
        try {
            JWSObject jwt = new JWSObject(new JWSHeader.Builder(JWSAlgorithm.HS256)
                    .type(JOSEObjectType.JWT).build(), new Payload(JSON.writeValueAsString(payloads)));
            jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (JOSEException | JacksonException e) {
            throw new SystemException(GlobalErrorCode.SYSTEM_ERROR.code(), "OAuth2 JWT 签发失败", e);
        }
    }

    /** 验签后才使用载荷；严格限定 HS256，保留 loginType 和 eff 期限校验。 */
    public static Map<String, Object> getPayloads(String token, String loginType, String secret) {
        if (token == null || secret == null || secret.isBlank()) throw new SecurityException("OAuth2 JWT 无效");
        try {
            JWSObject jwt = JWSObject.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())
                    || !jwt.getHeader().isBase64URLEncodePayload()
                    || !jwt.verify(new MACVerifier(secret.getBytes(StandardCharsets.UTF_8)))) {
                throw new SecurityException("OAuth2 JWT 签名无效");
            }
            Map<String, Object> payloads = JSON.readValue(jwt.getPayload().toString(), new TypeReference<>() {});
            if (payloads == null || !Objects.equals(loginType, payloads.get(LOGIN_TYPE_CLAIM))) {
                throw new SecurityException("OAuth2 JWT 类型无效");
            }
            long expiry = expiryOf(payloads);
            if (expiry != -1 && expiry < System.currentTimeMillis()) throw new SecurityException("OAuth2 JWT 已过期");
            return payloads;
        } catch (ParseException | JOSEException | JacksonException | IllegalArgumentException e) {
            throw new SecurityException("OAuth2 JWT 无效", e);
        }
    }

    /** 有效令牌的剩余秒数；无效/过期返回 -2，原有永久期限返回 -1。 */
    public static long getTimeout(String token, String loginType, String secret) {
        try {
            long expiry = expiryOf(getPayloads(token, loginType, secret));
            return expiry == -1 ? -1 : Math.max(0, (expiry - System.currentTimeMillis()) / 1000);
        } catch (SecurityException e) {
            return -2;
        }
    }

    private static long expiryOf(Map<String, Object> payloads) {
        Object value = payloads.get(EXPIRY_CLAIM);
        if (value == null) throw new SecurityException("OAuth2 JWT 缺少有效期");
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            throw new SecurityException("OAuth2 JWT 有效期无效", e);
        }
    }

    /** 撤销令牌黑名单键前缀（Redis，键 = 前缀 + jti）。 */
    public static final String BLACKLIST_KEY_PREFIX = "oauth2:blacklist:";

    /** 载荷键：租户 ID（签发时 String.valueOf(tenantId)，无租户为 "0"）。 */
    public static final String TENANT_CLAIM = "tenant_id";
    public static final String TENANT_EPOCH_CLAIM = "tenant_epoch";

    /** 载荷键：令牌唯一 ID（revoke 黑名单键值）。 */
    public static final String JTI_CLAIM = "jti";

    /** 载荷键：客户端标识（T-ACCESS-013 资源服务器按其动态校验客户端启用状态）。 */
    public static final String CLIENT_ID_CLAIM = "client_id";

    /** 载荷键：授权范围（RFC 8693 空格分隔委托范围，T-ACCESS-013 独立映射模型）。 */
    public static final String SCOPE_CLAIM = "scope";

    /** 载荷键：受众（T-ACCESS-013；客户端注册 audiences 非空时签发写入，List&lt;String&gt;）。 */
    public static final String AUD_CLAIM = "aud";

    public static final String PASSWORD_FINGERPRINT_CLAIM = "pwd_generation";
    public static final String CHAIN_ISSUED_AT_CLAIM = "chain_iat";
    public static final String CHAIN_EXPIRES_AT_CLAIM = "chain_exp";

    /** BCrypt 的算法/成本/随机盐前缀；不把可用于校验密码的完整哈希暴露到 JWT。 */
    public static String passwordFingerprint(String passwordHash) {
        return passwordHash != null && passwordHash.length() == 60 && passwordHash.startsWith("$2")
            ? passwordHash.substring(0, 29) : null;
    }

    /**
     * 码、刷新记录与访问 JWT 采用同一代际与绝对期限；缺字段的旧凭据拒绝。
     * <p>
     * clockSkewToleranceSeconds 为签发时间未来偏移容忍窗（秒，2026-10-06 拍板：
     * 默认 3、配 0 关闭）——多实例节点间 NTP 级时钟漂移下，稍早签发的链在慢时钟
     * 节点仍可兑换（重试即成功的秒级毛刺消除）；过期判定不放宽。
     * </p>
     */
    public static boolean isCurrentCredential(String passwordHash, String fingerprint,
                                               long issuedAt, long expiresAt, long clockSkewToleranceSeconds) {
        long now = System.currentTimeMillis() / 1000;
        return fingerprint != null && fingerprint.equals(passwordFingerprint(passwordHash))
            && issuedAt > 0 && issuedAt <= now + clockSkewToleranceSeconds
            && expiresAt > issuedAt && expiresAt > now;
    }

    public static boolean isCurrentCredential(String passwordHash, Map<String, Object> payloads,
                                               long clockSkewToleranceSeconds) {
        Object fingerprint = payloads.get(PASSWORD_FINGERPRINT_CLAIM);
        try {
            return fingerprint instanceof String value && isCurrentCredential(passwordHash, value,
                Long.parseLong(String.valueOf(payloads.get(CHAIN_ISSUED_AT_CLAIM))),
                Long.parseLong(String.valueOf(payloads.get(CHAIN_EXPIRES_AT_CLAIM))),
                clockSkewToleranceSeconds);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private OAuth2JwtSupport() {
    }

    /**
     * 从 JWT 载荷提取租户 ID；"0" 或缺省视为无租户（返回 null）。
     */
    public static Long tenantIdOf(Map<String, Object> payloads) {
        Object tenantId = payloads.get(TENANT_CLAIM);
        if (tenantId == null || tenantId.toString().isBlank() || "0".equals(tenantId.toString())) {
            return null;
        }
        try {
            return Long.parseLong(tenantId.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Long tenantEpochOf(Map<String,Object> payloads) {
        Object value=payloads.get(TENANT_EPOCH_CLAIM);
        if(value==null) return null;
        try { long epoch=Long.parseLong(value.toString()); return epoch>0?epoch:null; }
        catch(NumberFormatException exception) { return null; }
    }

    /**
     * 从 JWT 载荷提取授权范围（scope claim，空格分隔）；缺失或空返回空 Set。
     */
    public static Set<String> scopesOf(Map<String, Object> payloads) {
        Object scope = payloads.get(SCOPE_CLAIM);
        if (scope == null || scope.toString().isBlank()) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<>();
        for (String s : scope.toString().split(" ")) {
            if (!s.isBlank()) {
                result.add(s);
            }
        }
        return result;
    }

    /**
     * 从 JWT 载荷提取受众（aud claim）；兼容 List（签发形态）与单字符串，缺失返回空 List。
     */
    public static List<String> audiencesOf(Map<String, Object> payloads) {
        Object aud = payloads.get(AUD_CLAIM);
        if (aud == null) {
            return Collections.emptyList();
        }
        if (aud instanceof List<?> list) {
            List<String> result = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item != null && !item.toString().isBlank()) {
                    result.add(item.toString());
                }
            }
            return result;
        }
        String value = aud.toString();
        if (value.isBlank()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>(1);
        result.add(value);
        return result;
    }

    /**
     * HS256 签名密钥强度下限（release-preview 双轨评审 P3-3，2026-09-16 用户拍板 fail-fast）：
     * 32 字符 = 256 bit，与 HS256 安全强度对齐；短密钥静默签发 = 弱签名通道。
     */
    public static final int MIN_SECRET_LENGTH = 32;

    /**
     * 校验 JWT 签名密钥强度：空白或长度 &lt; {@link #MIN_SECRET_LENGTH} 抛 IllegalStateException
     * （启动 fail-fast，对齐 bootstrap 密码/双密钥拦截器先例）。
     */
    public static void validateHmacSecretStrength(String secret) {
        if (secret == null || secret.isBlank() || secret.trim().length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                "sa-token.jwt-secret-key（JWT_SECRET_KEY）必须至少 " + MIN_SECRET_LENGTH
                    + " 字符（HS256 强度对齐）；空白或过短即拒绝启动");
        }
    }
}
