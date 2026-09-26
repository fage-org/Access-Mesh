package cn.ac.fage.accessmesh.access.engine.query;

import cn.ac.fage.accessmesh.access.engine.core.SubjectDomainService;
import cn.ac.fage.accessmesh.access.engine.query.EvaluationCoverage.*;
import cn.ac.fage.accessmesh.access.engine.util.OperationPermissionUtils;
import cn.ac.fage.accessmesh.access.resource.dto.req.ResourceResolveRequest;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConditionDomainService;
import cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConflictDomainService;
import cn.ac.fage.accessmesh.access.type.entity.OperationPermission;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 新查询唯一执行主体：主体解析→TYPE_GRANT→INSTANCE→最小事实输出。
 * 暂不注册 Bean，消费者迁移从 T-PERM-089 开始，终名随 T-PERM-092 确定。
 * 父要求/GRANT_LIST、复杂投影/TRACE、审计提交按 086～088 边界后续接入。
 * 不调用旧完整核心，所有请求状态随 RunState 释放；Clock 沿进程本地时钟语义。
 */
public final class QueryExecutionEngine {
    private final Clock clock;
    private final QueryReadSupport reads;
    private final SubjectDomainService subjects;
    private final PermissionConditionDomainService conditions;
    private final PermissionConflictDomainService conflicts;
    private final ResourceEntityMapper resourceMapper;

    QueryExecutionEngine(Clock clock, QueryReadSupport reads, SubjectDomainService subjects,
                         PermissionConditionDomainService conditions, PermissionConflictDomainService conflicts,
                         ResourceEntityMapper resourceMapper) {
        this.clock = Objects.requireNonNull(clock);
        this.reads = Objects.requireNonNull(reads);
        this.subjects = Objects.requireNonNull(subjects);
        this.conditions = Objects.requireNonNull(conditions);
        this.conflicts = Objects.requireNonNull(conflicts);
        this.resourceMapper = Objects.requireNonNull(resourceMapper);
    }

    /** 结构错误零权限 I/O 拒绝；技术故障保留原异常，不伪装 DENY 或返回半批结果。 */
    public QueryResult execute(QueryRequest request) {
        QueryRequestValidator.validate(request);
        requireImplementedOutputs(request.items());
        if (request.items().isEmpty()) {
            return new QueryResult(UUID.randomUUID().toString(), LocalDateTime.now(clock), List.of());
        }
        RunState run = new RunState(request, clock);
        try {
            resolveSubject(run);
            if (run.roles().isEmpty()) return noRoleResults(run);
            requireImplemented(request.items());
            request.items().forEach(item -> run.items().put(item, new RunState.ItemExecution()));
            run.evaluator(new CandidateEvaluator(run, reads, conditions, conflicts));
            Map<TypeOperation, ResolvedOperation> operations = prepareOperations(run);
            processTypeGrantStage(run, operations);
            processInstanceStage(run, operations);
            return new QueryResult(run.executionId(), run.evaluatedAt(), request.items().stream()
                .map(item -> complete(item, run)).toList());
        } finally {
            run.release();
        }
    }

    private void resolveSubject(RunState run) {
        if (run.request().subject() instanceof Roles roles) {
            ResolvedSubject resolved = resolveSubject(roles);
            run.resolveSubject(resolved.roles(), resolved.resolution());
        } else {
            User user = (User) run.request().subject();
            Set<Long> held = subjects.resolveEffectiveRoles(run.request().tenantId(), user.userId());
            if (held.isEmpty()) {
                run.resolveSubject(Set.of(), SubjectResolution.USER_EFFECTIVE_WITH_MUTEX);
                return;
            }
            var computed = conflicts.computeRoleMutex(run.request().tenantId(), held);
            run.roleHits(computed.hits());
            run.resolveSubject(computed.keptRoleIds(), SubjectResolution.USER_EFFECTIVE_WITH_MUTEX);
        }
    }

    static ResolvedSubject resolveSubject(Roles roles) {
        return new ResolvedSubject(roles.roleIds(), SubjectResolution.EXPLICIT_ROLES);
    }

