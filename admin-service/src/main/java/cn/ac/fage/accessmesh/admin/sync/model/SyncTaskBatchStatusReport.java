package cn.ac.fage.accessmesh.admin.sync.model;

import java.util.Map;

/**
 * 同步任务批次状态领域报告。
 * <p>
 * Controller 层 {@code SyncTaskBatchStatusResp} 的领域对应物，
 * 由 {@code SyncTaskDomainService.queryBatchStatus} 计算并返回。
 * </p>
 *
 * @param batchKey      批次键原文（如经过 hash 查询，由 service 反查或回填）
 * @param totalTasks    总任务数
 * @param statusCounts  各 status 计数（含 PENDING/PROCESSING/SUCCESS/FAILED）
 * @param phaseCounts   各 phase 下 status 分布：phase -> status -> count
 * @param currentPhase  当前活跃 phase（PENDING/PROCESSING 中 phase 排序最小者）
 * @param hasFailed     是否存在 FAILED 任务
 */
public record SyncTaskBatchStatusReport(
    String batchKey,
    long totalTasks,
    Map<String, Long> statusCounts,
    Map<String, Map<String, Long>> phaseCounts,
    String currentPhase,
    boolean hasFailed
) {}
