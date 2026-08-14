package cn.ac.fage.accessmesh.access.infrastructure;

/**
 * 请求 attribute 常量（T-ACCESS-004 统一安全链）。
 * <p>
 * 安全决策原则：基于已验证的 attribute（仅前置拦截器可写，请求方无法伪造）
 * 而非未验证的原始请求头进行信任判定，防止请求头注入与拦截器顺序绕过。
 * </p>
 * <ul>
 *   <li>{@link #ATTR_INTERNAL_AUTHENTICATED}：仅 {@code InternalApiSecretInterceptor} 写入，
 *       表示内部凭证（X-Internal-Secret）验证通过。</li>
 *   <li>{@link #ATTR_SIGNATURE_VERIFIED}：仅 {@code HeaderSignatureInterceptor} 写入，
 *       表示 X-User-Id 等用户身份头的 HMAC 签名验证通过。</li>
 * </ul>
 */
public final class SecurityAttributes {

    /** 内部凭证已认证标志（X-Internal-Secret 验证通过后写入）。 */
    public static final String ATTR_INTERNAL_AUTHENTICATED =
        "cn.ac.fage.accessmesh.access.security.INTERNAL_AUTHENTICATED";

    /** 用户身份头签名已验证标志（HMAC 验证通过后写入）。 */
    public static final String ATTR_SIGNATURE_VERIFIED =
        "cn.ac.fage.accessmesh.access.security.SIGNATURE_VERIFIED";

    private SecurityAttributes() {
    }
}
