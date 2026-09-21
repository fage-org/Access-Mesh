package cn.ac.fage.accessmesh.access.grant.service.domain;

import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.engine.util.OperationPermissionUtils;
import cn.ac.fage.accessmesh.access.infrastructure.util.SqlBatches;
import cn.ac.fage.accessmesh.access.grant.dto.req.ApplyGrantPlanReq;
import cn.ac.fage.accessmesh.access.grant.dto.resp.GrantPlanPreviewResp;
import cn.ac.fage.accessmesh.access.grant.dto.resp.GrantPlanPreviewResp.ConditionRef;
import cn.ac.fage.accessmesh.access.grant.dto.resp.GrantPlanPreviewResp.FactElement;
import cn.ac.fage.accessmesh.access.grant.dto.resp.GrantPlanPreviewResp.FactKey;
import cn.ac.fage.accessmesh.access.grant.dto.resp.GrantPlanPreviewResp.ResourceKey;
import cn.ac.fage.accessmesh.access.grant.dto.resp.GrantPlanPreviewResp.SeedRef;
import cn.ac.fage.accessmesh.access.grant.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.access.grant.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.grant.service.domain.AutoGrantDerivation.DependencyEdge;
import cn.ac.fage.accessmesh.access.grant.service.domain.AutoGrantDerivation.Fact;
import cn.ac.fage.accessmesh.access.grant.service.domain.PermissionGrantPlanDomainService.PlannedGrantPlan;
import cn.ac.fage.accessmesh.access.resource.dto.resp.AutoGrantExplainResp;
import cn.ac.fage.accessmesh.access.resource.dto.resp.AutoGrantExplainResp.DeclarationRef;
import cn.ac.fage.accessmesh.access.resource.entity.PermissionDependencyDeclaration;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.resource.service.domain.DependencyCompilationDomainService;
import cn.ac.fage.accessmesh.access.resource.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.access.rule.entity.PermissionCondition;
import cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConditionDomainService;
import cn.ac.fage.accessmesh.access.type.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.type.service.domain.OperationPermissionDomainService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 自动授权来源解释与授撤影响预览的共享计算领域服务（T-PERM-073，设计 §11/§12）。
 * <p>
 * 消费 072 共享推导核心（{@link AutoGrantDerivation}）的 desired 事实与直接前驱共享 DAG：
 * explain 输出共享逻辑 DAG（节点=完整事实键、边=直接推导关系+声明引用、desired/actual 漂移标识、
 * maxDepth/maxNodes/maxEdges 只截断输出不影响完整推导）；preview 以三次推导（当前种子 beforeDesired、
 * 计划假想种子 afterDesired、被撤/被改旧种子 affected）求 added/removed/retained 与根显式来源归属。
 * 两入口共用同一事实键渲染管线（资源业务键/操作码/条件身份）。
 * </p>
 * <p>
 * 硬契约：无缓存直读、不声明独立事务（AppService 只读一致视图事务包裹）、不写任何数据；
 * preview 经 {@link PermissionGrantPlanDomainService#prepare} 与保存同源校验（不写 INLINE），
 * 禁止另写宽松预览算法。能力包 Mapper 边界不变——编译图/声明经
 * {@link DependencyCompilationDomainService} 读取。
 * </p>
 */
@Service
public class AutoGrantInsightDomainService {

    /** 种子来源渲染序：现有行按 permissionId 数值在前，计划条目按 requestItemRef 码点在后。 */
    private static final Comparator<SeedRef> SEED_REF_ORDER = Comparator
        .comparing((SeedRef ref) -> ref.permissionId() == null ? Long.MAX_VALUE : ref.permissionId())
        .thenComparing(ref -> ref.requestItemRef() == null ? "" : ref.requestItemRef());

    /** FactKey 渲染元组序（契约 §11.4.1：ResourceKey 各字段 → operationCode → ConditionRef(kind/id/itemRef)）。 */
    static final Comparator<FactKey> FACT_KEY_ORDER = Comparator
        .comparing((FactKey key) -> key.resource().resourceTypeCode(), Comparator.nullsFirst(Comparator.naturalOrder()))
        .thenComparing(key -> key.resource().resourceCode(), Comparator.nullsFirst(Comparator.naturalOrder()))
        .thenComparing(key -> key.resource().codeType(), Comparator.nullsFirst(Comparator.naturalOrder()))
        .thenComparing(key -> key.operationCode(), Comparator.nullsFirst(Comparator.naturalOrder()))
        .thenComparing(key -> key.conditionRef().kind(), Comparator.nullsFirst(Comparator.naturalOrder()))
        .thenComparing(key -> key.conditionRef().conditionId(), Comparator.nullsFirst(Comparator.naturalOrder()))
        .thenComparing(key -> key.conditionRef().requestItemRef(), Comparator.nullsFirst(Comparator.naturalOrder()));

    private final RoleResourcePermissionMapper rolePermissionMapper;
    private final AutoGrantDerivation derivation;
    private final DependencyCompilationDomainService compilation;
    private final ResourceEntityDomainService resourceEntities;
    private final OperationPermissionDomainService operations;
    private final PermissionConditionDomainService conditionDomainService;
    private final TypeResolutionService typeResolutionService;
    private final PermissionGrantPlanDomainService planDomainService;

    public AutoGrantInsightDomainService(RoleResourcePermissionMapper rolePermissionMapper,
            AutoGrantDerivation derivation, DependencyCompilationDomainService compilation,
            ResourceEntityDomainService resourceEntities, OperationPermissionDomainService operations,
            PermissionConditionDomainService conditionDomainService, TypeResolutionService typeResolutionService,
            PermissionGrantPlanDomainService planDomainService) {
        this.rolePermissionMapper = rolePermissionMapper;
        this.derivation = derivation;
        this.compilation = compilation;
        this.resourceEntities = resourceEntities;
        this.operations = operations;
        this.conditionDomainService = conditionDomainService;
        this.typeResolutionService = typeResolutionService;
        this.planDomainService = planDomainService;
    }

    // ===== 嵌套视图与输出类型 =====

    /** 租户级编译图快照（边 + 端点类型 + 操作定义 + 声明行；explain/preview/对账共用装载）。 */
    public record TenantGraph(List<DependencyEdge> edges,
                              Map<Long, Integer> resourceTypeByEntity,
                              List<OperationPermission> operations,
                              List<PermissionDependencyDeclaration> declarations) {}

    /** 事实键渲染上下文（资源行/类型值索引/类型编码/条件行）。 */
    record RenderContext(Map<Long, ResourceEntity> resourceById,
                         Map<Long, Integer> resourceTypeByEntity,
                         Map<Integer, String> resourceTypeCodeByValue,
                         Map<Long, PermissionCondition> conditionById) {}

    /** 单角色自动授权一致视图：行事实 + 编译图 + 渲染上下文。 */
    public record AutoGrantView(List<RoleResourcePermission> rows,
                                List<RoleResourcePermission> seeds,
                                Map<Fact, List<RoleResourcePermission>> autoDepByFact,
                                TenantGraph graph,
                                RenderContext render) {}

    /** explain 目标事实（AppService 解析业务键后传入；conditionId=null 表示无条件变体）。 */
    public record ExplainTargetFact(Long resourceEntityId, long operationBit, Long conditionId) {}

    /** explain 输出限额（契约 §12.3.1：只限制输出，不影响完整推导）。 */
    public record ExplainLimits(int maxDepth, int maxNodes, int maxEdges) {}

    /** 预览种子来源：现有行（permissionId）或计划新建条目（requestItemRef），严格二选一。 */
    record SeedSource(Long permissionId, String requestItemRef) {

        static SeedSource existing(RoleResourcePermission row) {
            return new SeedSource(row.getId(), null);
        }

        static SeedSource planned(String requestItemRef) {
            return new SeedSource(null, requestItemRef);
        }

        SeedRef toRef() {
            return new SeedRef(permissionId, requestItemRef);
        }

        boolean isPlaceholder() {
            return permissionId == null && requestItemRef == null;
        }
    }

    // ===== 一致视图装载 =====

    /** 装载租户编译图（边 + 端点类型 + 操作定义 + 声明行），分批防参数上限。 */
    public TenantGraph loadTenantGraph(Long tenantId) {
        List<DependencyEdge> edges = compilation.loadCompiledEdges(tenantId).stream()
            .map(e -> new DependencyEdge(e.sourceId(), e.targetId(), e.sourceOperationBits(), e.requiredOperationBits()))
            .toList();
        Map<Long, Integer> resourceTypeByEntity = new HashMap<>();
        Set<Long> endpoints = new LinkedHashSet<>();
        edges.forEach(edge -> {
            endpoints.add(edge.sourceId());
            endpoints.add(edge.targetId());
        });
        List<Long> endpointList = new ArrayList<>(endpoints);
        SqlBatches.forEach(endpointList, batch -> {
            for (var entity : resourceEntities.selectValidByIds(tenantId, new LinkedHashSet<>(batch))) {
                resourceTypeByEntity.put(entity.getId(), entity.getResourceType());
            }
        });
        List<OperationPermission> operationRows = resourceTypeByEntity.isEmpty() ? List.of()
            : operations.selectByTenantAndResourceTypes(tenantId, new HashSet<>(resourceTypeByEntity.values()));
        return new TenantGraph(edges, resourceTypeByEntity, operationRows, compilation.loadDeclarations(tenantId));
    }

    /**
     * 装载单角色自动授权视图（explain/preview 输入）。无缓存直读，调用方持有只读一致事务。
     * <p>图/操作始终装载：preview 对当前零授权角色新增种子时仍需完整推导图（物化器的
     * 「空角色跳图」优化不适用于计划假想推导）；空角色 explain 全集自然为空。</p>
     */
    public AutoGrantView loadAutoGrantView(Long tenantId, Long roleId) {
        List<RoleResourcePermission> rows = rolePermissionMapper.selectValidByRoleId(tenantId, roleId);
        List<RoleResourcePermission> seeds = rows.stream().filter(AutoGrantFacts::isSeed).toList();
        Map<Fact, List<RoleResourcePermission>> autoDepByFact = rows.stream()
            .filter(AutoGrantFacts::isAutoDep)
            .collect(Collectors.groupingBy(AutoGrantFacts::factOf,
                LinkedHashMap::new, Collectors.toList()));
        TenantGraph graph = loadTenantGraph(tenantId);
        return new AutoGrantView(rows, seeds, autoDepByFact, graph,
            buildRenderContext(tenantId, rows, graph));
    }

    private RenderContext buildRenderContext(Long tenantId, List<RoleResourcePermission> rows, TenantGraph graph) {
        // 渲染面：行资源 ∪ 图端点资源（分批装载，防御软删资源缺行）
        Set<Long> involved = new LinkedHashSet<>();
        rows.forEach(row -> {
            if (row.getResourceEntityId() != null) involved.add(row.getResourceEntityId());
        });
        graph.resourceTypeByEntity().keySet().forEach(involved::add);
        Map<Long, ResourceEntity> resourceById = new HashMap<>();
        List<Long> involvedList = new ArrayList<>(involved);
        SqlBatches.forEach(involvedList, batch -> {
            for (var entity : resourceEntities.selectValidByIds(tenantId, new LinkedHashSet<>(batch))) {
                resourceById.put(entity.getId(), entity);
            }
        });
        // 类型值全集 = 行携带（资源软删防御）∪ 资源行 ∪ 图端点；一次反查类型编码
        Map<Long, Integer> typeByEntity = new HashMap<>(graph.resourceTypeByEntity());
        rows.forEach(row -> {
            if (row.getResourceEntityId() != null && row.getResourceType() != null) {
                typeByEntity.put(row.getResourceEntityId(), row.getResourceType());
            }
        });
        Set<Integer> typeValues = new LinkedHashSet<>(typeByEntity.values());
        Map<Integer, String> typeCodeByValue = typeValues.isEmpty() ? Map.of()
            : typeResolutionService.batchResolveTypeCodes(tenantId, "resource_type", typeValues);

        Set<Long> conditionIds = rows.stream().map(RoleResourcePermission::getConditionId)
            .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, PermissionCondition> conditionById = conditionIds.isEmpty() ? Map.of()
            : conditionDomainService.selectValidConditionsByIds(tenantId, conditionIds).stream()
                .collect(Collectors.toMap(PermissionCondition::getId, Function.identity()));
        return new RenderContext(resourceById, typeByEntity, typeCodeByValue, conditionById);
    }

    // ===== explain：共享 DAG 计算 =====

    /**
     * explain 主计算：一致视图 → 共享推导 → 节点选择（target 反向闭包 / 全集 BFS 深度限额）→ 渲染输出。
     * <p>target 非空时从目标反向遍历直接来源（目标自身恒在闭包内，即使孤立）；无 target 时全集 =
     * 推导图（种子 ∪ desired）∪ 未被 desired 支持的孤立 actual 节点，显式根与孤立节点共同作为
     * BFS 起点。driftDetected 按全角色 desired/actual 比对（不随 target 收窄——漂移是角色级属性）。</p>
     */
    public AutoGrantExplainResp explain(Long tenantId, Long roleId, ExplainTargetFact target, ExplainLimits limits) {
        AutoGrantView view = loadAutoGrantView(tenantId, roleId);
        List<Fact> seedFacts = view.seeds().stream().map(AutoGrantFacts::factOf).toList();
        AutoGrantDerivation.Result derived = derivation.derive(seedFacts,
            view.graph().resourceTypeByEntity(), view.graph().edges(), view.graph().operations());
        Set<Fact> desired = new HashSet<>(derived.desiredFacts());
        Map<Fact, Set<Fact>> predecessors = derived.directPredecessors();

        // 全集：无 target = 推导图 ∪ 孤立 actual；有 target = 反向闭包。
        // 收窄口径（2026-09-21 用户定案 B）：无 target 时仅「参与推导的种子」入图——
        // 未命中任何依赖边的显式授权不进解释视图（完整授权清单走授权页 list），
        // 防大授权量角色的孤点种子淹没推导链并吃掉输出预算
        Set<Fact> participants = new TreeSet<>(predecessors.keySet());
        predecessors.values().forEach(participants::addAll);
        Set<Fact> participatingSeeds = new TreeSet<>(seedFacts);
        participatingSeeds.retainAll(participants);
        Set<Fact> universe = new TreeSet<>();
        if (target == null) {
            universe.addAll(participants);
            view.autoDepByFact().keySet().stream()
                .filter(fact -> !desired.contains(fact))
                .forEach(universe::add);
        } else {
            backwardClosure(new Fact(target.resourceEntityId(), target.operationBit(), target.conditionId()),
                predecessors, universe);
        }

        // 输出节点：深度限额内（无 target 从根前向 / 有 target 从目标反向）→ 逻辑键排序取前 maxNodes
        Set<Fact> depthEligible = depthLimitedNodes(target, universe, participatingSeeds, predecessors,
            desired, view.autoDepByFact().keySet(), limits.maxDepth());
        List<Fact> outputNodes = depthEligible.stream().sorted().limit(limits.maxNodes()).toList();
        if (target != null) {
            // target 模式目标保底入选（2026-09-21 贴回外评拍板）：截断只挤祖先，不把用户
            // 查询的目标本身挤出输出——目标恒在反向闭包与深度候选内（闭包起点即目标），
            // 未入选只可能因名额被祖先占满，替换最后一个名额
            Fact targetFact = new Fact(target.resourceEntityId(), target.operationBit(), target.conditionId());
            if (depthEligible.contains(targetFact) && !outputNodes.contains(targetFact)) {
                List<Fact> pinned = new ArrayList<>(outputNodes);
                pinned.remove(pinned.size() - 1);
                pinned.add(targetFact);
                outputNodes = List.copyOf(pinned);
            }
        }
        boolean truncated = outputNodes.size() < universe.size();

        // 边：两端都已输出的直接推导边，按起点/终点逻辑键排序取前 maxEdges；总数=全集内边数
        List<Map.Entry<Fact, Fact>> candidateEdges = new ArrayList<>();
        long totalEdgeCount = 0;
        for (Map.Entry<Fact, Set<Fact>> entry : predecessors.entrySet()) {
            if (!universe.contains(entry.getKey())) continue;
            for (Fact pred : entry.getValue()) {
                if (!universe.contains(pred)) continue;
                totalEdgeCount++;
                if (outputNodes.contains(entry.getKey()) && outputNodes.contains(pred)) {
                    candidateEdges.add(Map.entry(pred, entry.getKey()));
                }
            }
        }
        candidateEdges.sort((left, right) -> {
            int byKey = left.getKey().compareTo(right.getKey());
            return byKey != 0 ? byKey : left.getValue().compareTo(right.getValue());
        });
        List<Map.Entry<Fact, Fact>> outputEdges = candidateEdges.stream().limit(limits.maxEdges()).toList();
        truncated = truncated || outputEdges.size() < candidateEdges.size();

        return renderExplain(view, derived, universe, outputNodes, outputEdges,
            totalEdgeCount, truncated, seedFacts);
    }

    /** 反向闭包：目标 + 沿直接前驱可达的全部祖先（孤立目标闭包=自身）。 */
    private void backwardClosure(Fact target, Map<Fact, Set<Fact>> predecessors, Set<Fact> closure) {
        Deque<Fact> pending = new ArrayDeque<>();
        if (closure.add(target)) pending.push(target);
        while (!pending.isEmpty()) {
            Fact current = pending.pop();
            for (Fact pred : predecessors.getOrDefault(current, Set.of())) {
                if (closure.add(pred)) pending.push(pred);
            }
        }
    }

    /**
     * 深度限额内节点：无 target 从根（种子 ∪ 孤立 actual）沿推导方向前向；有 target 从目标反向。
     * 深度=min 跳数（BFS 天然保证）；全集聚合防重复入队。
     */
    private Set<Fact> depthLimitedNodes(ExplainTargetFact target, Set<Fact> universe, Set<Fact> participatingSeeds,
                                        Map<Fact, Set<Fact>> predecessors, Set<Fact> desired,
                                        Set<Fact> actualFacts, int maxDepth) {
        Map<Fact, List<Fact>> forward = new HashMap<>();
        for (Map.Entry<Fact, Set<Fact>> entry : predecessors.entrySet()) {
            for (Fact pred : entry.getValue()) {
                forward.computeIfAbsent(pred, key -> new ArrayList<>()).add(entry.getKey());
            }
        }
        Set<Fact> roots = new TreeSet<>();
        if (target == null) {
            roots.addAll(participatingSeeds);
            actualFacts.stream().filter(fact -> !desired.contains(fact)).forEach(roots::add);
        } else {
            roots.add(new Fact(target.resourceEntityId(), target.operationBit(), target.conditionId()));
        }
        Set<Fact> eligible = new TreeSet<>();
        Deque<Fact> frontier = new ArrayDeque<>();
        Set<Fact> visited = new HashSet<>();
        for (Fact root : roots) {
            if (universe.contains(root) && visited.add(root)) {
                frontier.offer(root);
                eligible.add(root);
            }
        }
        int depth = 0;
        while (!frontier.isEmpty() && depth < maxDepth) {
            List<Fact> next = new ArrayList<>();
            for (Fact current : frontier) {
                for (Fact successor : target == null
                        ? forward.getOrDefault(current, List.of())
                        : predecessors.getOrDefault(current, Set.of())) {
                    if (universe.contains(successor) && visited.add(successor)) {
                        next.add(successor);
                    }
                }
            }
            eligible.addAll(next);
            frontier = new ArrayDeque<>(next);
            depth++;
        }
        return eligible;
    }

    /** explain 渲染：nodeKey 按输出节点 FactKey 元组排序分配；边引用节点键；声明引用排序去重。 */
    private AutoGrantExplainResp renderExplain(AutoGrantView view, AutoGrantDerivation.Result derived,
            Set<Fact> universe, List<Fact> outputNodes, List<Map.Entry<Fact, Fact>> outputEdges,
            long totalEdgeCount, boolean truncated, List<Fact> seedFacts) {
        Set<Fact> desired = new HashSet<>(derived.desiredFacts());
        Set<Fact> seedFactSet = new HashSet<>(seedFacts);
        // 操作有效位索引（触发判定与推导核心同口径：NULL 触发=任意；定义缺失回退位本身）
        Map<String, Long> effectiveBits = new HashMap<>();
        for (OperationPermission operation : view.graph().operations()) {
            if (operation.getResourceType() != null && operation.getBinaryBit() != null) {
                effectiveBits.put(operationIndexKey(operation.getResourceType(), operation.getBinaryBit()),
                    OperationPermissionUtils.effectiveBits(operation));
            }
        }
        // nodeKey：输出节点按渲染元组排序分配 n1..（响应内引用；全集总数另报）
        Map<Fact, FactKey> rendered = new HashMap<>();
        for (Fact fact : outputNodes) {
            rendered.put(fact, renderFact(view, null, fact));
        }
        List<Fact> ordered = new ArrayList<>(outputNodes);
        ordered.sort(Comparator.comparing(rendered::get, FACT_KEY_ORDER));
        Map<Fact, String> nodeKeys = new HashMap<>();
        List<AutoGrantExplainResp.Node> nodes = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            Fact fact = ordered.get(index);
            String nodeKey = "n" + (index + 1);
            nodeKeys.put(fact, nodeKey);
            List<SeedRef> seedRefs = view.seeds().stream()
                .filter(seed -> AutoGrantFacts.factOf(seed).equals(fact))
                .map(seed -> SeedSource.existing(seed).toRef()).toList();
            nodes.add(new AutoGrantExplainResp.Node(nodeKey, rendered.get(fact), seedFactSet.contains(fact),
                seedRefs, desired.contains(fact),
                view.autoDepByFact().getOrDefault(fact, List.of()).stream()
                    .map(RoleResourcePermission::getId).toList()));
        }
        // 声明索引：编译键（源实体+目标实体+COALESCE 触发位）→ RESOLVED 声明
        Map<String, List<PermissionDependencyDeclaration>> declarationsByCompileKey = new HashMap<>();
        for (PermissionDependencyDeclaration declaration : view.graph().declarations()) {
            if (!PermissionDependencyDeclaration.COMPILE_STATUS_RESOLVED.equals(declaration.getCompileStatus())
                || declaration.getSourceResourceId() == null || declaration.getTargetResourceId() == null) {
                continue;
            }
            declarationsByCompileKey.computeIfAbsent(compileKey(declaration.getSourceResourceId(),
                declaration.getTargetResourceId(), declaration.getSourceOperationBits()),
                key -> new ArrayList<>()).add(declaration);
        }
        List<AutoGrantExplainResp.Edge> edges = new ArrayList<>(outputEdges.size());
        for (Map.Entry<Fact, Fact> edge : outputEdges) {
            List<PermissionDependencyDeclaration> refs = new ArrayList<>();
            for (DependencyEdge graphEdge : view.graph().edges()) {
                if (!Objects.equals(graphEdge.sourceId(), edge.getKey().resourceEntityId())
                    || !Objects.equals(graphEdge.targetId(), edge.getValue().resourceEntityId())) {
                    continue;
                }
                if (graphEdge.sourceOperationBits() == null || triggers(edge.getKey(),
                        graphEdge.sourceOperationBits(), view, effectiveBits)) {
                    // 同编译键（源+目标+触发）多条声明的目标位被编译器 OR 聚合到一条边——
                    // 声明引用必须过滤到「声明了本边目标操作位」的原始声明，否则同键的
                    // READ/DELETE 声明会同时挂到两条推导边上（来源解释失真）；编译边层过滤
                    // 无效（聚合后位已合并），只能逐条原始声明按位判定
                    long targetOperationBit = edge.getValue().operationBit();
                    refs.addAll(declarationsByCompileKey.getOrDefault(
                        compileKey(graphEdge.sourceId(), graphEdge.targetId(), graphEdge.sourceOperationBits()),
                        List.of()).stream()
                        .filter(declaration -> declaration.getRequiredOperationBits() != null
                            && (declaration.getRequiredOperationBits() & targetOperationBit) != 0L)
                        .toList());
                }
            }
            refs.sort(Comparator.comparing(PermissionDependencyDeclaration::getId));
            edges.add(new AutoGrantExplainResp.Edge(nodeKeys.get(edge.getKey()), nodeKeys.get(edge.getValue()),
                renderOperationCode(view, edge.getKey()),
                refs.stream().map(ref -> new DeclarationRef(ref.getId(), ref.getDeclarationKey(),
                        ref.getSourceService())).distinct().toList()));
        }
        boolean driftDetected = !desired.equals(view.autoDepByFact().keySet());
        return new AutoGrantExplainResp(LocalDateTime.now(), nodes, edges,
            universe.size(), totalEdgeCount, truncated, driftDetected);
    }

    /** 触发判定（与推导核心同口径）：源事实操作有效位与触发位按位与命中。 */
    private boolean triggers(Fact from, long triggerBits, AutoGrantView view, Map<String, Long> effectiveBits) {
        Integer typeValue = view.render().resourceTypeByEntity().get(from.resourceEntityId());
        if (typeValue == null) {
            return (from.operationBit() & triggerBits) != 0L;
        }
        Long effective = effectiveBits.get(operationIndexKey(typeValue, from.operationBit()));
        long bits = effective == null ? from.operationBit() : effective;
        return (bits & triggerBits) != 0L;
    }

    private static String operationIndexKey(Integer resourceType, long binaryBit) {
        return resourceType + ":" + binaryBit;
    }

    // ===== preview：授撤影响计算 =====

    /**
     * 授撤影响预览主计算（契约 §11.4.1）：prepare（与保存同源校验、纯只读）→ 三次共享推导 →
     * added/removed/retained + 根显式来源 + 漂移标识 + maxItems 截断。
     * <p>种子口径与物化器一致（§6.1）；affectedOld = 被删种子 + 被改种子的旧身份（仅 canGrant
     * 变更不改事实身份、不算受影响）。retained = affectedOld 可推导 ∩ afterDesired（受本计划
     * 影响但仍有其他显式来源支持）。driftDetected = beforeDesired 与 actual AUTO_DEP 已有漂移。</p>
     */
    public GrantPlanPreviewResp previewGrantPlan(Long tenantId, Long subjectId, Long roleId, String domainCode,
                                                 ApplyGrantPlanReq.GrantPlan plan, int maxItems) {
        AutoGrantView view = loadAutoGrantView(tenantId, roleId);
        PlannedGrantPlan planned = planDomainService.prepare(tenantId, subjectId, roleId, domainCode, plan);
        Map<Long, RoleResourcePermission> beforeById = view.rows().stream()
            .collect(Collectors.toMap(RoleResourcePermission::getId, Function.identity()));

        // afterSeeds = 保留行（未删未改）+ 更新后行 + 合种子形态的计划新建行
        Set<Long> removedIds = new HashSet<>(planned.removes());
        Set<Long> updatedIds = planned.updates().stream()
            .map(RoleResourcePermission::getId).collect(Collectors.toSet());
        Map<Fact, List<SeedSource>> afterSeedSources = new TreeMap<>();
        for (RoleResourcePermission row : view.seeds()) {
            if (removedIds.contains(row.getId()) || updatedIds.contains(row.getId())) continue;
            afterSeedSources.computeIfAbsent(AutoGrantFacts.factOf(row), key -> new ArrayList<>())
                .add(SeedSource.existing(row));
        }
        for (RoleResourcePermission row : planned.updates()) {
            if (AutoGrantFacts.isSeed(row)) {
                afterSeedSources.computeIfAbsent(AutoGrantFacts.factOf(row), key -> new ArrayList<>())
                    .add(SeedSource.existing(row));
            }
        }
        for (int index = 0; index < planned.creates().size(); index++) {
            RoleResourcePermission permission = planned.creates().get(index).permission();
            if (AutoGrantFacts.isSeed(permission)) {
                afterSeedSources.computeIfAbsent(AutoGrantFacts.factOf(permission), key -> new ArrayList<>())
                    .add(SeedSource.planned(planned.createItemRefs().get(index)));
            }
        }

        // affectedOld = 被删种子 + 被改种子的旧身份
        Set<Fact> affectedOldFacts = new LinkedHashSet<>();
        for (Long removedId : removedIds) {
            RoleResourcePermission row = beforeById.get(removedId);
            if (row != null && AutoGrantFacts.isSeed(row)) affectedOldFacts.add(AutoGrantFacts.factOf(row));
        }
        for (RoleResourcePermission updated : planned.updates()) {
            RoleResourcePermission before = beforeById.get(updated.getId());
            if (before != null && AutoGrantFacts.isSeed(before) && !AutoGrantFacts.factOf(before).equals(AutoGrantFacts.factOf(updated))) {
                affectedOldFacts.add(AutoGrantFacts.factOf(before));
            }
        }

        // 类型索引扩展：计划新建行资源类型并入（推导触发判定需要；行携带类型，无需再查库）
        Map<Long, Integer> graphTypes = new HashMap<>(view.graph().resourceTypeByEntity());
        for (var create : planned.creates()) {
            RoleResourcePermission permission = create.permission();
            if (permission.getResourceEntityId() != null && permission.getResourceType() != null) {
                graphTypes.putIfAbsent(permission.getResourceEntityId(), permission.getResourceType());
            }
        }
        for (RoleResourcePermission updated : planned.updates()) {
            if (updated.getResourceEntityId() != null && updated.getResourceType() != null) {
                graphTypes.putIfAbsent(updated.getResourceEntityId(), updated.getResourceType());
            }
        }

        List<Fact> beforeSeedFacts = view.seeds().stream().map(AutoGrantFacts::factOf).toList();
        // 三次推导（before/after/affected）共享同一张图的只读索引
        AutoGrantDerivation.PreparedGraph preparedGraph = derivation.prepareGraph(
            view.graph().edges(), view.graph().operations());
        AutoGrantDerivation.Result before = derivation.derive(beforeSeedFacts, graphTypes, preparedGraph);
        AutoGrantDerivation.Result after = derivation.derive(new ArrayList<>(afterSeedSources.keySet()),
            graphTypes, preparedGraph);
        AutoGrantDerivation.Result affected = derivation.derive(new ArrayList<>(affectedOldFacts),
            graphTypes, preparedGraph);

        Set<Fact> beforeDesired = new HashSet<>(before.desiredFacts());
        Set<Fact> afterDesired = new HashSet<>(after.desiredFacts());
        List<Fact> added = afterDesired.stream().filter(fact -> !beforeDesired.contains(fact)).toList();
        List<Fact> removed = beforeDesired.stream().filter(fact -> !afterDesired.contains(fact)).toList();
        Set<Fact> retainedSet = new TreeSet<>();
        for (Fact fact : affected.desiredFacts()) {
            if (afterDesired.contains(fact)) retainedSet.add(fact);
        }

        // 渲染上下文扩展：计划新建行资源 + 计划解析条件并入
        RenderContext extendedRender = extendRenderContext(tenantId, view, planned);
        AutoGrantView afterView = new AutoGrantView(view.rows(), view.seeds(), view.autoDepByFact(),
            view.graph(), extendedRender);
        Map<Fact, List<SeedSource>> beforeSeedSources = view.seeds().stream().collect(Collectors.groupingBy(
            AutoGrantFacts::factOf,
            Collectors.mapping(seed -> SeedSource.existing(seed), Collectors.toList())));

        // 根显式来源归属：added/retained 用 after-DAG（存续来源）、removed 用 before-DAG（旧根）
        Map<Fact, Set<SeedSource>> rootsAfter = rootSupport(after, afterSeedSources);
        Map<Fact, Set<SeedSource>> rootsBefore = rootSupport(before, beforeSeedSources);

        List<FactElement> removedElements = sortedByFactKey(removed.stream()
            .map(fact -> renderElement(afterView, planned, fact, rootsBefore, beforeSeedSources)).toList());
        List<FactElement> addedElements = sortedByFactKey(added.stream()
            .map(fact -> renderElement(afterView, planned, fact, rootsAfter, afterSeedSources)).toList());
        List<FactElement> retainedElements = sortedByFactKey(retainedSet.stream()
            .map(fact -> renderElement(afterView, planned, fact, rootsAfter, afterSeedSources)).toList());

        // 展示预算：removed → added → retained 顺序全局取前 maxItems
        long totalCount = addedElements.size() + removedElements.size() + retainedElements.size();
        List<FactElement> removedOut = truncatedCopy(removedElements, maxItems);
        int remaining = maxItems - removedOut.size();
        List<FactElement> addedOut = remaining <= 0 ? List.of() : truncatedCopy(addedElements, remaining);
        remaining -= addedOut.size();
        List<FactElement> retainedOut = remaining <= 0 ? List.of() : truncatedCopy(retainedElements, remaining);
        int outputCount = removedOut.size() + addedOut.size() + retainedOut.size();

        boolean driftDetected = !beforeDesired.equals(view.autoDepByFact().keySet());
        return new GrantPlanPreviewResp(true, LocalDateTime.now(), removedOut, addedOut, retainedOut,
            removedElements.size(), addedElements.size(), retainedElements.size(),
            totalCount, outputCount < totalCount, driftDetected);
    }

    /** 渲染上下文扩展：计划新建行引用的资源行 + 计划解析条件（引用轨/现绑定轨）并入。 */
    private RenderContext extendRenderContext(Long tenantId, AutoGrantView view, PlannedGrantPlan planned) {
        Set<Long> plannedResourceIds = planned.creates().stream()
            .map(create -> create.permission().getResourceEntityId())
            .filter(Objects::nonNull)
            .filter(id -> !view.render().resourceById().containsKey(id))
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, ResourceEntity> resourceById = new HashMap<>(view.render().resourceById());
        if (!plannedResourceIds.isEmpty()) {
            resourceById.putAll(resourceEntities.selectValidByIds(tenantId, plannedResourceIds).stream()
                .collect(Collectors.toMap(ResourceEntity::getId, Function.identity())));
        }
        Map<Long, Integer> typeByEntity = new HashMap<>(view.render().resourceTypeByEntity());
        resourceById.forEach((id, entity) -> typeByEntity.putIfAbsent(id, entity.getResourceType()));
        planned.creates().forEach(create -> {
            RoleResourcePermission permission = create.permission();
            if (permission.getResourceEntityId() != null && permission.getResourceType() != null) {
                typeByEntity.putIfAbsent(permission.getResourceEntityId(), permission.getResourceType());
            }
        });
        Set<Integer> typeValues = new LinkedHashSet<>(typeByEntity.values());
        Map<Integer, String> typeCodeByValue = typeValues.isEmpty() ? Map.of()
            : typeResolutionService.batchResolveTypeCodes(tenantId, "resource_type", typeValues);
        Map<Long, PermissionCondition> conditionById = new HashMap<>(view.render().conditionById());
        conditionById.putAll(planned.resolvedConditions());
        return new RenderContext(resourceById, typeByEntity, typeCodeByValue, conditionById);
    }

    /**
     * 事实的根显式来源集合：直接前驱 DAG 上的拓扑序传播（Kahn）——种子节点的根=自身来源，
     * 派生节点的根=全部直接前驱根的并集；不枚举完整路径组合。
     */
    private Map<Fact, Set<SeedSource>> rootSupport(AutoGrantDerivation.Result result,
                                                   Map<Fact, List<SeedSource>> seedSourcesByFact) {
        Map<Fact, Set<Fact>> predecessors = result.directPredecessors();
        Map<Fact, List<Fact>> forward = new HashMap<>();
        Map<Fact, Integer> inDegree = new HashMap<>();
        for (Map.Entry<Fact, Set<Fact>> entry : predecessors.entrySet()) {
            inDegree.putIfAbsent(entry.getKey(), 0);
            for (Fact pred : entry.getValue()) {
                forward.computeIfAbsent(pred, key -> new ArrayList<>()).add(entry.getKey());
                inDegree.merge(entry.getKey(), 1, Integer::sum);
                inDegree.putIfAbsent(pred, 0);
            }
        }
        Map<Fact, Set<SeedSource>> memo = new HashMap<>();
        Deque<Fact> ready = new ArrayDeque<>();
        inDegree.forEach((fact, degree) -> {
            if (degree == 0) ready.offer(fact);
        });
        Set<Fact> processed = new HashSet<>();
        while (!ready.isEmpty()) {
            Fact current = ready.poll();
            if (!processed.add(current)) continue;
            Set<SeedSource> roots = new TreeSet<>(Comparator.comparing((SeedSource source) ->
                source.permissionId() == null ? Long.MAX_VALUE : source.permissionId())
                .thenComparing(source -> source.requestItemRef() == null ? "" : source.requestItemRef()));
            roots.addAll(seedSourcesByFact.getOrDefault(current, List.of()));
            for (Fact pred : predecessors.getOrDefault(current, Set.of())) {
                roots.addAll(memo.getOrDefault(pred, Set.of()));
            }
            memo.put(current, roots);
            for (Fact successor : forward.getOrDefault(current, List.of())) {
                if (inDegree.merge(successor, -1, Integer::sum) == 0) {
                    ready.offer(successor);
                }
            }
        }
        return memo;
    }

    // ===== 共享渲染 =====

    /** 完整事实键渲染（业务键 + 操作码 + 条件身份；planned 非空时支持合成 ID/计划解析条件）。 */
    private FactKey renderFact(AutoGrantView view, PlannedGrantPlan planned, Fact fact) {
        ResourceEntity entity = view.render().resourceById().get(fact.resourceEntityId());
        Integer typeValue = view.render().resourceTypeByEntity().get(fact.resourceEntityId());
        String typeCode = typeValue == null ? null : view.render().resourceTypeCodeByValue().get(typeValue);
        ResourceKey resource = new ResourceKey(typeCode,
            entity == null ? null : entity.getCode(),
            entity == null ? null : (entity.getCodeType() == null ? "default" : entity.getCodeType()));
        return new FactKey(resource, renderOperationCode(view, fact),
            renderCondition(view, planned, fact.conditionId()));
    }

    private String renderOperationCode(AutoGrantView view, Fact fact) {
        Integer typeValue = view.render().resourceTypeByEntity().get(fact.resourceEntityId());
        if (typeValue == null) return null;
        return view.graph().operations().stream()
            .filter(op -> Objects.equals(op.getResourceType(), typeValue)
                && Objects.equals(op.getBinaryBit(), fact.operationBit()))
            .map(OperationPermission::getCode).findFirst().orElse(null);
    }

    private ConditionRef renderCondition(AutoGrantView view, PlannedGrantPlan planned, Long conditionId) {
        if (conditionId == null) return ConditionRef.none();
        if (conditionId < 0L) {
            return ConditionRef.previewInline(planned == null ? null
                : planned.syntheticConditionRefs().get(conditionId));
        }
        PermissionCondition condition = view.render().conditionById().get(conditionId);
        if (condition == null && planned != null) {
            condition = planned.resolvedConditions().get(conditionId);
        }
        return ConditionRef.existing(conditionId, condition == null ? null : condition.getCode());
    }

    private FactElement renderElement(AutoGrantView view, PlannedGrantPlan planned, Fact fact,
                                      Map<Fact, Set<SeedSource>> roots,
                                      Map<Fact, List<SeedSource>> directSeedIndex) {
        List<SeedRef> seeds = roots.getOrDefault(fact, Set.of()).stream()
            .filter(source -> !source.isPlaceholder())
            .map(SeedSource::toRef).sorted(SEED_REF_ORDER).toList();
        if (seeds.isEmpty()) {
            // 防御：根归属缺失（desired 必有种子根，不应发生）回退直接种子索引
            seeds = directSeedIndex.getOrDefault(fact, List.of()).stream()
                .map(SeedSource::toRef).sorted(SEED_REF_ORDER).toList();
        }
        return new FactElement(renderFact(view, planned, fact), seeds);
    }

    // ===== 口径与编译键 =====
    // 种子/事实口径唯一实现见 {@link AutoGrantFacts}（物化/解释/对账三方共用，§6.1）。

    /** 编译键（源实体 + 目标实体 + COALESCE 触发位，与编译器聚合口径一致）。 */
    static String compileKey(Long sourceId, Long targetId, Long sourceOperationBits) {
        return sourceId + ">" + targetId + "@" + (sourceOperationBits == null ? 0L : sourceOperationBits);
    }

    private static List<FactElement> sortedByFactKey(List<FactElement> elements) {
        return elements.stream().sorted(Comparator.comparing(FactElement::fact, FACT_KEY_ORDER)).toList();
    }

    private static List<FactElement> truncatedCopy(List<FactElement> elements, int limit) {
        return elements.size() <= limit ? elements : elements.subList(0, limit);
    }
}
