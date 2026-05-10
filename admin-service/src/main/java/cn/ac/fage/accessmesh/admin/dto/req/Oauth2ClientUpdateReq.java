package cn.ac.fage.accessmesh.admin.dto.req;

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
 * @param accessTokenTtl  访问令牌有效期（可选，秒，范围60-86400）
 * @param refreshTokenTtl 刷新令牌有效期（可选，秒，范围60-604800）
 * @param status          状态（可选）
 */
public record Oauth2ClientUpdateReq(
    /**
     * 客户端记录ID
     */
    @NotNull Long id,

    /**
     * 客户端密钥（不更新时传null）
     */
    String clientSecret,

    /**
     * 客户端名称
     */
    String clientName,

    /**
     * 授权类型（逗号分隔）
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