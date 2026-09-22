package cn.ac.fage.accessmesh.access.role.service.impl;

import cn.ac.fage.accessmesh.perm.common.util.BusinessKeyUtil;
import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import cn.ac.fage.accessmesh.access.audit.aop.OperationLog;
import cn.ac.fage.accessmesh.access.sync.dto.UserRoleFullSyncReq;
import cn.ac.fage.accessmesh.access.sync.dto.UserRoleSyncItem;
import cn.ac.fage.accessmesh.access.sync.dto.UserRoleSyncReq;
import cn.ac.fage.accessmesh.access.sync.guard.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.sync.metadata.SyncMetadata;
import cn.ac.fage.accessmesh.access.role.entity.UserRole;
import cn.ac.fage.accessmesh.access.type.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.role.mapper.UserRoleMapper;
import cn.ac.fage.accessmesh.access.role.service.UserRoleSyncAppService;
import cn.ac.fage.accessmesh.access.sync.guard.LocalProjectionGuard;
import cn.ac.fage.accessmesh.access.sync.metadata.SyncMetadataDomainService;
import cn.ac.fage.accessmesh.access.engine.core.SubjectDomainService;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.sync.SyncAuthVerifier;
import cn.ac.fage.accessmesh.access.sync.SyncResultBuilder;
import cn.ac.fage.accessmesh.access.sync.guard.SyncTypeGuard;
import cn.ac.fage.accessmesh.access.sync.guard.SyncTypeGuard.SyncTypes;
import cn.ac.fage.accessmesh.access.sync.SyncKeyCodecUtil;
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

