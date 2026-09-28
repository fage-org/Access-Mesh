package cn.ac.fage.accessmesh.access.engine.service.impl;

import cn.ac.fage.accessmesh.perm.common.util.BusinessKeyUtil;
import cn.ac.fage.accessmesh.perm.common.dto.req.QueryResourcesReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.QueryScopesReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.QueryResourcesResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.QueryScopesResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.QueryScopesResp.ScopeGroup;
import cn.ac.fage.accessmesh.access.type.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.engine.service.PermissionQueryAppService;
import cn.ac.fage.accessmesh.access.domain.enums.DomainQueryMode;
import cn.ac.fage.accessmesh.access.domain.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.engine.query.ByCode;
import cn.ac.fage.accessmesh.access.engine.query.CallerContext;
import cn.ac.fage.accessmesh.access.engine.query.Evaluation;
import cn.ac.fage.accessmesh.access.engine.query.FactDetail;
import cn.ac.fage.accessmesh.access.engine.query.GrantFact;
import cn.ac.fage.accessmesh.access.engine.query.GrantSetResult;
import cn.ac.fage.accessmesh.access.engine.query.OutputSpec;
import cn.ac.fage.accessmesh.access.engine.query.ParentRequirement;
import cn.ac.fage.accessmesh.access.engine.query.PresentationEntry;
import cn.ac.fage.accessmesh.access.engine.query.PresentationExpansion;
import cn.ac.fage.accessmesh.access.engine.query.QueryExecutionEngine;
import cn.ac.fage.accessmesh.access.engine.query.QueryItem;
import cn.ac.fage.accessmesh.access.engine.query.QueryRequest;
import cn.ac.fage.accessmesh.access.engine.query.ReadOptions;
import cn.ac.fage.accessmesh.access.engine.query.ResultDetails;
import cn.ac.fage.accessmesh.access.engine.query.ScopeCoverageProjector;
import cn.ac.fage.accessmesh.access.engine.query.TypeOperation;
import cn.ac.fage.accessmesh.access.engine.query.User;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import cn.ac.fage.accessmesh.access.engine.util.OperationPermissionUtils;
import cn.ac.fage.accessmesh.access.type.enums.ResourceTypeCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
 * 提供高级查询功能：资源查询、范围查询。
 * T-PERM-090 起入口全部经新 {@link QueryExecutionEngine#execute} 表达
 * （设计 §6.2/§6.4/§6.6）：queryResources=GRANT_LIST＋EVALUATE/ENFORCE＋FACTS
 * （展示树扩展走 OutputSpec 展示展开，判定与展示分离）；queryScopes=GRANT_LIST＋
 * 父要求＋EVALUATE/ENFORCE＋RAW_AND_KEPT（四态组装交给 {@link ScopeCoverageProjector}
 * 纯投影）。
 * </p>
 */
@Service
public class PermissionQueryAppServiceImpl implements PermissionQueryAppService {

    /** 展示派生行的 grantSource（沿旧引擎 expandByPresentMode 克隆口径）。 */
    private static final String GRANT_SOURCE_INHERITED = "INHERITED";

    private final TypeResolutionService typeResolutionService;
    private final DomainClassifyService domainClassifyService;
    private final QueryExecutionEngine queryEngine;

    /**
     * 构造函数注入依赖
     *
     * @param typeResolutionService  类型解析服务（主体/父对象预检查与类型码解析）
     * @param domainClassifyService  域分类服务（queryResources 的 domainCode 过滤）
     * @param queryEngine            新查询执行器（T-PERM-090 起入口统一）
     */
    public PermissionQueryAppServiceImpl(TypeResolutionService typeResolutionService,
                                         DomainClassifyService domainClassifyService,
                                         QueryExecutionEngine queryEngine) {
        this.typeResolutionService = typeResolutionService;
        this.domainClassifyService = domainClassifyService;
        this.queryEngine = queryEngine;
    }

    // ===== queryResources（GRANT_LIST＋EVALUATE/ENFORCE＋FACTS；展示面树扩展归展示展开） =====

    /**
     * 查询用户有权限的资源列表
     * <p>
     * 使用 GRANT_LIST 完整事实获取用户的所有权限（scopeAll 和实例级）；
     * includeChildren/includeInherited 树扩展经 OutputSpec 展示展开
     * （T-PERM-057 第三套形态收编口径延续：清单面树扩展归口展示面展开，
     * 判定与展示分离，契约字段语义不变）。
     * 返回用户可访问的资源列表与缓存有效期（T-PERM-018 后不再返回 permissionVersion）。
     * </p>
     *
     * @param tenantId 租户ID
     * @param req      资源查询请求
     * @return 资源查询响应，包含资源列表和缓存有效期
     */
    @Override
    @Transactional(readOnly = true)
    public QueryResourcesResp queryResources(Long tenantId, QueryResourcesReq req) {
        Long userId = typeResolutionService.resolveUserId(tenantId, req.subjectTypeCode(), req.subjectExternalId());
        if (userId == null) return new QueryResourcesResp(List.of(), 60);

        PresentationExpansion expansion = presentationExpansion(req.includeChildren(), req.includeInherited());
        OutputSpec output = new OutputSpec(FactDetail.KEPT, false, true, false, expansion, Set.of(), false);
        QueryItem item = QueryItem.grantListFacts("resources", null, Evaluation.full(), output);
        GrantSetResult result = (GrantSetResult) queryEngine.execute(new QueryRequest(tenantId, new User(userId),
            CallerContext.fromCallerMap(req.context()), ReadOptions.defaults(), List.of(item))).orderedResults().get(0);
        return buildQueryResourcesResponse(result, req, tenantId);
    }

    /** 展示展开方向：includeChildren→CHILDREN、includeInherited→PARENTS、两者→BOTH（判定不受影响）。 */
    private static PresentationExpansion presentationExpansion(Boolean includeChildren, Boolean includeInherited) {
        boolean children = Boolean.TRUE.equals(includeChildren);
        boolean parents = Boolean.TRUE.equals(includeInherited);
        if (children && parents) return PresentationExpansion.BOTH;
        if (children) return PresentationExpansion.CHILDREN;
        if (parents) return PresentationExpansion.PARENTS;
        return PresentationExpansion.NONE;
    }

    /**
     * 响应组装（消费新结果：保留事实＋展示展开投影＋描述块）。
     * <p>
     * 展开行=PresentationEntry 按源授权关联（PARENT/CHILD 派生行 grantSource=INHERITED，
     * 沿旧 allEntries 克隆口径）；T-PERM-058：子权限行不进清单面——其授权只在
     * query-scopes 主资源上下文内生效/可见，独立 INSTANCE 条目呈现会误导调用方。
     * </p>
     */
    private QueryResourcesResp buildQueryResourcesResponse(GrantSetResult result, QueryResourcesReq req, Long tenantId) {
        Set<String> resourceTypeCodes = new HashSet<>(req.resourceTypeCodes());
        Set<String> operationCodes = new HashSet<>(req.operationCodes());
        String codeType = req.codeType();
        String domainCode = req.domainCode();
        ResultDetails details = result.details();

        List<GrantFact> rows = responseRows(details);
        // T-PERM-058：depend_on 子行不进清单面（子行实例 ≠ 独立可访问）
        rows = rows.stream().filter(e -> e.dependOn() == null).toList();

        Map<Long, ResultDetails.ResourceDescription> resMap = details.descriptions().resources();
        Map<Long, OperationPermission> opMap = new LinkedHashMap<>();
        details.descriptions().operations().values().forEach(op -> opMap.put(op.id(), op.toCacheRow()));

        // Collect all resource types for batch resolution
        Set<Integer> resourceTypesNeeded = new HashSet<>();
        for (GrantFact e : rows) {
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
                o -> BusinessKeyUtil.operationCodeKey(o.getResourceType(), o.getCode()),
                o -> o, (a, b) -> a));

        // 使用引擎的 covers() 覆盖判定：MANAGE 覆盖 VIEW 等
        java.util.function.Predicate<GrantFact> opMatch = entry -> {
            if (entry.grantedBits() == null || entry.resourceType() == null) return false;
            if (operationCodes.isEmpty()) return true;
            OperationPermission granted = OperationPermissionUtils.findByResourceTypeAndBinaryBit(
                opMap, entry.resourceType(), entry.grantedBits());
            if (granted == null) return false;
            // 检查授予的操作是否覆盖请求中的任一操作
            return operationCodes.stream().anyMatch(reqOp -> {
                OperationPermission target = opByCode.get(BusinessKeyUtil.operationCodeKey(entry.resourceType(), reqOp));
                return target != null && OperationPermissionUtils.covers(granted, target);
            });
        };

        // 按 domainCode 过滤
        // T-PERM-055：GLOBAL_PLUS 覆盖集一次预载，谓词内 contains 复用（消除逐条目 matchesTypeCode 点查放大）
        Set<String> domainCoveredTypeCodes = domainCode == null || domainCode.isBlank()
            ? null
            : domainClassifyService.preloadCoveredTypeCodes(tenantId, DomainQueryMode.GLOBAL_PLUS, domainCode);
        java.util.function.Predicate<GrantFact> domainMatch = entry -> {
            if (domainCoveredTypeCodes == null) return true;
            if (entry.resourceType() == null) return false;
            String rtCode = resourceTypeCodeMap.get(entry.resourceType());
            return rtCode != null && domainCoveredTypeCodes.contains(rtCode);
        };

        // 按 codeType 过滤
        java.util.function.Predicate<GrantFact> codeTypeMatch = entry -> {
            if (codeType == null || codeType.isBlank()) return true;
            if (entry.resourceEntityId() == null) return true; // scopeAll 不限 codeType
            ResultDetails.ResourceDescription res = resMap.get(entry.resourceEntityId());
            return res == null || res.codeType() == null
                || codeType.equalsIgnoreCase(res.codeType());
        };

        List<QueryResourcesResp.ResourceEntry> entries = new ArrayList<>();

        // 1. scopeAll entries — filter by operationCodes, domainCode, codeType
        Map<Integer, List<GrantFact>> scopeAllByType = rows.stream()
            .filter(e -> Boolean.TRUE.equals(e.scopeAll()) && e.resourceEntityId() == null)
            .collect(Collectors.groupingBy(GrantFact::resourceType, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<Integer, List<GrantFact>> e : scopeAllByType.entrySet()) {
            String rtCode = resourceTypeCodeMap.get(e.getKey());
            if (rtCode == null || (!resourceTypeCodes.isEmpty() && !resourceTypeCodes.contains(rtCode))) {
                continue;
            }
            // 先按 operationCodes / domainCode / codeType 过滤条目
            List<GrantFact> matchedPerms = e.getValue().stream()
                .filter(opMatch).filter(domainMatch).filter(codeTypeMatch).toList();
            if (matchedPerms.isEmpty()) continue;

            Set<String> ops = matchedPerms.stream()
                .map(entry -> OperationPermissionUtils.findByResourceTypeAndBinaryBit(opMap, entry.resourceType(), entry.grantedBits()))
                .filter(Objects::nonNull).map(OperationPermission::getCode).filter(Objects::nonNull)
                .filter(op -> operationCodes.isEmpty() || operationCodes.contains(op))
                .collect(Collectors.toCollection(LinkedHashSet::new));
            if (ops.isEmpty() && !operationCodes.isEmpty()) continue;

            List<String> sources = matchedPerms.stream().map(GrantFact::grantSource).filter(Objects::nonNull).distinct().toList();
            entries.add(new QueryResourcesResp.ResourceEntry(
                rtCode, null, null, null, false, ScopeMode.ALL,
                new ArrayList<>(ops), sources));
        }

        // 2. Instance-level entries — filter by resourceType, operationCodes, domainCode, codeType
        Map<Long, List<GrantFact>> byResource = rows.stream()
            .filter(e -> e.resourceEntityId() != null)
            .collect(Collectors.groupingBy(GrantFact::resourceEntityId, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<Long, List<GrantFact>> e : byResource.entrySet()) {
            ResultDetails.ResourceDescription res = resMap.get(e.getKey());
            if (res == null) continue;
            String rtCode = resourceTypeCodeMap.get(res.resourceType());
            if (rtCode == null || (!resourceTypeCodes.isEmpty() && !resourceTypeCodes.contains(rtCode))) {
                continue;
            }
            // 先按 operationCodes / domainCode / codeType 过滤条目
            List<GrantFact> matchedPerms = e.getValue().stream()
                .filter(opMatch).filter(domainMatch).filter(codeTypeMatch).toList();
            if (matchedPerms.isEmpty()) continue;

            Set<String> ops = matchedPerms.stream()
                .map(entry -> OperationPermissionUtils.findByResourceTypeAndBinaryBit(opMap, entry.resourceType(), entry.grantedBits()))
                .filter(Objects::nonNull).map(OperationPermission::getCode).filter(Objects::nonNull)
                .filter(op -> operationCodes.isEmpty() || operationCodes.contains(op))
                .collect(Collectors.toCollection(LinkedHashSet::new));
            if (ops.isEmpty() && !operationCodes.isEmpty()) continue;

            List<String> sources = matchedPerms.stream().map(GrantFact::grantSource).filter(Objects::nonNull).distinct().toList();
            boolean canGrant = matchedPerms.stream().anyMatch(p -> Boolean.TRUE.equals(p.canGrant()));
            entries.add(new QueryResourcesResp.ResourceEntry(
                rtCode, res.code(), res.codeType(), res.name(),
                canGrant, ScopeMode.INSTANCE,
                new ArrayList<>(ops), sources));
        }

        return new QueryResourcesResp(entries, 60);
    }

    /**
     * 响应行集合＝旧 allEntries 等价物。
     * <p>
     * 无展示展开：保留事实本身（grantSource 为存储值）；有展示展开：按
     * PresentationEntry 驱动——ORIGINAL 行取源事实，PARENT/CHILD 行以展示实体
     * 克隆源事实（grantSource=INHERITED，沿旧 expandByPresentMode 口径）。
     * </p>
     */
    private List<GrantFact> responseRows(ResultDetails details) {
        List<GrantFact> kept = details.stageFacts().stream()
            .flatMap(stage -> stage.retainedAfterEvaluation().stream()).toList();
        List<PresentationEntry> presentation = details.presentation();
        if (presentation.isEmpty()) {
            return kept;
        }
        Map<Long, GrantFact> factsById = new LinkedHashMap<>();
        kept.forEach(f -> factsById.putIfAbsent(f.permissionId(), f));
        List<GrantFact> rows = new ArrayList<>(presentation.size());
        for (PresentationEntry entry : presentation) {
            GrantFact fact = factsById.get(entry.sourcePermissionId());
            if (fact == null) {
                continue;
            }
            if (entry.derivation() == PresentationEntry.Derivation.ORIGINAL) {
                rows.add(fact);
            } else {
                rows.add(new GrantFact(fact.permissionId(), fact.roleId(), fact.resourceType(),
                    entry.displayedEntityId(), fact.grantedBits(), fact.scopeAll(), fact.canGrant(),
                    fact.conditionId(), fact.hasCondition(), fact.dependOn(), GRANT_SOURCE_INHERITED));
            }
        }
        return rows;
    }

    // ===== queryScopes（GRANT_LIST＋父要求＋EVALUATE/ENFORCE＋RAW_AND_KEPT；四态组装=纯投影） =====

    /**
     * 数据范围查询（T-PERM-057 第六套形态收编口径延续：评估全进引擎，AppService 只留四态线格式组装）。
     * <p>
     * 条件评估、条目互斥、depend_on 子权限过滤（主资源上下文一等入参）全部由新引擎
     * GRANT_LIST 管线执行；四态分组交给 {@link ScopeCoverageProjector} 纯投影（只消费
     * 完整 raw/kept 结果与描述，不重跑判定）。父对象存在性由外层预检查返回
     * OBJECT_KEY_NOT_FOUND（设计 §6.2）；NO_ROLE/父整集合门禁失败映射既有整体原因
     * NO_PERMISSION 与全 DENIED 分组形状；matchedParentOperations 取
     * ResultDetails.parentCheck.matchedOperationCodes（已执行父判断的命中摘要，不重跑）。
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

        // 退化元素归一（沿 T-PERM-089 适配层归一口径）：父操作集过滤 null 元素后为空＝
        // 父判定必不命中，语义等价旧引擎空父操作集下的 PARENT_NO_PERMISSION 整表拒绝，
        // 不进引擎（避免新引擎父操作集空集的结构拒绝放大为 500）
        Set<String> parentOperations = req.parentOperationCodes() == null ? Set.of()
            : Set.copyOf(req.parentOperationCodes().stream().filter(Objects::nonNull).collect(Collectors.toSet()));
        if (parentOperations.isEmpty()) {
            return new QueryScopesResp("NO_PERMISSION", List.of(), buildDeniedGroups(req), 60);
        }

        // 范围要求＝类型×操作全组合（保持请求序）；null 元素组合不进引擎（旧引擎解析必落空=DENIED 同形）
        List<TypeOperation> requirements = new ArrayList<>();
        List<TypeOperation> degeneratePairs = new ArrayList<>();
        for (String typeCode : req.scopeResourceTypeCodes()) {
            for (String opCode : req.scopeOperationCodes()) {
                if (typeCode == null || typeCode.isBlank() || opCode == null || opCode.isBlank()) {
                    degeneratePairs.add(new TypeOperation(typeCode, opCode));
                } else {
                    requirements.add(new TypeOperation(typeCode, opCode));
                }
            }
        }
        if (requirements.isEmpty()) {
            return new QueryScopesResp(null, List.of(), degeneratePairs.stream()
                .map(key -> new ScopeGroup(key.resourceTypeCode(), key.operationCode(), ScopeMode.DENIED, List.of()))
                .toList(), 60);
        }

        OutputSpec output = new OutputSpec(FactDetail.RAW_AND_KEPT, true, true, false,
            PresentationExpansion.NONE, Set.copyOf(requirements), false);
        ParentRequirement parent = new ParentRequirement(req.parentResourceTypeCode(),
            new ByCode(req.parentResourceCode(), req.parentCodeType(), null), parentOperations);
        QueryItem item = QueryItem.grantListFacts("scopes", parent, Evaluation.full(), output);
        GrantSetResult result = (GrantSetResult) queryEngine.execute(new QueryRequest(tenantId, new User(userId),
            CallerContext.fromCallerMap(req.context()), ReadOptions.defaults(), List.of(item))).orderedResults().get(0);

        if (result.collectionStatus() == GrantSetResult.CollectionStatus.NO_ROLE
            || result.collectionStatus() == GrantSetResult.CollectionStatus.PARENT_DENIED) {
            // 仅「父资源无任何匹配权限 / 无角色」整表拒绝；条件评估清空与 depend_on 清空
            // 不属此列——raw 事实源仍可四态分态（有覆盖→EMPTY），勿压成 DENIED
            // （grok 外评 P1：评估摘光范围类型时整表拒绝会让业务方把 EMPTY 误当 403）
            List<ScopeGroup> deniedGroups = buildDeniedGroups(req);
            return new QueryScopesResp("NO_PERMISSION", List.of(), deniedGroups, 60);
        }

        List<ScopeGroup> scopeGroups = mergeDegenerateGroups(
            ScopeCoverageProjector.project(result, requirements), degeneratePairs, req);

        // T-API-002：父权限 id 集合仅内部用于 DEPENDENT 子权限过滤，不再进线格式
        return new QueryScopesResp(
            null,
            new ArrayList<>(result.details().parentCheck().matchedOperationCodes()),
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

    /** 退化（null/空白元素）组合按请求序回插 DENIED 分组——旧引擎对这些组合解析必落空出 DENIED。 */
    private List<ScopeGroup> mergeDegenerateGroups(List<ScopeGroup> projected, List<TypeOperation> degeneratePairs,
                                                   QueryScopesReq req) {
        if (degeneratePairs.isEmpty()) {
            return projected;
        }
        Map<TypeOperation, ScopeGroup> projectedByKey = new LinkedHashMap<>();
        projected.forEach(group -> projectedByKey.putIfAbsent(
            new TypeOperation(group.resourceTypeCode(), group.operationCode()), group));
        Map<TypeOperation, ScopeGroup> degenerateByKey = new LinkedHashMap<>();
        degeneratePairs.forEach(key -> degenerateByKey.put(key,
            new ScopeGroup(key.resourceTypeCode(), key.operationCode(), ScopeMode.DENIED, List.of())));
        List<ScopeGroup> merged = new ArrayList<>();
        for (String typeCode : req.scopeResourceTypeCodes()) {
            for (String opCode : req.scopeOperationCodes()) {
                TypeOperation key = new TypeOperation(typeCode, opCode);
                ScopeGroup group = degenerateByKey.containsKey(key)
                    ? degenerateByKey.get(key) : projectedByKey.get(key);
                if (group != null) {
                    merged.add(group);
                }
            }
        }
        return merged;
    }

}
