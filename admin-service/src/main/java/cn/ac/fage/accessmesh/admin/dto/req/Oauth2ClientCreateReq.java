package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * OAuth2客户端创建请求DTO
 */
public record Oauth2ClientCreateReq(
    @NotBlank String clientId,
    @NotBlank String clientSecret,
    @NotBlank String clientName,
    String grantTypes,
    String redirectUris,
    String scopes,
    @Min(60) @Max(86400) Integer accessTokenTtl,
    @Min(60) @Max(604800) Integer refreshTokenTtl,
    Integer status
) {}