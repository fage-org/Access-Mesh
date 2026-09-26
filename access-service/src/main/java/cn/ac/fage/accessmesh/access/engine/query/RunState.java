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
import cn.ac.fage.accessmesh.access.engine.dto.PermEvalContext;
import cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConflictDomainService.RolePairRef;
import java.util.UUID;

import cn.ac.fage.accessmesh.access.engine.query.EvaluationCoverage.SubjectResolution;

/**
 * 单次执行运行态（设计 §4.1）。
 * <p>
 * 一次 execute 一个实例：固定评估时刻（注入 Clock）、主体解析结果与已读记忆的宿主。
 * 禁止单例字段、ThreadLocal、跨请求/跨写复用（I07：同一事务先写后新 execute 创建新运行态）。
 * 持有读取记忆、共享条件/互斥评估器、父结果及分项阶段事实；根审计提交由后续任务接入。
 * </p>
 */
final class RunState {

    private final String executionId = UUID.randomUUID().toString();
    private final LocalDateTime evaluatedAt;
    private final QueryRequest request;
    private Set<Long> roles;
    private SubjectResolution subjectResolution;
    private QueryReadSupport.Memory readMemory;
    private boolean released;
    private CandidateEvaluator evaluator;
    private final Map<QueryItem, ItemExecution> items = new LinkedHashMap<>();
    private final Map<ParentRequirement, ParentExecution> parents = new LinkedHashMap<>();
    private List<RolePairRef> roleHits = List.of();

    RunState(QueryRequest request, Clock clock) {
        this.request = request;
        this.evaluatedAt = LocalDateTime.now(clock);
    }

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
    void roleHits(List<RolePairRef> hits) { this.roleHits = List.copyOf(hits); }

    /** 证据随请求保留，根审计/TRACE 在 T-PERM-088 接入。 */
    static final class ItemExecution {
        final Map<Stage, StageFacts> stages = new LinkedHashMap<>();
        final Map<Stage, Set<Long>> mutexHits = new LinkedHashMap<>();
        boolean dependentExcluded;
        boolean mutexCandidate;
        boolean shortCircuited;
        boolean parentDenied;
        ParentExecution parent;

        boolean retained() { return stages.values().stream().anyMatch(s -> !s.retainedAfterEvaluation().isEmpty()); }
        boolean hadRaw() { return stages.values().stream().anyMatch(s -> !s.rawAfterContext().isEmpty()); }
    }

    /** 一个实际父判定及其全部根项引用；父阶段证据保留一次，供根审计消费。 */
    static final class ParentExecution {
        final QueryItem item;
        final ItemExecution execution = new ItemExecution();
        final Set<String> affectedItemKeys = new LinkedHashSet<>();

        ParentExecution(QueryItem item) { this.item = item; }

        Set<Long> matchedPermissionIds() {
            Set<Long> ids = new LinkedHashSet<>();
            execution.stages.values().forEach(stage -> stage.retainedAfterEvaluation()
                .forEach(fact -> ids.add(fact.permissionId())));
            return Collections.unmodifiableSet(ids);
        }
    }

    /** 读取记忆只属于本次执行；释放后不能重新装载。 */
    QueryReadSupport.Memory readMemory() {
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

    /** 释放可清理引用（execute 完成后调用；运行态不跨请求存活）。 */
    void release() {
        this.roles = null;
        this.subjectResolution = null;
        this.readMemory = null;
        this.evaluator = null;
        this.items.clear();
        this.parents.clear();
        this.roleHits = List.of();
        this.released = true;
    }
}
