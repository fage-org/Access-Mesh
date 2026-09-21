package cn.ac.fage.accessmesh.access.resource.service.domain;

import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.infrastructure.util.SqlBatches;
import cn.ac.fage.accessmesh.access.resource.entity.PermissionDependencyDeclaration;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceDependency;
import cn.ac.fage.accessmesh.access.resource.mapper.PermissionDependencyDeclarationMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceDependencyMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ServiceManifestSyncMapper;
import cn.ac.fage.accessmesh.access.sync.SyncKeyCodecUtil;
import cn.ac.fage.accessmesh.access.type.service.domain.OperationPermissionDomainService;
import cn.ac.fage.accessmesh.access.type.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.type.service.domain.ResourceTypeOwnershipGuard;
import cn.ac.fage.accessmesh.access.type.service.domain.TypeDefinitionDomainService;
import cn.ac.fage.accessmesh.common.exception.SystemException;
import cn.ac.fage.accessmesh.perm.common.dto.req.PermissionManifestReq.ResourceKey;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


/** 声明编译与图替换的复用领域逻辑；调用方持有资源树锁并负责事务。 */
@Service
public class DependencyCompilationDomainService {
    private final PermissionManifestNormalizer normalizer;
    private final DependencyCompiler compiler;
    private final PermissionDependencyDeclarationMapper declarationMapper;
    private final ServiceManifestSyncMapper stateMapper;
    private final ResourceDependencyMapper edgeMapper;
    private final ResourceEntityMapper resourceMapper;
    private final TypeDefinitionDomainService types;
    private final OperationPermissionDomainService operations;
    private final ResourceTypeOwnershipGuard ownership;

    public DependencyCompilationDomainService(PermissionManifestNormalizer normalizer, DependencyCompiler compiler,
            PermissionDependencyDeclarationMapper declarationMapper, ServiceManifestSyncMapper stateMapper,
            ResourceDependencyMapper edgeMapper, ResourceEntityMapper resourceMapper,
            TypeDefinitionDomainService types, OperationPermissionDomainService operations, ResourceTypeOwnershipGuard ownership) {
        this.normalizer = normalizer;
        this.compiler = compiler;
        this.declarationMapper = declarationMapper;
        this.stateMapper = stateMapper;
        this.edgeMapper = edgeMapper;
        this.resourceMapper = resourceMapper;
        this.types = types;
        this.operations = operations;
        this.ownership = ownership;
    }
    private record Input(Map<ResourceKey, Long> resources, Map<String, DependencyCompiler.TypeInfo> types,
                         List<OperationPermission> operations, List<ResourceDependency> existingEdges) {}

    public DependencyCompiler.Result compile(Long tenantId, String service, List<DependencyCompiler.Declaration> declarations,
                                              Map<String, PermissionDependencyDeclaration> old) {
        return compileInMemory(service, declarations, old, loadInputs(tenantId, declarations));
    }
    private Input loadInputs(Long tenantId, List<DependencyCompiler.Declaration> declarations) {
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
        SqlBatches.forEach(requestedList, batch -> {
            Set<Integer> batchTypes = batch.stream().map(k -> typeInfo.get(k.resourceTypeCode()))
                    .filter(Objects::nonNull).map(DependencyCompiler.TypeInfo::value).collect(Collectors.toSet());
            if (batchTypes.isEmpty()) return;
            var resourceRows = resourceMapper.selectByTypesAndCodesAndCodeTypes(tenantId, batchTypes,
                    batch.stream().map(ResourceKey::resourceCode).collect(Collectors.toSet()),
                    batch.stream().map(ResourceKey::codeType).collect(Collectors.toSet()));
            for (var row : resourceRows) {
                ResourceKey key = new ResourceKey(codes.get(row.getResourceType()), row.getCode(), row.getCodeType());
                if (requested.contains(key)) resources.put(key, row.getId());
            }
        });
        List<Integer> typeValues = new ArrayList<>(values);
        List<OperationPermission> operationRows = new ArrayList<>();
        SqlBatches.forEach(typeValues,
                batch -> operationRows.addAll(operations.selectByTenantAndResourceTypes(tenantId, new HashSet<>(batch))));
        return new Input(resources, typeInfo, operationRows, edgeMapper.selectByTenantId(tenantId));
    }
    private DependencyCompiler.Result compileInMemory(String service, List<DependencyCompiler.Declaration> declarations,
                                                        Map<String, PermissionDependencyDeclaration> old, Input input) {
        List<DependencyCompiler.Edge> retained = input.existingEdges().stream()
                .filter(e -> !service.equals(e.getOwnerServiceCode()))
                .map(e -> new DependencyCompiler.Edge(e.getResourceEntityId(), e.getDependsOnResourceEntityId(),
                        e.getSourceOperationBits(), e.getRequiredOperationBits())).toList();
        Set<String> stable = declarations.stream().filter(d -> {
            var row = old.get(d.businessKey());
            return row != null && PermissionDependencyDeclaration.COMPILE_STATUS_RESOLVED.equals(row.getCompileStatus())
                    && semanticHash(d).equals(row.getSemanticHash());
        }).map(DependencyCompiler.Declaration::businessKey).collect(Collectors.toSet());
        return compiler.compile(service, declarations, input.resources(), input.types(),
                input.operations(), retained, stable);
    }

