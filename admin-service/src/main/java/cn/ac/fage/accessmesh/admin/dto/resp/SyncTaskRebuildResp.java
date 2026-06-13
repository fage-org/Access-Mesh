package cn.ac.fage.accessmesh.admin.dto.resp;

/**
 * 同步任务全量校准触发响应 DTO
 */
public record SyncTaskRebuildResp(
    /** 本次启动的 batchKey 原文 */
    String batchKey,

    /** 本次入队的任务数 */
    long taskCount
) {}
