package cn.ac.fage.accessmesh.admin.dto.oauth2;

import jakarta.validation.constraints.NotBlank;

public record AuthorizeReq(
    @NotBlank(message = "clientId 不能为空")
    String clientId,
    @NotBlank(message = "responseType 不能为空")
    String responseType,
    @NotBlank(message = "redirectUri 不能为空")
    String redirectUri,
    String state,
    String scope,
    String codeChallenge,
    String codeChallengeMethod
) {}
