package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLog;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleFullSyncReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleSyncItem;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleSyncReq;
import cn.ac.fage.accessmesh.access.permission.constant.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.permission.entity.SyncMetadata;
import cn.ac.fage.accessmesh.access.permission.entity.UserRole;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.UserRoleMapper;
import cn.ac.fage.accessmesh.access.permission.service.UserRoleSyncAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionGuard;
import cn.ac.fage.accessmesh.access.permission.service.domain.SyncMetadataDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.sync.SyncAuthVerifier;
import cn.ac.fage.accessmesh.access.permission.service.sync.SyncResultBuilder;
import cn.ac.fage.accessmesh.access.permission.service.domain.SyncTypeGuard;
import cn.ac.fage.accessmesh.access.permission.service.domain.SyncTypeGuard.SyncTypes;
import cn.ac.fage.accessmesh.access.permission.util.SyncKeyCodec;
import com.mybatisflex.core.query.QueryWrapper;
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
import java.util.stream.Collectors;

import cn.ac.fage.accessmesh.access.permission.entity.table.UserRoleTableDef;

/**
 * {@link UserRoleSyncAppService} 实现。
 * <p>
 * 事务边界由本类声明；DomainService 不声明事务。
 * </p>
 */
@Service
public class UserRoleSyncAppServiceImpl implements UserRoleSyncAppService {

    private static final String ENTITY_KIND = "USER_ROLE";
    private static final String OP_BIND = "BIND";
    private static final String OP_UNBIND = "UNBIND";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_UNBOUND = "UNBOUND";

    private final SyncMetadataDomainService syncMetadataDomainService;
    private final TypeResolutionService typeResolutionService;
    private final UserRoleMapper userRoleMapper;
    private final LocalProjectionGuard localProjectionGuard;
    private final SyncTypeGuard syncTypeGuard;

