package cn.ac.fage.accessmesh.access.admin.sync.handler;

import cn.ac.fage.accessmesh.access.admin.entity.SysSyncTask;

/**
 * 同步任务处理器
 * <p>
 * 每个 syncAction（{@code PERM_ABSTRACT_USER_SYNC} 等）对应一个 handler 实现。
 * 由 {@code SyncTaskScheduler} 通过 {@link SyncTaskHandlerRegistry} 路由到具体实现。
 * </p>
 * <p>
 * Handler 实现需将 {@link SysSyncTask#getPayload()} 反序列化为 {@code Map<String,Object>}，
 * 调用 {@code permission-center} 的 sync/full-sync 接口，并把响应映射为
 * {@link SyncTaskExecutionResult}。任务的状态推进由 Scheduler 统一负责。
 * </p>
 */
public interface SyncTaskHandler {

    /**
     * 此 handler 支持的 {@code syncAction} 值
     * （{@code PERM_ABSTRACT_USER_SYNC} / {@code PERM_ABSTRACT_ROLE_SYNC} /
     * {@code PERM_USER_ROLE_SYNC} / {@code PERM_RESOURCE_ENTITY_SYNC}）。
     */
    String supportedAction();

    /**
     * 执行远程同步并返回结果。
     * <p>
     * 实现应捕获已知的瞬时异常（如 IOException）并返回
     * {@link SyncTaskExecutionResult#retryable}，其他未知异常应抛出，
     * 由 Scheduler 统一兜底为 RETRYABLE。
     * </p>
     *
     * @param task 已经被 claim 的任务
     * @return 执行结果
     */
    SyncTaskExecutionResult execute(SysSyncTask task);
}
