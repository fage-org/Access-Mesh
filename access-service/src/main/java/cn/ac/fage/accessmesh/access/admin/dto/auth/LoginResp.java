package cn.ac.fage.accessmesh.access.admin.dto.auth;

/**
 * 登录响应记录类
 * <p>
 * 登录成功后返回的令牌和用户基本信息。
 * 用于前端存储令牌和初始化用户状态。
 * </p>
 *
 * @param accessToken  访问令牌
 * @param refreshToken 刷新令牌
 * @param expiresIn    令牌有效期（秒）
 * @param tokenType    令牌类型（Bearer）
 * @param userId       用户ID
 * @param username     用户名
 * @param tenantId     租户ID
 * @param forceResetPwd 是否强制重置密码
 */
public record LoginResp(
    /**
     * 访问令牌（用于API认证）
     */
    String accessToken,

    /**
     * 刷新令牌（用于获取新的访问令牌）
     */
    String refreshToken,

    /**
     * 访问令牌有效期（秒）
     */
    long expiresIn,

    /**
     * 令牌类型（Bearer）
     */
    String tokenType,

    /**
     * 用户ID
     */
    Long userId,

    /**
     * 用户名
     */
    String username,

    /**
     * 租户ID
     */
    Long tenantId,

    /**
     * 是否强制重置密码（首次登录或密码过期时为true）
     */
    boolean forceResetPwd
) {}