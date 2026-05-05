package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.permission.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.permission.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.permission.dto.req.CheckInterfaceReq;
import cn.ac.fage.accessmesh.permission.dto.req.InterfaceSnapshotReq;
import cn.ac.fage.accessmesh.permission.dto.req.PermissionTreeReq;
import cn.ac.fage.accessmesh.permission.dto.req.QueryResourcesReq;
import cn.ac.fage.accessmesh.permission.dto.req.QueryScopesReq;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceResolveRequest;
import cn.ac.fage.accessmesh.permission.dto.resp.AuthCheckResp;
import cn.ac.fage.accessmesh.permission.dto.resp.BatchAuthCheckResp;
import cn.ac.fage.accessmesh.permission.dto.resp.BatchAuthCheckResp.AuthCheckItemResult;
import cn.ac.fage.accessmesh.permission.dto.resp.CheckInterfaceResp;
import cn.ac.fage.accessmesh.permission.dto.resp.InterfaceSnapshotResp;
import cn.ac.fage.accessmesh.permission.dto.resp.InterfaceSnapshotResp.ApiPermissionEntry;
import cn.ac.fage.accessmesh.permission.dto.resp.PermissionTreeResp;
import cn.ac.fage.accessmesh.permission.dto.resp.PermissionTreeResp.TreeNode;
import cn.ac.fage.accessmesh.permission.dto.resp.QueryResourcesResp;
import cn.ac.fage.accessmesh.permission.dto.resp.QueryResourcesResp.ResourceEntry;
import cn.ac.fage.accessmesh.permission.dto.resp.QueryScopesResp;
import cn.ac.fage.accessmesh.permission.dto.resp.QueryScopesResp.ScopeEntry;
import cn.ac.fage.accessmesh.permission.entity.AbstractUser;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceDependencyMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.service.PermissionService;
import cn.ac.fage.accessmesh.permission.service.domain.EntityBatchLoadDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermCacheDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionConditionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionConflictDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionVersionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.RolePermissionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.UserRoleDomainService;
import cn.ac.fage.accessmesh.permission.dto.query.PermQuery;
import cn.ac.fage.accessmesh.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.permission.service.domain.impl.RolePermEntryMapper;
import cn.ac.fage.accessmesh.permission.util.PermResultUtils;
import cn.ac.fage.accessmesh.permission.vo.InterfaceSnapshot;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot.RolePermEntry;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import cn.ac.fage.accessmesh.permission.entity.table.AbstractUserTableDef;
import cn.ac.fage.accessmesh.permission.entity.table.OperationPermissionTableDef;
import cn.ac.fage.accessmesh.permission.entity.table.ResourceApiMappingTableDef;
import cn.ac.fage.accessmesh.permission.entity.table.ResourceEntityTableDef;
import cn.ac.fage.accessmesh.permission.entity.table.RoleResourcePermissionTableDef;
import cn.ac.fage.accessmesh.permission.entity.table.ResourceDependencyTableDef;

