package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * OAuth2客户端更新请求DTO
 */
public record Oauth2ClientUpdateReq(
    @NotNull Long id,
    String clientSecret,  // 可选，不更新时传null
    String clientName,
    String grantTypes,
    String redirectUris,
    String scopes,
    @Min(60) @Max(86400) Integer accessTokenTtl,
    @Min(60) @Max(604800) Integer refreshTokenTtl,
    Integer status
) {}