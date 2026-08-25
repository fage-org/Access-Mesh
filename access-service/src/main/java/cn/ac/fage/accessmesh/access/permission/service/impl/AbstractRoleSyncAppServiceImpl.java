package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.exception.SystemException;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLog;
import cn.ac.fage.accessmesh.access.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.access.permission.dto.common.SyncVersionRef;
import cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleFullSyncReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleSyncItem;
import cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleSyncReq;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.permission.entity.SyncMetadata;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.service.AbstractRoleSyncAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionGuard;
import cn.ac.fage.accessmesh.access.permission.service.domain.SyncMetadataDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.sync.SyncAuthVerifier;
import cn.ac.fage.accessmesh.access.permission.service.sync.SyncResultBuilder;
import cn.ac.fage.accessmesh.access.permission.service.domain.SyncTypeGuard;
import cn.ac.fage.accessmesh.access.permission.service.domain.SyncTypeGuard.SyncTypes;
import cn.ac.fage.accessmesh.access.permission.util.SyncKeyCodec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * abstract-role 同步应用服务实现。
 */
@Service
public class AbstractRoleSyncAppServiceImpl implements AbstractRoleSyncAppService {

    static final String ENTITY_KIND = "ABSTRACT_ROLE";
    private static final String OP_UPSERT = "UPSERT";
    private static final String OP_DISABLE = "DISABLE";
    private static final String OP_DELETE = "DELETE";

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String STATUS_DELETED = "DELETED";

    private static final Integer STATUS_ENABLED_VAL = 1;
    private static final Integer STATUS_DISABLED_VAL = 0;

    private final SyncMetadataDomainService syncMetadataDomainService;
    private final TypeResolutionService typeResolutionService;
    private final AbstractRoleMapper abstractRoleMapper;
    private final ObjectMapper objectMapper;
    private final LocalProjectionGuard localProjectionGuard;
    private final SyncTypeGuard syncTypeGuard;

