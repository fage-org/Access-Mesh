package cn.ac.fage.accessmesh.access.resource.service.impl;

import cn.ac.fage.accessmesh.access.resource.service.domain.DependencyCompilationDomainService;
import cn.ac.fage.accessmesh.access.sync.PublicationGeneration;
import cn.ac.fage.accessmesh.access.sync.ResourcePublicationNormalizer;
import cn.ac.fage.accessmesh.access.sync.metadata.ResourcePublicationDomainService;
import cn.ac.fage.accessmesh.access.sync.metadata.ResourcePublicationPolicy;
import cn.ac.fage.accessmesh.access.sync.metadata.SyncVersionOrder;
import cn.ac.fage.accessmesh.common.exception.SystemException;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import cn.ac.fage.accessmesh.access.audit.aop.OperationLog;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntityFullSyncReq;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntitySyncItem;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntitySyncReq;
import cn.ac.fage.accessmesh.access.resource.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.access.resource.dto.req.ResourceResolveRequest;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.sync.metadata.SyncMetadata;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.sync.mapper.SyncMetadataMapper;
import cn.ac.fage.accessmesh.access.resource.service.ResourceEntitySyncAppService;
import cn.ac.fage.accessmesh.access.sync.guard.LocalProjectionGuard;
import cn.ac.fage.accessmesh.access.resource.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.access.type.service.domain.ResourceTypeOwnershipGuard;
import cn.ac.fage.accessmesh.access.sync.metadata.SyncMetadataDomainService;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.sync.SyncAuthVerifier;
import cn.ac.fage.accessmesh.access.sync.SyncResultBuilder;
import cn.ac.fage.accessmesh.access.sync.SyncKeyCodecUtil;
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
 * {@link ResourceEntitySyncAppService} 实现。
 */
@Service
public class ResourceEntitySyncAppServiceImpl implements ResourceEntitySyncAppService {

    private static final int SQL_BATCH_SIZE = 500;
    private static final String ENTITY_KIND = "RESOURCE_ENTITY";
    private static final String DEFAULT_CODE_TYPE = "default";
    private static final String OP_UPSERT = "UPSERT";
    private static final String OP_DISABLE = "DISABLE";
    private static final String OP_DELETE = "DELETE";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String STATUS_DELETED = "DELETED";
    private static final String MAINTAIN_SOURCE_SYNC = "SYNC";

    private final DependencyCompilationDomainService compilation;
    private final SyncMetadataDomainService syncMetadataDomainService;
    private final SyncMetadataMapper syncMetadataMapper;
    private final TypeResolutionService typeResolutionService;
    private final ResourceEntityMapper resourceEntityMapper;
    private final ObjectMapper objectMapper;
    private final LocalProjectionGuard localProjectionGuard;
    private final ResourceTypeOwnershipGuard resourceTypeOwnershipGuard;
    private final ResourceEntityDomainService resourceEntityDomainService;
    private final TreeWriteLockSupport treeWriteLockSupport;
    private final ResourcePublicationDomainService publications;
    private final ResourcePublicationNormalizer publicationNormalizer;

