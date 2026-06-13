package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.permission.entity.SyncMetadata;
import cn.ac.fage.accessmesh.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.permission.mapper.SyncMetadataMapper;
import cn.ac.fage.accessmesh.permission.service.domain.SyncMetadataDomainService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * {@link SyncMetadataDomainService} 实现。
 * <p>
 * 不声明 {@code @Transactional}，事务由调用方 AppService 声明。
 * </p>
 */
@Service
public class SyncMetadataDomainServiceImpl implements SyncMetadataDomainService {

    /**
     * USER_ROLE 允许的 targetStatus 集合。
     */
    private static final Set<String> USER_ROLE_STATUSES = Set.of("ACTIVE", "UNBOUND");

    /**
     * ABSTRACT_USER / ABSTRACT_ROLE / RESOURCE_ENTITY 允许的 targetStatus 集合。
     */
    private static final Set<String> ENTITY_STATUSES = Set.of("ACTIVE", "DISABLED", "DELETED");

    private static final String KIND_USER_ROLE = "USER_ROLE";
    private static final String KIND_ABSTRACT_USER = "ABSTRACT_USER";
    private static final String KIND_ABSTRACT_ROLE = "ABSTRACT_ROLE";
    private static final String KIND_RESOURCE_ENTITY = "RESOURCE_ENTITY";

    private final SyncMetadataMapper syncMetadataMapper;

    public SyncMetadataDomainServiceImpl(SyncMetadataMapper syncMetadataMapper) {
        this.syncMetadataMapper = syncMetadataMapper;
    }

    @Override
    public ApplyVersionResult applyVersion(Long tenantId,
                                           String entityKind,
                                           String sourceService,
                                           String scopeKeyHash,
                                           String scopeKey,
                                           String businessKeyHash,
                                           String businessKey,
                                           String syncKey,
                                           String syncKeyHash,
                                           LocalDateTime occurredAt,
                                           Long sequenceNo) {
        if (occurredAt == null || sequenceNo == null) {
            throw new IllegalArgumentException("occurredAt and sequenceNo must not be null");
        }

        // 构造待写入对象。target_id / target_status / extra 由 PG upsertIfNewer 在更新分支中保留现存值，
        // 仅在 INSERT 分支生效（默认 ACTIVE，无 targetId）；调用方按 entityKind+operation 显式 markStatus 覆盖。
        SyncMetadata record = new SyncMetadata();
        record.setTenantId(tenantId);
        record.setEntityKind(entityKind);
        record.setSourceService(sourceService);
        record.setScopeKey(scopeKey);
        record.setScopeKeyHash(scopeKeyHash);
        record.setBusinessKey(businessKey);
        record.setBusinessKeyHash(businessKeyHash);
        record.setSyncKey(syncKey);
        record.setSyncKeyHash(syncKeyHash);
        record.setLastSyncOccurredAt(occurredAt);
        record.setLastSyncSequenceNo(sequenceNo);
        record.setTargetStatus("ACTIVE");

        // 版本比较下沉到 PG：ON CONFLICT DO UPDATE ... WHERE excluded.occurred_at > sync_metadata.occurred_at OR (..)
        // 受影响行数=0 表示版本未严格新于现存记录（含相等情形），视为 STALE。
        int affected = syncMetadataMapper.upsertIfNewer(record);
        return affected > 0 ? ApplyVersionResult.APPLIED : ApplyVersionResult.STALE;
    }

    @Override
    public Optional<Long> resolveTargetId(Long tenantId,
                                          String entityKind,
                                          String sourceService,
                                          String scopeKeyHash,
                                          String businessKeyHash) {
        SyncMetadata record = syncMetadataMapper.selectByBusinessKey(
                tenantId, entityKind, sourceService, scopeKeyHash, businessKeyHash);
        if (record == null || record.getTargetId() == null) {
            return Optional.empty();
        }
        return Optional.of(record.getTargetId());
    }

    @Override
    public List<SyncMetadata> listScopeForFullSync(Long tenantId,
                                                   String entityKind,
                                                   String sourceService,
                                                   String scopeKeyHash) {
        return syncMetadataMapper.selectAllInScope(tenantId, entityKind, sourceService, scopeKeyHash);
    }

    @Override
    public int markStatus(Long tenantId,
                          String entityKind,
                          String sourceService,
                          String scopeKeyHash,
                          String businessKeyHash,
                          String targetStatus) {
        validateTargetStatus(entityKind, targetStatus);
        return syncMetadataMapper.updateTargetStatus(
                tenantId, entityKind, sourceService, scopeKeyHash, businessKeyHash, targetStatus);
    }

    @Override
    public void backfillTargetId(Long tenantId,
                                 String entityKind,
                                 String sourceService,
                                 String scopeKeyHash,
                                 String businessKeyHash,
                                 Long targetId) {
        if (targetId == null) {
            return;
        }
        syncMetadataMapper.updateTargetId(
                tenantId, entityKind, sourceService, scopeKeyHash, businessKeyHash, targetId);
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    /**
     * 校验 targetStatus 是否在 entityKind 允许范围内，否则抛 BizException。
     */
    private static void validateTargetStatus(String entityKind, String targetStatus) {
        if (entityKind == null) {
            throw new BizException(PermissionErrorCode.SYNC_TARGET_STATUS_INVALID.getCode(),
                    "entityKind must not be null");
        }
        if (targetStatus == null) {
            throw new BizException(PermissionErrorCode.SYNC_TARGET_STATUS_INVALID.getCode(),
                    "targetStatus must not be null");
        }
        Set<String> allowed = switch (entityKind) {
            case KIND_USER_ROLE -> USER_ROLE_STATUSES;
            case KIND_ABSTRACT_USER, KIND_ABSTRACT_ROLE, KIND_RESOURCE_ENTITY -> ENTITY_STATUSES;
            default -> null;
        };
        if (allowed == null) {
            throw new BizException(PermissionErrorCode.SYNC_TARGET_STATUS_INVALID.getCode(),
                    "Unsupported entityKind for sync_metadata: " + entityKind);
        }
        if (!allowed.contains(targetStatus)) {
            throw new BizException(PermissionErrorCode.SYNC_TARGET_STATUS_INVALID.getCode(),
                    "Invalid targetStatus '" + targetStatus + "' for entityKind " + entityKind
                            + ", allowed: " + allowed);
        }
    }
}
