package cn.ac.fage.accessmesh.access.resource.service.impl;

import cn.ac.fage.accessmesh.access.audit.aop.OperationLog;
import cn.ac.fage.accessmesh.access.grant.service.domain.AutoGrantMaterializationDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChange;
import cn.ac.fage.accessmesh.access.infrastructure.PermissionChangeContext;
import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.resource.entity.PermissionDependencyDeclaration;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceDependency;
import cn.ac.fage.accessmesh.access.resource.entity.ServiceManifestSync;
import cn.ac.fage.accessmesh.access.resource.mapper.PermissionDependencyDeclarationMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceDependencyMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ServiceConfigMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ServiceManifestSyncMapper;
import cn.ac.fage.accessmesh.access.resource.service.PermissionManifestAppService;
import cn.ac.fage.accessmesh.access.resource.service.domain.DependencyCompiler;
import cn.ac.fage.accessmesh.access.resource.service.domain.DependencyCompilationDomainService;
import cn.ac.fage.accessmesh.access.resource.service.domain.PermissionManifestNormalizer;
import cn.ac.fage.accessmesh.access.sync.SyncResultBuilder;
import cn.ac.fage.accessmesh.access.sync.guard.LocalProjectionOwner;
import cn.ac.fage.accessmesh.common.exception.SystemException;
import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 服务声明 FULL：身份门禁、共同锁、规范化、编译及状态与事实同事务提交。 */
@Service
public class PermissionManifestAppServiceImpl implements PermissionManifestAppService {
    private static final int SQL_BATCH_SIZE = 500;
    private final PermissionManifestNormalizer normalizer;
    private final DependencyCompilationDomainService compilation;
    private final PermissionDependencyDeclarationMapper declarationMapper;
    private final ServiceManifestSyncMapper stateMapper;
    private final ResourceDependencyMapper edgeMapper;
    private final ServiceConfigMapper serviceMapper;
    private final TreeWriteLockSupport locks;
    private final AutoGrantMaterializationDomainService autoGrantMaterializationDomainService;

