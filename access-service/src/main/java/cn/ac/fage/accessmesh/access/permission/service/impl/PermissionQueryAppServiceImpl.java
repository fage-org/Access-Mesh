package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.perm.common.dto.req.InterfaceSnapshotReq;
import cn.ac.fage.accessmesh.perm.common.util.BusinessKeys;
import cn.ac.fage.accessmesh.perm.common.dto.req.QueryResourcesReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.QueryScopesReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp.ApiPermissionEntry;
import cn.ac.fage.accessmesh.perm.common.dto.resp.QueryResourcesResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.QueryScopesResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.QueryScopesResp.ScopeGroup;
import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.service.PermissionQueryAppService;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.access.permission.enums.DomainQueryMode;
import cn.ac.fage.accessmesh.access.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.access.permission.service.domain.PermissionConflictDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermEvalContext;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermQuery;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import cn.ac.fage.accessmesh.access.permission.util.OperationPermissionUtils;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.util.PermResultUtils;
import cn.ac.fage.accessmesh.access.permission.util.SnapshotAssembler;
import cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限查询应用服务实现
 * <p>
 * 提供高级查询功能：资源查询、范围查询、接口快照。
 * 从 PermissionServiceImpl 提取。
 * </p>
 */
@Service
public class PermissionQueryAppServiceImpl implements PermissionQueryAppService {

    private static final Logger log = LoggerFactory.getLogger(PermissionQueryAppServiceImpl.class);

    private final SubjectDomainService subjectDomainService;
    private final PermissionConflictDomainService permissionConflictDomainService;
    private final TypeResolutionService typeResolutionService;
    private final CacheService cacheService;
    private final DomainClassifyService domainClassifyService;
    private final PermQueryEngine engine;
    private final SnapshotAssembler snapshotAssembler;

    /**
     * 构造函数注入依赖
     *
     * @param subjectDomainService             主体领域服务
     * @param permissionConflictDomainService  权限冲突领域服务
     * @param typeResolutionService            类型解析服务
     * @param cacheService                     缓存服务
     * @param domainClassifyService            域分类服务
     * @param engine                           权限查询引擎
     * @param snapshotAssembler                快照装配器
     */
    public PermissionQueryAppServiceImpl(SubjectDomainService subjectDomainService,
                                          PermissionConflictDomainService permissionConflictDomainService,
                                          TypeResolutionService typeResolutionService,
                                          CacheService cacheService,
                                          DomainClassifyService domainClassifyService,
                                          PermQueryEngine engine,
                                          SnapshotAssembler snapshotAssembler) {
        this.subjectDomainService = subjectDomainService;
        this.permissionConflictDomainService = permissionConflictDomainService;
        this.typeResolutionService = typeResolutionService;
        this.cacheService = cacheService;
        this.domainClassifyService = domainClassifyService;
        this.engine = engine;
        this.snapshotAssembler = snapshotAssembler;
    }

    /**
     * 查询用户有权限的资源列表
     * <p>
     * 使用 forUserView 查询管线获取用户的所有权限（scopeAll 和实例级）。
     * 支持按资源类型、操作码过滤，支持继承权限和子资源展开。
     * 返回用户可访问的资源列表与缓存有效期（T-PERM-018 后不再返回 permissionVersion）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      资源查询请求
     * @return 资源查询响应，包含资源列表和缓存有效期
     */
    // ===== queryResources =====

    @Override
    @Transactional(readOnly = true)
    public QueryResourcesResp queryResources(Long tenantId, QueryResourcesReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) return new QueryResourcesResp(List.of(), 60);

