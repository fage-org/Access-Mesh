package cn.ac.fage.accessmesh.admin.dto.oauth2;

import jakarta.validation.constraints.NotBlank;

public record TokenReq(
    @NotBlank(message = "grantType 不能为空")
    String grantType,
    String clientId,
    String clientSecret,
    String code,
    String redirectUri,
    String codeVerifier,
    String refreshToken
) {}
