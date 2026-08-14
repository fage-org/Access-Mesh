package cn.ac.fage.accessmesh.access.infrastructure;

import java.util.Map;

/**
 * OAuth2 JWT 认证共享常量与载荷提取（评审三轮 P1，2026-08-14 用户决策完整实现）。
 * <p>
 * 评审四轮 P2：SaJwtUtil.getPayloads 返回 hutool JSONObject（LinkedHashMap 子类），
 * 业务代码一律以 {@code Map<String, Object>} 接收，禁止 hutool 类型进入业务代码
 * （AGENTS.md / project-rules 禁止 Hutool）。
 * </p>
 * <p>
 * OAuth2 访问令牌由 SaJwtUtil（HS256）独立签发，与平台用户会话（uuid 模式）无关。
 * 签发与验签必须使用相同的 loginType 与密钥（jwt-secret-key）：
 * </p>
 * <ul>
 *   <li>{@link #LOGIN_TYPE}：JWT 的 loginType claim（签发/验签一致，任意固定值）</li>
 *   <li>{@link #BLACKLIST_KEY_PREFIX}：撤销令牌黑名单键前缀（revoke 写入，拦截器校验）</li>
 *   <li>{@link #TENANT_CLAIM} / {@link #JTI_CLAIM}：签发时写入的扩展载荷键</li>
 * </ul>
 */
public final class OAuth2JwtSupport {

    /** JWT loginType claim（SaJwtTemplate.LOGIN_TYPE 语义）。 */
    public static final String LOGIN_TYPE = "oauth2";

    /** 撤销令牌黑名单键前缀（Redis，键 = 前缀 + jti）。 */
    public static final String BLACKLIST_KEY_PREFIX = "oauth2:blacklist:";

    /** 载荷键：租户 ID（签发时 String.valueOf(tenantId)，无租户为 "0"）。 */
    public static final String TENANT_CLAIM = "tenant_id";

    /** 载荷键：令牌唯一 ID（revoke 黑名单键值）。 */
    public static final String JTI_CLAIM = "jti";

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
}
