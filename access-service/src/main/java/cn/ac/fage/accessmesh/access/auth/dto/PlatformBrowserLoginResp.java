package cn.ac.fage.accessmesh.access.auth.dto;

public record PlatformBrowserLoginResp(String sessionId, long expiresIn, PlatformAccountResp account) {}
