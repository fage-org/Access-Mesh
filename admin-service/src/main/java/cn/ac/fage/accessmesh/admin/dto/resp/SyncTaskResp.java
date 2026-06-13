package cn.ac.fage.accessmesh.admin.dto.resp;

import cn.ac.fage.accessmesh.admin.entity.SysSyncTask;

import java.time.LocalDateTime;

/**
 * 同步任务响应记录类
 * <p>
 * 用于返回 {@code sys_sync_task} 的查询结果，对外暴露执行所需关键字段，
 * 排除 payload / displayAttrs 等大字段以及租户/审计字段。
 * 状态枚举仅可能为 PENDING / PROCESSING / SUCCESS / FAILED。
 * </p>
 */
public record SyncTaskResp(
    /**
     * 任务ID
     */
    Long id,

    /**
     * 消息键（唯一标识）
     */
    String messageKey,

    /**
     * 同步动作（PERM_ABSTRACT_USER_SYNC / PERM_USER_ROLE_SYNC 等）
     */
    String syncAction,

    /**
     * 业务键原文
     */
    String businessKey,

    /**
     * 业务键 SHA-256 hash
     */
    String businessKeyHash,

    /**
     * 批次键原文（实时同步为空）
     */
    String batchKey,

    /**
     * 批次键 SHA-256 hash
     */
    String batchKeyHash,

    /**
     * 目标服务（默认 permission-center）
     */
    String targetService,

    /**
     * payload 契约版本
     */
    Integer payloadVersion,

    /**
     * 源事件发生时间
     */
    LocalDateTime syncOccurredAt,

    /**
     * 源事件序号
     */
    Long syncSequenceNo,

    /**
     * 执行阶段枚举
     */
    String phase,

    /**
     * 已重试次数
     */
    Integer retryCount,

    /**
     * 最大重试次数
     */
    Integer maxRetries,

    /**
     * 下次重试时间
     */
    LocalDateTime nextRetryAt,

    /**
     * 任务认领时间
     */
    LocalDateTime lockedAt,

    /**
     * 任务认领节点
     */
    String lockedBy,

    /**
     * 状态（PENDING / PROCESSING / SUCCESS / FAILED）
     */
    String status,

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
     * 从实体转换为响应DTO（排除 payload / displayAttrs / tenantId / lastError 等敏感或大字段）。
     *
     * @param entity 同步任务实体
     * @return 同步任务响应DTO
     */
    public static SyncTaskResp from(SysSyncTask entity) {
        return new SyncTaskResp(
            entity.getId(),
            entity.getMessageKey(),
            entity.getSyncAction(),
            entity.getBusinessKey(),
            entity.getBusinessKeyHash(),
            entity.getBatchKey(),
            entity.getBatchKeyHash(),
            entity.getTargetService(),
            entity.getPayloadVersion(),
            entity.getSyncOccurredAt(),
            entity.getSyncSequenceNo(),
            entity.getPhase(),
            entity.getRetryCount(),
            entity.getMaxRetries(),
            entity.getNextRetryAt(),
            entity.getLockedAt(),
            entity.getLockedBy(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
