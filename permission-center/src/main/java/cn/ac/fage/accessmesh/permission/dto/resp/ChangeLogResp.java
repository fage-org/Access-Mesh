package cn.ac.fage.accessmesh.permission.dto.resp;

import java.time.LocalDateTime;

public record ChangeLogResp(
    Long id,
    Long tenantId,
    Long bizDomainId,
    String entityType,
    Long entityId,
    String operation,
    String oldSnapshot,
    String newSnapshot,
    String diffSnapshot,
    Long[] affectedAbstractUserIds,
    Long[] affectedAbstractRoleIds,
    String changeReason,
    String changeSource,
    String requestId,
    LocalDateTime createdAt
) {}
