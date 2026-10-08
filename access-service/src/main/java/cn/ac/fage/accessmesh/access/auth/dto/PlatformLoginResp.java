package cn.ac.fage.accessmesh.access.auth.dto;

public record PlatformLoginResp(String accessToken, long expiresIn, PlatformAccountResp account) {}