    public AbstractRoleSyncAppServiceImpl(SyncMetadataDomainService syncMetadataDomainService,
                                          TypeResolutionService typeResolutionService,
                                          AbstractRoleMapper abstractRoleMapper,
                                          ObjectMapper objectMapper,
                                          LocalProjectionGuard localProjectionGuard,
                                          SyncTypeGuard syncTypeGuard) {
        this.syncMetadataDomainService = syncMetadataDomainService;
        this.typeResolutionService = typeResolutionService;
        this.abstractRoleMapper = abstractRoleMapper;
        this.objectMapper = objectMapper;
        this.localProjectionGuard = localProjectionGuard;
        this.syncTypeGuard = syncTypeGuard;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "ABSTRACT_ROLE_SYNC", targetType = "abstract_role",
        targetId = "#req.roleExternalId()",
        summary = "'sync abstract_role from ' + #req.sourceService()")
    public SyncResultResp sync(Long tenantId, AbstractRoleSyncReq req, HttpServletRequest httpRequest) {
        // 1. 服务身份校验
        if (!SyncAuthVerifier.verify(req.sourceService(), httpRequest)) {
            return SyncResultBuilder.securityDenied("sourceService mismatch with X-Service-Code");
        }
        localProjectionGuard.rejectInternalSourceService(req.sourceService());
        localProjectionGuard.rejectReservedRoleType(req.roleTypeCode());
        // T-PERM-043：GROUP_ROLE 生命周期冻结——外部同步通道与通用 create/update 同口径拒绝（20022）
        if (PermConstants.TargetType.GROUP_ROLE.equals(req.roleTypeCode())) {
            throw new BizException(PermissionErrorCode.ROLE_TYPE_MISMATCH.getCode(),
                "不支持同步 GROUP_ROLE 分组角色（首期功能角色仅 BASIC_ROLE）");
        }
        // 服务-类型白名单（service_config.extra.syncTypes，fail-closed）：服务须声明该角色类型
        if (!syncTypeGuard.validate(tenantId, req.sourceService(), SyncTypes.role(req.roleTypeCode()))) {
            return SyncResultBuilder.securityDenied("SERVICE_TYPE_NOT_ALLOWED");
        }

        // 2. operation 合法性
        String op = req.operation();
        if (!OP_UPSERT.equals(op) && !OP_DISABLE.equals(op) && !OP_DELETE.equals(op)) {
            return SyncResultBuilder.nonRetryable("invalid operation: " + op);
        }

        // 3. 解析 roleType
        Integer roleType = typeResolutionService.resolveTypeValue(tenantId, "role_type", req.roleTypeCode());
        if (roleType == null) {
            return SyncResultBuilder.nonRetryable("Unknown roleTypeCode: " + req.roleTypeCode());
        }

        // 4. 父角色解析（UPSERT 且 parent 信息齐全时必须找到）
        Long parentId = null;
        if (OP_UPSERT.equals(op)
                && req.parentRoleTypeCode() != null && !req.parentRoleTypeCode().isBlank()
                && req.parentRoleExternalId() != null && !req.parentRoleExternalId().isBlank()) {
            parentId = typeResolutionService.resolveRoleId(
                    tenantId, req.parentRoleTypeCode(), req.parentRoleExternalId(), null);
            if (parentId == null) {
                return SyncResultBuilder.dependencyMissing(
                        "PARENT_ROLE_NOT_FOUND: " + req.parentRoleTypeCode() + ":" + req.parentRoleExternalId());
            }
        }

        // 5. 计算 keys
        String businessKey = SyncKeyCodec.abstractRoleBusinessKey(req.roleTypeCode(), req.roleExternalId());
        String businessKeyHash = SyncKeyCodec.sha256Hex(businessKey);
        String scopeKey = SyncKeyCodec.abstractRoleScopeKey(req.roleTypeCode(), req.treeRootExternalId());
        String scopeKeyHash = SyncKeyCodec.sha256Hex(scopeKey);
        String syncKey = req.sourceService() + "|" + ENTITY_KIND + "|" + businessKey;
        String syncKeyHash = SyncKeyCodec.sha256Hex(syncKey);

        // 6. applyVersion
        SyncMetadataDomainService.ApplyVersionResult ver = syncMetadataDomainService.applyVersion(
                tenantId, ENTITY_KIND, req.sourceService(),
                scopeKeyHash, scopeKey,
                businessKeyHash, businessKey,
                syncKey, syncKeyHash,
                req.syncVersion().occurredAt(), req.syncVersion().sequenceNo());

        if (ver == SyncMetadataDomainService.ApplyVersionResult.STALE) {
            return SyncResultBuilder.stale();
        }

        // 7. 写目标事实表 + markStatus
        Long targetId = applyToTarget(tenantId, roleType, req.roleExternalId(), op,
                req.name(), parentId, req.status(), req.sortOrder(), serializeExtra(req.extra()));

        String targetStatus = switch (op) {
            case OP_UPSERT -> STATUS_ACTIVE;
            case OP_DISABLE -> STATUS_DISABLED;
            case OP_DELETE -> STATUS_DELETED;
            default -> STATUS_ACTIVE;
        };
        syncMetadataDomainService.markStatus(tenantId, ENTITY_KIND, req.sourceService(),
                scopeKeyHash, businessKeyHash, targetStatus);

        // 回填 target_id（仅 UPSERT 路径有意义）
        if (OP_UPSERT.equals(op)) {
            syncMetadataDomainService.backfillTargetId(tenantId, ENTITY_KIND, req.sourceService(),
                    scopeKeyHash, businessKeyHash, targetId);
        }

        return SyncResultBuilder.applied();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "ABSTRACT_ROLE_FULL_SYNC", targetType = "abstract_role",
        targetId = "",
        summary = "'full sync abstract_role from ' + #req.scope().sourceService()")
    public SyncResultResp fullSync(Long tenantId, AbstractRoleFullSyncReq req, HttpServletRequest httpRequest) {
        if (!SyncAuthVerifier.verify(req.scope().sourceService(), httpRequest)) {
            SyncResultResp.ItemResult denied = new SyncResultResp.ItemResult(
                    null, false, false,
                    SyncResultBuilder.RETRY_SECURITY_DENIED, "sourceService mismatch with X-Service-Code");
            return SyncResultBuilder.fullSyncRejected(
                    SyncResultBuilder.RETRY_SECURITY_DENIED,
                    "sourceService mismatch with X-Service-Code",
                    req.items().size(), List.of(denied));
        }
        localProjectionGuard.rejectInternalSourceService(req.scope().sourceService());
        localProjectionGuard.rejectReservedRoleType(req.scope().roleTypeCode());
        // T-PERM-043：GROUP_ROLE 生命周期冻结——外部同步通道与通用 create/update 同口径拒绝（20022）
        if (PermConstants.TargetType.GROUP_ROLE.equals(req.scope().roleTypeCode())) {
            throw new BizException(PermissionErrorCode.ROLE_TYPE_MISMATCH.getCode(),
                "不支持同步 GROUP_ROLE 分组角色（首期功能角色仅 BASIC_ROLE）");
        }
        // 服务-类型白名单（fail-closed）：scope 角色类型须在服务声明的 roleTypeCodes 内
        if (!syncTypeGuard.validate(tenantId, req.scope().sourceService(),
                SyncTypes.role(req.scope().roleTypeCode()))) {
            SyncResultResp.ItemResult denied = new SyncResultResp.ItemResult(
                    null, false, false,
                    SyncResultBuilder.RETRY_SECURITY_DENIED, "SERVICE_TYPE_NOT_ALLOWED");
            return SyncResultBuilder.fullSyncRejected(
                    SyncResultBuilder.RETRY_SECURITY_DENIED, "SERVICE_TYPE_NOT_ALLOWED",
                    req.items().size(), List.of(denied));
        }

        Integer roleType = typeResolutionService.resolveTypeValue(tenantId, "role_type", req.scope().roleTypeCode());
        if (roleType == null) {
            SyncResultResp.ItemResult denied = new SyncResultResp.ItemResult(
                    null, false, false,
                    SyncResultBuilder.RETRY_NON_RETRYABLE,
                    "Unknown roleTypeCode: " + req.scope().roleTypeCode());
            return SyncResultBuilder.fullSyncRejected(
                    SyncResultBuilder.RETRY_NON_RETRYABLE,
                    "Unknown roleTypeCode: " + req.scope().roleTypeCode(),
                    req.items().size(), List.of(denied));
        }

        String scopeKey = SyncKeyCodec.abstractRoleScopeKey(req.scope().roleTypeCode(), req.scope().treeRootExternalId());
        String scopeKeyHash = SyncKeyCodec.sha256Hex(scopeKey);

        // ---- 阶段 A：收集 externalIds 与按 parentTypeCode 分桶的 parent externalIds ----
        Set<String> selfExternalIds = new HashSet<>(req.items().size());
        // parentTypeCode -> Set<parentExternalId>
        Map<String, Set<String>> parentExternalIdsByType = new LinkedHashMap<>();
        for (AbstractRoleSyncItem item : req.items()) {
            selfExternalIds.add(item.roleExternalId());
            if (item.parentRoleTypeCode() != null && !item.parentRoleTypeCode().isBlank()
                    && item.parentRoleExternalId() != null && !item.parentRoleExternalId().isBlank()) {
                parentExternalIdsByType
                        .computeIfAbsent(item.parentRoleTypeCode(), k -> new HashSet<>())
                        .add(item.parentRoleExternalId());
            }
        }

        // ---- 阶段 B：批量预加载现有 abstract_role + 解析所有 parent ----
        Map<String, AbstractRole> existingByExternalId = new HashMap<>();
        if (!selfExternalIds.isEmpty()) {
            for (AbstractRole r : abstractRoleMapper.selectByTypeAndExternalIds(tenantId, roleType, selfExternalIds)) {
                existingByExternalId.put(r.getExternalId(), r);
            }
        }
        // parentTypeCode -> Map<externalId, roleId>
        Map<String, Map<String, Long>> parentResolvedByType = new HashMap<>();
        for (Map.Entry<String, Set<String>> e : parentExternalIdsByType.entrySet()) {
            parentResolvedByType.put(e.getKey(),
                    typeResolutionService.batchResolveRoleIds(tenantId, e.getKey(), e.getValue(), null));
        }

        // ---- 阶段 C：逐项 applyVersion + upsert ----
        int applied = 0;
        int stale = 0;
        int failed = 0;
        List<SyncResultResp.ItemResult> itemResults = new ArrayList<>();
        Set<String> seenBusinessKeyHashes = new HashSet<>();
        LocalDateTime now = LocalDateTime.now();

        for (AbstractRoleSyncItem item : req.items()) {
            String businessKey = SyncKeyCodec.abstractRoleBusinessKey(req.scope().roleTypeCode(), item.roleExternalId());
            String businessKeyHash = SyncKeyCodec.sha256Hex(businessKey);
            seenBusinessKeyHashes.add(businessKeyHash);
            String syncKey = req.scope().sourceService() + "|" + ENTITY_KIND + "|" + businessKey;
            String syncKeyHash = SyncKeyCodec.sha256Hex(syncKey);

            // 父角色解析（命中阶段 B 预加载结果）
            Long parentId = null;
            if (item.parentRoleTypeCode() != null && !item.parentRoleTypeCode().isBlank()
                    && item.parentRoleExternalId() != null && !item.parentRoleExternalId().isBlank()) {
                Map<String, Long> parentMap = parentResolvedByType.get(item.parentRoleTypeCode());
                parentId = parentMap == null ? null : parentMap.get(item.parentRoleExternalId());
                if (parentId == null) {
                    failed++;
                    itemResults.add(new SyncResultResp.ItemResult(
                            businessKey, false, false,
                            SyncResultBuilder.RETRY_DEPENDENCY_MISSING,
                            "PARENT_ROLE_NOT_FOUND: " + item.parentRoleTypeCode() + ":" + item.parentRoleExternalId()));
                    continue;
                }
            }

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

            AbstractRole existing = existingByExternalId.get(item.roleExternalId());
            Long itemTargetId = applyToTargetWithExisting(tenantId, roleType, item.roleExternalId(), OP_UPSERT,
                    item.name(), parentId, item.status(), item.sortOrder(), serializeExtra(item.extra()),
                    existing, now);
            if (existing == null && itemTargetId != null) {
                AbstractRole fresh = new AbstractRole();
                fresh.setId(itemTargetId);
                fresh.setExternalId(item.roleExternalId());
                existingByExternalId.put(item.roleExternalId(), fresh);
            }
            syncMetadataDomainService.markStatus(tenantId, ENTITY_KIND, req.scope().sourceService(),
                    scopeKeyHash, businessKeyHash, STATUS_ACTIVE);
            // 回填 target_id：targetId 已由本地 existing/insert 决定，无需再查
            syncMetadataDomainService.backfillTargetId(tenantId, ENTITY_KIND, req.scope().sourceService(),
                    scopeKeyHash, businessKeyHash, itemTargetId);
            applied++;
            itemResults.add(new SyncResultResp.ItemResult(businessKey, true, false, null, null));
        }

        // 差异校准（批量软删 targetIds）
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
            abstractRoleMapper.softDeleteBatch(tenantId, deactivateTargetIds, LocalDateTime.now());
        }

