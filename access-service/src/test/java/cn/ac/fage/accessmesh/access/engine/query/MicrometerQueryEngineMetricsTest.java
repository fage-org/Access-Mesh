package cn.ac.fage.accessmesh.access.engine.query;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Micrometer 绑定回归锁（T-PERM-094，2026-10-01 拍板「计数＋执行时长＋超限细分」档）：
 * 指标名、低基数标签集（仅端口枚举）与时长/超限细分口径。
 */
class MicrometerQueryEngineMetricsTest {

    @Test
    @DisplayName("阶段终态按 选择×阶段×终态 低基数标签计数")
    void shouldRecordStageOutcomeAsLowCardinalityCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerQueryEngineMetrics metrics = new MicrometerQueryEngineMetrics(registry);
        metrics.itemStage(QueryEngineMetrics.SelectionKind.TARGET_SET, Stage.INSTANCE,
            QueryEngineMetrics.StageOutcome.COMPLETED);
        metrics.itemStage(QueryEngineMetrics.SelectionKind.TARGET_SET, Stage.INSTANCE,
            QueryEngineMetrics.StageOutcome.COMPLETED);
        metrics.itemStage(QueryEngineMetrics.SelectionKind.GRANT_LIST, Stage.GRANT_LIST,
            QueryEngineMetrics.StageOutcome.SKIPPED_SUFFICIENT_DECISION);

        assertThat(registry.get("access.query.stage")
            .tag("selection", "TARGET_SET").tag("stage", "INSTANCE").tag("outcome", "COMPLETED")
            .counter().count()).isEqualTo(2.0);
        assertThat(registry.get("access.query.stage")
            .tag("selection", "GRANT_LIST").tag("stage", "GRANT_LIST")
            .tag("outcome", "SKIPPED_SUFFICIENT_DECISION")
            .counter().count()).as("scopeAll 短路率观测载体").isEqualTo(1.0);
    }

    @Test
    @DisplayName("执行终态记 Timer：时长可观测，预算超限单列容量信号")
    void shouldRecordExecutionOutcomeAsTimerWithDurationAndBudgetSplit() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerQueryEngineMetrics metrics = new MicrometerQueryEngineMetrics(registry);

        metrics.executionCompleted(QueryEngineMetrics.ExecutionOutcome.SUCCESS, Duration.ofMillis(120).toNanos());
        metrics.executionCompleted(QueryEngineMetrics.ExecutionOutcome.BUDGET_EXCEEDED, Duration.ofMillis(30).toNanos());

        var success = registry.get("access.query.execution").tag("outcome", "SUCCESS").timer();
        assertThat(success.count()).isEqualTo(1);
        assertThat(success.totalTime(TimeUnit.NANOSECONDS))
            .isGreaterThanOrEqualTo(Duration.ofMillis(120).toNanos());
        assertThat(registry.get("access.query.execution").tag("outcome", "BUDGET_EXCEEDED").timer().count())
            .as("预算超限与技术故障分开告警（拍板 A 档超限细分）").isEqualTo(1);
        assertThat(registry.find("access.query.execution").tag("outcome", "TECHNICAL_FAILURE").timer())
            .as("未打点的终态不注册 meter（零计数）").isNull();
    }

    @Test
    @DisplayName("不带时长的终态调用转发为零时长 Timer（接口 default 链）")
    void shouldFallbackToZeroDurationWhenOutcomeOnly() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerQueryEngineMetrics metrics = new MicrometerQueryEngineMetrics(registry);

        metrics.executionCompleted(QueryEngineMetrics.ExecutionOutcome.SUCCESS);

        var timer = registry.get("access.query.execution").tag("outcome", "SUCCESS").timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isZero();
    }

    @Test
    @DisplayName("证据提交失败按类别计数（不带 ID 标签）")
    void shouldRecordEvidenceSubmissionFailureByKind() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerQueryEngineMetrics metrics = new MicrometerQueryEngineMetrics(registry);

        metrics.evidenceSubmissionFailed(QueryEngineMetrics.EvidenceKind.PERM_RULE);
        metrics.evidenceSubmissionFailed(QueryEngineMetrics.EvidenceKind.PERM_RULE);
        metrics.evidenceSubmissionFailed(QueryEngineMetrics.EvidenceKind.ROLE_PAIR);

        assertThat(registry.get("access.query.evidence.failed").tag("kind", "PERM_RULE").counter().count())
            .isEqualTo(2.0);
        assertThat(registry.get("access.query.evidence.failed").tag("kind", "ROLE_PAIR").counter().count())
            .isEqualTo(1.0);
    }
}