    private static void requireImplemented(List<QueryItem> items) {
        for (QueryItem item : items) {
            if (item.selection() instanceof GrantList || item.selection() instanceof TargetSet t && t.parent() != null) {
                throw new UnsupportedOperationException("父要求与 GRANT_LIST 随 T-PERM-086 落地");
            }
            if (item.selection() instanceof OperationAdmission) {
                throw new UnsupportedOperationException("ADMISSION_CANDIDATES 随 T-ACCESS-057 落地");
            }
        }
    }

    private static void requireImplementedOutputs(List<QueryItem> items) {
        for (QueryItem item : items) {
            OutputSpec output = item.output();
            if (output.descriptions() || output.effectiveOperations() || output.presentationExpansion() || output.trace()) {
                throw new UnsupportedOperationException("描述/有效操作/展示/TRACE 随 T-PERM-087/088 落地");
            }
        }
    }

    private Map<TypeOperation, ResolvedOperation> prepareOperations(RunState run) {
        Set<TypeOperation> keys = new LinkedHashSet<>();
        run.request().items().forEach(item -> keys.addAll(requirements(item.selection())));
        Map<TypeOperation, OperationDefinition> targets = reads.resolveOperations(run, keys);
        Set<Integer> types = new LinkedHashSet<>();
        targets.values().forEach(op -> types.add(op.resourceType()));
        Map<Integer, List<OperationPermission>> catalogs = new LinkedHashMap<>();
        reads.maskOperations(run, types).forEach((type, values) ->
            catalogs.put(type, values.stream().map(OperationDefinition::toCacheRow).toList()));
        Map<TypeOperation, ResolvedOperation> resolved = new LinkedHashMap<>();
        targets.forEach((key, target) -> {
            long mask = OperationPermissionUtils.computeCoveringBitMask(catalogs.get(target.resourceType()), target.binaryBit());
            if (mask != 0) resolved.put(key, new ResolvedOperation(target.resourceType(), mask));
        });
        return Map.copyOf(resolved);
    }

    private static List<TypeOperation> requirements(Selection selection) {
        if (selection instanceof TypeLevel type) return type.requirements();
        return ((TargetSet) selection).clauses().stream().map(TargetClause::operation).toList();
    }

    private void processTypeGrantStage(RunState run, Map<TypeOperation, ResolvedOperation> operations) {
        Map<QueryItem, List<CandidateSelector.Clause>> clauses = new LinkedHashMap<>();
        for (QueryItem item : run.request().items()) {
            if (!applicableStages(item.selection()).contains(Stage.TYPE_GRANT)) continue;
            clauses.put(item, requirements(item.selection()).stream().map(operations::get).filter(Objects::nonNull)
                .map(op -> new CandidateSelector.Clause(op.type(), op.mask(), Set.of())).toList());
        }
        evaluateStage(run, Stage.TYPE_GRANT, clauses, reads.scopeGrants(run, run.roles(), masks(clauses)));
        clauses.keySet().forEach(item -> {
            RunState.ItemExecution state = run.items().get(item);
            if (item.selection() instanceof TargetSet && item.resultForm() == ResultForm.DECISION && state.retained()) {
                state.shortCircuited = true;
            }
        });
    }

