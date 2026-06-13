package cn.ac.fage.accessmesh.admin.sync.model;

/**
 * 同步任务增强查询的领域参数。
 * <p>
 * 与 controller 层 {@code SyncTaskQueryReq} 一一对应，但只包含过滤维度，不含分页参数。
 * 任意字段为 null 或空字符串时该过滤条件不生效。
 * </p>
 */
public record SyncTaskQueryParams(
    /** 租户 ID（必填） */
    Long tenantId,

    /** 同步动作 */
    String syncAction,

    /** 状态 */
    String status,

    /** 执行阶段 */
    String phase,

    /** 批次键原文（精确匹配） */
    String batchKey,

    /** 业务键原文（精确匹配） */
    String businessKey
) {}
