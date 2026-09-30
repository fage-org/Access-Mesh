package cn.ac.fage.accessmesh.access.engine.query;

/** 超限是整次执行技术失败；只携带低基数预算类别，不附目标标识或上下文内容。 */
public final class QueryBudgetExceededException extends QueryExecutionException {
    private final EngineLimits.Kind kind;

    QueryBudgetExceededException(EngineLimits.Kind kind) {
        super("权限查询预算超限: " + kind, null);
        this.kind = kind;
    }

    public EngineLimits.Kind kind() { return kind; }
}
