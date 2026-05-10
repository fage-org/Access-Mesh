package cn.ac.fage.accessmesh.admin.dto.oauth2;

/**
 * OAuth2令牌响应记录类
 * <p>
 * 用于OAuth2令牌端点的响应。
 * 包含访问令牌、令牌类型、有效期、刷新令牌、权限范围。
 * </p>
 *
 * @param accessToken  访问令牌
 * @param tokenType    令牌类型（通常为"Bearer")
 * @param expiresIn    令牌有效期（秒）
 * @param refreshToken 刷新令牌
 * @param scope        权限范围
 */
public record TokenResp(
    /**
     * 访问令牌
     */
    String accessToken,

    /**
     * 令牌类型（通常为"Bearer")
     */
    String tokenType,

    /**
     * 令牌有效期（秒）
     */
    int expiresIn,

    /**
     * 刷新令牌
     */
    String refreshToken,

    /**
     * 权限范围
     */
    String scope
) {}