    private void processInstanceStage(RunState run, Map<TypeOperation, ResolvedOperation> operations) {
        List<QueryItem> items = run.request().items().stream().filter(item -> item.selection() instanceof TargetSet
            && !run.items().get(item).shortCircuited).toList();
        List<ResourceResolveRequest> requested = new ArrayList<>();
        items.forEach(item -> ((TargetSet) item.selection()).clauses().forEach(clause -> {
            if (operations.containsKey(clause.operation()) && clause.resource() instanceof ByCode code) {
                requested.add(resourceRequest(clause.operation(), code));
            }
        }));
        var resourceIds = reads.resolveResources(run, requested);
        Map<TargetClause, Long> targets = new LinkedHashMap<>();
        Set<Long> inheritedTargets = new LinkedHashSet<>();
        items.forEach(item -> {
            TargetSet selection = (TargetSet) item.selection();
            for (TargetClause clause : selection.clauses()) {
                if (!operations.containsKey(clause.operation())) continue;
                Long id = clause.resource() instanceof ByEntityId entity ? entity.entityId()
                    : resourceIds.get(resourceRequest(clause.operation(), (ByCode) clause.resource()).toKey());
                if (id != null) {
                    targets.put(clause, id);
                    if (selection.inheritance() == Inheritance.SELF_AND_ANCESTORS) inheritedTargets.add(id);
                }
            }
        });
        Map<Long, Set<Long>> closures = reads.ancestorClosures(run, resourceMapper, inheritedTargets);
        Map<QueryItem, List<CandidateSelector.Clause>> clauses = new LinkedHashMap<>();
        Set<Long> allEntities = new LinkedHashSet<>();
        items.forEach(item -> {
            TargetSet selection = (TargetSet) item.selection();
            List<CandidateSelector.Clause> resolved = new ArrayList<>();
            for (TargetClause clause : selection.clauses()) {
                Long id = targets.get(clause);
                if (id == null) continue;
                ResolvedOperation op = operations.get(clause.operation());
                Set<Long> closure = selection.inheritance() == Inheritance.SELF ? Set.of(id) : closures.get(id);
                resolved.add(new CandidateSelector.Clause(op.type(), op.mask(), closure));
                allEntities.addAll(closure);
            }
            clauses.put(item, List.copyOf(resolved));
        });
        evaluateStage(run, Stage.INSTANCE, clauses, reads.instanceGrants(run, run.roles(), allEntities, masks(clauses)));
    }

    private static ResourceResolveRequest resourceRequest(TypeOperation op, ByCode code) {
        return new ResourceResolveRequest(op.resourceTypeCode(), code.code(), code.codeType(), code.domainCode());
    }

    private static Map<Integer, Long> masks(Map<QueryItem, List<CandidateSelector.Clause>> clauses) {
        Map<Integer, Long> masks = new LinkedHashMap<>();
        clauses.values().forEach(values -> values.forEach(c -> masks.merge(c.type(), c.mask(), (a, b) -> a | b)));
        return masks;
    }

    private static void evaluateStage(RunState run, Stage stage,
        Map<QueryItem, List<CandidateSelector.Clause>> clauses, List<GrantFact> loaded) {
        Map<QueryItem, List<GrantFact>> rawByItem = new LinkedHashMap<>();
        clauses.forEach((item, paired) -> {
            List<GrantFact> candidates = CandidateSelector.select(loaded, paired, stage);
            List<GrantFact> raw = candidates.stream().filter(f -> f.dependOn() == null).toList();
            // TYPE_LEVEL 的子行不属于有效选择；只有目标项把排除解释为父上下文不匹配。
            if (item.selection() instanceof TargetSet && raw.size() < candidates.size()) {
                run.items().get(item).dependentExcluded = true;
            }
            rawByItem.put(item, raw);
        });
        run.evaluator().preload(rawByItem);
        rawByItem.forEach((item, raw) -> {
            var evaluated = run.evaluator().evaluate(item, stage, clauses.get(item), raw);
            RunState.ItemExecution state = run.items().get(item);
            StageFacts.Status status = !evaluated.retained().isEmpty() ? StageFacts.Status.PRESENT
                : raw.isEmpty() ? StageFacts.Status.NO_MATCH : StageFacts.Status.FILTERED_EMPTY;
            state.stages.put(stage, new StageFacts(stage, raw, evaluated.retained(), status));
            state.mutexHits.put(stage, evaluated.triggeredRuleIds());
            state.mutexCandidate |= evaluated.mutexCandidate();
        });
    }

