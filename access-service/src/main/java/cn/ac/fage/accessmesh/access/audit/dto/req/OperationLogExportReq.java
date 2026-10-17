package cn.ac.fage.accessmesh.access.audit.dto.req;

import java.time.LocalDateTime;

/** All matching rows, without pagination. */
public record OperationLogExportReq(
    String module,
    String action,
    Long operatorId,
    LocalDateTime since,
    LocalDateTime until,
    String targetType,
    @jakarta.validation.constraints.Size(max = 64) String requestId
) {}
