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

/**
 * 权限服务实现类
 * <p>
 * 提供权限校验、资源查询、范围查询、权限树查询、接口快照等核心功能。
 * 使用PermQueryEngine作为统一查询入口，支持条件评估、冲突解决等高级功能。
 * </p>
 * <p>
 * TODO: 构造函数依赖过多(17个)，违反单一职责原则
 * 建议：拆分为PermissionQueryService/PermissionCheckService/PermissionTreeService
 * </p>
 */
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

    /**
     * 构造函数注入所有依赖
     */
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

    /**
     * 单次权限校验
     * <p>
     * 检查用户对指定资源是否有指定操作的权限。
     * 使用PermQueryEngine作为统一查询入口。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      权限校验请求，包含用户、资源、操作等参数
     * @return 权限校验响应，包含是否允许、拒绝原因、匹配的权限等信息
     */
    @Override
    @Transactional(readOnly = true)
    public AuthCheckResp check(Long tenantId, AuthCheckReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) return AuthCheckResp.deny("USER_NOT_FOUND");

        PermQuery q = PermQuery.forAuthCheck(tenantId, userId,
            req.resourceTypeCode(), req.resourceCode(), req.operationCode());
        q.setCodeType(req.codeType());
        if (req.inheritMode() != null) q.setInheritMode(req.inheritMode());
        q.setContext(req.context());

        return PermResultUtils.toAuthCheckResp(engine.query(q));
    }

    /**
     * 批量权限校验
     * <p>
     * 批量检查用户对多个资源的权限，返回每个资源的校验结果。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      批量权限校验请求，包含多个校验项
     * @return 批量权限校验响应，包含每个项的结果
     */
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

    /**
     * 接口级权限校验
     * <p>
     * 检查用户是否有访问指定API接口的权限。
     * 根据服务编码、HTTP方法、路径匹配API映射配置。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      接口校验请求，包含服务编码、HTTP方法、路径等
     * @return 接口校验响应，包含是否允许、拒绝原因
     */
    @Override
    @Transactional(readOnly = true)
    public CheckInterfaceResp checkInterface(Long tenantId, CheckInterfaceReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) return CheckInterfaceResp.deny("USER_NOT_FOUND");

        // 查询API映射配置
        List<ResourceApiMapping> mappings = apiMappingMapper.selectForInterfaceCheck(
            tenantId, req.serviceCode(), req.httpMethod());
        if (mappings.isEmpty()) return CheckInterfaceResp.deny("API_NOT_REGISTERED");

        // 匹配路径模式
        List<ResourceApiMapping> matched = mappings.stream()
            .filter(m -> pathMatches(m.getPathPattern(), req.path())).toList();
        if (matched.isEmpty()) return CheckInterfaceResp.deny("API_NOT_REGISTERED");

        // 提取资源实体ID集合
        Set<Long> entityIds = matched.stream()
            .map(ResourceApiMapping::getResourceEntityId).filter(Objects::nonNull).collect(Collectors.toSet());

        // 使用PermQueryEngine进行权限校验
        PermQuery q = PermQuery.forInterfaceCheck(tenantId, userId, Set.of("API"), entityIds, "ACCESS");
        q.setContext(req.context());
        return PermResultUtils.toCheckInterfaceResp(engine.query(q), 30);
    }

    /**
     * 查询用户可访问的资源列表
     * <p>
     * 根据用户角色和权限配置，返回用户有权限访问的资源。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      资源查询请求，包含资源类型、操作等参数
     * @return 资源查询响应，包含资源列表
     */
    public QueryResourcesResp queryResources(Long tenantId, QueryResourcesReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) return new QueryResourcesResp(List.of(), "", 60);

        PermQuery q = PermQuery.forResourceQuery(tenantId, userId,
            req.resourceTypeCodes() != null ? new HashSet<>(req.resourceTypeCodes()) : Set.of(),
            req.operationCodes() != null ? new HashSet<>(req.operationCodes()) : Set.of());
        PermResult r = engine.query(q);
        return PermResultUtils.toQueryResourcesResp(r, 60);
    }

    /**
     * 查询用户的数据范围
     * <p>
     * 基于父资源的权限，查询用户在子资源类型上的数据范围。
     * 支持条件评估和冲突解决。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      范围查询请求
     * @return 范围查询响应，包含可访问的范围列表
     */
    public QueryScopesResp queryScopes(Long tenantId, QueryScopesReq req) {
        // 1. 参数验证
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) return new QueryScopesResp(false, "USER_NOT_FOUND", List.of(), List.of(), List.of(), "UNION", "", 60);

        Long parentResourceEntityId = typeResolutionService.resolveResourceId(
            tenantId, req.parentResourceTypeCode(), req.parentResourceCode(), req.parentCodeType(), req.domainCode());
        if (parentResourceEntityId == null) {
            return new QueryScopesResp(false, "OBJECT_KEY_NOT_FOUND", List.of(), List.of(), List.of(), "UNION", "", 60);
        }

        Map<String, Object> ctx = req.context() != null ? req.context() : Map.of();

        // 2. 验证父资源权限
        ParentPermissionsResult parentResult = validateParentPermissions(tenantId, userId,
            req, parentResourceEntityId, ctx);
        if (parentResult == null) {
            return new QueryScopesResp(false, "NO_PERMISSION", List.of(), List.of(), List.of(), "UNION", "", 60);
        }

        // 3. 处理范围权限
        Map<String, ScopeAccumulator> merged = processScopePermissions(tenantId, userId,
            req, parentResult.parentPermissionIds, ctx);

        // 4. 构建响应
        return buildQueryScopesResponse(merged, userId, tenantId, parentResult);
    }

    /**
     * 构建范围累加器
     * <p>
     * 根据权限条目构建范围累加器对象，用于合并相同范围的操作
     * </p>
     */
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

    /**
     * 构建范围查询响应
     */
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

    /**
     * 验证父资源权限
     * <p>
     * 检查用户对父资源是否有任一操作的权限
     * </p>
     */
    private ParentPermissionsResult validateParentPermissions(Long tenantId, Long userId,
                                                               QueryScopesReq req, Long parentResourceEntityId,
                                                               Map<String, Object> ctx) {
        Set<String> matchedParentOps = new HashSet<>();
        Set<Long> parentPermissionIds = new HashSet<>();
        for (String parentOpCode : req.parentOperationCodes()) {
            PermQuery q = PermQuery.forAuthCheck(tenantId, userId,
                req.parentResourceTypeCode(), req.parentResourceCode(), parentOpCode);
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

    /**
     * 处理范围权限
     * <p>
     * 查询用户在子资源类型上的权限，应用条件评估和冲突解决
     * </p>
     */
    private Map<String, ScopeAccumulator> processScopePermissions(Long tenantId, Long userId,
                                                                   QueryScopesReq req,
                                                                   Set<Long> parentPermissionIds,
                                                                   Map<String, Object> ctx) {
        PermQuery q = PermQuery.forScopeQuery(tenantId, userId,
            new HashSet<>(req.scopeResourceTypeCodes()),
            new HashSet<>(req.scopeOperationCodes()));
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

    /**
     * 处理范围操作权限
     * <p>
     * 对每个操作进行条件评估和冲突解决，然后累加结果
     * </p>
     */
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

    /**
     * 父资源权限验证结果
     */
    private static class ParentPermissionsResult {
        final Set<String> matchedParentOps;
        final Set<Long> parentPermissionIds;
        ParentPermissionsResult(Set<String> matchedParentOps, Set<Long> parentPermissionIds) {
            this.matchedParentOps = matchedParentOps;
            this.parentPermissionIds = parentPermissionIds;
        }
    }

    /**
     * 获取接口权限快照
     * <p>
     * 获取用户在指定服务下所有可访问的API列表。
     * 使用缓存提升性能，支持版本号判断是否需要更新。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      接口快照请求，包含用户、服务编码、版本号等
     * @return 接口快照响应，包含可访问的API列表和版本号
     */
    @Override
    @Transactional(readOnly = true)
    public InterfaceSnapshotResp interfaceSnapshot(Long tenantId, InterfaceSnapshotReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) return new InterfaceSnapshotResp(false, 0, List.of());

        // 检查缓存
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

        // 解析用户有效角色
        Set<Long> effectiveRoleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, userId);
        if (effectiveRoleIds.isEmpty()) return new InterfaceSnapshotResp(false, 0, List.of());
        Set<Long> validRoleIds = permissionConflictDomainService.filterRoleMutex(tenantId, effectiveRoleIds);
        if (validRoleIds.isEmpty()) return new InterfaceSnapshotResp(false, 0, List.of());

        // 计算当前版本
        long currentVersion = permissionVersionDomainService.calculateMaxVersion(tenantId, validRoleIds);
        if (req.permissionVersion() != null && req.permissionVersion().equals(currentVersion)) {
            return new InterfaceSnapshotResp(true, currentVersion, List.of());
        }

        // 查询所有角色权限
        List<RoleResourcePermission> allPerms = rolePermMapper.selectValidByRoleIds(tenantId, validRoleIds);

        // 1. 获取类型级权限的资源类型（scopeAll=true）
        Set<Integer> scopeAllResourceTypes = allPerms.stream()
            .filter(p -> Boolean.TRUE.equals(p.getScopeAll()))
            .map(RoleResourcePermission::getResourceType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        Set<Long> allowedResourceIds = new HashSet<>();

        // 2. 对scopeAll=true的资源类型，批量查询所有资源
        if (!scopeAllResourceTypes.isEmpty()) {
            List<ResourceEntity> allTypeResources = resourceEntityMapper.selectValidByResourceTypes(
                tenantId, scopeAllResourceTypes);
            allowedResourceIds.addAll(allTypeResources.stream()
                .map(ResourceEntity::getId)
                .toList());
        }

        // 3. 添加实例级权限的资源ID
        allowedResourceIds.addAll(allPerms.stream()
            .filter(p -> p.getResourceEntityId() != null && !Boolean.TRUE.equals(p.getScopeAll()))
            .map(RoleResourcePermission::getResourceEntityId)
            .collect(Collectors.toSet()));

        // 查询API映射并构建响应
        List<ApiPermissionEntry> entries = new ArrayList<>();
        if (!allowedResourceIds.isEmpty()) {
            List<ResourceApiMapping> apiMappings = apiMappingMapper.selectForSnapshot(
                tenantId, req.serviceCode(), allowedResourceIds);
            // Build permission map indexed by resourceEntityId for O(n+m) lookup
            Map<Long, List<RoleResourcePermission>> permsByResource = allPerms.stream()
                .filter(p -> p.getResourceEntityId() != null)
                .collect(Collectors.groupingBy(RoleResourcePermission::getResourceEntityId));

            for (ResourceApiMapping mapping : apiMappings) {
                List<RoleResourcePermission> resourcePerms = permsByResource.getOrDefault(mapping.getResourceEntityId(), List.of());
                boolean hasCondition = resourcePerms.stream().anyMatch(p -> p.getConditionId() != null);
                Long conditionId = resourcePerms.stream()
                    .filter(p -> p.getConditionId() != null)
                    .map(RoleResourcePermission::getConditionId).findFirst().orElse(null);
                entries.add(new ApiPermissionEntry(mapping.getServiceCode(), mapping.getHttpMethod(),
                    mapping.getPathPattern(), hasCondition, conditionId));
            }
        }

        // 去重处理
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

        // 缓存结果
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

    // =========== 内部辅助方法 ==========

    /**
     * 批量查询匹配的权限条目
     * <p>
     * 查询指定角色集合和资源集合的实例级权限
     * </p>
     */
    private List<RolePermEntry> queryMatchedEntriesBatch(Long tenantId, Set<Long> roleIds,
                                                          Set<Long> resourceEntityIds) {
        if (roleIds.isEmpty() || resourceEntityIds.isEmpty()) {
            return List.of();
        }

        // 查询所有实例级权限
        List<RoleResourcePermission> perms = rolePermMapper.selectValidByRoleIdsAndResourceIds(
            tenantId, roleIds, resourceEntityIds);

        if (perms.isEmpty()) {
            return List.of();
        }

        return rolePermEntryMapper.toEntryList(perms);
    }

    /**
     * 范围累加器
     * <p>
     * 用于合并相同范围的操作权限
     * </p>
     */
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

    /**
     * 路径匹配
     * <p>
     * 使用AntPathMatcher进行路径模式匹配，支持*、**、{xxx}通配符
     * </p>
     */
    private boolean pathMatches(String pattern, String path) {
        if (pattern.equals(path)) return true;
        return PATH_MATCHER.match(pattern, path);
    }

    /**
     * 查询权限树
     * <p>
     * 从指定资源开始，向上/向下遍历资源树，返回有权限的节点。
     * 支持祖先、子孙、双向三种遍历方向。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      权限树查询请求
     * @return 权限树响应，包含根节点、祖先列表、子孙列表
     */
    @Override
    @Transactional(readOnly = true)
    public PermissionTreeResp queryPermissionTree(Long tenantId, PermissionTreeReq req) {
        // 1. 准备上下文
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
        final Set<Long> validRoleIds;
        final Set<Long> operationIds;
        final Map<Long, OperationPermission> operationMap;
        final int maxDepth;
        final String direction;

        TreeContext(Long userId, Long rootResourceId, Set<Long> validRoleIds,
                    Set<Long> operationIds, Map<Long, OperationPermission> operationMap,
                    int maxDepth, String direction) {
            this.userId = userId;
            this.rootResourceId = rootResourceId;
            this.validRoleIds = validRoleIds;
            this.operationIds = operationIds;
            this.operationMap = operationMap;
            this.maxDepth = maxDepth;
            this.direction = direction;
        }
    }

    /**
     * 准备权限树查询上下文
     */
    private TreeContext prepareTreeContext(Long tenantId, PermissionTreeReq req) {
        // 1. 解析用户ID
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) {
            return new TreeContext(null, null, Set.of(), Set.of(), Map.of(), 10, "BOTH");
        }

        // 2. 解析根资源ID
        Long rootResourceId = typeResolutionService.resolveResourceId(
            tenantId, req.resourceTypeCode(), req.resourceCode(), req.codeType(), req.domainCode()
        );
        if (rootResourceId == null) {
            return new TreeContext(userId, null, Set.of(), Set.of(), Map.of(), 10, "BOTH");
        }

        // 3. 获取有效角色
        Set<Long> effectiveRoleIds = userRoleDomainService.resolveEffectiveRoles(tenantId, userId);
        if (effectiveRoleIds.isEmpty()) {
            return new TreeContext(userId, rootResourceId, Set.of(), Set.of(), Map.of(), 10, "BOTH");
        }
        Set<Long> validRoleIds = permissionConflictDomainService.filterRoleMutex(tenantId, effectiveRoleIds);
        if (validRoleIds.isEmpty()) {
            return new TreeContext(userId, rootResourceId, Set.of(), Set.of(), Map.of(), 10, "BOTH");
        }

        // 4. 解析操作ID
        Set<Long> operationIds = resolveOperationIds(tenantId, req);

        // 批量加载操作权限
        Map<Long, OperationPermission> operationMap = entityBatchLoadDomainService.batchLoadOperations(tenantId, operationIds);

        int maxDepth = req.maxDepth() != null ? req.maxDepth() : 10;
        String direction = req.direction() != null ? req.direction().toUpperCase() : "BOTH";

        return new TreeContext(userId, rootResourceId, validRoleIds, operationIds, operationMap, maxDepth, direction);
    }

    /**
     * 解析操作ID集合
     */
    private Set<Long> resolveOperationIds(Long tenantId, PermissionTreeReq req) {
        Set<String> opCodes = new HashSet<>(req.operationCodes());
        Map<String, Long> opIdMap = typeResolutionService.batchResolveOperationIds(tenantId, req.resourceTypeCode(), opCodes);
        return new HashSet<>(opIdMap.values());
    }

    /**
     * 构建权限映射
     * <p>
     * 查询所有角色权限并按资源ID分组
     * </p>
     */
    private Map<Long, List<RoleResourcePermission>> buildPermissionMap(Long tenantId, TreeContext context) {
        List<RoleResourcePermission> allPerms = rolePermMapper.selectValidByRoleIds(
            tenantId, context.validRoleIds);

        return allPerms.stream()
            .filter(p -> p.getResourceEntityId() != null)
            .collect(Collectors.groupingBy(RoleResourcePermission::getResourceEntityId));
    }

    /**
     * 构建权限树响应
     */
    private PermissionTreeResp buildPermissionTreeResponse(Long tenantId, TreeContext context,
                                                            Map<Long, List<RoleResourcePermission>> permsByResource) {
        // 批量加载所有资源
        List<ResourceEntity> allResources = resourceEntityMapper.selectAllValid(tenantId);
        Map<Long, ResourceEntity> allResourceMap = allResources.stream()
            .collect(Collectors.toMap(ResourceEntity::getId, r -> r));

        // 批量解析资源类型编码
        Set<Integer> allResourceTypes = allResources.stream()
            .map(ResourceEntity::getResourceType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Integer, String> resourceTypeCodeMap = typeResolutionService.batchResolveTypeCodes(tenantId, "resource_type", allResourceTypes);

        // 构建根节点
        ResourceEntity rootResource = allResourceMap.get(context.rootResourceId);
        TreeNode root = buildNode(context.rootResourceId, 0,
            getOperationsForResource(permsByResource.get(context.rootResourceId), context.operationIds, context.operationMap),
            hasCanGrant(permsByResource.get(context.rootResourceId), context.operationIds),
            rootResource != null ? rootResource.getName() : null,
            rootResource, resourceTypeCodeMap);

        // 遍历祖先和子孙
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

        String permissionVersion = permissionVersionDomainService.buildPermissionVersionKey(context.userId, tenantId, context.validRoleIds);

        return new PermissionTreeResp(root, ancestors, descendants, permissionVersion, 60);
    }

    /**
     * 构建树节点
     */
    private TreeNode buildNode(Long resourceId, int depth,
                               Set<String> operations, boolean canGrant, String name,
                               ResourceEntity resource, Map<Integer, String> resourceTypeCodeMap) {
        if (resource == null) {
            return new TreeNode(resourceId, null, null, name, depth, operations, canGrant, null);
        }
        String typeCode = resourceTypeCodeMap.get(resource.getResourceType());
        return new TreeNode(resourceId, typeCode, resource.getCode(), resource.getName(), depth, operations, canGrant, null);
    }

    /**
     * 获取资源的操作列表
     */
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

    /**
     * 检查是否有授权传递权限
     */
    private boolean hasCanGrant(List<RoleResourcePermission> perms, Set<Long> operationIds) {
        if (perms == null || perms.isEmpty()) return false;
        return perms.stream()
            .filter(p -> operationIds.contains(p.getOperationPermissionId()))
            .anyMatch(p -> Boolean.TRUE.equals(p.getCanGrant()));
    }

    /**
     * 向上遍历祖先节点
     */
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

    /**
     * 向下遍历子孙节点
     */
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

    /**
     * 递归收集有权限的子孙节点
     */
    private void collectDescendantsWithPermission(Long tenantId, Long parentId,
                                                   Map<Long, List<RoleResourcePermission>> permsByResource,
                                                   Set<Long> operationIds, Map<Long, OperationPermission> operationMap,
                                                   int currentDepth, int maxDepth,
                                                   List<TreeNode> result,
                                                   Map<Long, ResourceEntity> allResourceMap,
                                                   Map<Integer, String> resourceTypeCodeMap) {
        if (currentDepth > maxDepth) return;

        // 从预加载资源中过滤子节点
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