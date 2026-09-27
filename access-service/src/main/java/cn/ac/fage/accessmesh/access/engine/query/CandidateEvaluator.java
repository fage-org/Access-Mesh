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
import java.util.ArrayList;
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

    /** 合批预载本行条件；普通 PRESERVE 不读规则，准入 FACTS 需核规则状态但不求值。 */
    void preload(Map<QueryItem, List<GrantFact>> rawByItem) {
        Set<Long> ids = new LinkedHashSet<>();
        rawByItem.forEach((item, raw) -> {
            if (item.evaluation().conditionMode() == ConditionMode.EVALUATE
                || item.selection() instanceof OperationAdmission) {
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
        return results.computeIfAbsent(key, ignored -> item.selection() instanceof OperationAdmission
            ? computeAdmission(item, raw) : compute(item.evaluation(), raw));
    }

    /** 准入没有 PERM_MUTEX，在线才可存在性短路；FACTS 保留全部可用规则身份而不求值。 */
    private Evaluated computeAdmission(QueryItem item, List<GrantFact> raw) {
        List<GrantFact> retained = new ArrayList<>();
        for (int index = 0; index < raw.size(); index++) {
            GrantFact fact = raw.get(index);
            if (fact.hasCondition() != (fact.conditionId() != null)) {
                log.error("Admission invalid condition reference: tenantId={}, permissionId={}",
                    run.request().tenantId(), fact.permissionId());
                continue;
            }
            if (fact.hasCondition()) {
                var status = conditions.ruleStatus(fact.conditionId());
                if (status != BatchConditionEvaluator.RuleStatus.OK) {
                    log.warn("Admission unavailable condition: tenantId={}, permissionId={}, conditionId={}, status={}",
                        run.request().tenantId(), fact.permissionId(), fact.conditionId(), status);
                    continue;
                }
            }
            if (item.resultForm() == ResultForm.FACTS
                || !conditions.evaluate(run.request().tenantId(), List.of(entries.toEntry(fact)), run.evalContext()).isEmpty()) {
                retained.add(fact);
                if (item.resultForm() == ResultForm.ADMISSION) {
                    return new Evaluated(List.copyOf(retained), Set.of(), List.of(), false, index == raw.size() - 1);
                }
            }
        }
        return new Evaluated(List.copyOf(retained), Set.of(), List.of(), false, true);
    }

    private Evaluated compute(Evaluation evaluation, List<GrantFact> raw) {
        if (raw.isEmpty()) return new Evaluated(List.of(), Set.of(), List.of(), false, true);
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
        List<BatchPermMutexEvaluator.MutexRuleRef> triggeredRules = List.of();
        if (evaluation.mutexMode() == MutexMode.ENFORCE && mutexCandidate) {
            var computed = mutex.compute(adapted);
            adapted = computed.filtered();
            hits = Set.copyOf(computed.triggeredRuleIds());
            // 证据规则引用复用请求级已装载规则（零额外 I/O；收集责任在根审计，见 §6.1）
            triggeredRules = mutex.describeRules(hits);
        }
        Set<Long> retainedIds = new LinkedHashSet<>();
        adapted.forEach(e -> retainedIds.add(e.permissionId()));
        return new Evaluated(raw.stream().filter(f -> retainedIds.contains(f.permissionId())).toList(),
            hits, triggeredRules, mutexCandidate, true);
    }

    record Evaluated(List<GrantFact> retained, Set<Long> triggeredRuleIds,
                     List<BatchPermMutexEvaluator.MutexRuleRef> triggeredRules, boolean mutexCandidate,
                     boolean complete) {}
    private record Key(Selection selection, Stage stage, List<CandidateSelector.Clause> clauses,
                       List<GrantFact> raw, Evaluation evaluation, Set<Long> parentPermissionIds) {}
}
