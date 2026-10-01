package cn.ac.fage.accessmesh.access.engine.query;

/**
 * 引擎观测端口（T-PERM-088，设计 §6.1 指标低基数约束）。
 * <p>
 * 只接受枚举与布尔参数——标签维度在类型上限定为固定枚举集（选择类型／阶段／
 * 阶段终态／执行终态／证据类别），结构上排除 resourceCode、permissionId、itemKey
 * 等高基数标签进入指标。T-PERM-094 起 Micrometer 绑定落地
 * （{@code MicrometerQueryEngineMetrics}，2026-10-01 拍板「计数＋执行时长＋超限细分」档）；
 * 阶段级延迟拆分与定义缺失分布不新增指标，由执行时长＋阶段短路率计数与拒绝 reason 日志承载。
 * 本端口形状即为低基数契约的回归锁。
 * </p>
 */
public interface QueryEngineMetrics {

    /** 单项阶段终态：按选择类型＋阶段＋终态聚合（完成或短路理由）。 */
    void itemStage(SelectionKind selection, Stage stage, StageOutcome outcome);

    /** 一次 execute 终态：成功、预算超限或其余技术失败（失败不含任何目标标识维度）。 */
    void executionCompleted(ExecutionOutcome outcome);

    /** 带执行时长的终态上报（T-PERM-094：P50/P95/P99 观测）；缺省实现丢弃时长仅记终态。 */
    default void executionCompleted(ExecutionOutcome outcome, long durationNanos) {
        executionCompleted(outcome);
    }

    /** 证据受控提交失败：按证据类别计数（仅类别，不带 ID 标签）。 */
    void evidenceSubmissionFailed(EvidenceKind evidenceKind);

    /** 选择类型（Selection 具体形态）。 */
    enum SelectionKind { TYPE_LEVEL, TARGET_SET, GRANT_LIST, OPERATION_ADMISSION }

    /** 阶段终态（完成或映射自 SkipReason 的短路理由）。 */
    enum StageOutcome { COMPLETED, SKIPPED_NO_ROLE, SKIPPED_SUFFICIENT_DECISION, SKIPPED_PARENT_DENIED }

    /** 执行终态（BUDGET_EXCEEDED＝EngineLimits 预算/deadline 超限，容量信号与技术故障分开告警）。 */
    enum ExecutionOutcome { SUCCESS, BUDGET_EXCEEDED, TECHNICAL_FAILURE }

    /** 证据类别。 */
    enum EvidenceKind { PERM_RULE, ROLE_PAIR }

    /** 不打点实现（缺省形态）。 */
    static QueryEngineMetrics noop() {
        return new QueryEngineMetrics() {
            @Override public void itemStage(SelectionKind selection, Stage stage, StageOutcome outcome) {}
            @Override public void executionCompleted(ExecutionOutcome outcome) {}
            @Override public void evidenceSubmissionFailed(EvidenceKind evidenceKind) {}
        };
    }
}
