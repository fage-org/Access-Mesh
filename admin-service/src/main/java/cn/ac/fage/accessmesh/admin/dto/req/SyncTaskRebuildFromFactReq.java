package cn.ac.fage.accessmesh.admin.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 同步任务全量校准触发请求 DTO
 * <p>
 * 触发一次新的全量校准（生成新 batchKey）。
 * </p>
 */
public record SyncTaskRebuildFromFactReq(
    /** 来源服务编码（约定为 admin-service） */
    @NotBlank String sourceService,

    /** 触发方（cron / manual / username） */
    String triggeredBy
) {}
