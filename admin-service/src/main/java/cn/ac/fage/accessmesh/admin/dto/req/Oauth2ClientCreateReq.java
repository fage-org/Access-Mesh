package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * OAuth2客户端创建请求记录类
 * <p>
 * 用于创建OAuth2客户端配置的请求参数。
 * 包含客户端标识、密钥、名称、授权类型、重定向URI、令牌有效期等。
 * </p>
 *
 * @param clientId        客户端标识（必填）
 * @param clientSecret    客户端密钥（必填）
 * @param clientName      客户端名称（必填）
 * @param grantTypes      授权类型（可选，逗号分隔）
 * @param redirectUris    重定向URI列表（可选，逗号分隔）
 * @param scopes          权限范围（可选，逗号分隔）
 * @param accessTokenTtl  访问令牌有效期（可选，秒，范围60-86400）
 * @param refreshTokenTtl 刷新令牌有效期（可选，秒，范围60-604800）
 * @param status          状态（可选，默认0=正常）
 */
public record Oauth2ClientCreateReq(
    /**
     * 客户端标识（OAuth2协议中的client_id）
     */
    @NotBlank String clientId,

    /**
     * 客户端密钥（OAuth2协议中的client_secret）
     */
    @NotBlank String clientSecret,

    /**
     * 客户端名称（显示名称）
     */
    @NotBlank String clientName,

    /**
     * 授权类型（逗号分隔，如"authorization_code,refresh_token")
     */
    String grantTypes,

    /**
     * 重定向URI列表（逗号分隔）
     */
    String redirectUris,

    /**
     * 权限范围（逗号分隔）
     */
    String scopes,

    /**
     * 访问令牌有效期（秒，范围60-86400）
     */
    @Min(60) @Max(86400) Integer accessTokenTtl,

    /**
     * 刷新令牌有效期（秒，范围60-604800）
     */
    @Min(60) @Max(604800) Integer refreshTokenTtl,

    /**
     * 状态（0=正常，1=禁用）
     */
    Integer status
) {}