    public PermissionManifestAppServiceImpl(PermissionManifestNormalizer normalizer, DependencyCompilationDomainService compilation,
            PermissionDependencyDeclarationMapper declarationMapper, ServiceManifestSyncMapper stateMapper,
            ResourceDependencyMapper edgeMapper, ServiceConfigMapper serviceMapper, TreeWriteLockSupport locks,
            AutoGrantMaterializationDomainService autoGrantMaterializationDomainService) {
        this.normalizer = normalizer;
        this.compilation = compilation;
        this.declarationMapper = declarationMapper;
        this.stateMapper = stateMapper;
        this.edgeMapper = edgeMapper;
        this.serviceMapper = serviceMapper;
        this.locks = locks;
        this.autoGrantMaterializationDomainService = autoGrantMaterializationDomainService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "PERMISSION_MANIFEST_FULL_SYNC", targetType = "service_manifest_sync",
            targetId = "", summary = "'publish permission manifest'")
    @PermissionChange
    public SyncResultResp fullSync(Long tenantId, PermissionManifestReq request) {
        var normalized = normalizer.normalize(request);
        String service = AccessRequestContext.getServiceCode();
        int count = normalized.declarations().size();
        if (service == null || !Objects.equals(tenantId, AccessRequestContext.getTenantId())
                || LocalProjectionOwner.isLocalOwner(service)) {
            return rejected(SyncResultBuilder.RETRY_SECURITY_DENIED, "SERVICE_IDENTITY_REQUIRED", count);
        }
        locks.lockTreeWrites(tenantId, TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
        var registered = serviceMapper.selectByTenantAndServiceCode(tenantId, service);
        if (registered == null || !Integer.valueOf(1).equals(registered.getStatus())) {
            return rejected(SyncResultBuilder.RETRY_SECURITY_DENIED, "SERVICE_NOT_ENABLED", count);
        }
        ServiceManifestSync previous = stateMapper.selectScope(tenantId, service);
        if (previous != null && normalized.generation() < previous.getPublicationGeneration()) {
            return stale(normalized.declarations());
        }
        if (previous != null && normalized.generation() == previous.getPublicationGeneration()
                && !normalized.payloadHash().equals(previous.getPayloadHash())) {
            return rejected(SyncResultBuilder.RETRY_NON_RETRYABLE, "PUBLICATION_GENERATION_CONFLICT", count);
        }
        List<PermissionDependencyDeclaration> old = declarationMapper.selectScope(tenantId, service);
        Map<String, PermissionDependencyDeclaration> oldByKey = old.stream()
                .collect(Collectors.toMap(PermissionDependencyDeclaration::getBusinessKey, row -> row));
        boolean unchanged = previous != null && "SUCCESS".equals(previous.getSyncStatus())
                && !Boolean.TRUE.equals(previous.getIsDirty()) && normalized.revision().equals(previous.getRevision())
                && normalized.semanticHash().equals(previous.getSemanticHash());
        DependencyCompiler.Result result;
        if (unchanged) {
            // 展示字段仍保存；四条件短路仅复用已成功编译的图。
            result = new DependencyCompiler.Result(normalized.declarations().stream().map(d -> {
                var row = oldByKey.get(d.businessKey());
                if (row == null || !"RESOLVED".equals(row.getCompileStatus())) {
                    throw failure("manifest SUCCESS state disagrees with declarations");
                }
                return new DependencyCompiler.Resolution(d, null, new DependencyCompiler.Edge(row.getSourceResourceId(),
                        row.getTargetResourceId(), row.getSourceOperationBits(), row.getRequiredOperationBits()));
            }).toList(), List.of());
        } else {
            result = compilation.compile(tenantId, service, normalized.declarations(), oldByKey);
        }
        LocalDateTime now = LocalDateTime.now();
        List<PermissionDependencyDeclaration> rows = result.declarations().stream()
                .map(r -> compilation.toRow(tenantId, service, r, now)).toList();
        Set<String> keep = rows.stream().map(PermissionDependencyDeclaration::getBusinessKeyHash).collect(Collectors.toSet());
        List<Long> missing = old.stream().filter(row -> !keep.contains(row.getBusinessKeyHash()))
                .map(PermissionDependencyDeclaration::getId).toList();
        int removed = 0;
        for (int offset = 0; offset < missing.size(); offset += SQL_BATCH_SIZE) {
            var batch = missing.subList(offset, Math.min(offset + SQL_BATCH_SIZE, missing.size()));
            int affected = declarationMapper.softDeleteIds(tenantId, service, batch, now);
            if (affected != batch.size()) throw failure("manifest declaration removal count mismatch");
            removed += affected;
        }
        for (int offset = 0; offset < rows.size(); offset += SQL_BATCH_SIZE) {
            var batch = rows.subList(offset, Math.min(offset + SQL_BATCH_SIZE, rows.size()));
            if (declarationMapper.saveAll(batch) != batch.size()) throw failure("manifest declaration write count mismatch");
        }
        if (!unchanged) {
            // 声明变化触发面（§7）：受影响实体 = 本服务旧编译边端点 ∪ 新编译边端点，
            // 图替换同事务完整重算受影响角色的 AUTO_DEP（T-PERM-072）
            Set<Long> affectedEntities = new HashSet<>();
            for (ResourceDependency edge : edgeMapper.selectByTenantId(tenantId)) {
                if (!service.equals(edge.getOwnerServiceCode())) continue;
                affectedEntities.add(edge.getResourceEntityId());
                affectedEntities.add(edge.getDependsOnResourceEntityId());
            }
            result.edges().forEach(edge -> {
                affectedEntities.add(edge.sourceId());
                affectedEntities.add(edge.targetId());
            });
            compilation.replaceGraphs(tenantId, Map.of(service, result.edges()), now);
            Set<Long> changedRoles = autoGrantMaterializationDomainService
                .recomputeByResourceEntities(tenantId, affectedEntities);
            PermissionChangeContext.markRoles(tenantId, changedRoles);
        }
        edgeMapper.refreshCompiledScopeDescriptions(tenantId, List.of(service), now);

        int failed = (int) result.declarations().stream().filter(r -> r.reason() != null).count();
        var state = new ServiceManifestSync();
        state.setTenantId(tenantId);
        state.setSourceService(service);
        state.setPublicationGeneration(normalized.generation());
        state.setRevision(normalized.revision());
        state.setPayloadHash(normalized.payloadHash());
        state.setSemanticHash(normalized.semanticHash());
        state.setSyncStatus(failed == 0 ? "SUCCESS" : failed == count ? "FAILED" : "PARTIAL");
        state.setIsDirty(false);
        state.setLastSyncedAt(now);
        if (stateMapper.save(state) != 1) throw failure("manifest publication state changed during write");
        List<SyncResultResp.ItemResult> items = result.declarations().stream().map(r -> new SyncResultResp.ItemResult(
                r.declaration().businessKey(), r.reason() == null && !unchanged, unchanged,
                unchanged ? SyncResultBuilder.RETRY_STALE_VERSION : retryClass(r.reason()),
                unchanged ? "MANIFEST_UNCHANGED" : r.reason())).toList();
        return SyncResultBuilder.fullSync(unchanged ? 0 : count - failed, unchanged ? count : 0, failed, removed, items);
    }

    private String retryClass(String reason) {
        if (reason == null) return null;
        return "RESOURCE_MISSING".equals(reason) || "TYPE_MISSING".equals(reason)
                ? SyncResultBuilder.RETRY_DEPENDENCY_MISSING : SyncResultBuilder.RETRY_NON_RETRYABLE;
    }
    private SyncResultResp rejected(String category, String reason, int count) {
        return SyncResultBuilder.fullSyncRejected(category, reason, count,
                List.of(new SyncResultResp.ItemResult("*", false, false, category, reason)));
    }
    private SyncResultResp stale(List<DependencyCompiler.Declaration> declarations) {
        String reason = "PUBLICATION_GENERATION_STALE";
        var items = declarations.stream().map(d -> new SyncResultResp.ItemResult(d.businessKey(), false, true,
                SyncResultBuilder.RETRY_STALE_VERSION, reason)).toList();
        var detail = new SyncResultResp.FullSyncDetail(0, declarations.size(), 0, 0, items);
        return new SyncResultResp(true, false, true, SyncResultBuilder.RETRY_STALE_VERSION, reason, detail);
    }
    private SystemException failure(String message) {
        return new SystemException(AccessErrorCode.SYSTEM_INIT_FAILED.getCode(), message);
    }
}
