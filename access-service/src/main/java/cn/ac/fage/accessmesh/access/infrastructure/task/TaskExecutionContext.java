package cn.ac.fage.accessmesh.access.infrastructure.task;

/**
 * 任务执行上下文（T-ACCESS-009）
 * <p>
 * {@code @JobInvocable} 方法必须接收的单参数值对象（无参签名拒绝），承载本次执行的幂等标识：
 * {@code executionKey} 即外部副作用的幂等键（同一计划执行的所有重试/接管共用
 * 同一键，副作用方以键去重实现 at-least-once 不重复业务结果）。
 * </p>
 *
 * @param tenantId      租户ID
 * @param jobId         任务配置ID
 * @param executionKey  幂等执行键（job:{jobId}:{scheduledTime|manual:...}）
 * @param attemptCount  本次为第几次尝试（含首次与接管重试）
 * @param scheduledTime 计划触发时间；手动触发为 null
 */
public record TaskExecutionContext(
    Long tenantId,
    Long jobId,
    String executionKey,
    int attemptCount,
    java.time.LocalDateTime scheduledTime
) {
}