    public ResourceEntitySyncAppServiceImpl(SyncMetadataDomainService syncMetadataDomainService,
                                             SyncMetadataMapper syncMetadataMapper,
                                             TypeResolutionService typeResolutionService,
                                             ResourceEntityMapper resourceEntityMapper,
                                             ObjectMapper objectMapper,
                                             LocalProjectionGuard localProjectionGuard,
                                             ResourceTypeOwnershipGuard resourceTypeOwnershipGuard,
                                             ResourceEntityDomainService resourceEntityDomainService,
                                             TreeWriteLockSupport treeWriteLockSupport,
                                             ResourcePublicationDomainService publications,
                                      DependencyCompilationDomainService compilation) {
        this.compilation = compilation;
        this.syncMetadataDomainService = syncMetadataDomainService;
        this.syncMetadataMapper = syncMetadataMapper;
        this.typeResolutionService = typeResolutionService;
        this.resourceEntityMapper = resourceEntityMapper;
        this.objectMapper = objectMapper;
        this.localProjectionGuard = localProjectionGuard;
        this.resourceTypeOwnershipGuard = resourceTypeOwnershipGuard;
        this.resourceEntityDomainService = resourceEntityDomainService;
        this.treeWriteLockSupport = treeWriteLockSupport;
        this.publications = publications;
        this.publicationNormalizer = new ResourcePublicationNormalizer(objectMapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "RESOURCE_ENTITY_SYNC", targetType = "resource_entity",
        targetId = "#req.resourceCode()",
        summary = "'sync resource_entity from ' + #req.sourceService()")
    public SyncResultResp sync(Long tenantId, ResourceEntitySyncReq req, HttpServletRequest httpRequest) {
        if (!SyncAuthVerifier.verify(req.sourceService(), httpRequest)) {
            return SyncResultBuilder.securityDenied("SOURCE_SERVICE_MISMATCH");
        }
        localProjectionGuard.rejectInternalSourceService(req.sourceService());
        // T-PERM-044 评审 P1：资源同步 UPDATE 分支写 parent，与 moveResource 共持
        // (resource_entity, 租户) 树写锁——move×sync / sync×sync 交叉窗口收口
        treeWriteLockSupport.lockTreeWrites(tenantId, TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
        // T-PERM-052 类型级所有权门禁（取代 syncTypes.resourceTypeCodes 白名单维度，2026-09-05 定案）：
        // 目标类型必须声明 extra.managedMode=SYNC 且 syncSourceService==调用服务身份，且调用服务
        // 在 service_config 注册并启用（评审 P1 补强：服务停用/注销即四通道一起断）；类型不存在
        // fail-closed 一并拒绝。codex 复评 P1：门禁移到树锁之后——与 type-definition 声明变更/删除
        // 的行数守卫（同持本锁）互斥，堵「门禁放行→类型翻转来源→插入」交错破坏单一所有权
        if (!resourceTypeOwnershipGuard.isSyncEntranceAllowed(
                tenantId, req.resourceTypeCode(), req.sourceService())) {
            return SyncResultBuilder.securityDenied("RESOURCE_TYPE_OWNERSHIP_DENIED");
        }
        if (!OP_UPSERT.equals(req.operation())
                && !OP_DISABLE.equals(req.operation())
                && !OP_DELETE.equals(req.operation())) {
            return SyncResultBuilder.nonRetryable("INVALID_OPERATION");
        }
        Long generation;
        try { generation = req.publicationGeneration() == null ? null : PublicationGeneration.parse(req.publicationGeneration()); }
        catch (IllegalArgumentException e) { return SyncResultBuilder.nonRetryable("PUBLICATION_GENERATION_INVALID"); }
        String scope = SyncKeyCodecUtil.resourceEntityScopeKey(req.resourceTypeCode());
        var state = publications.read(tenantId, req.sourceService(), scope);
        SyncMetadata metadata = null;
        String itemHash = null;
        if (generation != null) {
            req = publicationNormalizer.snapshot(req);
            itemHash = publicationNormalizer.hash(req);
            String keyHash = SyncKeyCodecUtil.sha256Hex(publicationNormalizer.businessKey(req));
            metadata = syncMetadataDomainService.mapByBusinessKeyHash(tenantId, ENTITY_KIND, req.sourceService(),
                    SyncKeyCodecUtil.sha256Hex(scope), Set.of(keyHash)).get(keyHash);
        }
        var decision = ResourcePublicationPolicy.single(state, metadata, generation, itemHash);
        if (decision != ResourcePublicationPolicy.Decision.APPLY) return publicationRejection(decision);
        SyncResultResp result = doSyncOne(tenantId, req, metadata, generation, itemHash);
        if (generation != null && result.applied()) publications.acceptSingle(tenantId, req.sourceService(), scope, generation);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "RESOURCE_ENTITY_FULL_SYNC", targetType = "resource_entity",
        targetId = "",
        summary = "'full sync resource_entity from ' + #req.scope().sourceService()")
    public SyncResultResp fullSync(Long tenantId, ResourceEntityFullSyncReq req, HttpServletRequest httpRequest) {
        if (!SyncAuthVerifier.verify(req.scope().sourceService(), httpRequest)) {
            return SyncResultBuilder.fullSyncRejected(
                    SyncResultBuilder.RETRY_SECURITY_DENIED, "SOURCE_SERVICE_MISMATCH",
                    req.items().size(),
                    List.of(new SyncResultResp.ItemResult("*", false, false,
                            SyncResultBuilder.RETRY_SECURITY_DENIED, "SOURCE_SERVICE_MISMATCH")));
        }
        localProjectionGuard.rejectInternalSourceService(req.scope().sourceService());

        String scopeKey = SyncKeyCodecUtil.resourceEntityScopeKey(req.scope().resourceTypeCode());
        String scopeKeyHash = SyncKeyCodecUtil.sha256Hex(scopeKey);

        // T-PERM-044 评审 P1：全量同步批量写 parent，与 moveResource/sync 共持树写锁（对齐角色域）
        treeWriteLockSupport.lockTreeWrites(tenantId, TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);

        // T-PERM-052 类型级所有权门禁（同单条口径；codex 复评 P1：置于树锁之后，与类型声明
        // 变更/删除的行数守卫互斥）
        if (!resourceTypeOwnershipGuard.isSyncEntranceAllowed(
                tenantId, req.scope().resourceTypeCode(), req.scope().sourceService())) {
            return SyncResultBuilder.fullSyncRejected(
                    SyncResultBuilder.RETRY_SECURITY_DENIED, "RESOURCE_TYPE_OWNERSHIP_DENIED",
                    req.items().size(),
                    List.of(new SyncResultResp.ItemResult("*", false, false,
                            SyncResultBuilder.RETRY_SECURITY_DENIED, "RESOURCE_TYPE_OWNERSHIP_DENIED")));
        }

        long generation;
        String fullHash;
        try {
            if (req.publicationGeneration() == null) throw new IllegalArgumentException("PUBLICATION_GENERATION_REQUIRED");
            generation = PublicationGeneration.parse(req.publicationGeneration());
            if (req.items() == null || req.items().stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("INVALID_FULL_ITEMS");
            }
            req = publicationNormalizer.snapshot(req);
            fullHash = publicationNormalizer.fullHash(req);
        } catch (IllegalArgumentException e) {
            return fullPublicationRejected(SyncResultBuilder.RETRY_NON_RETRYABLE, e.getMessage(), req.items() == null ? 0 : req.items().size());
        }
        var publicationState = publications.read(tenantId, req.scope().sourceService(), scopeKey);
        var decision = ResourcePublicationPolicy.full(publicationState, generation, fullHash);
        if (decision != ResourcePublicationPolicy.Decision.APPLY) return fullPublicationRejected(decision, req);
        List<SyncMetadata> existingScope = syncMetadataDomainService.listScopeForFullSync(
                tenantId, ENTITY_KIND, req.scope().sourceService(), scopeKeyHash);
        Map<String, SyncMetadata> metadataByKey = new HashMap<>();
        existingScope.forEach(md -> metadataByKey.put(md.getBusinessKeyHash(), md));

        // ---- 阶段 A：收集 (resourceCode, codeType) 与 parent (typeCode, code, codeType) 集合 ----
        List<ResourceResolveRequest> parentRequests = new ArrayList<>();
        for (ResourceEntitySyncItem item : req.items()) {
            String codeType = (item.codeType() == null || item.codeType().isBlank()) ? DEFAULT_CODE_TYPE : item.codeType();
            // T-PERM-068：父字段组激活条件=parentResourceCode 非空；typeCode 缺省回填 scope 类型；
            // 跨类型项不参与批量父解析（循环体按类型拒绝，无需解析）
            if (item.parentResourceCode() != null && !item.parentResourceCode().isBlank()) {
                String parentTypeCode = effectiveParentTypeCode(
                        item.parentResourceTypeCode(), req.scope().resourceTypeCode());
                if (!parentTypeCode.equals(req.scope().resourceTypeCode())) {
                    continue;
                }
                String pct = (item.parentCodeType() == null || item.parentCodeType().isBlank())
                        ? DEFAULT_CODE_TYPE : item.parentCodeType();
                parentRequests.add(new ResourceResolveRequest(
                        parentTypeCode, item.parentResourceCode(), pct, null));
            }
        }

        // ---- 阶段 B：批量解析 resourceTypeValue + 现有 entities + parent ids ----
        Integer resourceTypeValue = typeResolutionService.resolveTypeValue(
                tenantId, "resource_type", req.scope().resourceTypeCode());
        // resourceTypeValue 为 null 时不直接拒绝整批，沿用单条 doSyncOne 路径让每条 item 独立返回 dependencyMissing。
        Map<CodeKey, ResourceEntity> existingByCodeKey = new HashMap<>();
        if (resourceTypeValue != null) {
            for (int offset = 0; offset < req.items().size(); offset += SQL_BATCH_SIZE) {
                var batch = req.items().subList(offset, Math.min(offset + SQL_BATCH_SIZE, req.items().size()));
                Set<String> batchCodes = new HashSet<>();
                Set<String> batchCodeTypes = new HashSet<>();
                for (var item : batch) {
                    batchCodes.add(item.resourceCode());
                    batchCodeTypes.add(item.codeType() == null || item.codeType().isBlank() ? DEFAULT_CODE_TYPE : item.codeType());
                }
                for (ResourceEntity re : resourceEntityMapper.selectByTypeAndCodesAndCodeTypes(
                        tenantId, resourceTypeValue, batchCodes, batchCodeTypes)) {
                    existingByCodeKey.put(new CodeKey(re.getCode(), re.getCodeType()), re);
                }
            }
        }
        Map<ResourceResolveKey, Long> parentResolved = new HashMap<>();
        List<ResourceResolveRequest> uniqueParents = parentRequests.stream().distinct().toList();
        for (int offset = 0; offset < uniqueParents.size(); offset += SQL_BATCH_SIZE) {
            parentResolved.putAll(typeResolutionService.batchResolveResourceIds(tenantId,
                    uniqueParents.subList(offset, Math.min(offset + SQL_BATCH_SIZE, uniqueParents.size()))));
        }

        // ---- 阶段 B.6：全量父子关系内存图（写入前逐项判环用；无父项批次跳过加载，
        // 对齐角色 fullSync 先例——循环体内无数据库调用，N+1 禁令）----
        Map<Long, Long> parentGraphById = null;
        if (!parentRequests.isEmpty()) {
            parentGraphById = new HashMap<>();
            for (ResourceEntity re : resourceEntityMapper.selectAllValid(tenantId)) {
                parentGraphById.put(re.getId(), re.getParentId());
            }
        }

        // ---- 阶段 C：逐项 applyVersion + upsert ----
        int applied = 0, stale = 0, failed = 0, deactivated = 0;
        List<SyncResultResp.ItemResult> itemResults = new ArrayList<>(req.items().size());
        Set<String> seenBusinessKeyHashes = new HashSet<>();
        LocalDateTime now = LocalDateTime.now();

        for (ResourceEntitySyncItem item : req.items()) {
            String codeType = (item.codeType() == null || item.codeType().isBlank()) ? DEFAULT_CODE_TYPE : item.codeType();
            String businessKey = SyncKeyCodecUtil.resourceEntityBusinessKey(
                    req.scope().resourceTypeCode(), item.resourceCode(), codeType);
            seenBusinessKeyHashes.add(SyncKeyCodecUtil.sha256Hex(businessKey));

            // 通过 doFullSyncOne 复用 single-sync 的所有版本/依赖语义，但 existing 与 parentId 命中阶段 B 缓存。
            ResourceEntity existing = existingByCodeKey.get(new CodeKey(item.resourceCode(), codeType));
            // T-PERM-068：父字段组激活条件=parentResourceCode 非空；typeCode 缺省回填 scope 类型
            boolean parentRequested = item.parentResourceCode() != null && !item.parentResourceCode().isBlank();
            String itemParentTypeCode = parentRequested
                    ? effectiveParentTypeCode(item.parentResourceTypeCode(), req.scope().resourceTypeCode())
                    : null;
            // T-PERM-068（Q-007 定案①，2026-09-17）：跨类型父边收紧——显式异类型 item 级 NON_RETRYABLE，
            // 先于 applyVersion 不推进同步版本（码比对足够：type_definition code↔value 双射，码不等即类型值不等）
            if (parentRequested && !itemParentTypeCode.equals(req.scope().resourceTypeCode())) {
                failed++;
                itemResults.add(new SyncResultResp.ItemResult(businessKey, false, false,
                        SyncResultBuilder.RETRY_NON_RETRYABLE,
                        "PARENT_TYPE_MISMATCH: " + itemParentTypeCode + ":" + item.parentResourceCode()));
                continue;
            }
            Long preResolvedParentId = null;
            if (parentRequested) {
                String pct = (item.parentCodeType() == null || item.parentCodeType().isBlank())
                        ? DEFAULT_CODE_TYPE : item.parentCodeType();
                preResolvedParentId = parentResolved.get(new ResourceResolveKey(
                        itemParentTypeCode, item.parentResourceCode(), pct, null));
            }

            ResourceEntitySyncReq oneReq = publicationNormalizer.asSingle(req, item);
            SyncMetadata itemMetadata = metadataByKey.get(SyncKeyCodecUtil.sha256Hex(businessKey));
            String itemHash = publicationNormalizer.hash(oneReq);
            // T-PERM-044 评审 P1：环路防护（写入前逐项判定，先于版本写入——拒绝不推进同步版本）：
            // 当前生效图 = 库内既有关系 + 本事务已应用项的边（应用成功后镜像更新）；新建实体
            // 无既有子树天然无环；仅拒绝真正闭合环的本项，指向环的前缀安全项放行
            if (parentRequested && existing != null && parentGraphById != null
                    && wouldCreateCycle(parentGraphById, existing.getId(), preResolvedParentId)) {
                failed++;
                itemResults.add(new SyncResultResp.ItemResult(businessKey, false, false,
                        SyncResultBuilder.RETRY_NON_RETRYABLE,
                        "RESOURCE_PARENT_INVALID: " + itemParentTypeCode + ":" + item.parentResourceCode()));
                continue;
            }
            SyncResultResp r = doSyncOneInternal(tenantId, oneReq, resourceTypeValue, existing,
                    true, parentRequested, preResolvedParentId, true, now, itemMetadata, generation, itemHash);
            if (r.applied()) {
                applied++;
                // doSyncOneInternal 在新建分支会把 insert 后的 ResourceEntity 注入 cache 不在此处再查 DB（避免 N+1）。
                // 内存图镜像写入语义：更新分支成功后把边改为本项 parent，供后续项判环
                if (parentGraphById != null && existing != null) {
                    parentGraphById.put(existing.getId(), parentRequested ? preResolvedParentId : null);
                }
            } else if (r.stale()) stale++;
            else failed++;
            itemResults.add(new SyncResultResp.ItemResult(businessKey, r.applied(), r.stale(),
                    r.retryClass(), r.reason()));
        }

        // 差异校准（批量软删 targetIds）。清理范围按 sync_metadata(entityKind=RESOURCE_ENTITY,
        // sourceService, scopeKey) 界定（总册 §19.1）：本地投影不写 sync_metadata
        // （§4.2），天然不在清理集合内——类型级保留取消后仍不触及本地投影行。

        Set<Long> deactivateTargetIds = new HashSet<>();
        List<String> missingHashes = new ArrayList<>();
        for (SyncMetadata md : existingScope) {
            if (seenBusinessKeyHashes.contains(md.getBusinessKeyHash())) continue;
            if (STATUS_DELETED.equals(md.getTargetStatus())) continue;
            missingHashes.add(md.getBusinessKeyHash());
            if (md.getTargetId() != null) {
                deactivateTargetIds.add(md.getTargetId());
            }
            deactivated++;
        }
        for (int offset = 0; offset < missingHashes.size(); offset += SQL_BATCH_SIZE) {
            publications.markDeleted(tenantId, req.scope().sourceService(), scopeKey,
                    missingHashes.subList(offset, Math.min(offset + SQL_BATCH_SIZE, missingHashes.size())));
        }
        List<Long> deleteIds = new ArrayList<>(deactivateTargetIds);
        for (int offset = 0; offset < deleteIds.size(); offset += SQL_BATCH_SIZE) {
            resourceEntityMapper.softDeleteBatch(tenantId, deleteIds.subList(offset, Math.min(offset + SQL_BATCH_SIZE, deleteIds.size())), now);
        }
        compilation.resourcesDeleted(tenantId, deleteIds, now);
        publications.acceptFull(tenantId, req.scope().sourceService(), scopeKey, generation, fullHash, failed > 0);
        return SyncResultBuilder.fullSync(applied, stale, failed, deactivated, itemResults);
    }

    /**
     * code + codeType 二维 key（用于 in-memory 现有实体索引）。
     */
    private record CodeKey(String code, String codeType) {}

    /**
     * T-PERM-068（Q-007 定案③）：父类型缺省回填——{@code parentResourceTypeCode} 缺省/空白时
     * 按 item（单条）/scope（full-sync）自身类型解析父（契约 §19.1/§19.2 原意，对齐角色域
     * full-sync {@code effectiveParentTypeCode} 先例）；回填后与自身类型比对即得跨类型判定。
     */
    private static String effectiveParentTypeCode(String parentResourceTypeCode, String itemTypeCode) {
        return parentResourceTypeCode == null || parentResourceTypeCode.isBlank()
                ? itemTypeCode
                : parentResourceTypeCode;
    }

    private SyncResultResp doSyncOne(Long tenantId, ResourceEntitySyncReq req, SyncMetadata metadata, Long generation, String hash) {
        return doSyncOneInternal(tenantId, req, null, null, false, false, null, false,
                LocalDateTime.now(), metadata, generation, hash);
    }

    /**
     * parent 环路判定（single-sync 路径，与 moveResource 同款）：parentId 为实体自身或其子孙
     * 时成环。parentId=null（解挂/未携带）天然无环。
     */
    private boolean isCyclicParent(Long tenantId, Long entityId, Long parentId) {
        if (parentId == null) {
            return false;
        }
        if (parentId.equals(entityId)) {
            return true;
        }
        return resourceEntityDomainService.batchGetDescendantIds(tenantId, Set.of(entityId))
                .getOrDefault(entityId, List.of()).contains(parentId);
    }

    /**
     * parent 环路判定（full-sync 内存图版，对齐角色同步 wouldCreateCycle 先例）：parentId 为
     * 实体自身，或实体在 parentId 的祖先链上即成环。visited 防既有环上溯不终止（防御）；
     * 祖先不在图内（软删/缺失）视为到顶。
     */
    private static boolean wouldCreateCycle(Map<Long, Long> parentById, Long entityId, Long parentId) {
        if (parentId == null) {
            return false;
        }
        if (entityId.equals(parentId)) {
            return true;
        }
        Set<Long> visited = new HashSet<>();
        Long cur = parentId;
        while (cur != null) {
            if (cur.equals(entityId)) {
                return true;
            }
            if (!visited.add(cur)) {
                return true;
            }
            cur = parentById.get(cur);
        }
        return false;
    }

    /**
     * 复用单条 sync 的版本/依赖语义；支持 full-sync 阶段 C 传入已批量预加载的 existing 与 parentId。
     *
     * @param preResolvedTypeValue 预解析的 resourceTypeValue（null 表示让本方法自己解析）
     * @param preLoadedExisting    预加载的 ResourceEntity（null 表示未预加载或确认不存在，由
     *                             {@code preExistingResolved} 区分）
     * @param preExistingResolved  调用方是否已完成 existing 解析（true=即使 preLoadedExisting=null 也直接走
     *                             INSERT/不存在分支，不再查 DB；false=本方法 fallback 单条 select）
     * @param parentRequested      调用方是否声明了 parent
     * @param preResolvedParentId  预解析的 parentId（仅当 parentRequested=true 时使用）
     * @param cyclePreChecked      调用方已完成判环（full-sync 循环体经内存图判定后传入，
     *                             跳过本方法内的 DB 子孙查询判定，避免逐项递归 CTE 的 N+1）
     */
    private SyncResultResp doSyncOneInternal(Long tenantId, ResourceEntitySyncReq req,
                                             Integer preResolvedTypeValue,
                                             ResourceEntity preLoadedExisting,
                                             boolean preExistingResolved,
                                             boolean parentRequested, Long preResolvedParentId,
                                             boolean cyclePreChecked,
                                             LocalDateTime now, SyncMetadata metadata, Long generation, String publicationHash) {
        String codeType = (req.codeType() == null || req.codeType().isBlank()) ? DEFAULT_CODE_TYPE : req.codeType();
        String businessKey = SyncKeyCodecUtil.resourceEntityBusinessKey(
                req.resourceTypeCode(), req.resourceCode(), codeType);
        String scopeKey = SyncKeyCodecUtil.resourceEntityScopeKey(req.resourceTypeCode());
        String businessKeyHash = SyncKeyCodecUtil.sha256Hex(businessKey);
        String scopeKeyHash = SyncKeyCodecUtil.sha256Hex(scopeKey);
        String syncKey = SyncKeyCodecUtil.syncKey(req.sourceService(), ENTITY_KIND, businessKey);
        String syncKeyHash = SyncKeyCodecUtil.sha256Hex(syncKey);

        if (preExistingResolved && metadata != null && java.util.Objects.equals(metadata.getLastPublicationGeneration(), generation)) {
            return java.util.Objects.equals(metadata.getLastPublicationHash(), publicationHash)
                    ? SyncResultBuilder.stale() : SyncResultBuilder.nonRetryable("PUBLICATION_GENERATION_CONFLICT");
        }
        Integer resourceTypeValue = preResolvedTypeValue != null
                ? preResolvedTypeValue
                : typeResolutionService.resolveTypeValue(tenantId, "resource_type", req.resourceTypeCode());
        if (resourceTypeValue == null) {
            return SyncResultBuilder.dependencyMissing("RESOURCE_TYPE_NOT_FOUND");
        }

        ResourceEntity existing = preExistingResolved
                ? preLoadedExisting
                : resourceEntityMapper.selectByTypeCodeAndCodeType(tenantId, resourceTypeValue, req.resourceCode(), codeType);

        // T-PERM-052：本地投影行防线（owner=access-service 拒绝）已收编进入口类型门禁——
        // 事实链路类型（USER/ORG/MENU/ROLE/ADMIN_FILE/TYPE_DEFINITION/CONDITION，API 同款种子声明 T-PERM-069）
        // 声明 SYNC+access-service，外部来源在入口即被拒，不可达本分支；
        // existing 为 null 的 INSERT 分支由 uk_resource_entity 唯一约束 fail-closed 兜底。

        // resolve parent (optional) —— 先于 applyVersion：环路/类型拒绝不推进同步版本，
        // 上游修正后同版本重试不被判 STALE（对齐角色同步先例 T-PERM-022 评审收口）
        Long parentId = null;
        // T-PERM-068：父字段组仅 UPSERT 生效（2026-09-18 用户拍板，claude 外评 P2 处置——DISABLE/DELETE
        // 忽略父字段，删/停不被父资源存否绑架，对齐角色域 OP_UPSERT 守卫先例）；激活条件=parentResourceCode
        // 非空（parentResourceTypeCode 单独传不激活——无父编码即无边）；typeCode 缺省回填自身类型
        // （Q-007 定案③，对齐角色域 full-sync effectiveParentTypeCode 先例与契约 §19.1/§19.2 原意）
        boolean callerHasParent = OP_UPSERT.equals(req.operation())
                && req.parentResourceCode() != null && !req.parentResourceCode().isBlank();
        if (callerHasParent) {
            String parentTypeCode = effectiveParentTypeCode(req.parentResourceTypeCode(), req.resourceTypeCode());
            // T-PERM-068（Q-007 定案①，2026-09-17）：跨类型父边收紧——显式异类型 item 级 NON_RETRYABLE，
            // 先于父解析不查库（码比对足够：type_definition code↔value 双射，码不等即类型值不等）
            if (!parentTypeCode.equals(req.resourceTypeCode())) {
                return SyncResultBuilder.nonRetryable(
                        "PARENT_TYPE_MISMATCH: " + parentTypeCode + ":" + req.parentResourceCode());
            }
            String parentCodeType = (req.parentCodeType() == null || req.parentCodeType().isBlank())
                    ? DEFAULT_CODE_TYPE : req.parentCodeType();
            // full-sync 路径已批量预解析；single-sync 路径走单条解析。
            parentId = parentRequested
                    ? preResolvedParentId
                    : typeResolutionService.resolveResourceId(tenantId,
                            parentTypeCode, req.parentResourceCode(), parentCodeType, null);
            if (parentId == null) {
                return SyncResultBuilder.dependencyMissing("PARENT_RESOURCE_NOT_FOUND");
            }
        }

        // T-PERM-044 评审 P1：parent 环路防护（与 moveResource 同款判定：目标父为自身或其子孙拒绝），
        // 先于 applyVersion；新建分支无既有子树天然无环。full-sync 路径经内存图预判后跳过
        //（cyclePreChecked），本处 DB 子孙查询判定仅服务 single-sync
        if (!cyclePreChecked && OP_UPSERT.equals(req.operation()) && existing != null
                && isCyclicParent(tenantId, existing.getId(), parentId)) {
            return SyncResultBuilder.nonRetryable(
                    "RESOURCE_PARENT_INVALID: " + effectiveParentTypeCode(
                            req.parentResourceTypeCode(), req.resourceTypeCode())
                            + ":" + req.parentResourceCode());
        }

        // DELETE 有子拒绝（2026-09-18 用户拍板，外评处置）：存在有效后代（含脏数据跨类型子——CTE
        // 不按类型过滤，保守阻塞防误删）→ DEPENDENCY_MISSING 可重试，子删除后同版本重发自愈
        // （拒绝先于 applyVersion 不推进版本，对齐依赖缺失先例）；full-sync 载荷恒 UPSERT，
        // 本判定仅服务单条 sync DELETE，无循环 N+1
        if (OP_DELETE.equals(req.operation()) && existing != null
                && !resourceEntityDomainService.batchGetDescendantIds(tenantId, Set.of(existing.getId()))
                        .getOrDefault(existing.getId(), List.of()).isEmpty()) {
            return SyncResultBuilder.dependencyMissing("CHILDREN_EXIST");
        }

        if (OP_DISABLE.equals(req.operation()) && existing == null) {
            // DISABLE 只走单条入口；保留已消费旧版本的 STALE 响应，同时不为新失败写版本。
            SyncMetadata existingMetadata = syncMetadataDomainService.mapByBusinessKeyHash(tenantId, ENTITY_KIND,
                    req.sourceService(), scopeKeyHash, Set.of(businessKeyHash)).get(businessKeyHash);
            if (existingMetadata != null && !syncMetadataDomainService.isNewerVersion(existingMetadata,
                    req.syncVersion().occurredAt(), req.syncVersion().sequenceNo())) {
                return SyncResultBuilder.stale();
            }
            return SyncResultBuilder.dependencyMissing("RESOURCE_NOT_FOUND");
        }

        if (preExistingResolved && metadata != null) {
            int order = SyncVersionOrder.compareIncoming(metadata, req.syncVersion());
            if (order < 0) return SyncResultBuilder.nonRetryable("SYNC_VERSION_CONFLICT");
            if (order == 0) {
                if (!publicationNormalizer.matchesExisting(req, existing, parentId)) return SyncResultBuilder.nonRetryable("SYNC_VERSION_CONFLICT");
                publications.stampItem(tenantId, req.sourceService(), scopeKey, businessKeyHash, generation, publicationHash);
                return SyncResultBuilder.stale();
            }
        }

        SyncMetadataDomainService.ApplyVersionResult vr = syncMetadataDomainService.applyVersion(
                tenantId, ENTITY_KIND, req.sourceService(),
                scopeKeyHash, scopeKey, businessKeyHash, businessKey,
                syncKey, syncKeyHash,
                req.syncVersion().occurredAt(), req.syncVersion().sequenceNo());
        if (vr == SyncMetadataDomainService.ApplyVersionResult.STALE) {
            return SyncResultBuilder.stale();
        }

        if (OP_UPSERT.equals(req.operation())) {
            if (existing == null) {
                ResourceEntity re = new ResourceEntity();
                re.setTenantId(tenantId);
                re.setParentId(parentId);
                re.setResourceType(resourceTypeValue);
                re.setCode(req.resourceCode());
                re.setCodeType(codeType);
                re.setName(req.name() == null ? req.resourceCode() : req.name());
                re.setPath(req.path());
                re.setStatus(req.status() == null ? 1 : req.status());
                re.setMaintainSource(MAINTAIN_SOURCE_SYNC);
                // 外部业务服务同步的行所有权保持 NULL（owner 由本地投影独占，见 LocalProjectionOwner）
                re.setExtra(serializeExtra(req.extra()));
                re.setCreatedAt(now);
                re.setUpdatedAt(now);
                re.setDeleteFlag(0L);
                resourceEntityMapper.insert(re);
                existing = re;
            } else {
                if (parentId == null) {
                    // 解挂须显式写 null 列：flex update(entity) 忽略 null 字段（moveResource/投影
                    // upsertResource 同款 UpdateEntity 先例；grok 外评 P2——「全不传=解挂」契约语义
                    // 必须落库，否则 applied=true 且版本已推进、旧父边残留 fail-open、同版本重发被 STALE 挡）
                    ResourceEntity patch = com.mybatisflex.core.util.UpdateEntity.of(ResourceEntity.class);
                    patch.setId(existing.getId());
                    patch.setParentId(null);
                    if (req.name() != null) patch.setName(req.name());
                    if (req.path() != null) patch.setPath(req.path());
                    if (req.status() != null) patch.setStatus(req.status());
                    if (req.extra() != null) patch.setExtra(serializeExtra(req.extra()));
                    patch.setUpdatedAt(now);
                    resourceEntityMapper.update(patch);
                } else {
                    existing.setParentId(parentId);
                    if (req.name() != null) existing.setName(req.name());
                    if (req.path() != null) existing.setPath(req.path());
                    if (req.status() != null) existing.setStatus(req.status());
                    if (req.extra() != null) existing.setExtra(serializeExtra(req.extra()));
                    existing.setUpdatedAt(now);
                    resourceEntityMapper.update(existing);
                }
            }
            syncMetadataDomainService.markStatus(tenantId, ENTITY_KIND, req.sourceService(),
                    scopeKeyHash, businessKeyHash, STATUS_ACTIVE);
            syncMetadataDomainService.backfillTargetId(tenantId, ENTITY_KIND, req.sourceService(),
                    scopeKeyHash, businessKeyHash, existing.getId());
        } else if (OP_DISABLE.equals(req.operation())) {
            existing.setStatus(0);
            existing.setUpdatedAt(now);
            resourceEntityMapper.update(existing);
            syncMetadataDomainService.markStatus(tenantId, ENTITY_KIND, req.sourceService(),
                    scopeKeyHash, businessKeyHash, STATUS_DISABLED);
        } else { // DELETE
            if (existing != null) {
                resourceEntityMapper.softDeleteBatch(tenantId, List.of(existing.getId()), now);
                compilation.resourcesDeleted(tenantId, List.of(existing.getId()), now);
            }
            syncMetadataDomainService.markStatus(tenantId, ENTITY_KIND, req.sourceService(),
                    scopeKeyHash, businessKeyHash, STATUS_DELETED);
        }

        if (generation != null) publications.stampItem(tenantId, req.sourceService(), scopeKey, businessKeyHash, generation, publicationHash);
        return SyncResultBuilder.applied();
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
            throw new SystemException(AccessErrorCode.SYSTEM_INIT_FAILED.getCode(),
                    "serialize resource_entity extra failed", e);
        }
    }

    private SyncResultResp publicationRejection(ResourcePublicationPolicy.Decision decision) {
        return switch (decision) {
            case GENERATION_REQUIRED -> SyncResultBuilder.nonRetryable("PUBLICATION_GENERATION_REQUIRED");
            case CONFLICT -> SyncResultBuilder.nonRetryable("PUBLICATION_GENERATION_CONFLICT");
            case UNCHANGED -> new SyncResultResp(true, false, true,
                    SyncResultBuilder.RETRY_STALE_VERSION, "PUBLICATION_UNCHANGED");
            case STALE -> new SyncResultResp(true, false, true,
                    SyncResultBuilder.RETRY_STALE_VERSION, "PUBLICATION_GENERATION_STALE");
            // APPLY 正常路径不经本拒绝映射；显式列出保持枚举新增分支时的编译期保护
            case APPLY -> throw new IllegalStateException("publication rejection mapped from APPLY: " + decision);
        };
    }
    private SyncResultResp fullPublicationRejected(String category, String reason, int count) {
        return SyncResultBuilder.fullSyncRejected(category, reason, count,
                List.of(new SyncResultResp.ItemResult("*", false, false, category, reason)));
    }
    private SyncResultResp fullPublicationRejected(ResourcePublicationPolicy.Decision decision, ResourceEntityFullSyncReq req) {
        if (decision != ResourcePublicationPolicy.Decision.STALE) {
            return fullPublicationRejected(SyncResultBuilder.RETRY_NON_RETRYABLE, "PUBLICATION_GENERATION_CONFLICT", req.items().size());
        }
        List<SyncResultResp.ItemResult> items = req.items().stream().map(item -> new SyncResultResp.ItemResult(
                publicationNormalizer.businessKey(publicationNormalizer.asSingle(req, item)), false, true,
                SyncResultBuilder.RETRY_STALE_VERSION, "PUBLICATION_GENERATION_STALE")).toList();
        return new SyncResultResp(true, false, true, SyncResultBuilder.RETRY_STALE_VERSION, "PUBLICATION_GENERATION_STALE",
                new SyncResultResp.FullSyncDetail(0, items.size(), 0, 0, items));
    }

}
