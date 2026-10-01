package cn.ac.fage.accessmesh.access.engine.query;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.Objects;

/**
 * 引擎指标 Micrometer 绑定（T-PERM-094 观测演练，2026-10-01 拍板「计数＋执行时长＋超限细分」档）。
 * <p>
 * 指标面（标签仅端口枚举，低基数契约见 {@link QueryEngineMetrics}）：
 * <ul>
 *   <li>{@code access.query.stage}（Counter，selection/stage/outcome）——各阶段完成/短路计数，
 *       scopeAll 短路率与无角色短路率可观测；</li>
 *   <li>{@code access.query.execution}（Timer，outcome，直方图开启）——执行终态计数与
 *       P50/P95/P99 耗时；{@code BUDGET_EXCEEDED} 单列容量信号，与技术故障分开告警；</li>
 *   <li>{@code access.query.evidence.failed}（Counter，kind）——审计证据受控提交失败。</li>
 * </ul>
 * §10.5 监控口径中阶段级延迟拆分与定义缺失分布按拍板不新增指标，
 * 由执行时长＋阶段短路率计数与拒绝 reason 日志承载。
 * </p>
 */
final class MicrometerQueryEngineMetrics implements QueryEngineMetrics {

    private final MeterRegistry registry;

    MicrometerQueryEngineMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry);
    }

    @Override
    public void itemStage(SelectionKind selection, Stage stage, StageOutcome outcome) {
        Counter.builder("access.query.stage")
            .tag("selection", selection.name())
            .tag("stage", stage.name())
            .tag("outcome", outcome.name())
            .description("权限查询引擎各阶段终态计数（低基数：选择/阶段/终态）")
            .register(registry)
            .increment();
    }

    @Override
    public void executionCompleted(ExecutionOutcome outcome) {
        executionCompleted(outcome, 0L);
    }

    @Override
    public void executionCompleted(ExecutionOutcome outcome, long durationNanos) {
        Timer.builder("access.query.execution")
            .tag("outcome", outcome.name())
            .description("权限查询引擎一次 execute 的终态与耗时")
            .publishPercentileHistogram()
            .register(registry)
            .record(Duration.ofNanos(durationNanos));
    }

    @Override
    public void evidenceSubmissionFailed(EvidenceKind evidenceKind) {
        Counter.builder("access.query.evidence.failed")
            .tag("kind", evidenceKind.name())
            .description("权限查询引擎审计证据受控提交失败计数")
            .register(registry)
            .increment();
    }
}
