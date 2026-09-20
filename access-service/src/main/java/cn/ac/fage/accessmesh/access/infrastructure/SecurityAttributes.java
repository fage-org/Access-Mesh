package cn.ac.fage.accessmesh.access.infrastructure;

/**
 * 请求 attribute 常量（T-ACCESS-004 统一安全链）。
 * <p>
 * 安全决策原则：基于已验证的 attribute（仅前置拦截器可写，请求方无法伪造）
 * 而非未验证的原始请求头进行信任判定，防止请求头注入与拦截器顺序绕过。
 * </p>
 * <ul>
 *   <li>{@link #ATTR_INTERNAL_AUTHENTICATED}：仅 {@code ServiceAuthArbiter}（T-PERM-070 前为
 *       InternalApiSecretInterceptor）写入，表示内部密钥（X-Internal-Secret）验证通过。</li>
 *   <li>{@link #ATTR_SIGNATURE_VERIFIED}：仅 {@code HeaderSignatureInterceptor} 写入，
 *       表示 X-User-Id 等用户身份头的 HMAC 签名验证通过。</li>
 * </ul>
 */
public final class SecurityAttributes {

    /** 内部凭证已认证标志（X-Internal-Secret 验证通过后写入）。 */
    public static final String ATTR_INTERNAL_AUTHENTICATED =
        "cn.ac.fage.accessmesh.access.security.INTERNAL_AUTHENTICATED";

    /** 用户身份头签名已验证标志（HMAC 验签通过后写入）。 */
    public static final String ATTR_SIGNATURE_VERIFIED =
        "cn.ac.fage.accessmesh.access.security.SIGNATURE_VERIFIED";

    /**
     * 凭证认证服务主体（T-PERM-070）：仅 {@code ServiceAuthArbiter}（order=1）在
     * X-Credential-Id/X-Credential-Secret 验证通过后写入，值为 {@link ServicePrincipal}
     * （服务端内存对象，调用方无法伪造）；后续拦截器与上下文绑定只消费本对象，
     * 不读取 X-Service-Code/X-Tenant-Id 自报头。
     */
    public static final String ATTR_SERVICE_PRINCIPAL =
        "cn.ac.fage.accessmesh.access.security.SERVICE_PRINCIPAL";

    private SecurityAttributes() {
    }
}