        // LIST 全量权限（scopeAll 和实例级）；includeChildren/includeInherited 树扩展
        // 经引擎展示面展开轨道（T-PERM-057 第三套形态收编：清单面树扩展归口展示面展开，
        // 契约字段语义不变）
        PermQuery q = PermQuery.forUserView(tenantId, userId);
        q.setEvalContext(PermEvalContext.fromCallerMap(req.context()));
        if (Boolean.TRUE.equals(req.includeChildren())) {
            q.setInheritChildren(true);
        }
        if (Boolean.TRUE.equals(req.includeInherited())) {
            q.setInheritParents(true);
        }
        PermResult r = engine.query(q);
        return buildQueryResourcesResponse(r, req, tenantId);
    }

    private QueryResourcesResp buildQueryResourcesResponse(PermResult r, QueryResourcesReq req, Long tenantId) {
        Set<String> resourceTypeCodes = new HashSet<>(req.resourceTypeCodes());
        Set<String> operationCodes = new HashSet<>(req.operationCodes());
        String codeType = req.codeType();
        String domainCode = req.domainCode();
        Map<Long, ResourceEntity> resMap = r.resourceMap() != null
            ? new LinkedHashMap<>(r.resourceMap()) : new LinkedHashMap<>();
        Map<Long, OperationPermission> opMap = r.operationMap() != null ? r.operationMap() : Map.of();

        // 展开条目已由引擎展示面轨道并入 allEntries（grantSource=INHERITED）
        List<RolePermEntry> allEntries = r.allEntries();
        // T-PERM-058：子权限行不进清单面——其授权只在 query-scopes 主资源上下文内生效/可见，
        // 独立 INSTANCE 条目呈现会误导调用方（子行实例 ≠ 独立可访问）
        allEntries = allEntries.stream().filter(e -> e.dependOn() == null).toList();

        // Collect all resource types for batch resolution
        Set<Integer> resourceTypesNeeded = new HashSet<>();
        for (RolePermEntry e : allEntries) {
            if (e.resourceType() != null) {
                resourceTypesNeeded.add(e.resourceType());
            }
        }
        Map<Integer, String> resourceTypeCodeMap = !resourceTypesNeeded.isEmpty()
            ? typeResolutionService.batchResolveTypeCodes(tenantId, "resource_type", resourceTypesNeeded)
            : Map.of();

        // 按操作码过滤
        // 先构建 code→OperationPermission 索引用于 opMatch
        Map<String, OperationPermission> opByCode = opMap.values().stream()
            .collect(Collectors.toMap(
                o -> BusinessKeys.operationCodeKey(o.getResourceType(), o.getCode()),
                o -> o, (a, b) -> a));

        // 使用引擎的 covers() 覆盖判定：MANAGE 覆盖 VIEW 等
        java.util.function.Predicate<RolePermEntry> opMatch = entry -> {
            if (entry.grantedBits() == null || entry.resourceType() == null) return false;
            if (operationCodes.isEmpty()) return true;
            OperationPermission granted = OperationPermissionUtils.findByResourceTypeAndBinaryBit(
                opMap, entry.resourceType(), entry.grantedBits());
            if (granted == null) return false;
            // 检查授予的操作是否覆盖请求中的任一操作
            return operationCodes.stream().anyMatch(reqOp -> {
                OperationPermission target = opByCode.get(BusinessKeys.operationCodeKey(entry.resourceType(), reqOp));
                return target != null && OperationPermissionUtils.covers(granted, target);
            });
        };

        // 按 domainCode 过滤
        // T-PERM-055：GLOBAL_PLUS 覆盖集一次预载，谓词内 contains 复用（消除逐条目 matchesTypeCode 点查放大）
        Set<String> domainCoveredTypeCodes = domainCode == null || domainCode.isBlank()
            ? null
            : domainClassifyService.preloadCoveredTypeCodes(tenantId, DomainQueryMode.GLOBAL_PLUS, domainCode);
        java.util.function.Predicate<RolePermEntry> domainMatch = entry -> {
            if (domainCoveredTypeCodes == null) return true;
            if (entry.resourceType() == null) return false;
            String rtCode = resourceTypeCodeMap.get(entry.resourceType());
            return rtCode != null && domainCoveredTypeCodes.contains(rtCode);
        };

        // 按 codeType 过滤
        java.util.function.Predicate<RolePermEntry> codeTypeMatch = entry -> {
            if (codeType == null || codeType.isBlank()) return true;
            if (entry.resourceEntityId() == null) return true; // scopeAll 不限 codeType
            ResourceEntity res = resMap.get(entry.resourceEntityId());
            return res == null || res.getCodeType() == null
                || codeType.equalsIgnoreCase(res.getCodeType());
        };

        List<QueryResourcesResp.ResourceEntry> entries = new ArrayList<>();

        // 1. scopeAll entries — filter by operationCodes, domainCode, codeType
        Map<Integer, List<RolePermEntry>> scopeAllByType = allEntries.stream()
            .filter(e -> Boolean.TRUE.equals(e.scopeAll()) && e.resourceEntityId() == null)
            .collect(Collectors.groupingBy(RolePermEntry::resourceType, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<Integer, List<RolePermEntry>> e : scopeAllByType.entrySet()) {
            String rtCode = resourceTypeCodeMap.get(e.getKey());
            if (rtCode == null || (!resourceTypeCodes.isEmpty() && !resourceTypeCodes.contains(rtCode))) {
                continue;
            }
            // 先按 operationCodes / domainCode / codeType 过滤条目
            List<RolePermEntry> matchedPerms = e.getValue().stream()
                .filter(opMatch).filter(domainMatch).filter(codeTypeMatch).toList();
            if (matchedPerms.isEmpty()) continue;

            Set<String> ops = matchedPerms.stream()
                .map(entry -> OperationPermissionUtils.findByResourceTypeAndBinaryBit(opMap, entry.resourceType(), entry.grantedBits()))
                .filter(Objects::nonNull).map(OperationPermission::getCode).filter(Objects::nonNull)
                .filter(op -> operationCodes.isEmpty() || operationCodes.contains(op))
                .collect(Collectors.toCollection(LinkedHashSet::new));
            if (ops.isEmpty() && !operationCodes.isEmpty()) continue;

            List<String> sources = matchedPerms.stream().map(RolePermEntry::grantSource).filter(Objects::nonNull).distinct().toList();
            entries.add(new QueryResourcesResp.ResourceEntry(
                rtCode, null, null, null, false, ScopeMode.ALL,
                new ArrayList<>(ops), sources));
        }

        // 2. Instance-level entries — filter by resourceType, operationCodes, domainCode, codeType
        Map<Long, List<RolePermEntry>> byResource = allEntries.stream()
            .filter(e -> e.resourceEntityId() != null)
            .collect(Collectors.groupingBy(RolePermEntry::resourceEntityId, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<Long, List<RolePermEntry>> e : byResource.entrySet()) {
            ResourceEntity res = resMap.get(e.getKey());
            if (res == null) continue;
            String rtCode = resourceTypeCodeMap.get(res.getResourceType());
            if (rtCode == null || (!resourceTypeCodes.isEmpty() && !resourceTypeCodes.contains(rtCode))) {
                continue;
            }
            // 先按 operationCodes / domainCode / codeType 过滤条目
            List<RolePermEntry> matchedPerms = e.getValue().stream()
                .filter(opMatch).filter(domainMatch).filter(codeTypeMatch).toList();
            if (matchedPerms.isEmpty()) continue;

            Set<String> ops = matchedPerms.stream()
                .map(entry -> OperationPermissionUtils.findByResourceTypeAndBinaryBit(opMap, entry.resourceType(), entry.grantedBits()))
                .filter(Objects::nonNull).map(OperationPermission::getCode).filter(Objects::nonNull)
                .filter(op -> operationCodes.isEmpty() || operationCodes.contains(op))
                .collect(Collectors.toCollection(LinkedHashSet::new));
            if (ops.isEmpty() && !operationCodes.isEmpty()) continue;

            List<String> sources = matchedPerms.stream().map(RolePermEntry::grantSource).filter(Objects::nonNull).distinct().toList();
            boolean canGrant = matchedPerms.stream().anyMatch(p -> Boolean.TRUE.equals(p.canGrant()));
            entries.add(new QueryResourcesResp.ResourceEntry(
                rtCode, res.getCode(), res.getCodeType(), res.getName(),
                canGrant, ScopeMode.INSTANCE,
                new ArrayList<>(ops), sources));
        }

        return new QueryResourcesResp(entries, 60);
    }

    // ===== queryScopes（评估已收编统一引擎，四态组装见上） =====

    /**
     * 数据范围查询（T-PERM-057 第六套形态收编：评估全进引擎，AppService 只留四态线格式组装）。
     * <p>
     * 条件评估、条目互斥、depend_on 子权限过滤（主资源上下文一等入参）全部由统一引擎
     * LIST 管线执行；位覆盖语义由本方法复用引擎同一 covers 判定做 (type×op) 线格分桶
     * （分桶即线格式组装，不归引擎）。四态分组——raw 无覆盖条目 DENIED、有覆盖但评估后
     * 清空 EMPTY、过滤后含 scopeAll ALL、仅实例 INSTANCE（T-PERM-009 契约维持）。
     * </p>
     */
    @Override
    @Transactional(readOnly = true)
    public QueryScopesResp queryScopes(Long tenantId, QueryScopesReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) {
            return new QueryScopesResp("USER_NOT_FOUND", List.of(), List.of(), 60);
        }

        Long parentResourceEntityId = typeResolutionService.resolveResourceId(
            tenantId, req.parentResourceTypeCode(), req.parentResourceCode(), req.parentCodeType(), req.domainCode());
        if (parentResourceEntityId == null) {
            return new QueryScopesResp("OBJECT_KEY_NOT_FOUND", List.of(), List.of(), 60);
        }

        PermQuery q = PermQuery.forScopeQuery(tenantId, userId,
            new HashSet<>(req.scopeResourceTypeCodes()), new HashSet<>(req.scopeOperationCodes()));
        q.setParentResource(req.parentResourceTypeCode(), req.parentResourceCode(),
            req.parentCodeType(), new HashSet<>(req.parentOperationCodes()));
        q.setEvalContext(PermEvalContext.fromCallerMap(req.context()));

        PermResult result = engine.query(q);
        if (!result.allowed()
            && ("PARENT_NO_PERMISSION".equals(result.reason()) || "NO_ROLE".equals(result.reason()))) {
            // 仅「父资源无任何匹配权限 / 无角色」整表拒绝；条件评估清空与 depend_on 清空
            // 不属此列——rawEntries 事实源仍可四态分态（有覆盖→EMPTY），勿压成 DENIED
            // （grok 外评 P1：评估摘光范围类型时整表拒绝会让业务方把 EMPTY 误当 403）
            List<ScopeGroup> deniedGroups = buildDeniedGroups(req);
            return new QueryScopesResp("NO_PERMISSION", List.of(), deniedGroups, 60);
        }

        List<ScopeGroup> scopeGroups = buildScopeGroups(tenantId, req, result);

        // T-API-002：父权限 id 集合仅内部用于 DEPENDENT 子权限过滤，不再进线格式
        return new QueryScopesResp(
            null,
            new ArrayList<>(result.parentMatchedOperationCodes()),
            scopeGroups,
            60
        );
    }

    /**
     * 构造全 DENIED 分组（父资源鉴权整体拒绝时使用）。
     */
    private List<ScopeGroup> buildDeniedGroups(QueryScopesReq req) {
        List<ScopeGroup> groups = new ArrayList<>();
        for (String scopeTypeCode : req.scopeResourceTypeCodes()) {
            for (String scopeOpCode : req.scopeOperationCodes()) {
                groups.add(new ScopeGroup(
                    scopeTypeCode, scopeOpCode, ScopeMode.DENIED,
                    List.of()
                ));
            }
        }
        return groups;
    }

    /**
     * 四态线格式组装（评估事实来自引擎：rawEntries 为 depend_on 过滤后评估前条目，
     * instanceEntries 为条件/互斥评估后条目）。
     */
    private List<ScopeGroup> buildScopeGroups(Long tenantId, QueryScopesReq req, PermResult result) {
        List<RolePermEntry> rawEntries = result.rawEntries();
        List<RolePermEntry> filteredEntries = result.instanceEntries();
        Map<Integer, List<RolePermEntry>> rawByType = rawEntries.stream()
            .filter(e -> e.resourceType() != null)
            .collect(Collectors.groupingBy(RolePermEntry::resourceType));
        Map<Integer, List<RolePermEntry>> filteredByType = filteredEntries.stream()
            .filter(e -> e.resourceType() != null)
            .collect(Collectors.groupingBy(RolePermEntry::resourceType));
        Map<Long, ResourceEntity> resourceMap = result.resourceMap() != null ? result.resourceMap() : Map.of();
        Map<Long, OperationPermission> operationMap = result.operationMap() != null ? result.operationMap() : Map.of();
        Map<String, OperationPermission> grantedOpIndex = OperationPermissionUtils.indexByResourceTypeAndBinaryBit(operationMap.values());

        Map<String, Integer> scopeTypeValueMap = typeResolutionService.batchResolveTypeValues(
            tenantId, "resource_type", new HashSet<>(req.scopeResourceTypeCodes()));

        List<ScopeGroup> groups = new ArrayList<>();
        for (String scopeTypeCode : req.scopeResourceTypeCodes()) {
            Integer scopeType = scopeTypeValueMap.get(scopeTypeCode);
            if (scopeType == null) {
                // 类型未解析 → 该类型下所有操作 DENIED
                for (String scopeOpCode : req.scopeOperationCodes()) {
                    groups.add(new ScopeGroup(scopeTypeCode, scopeOpCode, ScopeMode.DENIED, List.of()));
                }
                continue;
            }
            List<RolePermEntry> typeRaw = rawByType.getOrDefault(scopeType, List.of());
            List<RolePermEntry> typeFiltered = filteredByType.getOrDefault(scopeType, List.of());
            Map<String, Long> scopeOpIdMap = typeResolutionService.batchResolveOperationIds(
                tenantId, scopeTypeCode, new HashSet<>(req.scopeOperationCodes()));

            for (String scopeOpCode : req.scopeOperationCodes()) {
                Long scopeOpId = scopeOpIdMap.get(scopeOpCode);
                if (scopeOpId == null) {
                    groups.add(new ScopeGroup(scopeTypeCode, scopeOpCode, ScopeMode.DENIED, List.of()));
                    continue;
                }
                OperationPermission targetOp = operationMap.get(scopeOpId);
                // raw：覆盖目标操作的条目（depend_on 过滤后、条件/互斥评估前）
                List<RolePermEntry> rawCovered = typeRaw.stream()
                    .filter(entry -> coversEntry(grantedOpIndex, entry, targetOp))
                    .toList();
                if (rawCovered.isEmpty()) {
                    groups.add(new ScopeGroup(scopeTypeCode, scopeOpCode, ScopeMode.DENIED, List.of()));
                    continue;
                }
                List<RolePermEntry> filteredCovered = typeFiltered.stream()
                    .filter(entry -> coversEntry(grantedOpIndex, entry, targetOp))
                    .toList();
                if (filteredCovered.isEmpty()) {
                    // 有操作权限但条件/互斥过滤后无数据
                    groups.add(new ScopeGroup(scopeTypeCode, scopeOpCode, ScopeMode.EMPTY, List.of()));
                    continue;
                }
                // ALL 优先：任一 scopeAll 条目存在 → 全量授权
                boolean hasScopeAll = filteredCovered.stream().anyMatch(e -> e.resourceEntityId() == null);
                if (hasScopeAll) {
                    groups.add(new ScopeGroup(scopeTypeCode, scopeOpCode, ScopeMode.ALL, List.of()));
                    continue;
                }
                // INSTANCE：收集有效具体实例（去重，跳过已删除资源）
                Map<String, QueryScopesResp.ScopeItem> items = new LinkedHashMap<>();
                for (RolePermEntry entry : filteredCovered) {
                    if (entry.resourceEntityId() == null) continue;
                    ResourceEntity resource = resourceMap.get(entry.resourceEntityId());
                    if (resource == null || resource.getDeleteFlag() != 0L) continue;
                    String itemKey = BusinessKeys.scopeItemKey(resource.getCodeType(), resource.getCode());
                    items.putIfAbsent(itemKey, new QueryScopesResp.ScopeItem(
                        resource.getCode(), resource.getCodeType(), resource.getName()));
                }
                // T-PERM-009 契约：INSTANCE 要求 items 非空；过滤后实例全失效（资源删除/不存在）→ EMPTY
                if (items.isEmpty()) {
                    groups.add(new ScopeGroup(scopeTypeCode, scopeOpCode, ScopeMode.EMPTY, List.of()));
                    continue;
                }
                groups.add(new ScopeGroup(scopeTypeCode, scopeOpCode, ScopeMode.INSTANCE,
                    new ArrayList<>(items.values())));
            }
        }
        return groups;
    }

    /** 条目授予操作是否覆盖目标操作（引擎位覆盖语义的组装层只读复用）。 */
    private boolean coversEntry(Map<String, OperationPermission> grantedOpIndex,
                                 RolePermEntry entry, OperationPermission targetOp) {
        if (targetOp == null || entry.grantedBits() == null || entry.resourceType() == null) {
            return false;
        }
        OperationPermission granted = OperationPermissionUtils.findIndexedByResourceTypeAndBinaryBit(
            grantedOpIndex, entry.resourceType(), entry.grantedBits());
        return granted != null && OperationPermissionUtils.covers(granted, targetOp);
    }

    // ===== interfaceSnapshot =====

    @Override
    @Transactional(readOnly = true)
    public InterfaceSnapshotResp interfaceSnapshot(Long tenantId, InterfaceSnapshotReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) return new InterfaceSnapshotResp(List.of());

        Set<Long> effectiveRoleIds = subjectDomainService.resolveEffectiveRoles(tenantId, userId);
        Set<Long> validRoleIds = effectiveRoleIds.isEmpty()
            ? Set.of()
            : permissionConflictDomainService.filterRoleMutex(tenantId, effectiveRoleIds);

        if (validRoleIds.isEmpty()) {
            // 无有效角色 → 空快照。Gateway 缓存空快照，靠 TTL + 广播最终一致。
            return new InterfaceSnapshotResp(List.of());
        }

        // T-PERM-018：缓存下沉——access-service 侧不再缓存 INTERFACE_SNAPSHOT(L2) 与 permissionVersion。
        // 每次实时调引擎构建全量快照（ROLE_PERM_SNAPSHOT 兜住角色权限记录读路径），交 Gateway 本地缓存匹配。
        // T-PERM-017 C3：标记不过滤——条件评估应在 Gateway 用真实请求 context 完成（IP/clientIp），
        // access-service 此处空 context 评估会误丢弃 IP 类条件条目；故标记为 markConditionsOnly。
        PermQuery query = PermQuery.forUserView(tenantId, userId);
        query.setRoleIds(validRoleIds); // 使用已过滤互斥的角色
        query.setMarkConditionsOnly(true);
        PermResult result = engine.query(query);
        Integer apiType = typeResolutionService.resolveTypeValue(tenantId, "resource_type", "API");
        List<ApiPermissionEntry> entries = snapshotAssembler.buildSnapshot(tenantId, result, req.serviceCode(), apiType);

        List<ApiPermissionEntry> dedupedEntries = entries.stream()
            .collect(Collectors.toMap(
                // T-PERM-017 C4 修 P1-②：去重 key 加 conditionId，避免同 API 多授权（无条件+含条件）
                // 被折叠成单条。Gateway InterfaceSnapshotMatcher 用 OR 语义合并多条 entry。
                // conditionId=null（无条件）参与 key，使无条件分支与任何条件分支独立保留。
                item -> BusinessKeys.apiEntryDedupKey(
                    item.serviceCode(), item.httpMethod(), item.pathPattern(), item.conditionId()),
                item -> item,
                (left, right) -> left,
                LinkedHashMap::new
            ))
            .values()
            .stream()
            .toList();

        return new InterfaceSnapshotResp(dedupedEntries);
    }

}
