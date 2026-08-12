package cn.ac.fage.accessmesh.access.admin.dto.resp;

import cn.ac.fage.accessmesh.access.admin.entity.SysSyncTask;

import java.time.LocalDateTime;

/**
 * 同步任务详情响应 DTO
 * <p>
 * 在 {@link SyncTaskResp} 基础上额外暴露 payload / displayAttrs / lastError 等大字段，
 * 用于管理台单条任务详情查看。
 * </p>
 */
public record SyncTaskDetailResp(
    /** 任务ID */
    Long id,

    /** 消息键（唯一标识） */
    String messageKey,

    /** 同步动作 */
    String syncAction,

    /** 业务键原文 */
    String businessKey,

    /** 业务键 hash */
    String businessKeyHash,

    /** 批次键原文 */
    String batchKey,

    /** 批次键 hash */
    String batchKeyHash,

    /** 目标服务 */
    String targetService,

    /** payload 契约版本 */
    Integer payloadVersion,

    /** payload 原文（JSON 字符串） */
    String payload,

    /** displayAttrs 原文（JSON 字符串，仅供 UI 展示） */
    String displayAttrs,

    /** 源事件发生时间 */
    LocalDateTime syncOccurredAt,

    /** 源事件序号 */
    Long syncSequenceNo,

    /** 执行阶段枚举 */
    String phase,

    /** 已重试次数 */
    Integer retryCount,

    /** 最大重试次数 */
    Integer maxRetries,

    /** 下次重试时间 */
    LocalDateTime nextRetryAt,

    /** 任务认领时间 */
    LocalDateTime lockedAt,

    /** 任务认领节点 */
    String lockedBy,

    /** 最近一次错误信息 */
    String lastError,

    /** 状态（PENDING / PROCESSING / SUCCESS / FAILED） */
    String status,

    /** 创建时间 */
    LocalDateTime createdAt,

    /** 更新时间 */
    LocalDateTime updatedAt
) {
    /**
     * 从实体转换为详情响应 DTO（包含 payload / displayAttrs / lastError 等大字段）。
     */
    public static SyncTaskDetailResp from(SysSyncTask entity) {
        return new SyncTaskDetailResp(
            entity.getId(),
            entity.getMessageKey(),
            entity.getSyncAction(),
            entity.getBusinessKey(),
            entity.getBusinessKeyHash(),
            entity.getBatchKey(),
            entity.getBatchKeyHash(),
            entity.getTargetService(),
            entity.getPayloadVersion(),
            entity.getPayload(),
            entity.getDisplayAttrs(),
            entity.getSyncOccurredAt(),
            entity.getSyncSequenceNo(),
            entity.getPhase(),
            entity.getRetryCount(),
            entity.getMaxRetries(),
            entity.getNextRetryAt(),
            entity.getLockedAt(),
            entity.getLockedBy(),
            entity.getLastError(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
