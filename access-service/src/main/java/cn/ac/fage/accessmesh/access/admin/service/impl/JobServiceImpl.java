package cn.ac.fage.accessmesh.access.admin.service.impl;

import cn.ac.fage.accessmesh.access.admin.service.domain.JobInvokeDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.JobLogDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.TaskExecutionDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.infrastructure.aop.OperationLog;
import cn.ac.fage.accessmesh.access.infrastructure.entity.SysTaskExecution;
import cn.ac.fage.accessmesh.access.infrastructure.task.TaskExecutionContext;
import cn.ac.fage.accessmesh.access.admin.dto.resp.JobLogResp;
import cn.ac.fage.accessmesh.access.admin.dto.resp.JobResp;
import cn.ac.fage.accessmesh.access.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.JobCreateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.JobLogPageReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.JobUpdateReq;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.access.admin.entity.SysJob;
import cn.ac.fage.accessmesh.access.admin.entity.SysJobLog;
import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.access.admin.mapper.SysJobLogMapper;
import cn.ac.fage.accessmesh.access.admin.mapper.SysJobMapper;
import cn.ac.fage.accessmesh.access.admin.service.JobService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 定时任务管理服务实现类
 * <p>
 * 提供定时任务的CRUD操作、启停控制、手动触发、执行日志查询等功能。
 * 使用Spring TaskScheduler实现任务调度，支持Cron表达式配置。
 * 应用启动时自动加载并调度所有启用的任务。
 * 使用细粒度锁池（按jobId分组）保护调度操作，避免全局锁竞争和并发问题。
 * </p>
 * <p>
 * 任务执行编排（T-ACCESS-009，调度层职责）：多实例继续各自触发 Spring
 * Scheduler，同一计划时刻以稳定执行键在数据库原子抢占（{@link TaskExecutionDomainService}），
 * 抢占成功者提交专用执行器异步执行并后台续租；租约丢失（按尝试号 fencing）
 * 后结果不写回。执行编排方法不声明事务——业务事务由任务方法自身决定。
 * </p>
 */
@Service
public class JobServiceImpl implements JobService {

    private static final Logger log = LoggerFactory.getLogger(JobServiceImpl.class);
    private static final int JOB_STATUS_DISABLED = 0;
    private static final int JOB_STATUS_ENABLED = 1;

    /** 接管扫描单批上限 */
    private static final int TAKEOVER_BATCH_LIMIT = 20;

    private final SysJobMapper jobMapper;
    private final SysJobLogMapper jobLogMapper;
    private final TaskScheduler taskScheduler;
    private final TaskScheduler leaseRenewalScheduler;
    private final AdminPermissionValidator permissionValidator;
    private final TaskExecutionDomainService taskExecutionDomainService;
    private final JobInvokeDomainService jobInvokeDomainService;
    private final JobLogDomainService jobLogDomainService;
    private final TaskExecutor taskExecutor;
    private final String leaseOwner;
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    /**
     * 已调度任务的 cron 快照（jobId → cron），对账 diff 用：cron 未变化跳过重建。
     * 与 {@link #scheduledTasks} 同步维护，读取走弱一致迭代。
     */
    private final Map<Long, String> scheduledCrons = new ConcurrentHashMap<>();

    /**
     * 细粒度锁池
     * <p>
     * 按jobId分组，避免全局锁竞争。
     * 解决并发场景下任务重复调度和ScheduledFuture泄漏问题。
     * </p>
     */
    private final ConcurrentHashMap<Long, ReentrantLock> jobLocks = new ConcurrentHashMap<>();

