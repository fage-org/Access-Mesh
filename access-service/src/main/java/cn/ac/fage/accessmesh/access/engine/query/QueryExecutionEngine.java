package cn.ac.fage.accessmesh.access.engine.query;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import cn.ac.fage.accessmesh.access.engine.query.EvaluationCoverage.AuthorizationStage;
import cn.ac.fage.accessmesh.access.engine.query.EvaluationCoverage.ConditionCoverage;
import cn.ac.fage.accessmesh.access.engine.query.EvaluationCoverage.MutexCoverage;
import cn.ac.fage.accessmesh.access.engine.query.EvaluationCoverage.ParentCheckCoverage;
import cn.ac.fage.accessmesh.access.engine.query.EvaluationCoverage.SkipReason;
import cn.ac.fage.accessmesh.access.engine.query.EvaluationCoverage.SubjectResolution;

/**
 * 唯一 execute 主体骨架（T-PERM-082，设计 §1.1/§4.1）。
 * <p>
 * 生命周期：结构校验（零权限 I/O 整体拒绝）→ 空 items 短路（零权限 I/O）→
 * 创建本次 {@link RunState} → 主体解析一次 → 无有效角色按结果形式返回 NO_ROLE →
 * 判定阶段（TYPE_GRANT/INSTANCE 随 T-PERM-085、GRANT_LIST 随 T-PERM-086、
 * ADMISSION_CANDIDATES 随 T-ACCESS-057〔ADM-T02〕落地；落地前 fail-closed 抛
 * {@link UnsupportedOperationException}）→ finally 释放运行态。
 * </p>
 * <p>
 * 类名为迁移期暂名（2026-09-25 拍板：骨架独立成类、不动旧执行体 PermQueryEngine；
 * 旧类随 T-PERM-092 退出时定终名）。暂不注册 Spring bean——本卡零生产消费者，
 * 消费者迁移自 T-PERM-089 起，届时补 Clock 装配（Clock.systemDefaultZone()）。
 * 评估时刻由注入 Clock 固定：不引入 ZoneId 抽象，沿进程本地时钟语义
 * （时区拍板 2026-09-25；JVM 默认时区由既有 UtcTimezoneEnvironmentPostProcessor 启动即强制 UTC）。
 * </p>
 */
public final class QueryExecutionEngine {

    private final Clock clock;

    public QueryExecutionEngine(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock 不能为空");
    }

    /**
     * 统一查询执行入口。
     *
     * @param request 已通过适配层构造的内部请求
     * @return 与输入等长同序的结果
     * @throws QueryValidationException 非法结构/组合/混批（执行前整体拒绝）
     * @throws UnsupportedOperationException 判定阶段未落地（TYPE_GRANT/INSTANCE→T-PERM-085、
     *         GRANT_LIST→T-PERM-086、ADMISSION_CANDIDATES→T-ACCESS-057；骨架 fail-closed）
     */
    public QueryResult execute(QueryRequest request) {
        QueryRequestValidator.validate(request);
        if (request.items().isEmpty()) {
            return new QueryResult(UUID.randomUUID().toString(), LocalDateTime.now(clock), List.of());
        }
        RunState run = new RunState(request, clock);
        try {
            ResolvedSubject subject = resolveSubject(request.subject());
            run.resolveSubject(subject.roles(), subject.resolution());
            if (subject.roles().isEmpty()) {
                return noRoleResults(run);
            }
            throw new UnsupportedOperationException(
                "判定阶段未落地：TYPE_GRANT/INSTANCE→T-PERM-085、GRANT_LIST→T-PERM-086、"
                    + "ADMISSION_CANDIDATES→T-ACCESS-057（ADM-T02）");
        } finally {
            run.release();
        }
    }

    /**
     * 主体解析（每请求一次，§2.2）：Roles 视角原样采用——不暗中解析用户、不补加角色、
     * 不做 ROLE_MUTEX 过滤（R03 口径；过滤仅发生在 User 主体共同入口，随 T-PERM-084/085 落地）。
     */
    static ResolvedSubject resolveSubject(Subject subject) {
        if (subject instanceof Roles roles) {
            return new ResolvedSubject(roles.roleIds(), SubjectResolution.EXPLICIT_ROLES);
        }
        throw new UnsupportedOperationException("User 主体有效角色+ROLE_MUTEX 解析随 T-PERM-084/085 落地");
    }

    private QueryResult noRoleResults(RunState run) {
        List<ItemResult> results = new ArrayList<>(run.request().items().size());
        for (QueryItem item : run.request().items()) {
            results.add(noRoleResult(item));
        }
        return new QueryResult(run.executionId(), run.evaluatedAt(), results);
    }

    private ItemResult noRoleResult(QueryItem item) {
        EvaluationCoverage coverage = noRoleCoverage(item);
        return switch (item.resultForm()) {
            case DECISION -> DecisionResult.deny(item.key(), DecisionResult.Reason.NO_ROLE, coverage, ResultDetails.empty());
            case FACTS -> new GrantSetResult(item.key(), GrantSetResult.CollectionStatus.NO_ROLE, coverage, ResultDetails.empty());
            case ADMISSION -> AdmissionResult.deny(item.key(), AdmissionResult.Reason.NO_ROLE, coverage, ResultDetails.empty());
        };
    }

    private EvaluationCoverage noRoleCoverage(QueryItem item) {
        Map<Stage, SkipReason> skipped = new EnumMap<>(Stage.class);
        applicableStages(item.selection()).forEach(stage -> skipped.put(stage, SkipReason.NO_ROLE));
        boolean parentRequired = switch (item.selection()) {
            case TargetSet targetSet -> targetSet.parent() != null;
            case GrantList grantList -> grantList.requiredParent() != null;
            default -> false;
        };
        return new EvaluationCoverage(SubjectResolution.EXPLICIT_ROLES, ConditionCoverage.NO_CANDIDATE,
            MutexCoverage.NO_CANDIDATE,
            parentRequired ? ParentCheckCoverage.NOT_TRIGGERED : ParentCheckCoverage.NOT_REQUIRED,
            Set.of(), skipped, false, authorizationStage(item.resultForm()));
    }

    private Set<Stage> applicableStages(Selection selection) {
        return switch (selection) {
            case TypeLevel ignored -> EnumSet.of(Stage.TYPE_GRANT);
            case TargetSet targetSet -> targetSet.typeFallback() == TypeFallback.ALLOW
                ? EnumSet.of(Stage.TYPE_GRANT, Stage.INSTANCE)
                : EnumSet.of(Stage.INSTANCE);
            case GrantList ignored -> EnumSet.of(Stage.GRANT_LIST);
            case OperationAdmission ignored -> EnumSet.of(Stage.ADMISSION_CANDIDATES);
        };
    }

    private AuthorizationStage authorizationStage(ResultForm resultForm) {
        return switch (resultForm) {
            case DECISION -> AuthorizationStage.FINAL_DECISION;
            case FACTS -> AuthorizationStage.FACT_COLLECTION;
            case ADMISSION -> AuthorizationStage.OPERATION_ADMISSION;
        };
    }

    /** 主体解析结果（角色集＋解析方式）。 */
    record ResolvedSubject(Set<Long> roles, SubjectResolution resolution) {
    }
}
