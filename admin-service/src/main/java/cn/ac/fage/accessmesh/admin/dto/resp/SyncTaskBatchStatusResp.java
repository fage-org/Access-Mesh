package cn.ac.fage.accessmesh.admin.dto.resp;

import java.util.Map;

/**
 * 同步任务批次状态响应 DTO
 * <p>
 * 描述某 batchKey 下的整体进度，包括各 status 总计数、各 phase 下的 status 分布、
 * 当前活跃的 phase（PENDING/PROCESSING 中 phase 排序最小），以及是否存在失败任务。
 * </p>
 */
public record SyncTaskBatchStatusResp(
    /** 批次键原文 */
    String batchKey,

    /** 总任务数 */
    long totalTasks,

    /** 各 status 计数（PENDING/PROCESSING/SUCCESS/FAILED） */
    Map<String, Long> statusCounts,

    /** 各 phase 下 status 分布：phase -> status -> count */
    Map<String, Map<String, Long>> phaseCounts,

    /** 当前活跃 phase（PENDING/PROCESSING 中 phase 排序最小者，全部完成时为 null） */
    String currentPhase,

    /** 是否存在失败任务 */
    boolean hasFailed
) {}
