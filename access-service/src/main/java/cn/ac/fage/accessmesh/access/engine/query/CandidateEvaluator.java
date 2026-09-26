package cn.ac.fage.accessmesh.access.engine.query;

import cn.ac.fage.accessmesh.access.engine.core.BatchConditionEvaluator;
import cn.ac.fage.accessmesh.access.engine.core.BatchPermMutexEvaluator;
import cn.ac.fage.accessmesh.access.engine.util.RolePermEntryMapper;
import cn.ac.fage.accessmesh.access.engine.vo.RolePermEntry;
import cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConditionDomainService;
import cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConflictDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 单次执行的封闭评估策略；复用领域条件/互斥能力，不通知、不修改共享事实。 */
final class CandidateEvaluator {
    private static final Logger log = LoggerFactory.getLogger(CandidateEvaluator.class);
    private final RunState run;
    private final BatchConditionEvaluator conditions;
    private final BatchPermMutexEvaluator mutex;
    private final RolePermEntryMapper entries = new RolePermEntryMapper();
    private final Map<Key, Evaluated> results = new LinkedHashMap<>();

    CandidateEvaluator(RunState run, QueryReadSupport reads, PermissionConditionDomainService conditions,
                       PermissionConflictDomainService conflicts) {
        this.run = run;
        this.conditions = conditions.openBatchEvaluator(run.request().tenantId());
        this.mutex = conflicts.openBatchMutexEvaluator(run.request().tenantId(), types ->
            reads.freshOperations(run, types).values().stream().flatMap(List::stream)
                .map(OperationDefinition::toCacheRow).toList());
    }

    /** 对本阶段真正需要求值的上下文后候选合批预载，PRESERVE 不读取条件规则。 */
    void preload(Map<QueryItem, List<GrantFact>> rawByItem) {
        Set<Long> ids = new LinkedHashSet<>();
        rawByItem.forEach((item, raw) -> {
            if (item.evaluation().conditionMode() == ConditionMode.EVALUATE) {
                raw.stream().filter(f -> f.hasCondition() && f.conditionId() != null)
                    .map(GrantFact::conditionId).forEach(ids::add);
            }
        });
        if (!ids.isEmpty()) conditions.preload(run.request().tenantId(), ids);
    }

    Evaluated evaluate(QueryItem item, Stage stage, List<CandidateSelector.Clause> clauses, List<GrantFact> raw,
                       Set<Long> parentPermissionIds) {
        // 完整 selection 包含继承和绑定要求；阶段、配对、真实候选、策略均参与，输出不参与判定。
        Key key = new Key(item.selection(), stage, List.copyOf(clauses), raw, item.evaluation(), Set.copyOf(parentPermissionIds));
        return results.computeIfAbsent(key, ignored -> compute(item.evaluation(), raw));
    }

    private Evaluated compute(Evaluation evaluation, List<GrantFact> raw) {
        if (raw.isEmpty()) return new Evaluated(List.of(), Set.of(), false);
        raw.stream().filter(f -> f.hasCondition() != (f.conditionId() != null)).forEach(f ->
            log.error("Inconsistent condition reference: tenantId={}, permissionId={}",
                run.request().tenantId(), f.permissionId()));
        List<GrantFact> eligible = raw;
        if (evaluation.conditionMode() == ConditionMode.EVALUATE) {
            eligible = raw.stream().filter(f -> f.hasCondition() == (f.conditionId() != null)).toList();
        }
        List<RolePermEntry> adapted = eligible.stream().map(entries::toEntry).toList();
        if (evaluation.conditionMode() == ConditionMode.EVALUATE && !adapted.isEmpty()) {
            adapted = conditions.evaluate(run.request().tenantId(), adapted, run.evalContext());
        }
        boolean mutexCandidate = !adapted.isEmpty();
        Set<Long> hits = Set.of();
        if (evaluation.mutexMode() == MutexMode.ENFORCE && mutexCandidate) {
            var computed = mutex.compute(adapted);
            adapted = computed.filtered();
            hits = Set.copyOf(computed.triggeredRuleIds());
        }
        Set<Long> retainedIds = new LinkedHashSet<>();
        adapted.forEach(e -> retainedIds.add(e.permissionId()));
        return new Evaluated(raw.stream().filter(f -> retainedIds.contains(f.permissionId())).toList(), hits, mutexCandidate);
    }

    record Evaluated(List<GrantFact> retained, Set<Long> triggeredRuleIds, boolean mutexCandidate) {}
    private record Key(Selection selection, Stage stage, List<CandidateSelector.Clause> clauses,
                       List<GrantFact> raw, Evaluation evaluation, Set<Long> parentPermissionIds) {}
}