        return SyncResultBuilder.fullSync(applied, stale, failed, deactivated, itemResults);
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private Long applyToTarget(Long tenantId, Integer roleType, String externalId,
                                String operation, String name, Long parentId,
                                Integer statusVal, Integer sortOrder, String extra) {
        AbstractRole existing = abstractRoleMapper.selectByTypeAndExternalId(tenantId, roleType, externalId);
        return applyToTargetWithExisting(tenantId, roleType, externalId, operation,
                name, parentId, statusVal, sortOrder, extra, existing, LocalDateTime.now());
    }

    /**
     * 与 {@link #applyToTarget} 相同但接受调用方已批量加载的 {@code existing}，避免单条 select。
     * 用于 full-sync 阶段 C。外部业务服务同步写入的行所有权保持 NULL（owner 由本地投影独占）。
     */
    private Long applyToTargetWithExisting(Long tenantId, Integer roleType, String externalId,
                                            String operation, String name, Long parentId,
                                            Integer statusVal, Integer sortOrder, String extra,
                                            AbstractRole existing, LocalDateTime now) {
        if (OP_DELETE.equals(operation)) {
            if (existing != null) {
                abstractRoleMapper.softDeleteBatch(tenantId, List.of(existing.getId()), now);
            }
            return null;
        }

        if (existing == null) {
            AbstractRole role = new AbstractRole();
            role.setTenantId(tenantId);
            role.setRoleType(roleType);
            role.setExternalId(externalId);
            role.setName(name);
            role.setParentId(parentId);
            role.setStatus(OP_DISABLE.equals(operation)
                    ? STATUS_DISABLED_VAL
                    : (statusVal != null ? statusVal : STATUS_ENABLED_VAL));
            role.setSortOrder(sortOrder);
            role.setExtra(extra);
            role.setCreatedAt(now);
            role.setUpdatedAt(now);
            role.setDeleteFlag(0L);
            abstractRoleMapper.insert(role);
            return role.getId();
        }

        // UPDATE
        if (name != null) {
            existing.setName(name);
        }
        if (parentId != null) {
            existing.setParentId(parentId);
        }
        if (OP_DISABLE.equals(operation)) {
            existing.setStatus(STATUS_DISABLED_VAL);
        } else if (statusVal != null) {
            existing.setStatus(statusVal);
        }
        if (sortOrder != null) {
            existing.setSortOrder(sortOrder);
        }
        if (extra != null) {
            existing.setExtra(extra);
        }
        existing.setUpdatedAt(now);
        abstractRoleMapper.update(existing);
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
                    "serialize abstract_role extra failed", e);
        }
    }
}
