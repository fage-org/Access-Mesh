package cn.ac.fage.accessmesh.access.permission.mapper;

import cn.ac.fage.accessmesh.access.permission.entity.SyncMetadata;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 外部同步元数据数据访问接口
 * <p>
 * 提供 {@link SyncMetadata} 表的基础 CRUD、按业务键定位、按 scope 全量列表，以及 upsert 与软删除等操作。
 * 仅承担数据访问职责，不包含同步领域规则；版本比较与状态校验由 SyncMetadataDomainService 承担。
 * </p>
 */
public interface SyncMetadataMapper extends BaseMapper<SyncMetadata> {

    /**
     * 根据业务键定位元数据。
     *
     * @param tenantId        租户 ID
     * @param entityKind      实体类型
     * @param sourceService   同步来源服务
     * @param scopeKeyHash    scopeKey 的 SHA-256 hex
     * @param businessKeyHash businessKey 的 SHA-256 hex
     * @return 匹配的元数据，未找到返回 null
     */
    List<SyncMetadata> selectByBusinessKeyHashes(@Param("tenantId") Long tenantId,
                                                  @Param("entityKind") String entityKind,
                                                  @Param("sourceService") String sourceService,
                                                  @Param("scopeKeyHash") String scopeKeyHash,
                                                  @Param("businessKeyHashes") java.util.Set<String> businessKeyHashes);

    SyncMetadata selectByBusinessKey(@Param("tenantId") Long tenantId,
                                     @Param("entityKind") String entityKind,
                                     @Param("sourceService") String sourceService,
                                     @Param("scopeKeyHash") String scopeKeyHash,
                                     @Param("businessKeyHash") String businessKeyHash);

    /**
     * 列出指定 scope 下全部有效元数据，用于 full-sync 差异校准。
     *
     * @param tenantId      租户 ID
     * @param entityKind    实体类型
     * @param sourceService 同步来源服务
     * @param scopeKeyHash  scopeKey 的 SHA-256 hex
     * @return scope 下的元数据列表
     */
    List<SyncMetadata> selectAllInScope(@Param("tenantId") Long tenantId,
                                        @Param("entityKind") String entityKind,
                                        @Param("sourceService") String sourceService,
                                        @Param("scopeKeyHash") String scopeKeyHash);

    /**
     * 按业务键插入或更新元数据。
     * <p>
     * 当 (tenantId, entityKind, sourceService, scopeKeyHash, businessKeyHash) 命中已有记录则更新，否则插入。
     * 调用方负责保证 lastSyncOccurredAt + lastSyncSequenceNo 严格新于现存记录。
     * </p>
     *
     * @param record 待落库的同步元数据
     * @return 受影响行数
     */
    int upsert(@Param("record") SyncMetadata record);

    /**
     * 原子的“仅当新版本时”插入或更新元数据，将版本比较下沉到 PostgreSQL。
     * <p>
     * 语义：
     * <ul>
     *   <li>若 (tenantId, entityKind, sourceService, scopeKeyHash, businessKeyHash) 命中现存记录：
     *       仅当传入的 (lastSyncOccurredAt, lastSyncSequenceNo) 严格新于现存记录时才执行 UPDATE，
     *       且仅更新版本相关字段（scope_key/business_key/sync_key/sync_key_hash/last_sync_occurred_at/last_sync_sequence_no/updated_at），
     *       不覆盖 target_id / target_status / extra；</li>
     *   <li>若不命中：直接 INSERT；</li>
     *   <li>若命中但版本不严格新于：返回 0，调用方据此判定为 STALE。</li>
     * </ul>
     * 通过 PostgreSQL 的 ON CONFLICT DO UPDATE ... WHERE 子句保证版本比较与写入是同一原子操作，
     * 避免 select-then-upsert 带来的并发覆盖。
     *
     * @param metadata 待落库的同步元数据（占位字段需完整）
     * @return 受影响行数：1=插入或更新成功；0=已存在更新版本（视为 STALE）
     */
    int upsertIfNewer(@Param("metadata") SyncMetadata metadata);

    /**
     * 软删除指定业务键对应的元数据。
     *
     * @param tenantId        租户 ID
     * @param entityKind      实体类型
     * @param sourceService   同步来源服务
     * @param scopeKeyHash    scopeKey 的 SHA-256 hex
     * @param businessKeyHash businessKey 的 SHA-256 hex
     * @param deletedAt       软删除时间
     * @return 受影响行数
     */
    int softDelete(@Param("tenantId") Long tenantId,
                   @Param("entityKind") String entityKind,
                   @Param("sourceService") String sourceService,
                   @Param("scopeKeyHash") String scopeKeyHash,
                   @Param("businessKeyHash") String businessKeyHash,
                   @Param("deletedAt") LocalDateTime deletedAt);

    /**
     * 更新指定业务键对应元数据的 target_status。
     *
     * @param tenantId        租户 ID
     * @param entityKind      实体类型
     * @param sourceService   同步来源服务
     * @param scopeKeyHash    scopeKey 的 SHA-256 hex
     * @param businessKeyHash businessKey 的 SHA-256 hex
     * @param targetStatus    目标状态
     * @return 受影响行数
     */
    int updateTargetStatus(@Param("tenantId") Long tenantId,
                           @Param("entityKind") String entityKind,
                           @Param("sourceService") String sourceService,
                           @Param("scopeKeyHash") String scopeKeyHash,
                           @Param("businessKeyHash") String businessKeyHash,
                           @Param("targetStatus") String targetStatus);

    /**
     * Update target_id directly without select-then-upsert.
     */
    int updateTargetId(@Param("tenantId") Long tenantId,
                       @Param("entityKind") String entityKind,
                       @Param("sourceService") String sourceService,
                       @Param("scopeKeyHash") String scopeKeyHash,
                       @Param("businessKeyHash") String businessKeyHash,
                       @Param("targetId") Long targetId);
}
