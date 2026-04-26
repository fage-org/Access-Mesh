package cn.ac.fage.accessmesh.admin.dto.oauth2;

public record TokenResp(
    String accessToken,
    String tokenType,
    int expiresIn,
    String refreshToken,
    String scope
) {}
