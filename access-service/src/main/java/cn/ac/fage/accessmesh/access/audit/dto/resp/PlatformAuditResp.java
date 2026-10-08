package cn.ac.fage.accessmesh.access.audit.dto.resp;

import cn.ac.fage.accessmesh.access.audit.entity.PlatformAuditLog;
import java.time.LocalDateTime;

public record PlatformAuditResp(Long id, Long operatorId, String operatorName, Long targetTenantId,
    String targetType, String targetId, String action, String outcome, String summary,
    String requestId, String ipAddress, LocalDateTime createdAt) {
    public static PlatformAuditResp from(PlatformAuditLog row) {
        return new PlatformAuditResp(row.getId(), row.getOperatorId(), row.getOperatorName(), row.getTargetTenantId(),
            row.getTargetType(), row.getTargetId(), row.getAction(), row.getOutcome(), row.getSummary(),
            row.getRequestId(), row.getIpAddress(), row.getCreatedAt());
    }
}
