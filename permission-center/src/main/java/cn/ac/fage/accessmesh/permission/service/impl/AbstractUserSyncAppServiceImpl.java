package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.SystemException;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import cn.ac.fage.accessmesh.permission.dto.common.SyncVersionRef;
import cn.ac.fage.accessmesh.permission.dto.req.AbstractUserFullSyncReq;
import cn.ac.fage.accessmesh.permission.dto.req.AbstractUserSyncItem;
import cn.ac.fage.accessmesh.permission.dto.req.AbstractUserSyncReq;
import cn.ac.fage.accessmesh.permission.entity.AbstractUser;
import cn.ac.fage.accessmesh.permission.entity.SyncMetadata;
import cn.ac.fage.accessmesh.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.permission.service.AbstractUserSyncAppService;
import cn.ac.fage.accessmesh.permission.service.domain.SyncMetadataDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.sync.SyncAuthVerifier;
import cn.ac.fage.accessmesh.permission.service.sync.SyncResultBuilder;
import cn.ac.fage.accessmesh.permission.util.SyncKeyCodec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * abstract-user 同步应用服务实现。
 */
@Service
public class AbstractUserSyncAppServiceImpl implements AbstractUserSyncAppService {

    static final String ENTITY_KIND = "ABSTRACT_USER";
    private static final String OP_UPSERT = "UPSERT";
    private static final String OP_DISABLE = "DISABLE";
    private static final String OP_DELETE = "DELETE";

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String STATUS_DELETED = "DELETED";

    private final SyncMetadataDomainService syncMetadataDomainService;
    private final TypeResolutionService typeResolutionService;
    private final AbstractUserMapper abstractUserMapper;
    private final ObjectMapper objectMapper;