    private record EdgeKey(String service, Long source, Long target, Long trigger) {}
    public void replaceGraphs(Long tenantId, Map<String, List<DependencyCompiler.Edge>> graphs, LocalDateTime now) {
        Map<EdgeKey, Long> diagnosticIds = new HashMap<>();
        for (var row : loadScopes(tenantId, graphs.keySet())) {
            if (PermissionDependencyDeclaration.COMPILE_STATUS_RESOLVED.equals(row.getCompileStatus())) {
                diagnosticIds.putIfAbsent(new EdgeKey(row.getSourceService(),
                        row.getSourceResourceId(), row.getTargetResourceId(), row.getSourceOperationBits()), row.getId());
            }
        }
        List<String> services = new ArrayList<>(graphs.keySet());
        SqlBatches.forEach(services, batch -> edgeMapper.removeCompiledScopes(tenantId, batch, now));
        List<ResourceDependency> compiled = new ArrayList<>();
        for (var entry : graphs.entrySet()) {
            String service = entry.getKey();
            for (var edge : entry.getValue()) {
                var row = new ResourceDependency();
                row.setTenantId(tenantId);
                row.setResourceEntityId(edge.sourceId());
                row.setDependsOnResourceEntityId(edge.targetId());
                row.setSourceOperationBits(edge.sourceOperationBits());
                row.setRequiredOperationBits(edge.requiredOperationBits());
                row.setDeclarationId(diagnosticIds.get(new EdgeKey(service, edge.sourceId(), edge.targetId(), edge.sourceOperationBits())));
                row.setOwnerServiceCode(service);
                row.setMaintainSource(ResourceDependency.MAINTAIN_SOURCE_MANIFEST);
                row.setCreatedAt(now);
                row.setUpdatedAt(now);
                row.setDeleteFlag(0L);
                compiled.add(row);
            }
        }

        SqlBatches.forEach(compiled, batch -> {
            if (edgeMapper.insertBatch(batch) != batch.size()) throw failure("compiled edge write count mismatch");
        });
    }

    /**
     * 装载租户全部有效编译边（物化共享图装载，T-PERM-072）。
     * <p>
     * 不按资源启停过滤；grant 能力包经本方法读取编译图（跨包不互读 mapper）。
     * 写路径调用方（物化/编译入口）持有资源树锁；只读解释/预览（T-PERM-073）经
     * REPEATABLE_READ 一致视图消费，不取写锁（设计 §11/§12 定案形态）。
     * 本方法无缓存直读、不声明独立事务（与类级硬契约一致）。
     * </p>
     */
    public List<DependencyCompiler.Edge> loadCompiledEdges(Long tenantId) {
        return edgeMapper.selectByTenantId(tenantId).stream()
                .map(e -> new DependencyCompiler.Edge(e.getResourceEntityId(), e.getDependsOnResourceEntityId(),
                        e.getSourceOperationBits(), e.getRequiredOperationBits())).toList();
    }

    /**
     * 装载租户全部有效声明行（T-PERM-073）：解释的边声明引用、声明诊断与对账共用。
     * <p>
     * 与 {@link #loadCompiledEdges} 同口径：无缓存直读、不声明独立事务，grant 能力包经
     * 本方法读取（跨包不互读 mapper）。REJECTED 行含 rejectReason 供诊断；RESOLVED 行
     * 携带编译键（source/target/触发位）供边↔声明对账。
     * </p>
     */
    public List<PermissionDependencyDeclaration> loadDeclarations(Long tenantId) {
        return declarationMapper.selectByTenantId(tenantId);
    }