    /**
     * 构造函数注入依赖
     *
     * @param jobMapper 任务数据访问Mapper
     * @param jobLogMapper 任务日志数据访问Mapper
     * @param taskScheduler 共享任务调度器（cron 触发；容器内与续租调度器并存，按名限定）
     * @param leaseRenewalScheduler 租约续租专用调度器（与共享调度器隔离的正确性路径）
     * @param permissionValidator 权限校验器
     * @param taskExecutionDomainService 任务租约领域服务（原子抢占/续租/条件完成，T-ACCESS-009）
     * @param jobInvokeDomainService 任务调用目标执行领域服务（@JobInvocable 白名单反射，T-ACCESS-009）
     * @param jobLogDomainService 任务执行日志独立短事务领域服务
     * @param taskExecutor 任务执行专用有界线程池（accessTaskExecutor，T-ACCESS-009）
     */
    public JobServiceImpl(SysJobMapper jobMapper, SysJobLogMapper jobLogMapper,
                          @Qualifier("taskScheduler") TaskScheduler taskScheduler,
                          @Qualifier("taskLeaseRenewalScheduler") TaskScheduler leaseRenewalScheduler,
                          AdminPermissionValidator permissionValidator,
                          TaskExecutionDomainService taskExecutionDomainService,
                          JobInvokeDomainService jobInvokeDomainService,
                          JobLogDomainService jobLogDomainService,
                          @Qualifier("accessTaskExecutor") TaskExecutor taskExecutor) {
        this.jobMapper = jobMapper;
        this.jobLogMapper = jobLogMapper;
        this.taskScheduler = taskScheduler;
        this.leaseRenewalScheduler = leaseRenewalScheduler;
        this.permissionValidator = permissionValidator;
        this.taskExecutionDomainService = taskExecutionDomainService;
        this.jobInvokeDomainService = jobInvokeDomainService;
        this.jobLogDomainService = jobLogDomainService;
        this.taskExecutor = taskExecutor;
        this.leaseOwner = resolveLeaseOwner();
    }

