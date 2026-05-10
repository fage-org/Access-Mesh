package cn.ac.fage.accessmesh.admin.dto.resp;

import cn.ac.fage.accessmesh.admin.entity.SysSyncRetry;

import java.time.LocalDateTime;

/**
 * 同步重试响应记录类
 * <p>
 * 用于返回同步重试任务查询结果。
 * 排除敏感字段：payload（消息内容）、lastError（错误详情）、
 * tenantId（租户隔离）、createdBy/updatedBy/deletedBy/deletedAt/deleteFlag（审计字段）。
 * </p>
 *
 * @param id            同步重试ID
 * @param messageKey    消息键（唯一标识）
 * @param targetService 目标服务
 * @param entityType    实体类型
 * @param externalId    外部ID
 * @param operationType 操作类型（create、update、delete）
 * @param retryCount    已重试次数
 * @param maxRetries    最大重试次数
 * @param status        状态（pending、retrying、success、failed）
 * @param nextRetryAt   下次重试时间
 * @param createdAt     创建时间
 * @param updatedAt     更新时间
 */
public record SyncRetryResp(
    /**
     * 同步重试ID
     */
    Long id,

    /**
     * 消息键（唯一标识）
     */
    String messageKey,

    /**
     * 目标服务
     */
    String targetService,

    /**
     * 实体类型
     */
    String entityType,

    /**
     * 外部ID
     */
    String externalId,

    /**
     * 操作类型（create、update、delete）
     */
    String operationType,

    /**
     * 已重试次数
     */
    Integer retryCount,

    /**
     * 最大重试次数
     */
    Integer maxRetries,

    /**
     * 状态（pending、retrying、success、failed）
     */
    String status,

    /**
     * 下次重试时间
     */
    LocalDateTime nextRetryAt,

    /**
     * 创建时间
     */
    LocalDateTime createdAt,

    /**
     * 更新时间
     */
    LocalDateTime updatedAt
) {
    /**
     * 从实体转换为响应DTO（排除敏感字段）
     *
     * @param entity 同步重试实体
     * @return 同步重试响应DTO
     */
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