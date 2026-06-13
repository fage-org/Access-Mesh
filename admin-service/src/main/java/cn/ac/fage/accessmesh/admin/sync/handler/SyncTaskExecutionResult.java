package cn.ac.fage.accessmesh.admin.sync.handler;

/**
 * 同步任务执行结果
 * <p>
 * 由 {@link SyncTaskHandler} 返回给 {@code SyncTaskScheduler} 决定后续操作：
 * <ul>
 *   <li>{@code SUCCESS}：标记任务为终态 {@code SUCCESS}</li>
 *   <li>{@code STALE_VERSION}：直接标记为 {@code SUCCESS}（permission-center 已钝化，不重试）</li>
 *   <li>{@code RETRYABLE}：使用指数退避重新入队</li>
 *   <li>{@code DEPENDENCY_MISSING}：使用短退避重新入队</li>
 *   <li>{@code NON_RETRYABLE}：直接标记为终态 {@code FAILED}</li>
 *   <li>{@code SECURITY_DENIED}：直接标记为终态 {@code FAILED}</li>
 * </ul>
 *
 * @param outcome 处理结果分类
 * @param reason  可读的原因（用于审计/排查）
 */
public record SyncTaskExecutionResult(String outcome, String reason) {

    /** 同步成功落库。 */
    public static final String SUCCESS = "SUCCESS";
    /** 因 syncVersion 较旧被钝化，无需重试。 */
    public static final String STALE_VERSION = "STALE_VERSION";
    /** 可重试错误（网络/瞬时故障）。 */
    public static final String RETRYABLE = "RETRYABLE";
    /** 依赖缺失（前置任务未完成），短退避重试。 */
    public static final String DEPENDENCY_MISSING = "DEPENDENCY_MISSING";
    /** 非可重试错误（数据契约不合规等）。 */
    public static final String NON_RETRYABLE = "NON_RETRYABLE";
    /** 安全拒绝（鉴权失败）。 */
    public static final String SECURITY_DENIED = "SECURITY_DENIED";

    public static SyncTaskExecutionResult success() {
        return new SyncTaskExecutionResult(SUCCESS, null);
    }

    public static SyncTaskExecutionResult stale(String reason) {
        return new SyncTaskExecutionResult(STALE_VERSION, reason);
    }

    public static SyncTaskExecutionResult retryable(String reason) {
        return new SyncTaskExecutionResult(RETRYABLE, reason);
    }

    public static SyncTaskExecutionResult dependencyMissing(String reason) {
        return new SyncTaskExecutionResult(DEPENDENCY_MISSING, reason);
    }

    public static SyncTaskExecutionResult nonRetryable(String reason) {
        return new SyncTaskExecutionResult(NON_RETRYABLE, reason);
    }

    public static SyncTaskExecutionResult securityDenied(String reason) {
        return new SyncTaskExecutionResult(SECURITY_DENIED, reason);
    }
}