    public PermissionDependencyDeclaration toRow(Long tenantId, String service, DependencyCompiler.Resolution resolution, LocalDateTime now) {        var d = resolution.declaration();
        var row = new PermissionDependencyDeclaration();
        row.setTenantId(tenantId);
        row.setSourceService(service);
        row.setDeclarationKey(d.declarationKey());
        row.setBusinessKey(d.businessKey());
        row.setBusinessKeyHash(SyncKeyCodecUtil.sha256Hex(d.businessKey()));
        row.setDeclarationPayload(normalizer.declarationJson(d));
        row.setSemanticHash(semanticHash(d));
        row.setCompileStatus(resolution.reason() == null
                ? PermissionDependencyDeclaration.COMPILE_STATUS_RESOLVED
                : PermissionDependencyDeclaration.COMPILE_STATUS_REJECTED);
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

    private List<PermissionDependencyDeclaration> loadScopes(Long tenantId, Set<String> services) {
        List<String> keys = new ArrayList<>(services);
        List<PermissionDependencyDeclaration> result = new ArrayList<>();
        SqlBatches.forEach(keys, batch -> result.addAll(declarationMapper.selectScopes(tenantId, batch)));
        return result;
    }
    /**
     * 资源删除：收缩编译贡献；返回受影响实体（受影响服务新旧边端点并集 ∪ 被删资源 ID），
     * 供调用方定位 AUTO_DEP 重算角色（T-PERM-072）。
     */
    public Set<Long> resourcesDeleted(Long tenantId, List<Long> ids, LocalDateTime now) {
        Set<String> services = new HashSet<>();
        SqlBatches.forEach(ids, batch -> services.addAll(declarationMapper.selectServicesByResourceIds(tenantId, batch)));
        Set<Long> affected = recompileResolved(tenantId, services, now);
        affected.addAll(ids);
        return affected;
    }

    /**
     * 类型/所有权/操作定义变更：重编译受影响声明并替换图；返回受影响实体
     * （受影响服务新旧边端点并集），供调用方定位 AUTO_DEP 重算角色（T-PERM-072）。
     */
    public Set<Long> typesChanged(Long tenantId, Set<String> typeCodes, boolean deleted, LocalDateTime now) {
        List<String> keys = new ArrayList<>(typeCodes);
        Set<String> services = new HashSet<>();
        SqlBatches.forEach(keys, batch -> {
            services.addAll(declarationMapper.selectServicesByTypes(tenantId, batch));
            if (deleted) declarationMapper.softDeleteTypes(tenantId, batch, now);
        });
        return recompileResolved(tenantId, services, now);
    }
    /** 仅重判已成功贡献；REJECTED 保留原状态，须由所属服务重发恢复。返回受影响实体集合。 */
    private Set<Long> recompileResolved(Long tenantId, Set<String> services, LocalDateTime now) {
        if (services.isEmpty()) return new HashSet<>();
        var existing = loadScopes(tenantId, services);
        Map<String, List<DependencyCompiler.Declaration>> active = new HashMap<>();
        Map<String, Map<String, PermissionDependencyDeclaration>> byService = new HashMap<>();
        for (var row : existing) {
            byService.computeIfAbsent(row.getSourceService(), k -> new HashMap<>()).put(row.getBusinessKey(), row);
            if (PermissionDependencyDeclaration.COMPILE_STATUS_RESOLVED.equals(row.getCompileStatus())) {
                active.computeIfAbsent(row.getSourceService(), k -> new ArrayList<>())
                        .add(normalizer.readDeclaration(row.getDeclarationPayload()));
            }
        }
        Input input = loadInputs(tenantId, active.values().stream().flatMap(List::stream).toList());
        // 受影响实体=受影响服务旧边端点 ∪ 重编译后新边端点（T-PERM-072 物化重算定位面）
        Set<Long> affectedEntities = new HashSet<>();
        input.existingEdges().stream()
                .filter(e -> services.contains(e.getOwnerServiceCode()))
                .forEach(e -> {
                    affectedEntities.add(e.getResourceEntityId());
                    affectedEntities.add(e.getDependsOnResourceEntityId());
                });
        Map<String, List<DependencyCompiler.Edge>> graphs = new HashMap<>();
        List<PermissionDependencyDeclaration> changes = new ArrayList<>();
        for (String service : services.stream().sorted().toList()) {
            var result = compileInMemory(service, active.getOrDefault(service, List.of()), byService.getOrDefault(service, Map.of()), input);
            result.declarations().forEach(r -> changes.add(toRow(tenantId, service, r, now)));
            graphs.put(service, result.edges());
            result.edges().forEach(edge -> {
                affectedEntities.add(edge.sourceId());
                affectedEntities.add(edge.targetId());
            });
        }
        SqlBatches.forEach(changes, batch -> {
            if (declarationMapper.saveAll(batch) != batch.size()) throw failure("lifecycle declaration write count mismatch");
        });
        replaceGraphs(tenantId, graphs, now);
        List<String> keys = new ArrayList<>(services);
        SqlBatches.forEach(keys, batch -> {
            stateMapper.markDirtyScopes(tenantId, batch);
            edgeMapper.refreshCompiledScopeDescriptions(tenantId, batch, now);
        });
        return affectedEntities;
    }
    private SystemException failure(String message) {
        return new SystemException(AccessErrorCode.SYSTEM_INIT_FAILED.getCode(), message);
    }
}
