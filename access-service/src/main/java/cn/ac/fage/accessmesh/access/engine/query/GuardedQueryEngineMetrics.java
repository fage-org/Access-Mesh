package cn.ac.fage.accessmesh.access.engine.query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 打点防御包装（Micrometer 绑定 T-PERM-089+ 接线后的防御面）：指标后端异常不得
 * 放大为查询故障、覆盖主异常或跳过 RunState 释放。引擎与审计收集器两消费点统一经
 * {@link #guard} 防护（各自构造器内调用，直连构造路径同覆盖，T-PERM-094 复评 P3 收口）。
 */
final class GuardedQueryEngineMetrics implements QueryEngineMetrics {

    private static final Logger log = LoggerFactory.getLogger(GuardedQueryEngineMetrics.class);

    private final QueryEngineMetrics target;

    private GuardedQueryEngineMetrics(QueryEngineMetrics target) {
        this.target = target;
    }

    /** null 归一为 noop；失败吞掉仅 warn（观测面故障不影响查询语义）。 */
    static QueryEngineMetrics guard(QueryEngineMetrics metrics) {
        return new GuardedQueryEngineMetrics(metrics == null ? QueryEngineMetrics.noop() : metrics);
    }

    @Override
    public void itemStage(SelectionKind selection, Stage stage, StageOutcome outcome) {
        try {
            target.itemStage(selection, stage, outcome);
        } catch (RuntimeException error) {
            log.warn("Query stage metric failed: {}", error.getMessage());
        }
    }

    @Override
    public void executionCompleted(ExecutionOutcome outcome) {
        try {
            target.executionCompleted(outcome);
        } catch (RuntimeException error) {
            log.warn("Query execution metric failed: {}", error.getMessage());
        }
    }

    @Override
    public void executionCompleted(ExecutionOutcome outcome, long durationNanos) {
        try {
            target.executionCompleted(outcome, durationNanos);
        } catch (RuntimeException error) {
            log.warn("Query execution metric failed: {}", error.getMessage());
        }
    }

    @Override
    public void evidenceSubmissionFailed(EvidenceKind evidenceKind) {
        try {
            target.evidenceSubmissionFailed(evidenceKind);
        } catch (RuntimeException error) {
            log.warn("Query evidence metric failed: {}", error.getMessage());
        }
    }
}
