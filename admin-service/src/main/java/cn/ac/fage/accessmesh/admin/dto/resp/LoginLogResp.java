package cn.ac.fage.accessmesh.admin.dto.resp;

import cn.ac.fage.accessmesh.admin.entity.SysLoginLog;

import java.time.LocalDateTime;

/**
 * Login log response DTO.
 * Hides internal fields (tenantId, userId) while exposing audit-relevant PII fields.
 */
public record LoginLogResp(
    Long id,
    String username,
    String clientId,
    String loginType,
    Integer status,
    String ipAddress,
    String userAgent,
    String location,
    String failReason,
    LocalDateTime loginAt
) {
    public static LoginLogResp from(SysLoginLog entity) {
        return new LoginLogResp(
            entity.getId(),
            entity.getUsername(),
            entity.getClientId(),
            entity.getLoginType(),
            entity.getStatus(),
            entity.getIpAddress(),
            entity.getUserAgent(),
            entity.getLocation(),
            entity.getFailReason(),
            entity.getLoginAt()
        );
    }
}
