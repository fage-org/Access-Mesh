package cn.ac.fage.accessmesh.access.infrastructure.mapper;

import com.mybatisflex.core.BaseMapper;
import cn.ac.fage.accessmesh.access.infrastructure.entity.SysTaskExecution;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 任务执行记录数据访问接口（T-ACCESS-002 预建，T-ACCESS-009 扩展原子并发 SQL）
 * <p>
 * 基础 CRUD 来自 BaseMapper；{@link #tryClaimExecution}/{@link #renewLease}/
 * {@link #completeExecution} 为单条原子条件 SQL，抢占与续租时间基准均为数据库
 * {@code now()}，多实例并发下同一执行键最多一个活动执行者。
 * attempt_count 同时是 fencing token：每次抢占递增，续租/完成按
 * 「lease_owner + attempt_count」双条件判定，防止旧执行尝试（含同实例接管
 * 自己过期任务的前一尝试）覆盖新尝试的结果。
 * </p>
 */
public interface SysTaskExecutionMapper extends BaseMapper<SysTaskExecution> {

    /**
     * 按租户与执行键查询有效执行记录
     *
     * @param tenantId     租户ID
     * @param executionKey 执行键
     * @return 执行记录，不存在返回null
     */
    SysTaskExecution selectByTenantAndExecutionKey(@Param("tenantId") Long tenantId,
                                                    @Param("executionKey") String executionKey);

    /**
     * 原子抢占一次计划执行（T-ACCESS-009）
     * <p>
     * 首次执行插入新行（attempt_count=1）；记录已存在时仅当未成功完成、
     * 租约不存在/已过期且未超最大尝试次数才允许抢占（attempt_count+1，
     * 故障接管与失败重试共用此路径）。所有条件在数据库端原子判定。
     * </p>
     *
     * @param tenantId     租户ID
     * @param executionKey 计划执行键
     * @param leaseOwner   抢占实例标识（host:pid）
     * @param leaseSeconds 租约时长（秒）
     * @param maxAttempts  最大尝试次数（含首次）
     * @return 抢占成功返回本次尝试号（attempt_count）；抢占失败返回 null
     */
    Integer tryClaimExecution(@Param("tenantId") Long tenantId,
                              @Param("executionKey") String executionKey,
                              @Param("leaseOwner") String leaseOwner,
                              @Param("leaseSeconds") int leaseSeconds,
                              @Param("maxAttempts") int maxAttempts);

    /**
     * 续租（T-ACCESS-009）
     * <p>
     * 仅当前持有者、租约未过期且 attempt 未被新抢占顶替时延长；
     * 返回 0 表示租约已丢失，调用方必须中止执行且不得写回结果。
     * </p>
     *
     * @param tenantId       租户ID
     * @param executionKey   执行键
     * @param leaseOwner     当前持有者标识
     * @param expectedAttempt 抢占时获得的尝试号（fencing）
     * @param leaseSeconds   续租时长（秒）
     * @return 1=续租成功；0=租约丢失
     */
    int renewLease(@Param("tenantId") Long tenantId,
                   @Param("executionKey") String executionKey,
                   @Param("leaseOwner") String leaseOwner,
                   @Param("expectedAttempt") int expectedAttempt,
                   @Param("leaseSeconds") int leaseSeconds);

    /**
     * 条件完成/失败写回（T-ACCESS-009）
     * <p>
     * 仅当前租约持有者且 attempt 未被顶替时可写回终态；
     * 返回 0 表示租约已丢失/被接管，本次结果被丢弃。
     * </p>
     *
     * @param tenantId        租户ID
     * @param executionKey    执行键
     * @param leaseOwner      当前持有者标识
     * @param expectedAttempt 抢占时获得的尝试号（fencing）
     * @param success         true=SUCCESS，false=FAILED
     * @param lastError       失败原因（成功时传null）
     * @return 1=写回成功；0=租约已丢失/被接管
     */
    int completeExecution(@Param("tenantId") Long tenantId,
                          @Param("executionKey") String executionKey,
                          @Param("leaseOwner") String leaseOwner,
                          @Param("expectedAttempt") int expectedAttempt,
                          @Param("success") boolean success,
                          @Param("lastError") String lastError);

    /**
     * 查询可重试执行（T-ACCESS-009 接管扫描）
     * <p>
     * 两种候选：RUNNING 且租约已过期（持有实例故障）；FAILED 且未超最大尝试
     * （失败后至少一次重试）。
     * </p>
     *
     * @param maxAttempts 最大重试次数（attempt_count 达到后不再重试）
     * @param limit       单批上限
     * @return 可重试的执行记录
     */
    List<SysTaskExecution> selectRetryable(@Param("maxAttempts") int maxAttempts,
                                           @Param("limit") int limit);

    /**
     * 将超过最大重试次数的过期 RUNNING 执行收敛为 FAILED（T-ACCESS-009）
     *
     * @param maxAttempts 最大重试次数
     * @param lastError   收敛原因
     * @return 受影响行数
     */
    int failExpiredOverMaxAttempts(@Param("maxAttempts") int maxAttempts,
                                   @Param("lastError") String lastError);

    /**
     * 收敛不可重试的执行（T-ACCESS-009，如任务配置已删除/执行键无法解析）：
     * attempt_count 拉满到 maxAttempts，使其不再满足重试候选与抢占条件，
     * 防止僵尸记录每轮占据接管批次导致有效重试饥饿。
     * <p>
     * fencing：按候选读取时的快照（status + attempt_count）条件更新，且不碰
     * SUCCESS 行、RUNNING 候选要求租约仍过期——读取后被其他实例抢占/完成的
     * 行更新不生效（返回 0）。
     * </p>
     *
     * @param tenantId        租户ID
     * @param executionKey    执行键
     * @param expectedStatus  候选读取时的状态（RUNNING/FAILED）
     * @param expectedAttempt 候选读取时的尝试号
     * @param maxAttempts     最大尝试次数
     * @param reason          收敛原因
     * @return 受影响行数
     */
    int abandonExecution(@Param("tenantId") Long tenantId,
                         @Param("executionKey") String executionKey,
                         @Param("expectedStatus") String expectedStatus,
                         @Param("expectedAttempt") int expectedAttempt,
                         @Param("maxAttempts") int maxAttempts,
                         @Param("reason") String reason);
}