@Service
public class PermissionServiceImpl implements PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionServiceImpl.class);
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final AbstractUserMapper abstractUserMapper;
    private final ResourceEntityMapper resourceEntityMapper;
    private final ResourceApiMappingMapper apiMappingMapper;
    private final OperationPermissionMapper operationPermissionMapper;
    private final RoleResourcePermissionMapper rolePermMapper;
    private final ResourceDependencyMapper resourceDependencyMapper;
    private final UserRoleDomainService userRoleDomainService;
    private final PermissionConflictDomainService permissionConflictDomainService;
    private final PermissionConditionDomainService permissionConditionDomainService;
    private final RolePermissionDomainService rolePermissionDomainService;
    private final TypeResolutionService typeResolutionService;
    private final PermCacheDomainService permCacheDomainService;
    private final PermissionVersionDomainService permissionVersionDomainService;
    private final ResourceEntityDomainService resourceEntityDomainService;
    private final EntityBatchLoadDomainService entityBatchLoadDomainService;
    private final RolePermEntryMapper rolePermEntryMapper;
    private final PermQueryEngine engine;

    // TODO: 构造函数依赖过多(17个)，违反单一职责原则
    // 建议：拆分为 PermissionQueryService/PermissionCheckService/PermissionTreeService
    // 优先级：P2（非阻塞，建议在下次大版本重构时处理）
    public PermissionServiceImpl(AbstractUserMapper abstractUserMapper,
                                 ResourceEntityMapper resourceEntityMapper,
                                 ResourceApiMappingMapper apiMappingMapper,
                                 OperationPermissionMapper operationPermissionMapper,
                                 RoleResourcePermissionMapper rolePermMapper,
                                 ResourceDependencyMapper resourceDependencyMapper,
                                 UserRoleDomainService userRoleDomainService,
                                 PermissionConflictDomainService permissionConflictDomainService,
                                 PermissionConditionDomainService permissionConditionDomainService,
                                 RolePermissionDomainService rolePermissionDomainService,
                                 TypeResolutionService typeResolutionService,
                                 PermCacheDomainService permCacheDomainService,
                                 PermissionVersionDomainService permissionVersionDomainService,
                                 ResourceEntityDomainService resourceEntityDomainService,
                                 EntityBatchLoadDomainService entityBatchLoadDomainService,
                                 RolePermEntryMapper rolePermEntryMapper,
                                 PermQueryEngine engine) {
        this.abstractUserMapper = abstractUserMapper;
        this.resourceEntityMapper = resourceEntityMapper;
        this.apiMappingMapper = apiMappingMapper;
        this.operationPermissionMapper = operationPermissionMapper;
        this.rolePermMapper = rolePermMapper;
        this.resourceDependencyMapper = resourceDependencyMapper;
        this.userRoleDomainService = userRoleDomainService;
        this.permissionConflictDomainService = permissionConflictDomainService;
        this.permissionConditionDomainService = permissionConditionDomainService;
        this.rolePermissionDomainService = rolePermissionDomainService;
        this.typeResolutionService = typeResolutionService;
        this.permCacheDomainService = permCacheDomainService;
        this.permissionVersionDomainService = permissionVersionDomainService;
        this.resourceEntityDomainService = resourceEntityDomainService;
        this.entityBatchLoadDomainService = entityBatchLoadDomainService;
        this.rolePermEntryMapper = rolePermEntryMapper;
        this.engine = engine;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthCheckResp check(Long tenantId, AuthCheckReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) return AuthCheckResp.deny("USER_NOT_FOUND");
        Long bizDomainId = typeResolutionService.resolveDomainId(tenantId, req.domainCode());

        PermQuery q = PermQuery.forAuthCheck(tenantId, userId,
            req.resourceTypeCode(), req.resourceCode(), req.operationCode());
        q.setCodeType(req.codeType());
        if (bizDomainId != null) q.setBizDomainId(bizDomainId);
        if (req.inheritMode() != null) q.setInheritMode(req.inheritMode());
        q.setContext(req.context());

        return PermResultUtils.toAuthCheckResp(engine.query(q));
    }

    @Override
    @Transactional(readOnly = true)
    public BatchAuthCheckResp batchCheck(Long tenantId, BatchAuthCheckReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) {
            return new BatchAuthCheckResp(req.items().stream()
                .map(item -> new AuthCheckItemResult(
                    item.resourceTypeCode(), item.resourceCode(), item.operationCode(), false, "USER_NOT_FOUND",
                    List.of(), List.of()))
                .toList());
        }
        Map<String, PermResult> resultsByKey = new LinkedHashMap<>();
        for (var item : req.items()) {
            PermQuery q = PermQuery.forAuthCheck(tenantId, userId,
                item.resourceTypeCode(), item.resourceCode(), item.operationCode());
            q.setCodeType(item.codeType());
            q.setInheritMode(item.inheritMode());
            q.setContext(req.context());
            String key = item.resourceCode() != null && !item.resourceCode().isBlank()
                ? item.resourceCode() : item.resourceTypeCode() + ":" + item.operationCode();
            resultsByKey.put(key, engine.query(q));
        }
        return PermResultUtils.toBatchAuthCheckResp(resultsByKey);
    }

    @Override
    @Transactional(readOnly = true)
    public CheckInterfaceResp checkInterface(Long tenantId, CheckInterfaceReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) return CheckInterfaceResp.deny("USER_NOT_FOUND");

        List<ResourceApiMapping> mappings = apiMappingMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.TENANT_ID.eq(tenantId))
                .and(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.SERVICE_CODE.eq(req.serviceCode()))
                .and(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.HTTP_METHOD.eq(req.httpMethod()))
                .and(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.DELETE_FLAG.eq(0))
                .and(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.ENABLED.eq(true)));
        if (mappings.isEmpty()) return CheckInterfaceResp.deny("API_NOT_REGISTERED");
        List<ResourceApiMapping> matched = mappings.stream()
            .filter(m -> pathMatches(m.getPathPattern(), req.path())).toList();
        if (matched.isEmpty()) return CheckInterfaceResp.deny("API_NOT_REGISTERED");
        Set<Long> entityIds = matched.stream()
            .map(ResourceApiMapping::getResourceEntityId).filter(Objects::nonNull).collect(Collectors.toSet());

        PermQuery q = PermQuery.forInterfaceCheck(tenantId, userId, Set.of("API"), entityIds, "ACCESS");
        q.setContext(req.context());
        return PermResultUtils.toCheckInterfaceResp(engine.query(q), 30);
    }

    public QueryResourcesResp queryResources(Long tenantId, QueryResourcesReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) return new QueryResourcesResp(List.of(), "", 60);
        Long bizDomainId = typeResolutionService.resolveDomainId(tenantId, req.domainCode());

        PermQuery q = PermQuery.forResourceQuery(tenantId, userId,
            req.resourceTypeCodes() != null ? new HashSet<>(req.resourceTypeCodes()) : Set.of(),
            req.operationCodes() != null ? new HashSet<>(req.operationCodes()) : Set.of());
        if (bizDomainId != null) q.setBizDomainId(bizDomainId);
        PermResult r = engine.query(q);
        return PermResultUtils.toQueryResourcesResp(r, 60);
    }

    public QueryScopesResp queryScopes(Long tenantId, QueryScopesReq req) {
        // 1. 参数验证
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) return new QueryScopesResp(false, "USER_NOT_FOUND", List.of(), List.of(), List.of(), "UNION", "", 60);

        Long parentResourceEntityId = typeResolutionService.resolveResourceId(
            tenantId, req.parentResourceTypeCode(), req.parentResourceCode(), req.parentCodeType(), req.domainCode());
        if (parentResourceEntityId == null) {
            return new QueryScopesResp(false, "OBJECT_KEY_NOT_FOUND", List.of(), List.of(), List.of(), "UNION", "", 60);
        }

        Long bizDomainId = typeResolutionService.resolveDomainId(tenantId, req.domainCode());
        Map<String, Object> ctx = req.context() != null ? req.context() : Map.of();

        ParentPermissionsResult parentResult = validateParentPermissions(tenantId, userId,
            req, parentResourceEntityId, bizDomainId, ctx);
        if (parentResult == null) {
            return new QueryScopesResp(false, "NO_PERMISSION", List.of(), List.of(), List.of(), "UNION", "", 60);
        }

        Map<String, ScopeAccumulator> merged = processScopePermissions(tenantId, userId,
            bizDomainId, req, parentResult.parentPermissionIds, ctx);

        return buildQueryScopesResponse(merged, userId, tenantId, parentResult);
    }

    private ScopeAccumulator buildScopeAccumulator(RolePermEntry entry, String scopeTypeCode,
                                                    Map<Long, ResourceEntity> scopeResourceMap,
                                                    Map<String, ScopeAccumulator> merged) {
        boolean scopeAll = entry.resourceEntityId() == null;
        ResourceEntity resource = scopeAll ? null : scopeResourceMap.get(entry.resourceEntityId());
        if (!scopeAll && (resource == null || resource.getDeleteFlag() != 0L)) {
            return null;
        }
        String mergeKey = scopeAll
            ? "ALL|" + scopeTypeCode
            : "ONE|" + scopeTypeCode + "|" + resource.getCodeType() + "|" + resource.getCode();
        return merged.computeIfAbsent(
            mergeKey,
            key -> new ScopeAccumulator(
                scopeTypeCode,
                scopeAll ? null : resource.getCode(),
                scopeAll ? null : resource.getCodeType(),
                scopeAll ? null : resource.getName(),
                scopeAll
            )
        );
    }

    private QueryScopesResp buildQueryScopesResponse(Map<String, ScopeAccumulator> merged,
                                                      Long userId, Long tenantId,
                                                      ParentPermissionsResult parentResult) {
        List<ScopeEntry> items = merged.values().stream()
            .map(item -> new ScopeEntry(
                item.resourceTypeCode,
                item.resourceCode,
                item.codeType,
                item.resourceName,
                item.scopeAll,
                new ArrayList<>(item.operations),
                new ArrayList<>(item.sources),
                new ArrayList<>(item.matchedRoleIds),
                new ArrayList<>(item.matchedPermissionIds),
                new ArrayList<>(item.dependOnPermissionIds)
            ))
            .toList();
        String permissionVersion = permissionVersionDomainService.buildPermissionVersionKey(userId, tenantId, Set.of());
        return new QueryScopesResp(
            true,
            null,
            new ArrayList<>(parentResult.matchedParentOps),
            new ArrayList<>(parentResult.parentPermissionIds),
            items,
            "UNION",
            permissionVersion,
            60
        );
    }

    private ParentPermissionsResult validateParentPermissions(Long tenantId, Long userId,
                                                               QueryScopesReq req, Long parentResourceEntityId,
                                                               Long bizDomainId, Map<String, Object> ctx) {
        Set<String> matchedParentOps = new HashSet<>();
        Set<Long> parentPermissionIds = new HashSet<>();
        for (String parentOpCode : req.parentOperationCodes()) {
            PermQuery q = PermQuery.forAuthCheck(tenantId, userId,
                req.parentResourceTypeCode(), req.parentResourceCode(), parentOpCode);
            q.setBizDomainId(bizDomainId);
            q.setContext(ctx);
            PermResult r = engine.query(q);
            if (r.allowed()) {
                matchedParentOps.add(parentOpCode);
                parentPermissionIds.addAll(r.matchedPermissionIds());
            }
        }
        if (parentPermissionIds.isEmpty()) return null;
        return new ParentPermissionsResult(matchedParentOps, parentPermissionIds);
    }

    private Map<String, ScopeAccumulator> processScopePermissions(Long tenantId, Long userId,
                                                                   Long bizDomainId, QueryScopesReq req,
                                                                   Set<Long> parentPermissionIds,
                                                                   Map<String, Object> ctx) {
        PermQuery q = PermQuery.forScopeQuery(tenantId, userId,
            new HashSet<>(req.scopeResourceTypeCodes()),
            new HashSet<>(req.scopeOperationCodes()));
        q.setBizDomainId(bizDomainId);
        q.setContext(ctx);
        PermResult result = engine.query(q);

        List<RolePermEntry> allEntries = result.allEntries();
        Map<Integer, List<RolePermEntry>> byType = allEntries.stream()
            .filter(e -> e.resourceType() != null)
            .collect(Collectors.groupingBy(RolePermEntry::resourceType));
        Map<Long, ResourceEntity> resourceMap = result.resourceMap();

        Map<String, Integer> scopeTypeValueMap = typeResolutionService.batchResolveTypeValues(
            tenantId, "resource_type", new HashSet<>(req.scopeResourceTypeCodes()));

        Map<String, ScopeAccumulator> merged = new LinkedHashMap<>();
        for (String scopeTypeCode : req.scopeResourceTypeCodes()) {
            Integer scopeType = scopeTypeValueMap.get(scopeTypeCode);
            if (scopeType == null) continue;
            List<RolePermEntry> typeEntries = byType.getOrDefault(scopeType, List.of());
            Map<String, Long> scopeOpIdMap = typeResolutionService.batchResolveOperationIds(
                tenantId, scopeTypeCode, new HashSet<>(req.scopeOperationCodes()));

            processScopeOperations(tenantId, ctx, req, scopeTypeCode, scopeOpIdMap,
                scopeOpId -> typeEntries.stream()
                    .filter(entry -> entry.operationPermissionId().equals(scopeOpId))
                    .filter(entry -> entry.dependOn() == null || parentPermissionIds.contains(entry.dependOn()))
                    .toList(), resourceMap, merged);
        }
        return merged;
    }

    private void processScopeOperations(Long tenantId, Map<String, Object> ctx,
                                         QueryScopesReq req, String scopeTypeCode,
                                         Map<String, Long> scopeOpIdMap,
                                         java.util.function.Function<Long, List<RolePermEntry>> entriesSupplier,
                                         Map<Long, ResourceEntity> scopeResourceMap,
                                         Map<String, ScopeAccumulator> merged) {
        for (String scopeOpCode : req.scopeOperationCodes()) {
            Long scopeOpId = scopeOpIdMap.get(scopeOpCode);
            if (scopeOpId == null) continue;
            List<RolePermEntry> entries = entriesSupplier.apply(scopeOpId);
            entries = permissionConditionDomainService.evaluate(tenantId, entries, ctx);
            entries = permissionConflictDomainService.filterPermMutex(tenantId, entries);
            for (RolePermEntry entry : entries) {
                ScopeAccumulator accumulator = buildScopeAccumulator(entry, scopeTypeCode, scopeResourceMap, merged);
                if (accumulator == null) continue;
                accumulator.operations.add(scopeOpCode);
                accumulator.sources.add(entry.dependOn() == null ? "DIRECT" : "DEPENDENT");
                if (entry.roleId() != null) accumulator.matchedRoleIds.add(entry.roleId());
                if (entry.permissionId() != null) accumulator.matchedPermissionIds.add(entry.permissionId());
                if (entry.dependOn() != null) accumulator.dependOnPermissionIds.add(entry.dependOn());
            }
        }
    }

    private static class ParentPermissionsResult {
        final Set<String> matchedParentOps;
        final Set<Long> parentPermissionIds;
        ParentPermissionsResult(Set<String> matchedParentOps, Set<Long> parentPermissionIds) {
            this.matchedParentOps = matchedParentOps;
            this.parentPermissionIds = parentPermissionIds;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public InterfaceSnapshotResp interfaceSnapshot(Long tenantId, InterfaceSnapshotReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) return new InterfaceSnapshotResp(false, 0, List.of());

        var cached = permCacheDomainService.getInterfaceSnapshot(tenantId, req.serviceCode());
        if (cached.isPresent()) {
            long cachedVersion = cached.get().version();
            if (req.permissionVersion() != null && req.permissionVersion().equals(cachedVersion)) {
                return new InterfaceSnapshotResp(true, cachedVersion, List.of());
            }
            List<ApiPermissionEntry> entries = cached.get().entries().stream()
                .map(e -> new ApiPermissionEntry(e.serviceCode(), e.httpMethod(), e.pathPattern(),
                    e.hasCondition(), e.conditionId()))
                .collect(Collectors.toList());
            return new InterfaceSnapshotResp(false, cachedVersion, entries);
        }

        Set<Long> effectiveRoleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, userId, null);
        if (effectiveRoleIds.isEmpty()) return new InterfaceSnapshotResp(false, 0, List.of());
        Set<Long> validRoleIds = permissionConflictDomainService.filterRoleMutex(tenantId, effectiveRoleIds);
        if (validRoleIds.isEmpty()) return new InterfaceSnapshotResp(false, 0, List.of());
        long currentVersion = permissionVersionDomainService.calculateMaxVersion(tenantId, validRoleIds);
        if (req.permissionVersion() != null && req.permissionVersion().equals(currentVersion)) {
            return new InterfaceSnapshotResp(true, currentVersion, List.of());
        }

        List<RoleResourcePermission> allPerms = rolePermMapper.selectListByQuery(
            QueryWrapper.create()
                .where(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(validRoleIds))
                .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );

        // 1. Get resource types with type-level permission (scopeAll=true)
        Set<Integer> scopeAllResourceTypes = allPerms.stream()
            .filter(p -> Boolean.TRUE.equals(p.getScopeAll()))
            .map(RoleResourcePermission::getResourceType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        Set<Long> allowedResourceIds = new HashSet<>();

        // 2. For scopeAll=true resource types, batch query all resources using IN clause
        if (!scopeAllResourceTypes.isEmpty()) {
            List<ResourceEntity> allTypeResources = resourceEntityMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(ResourceEntityTableDef.RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                    .and(ResourceEntityTableDef.RESOURCE_ENTITY.RESOURCE_TYPE.in(scopeAllResourceTypes))
                    .and(ResourceEntityTableDef.RESOURCE_ENTITY.DELETE_FLAG.eq(0))
            );
            allowedResourceIds.addAll(allTypeResources.stream()
                .map(ResourceEntity::getId)
                .toList());
        }

        // 3. Add instance-level permission resource IDs
        allowedResourceIds.addAll(allPerms.stream()
            .filter(p -> p.getResourceEntityId() != null && !Boolean.TRUE.equals(p.getScopeAll()))
            .map(RoleResourcePermission::getResourceEntityId)
            .collect(Collectors.toSet()));

        List<ApiPermissionEntry> entries = new ArrayList<>();
        if (!allowedResourceIds.isEmpty()) {
            List<ResourceApiMapping> apiMappings = apiMappingMapper.selectListByQuery(
                QueryWrapper.create()
                    .where(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.TENANT_ID.eq(tenantId))
                    .and(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.SERVICE_CODE.eq(req.serviceCode()))
                    .and(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.RESOURCE_ENTITY_ID.in(allowedResourceIds))
                    .and(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.DELETE_FLAG.eq(0))
                    .and(ResourceApiMappingTableDef.RESOURCE_API_MAPPING.ENABLED.eq(true))
            );
            for (ResourceApiMapping mapping : apiMappings) {
                boolean hasCondition = allPerms.stream()
                    .anyMatch(p -> p.getResourceEntityId().equals(mapping.getResourceEntityId()) && p.getConditionId() != null);
                Long conditionId = allPerms.stream()
                    .filter(p -> p.getResourceEntityId().equals(mapping.getResourceEntityId()) && p.getConditionId() != null)
                    .map(RoleResourcePermission::getConditionId).findFirst().orElse(null);
                entries.add(new ApiPermissionEntry(mapping.getServiceCode(), mapping.getHttpMethod(),
                    mapping.getPathPattern(), hasCondition, conditionId));
            }
        }
        List<ApiPermissionEntry> dedupedEntries = entries.stream()
            .collect(Collectors.toMap(
                item -> item.serviceCode() + "|" + item.httpMethod() + "|" + item.pathPattern(),
                item -> item,
                (left, right) -> left.hasCondition() ? left : right,
                LinkedHashMap::new
            ))
            .values()
            .stream()
            .toList();
        InterfaceSnapshot snapshot = new InterfaceSnapshot(
            tenantId,
            req.serviceCode(),
            currentVersion,
            dedupedEntries.stream()
                .map(item -> new InterfaceSnapshot.InterfacePermEntry(
                    item.serviceCode(), item.httpMethod(), item.pathPattern(), item.hasCondition(), item.conditionId()
                ))
                .toList()
        );
        permCacheDomainService.setInterfaceSnapshot(tenantId, req.serviceCode(), snapshot);
        return new InterfaceSnapshotResp(false, currentVersion, dedupedEntries);
    }

    // =========== Internal helpers ===========

    /**
     * Query type-level permissions (scopeAll=true) for a resource type + operation.
     * Type-level permission means the user has permission on ALL instances of this resource type.
     *
     * @param tenantId tenant ID
     * @param roleIds valid role IDs (after mutex filtering)
     * @param resourceType resource type value
     * @param operationPermissionId operation permission ID
     * @return list of matching RolePermEntry, empty if no type-level permission found
     */
    private List<RolePermEntry> queryMatchedEntriesBatch(Long tenantId, Set<Long> roleIds,
                                                          Set<Long> resourceEntityIds) {
        if (roleIds.isEmpty() || resourceEntityIds.isEmpty()) {
            return List.of();
        }

        // Query ALL instance-level permissions for these resources
        // Caller will filter by matchesBit to preserve original behavior
        List<RoleResourcePermission> perms = rolePermMapper.selectListByQuery(
            QueryWrapper.create()
                .where(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(roleIds))
                .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.RESOURCE_ENTITY_ID.in(resourceEntityIds))
                .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.SCOPE_ALL.ne(true))  // Instance-level only
                .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );

        if (perms.isEmpty()) {
            return List.of();
        }

        // Convert to RolePermEntry without matchesBit filtering
        return perms.stream()
            .map(perm -> new RolePermEntry(
                perm.getId(),
                perm.getAbstractRoleId(),
                perm.getResourceEntityId(),
                null,
                perm.getResourceType(),
                perm.getOperationPermissionId(),
                null,  // opCode will be loaded by caller if needed
                null,
                perm.getGrantSource(),
                perm.getCanGrant(),
                perm.getConditionId(),
                perm.getConditionId() != null,
                perm.getDependOn()))
            .toList();
    }

    private static final class ScopeAccumulator {
        private final String resourceTypeCode;
        private final String resourceCode;
        private final String codeType;
        private final String resourceName;
        private final boolean scopeAll;
        private final Set<String> operations = new LinkedHashSet<>();
        private final Set<String> sources = new LinkedHashSet<>();
        private final Set<Long> matchedRoleIds = new LinkedHashSet<>();
        private final Set<Long> matchedPermissionIds = new LinkedHashSet<>();
        private final Set<Long> dependOnPermissionIds = new LinkedHashSet<>();

        private ScopeAccumulator(
            String resourceTypeCode,
            String resourceCode,
            String codeType,
            String resourceName,
            boolean scopeAll
        ) {
            this.resourceTypeCode = resourceTypeCode;
            this.resourceCode = resourceCode;
            this.codeType = codeType;
            this.resourceName = resourceName;
            this.scopeAll = scopeAll;
        }
    }

    private boolean pathMatches(String pattern, String path) {
        if (pattern.equals(path)) return true;
        // AntPathMatcher 支持 *、**、{xxx} 三种通配符
        return PATH_MATCHER.match(pattern, path);
    }

    @Override
    @Transactional(readOnly = true)
    public PermissionTreeResp queryPermissionTree(Long tenantId, PermissionTreeReq req) {
        // 1. 参数验证和上下文准备
        TreeContext context = prepareTreeContext(tenantId, req);
        if (context.userId == null) {
            return new PermissionTreeResp(null, List.of(), List.of(), null, 60);
        }
        if (context.rootResourceId == null) {
            return new PermissionTreeResp(null, List.of(), List.of(), null, 60);
        }
        if (context.validRoleIds.isEmpty()) {
            ResourceEntity rootResource = resourceEntityMapper.selectOneById(context.rootResourceId);
            return new PermissionTreeResp(buildNode(context.rootResourceId, 0, Set.of(), false, null, rootResource, Map.of()),
                List.of(), List.of(), null, 60);
        }

        // 2. 构建权限映射
        Map<Long, List<RoleResourcePermission>> permissionMap = buildPermissionMap(tenantId, context);

        // 3. 构建树
        return buildPermissionTreeResponse(tenantId, context, permissionMap);
    }

    /**
     * 权限树查询上下文
     */
    private static class TreeContext {
        final Long userId;
        final Long rootResourceId;
        final Long bizDomainId;
        final Set<Long> validRoleIds;
        final Set<Long> operationIds;
        final Map<Long, OperationPermission> operationMap;
        final int maxDepth;
        final String direction;

        TreeContext(Long userId, Long rootResourceId, Long bizDomainId, Set<Long> validRoleIds,
                    Set<Long> operationIds, Map<Long, OperationPermission> operationMap,
                    int maxDepth, String direction) {
            this.userId = userId;
            this.rootResourceId = rootResourceId;
            this.bizDomainId = bizDomainId;
            this.validRoleIds = validRoleIds;
            this.operationIds = operationIds;
            this.operationMap = operationMap;
            this.maxDepth = maxDepth;
            this.direction = direction;
        }
    }

    private TreeContext prepareTreeContext(Long tenantId, PermissionTreeReq req) {
        // 1. Resolve subject to internal user ID
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) {
            return new TreeContext(null, null, null, Set.of(), Set.of(), Map.of(), 10, "BOTH");
        }

        // 2. Resolve starting resource
        Long rootResourceId = typeResolutionService.resolveResourceId(
            tenantId, req.resourceTypeCode(), req.resourceCode(), req.codeType(), req.domainCode()
        );
        if (rootResourceId == null) {
            return new TreeContext(userId, null, null, Set.of(), Set.of(), Map.of(), 10, "BOTH");
        }

        // 3. Resolve bizDomainId from domainCode
        Long bizDomainId = typeResolutionService.resolveDomainId(tenantId, req.domainCode());

        // 4. Get effective roles
        Set<Long> effectiveRoleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, userId, bizDomainId);
        if (effectiveRoleIds.isEmpty()) {
            return new TreeContext(userId, rootResourceId, bizDomainId, Set.of(), Set.of(), Map.of(), 10, "BOTH");
        }
        Set<Long> validRoleIds = permissionConflictDomainService.filterRoleMutex(tenantId, effectiveRoleIds);
        if (validRoleIds.isEmpty()) {
            return new TreeContext(userId, rootResourceId, bizDomainId, Set.of(), Set.of(), Map.of(), 10, "BOTH");
        }

        // 5. Resolve operation permission IDs for requested operation codes
        Set<Long> operationIds = resolveOperationIds(tenantId, req);

        // Batch load operation permissions for getOperationsForResource calls
        Map<Long, OperationPermission> operationMap = entityBatchLoadDomainService.batchLoadOperations(tenantId, operationIds);

        int maxDepth = req.maxDepth() != null ? req.maxDepth() : 10;
        String direction = req.direction() != null ? req.direction().toUpperCase() : "BOTH";

        return new TreeContext(userId, rootResourceId, bizDomainId, validRoleIds, operationIds, operationMap, maxDepth, direction);
    }

    private Set<Long> resolveOperationIds(Long tenantId, PermissionTreeReq req) {
        // Batch resolve operation codes (avoid N+1)
        Set<String> opCodes = new HashSet<>(req.operationCodes());
        Map<String, Long> opIdMap = typeResolutionService.batchResolveOperationIds(tenantId, req.resourceTypeCode(), opCodes);
        return new HashSet<>(opIdMap.values());
    }

    private Map<Long, List<RoleResourcePermission>> buildPermissionMap(Long tenantId, TreeContext context) {
        // Get all permissions for valid roles
        List<RoleResourcePermission> allPerms = rolePermMapper.selectListByQuery(
            QueryWrapper.create()
                .where(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.TENANT_ID.eq(tenantId))
                .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.ABSTRACT_ROLE_ID.in(context.validRoleIds))
                .and(RoleResourcePermissionTableDef.ROLE_RESOURCE_PERMISSION.DELETE_FLAG.eq(0))
        );

        // Build permission map by resource entity ID
        return allPerms.stream()
            .filter(p -> p.getResourceEntityId() != null)
            .collect(Collectors.groupingBy(RoleResourcePermission::getResourceEntityId));
    }

    private PermissionTreeResp buildPermissionTreeResponse(Long tenantId, TreeContext context,
                                                            Map<Long, List<RoleResourcePermission>> permsByResource) {
        // Batch load all related resources for tree traversal (avoid N+1)
        List<ResourceEntity> allResources = resourceEntityMapper.selectListByQuery(
            QueryWrapper.create()
                .where(ResourceEntityTableDef.RESOURCE_ENTITY.TENANT_ID.eq(tenantId))
                .and(ResourceEntityTableDef.RESOURCE_ENTITY.DELETE_FLAG.eq(0))
        );
        Map<Long, ResourceEntity> allResourceMap = allResources.stream()
            .collect(Collectors.toMap(ResourceEntity::getId, r -> r));

        // Batch resolve all resource type codes (avoid N+1)
        Set<Integer> allResourceTypes = allResources.stream()
            .map(ResourceEntity::getResourceType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Integer, String> resourceTypeCodeMap = typeResolutionService.batchResolveTypeCodes(tenantId, "resource_type", allResourceTypes);

        // Build root node
        ResourceEntity rootResource = allResourceMap.get(context.rootResourceId);
        TreeNode root = buildNode(context.rootResourceId, 0,
            getOperationsForResource(permsByResource.get(context.rootResourceId), context.operationIds, context.operationMap),
            hasCanGrant(permsByResource.get(context.rootResourceId), context.operationIds),
            rootResource != null ? rootResource.getName() : null,
            rootResource, resourceTypeCodeMap);

        // Traverse based on direction
        List<TreeNode> ancestors = List.of();
        List<TreeNode> descendants = List.of();

        if ("ANCESTORS".equals(context.direction) || "BOTH".equals(context.direction)) {
            ancestors = traverseAncestors(tenantId, context.rootResourceId, permsByResource,
                context.operationIds, context.operationMap, context.maxDepth, allResourceMap, resourceTypeCodeMap);
        }
        if ("DESCENDANTS".equals(context.direction) || "BOTH".equals(context.direction)) {
            descendants = traverseDescendants(tenantId, context.rootResourceId, permsByResource,
                context.operationIds, context.operationMap, context.maxDepth, allResourceMap, resourceTypeCodeMap);
        }

        // Build permission version
        String permissionVersion = permissionVersionDomainService.buildPermissionVersionKey(context.userId, tenantId, context.validRoleIds);

        return new PermissionTreeResp(root, ancestors, descendants, permissionVersion, 60);
    }

    private TreeNode buildNode(Long resourceId, int depth,
                               Set<String> operations, boolean canGrant, String name,
                               ResourceEntity resource, Map<Integer, String> resourceTypeCodeMap) {
        if (resource == null) {
            return new TreeNode(resourceId, null, null, name, depth, operations, canGrant, null);
        }
        String typeCode = resourceTypeCodeMap.get(resource.getResourceType());
        return new TreeNode(resourceId, typeCode, resource.getCode(), resource.getName(), depth, operations, canGrant, null);
    }

    private Set<String> getOperationsForResource(List<RoleResourcePermission> perms, Set<Long> operationIds,
                                                  Map<Long, OperationPermission> operationMap) {
        if (perms == null || perms.isEmpty()) return Set.of();
        return perms.stream()
            .filter(p -> operationIds.contains(p.getOperationPermissionId()))
            .map(p -> {
                OperationPermission op = operationMap.get(p.getOperationPermissionId());
                return op != null ? op.getCode() : null;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }

    private boolean hasCanGrant(List<RoleResourcePermission> perms, Set<Long> operationIds) {
        if (perms == null || perms.isEmpty()) return false;
        return perms.stream()
            .filter(p -> operationIds.contains(p.getOperationPermissionId()))
            .anyMatch(p -> Boolean.TRUE.equals(p.getCanGrant()));
    }

    private List<TreeNode> traverseAncestors(Long tenantId, Long startResourceId,
                                              Map<Long, List<RoleResourcePermission>> permsByResource,
                                              Set<Long> operationIds, Map<Long, OperationPermission> operationMap,
                                              int maxDepth, Map<Long, ResourceEntity> allResourceMap,
                                              Map<Integer, String> resourceTypeCodeMap) {
        List<TreeNode> ancestors = new ArrayList<>();
        Long currentId = startResourceId;
        int depth = -1;

        while (currentId != null && Math.abs(depth) <= maxDepth) {
            ResourceEntity resource = allResourceMap.get(currentId);
            if (resource == null || resource.getDeleteFlag() != 0L || !resource.getTenantId().equals(tenantId)) {
                break;
            }
            if (resource.getParentId() != null) {
                List<RoleResourcePermission> perms = permsByResource.get(resource.getParentId());
                Set<String> ops = getOperationsForResource(perms, operationIds, operationMap);
                if (!ops.isEmpty()) {
                    ResourceEntity parentResource = allResourceMap.get(resource.getParentId());
                    TreeNode node = buildNode(resource.getParentId(), depth, ops,
                        hasCanGrant(perms, operationIds), null, parentResource, resourceTypeCodeMap);
                    ancestors.add(node);
                }
                currentId = resource.getParentId();
                depth--;
            } else {
                break;
            }
        }
        return ancestors;
    }

    private List<TreeNode> traverseDescendants(Long tenantId, Long startResourceId,
                                                Map<Long, List<RoleResourcePermission>> permsByResource,
                                                Set<Long> operationIds, Map<Long, OperationPermission> operationMap,
                                                int maxDepth, Map<Long, ResourceEntity> allResourceMap,
                                                Map<Integer, String> resourceTypeCodeMap) {
        List<TreeNode> descendants = new ArrayList<>();
        collectDescendantsWithPermission(tenantId, startResourceId, permsByResource, operationIds, operationMap,
            1, maxDepth, descendants, allResourceMap, resourceTypeCodeMap);
        return descendants;
    }

    private void collectDescendantsWithPermission(Long tenantId, Long parentId,
                                                   Map<Long, List<RoleResourcePermission>> permsByResource,
                                                   Set<Long> operationIds, Map<Long, OperationPermission> operationMap,
                                                   int currentDepth, int maxDepth,
                                                   List<TreeNode> result,
                                                   Map<Long, ResourceEntity> allResourceMap,
                                                   Map<Integer, String> resourceTypeCodeMap) {
        if (currentDepth > maxDepth) return;

        // Filter children from pre-loaded resources
        List<ResourceEntity> children = allResourceMap.values().stream()
            .filter(r -> Objects.equals(r.getParentId(), parentId) && r.getDeleteFlag() == 0L && r.getTenantId().equals(tenantId))
            .collect(Collectors.toList());

        for (ResourceEntity child : children) {
            List<RoleResourcePermission> perms = permsByResource.get(child.getId());
            Set<String> ops = getOperationsForResource(perms, operationIds, operationMap);
            if (!ops.isEmpty()) {
                TreeNode node = buildNode(child.getId(), currentDepth, ops,
                    hasCanGrant(perms, operationIds), child.getName(), child, resourceTypeCodeMap);
                result.add(node);
            }
            collectDescendantsWithPermission(tenantId, child.getId(), permsByResource, operationIds, operationMap,
                currentDepth + 1, maxDepth, result, allResourceMap, resourceTypeCodeMap);
        }
    }
}
