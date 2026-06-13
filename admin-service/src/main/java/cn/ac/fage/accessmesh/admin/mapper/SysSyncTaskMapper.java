package cn.ac.fage.accessmesh.admin.mapper;

import cn.ac.fage.accessmesh.admin.entity.SysSyncTask;
import cn.ac.fage.accessmesh.admin.sync.model.SyncTaskQueryParams;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.paginate.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 同步任务数据访问接口
 * <p>
 * 对应数据库表 {@code sys_sync_task}，作为 admin-service 与 permission-center 之间
 * 的本地消息表（Outbox）。提供按消息键定位、按租户 ID 安全定位、按 due 时间扫描待执行任务、
 * 分页查询等基础操作。
 * </p>
 * <p>
 * 注意：S1 阶段仅落实表结构与重命名；任务认领（claim/locked_by）、
 * payloadVersion 校验、phase 推进等行为由 S4 任务生产器与 S5 调度器实施。
 * </p>
 */
@Mapper
public interface SysSyncTaskMapper extends BaseMapper<SysSyncTask> {

    /**
     * 根据消息键查询同步任务
     *
     * @param tenantId   租户ID
     * @param messageKey 消息键
     * @return 同步任务记录，未找到时返回 null
     */
    SysSyncTask selectByMessageKey(@Param("tenantId") Long tenantId,
                                   @Param("messageKey") String messageKey);

    /**
     * 根据ID安全查询同步任务（含租户隔离与删除标记过滤）
     *
     * @param id       主键ID
     * @param tenantId 租户ID
     * @return 同步任务记录，未找到时返回 null
     */
    SysSyncTask selectByIdSafe(@Param("id") Long id,
                               @Param("tenantId") Long tenantId);

    /**
     * 查询到期可执行的任务列表（status=PENDING 且 next_retry_at <= now 且 retry_count < max_retries）
     *
     * @param tenantId 租户ID
     * @param now      当前时间
     * @return 到期任务列表
     */
    List<SysSyncTask> selectDueTasks(@Param("tenantId") Long tenantId,
                                     @Param("now") LocalDateTime now);

    /**
     * 分页查询指定租户的同步任务记录，按创建时间倒序排列
     *
     * @param page     分页参数
     * @param tenantId 租户ID
     * @return 分页结果
     */
    Page<SysSyncTask> paginateByTenantId(@Param("page") Page<SysSyncTask> page,
                                         @Param("tenantId") Long tenantId);

    /**
     * 在同租户内按 (syncAction, businessKeyHash) 查找处于 PENDING 状态、未删除的任务。
     * <p>
     * 用于 S4 任务生产器的 PENDING 合并：若已存在则覆盖最新事件，
     * 不存在则插入新行。{@code PROCESSING/SUCCESS/FAILED} 状态的行不参与合并。
     * </p>
     *
     * @param tenantId        租户ID
     * @param syncAction      同步动作
     * @param businessKeyHash businessKey 的 SHA-256 lowercase hex
     * @return 已存在的 PENDING 任务，未找到时返回 null
     */
    SysSyncTask selectPendingByBusinessKeyHash(@Param("tenantId") Long tenantId,
                                               @Param("syncAction") String syncAction,
                                               @Param("businessKeyHash") String businessKeyHash);

    /**
     * 调度器认领到期任务
     * <p>
     * 一次 SQL 内将到期 PENDING 任务以及 PROCESSING 但 {@code locked_at < staleBefore}
     * 的任务切换为 {@code PROCESSING}，并写入 {@code locked_at=now}, {@code locked_by=workerId}。
     * 使用 {@code FOR UPDATE SKIP LOCKED} 避免多实例争用。
     * </p>
     *
     * @param now          当前时间
     * @param staleBefore  PROCESSING 锁陈旧判定阈值（{@code locked_at < staleBefore} 的任务可被抢占）
     * @param workerId     节点标识
     * @param batchSize    单次拉取上限
     * @return 实际被本节点认领的任务列表
     */
    List<SysSyncTask> claimDueTasks(@Param("now") LocalDateTime now,
                                    @Param("defaultStaleBefore") LocalDateTime defaultStaleBefore,
                                    @Param("fullSyncStaleBefore") LocalDateTime fullSyncStaleBefore,
                                    @Param("abstractUserStaleBefore") LocalDateTime abstractUserStaleBefore,
                                    @Param("abstractRoleStaleBefore") LocalDateTime abstractRoleStaleBefore,
                                    @Param("userRoleStaleBefore") LocalDateTime userRoleStaleBefore,
                                    @Param("resourceEntityStaleBefore") LocalDateTime resourceEntityStaleBefore,
                                    @Param("workerId") String workerId,
                                    @Param("batchSize") int batchSize);

