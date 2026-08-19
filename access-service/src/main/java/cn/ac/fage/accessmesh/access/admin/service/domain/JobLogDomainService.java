package cn.ac.fage.accessmesh.access.admin.service.domain;

/**
 * 任务执行日志领域服务
 * <p>
 * 提供定时任务执行日志的独立短事务写入（T-ACCESS-007 §8.2）。
 * 任务执行日志与调度主流程隔离，写入失败不影响任务执行结果。
 * </p>
 */
public interface JobLogDomainService {

    /**
     * 记录任务执行日志（独立短事务）
     *
     * @param tenantId     租户ID
     * @param jobId        任务ID
     * @param jobName      任务名称
     * @param invokeTarget 调用目标
     * @param status       执行状态（1成功，0失败）
     * @param message      执行消息/失败原因
     * @param costTime     执行耗时（毫秒）
     */
    void recordJobLog(Long tenantId, Long jobId, String jobName, String invokeTarget,
                      Integer status, String message, Integer costTime);
}
