package cn.ac.fage.accessmesh.permission.dto.resp;

import java.time.LocalDateTime;

public record OperationLogResp(
    Long id,
    Long tenantId,
    String module,
    String action,
    String targetType,
    Long targetId,
    String summary,
    Long operatorId,
    String operatorName,
    String ipAddress,
    String requestId,
    LocalDateTime createdAt
) {}
