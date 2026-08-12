package cn.ac.fage.accessmesh.access.admin.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 同步任务实体类
 * <p>
 * 对应数据库表 {@code sys_sync_task}，作为 admin-service 与 permission-center 之间
 * 的本地消息表。主业务事务内写任务，事务外重放，保障跨服务最终一致性。
 * </p>
 * <p>
 * 字段对齐 {@code docs/design/schema/admin-service.sql} 中 {@code sys_sync_task} 定义。
 * 旧的 {@code entityType / externalId / operationType} 不再作为执行字段，
 * 统一收敛进 {@link #displayAttrs}（仅供 UI/审计展示，严禁参与执行路由）。
 * </p>
 */
@Getter
@Setter
@Table("sys_sync_task")
public class SysSyncTask {

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
     * 事件唯一键，每次业务变更唯一；合并 PENDING 任务时覆盖为最新事件 key。
     */
    private String messageKey;

    /**
     * 同步动作（PERM_ABSTRACT_USER_SYNC / PERM_ABSTRACT_ROLE_SYNC /
     * PERM_USER_ROLE_SYNC / PERM_RESOURCE_ENTITY_SYNC 等）。
     */
    private String syncAction;

    /**
     * 同步业务键原文，用于排查；不直接参与唯一索引。
     */
    private String businessKey;

    /**
     * {@link #businessKey} 的 SHA-256 lowercase hex，用于唯一约束和索引。
     */
    private String businessKeyHash;

    /**
     * 全量校准批次键原文；单次实时同步为空。
     */
    private String batchKey;

    /**
     * {@link #batchKey} 的 SHA-256 lowercase hex，用于同批次 phase 推进查询。
     */
    private String batchKeyHash;

    /**
     * 目标服务（默认 {@code permission-center}）。
     */
    private String targetService;

    /**
     * 同步请求参数快照（JSONB），必须匹配 {@link #syncAction} 对应的强类型 DTO。
     * <p>S1 阶段以原始 JSON 字符串保存；JSONB 类型适配（PgObject / TypeHandler）由 S4 任务生产器实施。
     */
    private String payload;

    /**
     * payload 契约版本；handler 遇到高于自身支持上限的版本必须拒绝。
     */
    private Integer payloadVersion;

    /**
     * 仅供 UI/审计展示的冗余信息（如 entityType/externalId/operationType）。
     * 严禁用于执行路由或业务判断。
     * <p>S1 阶段以原始 JSON 字符串保存；JSONB 类型适配由 S4 任务生产器实施。
     */
    private String displayAttrs;

    /**
     * 源事件发生时间，参与 syncVersion 乱序判断。
     */
    private LocalDateTime syncOccurredAt;

    /**
     * 源事件序号，与 {@link #syncOccurredAt} 共同构成 syncVersion。
     */
    private Long syncSequenceNo;

    /**
     * 执行阶段枚举（USER_SUBJECT / USER_RESOURCE / ORG_RESOURCE / ORG_ROLE /
     * USER_ROLE / MENU_RESOURCE / OTHER_RESOURCE 等）。
     */
    private String phase;

    /**
     * 已重试次数。
     */
    private Integer retryCount;

    /**
     * 最大自动重试次数。
     */
    private Integer maxRetries;

    /**
     * 下次重试时间（退避策略计算）。
     */
    private LocalDateTime nextRetryAt;

    /**
     * 任务认领时间，用于多实例调度防重复执行。
     */
    private LocalDateTime lockedAt;

    /**
     * 任务认领节点标识。
     */
    private String lockedBy;

    /**
     * 最后一次失败原因。
     */
    private String lastError;

    /**
     * 状态（PENDING / PROCESSING / SUCCESS / FAILED）。
     */
    private String status;

    /**
     * 创建人ID
     */
    private Long createdBy;

    /**
     * 更新人ID
     */
    private Long updatedBy;

    /**
     * 删除人ID
     */
    private Long deletedBy;

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
     * 删除标记（0=未删除，否则记录被删除时的ID）
     */
    private Long deleteFlag;
}
