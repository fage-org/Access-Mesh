package cn.ac.fage.accessmesh.access.admin.service.domain;

import cn.ac.fage.accessmesh.access.infrastructure.entity.SysTaskExecution;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务执行租约领域服务（T-ACCESS-009）
 * <p>
 * 基于 {@code sys_task_execution} 的数据库租约：原子抢占、续租、
 * 条件完成与重试候选查询。所有时间判定使用数据库时间（SQL 内 now()），
 * Redis 不承担任务正确性。attempt_count 是 fencing token：每次抢占递增，
 * 续租/完成按「lease_owner + attempt_count」双条件判定，防止旧执行尝试
 * （含同实例接管自己过期任务的前一尝试）续租或覆盖新尝试的结果。
 * 方法不声明事务——每条原子 SQL 即一个隐式短事务。
 * </p>
 */
public interface TaskExecutionDomainService {

    /** 租约时长（秒）：持有者须在该窗口内完成或续租 */
    int LEASE_SECONDS = 60;

    /** 续租间隔（秒）：约为租约的 1/3，允许一次续租失败仍保有租约 */
    int RENEW_INTERVAL_SECONDS = 20;

    /** 单次执行键最大尝试次数（含首次执行与全部重试） */
    int MAX_ATTEMPTS = 3;

    /**
     * 构建计划触发执行键
     * <p>
     * 计划时刻秒级截断；各实例部署约定同一时区（项目约定，不做跨时区支持），
     * 同一触发时刻生成相同执行键。
     * </p>
     *
     * @param jobId         任务ID
     * @param scheduledTime 计划触发时间（秒级截断）
     * @return 执行键 job:{jobId}:{scheduledTime}
     */
    String buildScheduledKey(Long jobId, LocalDateTime scheduledTime);

    /**
     * 构建手动触发执行键（每次手动触发为独立执行）
     *
     * @param jobId 任务ID
     * @return 执行键 job:{jobId}:manual:{epochMilli}-{UUID}
     */
    String buildManualKey(Long jobId);

    /**
     * 从执行键解析任务ID（接管扫描用）
     *
     * @param executionKey 执行键
     * @return 任务ID；非 job: 键格式返回 null
     */
    Long parseJobId(String executionKey);

    /**
     * 从执行键恢复原计划触发时间（接管重试用，语义与首次执行一致）
     *
     * @param executionKey 执行键
     * @return 计划触发时间；手动触发键或无法解析返回 null
     */
    LocalDateTime parseScheduledTime(String executionKey);

    /**
     * 原子抢占一次执行（含故障接管与失败重试）
     *
     * @param tenantId     租户ID
     * @param executionKey 执行键
     * @param leaseOwner   实例标识（host:pid）
     * @return 抢占成功返回本次尝试号（fencing token）；失败返回 null
     */
    Integer tryClaim(Long tenantId, String executionKey, String leaseOwner);

    /**
     * 续租（按尝试号 fencing）；返回 false 表示租约已丢失，
     * 调用方必须中止且不得写回结果
     *
     * @param attempt 抢占时获得的尝试号
     */
    boolean renewLease(Long tenantId, String executionKey, String leaseOwner, int attempt);

    /**
     * 条件完成/失败写回（按尝试号 fencing）；返回 false 表示租约已丢失/被接管，
     * 本次结果被丢弃
     *
     * @param attempt   抢占时获得的尝试号
     * @param success   true=SUCCESS，false=FAILED
     * @param lastError 失败原因（成功时 null）
     */
    boolean complete(Long tenantId, String executionKey, String leaseOwner, int attempt,
                     boolean success, String lastError);

    /**
     * 查询可重试执行：RUNNING 且租约过期（持有实例故障），或 FAILED 且未超
     * 最大尝试次数（失败后至少一次重试，§8.1 语义）
     *
     * @param limit 单批上限
     * @return 可重试执行记录
     */
    List<SysTaskExecution> findRetryable(int limit);

    /**
     * 将超过最大重试次数的过期 RUNNING 执行收敛为 FAILED
     *
     * @return 受影响行数
     */
    int failExpiredOverMaxAttempts();

    /**
     * 收敛不可重试的执行（任务配置已删除/执行键无法解析）：attempt 拉满使其
     * 不再进入重试候选，防止僵尸记录占据接管批次。
     * <p>
     * fencing：按候选快照（status + attempt）条件更新，读取后被其他实例
     * 抢占/完成的行不受影响。
     * </p>
     *
     * @param execution 候选读取时的执行快照
     * @param reason    收敛原因
     * @return 受影响行数
     */
    int abandonExecution(cn.ac.fage.accessmesh.access.infrastructure.entity.SysTaskExecution execution,
                         String reason);

    /**
     * 按租户与执行键查询执行记录
     */
    SysTaskExecution findByExecutionKey(Long tenantId, String executionKey);
}
