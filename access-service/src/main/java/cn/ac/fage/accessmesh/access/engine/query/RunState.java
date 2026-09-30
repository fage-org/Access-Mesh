package cn.ac.fage.accessmesh.access.engine.query;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.Objects;
import cn.ac.fage.accessmesh.access.engine.core.BatchPermMutexEvaluator;
import cn.ac.fage.accessmesh.access.engine.dto.PermEvalContext;
import cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConflictDomainService.RolePairRef;
import java.util.UUID;

import cn.ac.fage.accessmesh.access.engine.query.EvaluationCoverage.SubjectResolution;

/**
 * 单次执行运行态（设计 §4.1）。
 * <p>
 * 一次 execute 一个实例：固定评估时刻（注入 Clock）、主体解析结果与已读记忆的宿主。
 * 禁止单例字段、ThreadLocal、跨请求/跨写复用（I07：同一事务先写后新 execute 创建新运行态）。
 * 持有读取记忆、共享条件/互斥评估器、父结果及分项阶段事实；根级受控证据提交在释放前由
 * QueryAuditCollector 消费本运行态（T-PERM-088）。
 * </p>
 */
final class RunState {

    private final String executionId = UUID.randomUUID().toString();
    private final LocalDateTime evaluatedAt;
    private final QueryRequest request;
    private final EngineLimits.Budget budget;
    private Set<Long> roles;
    private SubjectResolution subjectResolution;
    private QueryReadSupport.Memory readMemory;
    private boolean released;
    private CandidateEvaluator evaluator;
    private final Map<QueryItem, ItemExecution> items = new LinkedHashMap<>();
    private final Map<ParentRequirement, ParentExecution> parents = new LinkedHashMap<>();
    private List<RolePairRef> roleHits = List.of();
    private Throwable executionFailure;
    private boolean evidenceSubmitted;
    private int parentSequence;

    RunState(QueryRequest request, Clock clock) {
        this(request, clock, EngineLimits.unlimited());
    }

    RunState(QueryRequest request, Clock clock, EngineLimits limits) {
        this.request = request;
        this.evaluatedAt = LocalDateTime.now(clock);
        this.budget = limits.openBudget(System::nanoTime);
    }

    EngineLimits.Budget budget() { return budget; }

    String executionId() {
        return executionId;
    }

    LocalDateTime evaluatedAt() {
        return evaluatedAt;
    }

    QueryRequest request() {
        return request;
    }

    Set<Long> roles() {
        return roles == null ? Set.of() : roles;
    }

    SubjectResolution subjectResolution() {
        return subjectResolution;
    }

    Map<String, Object> evalContext() {
        CallerContext caller = request.context();
        return new PermEvalContext(caller.clientIp(), evaluatedAt, caller.attributes()).toEvalMap();
    }

    Map<QueryItem, ItemExecution> items() { return items; }
    Map<ParentRequirement, ParentExecution> parents() { return parents; }
    CandidateEvaluator evaluator() { return evaluator; }
    void evaluator(CandidateEvaluator evaluator) { this.evaluator = evaluator; }
    void roleHits(List<RolePairRef> hits) {
        budget.evidence(hits.size());
        this.roleHits = List.copyOf(hits);
    }
    List<RolePairRef> roleHits() { return roleHits; }

    /** 记录执行中途技术失败（§4.1：证据按 EXECUTION_ERROR_AFTER_CONFIRMED_STAGE 提交，主异常不被覆盖）。 */
    void recordExecutionFailure(Throwable error) {
        if (executionFailure == null) {
            executionFailure = error;
        }
    }

    boolean executionFailed() { return executionFailure != null; }

    /** 父项证据标识序列（本次执行内唯一；父项不占调用方 key 空间）。 */
    String nextParentEvidenceId() {
        return "parent#" + ++parentSequence;
    }

    /** 根级受控提交幂等闸（一次 execute 至多一次提交）。 */
    boolean markEvidenceSubmitted() {
        if (evidenceSubmitted) {
            return false;
        }
        evidenceSubmitted = true;
        return true;
    }

    /** 证据随请求保留（T-PERM-088）：阶段互斥命中携带规则引用，供根审计与 TRACE 消费。 */
    static final class ItemExecution {
        final Map<Stage, StageFacts> stages = new LinkedHashMap<>();
        final Map<Stage, List<BatchPermMutexEvaluator.MutexRuleRef>> mutexHits = new LinkedHashMap<>();
        boolean dependentExcluded;
        boolean mutexCandidate;
        boolean shortCircuited;
        boolean parentDenied;
        boolean candidateEvaluationComplete = true;
        ParentExecution parent;

        boolean retained() { return stages.values().stream().anyMatch(s -> !s.retainedAfterEvaluation().isEmpty()); }
        boolean hadRaw() { return stages.values().stream().anyMatch(s -> !s.rawAfterContext().isEmpty()); }
    }

    /** 一个实际父判定及其全部根项引用；父阶段证据保留一次，供根审计消费。 */
    static final class ParentExecution {
        /** 父项内部证据标识（父项不占调用方 key 空间；序号在本次执行内唯一）。 */
        final String evidenceItemId;
        final QueryItem item;
        final ItemExecution execution = new ItemExecution();
        final Set<String> affectedItemKeys = new LinkedHashSet<>();

        ParentExecution(String evidenceItemId, QueryItem item) {
            this.evidenceItemId = evidenceItemId;
            this.item = item;
        }

        Set<Long> matchedPermissionIds() {
            Set<Long> ids = new LinkedHashSet<>();
            execution.stages.values().forEach(stage -> stage.retainedAfterEvaluation()
                .forEach(fact -> ids.add(fact.permissionId())));
            return Collections.unmodifiableSet(ids);
        }
    }

    /** 读取记忆只属于本次执行；释放后不能重新装载。 */
    QueryReadSupport.Memory readMemory() {
        budget.checkpoint();
        if (released) {
            throw new IllegalStateException("RunState 已释放");
        }
        if (request.tenantId() <= 0) {
            throw new QueryValidationException("tenantId 必须为正数");
        }
        if (readMemory == null) {
            readMemory = new QueryReadSupport.Memory();
        }
        return readMemory;
    }

    /** 记录主体解析结果（每请求一次；Roles 视角原样采用，User 经共同入口，§2.2）。 */
    void resolveSubject(Set<Long> roles, SubjectResolution resolution) {
        Set<Long> resolvedRoles = new LinkedHashSet<>(roles);
        resolvedRoles.forEach(Objects::requireNonNull);
        this.roles = Collections.unmodifiableSet(resolvedRoles);
        this.subjectResolution = resolution;
    }

    /** 释放可清理引用（execute 完成后调用；运行态不跨请求存活——证据提交先于释放）。 */
    void release() {
        this.roles = null;
        this.subjectResolution = null;
        this.readMemory = null;
        this.evaluator = null;
        this.items.clear();
        this.parents.clear();
        this.roleHits = List.of();
        this.executionFailure = null;
        this.evidenceSubmitted = true;
        this.released = true;
    }
}