    public UserRoleSyncAppServiceImpl(SyncMetadataDomainService syncMetadataDomainService,
                                       TypeResolutionService typeResolutionService,
                                       UserRoleMapper userRoleMapper,
                                       LocalProjectionGuard localProjectionGuard,
                                       SyncTypeGuard syncTypeGuard) {
        this.syncMetadataDomainService = syncMetadataDomainService;
        this.typeResolutionService = typeResolutionService;
        this.userRoleMapper = userRoleMapper;
        this.localProjectionGuard = localProjectionGuard;
        this.syncTypeGuard = syncTypeGuard;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "USER_ROLE_SYNC", targetType = "user_role",
        targetId = "#req.subjectExternalId()",
        summary = "'sync user_role op=' + #req.operation() + ' role=' + #req.roleExternalId() + ' from ' + #req.sourceService()")
    public SyncResultResp sync(Long tenantId, UserRoleSyncReq req, HttpServletRequest httpRequest) {
        // 1. 身份校验
        if (!SyncAuthVerifier.verify(req.sourceService(), httpRequest)) {
            return SyncResultBuilder.securityDenied("SOURCE_SERVICE_MISMATCH");
        }
        localProjectionGuard.rejectInternalSourceService(req.sourceService());
        // 2. payload 校验：sourceType/主体/目标角色/relationKey 角色类型均为调用方自有类型，
        // 保留键（SYS_USER_ORG/LOCAL_USER/ORG|POSITION）由 guard 拒绝（20045 整体回滚）
        localProjectionGuard.rejectReservedUserRoleSource(req.sourceType());
        localProjectionGuard.rejectReservedSubjectType(req.subjectTypeCode());
        localProjectionGuard.rejectReservedRoleType(req.roleTypeCode());
        rejectReservedRelationType(req.relationKey());
        // 服务-类型白名单（service_config.extra.syncTypes，fail-closed）：服务须声明
        // sourceType/主体/目标角色类型（relationKey 角色类型为引用，由依赖解析负责）
        if (!syncTypeGuard.validate(tenantId, req.sourceService(),
                SyncTypes.userRole(Set.of(req.subjectTypeCode()), Set.of(req.roleTypeCode()),
                        Set.of(req.sourceType())))) {
            return SyncResultBuilder.securityDenied("SERVICE_TYPE_NOT_ALLOWED");
        }
        if (!OP_BIND.equals(req.operation()) && !OP_UNBIND.equals(req.operation())) {
            return SyncResultBuilder.nonRetryable("INVALID_OPERATION");
        }
        return doSyncOne(tenantId, req);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "USER_ROLE_FULL_SYNC", targetType = "user_role",
        targetId = "",
        summary = "'full sync user_role from ' + #req.scope().sourceService()")
    public SyncResultResp fullSync(Long tenantId, UserRoleFullSyncReq req, HttpServletRequest httpRequest) {
        if (!SyncAuthVerifier.verify(req.scope().sourceService(), httpRequest)) {
            return SyncResultBuilder.fullSyncRejected(
                    SyncResultBuilder.RETRY_SECURITY_DENIED, "SOURCE_SERVICE_MISMATCH",
                    req.items().size(),
                    List.of(new SyncResultResp.ItemResult("*", false, false,
                            SyncResultBuilder.RETRY_SECURITY_DENIED, "SOURCE_SERVICE_MISMATCH")));
        }
        localProjectionGuard.rejectInternalSourceService(req.scope().sourceService());
        localProjectionGuard.rejectReservedUserRoleSource(req.scope().sourceType());

        String scopeKey = SyncKeyCodec.userRoleScopeKey(
                req.scope().sourceType(), req.scope().roleTypeCode(), req.scope().treeRootExternalId());
        String scopeKeyHash = SyncKeyCodec.sha256Hex(scopeKey);

        // ---- 阶段 A：按 typeCode 分桶收集 subject/role/relation 的 externalId ----
        // subjectTypeCode -> Set<externalId>
        Map<String, Set<String>> subjectExternalIdsByType = new HashMap<>();
        // roleTypeCode -> Set<externalId>
        Map<String, Set<String>> roleExternalIdsByType = new HashMap<>();
        // relation typeCode -> Set<externalId>（解析自 relationKey "TYPE:externalId"）
        Map<String, Set<String>> relationExternalIdsByType = new HashMap<>();
        for (UserRoleSyncItem item : req.items()) {
            // item 级保留键拒绝：主体/目标角色/relationKey 角色类型均须为调用方自有类型
            localProjectionGuard.rejectReservedSubjectType(item.subjectTypeCode());
            localProjectionGuard.rejectReservedRoleType(item.roleTypeCode());
            rejectReservedRelationType(item.relationKey());
            subjectExternalIdsByType.computeIfAbsent(item.subjectTypeCode(), k -> new HashSet<>())
                    .add(item.subjectExternalId());
            roleExternalIdsByType.computeIfAbsent(item.roleTypeCode(), k -> new HashSet<>())
                    .add(item.roleExternalId());
            String rk = item.relationKey();
            if (rk != null && !rk.isBlank()) {
                int idx = rk.indexOf(':');
                if (idx > 0 && idx < rk.length() - 1) {
                    relationExternalIdsByType.computeIfAbsent(rk.substring(0, idx), k -> new HashSet<>())
                            .add(rk.substring(idx + 1));
                }
            }
        }

        // 服务-类型白名单（fail-closed）：item 主体类型去重 + scope 角色类型/sourceType 一次校验
        if (!syncTypeGuard.validate(tenantId, req.scope().sourceService(),
                SyncTypes.userRole(subjectExternalIdsByType.keySet(),
                        Set.of(req.scope().roleTypeCode()), Set.of(req.scope().sourceType())))) {
            return SyncResultBuilder.fullSyncRejected(
                    SyncResultBuilder.RETRY_SECURITY_DENIED, "SERVICE_TYPE_NOT_ALLOWED",
                    req.items().size(),
                    List.of(new SyncResultResp.ItemResult("*", false, false,
                            SyncResultBuilder.RETRY_SECURITY_DENIED, "SERVICE_TYPE_NOT_ALLOWED")));
        }

        // ---- 阶段 B：批量解析 subject/role/relation IDs ----
        // subjectTypeCode -> (externalId -> userId)
        Map<String, Map<String, Long>> subjectResolved = new HashMap<>();
        for (Map.Entry<String, Set<String>> e : subjectExternalIdsByType.entrySet()) {
            subjectResolved.put(e.getKey(),
                    typeResolutionService.batchResolveUserIds(tenantId, e.getKey(), e.getValue()));
        }
        Map<String, Map<String, Long>> roleResolved = new HashMap<>();
        for (Map.Entry<String, Set<String>> e : roleExternalIdsByType.entrySet()) {
            roleResolved.put(e.getKey(),
                    typeResolutionService.batchResolveRoleIds(tenantId, e.getKey(), e.getValue(), null));
        }
        Map<String, Map<String, Long>> relationResolved = new HashMap<>();
        for (Map.Entry<String, Set<String>> e : relationExternalIdsByType.entrySet()) {
            relationResolved.put(e.getKey(),
                    typeResolutionService.batchResolveRoleIds(tenantId, e.getKey(), e.getValue(), null));
        }

        // 预加载现有 user_role：先收集所有解析成功的 (userId, roleId, relationId) 三元组的候选集
        Set<Long> candidateUserIds = new HashSet<>();
        Set<Long> candidateRoleIds = new HashSet<>();
        Set<Long> candidateRelationIds = new HashSet<>();
        for (UserRoleSyncItem item : req.items()) {
            Long uid = subjectResolved.getOrDefault(item.subjectTypeCode(), Map.of()).get(item.subjectExternalId());
            Long rid = roleResolved.getOrDefault(item.roleTypeCode(), Map.of()).get(item.roleExternalId());
            Long relId = resolveRelationFromCache(item.relationKey(), relationResolved);
            if (uid != null) candidateUserIds.add(uid);
            if (rid != null) candidateRoleIds.add(rid);
            if (relId != null) candidateRelationIds.add(relId);
        }
        // (userId, roleId, relationId) -> UserRole
        Map<TriKey, UserRole> existingByTriKey = new HashMap<>();
        if (!candidateUserIds.isEmpty() && !candidateRoleIds.isEmpty() && !candidateRelationIds.isEmpty()) {
            for (UserRole ur : userRoleMapper.selectValidByUserTargetRelation(
                    tenantId, candidateUserIds, candidateRoleIds, candidateRelationIds, ResourceTypeCode.ROLE)) {
                existingByTriKey.put(new TriKey(ur.getAbstractUserId(), ur.getTargetId(), ur.getRelationId()), ur);
            }
        }

        // 归属预加载：当前 scope 全部 metadata 一次加载（businessKeyHash -> target_id），
        // 供阶段 C 归属校验复用（避免每个存量行一次 resolveTargetId 的 N+1），差异校准同源复用
        List<SyncMetadata> scopeMetadata = syncMetadataDomainService.listScopeForFullSync(
                tenantId, ENTITY_KIND, req.scope().sourceService(), scopeKeyHash);
        Map<String, Long> ownedTargetIdsByBusinessKeyHash = new HashMap<>();
        for (SyncMetadata md : scopeMetadata) {
            if (md.getTargetId() != null) {
                ownedTargetIdsByBusinessKeyHash.put(md.getBusinessKeyHash(), md.getTargetId());
            }
        }

        // ---- 阶段 C：逐项 applyVersion + upsert ----
        int applied = 0, stale = 0, failed = 0, deactivated = 0;
        List<SyncResultResp.ItemResult> itemResults = new ArrayList<>(req.items().size());
        Set<String> seenBusinessKeyHashes = new HashSet<>();
        LocalDateTime now = LocalDateTime.now();

        for (UserRoleSyncItem item : req.items()) {
            String businessKey = SyncKeyCodec.userRoleBusinessKey(
                    item.subjectTypeCode(), item.subjectExternalId(),
                    item.roleTypeCode(), item.roleExternalId(), item.relationKey());
            // 同批重复 businessKey：写入前拒绝（existingByTriKey 为循环前一次性加载，
            // 重复项二次 INSERT 会触发 uk_user_role 唯一约束整批回滚）
            if (!seenBusinessKeyHashes.add(SyncKeyCodec.sha256Hex(businessKey))) {
                failed++;
                itemResults.add(new SyncResultResp.ItemResult(businessKey, false, false,
                        SyncResultBuilder.RETRY_NON_RETRYABLE, "DUPLICATE_BUSINESS_KEY"));
                continue;
            }

            // 守卫：item.roleTypeCode 必须等于 scope.roleTypeCode；不一致直接 NON_RETRYABLE 失败，
            // 不进入 markStatus 路径，避免污染 metadata
            if (!req.scope().roleTypeCode().equals(item.roleTypeCode())) {
                failed++;
                itemResults.add(new SyncResultResp.ItemResult(businessKey, false, false,
                        SyncResultBuilder.RETRY_NON_RETRYABLE, "ROLE_TYPE_CODE_MISMATCH_WITH_SCOPE"));
                continue;
            }

            UserRoleSyncReq oneReq = new UserRoleSyncReq(
                    OP_BIND, req.scope().sourceType(),
                    item.subjectTypeCode(), item.subjectExternalId(),
                    item.roleTypeCode(), req.scope().treeRootExternalId(),
                    item.roleExternalId(), item.relationKey(),
                    item.validFrom(), item.validTo(),
                    req.scope().sourceService(),
                    item.sourceEntityType(), item.sourceEntityId(),
                    item.syncVersion());

            Long preUserId = subjectResolved.getOrDefault(item.subjectTypeCode(), Map.of())
                    .get(item.subjectExternalId());
            Long preRoleId = roleResolved.getOrDefault(item.roleTypeCode(), Map.of())
                    .get(item.roleExternalId());
            Long preRelId = resolveRelationFromCache(item.relationKey(), relationResolved);
            UserRole preExisting = (preUserId != null && preRoleId != null && preRelId != null)
                    ? existingByTriKey.get(new TriKey(preUserId, preRoleId, preRelId))
                    : null;

            SyncResultResp r = doSyncOneInternal(tenantId, oneReq,
                    preUserId, preRoleId, preRelId, preExisting, true, now,
                    ownedTargetIdsByBusinessKeyHash);
            if (r.applied()) {
                applied++;
                // backfillTargetId 已在 doSyncOneInternal 内完成（upserted.getId()），无需额外查 DB。
            } else if (r.stale()) stale++;
            else failed++;
            itemResults.add(new SyncResultResp.ItemResult(businessKey, r.applied(), r.stale(),
                    r.retryClass(), r.reason()));
        }

        // 差异校准（批量软删 targetIds；复用阶段 C 前的归属预加载结果，不再二次查询）
        List<Long> deactivateTargetIds = new ArrayList<>();
        for (SyncMetadata md : scopeMetadata) {
            if (seenBusinessKeyHashes.contains(md.getBusinessKeyHash())) continue;
            if (!STATUS_ACTIVE.equals(md.getTargetStatus())) continue;
            syncMetadataDomainService.markStatus(tenantId, ENTITY_KIND,
                    req.scope().sourceService(), scopeKeyHash, md.getBusinessKeyHash(), STATUS_UNBOUND);
            if (md.getTargetId() != null) {
                deactivateTargetIds.add(md.getTargetId());
            }
            deactivated++;
        }
        // 本地投影保护：scope 内出现 access-service 所有权行时仅标记 UNBOUND 不软删
        // （防御：历史残留 metadata 指向本地行；本地投影不可被外部 full-sync 差异清理）
        if (!deactivateTargetIds.isEmpty()) {
            Set<Long> localOwnedIds = userRoleMapper.selectValidByIds(tenantId, deactivateTargetIds).stream()
                    .filter(ur -> LocalProjectionOwner.isLocalOwner(ur.getOwnerServiceCode()))
                    .map(UserRole::getId)
                    .collect(Collectors.toSet());
            deactivateTargetIds.removeAll(localOwnedIds);
        }
        if (!deactivateTargetIds.isEmpty()) {
            userRoleMapper.softDeleteBatch(tenantId, deactivateTargetIds, now);
        }

        return SyncResultBuilder.fullSync(applied, stale, failed, deactivated, itemResults);
    }

