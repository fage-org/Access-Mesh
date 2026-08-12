package cn.ac.fage.accessmesh.access.admin.dto.oauth2;

/**
 * OAuth2用户信息响应记录类
 * <p>
 * 用于OAuth2 UserInfo端点返回的用户信息。
 * 包含用户唯一标识、用户名、姓名、手机号、邮箱。
 * </p>
 *
 * @param sub      用户唯一标识（subject）
 * @param username 用户名（登录账号）
 * @param name     姓名（显示名称）
 * @param phone    手机号
 * @param email    邮箱
 */
public record OAuth2UserInfoResp(
    /**
     * 用户唯一标识（subject）
     */
    String sub,

    /**
     * 用户名（登录账号）
     */
    String username,

    /**
     * 姓名（显示名称）
     */
    String name,

    /**
     * 手机号
     */
    String phone,

    /**
     * 邮箱
     */
    String email
) {}