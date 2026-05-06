package cn.ac.fage.accessmesh.admin.dto.resp;

import cn.ac.fage.accessmesh.admin.entity.SysSyncRetry;

import java.time.LocalDateTime;

/**
 * Sync retry response DTO.
 * Excludes sensitive fields: payload, lastError, tenantId, createdBy, updatedBy, deletedBy, deletedAt, deleteFlag.
 */
public record SyncRetryResp(
    Long id,
    String messageKey,
    String targetService,
    String entityType,
    String externalId,
    String operationType,
    Integer retryCount,
    Integer maxRetries,
    String status,
    LocalDateTime nextRetryAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static SyncRetryResp from(SysSyncRetry entity) {
        return new SyncRetryResp(
            entity.getId(),
            entity.getMessageKey(),
            entity.getTargetService(),
            entity.getEntityType(),
            entity.getExternalId(),
            entity.getOperationType(),
            entity.getRetryCount(),
            entity.getMaxRetries(),
            entity.getStatus(),
            entity.getNextRetryAt(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