    private static Long resolveRelationFromCache(String relationKey,
                                                  Map<String, Map<String, Long>> relationResolved) {
        if (relationKey == null || relationKey.isBlank()) return null;
        int idx = relationKey.indexOf(':');
        if (idx <= 0 || idx == relationKey.length() - 1) return null;
        String typeCode = relationKey.substring(0, idx);
        String externalId = relationKey.substring(idx + 1);
        Map<String, Long> bucket = relationResolved.get(typeCode);
        return bucket == null ? null : bucket.get(externalId);
    }

    /**
     * (userId, targetId, relationId) 三元组 key，用于 in-memory 现有关联索引。
     */
    private record TriKey(Long userId, Long targetId, Long relationId) {}

    /**
     * 执行单次 sync 写入（不做身份/payload 预检，调用方负责）。
     */
    private SyncResultResp doSyncOne(Long tenantId, UserRoleSyncReq req) {
        return doSyncOneInternal(tenantId, req, null, null, null, null, false, LocalDateTime.now(), null);
    }

    /**
     * 与 {@link #doSyncOne} 相同的版本/依赖语义，但允许 full-sync 阶段 C 传入已批量预解析的
     * {@code abstractUserId/roleId/relationId} 与已批量预加载的 {@code preExisting}，避免循环单条 select。
     *
     * @param preExistingResolved 调用方是否已完成 existing 的解析（true=即使 preExisting=null 也直接走 INSERT 分支，
     *                            不再查 DB；false=单条 sync 路径需在 BIND 分支内 fallback 查询）
     * @param ownedTargetIdsByBusinessKeyHash full-sync 预加载的当前 scope 归属 Map（businessKeyHash -> target_id），
     *                                       归属校验直接命中不查 DB；null 时单条 sync 回退 resolveTargetId
     */
    private SyncResultResp doSyncOneInternal(Long tenantId, UserRoleSyncReq req,
                                              Long preAbstractUserId, Long preRoleId, Long preRelationId,
                                              UserRole preExisting, boolean preExistingResolved,
                                              LocalDateTime now,
                                              Map<String, Long> ownedTargetIdsByBusinessKeyHash) {
        String businessKey = SyncKeyCodec.userRoleBusinessKey(
                req.subjectTypeCode(), req.subjectExternalId(),
                req.roleTypeCode(), req.roleExternalId(), req.relationKey());
        String scopeKey = SyncKeyCodec.userRoleScopeKey(
                req.sourceType(), req.roleTypeCode(), req.treeRootExternalId());
        String businessKeyHash = SyncKeyCodec.sha256Hex(businessKey);
        String scopeKeyHash = SyncKeyCodec.sha256Hex(scopeKey);
        String syncKey = req.sourceService() + "|" + ENTITY_KIND + "|" + businessKey;
        String syncKeyHash = SyncKeyCodec.sha256Hex(syncKey);

        // applyVersion (atomic compare)
        SyncMetadataDomainService.ApplyVersionResult vr = syncMetadataDomainService.applyVersion(
                tenantId, ENTITY_KIND, req.sourceService(),
                scopeKeyHash, scopeKey, businessKeyHash, businessKey,
                syncKey, syncKeyHash,
                req.syncVersion().occurredAt(), req.syncVersion().sequenceNo());
        if (vr == SyncMetadataDomainService.ApplyVersionResult.STALE) {
            return SyncResultBuilder.stale();
        }

        // resolve subject (abstract_user.id) — full-sync 复用阶段 B 结果，single-sync 走单条解析
        Long abstractUserId = preAbstractUserId != null
                ? preAbstractUserId
                : typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (abstractUserId == null) {
            return SyncResultBuilder.dependencyMissing("SUBJECT_NOT_FOUND");
        }
        // resolve target role (abstract_role.id)
        Long roleId = preRoleId != null
                ? preRoleId
                : typeResolutionService.resolveRoleId(tenantId, req.roleTypeCode(), req.roleExternalId(), null);
        if (roleId == null) {
            return SyncResultBuilder.dependencyMissing("ROLE_NOT_FOUND");
        }
        // resolve relation_id from relationKey "TYPE:externalId"
        Long relationId = preRelationId != null
                ? preRelationId
                : resolveRelationRoleId(tenantId, req.relationKey());
        if (relationId == null) {
            return SyncResultBuilder.dependencyMissing("RELATION_ROLE_NOT_FOUND");
        }

        if (OP_BIND.equals(req.operation())) {
            // full-sync 路径已批量预加载 preExisting；single-sync 路径需在此处 lookup 一次
            UserRole existingForUpsert = preExistingResolved
                    ? preExisting
                    : findUserRole(tenantId, abstractUserId, roleId, relationId);
            // 本地投影保护：access-service 所有权的已有行不得被外部 sync 改写（20045 整体回滚）
            localProjectionGuard.rejectIfLocalUserRole(existingForUpsert);
            // 归属校验：现有行必须由当前 sourceService+scope+businessKey 的 sync_metadata 指向
            // （owner=NULL 同时表示人工维护与外部同步，仅靠 owner 无法区分；外部不得接管他人关系）
            if (existingForUpsert != null
                    && !ownedByCurrentSource(tenantId, existingForUpsert, req.sourceService(),
                    scopeKeyHash, businessKeyHash, ownedTargetIdsByBusinessKeyHash)) {
                return SyncResultBuilder.nonRetryable("OWNERSHIP_CONFLICT");
            }
            UserRole upserted = upsertUserRoleWithExisting(tenantId, abstractUserId, roleId, relationId, req,
                    existingForUpsert, now);
            syncMetadataDomainService.markStatus(tenantId, ENTITY_KIND, req.sourceService(),
                    scopeKeyHash, businessKeyHash, STATUS_ACTIVE);
            // upserted 由 mapper.insert/update 内联返回（含主键），无需再查 DB
            Long targetId = upserted == null ? null : upserted.getId();
            syncMetadataDomainService.backfillTargetId(tenantId, ENTITY_KIND, req.sourceService(),
                    scopeKeyHash, businessKeyHash, targetId);
        } else { // UNBIND
            UserRole existing = preExistingResolved
                    ? preExisting
                    : findUserRole(tenantId, abstractUserId, roleId, relationId);
            // 本地投影保护：access-service 所有权的已有行不得被外部 sync 解绑（20045 整体回滚）
            localProjectionGuard.rejectIfLocalUserRole(existing);
            // 归属校验：人工维护或其他来源的关系不得被当前来源 UNBIND 软删
            if (existing != null
                    && !ownedByCurrentSource(tenantId, existing, req.sourceService(),
                    scopeKeyHash, businessKeyHash, ownedTargetIdsByBusinessKeyHash)) {
                return SyncResultBuilder.nonRetryable("OWNERSHIP_CONFLICT");
            }
            if (existing != null) {
                userRoleMapper.softDeleteBatch(tenantId, List.of(existing.getId()), now);
            }
            syncMetadataDomainService.markStatus(tenantId, ENTITY_KIND, req.sourceService(),
                    scopeKeyHash, businessKeyHash, STATUS_UNBOUND);
        }
        return SyncResultBuilder.applied();
    }

