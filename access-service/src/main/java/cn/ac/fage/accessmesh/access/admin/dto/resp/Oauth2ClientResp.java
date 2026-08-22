package cn.ac.fage.accessmesh.access.admin.dto.resp;

import cn.ac.fage.accessmesh.access.admin.entity.SysOauth2Client;

import java.time.LocalDateTime;

/**
 * OAuth2客户端响应记录类
 * <p>
 * 用于返回OAuth2客户端配置信息。
 * 注意：clientSecret字段不返回，防止敏感信息泄露。
 * </p>
 *
 * @param id              客户端记录ID
 * @param tenantId        租户ID
 * @param clientId        客户端标识
 * @param clientName      客户端名称
 * @param grantTypes      授权类型（逗号分隔）
 * @param redirectUris    重定向URI列表（逗号分隔）
 * @param scopes          权限范围（逗号分隔）
 * @param audiences       令牌受众/资源服务器标识（逗号分隔）
 * @param accessTokenTtl  访问令牌有效期（秒）
 * @param refreshTokenTtl 刷新令牌有效期（秒）
 * @param status          状态（0=正常，1=禁用）
 * @param createdAt       创建时间
 * @param updatedAt       更新时间
 */
public record Oauth2ClientResp(
    /**
     * 客户端记录ID
     */
    Long id,

    /**
     * 租户ID
     */
    Long tenantId,

    /**
     * 客户端标识（OAuth2协议中的client_id）
     */
    String clientId,

    /**
     * 客户端名称（显示名称）
     */
    String clientName,

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
     * 令牌受众/目标资源服务器标识（逗号分隔）
     */
    String audiences,

    /**
     * 访问令牌有效期（秒）
     */
    Integer accessTokenTtl,

    /**
     * 刷新令牌有效期（秒）
     */
    Integer refreshTokenTtl,

    /**
     * 状态（0=正常，1=禁用）
     */
    Integer status,

    /**
     * 创建时间
     */
    LocalDateTime createdAt,

    /**
     * 更新时间
     */
    LocalDateTime updatedAt
) {
    /**
     * 从实体转换为响应DTO（排除clientSecret）
     *
     * @param entity OAuth2客户端实体
     * @return OAuth2客户端响应DTO，entity为null时返回null
     */
    public static Oauth2ClientResp fromEntity(SysOauth2Client entity) {
        if (entity == null) return null;
        return new Oauth2ClientResp(
            entity.getId(),
            entity.getTenantId(),
            entity.getClientId(),
            entity.getClientName(),
            entity.getGrantTypes(),
            entity.getRedirectUris(),
            entity.getScopes(),
            entity.getAudiences(),
            entity.getAccessTokenTtl(),
            entity.getRefreshTokenTtl(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}