package cn.ac.fage.accessmesh.access.resource.service.impl;

import cn.ac.fage.accessmesh.access.audit.aop.OperationLog;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.resource.entity.PermissionDependencyDeclaration;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceDependency;
import cn.ac.fage.accessmesh.access.resource.entity.ServiceManifestSync;
import cn.ac.fage.accessmesh.access.resource.mapper.PermissionDependencyDeclarationMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceDependencyMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ServiceConfigMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ServiceManifestSyncMapper;
import cn.ac.fage.accessmesh.access.resource.service.PermissionManifestAppService;
import cn.ac.fage.accessmesh.access.resource.service.domain.DependencyCompiler;
import cn.ac.fage.accessmesh.access.resource.service.domain.PermissionManifestNormalizer;
import cn.ac.fage.accessmesh.access.sync.SyncKeyCodecUtil;
import cn.ac.fage.accessmesh.access.sync.SyncResultBuilder;
import cn.ac.fage.accessmesh.access.sync.guard.LocalProjectionOwner;
import cn.ac.fage.accessmesh.access.type.service.domain.OperationPermissionDomainService;
import cn.ac.fage.accessmesh.access.type.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.type.service.domain.ResourceTypeOwnershipGuard;
import cn.ac.fage.accessmesh.access.type.service.domain.TypeDefinitionDomainService;
import cn.ac.fage.accessmesh.common.exception.SystemException;
import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq.ResourceKey;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
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
    private final DependencyCompiler compiler;
    private final PermissionDependencyDeclarationMapper declarationMapper;
    private final ServiceManifestSyncMapper stateMapper;
    private final ResourceDependencyMapper edgeMapper;
    private final ResourceEntityMapper resourceMapper;
    private final ServiceConfigMapper serviceMapper;
    private final TypeDefinitionDomainService types;
    private final OperationPermissionDomainService operations;
    private final ResourceTypeOwnershipGuard ownership;
    private final TreeWriteLockSupport locks;

    public PermissionManifestAppServiceImpl(PermissionManifestNormalizer normalizer, DependencyCompiler compiler,
            PermissionDependencyDeclarationMapper declarationMapper, ServiceManifestSyncMapper stateMapper,
            ResourceDependencyMapper edgeMapper, ResourceEntityMapper resourceMapper, ServiceConfigMapper serviceMapper,
            TypeDefinitionDomainService types, OperationPermissionDomainService operations,
            ResourceTypeOwnershipGuard ownership, TreeWriteLockSupport locks) {
        this.normalizer = normalizer;
        this.compiler = compiler;
        this.declarationMapper = declarationMapper;
        this.stateMapper = stateMapper;
        this.edgeMapper = edgeMapper;
        this.resourceMapper = resourceMapper;
        this.serviceMapper = serviceMapper;
        this.types = types;
        this.operations = operations;
        this.ownership = ownership;
        this.locks = locks;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "PERMISSION", action = "PERMISSION_MANIFEST_FULL_SYNC", targetType = "service_manifest_sync",
            targetId = "", summary = "'publish permission manifest'")
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
            result = compile(tenantId, service, normalized.declarations(), oldByKey);
        }
        LocalDateTime now = LocalDateTime.now();
        List<PermissionDependencyDeclaration> rows = result.declarations().stream()
                .map(r -> toRow(tenantId, service, r, now)).toList();
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
        if (!unchanged) replaceCompiled(tenantId, service, result.edges(), now);
        edgeMapper.refreshCompiledDescriptions(tenantId, service, now);

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

    private DependencyCompiler.Result compile(Long tenantId, String service, List<DependencyCompiler.Declaration> declarations,
                                               Map<String, PermissionDependencyDeclaration> old) {
        Map<String, DependencyCompiler.TypeInfo> typeInfo = new HashMap<>();
        Map<Integer, String> codes = new HashMap<>();
        for (var type : types.selectByTenantAndTypeKey(tenantId, "resource_type")) {
            var owner = ownership.parseOwnership(type.getExtra());
            typeInfo.put(type.getTypeCode(), new DependencyCompiler.TypeInfo(type.getTypeValue(),
                    ResourceTypeOwnershipGuard.MODE_SYNC.equals(owner.managedMode()) ? owner.syncSourceService() : null));
            codes.put(type.getTypeValue(), type.getTypeCode());
        }
        Set<ResourceKey> requested = new HashSet<>();
        declarations.forEach(d -> { requested.add(d.source()); requested.add(d.target()); });
        Set<Integer> values = requested.stream().map(k -> typeInfo.get(k.resourceTypeCode()))
                .filter(Objects::nonNull).map(DependencyCompiler.TypeInfo::value).collect(Collectors.toSet());
        Map<ResourceKey, Long> resources = new HashMap<>();
        List<ResourceKey> requestedList = new ArrayList<>(requested);
        for (int offset = 0; offset < requestedList.size(); offset += SQL_BATCH_SIZE) {
            var batch = requestedList.subList(offset, Math.min(offset + SQL_BATCH_SIZE, requestedList.size()));
            Set<Integer> batchTypes = batch.stream().map(k -> typeInfo.get(k.resourceTypeCode()))
                    .filter(Objects::nonNull).map(DependencyCompiler.TypeInfo::value).collect(Collectors.toSet());
            if (batchTypes.isEmpty()) continue;
            var resourceRows = resourceMapper.selectByTypesAndCodesAndCodeTypes(tenantId, batchTypes,
                    batch.stream().map(ResourceKey::resourceCode).collect(Collectors.toSet()),
                    batch.stream().map(ResourceKey::codeType).collect(Collectors.toSet()));
            for (var row : resourceRows) {
                ResourceKey key = new ResourceKey(codes.get(row.getResourceType()), row.getCode(), row.getCodeType());
                if (requested.contains(key)) resources.put(key, row.getId());
            }
        }
        List<Integer> typeValues = new ArrayList<>(values);
        List<OperationPermission> operationRows = new ArrayList<>();
        for (int offset = 0; offset < typeValues.size(); offset += SQL_BATCH_SIZE) {
            operationRows.addAll(operations.selectByTenantAndResourceTypes(tenantId,
                    new HashSet<>(typeValues.subList(offset, Math.min(offset + SQL_BATCH_SIZE, typeValues.size())))));
        }
        List<DependencyCompiler.Edge> retained = edgeMapper.selectByTenantId(tenantId).stream()
                .filter(e -> e.getDeclarationId() != null && !service.equals(e.getOwnerServiceCode()))
                .map(e -> new DependencyCompiler.Edge(e.getResourceEntityId(), e.getDependsOnResourceEntityId(),
                        e.getSourceOperationBits(), e.getRequiredOperationBits())).toList();
        Set<String> stable = declarations.stream().filter(d -> {
            var row = old.get(d.businessKey());
            return row != null && "RESOLVED".equals(row.getCompileStatus()) && semanticHash(d).equals(row.getSemanticHash());
        }).map(DependencyCompiler.Declaration::businessKey).collect(Collectors.toSet());
        return compiler.compile(service, declarations, resources, typeInfo,
                operationRows, retained, stable);
    }

    private record EdgeKey(Long source, Long target, Long trigger) {}
    private void replaceCompiled(Long tenantId, String service, List<DependencyCompiler.Edge> edges, LocalDateTime now) {
        Map<EdgeKey, Long> diagnosticIds = new HashMap<>();
        for (var row : declarationMapper.selectScope(tenantId, service)) {
            if ("RESOLVED".equals(row.getCompileStatus())) diagnosticIds.putIfAbsent(new EdgeKey(
                    row.getSourceResourceId(), row.getTargetResourceId(), row.getSourceOperationBits()), row.getId());
        }
        edgeMapper.removeCompiledScope(tenantId, service, now);
        List<ResourceDependency> compiled = new ArrayList<>();
        for (var edge : edges) {
            var row = new ResourceDependency();
            row.setTenantId(tenantId);
            row.setResourceEntityId(edge.sourceId());
            row.setDependsOnResourceEntityId(edge.targetId());
            row.setSourceOperationBits(edge.sourceOperationBits());
            row.setRequiredOperationBits(edge.requiredOperationBits());
            row.setDeclarationId(diagnosticIds.get(new EdgeKey(edge.sourceId(), edge.targetId(), edge.sourceOperationBits())));
            row.setOwnerServiceCode(service);
            row.setMaintainSource("MANIFEST");
            row.setAutoGrant(false); // 071 退役旧字段时删除；当前只标记编译图，不激活旧边。
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            row.setDeleteFlag(0L);
            compiled.add(row);
        }
        for (int offset = 0; offset < compiled.size(); offset += SQL_BATCH_SIZE) {
            var batch = compiled.subList(offset, Math.min(offset + SQL_BATCH_SIZE, compiled.size()));
            if (edgeMapper.insertBatch(batch) != batch.size()) throw failure("compiled edge write count mismatch");
        }
    }

    private PermissionDependencyDeclaration toRow(Long tenantId, String service, DependencyCompiler.Resolution resolution, LocalDateTime now) {
        var d = resolution.declaration();
        var row = new PermissionDependencyDeclaration();
        row.setTenantId(tenantId);
        row.setSourceService(service);
        row.setDeclarationKey(d.declarationKey());
        row.setBusinessKey(d.businessKey());
        row.setBusinessKeyHash(SyncKeyCodecUtil.sha256Hex(d.businessKey()));
        row.setDeclarationPayload(normalizer.declarationJson(d));
        row.setSemanticHash(semanticHash(d));
        row.setCompileStatus(resolution.reason() == null ? "RESOLVED" : "REJECTED");
        row.setRejectReason(resolution.reason());
        if (resolution.edge() != null) {
            row.setSourceResourceId(resolution.edge().sourceId());
            row.setTargetResourceId(resolution.edge().targetId());
            row.setSourceOperationBits(resolution.edge().sourceOperationBits());
            row.setRequiredOperationBits(resolution.edge().requiredOperationBits());
        }
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        return row;
    }
    private String semanticHash(DependencyCompiler.Declaration d) {
        return normalizer.declarationSemanticHash(d);
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