    private Long resolveRelationRoleId(Long tenantId, String relationKey) {
        if (relationKey == null || relationKey.isBlank()) return null;
        int idx = relationKey.indexOf(':');
        if (idx <= 0 || idx == relationKey.length() - 1) return null;
        String typeCode = relationKey.substring(0, idx);
        String externalId = relationKey.substring(idx + 1);
        return typeResolutionService.resolveRoleId(tenantId, typeCode, externalId, null);
    }

    // upsertUserRoleWithExisting 由 doSyncOneInternal 直接调用

    /**
     * 现有行归属校验：目标行必须由当前 {@code sourceService + scopeKey + businessKey} 的
     * sync_metadata 指向（target_id 匹配）。owner=NULL 同时表示人工维护与外部同步，
     * 仅靠 owner_service_code 无法区分；未匹配视为人工维护或其他来源所有，外部同步不得接管。
     * full-sync 传入预加载 Map 直接命中（N+1 防护），single-sync 走单条 resolveTargetId。
     */
    private boolean ownedByCurrentSource(Long tenantId, UserRole row, String sourceService,
                                         String scopeKeyHash, String businessKeyHash,
                                         Map<String, Long> ownedTargetIdsByBusinessKeyHash) {
        if (ownedTargetIdsByBusinessKeyHash != null) {
            Long targetId = ownedTargetIdsByBusinessKeyHash.get(businessKeyHash);
            return targetId != null && targetId.equals(row.getId());
        }
        return syncMetadataDomainService.resolveTargetId(
                        tenantId, ENTITY_KIND, sourceService, scopeKeyHash, businessKeyHash)
                .map(id -> id.equals(row.getId()))
                .orElse(false);
    }

