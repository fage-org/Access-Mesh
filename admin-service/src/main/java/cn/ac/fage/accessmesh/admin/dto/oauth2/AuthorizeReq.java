package cn.ac.fage.accessmesh.admin.dto.oauth2;

import jakarta.validation.constraints.NotBlank;

/**
 * OAuth2授权请求记录类
 * <p>
 * 用于OAuth2授权码流程的授权请求参数。
 * 包含客户端ID、响应类型、重定向URI、状态、scope、PKCE挑战码等。
 * </p>
 *
 * @param clientId          客户端ID（必填）
 * @param responseType      响应类型（必填，通常为"code")
 * @param redirectUri       重定向URI（必填）
 * @param state             状态参数（可选，用于防CSRF）
 * @param scope             权限范围（可选）
 * @param codeChallenge     PKCE挑战码（可选）
 * @param codeChallengeMethod PKCE挑战码方法（可选，如"S256")
 */
public record AuthorizeReq(
    /**
     * 客户端ID
     */
    @NotBlank(message = "clientId 不能为空")
    String clientId,

    /**
     * 响应类型（通常为"code"）
     */
    @NotBlank(message = "responseType 不能为空")
    String responseType,

    /**
     * 重定向URI
     */
    @NotBlank(message = "redirectUri 不能为空")
    String redirectUri,

    /**
     * 状态参数（用于防CSRF攻击）
     */
    String state,

    /**
     * 权限范围
     */
    String scope,

    /**
     * PKCE挑战码
     */
    String codeChallenge,

    /**
     * PKCE挑战码方法（如"S256"、"plain")
     */
    String codeChallengeMethod
) {}