    /**
     * CAS 标记任务为 SUCCESS（仅当 locked_by 匹配时生效）。
     */
    int markSuccessByWorker(@Param("id") Long id, @Param("workerId") String workerId,
                            @Param("now") LocalDateTime now);

    /**
     * CAS 重新入队待重试（仅当 locked_by 匹配时生效）。
     */
    int markRetryableByWorker(@Param("id") Long id, @Param("workerId") String workerId,
                              @Param("lastError") String lastError,
                              @Param("nextRetryAt") LocalDateTime nextRetryAt,
                              @Param("now") LocalDateTime now);

    /**
     * CAS 标记任务为终态 FAILED（仅当 locked_by 匹配时生效）。
     */
    int markFailedByWorker(@Param("id") Long id, @Param("workerId") String workerId,
                           @Param("lastError") String lastError,
                           @Param("now") LocalDateTime now);

    /**
     * 查询某租户下指定 sourceService 当前活跃的全量校准 batchKey（PENDING/PROCESSING 任意阶段任意一条任务）。
     * <p>
     * 用于 S6 全量校准编排器：在启动一次 batchKey 之前需要确认上一次批次已结束（全部 SUCCESS 或 FAILED）。
     * batchKey 格式约定为 {@code sourceService={sourceService}&runId={uuid}}，
     * 因此通过 {@code batch_key LIKE 'sourceService=...%'} 即可定位。
     * </p>
     *
     * @param tenantId      租户 ID
     * @param batchKeyPrefix batchKey 前缀，例如 {@code sourceService=admin-service&}
     * @return 活跃 batchKey；不存在时返回 null
     */
    String selectActiveBatchByTenantSource(@Param("tenantId") Long tenantId,
                                           @Param("batchKeyPrefix") String batchKeyPrefix);

    /**
     * 增强查询：按多维度过滤的分页列表查询。
     *
     * @param params  过滤参数（任意字段为 null 即跳过该条件，但 tenantId 必填）
     * @param offset  偏移量
     * @param limit   每页大小
     * @return 任务列表（按 created_at DESC 排序）
     */
    List<SysSyncTask> selectByQuery(@Param("params") SyncTaskQueryParams params,
                                    @Param("offset") long offset,
                                    @Param("limit") long limit);

    /**
     * 增强查询的总数统计，与 {@link #selectByQuery} 配合使用。
     */
    long countByQuery(@Param("params") SyncTaskQueryParams params);

    /**
     * 按 batchKeyHash 查询整批任务（用于状态聚合）。
     */
    List<SysSyncTask> selectAllByBatchKeyHash(@Param("tenantId") Long tenantId,
                                              @Param("batchKeyHash") String batchKeyHash);

    /**
     * 立即重试更新（仅 PENDING / FAILED 状态有效）。
     * <p>影响行数 = 0 表示前置状态约束不满足（已 SUCCESS 或处于 PROCESSING）。</p>
     */
    int markRetryNow(@Param("id") Long id,
                     @Param("tenantId") Long tenantId,
                     @Param("now") LocalDateTime now);

    /**
     * 重置任务（PROCESSING 排除）：清空 retry_count + last_error，回到 PENDING。
     */
    int markReset(@Param("id") Long id,
                  @Param("tenantId") Long tenantId,
                  @Param("now") LocalDateTime now);
}