    private static ItemResult complete(QueryItem item, RunState run) {
        RunState.ItemExecution state = run.items().get(item);
        Map<Stage, SkipReason> skipped = state.shortCircuited ? Map.of(Stage.INSTANCE, SkipReason.SUFFICIENT_DECISION) : Map.of();
        ConditionCoverage condition = !state.hadRaw() ? ConditionCoverage.NO_CANDIDATE
            : item.evaluation().conditionMode() == ConditionMode.EVALUATE ? ConditionCoverage.EVALUATED : ConditionCoverage.PRESERVED;
        MutexCoverage mutex = !state.mutexCandidate ? MutexCoverage.NO_CANDIDATE
            : item.evaluation().mutexMode() == MutexMode.ENFORCE ? MutexCoverage.EVALUATED : MutexCoverage.SKIPPED;
        EvaluationCoverage coverage = new EvaluationCoverage(run.subjectResolution(), condition, mutex,
            ParentCheckCoverage.NOT_REQUIRED, state.stages.keySet(), skipped, !state.shortCircuited, authorizationStage(item.resultForm()));
        ResultDetails details = QueryProjector.project(item.output(), state);
        if (item.resultForm() == ResultForm.FACTS) {
            GrantSetResult.CollectionStatus status = state.retained() ? GrantSetResult.CollectionStatus.PRESENT
                : state.hadRaw() ? GrantSetResult.CollectionStatus.FILTERED_EMPTY : GrantSetResult.CollectionStatus.NO_MATCH;
            return new GrantSetResult(item.key(), status, coverage, details);
        }
        if (state.retained()) return DecisionResult.allow(item.key(), coverage, details);
        DecisionResult.Reason reason = state.hadRaw() ? DecisionResult.Reason.CONDITION_NOT_MET_OR_CONFLICT
            : state.dependentExcluded ? DecisionResult.Reason.DEPENDENT_NOT_IN_PARENT_CONTEXT : DecisionResult.Reason.NO_PERMISSION;
        return DecisionResult.deny(item.key(), reason, coverage, details);
    }

    private static QueryResult noRoleResults(RunState run) {
        List<ItemResult> results = run.request().items().stream().map(item -> {
            Map<Stage, SkipReason> skipped = new EnumMap<>(Stage.class);
            applicableStages(item.selection()).forEach(stage -> skipped.put(stage, SkipReason.NO_ROLE));
            boolean parentRequired = item.selection() instanceof TargetSet t && t.parent() != null
                || item.selection() instanceof GrantList g && g.requiredParent() != null;
            EvaluationCoverage coverage = new EvaluationCoverage(run.subjectResolution(), ConditionCoverage.NO_CANDIDATE,
                MutexCoverage.NO_CANDIDATE, parentRequired ? ParentCheckCoverage.NOT_TRIGGERED : ParentCheckCoverage.NOT_REQUIRED,
                Set.of(), skipped, false, authorizationStage(item.resultForm()));
            ResultDetails details = QueryProjector.project(item.output(), new RunState.ItemExecution());
            return switch (item.resultForm()) {
                case DECISION -> (ItemResult) DecisionResult.deny(item.key(), DecisionResult.Reason.NO_ROLE, coverage, details);
                case FACTS -> new GrantSetResult(item.key(), GrantSetResult.CollectionStatus.NO_ROLE, coverage, details);
                case ADMISSION -> AdmissionResult.deny(item.key(), AdmissionResult.Reason.NO_ROLE, coverage, details);
            };
        }).toList();
        return new QueryResult(run.executionId(), run.evaluatedAt(), results);
    }

    private static Set<Stage> applicableStages(Selection selection) {
        return switch (selection) {
            case TypeLevel ignored -> EnumSet.of(Stage.TYPE_GRANT);
            case TargetSet t -> t.typeFallback() == TypeFallback.ALLOW
                ? EnumSet.of(Stage.TYPE_GRANT, Stage.INSTANCE) : EnumSet.of(Stage.INSTANCE);
            case GrantList ignored -> EnumSet.of(Stage.GRANT_LIST);
            case OperationAdmission ignored -> EnumSet.of(Stage.ADMISSION_CANDIDATES);
        };
    }

    private static AuthorizationStage authorizationStage(ResultForm form) {
        return switch (form) {
            case DECISION -> AuthorizationStage.FINAL_DECISION;
            case FACTS -> AuthorizationStage.FACT_COLLECTION;
            case ADMISSION -> AuthorizationStage.OPERATION_ADMISSION;
        };
    }

    record ResolvedSubject(Set<Long> roles, SubjectResolution resolution) {}
    private record ResolvedOperation(int type, long mask) {}
}