    public AbstractUserSyncAppServiceImpl(SyncMetadataDomainService syncMetadataDomainService,
                                          TypeResolutionService typeResolutionService,
                                          AbstractUserMapper abstractUserMapper,
                                          ObjectMapper objectMapper) {
        this.syncMetadataDomainService = syncMetadataDomainService;
        this.typeResolutionService = typeResolutionService;
        this.abstractUserMapper = abstractUserMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SyncResultResp sync(Long tenantId, AbstractUserSyncReq req, HttpServletRequest httpRequest) {
        // 1. 服务身份校验
        if (!SyncAuthVerifier.verify(req.sourceService(), httpRequest)) {
            return SyncResultBuilder.securityDenied("sourceService mismatch with X-Service-Code");
        }

        // 2. 校验 operation 合法
        String op = req.operation();
        if (!OP_UPSERT.equals(op) && !OP_DISABLE.equals(op) && !OP_DELETE.equals(op)) {
            return SyncResultBuilder.nonRetryable("invalid operation: " + op);
        }

        // 3. 解析 subjectTypeCode -> userType
        Integer userType = typeResolutionService.resolveTypeValue(tenantId, "user_type", req.subjectTypeCode());
        if (userType == null) {
            return SyncResultBuilder.nonRetryable("Unknown subjectTypeCode: " + req.subjectTypeCode());
        }

        // 4. 计算 keys
        String businessKey = SyncKeyCodec.abstractUserBusinessKey(req.subjectTypeCode(), req.subjectExternalId());
        String businessKeyHash = SyncKeyCodec.sha256Hex(businessKey);
        String scopeKey = SyncKeyCodec.abstractUserScopeKey(req.subjectTypeCode());
        String scopeKeyHash = SyncKeyCodec.sha256Hex(scopeKey);
        String syncKey = req.sourceService() + "|" + ENTITY_KIND + "|" + businessKey;
        String syncKeyHash = SyncKeyCodec.sha256Hex(syncKey);

        // 5. applyVersion
        SyncMetadataDomainService.ApplyVersionResult ver = syncMetadataDomainService.applyVersion(
                tenantId, ENTITY_KIND, req.sourceService(),
                scopeKeyHash, scopeKey,
                businessKeyHash, businessKey,
                syncKey, syncKeyHash,
                req.syncVersion().occurredAt(), req.syncVersion().sequenceNo());

        if (ver == SyncMetadataDomainService.ApplyVersionResult.STALE) {
            return SyncResultBuilder.stale();
        }

        // 6. 写目标事实表 + markStatus
        Long targetId = applyToTarget(tenantId, userType, req.subjectExternalId(), op,
                req.name(), req.enabled(), serializeExtra(req.extra()));

        String targetStatus = switch (op) {
            case OP_UPSERT -> STATUS_ACTIVE;
            case OP_DISABLE -> STATUS_DISABLED;
            case OP_DELETE -> STATUS_DELETED;
            default -> STATUS_ACTIVE;
        };
        syncMetadataDomainService.markStatus(tenantId, ENTITY_KIND, req.sourceService(),
                scopeKeyHash, businessKeyHash, targetStatus);

        // 回填 target_id（仅 UPSERT 路径有意义；DISABLE/DELETE 时 targetId 可能已存在或被软删，
        // 此时不回填）
        if (OP_UPSERT.equals(op)) {
            syncMetadataDomainService.backfillTargetId(tenantId, ENTITY_KIND, req.sourceService(),
                    scopeKeyHash, businessKeyHash, targetId);
        }

        return SyncResultBuilder.applied();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SyncResultResp fullSync(Long tenantId, AbstractUserFullSyncReq req, HttpServletRequest httpRequest) {
        // 1. 服务身份校验
        if (!SyncAuthVerifier.verify(req.scope().sourceService(), httpRequest)) {
            SyncResultResp.ItemResult denied = new SyncResultResp.ItemResult(
                    null, false, false,
                    SyncResultBuilder.RETRY_SECURITY_DENIED, "sourceService mismatch with X-Service-Code");
            return SyncResultBuilder.fullSyncRejected(
                    SyncResultBuilder.RETRY_SECURITY_DENIED,
                    "sourceService mismatch with X-Service-Code",
                    req.items().size(), List.of(denied));
        }

        Integer userType = typeResolutionService.resolveTypeValue(tenantId, "user_type", req.scope().subjectTypeCode());
        if (userType == null) {
            SyncResultResp.ItemResult denied = new SyncResultResp.ItemResult(
                    null, false, false,
                    SyncResultBuilder.RETRY_NON_RETRYABLE,
                    "Unknown subjectTypeCode: " + req.scope().subjectTypeCode());
            return SyncResultBuilder.fullSyncRejected(
                    SyncResultBuilder.RETRY_NON_RETRYABLE,
                    "Unknown subjectTypeCode: " + req.scope().subjectTypeCode(),
                    req.items().size(), List.of(denied));
        }

        String scopeKey = SyncKeyCodec.abstractUserScopeKey(req.scope().subjectTypeCode());
        String scopeKeyHash = SyncKeyCodec.sha256Hex(scopeKey);

        // ---- 阶段 A：收集所有 externalIds ----
        Set<String> externalIds = new HashSet<>(req.items().size());
        for (AbstractUserSyncItem item : req.items()) {
            externalIds.add(item.subjectExternalId());
        }

        // ---- 阶段 B：批量预加载现有 abstract_user ----
        Map<String, AbstractUser> existingByExternalId = new HashMap<>();
        if (!externalIds.isEmpty()) {
            for (AbstractUser u : abstractUserMapper.selectByTypeAndExternalIds(tenantId, userType, externalIds)) {
                existingByExternalId.put(u.getExternalId(), u);
            }
        }

        // ---- 阶段 C：逐项 applyVersion + upsert 决策 + markStatus ----
        int applied = 0;
        int stale = 0;
        int failed = 0;
        List<SyncResultResp.ItemResult> itemResults = new ArrayList<>();
        Set<String> seenBusinessKeyHashes = new HashSet<>();

        LocalDateTime now = LocalDateTime.now();
        for (AbstractUserSyncItem item : req.items()) {
            String businessKey = SyncKeyCodec.abstractUserBusinessKey(
                    req.scope().subjectTypeCode(), item.subjectExternalId());
            String businessKeyHash = SyncKeyCodec.sha256Hex(businessKey);
            seenBusinessKeyHashes.add(businessKeyHash);
            String syncKey = req.scope().sourceService() + "|" + ENTITY_KIND + "|" + businessKey;
            String syncKeyHash = SyncKeyCodec.sha256Hex(syncKey);

            SyncVersionRef ver = item.syncVersion();
            SyncMetadataDomainService.ApplyVersionResult result = syncMetadataDomainService.applyVersion(
                    tenantId, ENTITY_KIND, req.scope().sourceService(),
                    scopeKeyHash, scopeKey, businessKeyHash, businessKey,
                    syncKey, syncKeyHash, ver.occurredAt(), ver.sequenceNo());

            if (result == SyncMetadataDomainService.ApplyVersionResult.STALE) {
                stale++;
                itemResults.add(new SyncResultResp.ItemResult(
                        businessKey, false, true,
                        SyncResultBuilder.RETRY_STALE_VERSION, SyncResultBuilder.REASON_STALE));
                continue;
            }

            AbstractUser existing = existingByExternalId.get(item.subjectExternalId());
            Long itemTargetId = applyToTargetWithExisting(tenantId, userType, item.subjectExternalId(),
                    OP_UPSERT, item.name(), item.enabled(), serializeExtra(item.extra()),
                    existing, now);
            // 写入后更新 cache：新插入的 existing 会在 mapper.insert 中获得 id；后续 item 不会重复同 externalId
            if (existing == null && itemTargetId != null) {
                AbstractUser fresh = new AbstractUser();
                fresh.setId(itemTargetId);
                fresh.setExternalId(item.subjectExternalId());
                existingByExternalId.put(item.subjectExternalId(), fresh);
            }
            syncMetadataDomainService.markStatus(tenantId, ENTITY_KIND, req.scope().sourceService(),
                    scopeKeyHash, businessKeyHash, STATUS_ACTIVE);
            // 回填 target_id：full-sync items 均按 UPSERT 处理；targetId 已由本地 existing/insert 决定，无需再查
            syncMetadataDomainService.backfillTargetId(tenantId, ENTITY_KIND, req.scope().sourceService(),
                    scopeKeyHash, businessKeyHash, itemTargetId);
            applied++;
            itemResults.add(new SyncResultResp.ItemResult(businessKey, true, false, null, null));
        }

        // 差异校准：scope 内有但 items 缺失 -> DELETE（批量软删 targetIds）
        int deactivated = 0;
        List<SyncMetadata> existing = syncMetadataDomainService.listScopeForFullSync(
                tenantId, ENTITY_KIND, req.scope().sourceService(), scopeKeyHash);
        List<Long> deactivateTargetIds = new ArrayList<>();
        for (SyncMetadata md : existing) {
            if (md.getBusinessKeyHash() == null || seenBusinessKeyHashes.contains(md.getBusinessKeyHash())) {
                continue;
            }
            if (STATUS_DELETED.equals(md.getTargetStatus())) {
                continue;
            }
            if (md.getTargetId() != null) {
                deactivateTargetIds.add(md.getTargetId());
            }
            syncMetadataDomainService.markStatus(tenantId, ENTITY_KIND, req.scope().sourceService(),
                    scopeKeyHash, md.getBusinessKeyHash(), STATUS_DELETED);
            deactivated++;
        }
        if (!deactivateTargetIds.isEmpty()) {
            abstractUserMapper.softDeleteBatch(tenantId, deactivateTargetIds, LocalDateTime.now());
        }

        return SyncResultBuilder.fullSync(applied, stale, failed, deactivated, itemResults);
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    /**
     * 将业务变更落到 abstract_user 表。
     *
     * @return 写入/已存在的 abstract_user.id；DELETE 操作或 existing 缺失时返回 {@code null}
     */
    private Long applyToTarget(Long tenantId, Integer userType, String externalId,
                                String operation,
                                String name, Boolean enabled, String extra) {
        AbstractUser existing = abstractUserMapper.selectByTypeAndExternalId(tenantId, userType, externalId);
        return applyToTargetWithExisting(tenantId, userType, externalId, operation,
                name, enabled, extra, existing, LocalDateTime.now());
    }

    /**
     * 与 {@link #applyToTarget} 相同但接受调用方已批量加载的 {@code existing}，避免单条 select。
     * 用于 full-sync 阶段 C。
     */
    private Long applyToTargetWithExisting(Long tenantId, Integer userType, String externalId,
                                            String operation,
                                            String name, Boolean enabled, String extra,
                                            AbstractUser existing, LocalDateTime now) {
        if (OP_DELETE.equals(operation)) {
            if (existing != null) {
                abstractUserMapper.softDeleteBatch(tenantId, List.of(existing.getId()), now);
            }
            return null;
        }

        if (existing == null) {
            AbstractUser user = new AbstractUser();
            user.setTenantId(tenantId);
            user.setUserType(userType);
            user.setExternalId(externalId);
            user.setName(name);
            user.setEnabled(OP_DISABLE.equals(operation) ? Boolean.FALSE : (enabled != null ? enabled : Boolean.TRUE));
            user.setExtra(extra);
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            user.setDeleteFlag(0L);
            abstractUserMapper.insert(user);
            return user.getId();
        }

        // UPDATE
        if (name != null) {
            existing.setName(name);
        }
        if (OP_DISABLE.equals(operation)) {
            existing.setEnabled(Boolean.FALSE);
        } else if (enabled != null) {
            existing.setEnabled(enabled);
        }
        if (extra != null) {
            existing.setExtra(extra);
        }
        existing.setUpdatedAt(now);
        abstractUserMapper.update(existing);
        return existing.getId();
    }

    /**
     * 把 sync DTO 中的 {@code extra}（{@code Map<String,Object>}）序列化为 JSONB 文本，
     * 失败包装为 {@link SystemException}（保留 cause）。
     *
     * @return JSON 字符串；入参为 {@code null} 或空 Map 时返回 {@code null}
     */
    private String serializeExtra(Map<String, Object> extra) {
        if (extra == null || extra.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(extra);
        } catch (JsonProcessingException e) {
            throw new SystemException(PermissionErrorCode.SYSTEM_INIT_FAILED.getCode(),
                    "serialize abstract_user extra failed", e);
        }
    }
}
