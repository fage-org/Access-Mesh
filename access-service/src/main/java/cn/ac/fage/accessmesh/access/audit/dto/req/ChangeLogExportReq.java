package cn.ac.fage.accessmesh.access.audit.dto.req;

import java.time.LocalDateTime;

/** All matching rows, without pagination. */
public record ChangeLogExportReq(
    String entityType,
    Long entityId,
    String eventType,
    String changeSource,
    Long affectedUserId,
    Long affectedRoleId,
    LocalDateTime since,
    LocalDateTime until
) {}