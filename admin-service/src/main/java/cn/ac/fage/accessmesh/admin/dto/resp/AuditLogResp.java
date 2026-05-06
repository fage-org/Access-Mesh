package cn.ac.fage.accessmesh.admin.dto.resp;

import cn.ac.fage.accessmesh.admin.entity.SysAuditLog;

import java.time.LocalDateTime;

/**
 * Audit log response DTO.
 * Excludes sensitive fields like requestBody that may contain passwords, tokens, or PII.
 */
public record AuditLogResp(
    Long id,
    String module,
    String operation,
    String targetType,
    String targetId,
    String summary,
    String username,
    String uri,
    String ipAddress,
    Integer statusCode,
    Integer durationMs,
    LocalDateTime createdAt
) {
    public static AuditLogResp from(SysAuditLog entity) {
        return new AuditLogResp(
            entity.getId(),
            entity.getModule(),
            entity.getAction(),
            entity.getTargetType(),
            entity.getTargetId(),
            entity.getSummary(),
            entity.getUsername(),
            entity.getRequestUrl(),
            entity.getIpAddress(),
            entity.getResponseCode(),
            entity.getCostTime(),
            entity.getCreatedAt()
        );
    }
}