    /**
     * relationKey 的角色类型必须为调用方自有类型（格式 {@code TYPE:externalId}；
     * 保留类型 ORG/POSITION 由 guard 拒绝，防止外部 sync 借 relationKey 改写本地投影语义）。
     */
    private void rejectReservedRelationType(String relationKey) {
        if (relationKey == null || relationKey.isBlank()) {
            return;
        }
        int idx = relationKey.indexOf(':');
        if (idx > 0) {
            localProjectionGuard.rejectReservedRoleType(relationKey.substring(0, idx));
        }
    }

    /**
     * upsert user_role；接受调用方已加载的 {@code existing}（可为 null 表示需新建）。
     * 外部业务服务同步写入的行所有权保持 NULL（owner 由本地投影独占，见 LocalProjectionOwner）。
     *
     * @return 写入或已更新的 UserRole 实例
     */
    private UserRole upsertUserRoleWithExisting(Long tenantId, Long userId, Long roleId, Long relationId,
                                                 UserRoleSyncReq req, UserRole existing, LocalDateTime now) {
        if (existing == null) {
            UserRole ur = new UserRole();
            ur.setTenantId(tenantId);
            ur.setAbstractUserId(userId);
            ur.setTargetType(ResourceTypeCode.ROLE);
            ur.setTargetId(roleId);
            ur.setRelationId(relationId);
            ur.setValidFrom(req.validFrom());
            ur.setValidTo(req.validTo());
            ur.setCreatedAt(now);
            ur.setUpdatedAt(now);
            ur.setDeleteFlag(0L);
            userRoleMapper.insert(ur);
            return ur;
        }
        existing.setValidFrom(req.validFrom());
        existing.setValidTo(req.validTo());
        existing.setUpdatedAt(now);
        userRoleMapper.update(existing);
        return existing;
    }

    private UserRole findUserRole(Long tenantId, Long userId, Long roleId, Long relationId) {
        QueryWrapper qw = QueryWrapper.create()
                .where(UserRoleTableDef.USER_ROLE.TENANT_ID.eq(tenantId))
                .and(UserRoleTableDef.USER_ROLE.ABSTRACT_USER_ID.eq(userId))
                .and(UserRoleTableDef.USER_ROLE.TARGET_TYPE.eq(ResourceTypeCode.ROLE))
                .and(UserRoleTableDef.USER_ROLE.TARGET_ID.eq(roleId))
                .and(UserRoleTableDef.USER_ROLE.RELATION_ID.eq(relationId))
                .and(UserRoleTableDef.USER_ROLE.DELETE_FLAG.eq(0L));
        return userRoleMapper.selectOneByQuery(qw);
    }
}
