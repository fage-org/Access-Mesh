package cn.ac.fage.accessmesh.access.infrastructure.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 任务执行记录实体（T-ACCESS-002 预建，T-ACCESS-009 使用）
 * <p>
 * 对应数据库表 sys_task_execution：多实例下同一 execution_key 最多一个活动执行者
 * （唯一约束保证），租约由 lease_owner/lease_until 承载，execution_key 即幂等标识。
 * 原子抢占/续租/接管/条件完成 SQL 由 T-ACCESS-009 扩展。
 * </p>
 */
@Getter
@Setter
@Table("sys_task_execution")
public class SysTaskExecution {

    /**
     * 主键ID（自增）
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 计划实例稳定执行键（如 jobId + scheduledTime），租户内唯一；
     * 外部副作用携带该键实现幂等
     */
    private String executionKey;

    /**
     * 执行状态：PENDING/RUNNING/SUCCESS/FAILED
     */
    private String status;

    /**
     * 租约持有实例标识（如 host:pid），故障后其他实例可接管
     */
    private String leaseOwner;

    /**
     * 租约截止时间；过期后其他实例可原子抢占
     */
    private LocalDateTime leaseUntil;

    /**
     * 已尝试执行次数（幂等重试计数）
     */
    private Integer attemptCount;

    /**
     * 最近一次失败原因
     */
    private String lastError;

    /**
     * 开始执行时间
     */
    private LocalDateTime startedAt;

    /**
     * 结束执行时间
     */
    private LocalDateTime finishedAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 删除时间
     */
    private LocalDateTime deletedAt;

    /**
     * 删除标记（0=未删除，删除时填本行id）
     */
    private Long deleteFlag;
}
