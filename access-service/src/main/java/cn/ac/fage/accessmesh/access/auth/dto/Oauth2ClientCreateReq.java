package cn.ac.fage.accessmesh.access.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * OAuth2客户端创建请求记录类
 * <p>
 * 用于创建OAuth2客户端配置的请求参数。
 * 包含客户端标识、密钥、名称、授权类型、重定向URI、令牌有效期等。
 * </p>
 *
 * @param clientId        客户端标识（必填）
 * @param clientSecret    机密客户端必填，公开客户端必须省略
 * @param clientName      客户端名称（必填）
 * @param grantTypes      授权类型（必填，逗号分隔）
 * @param redirectUris    重定向URI列表（可选，逗号分隔）
 * @param scopes          权限范围（可选，逗号分隔）
 * @param audiences       令牌受众/资源服务器标识（可选，逗号分隔；配置后签发写入 aud claim）
 * @param accessTokenTtl  访问令牌有效期（可选，秒，范围60-86400）
 * @param refreshTokenTtl 刷新令牌有效期（可选，秒，范围60-604800）
 * @param status          状态（可选，缺省 1=启用；0=停用，1=启用——与 DDL sys_oauth2_client.status 一致）
 */
public record Oauth2ClientCreateReq(
    /**
     * 客户端标识（OAuth2协议中的client_id）
     */
    @NotBlank String clientId,

    /**
     * 客户端密钥（OAuth2协议中的client_secret）
     */
    String clientSecret,

    /**
     * 客户端名称（显示名称）
     */
    @NotBlank String clientName,

    /**
     * 授权类型（逗号分隔，如"authorization_code,refresh_token")
     */
    @NotBlank(message = "grantTypes 不能为空白")
    String grantTypes,

    /**
     * 重定向URI列表（逗号分隔）
     */
    @Pattern(regexp = "(?s)(?U).*\\S.*", message = "redirectUris 不能为空白")
    String redirectUris,

    /**
     * 权限范围（逗号分隔）
     */
    @Pattern(regexp = "(?s)(?U).*\\S.*", message = "scopes 不能为空白")
    String scopes,

    /**
     * 令牌受众/目标资源服务器标识（可选，逗号分隔；配置后签发的访问令牌写入 aud claim）
     */
    @Pattern(regexp = "(?s)(?U).*\\S.*", message = "audiences 不能为空白")
    String audiences,

    /**
     * 访问令牌有效期（秒，范围60-86400）
     */
    @Min(60) @Max(86400) Integer accessTokenTtl,

    /**
     * 刷新令牌有效期（秒，范围60-604800）
     */
    @Min(60) @Max(604800) Integer refreshTokenTtl,

    /**
     * 状态（0=停用，1=启用）
     */
    Integer status,

    /** 缺省 CONFIDENTIAL；PUBLIC 使用 S256 PKCE，无客户端密钥。 */
    @Pattern(regexp = "CONFIDENTIAL|PUBLIC") String clientType
) {
    @AssertTrue(message = "CONFIDENTIAL 必须提供密钥；PUBLIC 必须省略密钥")
    @JsonIgnore
    public boolean isClientSecretValid() {
        return "PUBLIC".equals(clientType) ? clientSecret == null
            : clientSecret != null && !clientSecret.isBlank();
    }
}