import cn.ac.fage.accessmesh.access.role.entity.table.UserRoleTableDef;

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

    private final TreeWriteLockSupport treeWriteLockSupport;
    private final SyncMetadataDomainService syncMetadataDomainService;
    private final TypeResolutionService typeResolutionService;
    private final UserRoleMapper userRoleMapper;
    private final LocalProjectionGuard localProjectionGuard;
    private final SyncTypeGuard syncTypeGuard;
    // T-PERM-064/T-PERM-075：sync 通道角色互斥授予守卫（原始持有候选解析 + 冲突检测）
    private final SubjectDomainService subjectDomainService;
    private final cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConflictDomainService permissionConflictDomainService;

    public UserRoleSyncAppServiceImpl(SyncMetadataDomainService syncMetadataDomainService,
                                       TypeResolutionService typeResolutionService,
                                       UserRoleMapper userRoleMapper,
                                       LocalProjectionGuard localProjectionGuard,
                                       SyncTypeGuard syncTypeGuard,
                                       SubjectDomainService subjectDomainService,
                                       cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConflictDomainService permissionConflictDomainService,
                                       TreeWriteLockSupport treeWriteLockSupport) {
        this.treeWriteLockSupport = treeWriteLockSupport;
        this.syncMetadataDomainService = syncMetadataDomainService;
        this.typeResolutionService = typeResolutionService;
        this.userRoleMapper = userRoleMapper;
        this.localProjectionGuard = localProjectionGuard;
        this.syncTypeGuard = syncTypeGuard;
        this.subjectDomainService = subjectDomainService;
        this.permissionConflictDomainService = permissionConflictDomainService;
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
        treeWriteLockSupport.lockTreeWrites(tenantId, TreeWriteLockSupport.TreeLockTarget.ABSTRACT_ROLE);
        return applySingleSync(tenantId, req);
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

        String scopeKey = SyncKeyCodecUtil.userRoleScopeKey(
                req.scope().sourceType(), req.scope().roleTypeCode(), req.scope().treeRootExternalId());
        String scopeKeyHash = SyncKeyCodecUtil.sha256Hex(scopeKey);

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
            BusinessKeyUtil.RelationKeyRef rk = BusinessKeyUtil.parseRelationKey(item.relationKey());
            if (rk != null) {
                relationExternalIdsByType.computeIfAbsent(rk.typeCode(), k -> new HashSet<>())
                        .add(rk.externalId());
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

        // 在批量读取前串行化同步写入，锁延续到事务完成，避免旧预加载事实覆盖新版本。
        treeWriteLockSupport.lockTreeWrites(tenantId, TreeWriteLockSupport.TreeLockTarget.ABSTRACT_ROLE);

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
        Map<String, SyncMetadata> metadataByBusinessKeyHash = new HashMap<>();
        Map<String, Long> ownedTargetIdsByBusinessKeyHash = new HashMap<>();
        for (SyncMetadata md : scopeMetadata) {
            metadataByBusinessKeyHash.put(md.getBusinessKeyHash(), md);
            if (md.getTargetId() != null) {
                ownedTargetIdsByBusinessKeyHash.put(md.getBusinessKeyHash(), md.getTargetId());
            }
        }

        // ---- 阶段 C：逐项 applyVersion + upsert ----
        int applied = 0, stale = 0, failed = 0, deactivated = 0;
        List<SyncResultResp.ItemResult> itemResults = new ArrayList<>(req.items().size());
        Set<String> seenBusinessKeyHashes = new HashSet<>();
        // grok 复评 P1 修复（T-PERM-075 窗口化）+ 外评 R2 修正（2026-09-22）：请求级批内
        // 工作状态（多重集）＝循环前原始持有候选预载，随批内成功应用增删补偿——新增/改期
        // 补入新窗口、被替换绑定移除旧窗口一份提供方（旧实现只增不减，改期后的旧窗口残留
        // 候选使后续合法改期被 ROLE_MUTEX_CONFLICT 误拒；同值窗口可由多绑定/组展开重复提供，
        // 多重集按提供方扣减才不误删仍生效的同值窗口）。守卫逐 item 消费，无循环单查 N+1
        Map<Long, List<SubjectDomainService.RawHolding>> workingHoldingsByUser = new HashMap<>();
        if (!candidateUserIds.isEmpty()) {
            subjectDomainService.batchResolveRawHoldingsMultiset(tenantId, candidateUserIds)
                    .forEach((uid, windows) -> workingHoldingsByUser.put(uid, new ArrayList<>(windows)));
        }
        // claude 外评 P3-1：互斥规则批内一次预载——新守卫口径下稳态全量重放逐 item 触发，
        // 逐 item DB 直查会在 ABSTRACT_ROLE 树写锁持有期放大语句数（旧口径幂等重放零规则查询）
        List<cn.ac.fage.accessmesh.access.rule.entity.PermissionConflictRule> mutexRules =
                permissionConflictDomainService.loadRoleMutexRulesFresh(tenantId);
        LocalDateTime now = LocalDateTime.now();

        for (UserRoleSyncItem item : req.items()) {
            String businessKey = SyncKeyCodecUtil.userRoleBusinessKey(
                    item.subjectTypeCode(), item.subjectExternalId(),
                    item.roleTypeCode(), item.roleExternalId(), item.relationKey());
            // 同批重复 businessKey：写入前拒绝（existingByTriKey 为循环前一次性加载，
            // 重复项二次 INSERT 会触发 uk_user_role 唯一约束整批回滚）
            if (!seenBusinessKeyHashes.add(SyncKeyCodecUtil.sha256Hex(businessKey))) {
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

            SyncResultResp r = applyItemSync(tenantId, oneReq,
                    new FullSyncPreload(true, preUserId, preRoleId, preRelId, preExisting,
                            ownedTargetIdsByBusinessKeyHash, workingHoldingsByUser, metadataByBusinessKeyHash,
                            mutexRules),
                    now);
            if (r.applied()) {
                applied++;
                // backfillTargetId 已在 applyItemSync 内完成（upserted.getId()），无需额外查 DB。
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
        BusinessKeyUtil.RelationKeyRef ref = BusinessKeyUtil.parseRelationKey(relationKey);
        if (ref == null) return null;
        Map<String, Long> bucket = relationResolved.get(ref.typeCode());
        return bucket == null ? null : bucket.get(ref.externalId());
    }

    /**
     * (userId, targetId, relationId) 三元组 key，用于 in-memory 现有关联索引。
     */
    private record TriKey(Long userId, Long targetId, Long relationId) {}

    /**
     * full-sync 阶段 B/C 预解析与批级预载参数组（T-PERM-079 收敛，原 doSyncOneInternal 的
     * 预解析散参与批级 Map 收拢）。{@code NONE} 为 single-sync 无预载形态——逐项回退
     * 单条主体/角色/关系解析、单条 existing 查询与单条归属/元数据查询。
     *
     * @param resolved      调用方是否已批量解析主体/目标角色/关系角色与 existing（true 时
     *                      null 字段表示确认缺失，不再逐项查询）
     * @param abstractUserId 预解析的 abstract_user.id
     * @param roleId        预解析的目标 abstract_role.id
     * @param relationId    预解析的 relationKey 角色 id
     * @param existing      预加载的现有 UserRole 行
     * @param ownedTargetIds full-sync 预加载的当前 scope 归属 Map（businessKeyHash -> target_id），
     *                       归属校验直接命中不查 DB
     * @param workingHoldings full-sync 批内工作状态（userId -> 原始持有窗口多重集；循环前
     *                      预载，随批内成功应用增删补偿——新增/改期补新窗口、被替换绑定移除
     *                      旧窗口一份提供方，外评 R2 修正；single-sync 传 null，守卫内单用户直查）
     * @param metadata      full-sync 预加载的 scope 元数据 Map（businessKeyHash -> SyncMetadata）
     * @param mutexRules    full-sync 预加载的 ROLE_MUTEX 规则集（claude 外评 P3-1，循环前 DB 直查一次；
     *                      single-sync 传 null，守卫内两参重载直查）
     */
    private record FullSyncPreload(boolean resolved, Long abstractUserId, Long roleId, Long relationId,
                                   UserRole existing, Map<String, Long> ownedTargetIds,
                                   Map<Long, List<SubjectDomainService.RawHolding>> workingHoldings,
                                   Map<String, SyncMetadata> metadata,
                                   List<cn.ac.fage.accessmesh.access.rule.entity.PermissionConflictRule> mutexRules) {
        static final FullSyncPreload NONE =
                new FullSyncPreload(false, null, null, null, null, null, null, null, null);
    }

    /** single-sync 路径入口：无预载，逐项单条解析/查询（版本/依赖语义与 full-sync 同源）。 */
    private SyncResultResp applySingleSync(Long tenantId, UserRoleSyncReq req) {
        return applyItemSync(tenantId, req, FullSyncPreload.NONE, LocalDateTime.now());
    }

    /**
     * 与 {@link #applySingleSync} 相同的版本/依赖语义，full-sync 阶段 C 经
     * {@link FullSyncPreload} 传入批量预解析与预载，避免循环单条 select。
     */
    private SyncResultResp applyItemSync(Long tenantId, UserRoleSyncReq req, FullSyncPreload preload,
                                         LocalDateTime now) {
        String businessKey = SyncKeyCodecUtil.userRoleBusinessKey(
                req.subjectTypeCode(), req.subjectExternalId(),
                req.roleTypeCode(), req.roleExternalId(), req.relationKey());
        String scopeKey = SyncKeyCodecUtil.userRoleScopeKey(
                req.sourceType(), req.roleTypeCode(), req.treeRootExternalId());
        String businessKeyHash = SyncKeyCodecUtil.sha256Hex(businessKey);
        String scopeKeyHash = SyncKeyCodecUtil.sha256Hex(scopeKey);
        String syncKey = SyncKeyCodecUtil.syncKey(req.sourceService(), ENTITY_KIND, businessKey);
        String syncKeyHash = SyncKeyCodecUtil.sha256Hex(syncKey);

        // 旧事件先按 STALE 返回；预检不消费版本，最终仍由数据库原子比较兜底。
        SyncMetadata selfMeta = (preload.metadata() != null ? preload.metadata()
                : syncMetadataDomainService.mapByBusinessKeyHash(tenantId, ENTITY_KIND,
                        req.sourceService(), scopeKeyHash, Set.of(businessKeyHash))).get(businessKeyHash);
        if (selfMeta != null && !syncMetadataDomainService.isNewerVersion(selfMeta,
                req.syncVersion().occurredAt(), req.syncVersion().sequenceNo())) {
            return SyncResultBuilder.stale();
        }

        // resolve subject (abstract_user.id) — full-sync 复用阶段 B 结果，single-sync 走单条解析
        Long abstractUserId = preload.resolved()
                ? preload.abstractUserId()
                : typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (abstractUserId == null) {
            return SyncResultBuilder.dependencyMissing("SUBJECT_NOT_FOUND");
        }
        // resolve target role (abstract_role.id)
        Long roleId = preload.resolved()
                ? preload.roleId()
                : typeResolutionService.resolveRoleId(tenantId, req.roleTypeCode(), req.roleExternalId(), null);
        if (roleId == null) {
            return SyncResultBuilder.dependencyMissing("ROLE_NOT_FOUND");
        }
        // resolve relation_id from relationKey "TYPE:externalId"
        Long relationId = preload.resolved()
                ? preload.relationId()
                : resolveRelationRoleId(tenantId, req.relationKey());
        if (relationId == null) {
            return SyncResultBuilder.dependencyMissing("RELATION_ROLE_NOT_FOUND");
        }

        UserRole existingForUpsert = preload.resolved()
                ? preload.existing() : findUserRole(tenantId, abstractUserId, roleId, relationId);
        localProjectionGuard.rejectIfLocalUserRole(existingForUpsert);
        if (existingForUpsert != null
                && !ownedByCurrentSource(tenantId, existingForUpsert, req.sourceService(),
                scopeKeyHash, businessKeyHash, preload.ownedTargetIds())) {
            return SyncResultBuilder.nonRetryable("OWNERSHIP_CONFLICT");
        }
        boolean introducesUnexpiredHolding = OP_BIND.equals(req.operation())
                && bindIntroducesUnexpiredHolding(req, now);
        if (introducesUnexpiredHolding
                && hitsRoleMutexOnBind(tenantId, abstractUserId, roleId, req, preload)) {
            return SyncResultBuilder.nonRetryable("ROLE_MUTEX_CONFLICT");
        }

        // 预期业务拒绝均已完成，版本与事实由入口事务共同提交/回滚。
        SyncMetadataDomainService.ApplyVersionResult vr = syncMetadataDomainService.applyVersion(
                tenantId, ENTITY_KIND, req.sourceService(),
                scopeKeyHash, scopeKey, businessKeyHash, businessKey,
                syncKey, syncKeyHash,
                req.syncVersion().occurredAt(), req.syncVersion().sequenceNo());
        if (vr == SyncMetadataDomainService.ApplyVersionResult.STALE) {
            return SyncResultBuilder.stale();
        }

        if (OP_BIND.equals(req.operation())) {
            // 外评 R2：upsert 原地改写 existing 实体窗口字段，先取旧窗口供批内工作状态扣减
            SubjectDomainService.RawHolding replacedWindow = existingForUpsert == null ? null
                    : new SubjectDomainService.RawHolding(existingForUpsert.getTargetId(),
                            existingForUpsert.getValidFrom(), existingForUpsert.getValidTo());
            UserRole upserted = upsertUserRoleWithExisting(tenantId, abstractUserId, roleId, relationId, req,
                    existingForUpsert, now);
            if (preload.workingHoldings() != null) {
                List<SubjectDomainService.RawHolding> working = preload.workingHoldings()
                        .computeIfAbsent(abstractUserId, k -> new ArrayList<>());
                if (replacedWindow != null) {
                    // List.remove 精确扣减一份提供方（多重集语义）：被替换绑定的旧窗口不再
                    // 参与后续 item 候选——含改成已过期窗口的替换（旧未过期窗口同样要离开）
                    working.remove(replacedWindow);
                }
                if (introducesUnexpiredHolding) {
                    working.add(new SubjectDomainService.RawHolding(roleId, req.validFrom(), req.validTo()));
                }
            }
            syncMetadataDomainService.markStatus(tenantId, ENTITY_KIND, req.sourceService(),
                    scopeKeyHash, businessKeyHash, STATUS_ACTIVE);
            // upserted 由 mapper.insert/update 内联返回（含主键），无需再查 DB
            Long targetId = upserted == null ? null : upserted.getId();
            syncMetadataDomainService.backfillTargetId(tenantId, ENTITY_KIND, req.sourceService(),
                    scopeKeyHash, businessKeyHash, targetId);
        } else { // UNBIND
            UserRole existing = existingForUpsert;
            if (existing != null) {
                userRoleMapper.softDeleteBatch(tenantId, List.of(existing.getId()), now);
            }
            syncMetadataDomainService.markStatus(tenantId, ENTITY_KIND, req.sourceService(),
                    scopeKeyHash, businessKeyHash, STATUS_UNBOUND);
        }
        return SyncResultBuilder.applied();
    }

    /**
     * T-PERM-064/T-PERM-075：BIND 是否将引入「未过期持有」（触发互斥守卫的谓词）。
     * <p>
     * U002-1 写时拒绝口径（用户拍板 2026-09-22）：仅按 valid_to 未过期判定
     * (valid_to >= now OR NULL)，不看 valid_from——未来窗口同入候选，改写后与既有
     * 持有做重叠判定；已过期行永不生效不触发。既有行不做「改写前有效」比较：
     * 改写后窗口未过期即检查（纯幂等重放因持有侧无重叠天然通过；改期引入新窗口
     * 引入新冲突面必须重查——旧「幂等改期不触发」口径随窗口重叠判定自然消解，
     * registry 2026-09-22 修订登记）。
     * </p>
     */
    private boolean bindIntroducesUnexpiredHolding(UserRoleSyncReq req, LocalDateTime now) {
        return req.validTo() == null || !req.validTo().isBefore(now);
    }

    /**
     * T-PERM-064/T-PERM-075：授予后状态命中 ROLE_MUTEX 对检测。
     * <p>
     * postState = 当前绑定状态（full-sync=批内工作状态：原始持有候选多重集随批内成功应用
     * 增删补偿，外评 R2；single-sync=单用户 DB 新鲜读，单请求单 item 无快照陈旧问题）
     * ∪ 本目标（不再过滤启用——禁用可逆，U002-2 绑定写时堵死），
     * 与管理面 {@code UserManageAppServiceImpl#rejectRoleMutexOnAssign} 同口径；
     * 规则读取复用 {@code findAssignMutexConflicts} DB 直查（新规则即刻生效）。
     * </p>
     */
    private boolean hitsRoleMutexOnBind(Long tenantId, Long userId, Long roleId,
                                        UserRoleSyncReq req, FullSyncPreload preload) {
        Set<SubjectDomainService.RawHolding> postState = preload.workingHoldings() != null
                ? new HashSet<>(preload.workingHoldings().getOrDefault(userId, List.of()))
                : new HashSet<>(subjectDomainService.resolveRawHoldings(tenantId, userId));
        postState.add(new SubjectDomainService.RawHolding(roleId, req.validFrom(), req.validTo()));
        // full-sync 传批内预载规则（N+1 防护）；single-sync（NONE preload → null）走两参重载直查
        return !permissionConflictDomainService
            .findAssignMutexConflicts(tenantId, Map.of(userId, postState), preload.mutexRules()).isEmpty();
    }

    private Long resolveRelationRoleId(Long tenantId, String relationKey) {
        BusinessKeyUtil.RelationKeyRef ref = BusinessKeyUtil.parseRelationKey(relationKey);
        if (ref == null) return null;
        return typeResolutionService.resolveRoleId(tenantId, ref.typeCode(), ref.externalId(), null);
    }

    // upsertUserRoleWithExisting 由 applyItemSync 直接调用

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
        // 不用 BusinessKeyUtil.parseRelationKey：守卫语义只看类型段是否存在（idx > 0），
        // "ORG:" 这类空 id 段的畸形键也要拒绝保留类型，parseRelationKey 则将其归为 null
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
