package cn.ac.fage.accessmesh.access.admin.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 同步任务批次状态查询请求 DTO
 * <p>
 * 查询某 batchKey 下的阶段进度，包括各 status / phase 计数，
 * 以及当前活跃 phase 与是否存在失败任务。
 * </p>
 */
public record SyncTaskBatchStatusReq(
    /** 批次键原文（service 内会 hash 后查询） */
    @NotBlank String batchKey
) {}