    /** 实例租约标识 host:pid（同一实例接管自己的过期任务时靠 attempt fencing 区分） */
    private static String resolveLeaseOwner() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "unknown-host";
        }
        return host + ":" + ProcessHandle.current().pid();
    }

    /**
     * 获取指定jobId对应的锁
     * <p>
     * 懒加载锁对象，按jobId分组避免全局锁竞争。
     * </p>
     *
     * @param jobId 任务ID
     * @return 该任务对应的锁对象
     */
    private ReentrantLock getLockForJob(Long jobId) {
        return jobLocks.computeIfAbsent(jobId, id -> new ReentrantLock());
    }

    /**
     * 初始化任务调度
     * <p>
     * 应用启动时跨租户单条批量加载所有启用任务并调度（§8.4.8：禁止按租户循环查询）。
     * 通过@PostConstruct注解在Bean初始化后执行。
     * </p>
     */
    @PostConstruct
    public void initScheduledTasks() {
        log.info("Initializing scheduled tasks from database...");
        List<SysJob> enabledJobs;
        try {
            enabledJobs = jobMapper.selectAllEnabledJobs();
        } catch (Exception e) {
            // 加载失败不抛出（不阻断启动）：当前无调度，首轮对账（60s 后）兜底加载
            log.error("Failed to load jobs on startup, first reconcile will retry", e);
            return;
        }
        for (SysJob job : enabledJobs) {
            try {
                scheduleJob(job);
                log.info("Loaded job on startup: tenant={}, id={}, name={}",
                    job.getTenantId(), job.getId(), job.getJobName());
            } catch (Exception e) {
                log.error("Failed to schedule job on startup: id={}", job.getId(), e);
            }
        }
        log.info("Initialized {} scheduled tasks", enabledJobs.size());
    }

    /**
     * 创建定时任务
     * <p>
     * 创建新的定时任务，设置任务名称、Cron表达式、调用目标等。
     * 执行类型级权限校验(CREATE)。
     * 如果任务状态为启用，创建后立即调度。
     * </p>
     *
     * @param job 任务实体，包含任务配置信息
     * @return 新任务ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "ADMIN", action = "JOB_CREATE", targetType = "sys_job",
        targetId = "#result", summary = "'create job'")
    public Long createJob(JobCreateReq req) {
        Long tenantId = TenantContextHolder.getTenantId();

        permissionValidator.checkTypeLevel(ResourceTypeCode.ADMIN_JOB, AdminOperationCode.CREATE);

        SysJob job = new SysJob();
        job.setTenantId(tenantId);
        job.setJobName(req.jobName());
        job.setJobGroup(req.jobGroup() != null ? req.jobGroup() : "DEFAULT");
        job.setInvokeTarget(req.invokeTarget());
        job.setCronExpression(req.cronExpression());
        job.setMisfirePolicy(req.misfirePolicy() != null ? req.misfirePolicy() : 1);
        job.setStatus(req.status() != null ? req.status() : JOB_STATUS_ENABLED);
        job.setRemark(req.remark());
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        job.setDeleteFlag(0L);
        jobMapper.insert(job);
        if (job.getStatus() == JOB_STATUS_ENABLED) {
            scheduleJob(job);
        }
        return job.getId();
    }

    /**
     * 更新定时任务
     * <p>
     * 更新任务的配置信息，如Cron表达式、调用目标等。
     * 执行实例级权限校验(UPDATE)。
     * 如果任务原状态为启用，先取消调度再更新。
     * 如果新状态为启用，更新后重新调度。
     * </p>
     *
     * @param job 任务实体，包含任务ID和新配置信息
     * @throws BizException 任务不存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "ADMIN", action = "JOB_UPDATE", targetType = "sys_job",
        targetId = "#req.id()", summary = "'update job ' + #req.id()")
    public void updateJob(JobUpdateReq req) {
        Long tenantId = TenantContextHolder.getTenantId();
        SysJob existing = jobMapper.selectValidById(tenantId, req.id());
        if (existing == null) {
            throw new BizException(AdminErrorCode.JOB_NOT_FOUND.getCode(), AdminErrorCode.JOB_NOT_FOUND.getMessage());
        }

        // 权限检查 — 实例级 UPDATE
        permissionValidator.checkInstanceLevel(ResourceTypeCode.ADMIN_JOB, req.id().toString(), AdminOperationCode.UPDATE);

        // 若当前正在运行，先取消调度
        if (existing.getStatus() == JOB_STATUS_ENABLED) {
            unscheduleJob(req.id());
        }

        if (req.jobName() != null) existing.setJobName(req.jobName());
        if (req.jobGroup() != null) existing.setJobGroup(req.jobGroup());
        if (req.invokeTarget() != null) existing.setInvokeTarget(req.invokeTarget());
        if (req.cronExpression() != null) existing.setCronExpression(req.cronExpression());
        if (req.misfirePolicy() != null) existing.setMisfirePolicy(req.misfirePolicy());
        if (req.status() != null) existing.setStatus(req.status());
        if (req.remark() != null) existing.setRemark(req.remark());
        existing.setUpdatedAt(LocalDateTime.now());
        jobMapper.update(existing);

        if (existing.getStatus() == JOB_STATUS_ENABLED) {
            scheduleJob(existing);
        }
    }

    /**
     * 批量删除定时任务
     * <p>
     * 执行批量实例级权限校验后软删除任务。
     * 删除前先取消任务调度，防止已删除任务继续执行。
     * 使用批量查询和批量软删除优化性能。
     * </p>
     *
     * @param req ID集合请求，包含待删除的任务ID列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "ADMIN", action = "JOB_DELETE", targetType = "sys_job",
        targetId = "", summary = "'batch delete jobs'")
    public void deleteJobs(IdsReq req) {
        Long tenantId = TenantContextHolder.getTenantId();

        // 权限检查 — 批量实例级 DELETE
        List<String> resourceCodes = req.ids().stream().map(String::valueOf).toList();
        permissionValidator.checkBatchInstanceLevel(ResourceTypeCode.ADMIN_JOB, resourceCodes, AdminOperationCode.DELETE);

        // 先取消调度（必须循环执行调度器操作）
        for (Long id : req.ids()) {
            unscheduleJob(id);
        }

        // 批量查询有效任务（性能优化：避免 N+1 SELECT 查询）
        List<SysJob> jobs = jobMapper.selectValidByIds(tenantId, req.ids());

        // 批量软删除（性能优化：单次 SQL 替代循环）
        if (!jobs.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            List<Long> validIds = jobs.stream().map(SysJob::getId).collect(java.util.stream.Collectors.toList());
            jobMapper.softDeleteBatch(tenantId, validIds, now);
        }
    }

    /**
     * 切换任务状态（启用/停用）
     * <p>
     * 启用或停用指定任务。
     * 执行实例级权限校验(ENABLE/DISABLE)。
     * 启用时立即调度，停用时取消调度。
     * </p>
     *
     * @param id 任务ID
     * @param status 新状态（1启用，0停用）
     * @throws BizException 任务不存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @OperationLog(module = "ADMIN", action = "JOB_TOGGLE", targetType = "sys_job",
        targetId = "#id", summary = "'toggle job ' + #id")
    public void toggleJobStatus(Long id, Integer status) {
        Long tenantId = TenantContextHolder.getTenantId();
        SysJob job = jobMapper.selectValidById(tenantId, id);
        if (job == null) {
            throw new BizException(AdminErrorCode.JOB_NOT_FOUND.getCode(), AdminErrorCode.JOB_NOT_FOUND.getMessage());
        }

        // 权限检查 — 实例级 ENABLE（启用/禁用共用，toggle 语义，v1.4 合并）
        permissionValidator.checkInstanceLevel(ResourceTypeCode.ADMIN_JOB, id.toString(), AdminOperationCode.ENABLE);

        job.setStatus(status);
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.update(job);
        if (status == JOB_STATUS_ENABLED) {
            scheduleJob(job);
        } else {
            unscheduleJob(id);
        }
    }

    /**
     * 手动触发任务执行
     * <p>
     * 立即执行指定任务，不受调度时间限制。
     * 执行实例级权限校验(TRIGGER)。
     * </p>
     *
     * @param id 任务ID
     * @throws BizException 任务不存在
     */
    @Override
    @OperationLog(module = "ADMIN", action = "JOB_TRIGGER", targetType = "sys_job",
        targetId = "#id", summary = "'trigger job ' + #id")
    public void triggerJob(Long id) {
        // 权限检查 — 实例级 TRIGGER
        permissionValidator.checkInstanceLevel(ResourceTypeCode.ADMIN_JOB, id.toString(), AdminOperationCode.TRIGGER);

        Long tenantId = TenantContextHolder.getTenantId();
        SysJob job = jobMapper.selectValidById(tenantId, id);
        if (job == null) {
            throw new BizException(AdminErrorCode.JOB_NOT_FOUND.getCode(), AdminErrorCode.JOB_NOT_FOUND.getMessage());
        }
        executeJob(job, null);
    }

    /**
     * 获取任务详情
     * <p>
     * 根据任务ID查询任务完整信息。
     * </p>
     *
     * @param id 任务ID
     * @return 任务详情响应
     */
    @Override
    public JobResp getJob(Long id) {
        Long tenantId = TenantContextHolder.getTenantId();
        SysJob job = jobMapper.selectValidById(tenantId, id);
        return JobResp.from(job);
    }

    /**
     * 分页查询任务列表
     * <p>
     * 支持按任务组过滤，按创建时间倒序排列。
     * </p>
     *
     * @param pageReq 分页查询请求，包含分页参数
     * @param jobGroup 任务组过滤条件，可选
     * @return 分页任务列表结果
     */
    @Override
    public PaginatedResult<JobResp> pageJobs(PageReq pageReq, String jobGroup) {
        Long tenantId = TenantContextHolder.getTenantId();

        Page<SysJob> result = jobMapper.paginateJobs(Page.of(pageReq.pageNum(), pageReq.pageSize()), tenantId, jobGroup);

        List<JobResp> items = result.getRecords().stream()
            .map(JobResp::from)
            .toList();

        long totalPages = (result.getTotalRow() + pageReq.pageSize() - 1) / pageReq.pageSize();
        return new PaginatedResult<>(items,
            new PaginatedResult.PaginationMeta(result.getTotalRow(), pageReq.pageNum(), pageReq.pageSize(), (int) totalPages));
    }

    /**
     * 分页查询任务执行日志
     * <p>
     * 查询任务的执行历史记录，支持按任务ID过滤。
     * 按创建时间倒序排列，最新的执行记录在前。
     * </p>
     *
     * @param pageReq 分页查询请求，包含分页参数
     * @param jobId 任务ID过滤条件，可选
     * @return 分页任务日志列表结果
     */
    @Override
    public PaginatedResult<JobLogResp> pageJobLogs(JobLogPageReq pageReq, Long jobId) {
        Long tenantId = TenantContextHolder.getTenantId();

        Page<SysJobLog> result = jobLogMapper.paginateJobLogs(Page.of(pageReq.pageNum(), pageReq.pageSize()), tenantId, jobId);

        List<JobLogResp> items = result.getRecords().stream()
            .map(JobLogResp::from)
            .toList();

        long totalPages = (result.getTotalRow() + pageReq.pageSize() - 1) / pageReq.pageSize();
        return new PaginatedResult<>(items,
            new PaginatedResult.PaginationMeta(result.getTotalRow(), pageReq.pageNum(), pageReq.pageSize(), (int) totalPages));
    }

    /**
     * 调度任务（真 diff：cron 未变化直接跳过，避免对账每轮全量取消/重建）
     * <p>
     * 使用ReentrantLock保护调度操作，解决并发问题。
     * 先构造 Trigger 再取消旧调度：cron 非法等构造失败时保留旧调度，
     * 不因一次失败把任务打成未调度。
     * </p>
     *
     * @param job 任务实体
     */
    private void scheduleJob(SysJob job) {
        ReentrantLock lock = getLockForJob(job.getId());
        lock.lock();
        try {
            String scheduledCron = scheduledCrons.get(job.getId());
            if (scheduledCron != null && scheduledCron.equals(job.getCronExpression())) {
                return;
            }
            ExecutionKeyCronTrigger trigger = new ExecutionKeyCronTrigger(job.getCronExpression());
            unscheduleJobInternal(job.getId());
            ScheduledFuture<?> future = taskScheduler.schedule(
                () -> executeJob(job, trigger.lastComputedFireTime()), trigger);
            scheduledTasks.put(job.getId(), future);
            scheduledCrons.put(job.getId(), job.getCronExpression());
            log.info("Scheduled job: id={}, cron={}", job.getId(), job.getCronExpression());
        } catch (Exception e) {
            log.error("Failed to schedule job: id={}", job.getId(), e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 取消任务调度
     * <p>
     * 使用ReentrantLock保护取消操作。
     * 从调度表中移除并取消ScheduledFuture。
     * </p>
     *
     * @param jobId 任务ID
     */
    private void unscheduleJob(Long jobId) {
        ReentrantLock lock = getLockForJob(jobId);
        lock.lock();
        try {
            unscheduleJobInternal(jobId);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 取消任务调度（内部方法）
     * <p>
     * 不加锁，在锁保护下调用。
     * 避免锁嵌套，减少死锁风险。
     * </p>
     *
     * @param jobId 任务ID
     */
    private void unscheduleJobInternal(Long jobId) {
        ScheduledFuture<?> future = scheduledTasks.remove(jobId);
        scheduledCrons.remove(jobId);
        if (future != null) {
            future.cancel(false);
            log.info("Unscheduled job: id={}", jobId);
        }
    }

    /**
     * 执行任务（T-ACCESS-009 数据库租约，调度层编排入口）
     * <p>
     * 多实例继续各自触发 Spring Scheduler；同一计划时刻由稳定执行键
     * （jobId + scheduledTime）在数据库原子抢占，只有抢占成功者进入业务执行。
     * 抢占/续租/接管/条件完成的正确性全部由数据库原子条件 SQL 保证（含
     * attempt 级 fencing），Redis 不参与。执行键同时是外部副作用的幂等键。
     * 计划触发时重读数据库任务行（权威配置）：已删除/已停用的任务跳过本次执行，
     * 变更后的 invokeTarget 立即生效（多实例配置漂移由 {@link #reconcileScheduledJobs()}
     * 周期对账收敛，漂移窗口内以数据库行为准）。
     * </p>
     *
     * @param job           任务实体（调度快照或手动触发读取）
     * @param scheduledTime 本轮计划触发时间（调度器计算；null 表示手动触发）
     */
    void executeJob(SysJob job, LocalDateTime scheduledTime) {
        if (scheduledTime != null) {
            SysJob current = jobMapper.selectValidById(job.getTenantId(), job.getId());
            if (current == null || current.getStatus() != JOB_STATUS_ENABLED) {
                log.info("Skip scheduled execution: job deleted or disabled: jobId={}", job.getId());
                return;
            }
            job = current;
        }
        String executionKey = scheduledTime != null
            ? taskExecutionDomainService.buildScheduledKey(job.getId(), scheduledTime)
            : taskExecutionDomainService.buildManualKey(job.getId());
        submitIfClaimed(job, executionKey, scheduledTime);
    }

    @Override
    public void reconcileScheduledJobs() {
        // 多实例配置对账：跨租户单条批量加载启用任务（§8.4.8：禁止按租户循环查询），
        // diff 重调度收敛「CRUD 只改本 JVM 内存」造成的跨实例配置漂移
        List<SysJob> enabledJobs;
        try {
            enabledJobs = jobMapper.selectAllEnabledJobs();
        } catch (Exception e) {
            // 加载失败 = 全部任务状态未知：本轮不做任何调度变更（包括删除判定），
            // 错过的计划触发不产生执行记录，一次临时数据库异常不得被解释成全部停用
            log.error("Reconcile failed to load jobs, keep current schedules unchanged", e);
            return;
        }
        Set<Long> activeJobIds = new java.util.HashSet<>();
        for (SysJob job : enabledJobs) {
            activeJobIds.add(job.getId());
            try {
                scheduleJob(job);
            } catch (Exception e) {
                log.error("Reconcile failed to schedule job: id={}", job.getId(), e);
            }
        }
        // 本实例已调度但数据库确认不再启用的任务：取消调度（加载成功才有删除资格）
        for (Long scheduledId : scheduledCrons.keySet()) {
            if (!activeJobIds.contains(scheduledId)) {
                unscheduleJob(scheduledId);
            }
        }
    }

    @Override
    public void takeoverExpiredExecutions() {
        int abandoned = taskExecutionDomainService.failExpiredOverMaxAttempts();
        if (abandoned > 0) {
            log.warn("Abandoned {} expired task execution(s) over max attempts {}",
                abandoned, TaskExecutionDomainService.MAX_ATTEMPTS);
        }
        List<SysTaskExecution> retryable =
            taskExecutionDomainService.findRetryable(TAKEOVER_BATCH_LIMIT);
        for (SysTaskExecution execution : retryable) {
            try {
                takeoverOne(execution);
            } catch (Exception e) {
                log.error("Takeover attempt failed for execution {}",
                    execution.getExecutionKey(), e);
            }
        }
    }

    /**
     * 接管单条可重试执行：解析任务配置后走统一抢占路径
     * （FAILED 重试与 RUNNING 过期接管共用 tryClaim 原子竞争，同一执行键）。
     * 任务已删除/已停用或执行键无法解析的记录立即收敛（attempt 拉满，不再
     * 进入重试候选），防止僵尸记录每轮占据接管批次导致有效重试饥饿。
     */
    private void takeoverOne(SysTaskExecution execution) {
        String executionKey = execution.getExecutionKey();
        Long jobId = taskExecutionDomainService.parseJobId(executionKey);
        if (jobId == null) {
            taskExecutionDomainService.abandonExecution(
                execution, "unparseable execution key, retry abandoned");
            log.warn("Takeover abandoned: unparseable execution key {}", executionKey);
            return;
        }
        SysJob job = jobMapper.selectValidById(execution.getTenantId(), jobId);
        if (job == null || job.getStatus() != JOB_STATUS_ENABLED) {
            // 按候选快照 fencing 收敛：读取后已被其他实例抢占/完成的行不受影响
            taskExecutionDomainService.abandonExecution(
                execution, "job deleted or disabled, retry abandoned");
            log.warn("Takeover abandoned: job {} deleted or disabled (tenant {})",
                jobId, execution.getTenantId());
            return;
        }
        // 计划时刻从执行键反解（startedAt 是逐次尝试的开始时间，会随抢占漂移）
        boolean claimed = submitIfClaimed(job, executionKey,
            taskExecutionDomainService.parseScheduledTime(executionKey));
        log.info("Takeover attempt for execution {}: {}",
            executionKey, claimed ? "claimed" : "skipped");
    }

    /**
     * 原子抢占执行权；成功则先启动续租再提交执行器（排队期间租约有人续，
     * 不会被误接管），失败（他实例已获执行权/已成功/超最大尝试）直接跳过。
     *
     * @return true=抢占成功并已提交
     */
    private boolean submitIfClaimed(SysJob job, String executionKey, LocalDateTime scheduledTime) {
        Integer attempt =
            taskExecutionDomainService.tryClaim(job.getTenantId(), executionKey, leaseOwner);
        if (attempt == null) {
            log.info("Job execution claimed by another instance or not retryable: "
                + "jobId={}, executionKey={}", job.getId(), executionKey);
            return false;
        }
        TaskExecutionContext context = new TaskExecutionContext(
            job.getTenantId(), job.getId(), executionKey, attempt, scheduledTime);
        ScheduledFuture<?> renewal = startRenewal(context);
        try {
            taskExecutor.execute(() -> executeClaimed(job, context, renewal));
        } catch (org.springframework.core.task.TaskRejectedException e) {
            // 有界执行器满/停机：停续租并写回 FAILED（未超次数时后续扫描按至少一次语义重试）
            renewal.cancel(false);
            taskExecutionDomainService.complete(job.getTenantId(), executionKey, leaseOwner,
                attempt, false, "task executor rejected: " + e.getMessage());
            log.error("Task executor rejected job execution (will be retried by takeover scan): "
                + "jobId={}, executionKey={}", job.getId(), executionKey, e);
            return false;
        }
        return true;
    }

    /**
     * 执行线程体：绑定 TASK 可信上下文 → 出队后先校验租约仍在本尝试 →
     * 反射执行 → 按尝试号 fencing 条件写回 → 独立短事务记录执行日志。
     */
    private void executeClaimed(SysJob job, TaskExecutionContext context, ScheduledFuture<?> renewal) {
        long start = System.currentTimeMillis();
        Integer status = 1;
        String message = "Executed successfully";
        try {
            // 显式建立 TASK 可信上下文（四要素之 callerType=TASK），不继承调度/请求线程
            AccessRequestContext.bind(RequestContext.task(context.tenantId()));
            TenantContextHolder.setTenantId(context.tenantId());

            // 出队校验：排队期间若续租停摆导致租约易主/被顶替，本次不执行
            if (!taskExecutionDomainService.renewLease(
                context.tenantId(), context.executionKey(), leaseOwner, context.attemptCount())) {
                status = 0;
                message = "Lease lost before execution, skipped";
                log.warn("Lease lost before execution: jobId={}, executionKey={}",
                    job.getId(), context.executionKey());
                return;
            }

            jobInvokeDomainService.invoke(job.getInvokeTarget(), context);

            if (!taskExecutionDomainService.complete(context.tenantId(), context.executionKey(),
                leaseOwner, context.attemptCount(), true, null)) {
                // 租约被接管（owner/attempt 已变）：本尝试结果丢弃，不覆盖新尝试
                status = 0;
                message = "Lease lost before completion, result discarded";
                log.warn("Lease lost before completion: jobId={}, executionKey={}",
                    job.getId(), context.executionKey());
            }
        } catch (Exception e) {
            status = 0;
            message = e.getMessage();
            log.error("Job execution failed: jobId={}, executionKey={}",
                job.getId(), context.executionKey(), e);
            boolean written = taskExecutionDomainService.complete(context.tenantId(),
                context.executionKey(), leaseOwner, context.attemptCount(), false,
                e.getClass().getSimpleName() + ": " + e.getMessage());
            if (!written) {
                message = "Lease lost before failure write-back: " + message;
            }
        } finally {
            renewal.cancel(false);
            // 独立短事务记录执行日志（T-ACCESS-007 §8.2），失败仅告警不影响执行
            try {
                jobLogDomainService.recordJobLog(job.getTenantId(), job.getId(), job.getJobName(),
                    job.getInvokeTarget(), status, message,
                    (int) (System.currentTimeMillis() - start));
            } catch (Exception e) {
                log.warn("记录任务执行日志失败（已隔离，不影响任务执行）: jobId={}, error={}",
                    job.getId(), e.getMessage());
            }
            TenantContextHolder.clear();
            AccessRequestContext.clear();
        }
    }

    /**
     * 后台续租：抢占成功后立即启动（覆盖排队等待期），固定间隔续租；
     * 失败仅记录告警——出队校验与完成写回的 fencing 条件兜底。
     * <p>
     * 使用专用 {@code taskLeaseRenewalScheduler}（AI 三轮复评修复）：续租是
     * 正确性路径，不得与共享调度器上的对账/接管扫描（同步数据库 IO）竞争
     * 线程——共享调度器阻塞超过租约窗口会停摆续租、误触发接管，破坏
     * 「最多一个活动执行者」。
     * </p>
     */
    private ScheduledFuture<?> startRenewal(TaskExecutionContext context) {
        return leaseRenewalScheduler.scheduleAtFixedRate(() -> {
            if (!taskExecutionDomainService.renewLease(
                context.tenantId(), context.executionKey(), leaseOwner, context.attemptCount())) {
                log.warn("Lease renew failed (expired or taken over): executionKey={}",
                    context.executionKey());
            }
        }, Instant.now().plusSeconds(TaskExecutionDomainService.RENEW_INTERVAL_SECONDS),
            Duration.ofSeconds(TaskExecutionDomainService.RENEW_INTERVAL_SECONDS));
    }

    /**
     * 计划触发时间捕获 Trigger（T-ACCESS-009）
     * <p>
     * Spring 的 {@code ReschedulingRunnable} 会在每次执行前（初始注册时与上一轮
     * 结束后）调用 {@code nextExecution} 计算下次触发时刻，随后才运行任务——
     * 因此 {@link #lastComputedFireTime()} 在任务体开头读取到的即为本轮计划触发
     * 时刻。cron 按 JVM 时区计算，各实例部署约定同一时区（项目约定，
     * 不做跨时区支持）。
     * </p>
     */
    static final class ExecutionKeyCronTrigger implements org.springframework.scheduling.Trigger {

        private final CronTrigger delegate;
        private volatile LocalDateTime lastComputed;

        ExecutionKeyCronTrigger(String cronExpression) {
            this.delegate = new CronTrigger(cronExpression);
        }

        @Override
        public Instant nextExecution(org.springframework.scheduling.TriggerContext triggerContext) {
            Instant next = delegate.nextExecution(triggerContext);
            if (next != null) {
                lastComputed = LocalDateTime.ofInstant(next, java.time.ZoneOffset.UTC);
            }
            return next;
        }

        /** 本轮计划触发时间（统一 UTC 表示；极端情况下未被调用时返回 null 走手动键） */
        LocalDateTime lastComputedFireTime() {
            return lastComputed;
        }
    }
}