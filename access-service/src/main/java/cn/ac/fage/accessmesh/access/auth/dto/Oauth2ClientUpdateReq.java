package cn.ac.fage.accessmesh.access.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;

import jakarta.validation.constraints.Pattern;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * OAuth2客户端更新请求记录类
 * <p>
 * 用于更新OAuth2客户端配置的请求参数。
 * 所有字段均为可选，仅更新提供的字段。
 * clientSecret可选，不更新时传null。
 * </p>
 *
 * @param id              客户端记录ID（必填）
 * @param clientSecret    客户端密钥（可选，不更新时传null）
 * @param clientName      客户端名称（可选）
 * @param grantTypes      授权类型（可选）
 * @param redirectUris    重定向URI列表（可选）
 * @param scopes          权限范围（可选）
 * @param audiences       令牌受众/资源服务器标识（可选，逗号分隔；配置后签发写入 aud claim）
 * @param accessTokenTtl  访问令牌有效期（可选，秒，范围60-86400）
 * @param refreshTokenTtl 刷新令牌有效期（可选，秒，范围60-604800）
 * @param status          状态（可选）
 * @param redirectUrisClear 显式清空 redirectUris 为 NULL，与新值同传拒绝
 * @param scopesClear 显式清空 scopes 为 NULL，与新值同传拒绝
 * @param audiencesClear 显式清空 audiences 为 NULL，与新值同传拒绝
 */
public record Oauth2ClientUpdateReq(
    /**
     * 客户端记录ID
     */
    @NotNull Long id,

    /**
     * 客户端密钥（不更新时传null）
     */
    @Pattern(regexp = "(?s)(?U).*\\S.*", message = "clientSecret 不能为空白")
    String clientSecret,

    /**
     * 客户端名称
     */
    @Pattern(regexp = "(?s)(?U).*\\S.*", message = "clientName 不能为空白")
    String clientName,

    /**
     * 授权类型（逗号分隔）
     */
    @Pattern(regexp = "(?s)(?U).*\\S.*", message = "grantTypes 不能为空白")
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
     * 令牌受众/目标资源服务器标识（逗号分隔；配置后签发的访问令牌写入 aud claim）
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
    Boolean redirectUrisClear,
    Boolean scopesClear,
    Boolean audiencesClear
) {
    @AssertTrue(message = "redirectUris 与 redirectUrisClear 不能同时提供")
    @JsonIgnore
    public boolean isRedirectUrisConflictFree() {
        return redirectUris == null || !Boolean.TRUE.equals(redirectUrisClear);
    }

    @AssertTrue(message = "scopes 与 scopesClear 不能同时提供")
    @JsonIgnore
    public boolean isScopesConflictFree() {
        return scopes == null || !Boolean.TRUE.equals(scopesClear);
    }

    @AssertTrue(message = "audiences 与 audiencesClear 不能同时提供")
    @JsonIgnore
    public boolean isAudiencesConflictFree() {
        return audiences == null || !Boolean.TRUE.equals(audiencesClear);
    }
}
