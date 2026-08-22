package cn.ac.fage.accessmesh.access.permission.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import cn.ac.fage.accessmesh.access.infrastructure.JsonbStringTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 外部同步元数据实体
 * <p>
 * 统一记录 abstract_user / abstract_role / user_role / resource_entity 的外部同步来源、scope、业务键、
 * 目标内部 ID 与最后同步事件版本，用于：
 * <ul>
 *   <li>旧版本 no-op：通过 last_sync_occurred_at + last_sync_sequence_no 原子比较，旧版本事件不写库；</li>
 *   <li>full-sync 差异校准：按 (tenantId, entityKind, sourceService, scopeKeyHash) 圈定 ownership 范围；</li>
 *   <li>业务键到 target_id 的稳定映射，避免外部 ID 反复反查目标事实表。</li>
 * </ul>
 * scopeKey/businessKey/syncKey 的格式由 api-contract.md §6.2.2.4 规范化，不包含 tenantId/sourceService/entityKind。
 * </p>
 *
 * @author AccessMesh Team
 */
@Getter
@Setter
@Table("sync_metadata")
public class SyncMetadata {

    /**
     * 主键
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 租户 ID，用于多租户隔离
     */
    private Long tenantId;

    /**
     * 同步实体类型：ABSTRACT_USER / ABSTRACT_ROLE / USER_ROLE / RESOURCE_ENTITY
     */
    private String entityKind;

    /**
     * 同步来源服务编码，与服务间认证主体一致
     */
    private String sourceService;

    /**
     * full-sync 清理范围键原文（api-contract §6.2.2.4 规范化）
     */
    private String scopeKey;

    /**
     * scopeKey 的 SHA-256 lowercase hex
     */
    private String scopeKeyHash;

    /**
     * 同步对象业务键原文（api-contract §6.2.2.4 规范化）
     */
    private String businessKey;

    /**
     * businessKey 的 SHA-256 lowercase hex
     */
    private String businessKeyHash;

    /**
     * 来源内稳定同步键，格式 sourceService|entityKind|businessKey
     */
    private String syncKey;

    /**
     * syncKey 的 SHA-256 lowercase hex
     */
    private String syncKeyHash;

    /**
     * 目标事实表内部 ID，仅 access-service 内部使用
     */
    private Long targetId;

    /**
     * 目标同步状态：
     * <ul>
     *   <li>USER_ROLE：仅 ACTIVE / UNBOUND；</li>
     *   <li>ABSTRACT_USER / ABSTRACT_ROLE / RESOURCE_ENTITY：仅 ACTIVE / DISABLED / DELETED。</li>
     * </ul>
     */
    private String targetStatus;

    /**
     * 最后已应用同步事件发生时间
     */
    private LocalDateTime lastSyncOccurredAt;

    /**
     * 最后已应用同步事件序号，与 occurredAt 共同判断版本新旧
     */
    private Long lastSyncSequenceNo;

    /**
     * 扩展信息（JSONB 原文）
     */
    @Column(typeHandler = JsonbStringTypeHandler.class)
    private String extra;

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
     * 删除标记（0=未删除，其他=已删除）
     */
    private Long deleteFlag;
}
