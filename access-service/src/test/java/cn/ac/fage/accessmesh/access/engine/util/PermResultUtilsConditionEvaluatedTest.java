package cn.ac.fage.accessmesh.access.engine.util;

import cn.ac.fage.accessmesh.access.engine.dto.AuthCheckResp;
import cn.ac.fage.accessmesh.access.engine.query.DecisionResult;
import cn.ac.fage.accessmesh.access.engine.query.EvaluationCoverage;
import cn.ac.fage.accessmesh.access.engine.query.GrantFact;
import cn.ac.fage.accessmesh.access.engine.query.ResultDetails;
import cn.ac.fage.accessmesh.access.engine.query.Stage;
import cn.ac.fage.accessmesh.access.engine.query.StageFacts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * allow 侧 conditionEvaluated 基准锁（2026-10-06 逐任务评审补直接锁）。
 * <p>
 * T-PERM-107 起 allow 的 conditionEvaluated 取 rawAfterContext（旧基准为
 * retainedAfterEvaluation）：混合 allow——raw 含条件事实、条件/互斥阶段把该事实从
 * retained 过滤掉——旧基准下报 false、新基准报 true。既有测试的 mock 工厂把事实同放
 * raw+retained 两集合，新旧基准均过，不构成区分性锁；本用例以分离集合直接钉死 raw 基准。
 * </p>
 */
class PermResultUtilsConditionEvaluatedTest {

    @Test
    @DisplayName("混合 allow：raw 含条件事实而 retained 已过滤 → conditionEvaluated=true（raw 基准；旧 retained 基准必红）")
    void mixedAllowReportsConditionEvaluatedFromRawBaseline() {
        GrantFact conditional = new GrantFact(1L, 2L, 5, null, 2L, true, false, 88L, true, null, "MANUAL");
        GrantFact plain = new GrantFact(2L, 2L, 5, null, 2L, true, false, null, false, null, "MANUAL");
        StageFacts stage = new StageFacts(Stage.INSTANCE, List.of(conditional, plain), List.of(plain),
            StageFacts.Status.PRESENT);
        ResultDetails details = new ResultDetails(Set.of(), List.of(2L), List.of(1L, 2L), List.of(stage));
        DecisionResult allow = new DecisionResult("k", DecisionResult.Decision.ALLOW, null,
            new EvaluationCoverage(EvaluationCoverage.SubjectResolution.USER_EFFECTIVE_WITH_MUTEX,
                EvaluationCoverage.ConditionCoverage.EVALUATED, EvaluationCoverage.MutexCoverage.EVALUATED,
                EvaluationCoverage.ParentCheckCoverage.NOT_REQUIRED, Set.of(Stage.INSTANCE),
                Map.of(), true, EvaluationCoverage.AuthorizationStage.FACT_COLLECTION),
            details);

        AuthCheckResp resp = PermResultUtils.toAuthCheckResp(allow);

        assertThat(resp.conditionEvaluated())
            .as("allow 的 conditionEvaluated 按 raw 基准——条件事实曾参与本次判定即 true").isTrue();
    }

    @Test
    @DisplayName("无任何条件事实的 allow → conditionEvaluated=false")
    void plainAllowReportsFalse() {
        GrantFact plain = new GrantFact(2L, 2L, 5, null, 2L, true, false, null, false, null, "MANUAL");
        StageFacts stage = new StageFacts(Stage.INSTANCE, List.of(plain), List.of(plain), StageFacts.Status.PRESENT);
        ResultDetails details = new ResultDetails(Set.of(), List.of(2L), List.of(2L), List.of(stage));
        DecisionResult allow = new DecisionResult("k", DecisionResult.Decision.ALLOW, null,
            new EvaluationCoverage(EvaluationCoverage.SubjectResolution.USER_EFFECTIVE_WITH_MUTEX,
                EvaluationCoverage.ConditionCoverage.EVALUATED, EvaluationCoverage.MutexCoverage.EVALUATED,
                EvaluationCoverage.ParentCheckCoverage.NOT_REQUIRED, Set.of(Stage.INSTANCE),
                Map.of(), true, EvaluationCoverage.AuthorizationStage.FACT_COLLECTION),
            details);

        assertThat(PermResultUtils.toAuthCheckResp(allow).conditionEvaluated()).isFalse();
    }
}
