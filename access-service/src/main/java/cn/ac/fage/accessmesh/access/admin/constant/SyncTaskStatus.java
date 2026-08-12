package cn.ac.fage.accessmesh.access.admin.constant;

/**
 * 同步任务状态常量
 * <p>
 * 对应 {@code sys_sync_task.status} 字段的合法取值。
 * 仅保留四个状态：
 * <ul>
 *     <li>{@link #PENDING} 待执行（含等待重试）</li>
 *     <li>{@link #PROCESSING} 任务被节点认领并正在执行</li>
 *     <li>{@link #SUCCESS} 同步成功</li>
 *     <li>{@link #FAILED} 同步失败（含达到最大重试次数后的终态）</li>
 * </ul>
 * 旧的 {@code RETRYING / EXHAUSTED / MAX_RETRIES_EXCEEDED} 已废弃，
 * 重试中状态由 {@link #PENDING} + {@code retry_count} 表达，
 * 终态失败由 {@link #FAILED} 表达。
 * </p>
 */
public final class SyncTaskStatus {

    /**
     * 待执行（包含正在等待下次重试）。
     */
    public static final String PENDING = "PENDING";

    /**
     * 已被节点认领，正在执行。
     */
    public static final String PROCESSING = "PROCESSING";

    /**
     * 同步成功。
     */
    public static final String SUCCESS = "SUCCESS";

    /**
     * 同步失败（含重试耗尽的终态）。
     */
    public static final String FAILED = "FAILED";

    private SyncTaskStatus() {}
}
