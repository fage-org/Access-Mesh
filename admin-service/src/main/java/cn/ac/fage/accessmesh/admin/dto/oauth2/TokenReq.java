package cn.ac.fage.accessmesh.admin.dto.oauth2;

import jakarta.validation.constraints.NotBlank;

/**
 * OAuth2令牌请求记录类
 * <p>
 * 用于OAuth2令牌端点的请求参数。
 * 支持授权码模式（authorization_code）和刷新令牌模式（refresh_token）。
 * 支持PKCE扩展（codeVerifier）。
 * </p>
 *
 * @param grantType    授权类型（必填，"authorization_code"或"refresh_token")
 * @param clientId     客户端ID
 * @param clientSecret 客户端密钥
 * @param code         授权码（authorization_code模式必填）
 * @param redirectUri  重定向URI（authorization_code模式必填）
 * @param codeVerifier PKCE验证码（可选）
 * @param refreshToken 刷新令牌（refresh_token模式必填）
 */
public record TokenReq(
    /**
     * 授权类型（"authorization_code"或"refresh_token")
     */
    @NotBlank(message = "grantType 不能为空")
    String grantType,

    /**
     * 客户端ID
     */
    String clientId,

    /**
     * 客户端密钥
     */
    String clientSecret,

    /**
     * 授权码（authorization_code模式使用）
     */
    String code,

    /**
     * 重定向URI（authorization_code模式使用）
     */
    String redirectUri,

    /**
     * PKCE验证码
     */
    String codeVerifier,

    /**
     * 刷新令牌（refresh_token模式使用）
     */
    String refreshToken
) {}