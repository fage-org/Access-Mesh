package cn.ac.fage.accessmesh.access.auth.dto;

/** Browser metadata only: the native authentication credential never appears in JSON. */
public record BrowserLoginResp(String sessionId, long expiresIn, Long userId, String username, Long tenantId, boolean forceResetPwd) {}
