package cn.ac.fage.accessmesh.admin.dto.resp;

import cn.ac.fage.accessmesh.admin.entity.SysOauth2Client;

import java.time.LocalDateTime;

/**
 * OAuth2客户端响应DTO
 * 注意：clientSecret字段不返回，防止敏感信息泄露
 */
public record Oauth2ClientResp(
    Long id,
    Long tenantId,
    String clientId,
    String clientName,
    String grantTypes,
    String redirectUris,
    String scopes,
    Integer accessTokenTtl,
    Integer refreshTokenTtl,
    Integer status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    /**
     * 从实体转换为响应DTO（排除clientSecret）
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
            entity.getAccessTokenTtl(),
            entity.getRefreshTokenTtl(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}