package cn.ac.fage.accessmesh.admin.dto.auth;

public record LoginResp(
    String accessToken,
    String refreshToken,
    long expiresIn,
    String tokenType,
    Long userId,
    String username,
    Long tenantId,
    boolean forceResetPwd
) {}
