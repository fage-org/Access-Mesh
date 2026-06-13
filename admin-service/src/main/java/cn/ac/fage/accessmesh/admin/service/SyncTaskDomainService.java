package cn.ac.fage.accessmesh.admin.service;

import cn.ac.fage.accessmesh.admin.entity.SysSyncTask;
import cn.ac.fage.accessmesh.admin.sync.model.SyncTaskBatchStatusReport;
import cn.ac.fage.accessmesh.admin.sync.model.SyncTaskClaimTimeouts;
import cn.ac.fage.accessmesh.admin.sync.model.SyncTaskEnvelope;
import cn.ac.fage.accessmesh.admin.sync.model.SyncTaskQueryParams;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;

import java.time.Duration;
import java.util.List;

/**
 * 同步任务领域服务接口
 * <p>
 * 提供 {@code sys_sync_task} 本地消息表的领域级操作（入队、状态推进、查询、删除）。
 * 任务的扫描调度（authorization claim、phase 推进、payloadVersion 校验）由 S5 调度器实施；
 * 全量校准 phase 推进由 S6 实施。
 * </p>
 * <p>
 * 命名遵循 permission-center 编码规范：DomainService 提供领域规则与缓存管理，
 * 不声明事务边界，由上层 AppService / 调用方决定事务作用域。
 * </p>
 */
public interface SyncTaskDomainService {

    /**
     * 单条入队：将 {@link SyncTaskEnvelope} 写入 {@code sys_sync_task}。
     * <p>
     * 合并语义（同事务内）：先按 {@code (tenantId, syncAction, businessKey_hash, status='PENDING', delete_flag=0)}
     * 查找；若存在则覆盖该行的 {@code messageKey/businessKey/payload/payloadVersion/displayAttrs/syncOccurredAt/syncSequenceNo}
     * 为新事件，{@code retry_count} 不重置；若不存在则插入新行。
     * {@code PROCESSING/SUCCESS/FAILED} 状态的行不动。
     * {@link SyncTaskEnvelope#messageKey()} 为 {@code null} 时由本方法生成 UUID。
     * </p>
     *
     * @param tenantId 租户 ID
     * @param envelope 同步任务信封
     */
    void enqueue(Long tenantId, SyncTaskEnvelope envelope);

    /**
     * 批量入队：调用 {@link #enqueue} 逐条处理。
     *
     * @param tenantId  租户 ID
     * @param envelopes 同步任务信封列表
     */
    void enqueueAll(Long tenantId, List<SyncTaskEnvelope> envelopes);

    /**
     * 标记任务执行成功（终态 SUCCESS）
     *
     * @param id 任务ID
     */
    void markSuccess(Long id);

    /**
     * 调度器原子认领到期任务（S5 调度器使用）
     * <p>
     * 一次 SQL 内将到期 PENDING 任务以及锁超时的 PROCESSING 任务原子地切换为
     * {@code PROCESSING} 状态，并写入 {@code locked_at=now()} 和 {@code locked_by=workerId}。
     * 使用 {@code FOR UPDATE SKIP LOCKED} 避免多实例争用。
     * </p>
     *
     * @param batchSize   单次拉取上限
     * @param workerId    调度器节点标识（用于 CAS 校验）
     * @param maxLockAge  PROCESSING 锁超时阈值；超过则视为锁陈旧可被抢占
     * @return 本次成功认领的任务列表（不含其他 worker 持有的锁）
     */
    List<SysSyncTask> claimDueTasks(int batchSize, String workerId, SyncTaskClaimTimeouts timeouts);

    /**
     * 调度器版 markSuccess：通过 {@code lockedBy} CAS 校验后置为 SUCCESS。
     *
     * @param taskId   任务ID
     * @param workerId 节点标识
     */
    void markSuccess(Long taskId, String workerId);

    /**
     * 调度器版可重试标记：retry_count+1 + 退避 + 释放锁。
     * <p>调用方负责判断是否超过 max_retries；本方法只做一次重新入队。</p>
     *
     * @param taskId    任务ID
     * @param workerId  节点标识
     * @param lastError 错误信息（截断后保存）
     * @param backoff   退避时长
     */
    void markRetryable(Long taskId, String workerId, String lastError, Duration backoff);

    /**
     * 调度器版终态失败标记。
     *
     * @param taskId     任务ID
     * @param workerId   节点标识
     * @param retryClass 终态分类（NON_RETRYABLE / SECURITY_DENIED 等）
     * @param reason     原因
     */
    void markFailed(Long taskId, String workerId, String retryClass, String reason);

    /**
     * 标记任务执行失败（更新错误信息与重试次数；达到 max_retries 时置为终态 FAILED）
     *
     * @param id    任务ID
     * @param error 错误信息
     */
    void markFailed(Long id, String error);

    /**
     * 获取到期可执行的任务列表
     * <p>
     * 当前租户中状态为 PENDING、retry_count &lt; max_retries 且 next_retry_at 已到的任务，
     * 按创建时间正序排列。
     * </p>
     *
     * @return 到期任务列表
     */
    List<SysSyncTask> getDueTasks();

    /**
     * 软删除已处理任务（SUCCESS 或 FAILED 终态记录的归档清理）
     *
     * @param id 任务ID
     */
    void deleteProcessed(Long id);

    /**
     * 分页查询当前租户的同步任务
     *
     * @param pageReq 分页参数
     * @return 分页任务列表
     */
    PaginatedResult<SysSyncTask> page(PageReq pageReq);

    /**
     * 立即重试任务：状态 PENDING / FAILED 时切回 PENDING 并将 next_retry_at 设为 now。
     * <p>retry_count 不重置。SUCCESS 或 PROCESSING 状态抛 {@link cn.ac.fage.accessmesh.common.exception.BizException}。</p>
     *
     * @param taskId 任务 ID
     */
    void retryNow(Long taskId);

    /**
     * 重置任务：retry_count = 0，last_error = NULL，回到 PENDING 初始状态。
     * <p>PROCESSING 状态拒绝（避免与活跃 worker 冲突），抛 {@link cn.ac.fage.accessmesh.common.exception.BizException}。</p>
     *
     * @param taskId 任务 ID
     */
    void resetTask(Long taskId);

    /**
     * 查询某 batchKey 下的进度报告。
     *
     * @param batchKey 批次键原文（service 内部 hash 后查询）
     * @return 状态报告
     */
    SyncTaskBatchStatusReport queryBatchStatus(String batchKey);

    /**
     * 增强查询：按多维度过滤的分页列表查询。
     *
     * @param params   过滤参数（tenantId 由 service 注入）
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 分页结果
     */
    PaginatedResult<SysSyncTask> listEnhanced(SyncTaskQueryParams params, int pageNum, int pageSize);

    /**
     * 根据 ID 安全查询任务（含租户隔离），未找到时返回 null。
     */
    SysSyncTask getByIdSafe(Long taskId);
}
