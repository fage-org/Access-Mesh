package cn.ac.fage.accessmesh.access.permission.service.domain;

import cn.ac.fage.accessmesh.access.permission.entity.SyncMetadata;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 同步元数据领域服务
 * <p>
 * 负责 {@link SyncMetadata} 上的版本原子比较、targetStatus 校验更新、target_id 业务键解析、
 * full-sync 差异校准范围列出等同步领域规则。本服务只维护 {@code sync_metadata}，
 * 不直接写目标事实表（abstract_user/abstract_role/user_role/resource_entity），由调用方负责。
 * </p>
 *
 * <p>
 * 事务边界由调用方（AppService）声明，本接口实现不声明 {@code @Transactional}。
 * </p>
 */
public interface SyncMetadataDomainService {

    /**
     * applyVersion 结果。
     */
    enum ApplyVersionResult {
        /**
         * 新版本已写入：metadata 已 upsert。
         */
        APPLIED,
        /**
         * 旧版本：occurredAt + sequenceNo 不严格新于现存记录，未写入任何数据。
         */
        STALE
    }

    /**
     * 原子比较 {@code last_sync_occurred_at + last_sync_sequence_no} 应用同步事件版本：
     * <ul>
     *   <li>若现存记录存在且 (existing.occurredAt > occurredAt) 或 (occurredAt 相等且 existing.sequenceNo &gt;= sequenceNo)，
     *       返回 {@link ApplyVersionResult#STALE}，不调用 Mapper.upsert；</li>
     *   <li>否则按本次入参 upsert sync_metadata 并返回 {@link ApplyVersionResult#APPLIED}。</li>
     * </ul>
     * 本方法不会写目标事实表，也不会主动设置 {@code targetId/targetStatus} —— 这些由调用方根据本结果再调用
     * {@link #markStatus} 或下次 applyVersion 时一并传入 extra 字段维护。
     *
     * @param tenantId        租户 ID
     * @param entityKind      实体类型
     * @param sourceService   同步来源服务
     * @param scopeKeyHash    scopeKey 的 SHA-256 hex
     * @param scopeKey        scopeKey 原文
     * @param businessKeyHash businessKey 的 SHA-256 hex
     * @param businessKey     businessKey 原文
     * @param syncKey         syncKey 原文（{@code sourceService|entityKind|businessKey}）
     * @param syncKeyHash     syncKey 的 SHA-256 hex
     * @param occurredAt      事件发生时间
     * @param sequenceNo      事件序号
     * @return APPLIED / STALE
     */
    ApplyVersionResult applyVersion(Long tenantId,
                                    String entityKind,
                                    String sourceService,
                                    String scopeKeyHash,
                                    String scopeKey,
                                    String businessKeyHash,
                                    String businessKey,
                                    String syncKey,
                                    String syncKeyHash,
                                    LocalDateTime occurredAt,
                                    Long sequenceNo);

    /**
     * 按业务键解析目标事实表内部 ID。
     *
     * @param tenantId        租户 ID
     * @param entityKind      实体类型
     * @param sourceService   同步来源服务
     * @param scopeKeyHash    scopeKey 的 SHA-256 hex
     * @param businessKeyHash businessKey 的 SHA-256 hex
     * @return target_id（非空软删除记录），未匹配返回 {@link Optional#empty()}
     */
    Optional<Long> resolveTargetId(Long tenantId,
                                   String entityKind,
                                   String sourceService,
                                   String scopeKeyHash,
                                   String businessKeyHash);

    /**
     * 列出 scope 下全部有效元数据，供 full-sync 差异校准。
     */
    List<SyncMetadata> listScopeForFullSync(Long tenantId,
                                            String entityKind,
                                            String sourceService,
                                            String scopeKeyHash);

    /**
     * 更新指定业务键对应元数据的 {@code target_status}。
     * <p>
     * 校验规则（违反时抛 {@link cn.ac.fage.accessmesh.common.exception.BizException}）：
     * <ul>
     *   <li>USER_ROLE：仅允许 {@code ACTIVE} / {@code UNBOUND}；</li>
     *   <li>ABSTRACT_USER / ABSTRACT_ROLE / RESOURCE_ENTITY：仅允许 {@code ACTIVE} / {@code DISABLED} / {@code DELETED}。</li>
     * </ul>
     *
     * @return 受影响行数（0 表示业务键无对应有效记录）
     */
    int markStatus(Long tenantId,
                   String entityKind,
                   String sourceService,
                   String scopeKeyHash,
                   String businessKeyHash,
                   String targetStatus);

    /**
     * 回填 sync_metadata 的 {@code target_id}（业务键到事实表内部 ID 的映射）。
     * <p>
     * 行为：
     * <ul>
     *   <li>{@code targetId} 为 {@code null}：直接返回，不查询、不写入。</li>
     *   <li>对应业务键的 metadata 不存在：直接返回，不写入。</li>
     *   <li>existing 存在且 targetId 非空：更新 target_id 字段并 upsert，其它字段保持现存值。</li>
     * </ul>
     *
     * @param tenantId        租户 ID
     * @param entityKind      实体类型（ABSTRACT_USER / ABSTRACT_ROLE / RESOURCE_ENTITY / USER_ROLE）
     * @param sourceService   同步来源服务
     * @param scopeKeyHash    scopeKey 的 SHA-256 hex
     * @param businessKeyHash businessKey 的 SHA-256 hex
     * @param targetId        目标事实表内部 ID
     */
    void backfillTargetId(Long tenantId,
                          String entityKind,
                          String sourceService,
                          String scopeKeyHash,
                          String businessKeyHash,
                          Long targetId);
}
