package cn.ac.fage.accessmesh.access.engine.query;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import cn.ac.fage.accessmesh.access.engine.query.EvaluationCoverage.SubjectResolution;

/**
 * 单次执行运行态（T-PERM-082，设计 §4.1 骨架）。
 * <p>
 * 一次 execute 一个实例：固定评估时刻（注入 Clock）、主体解析结果与已读记忆的宿主。
 * 禁止单例字段、ThreadLocal、跨请求/跨写复用（I07：同一事务先写后新 execute 创建新运行态）。
 * 已读/缺失三态记忆与读取来源桶由 T-PERM-084 挂载；阶段规则、条件结果、父结果与审计随后接入。
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
        this.roles = Set.copyOf(roles);
        this.subjectResolution = resolution;
    }

    /** 释放可清理引用（execute 完成后调用；运行态不跨请求存活）。 */
    void release() {
        this.roles = null;
        this.subjectResolution = null;
        this.readMemory = null;
        this.released = true;
    }
}
