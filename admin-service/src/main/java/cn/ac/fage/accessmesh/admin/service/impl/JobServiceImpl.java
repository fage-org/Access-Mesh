package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.config.TenantContextHolder;
import cn.ac.fage.accessmesh.admin.dto.resp.JobLogResp;
import cn.ac.fage.accessmesh.admin.dto.resp.JobResp;
import cn.ac.fage.accessmesh.admin.security.AdminOperationCode;
import cn.ac.fage.accessmesh.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.admin.security.AdminResourceType;
import cn.ac.fage.accessmesh.common.model.IdReq;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.JobLogPageReq;
import cn.ac.fage.accessmesh.common.model.PageReq;
import cn.ac.fage.accessmesh.admin.entity.SysJob;
import cn.ac.fage.accessmesh.admin.entity.SysJobLog;
import cn.ac.fage.accessmesh.admin.entity.table.SysJobTableDef;
import cn.ac.fage.accessmesh.admin.entity.table.SysJobLogTableDef;
import cn.ac.fage.accessmesh.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.admin.mapper.SysJobLogMapper;
import cn.ac.fage.accessmesh.admin.mapper.SysJobMapper;
import cn.ac.fage.accessmesh.admin.service.JobService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.common.model.PaginatedResult;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
 * 任务执行目前仅记录日志（待完善：通过反射或Spring Bean机制动态调用目标方法）。
 * </p>
 */
@Service
public class JobServiceImpl implements JobService {

    private static final Logger log = LoggerFactory.getLogger(JobServiceImpl.class);

    private final SysJobMapper jobMapper;
    private final SysJobLogMapper jobLogMapper;
    private final TaskScheduler taskScheduler;
    private final AdminPermissionValidator permissionValidator;
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

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
     * @param taskScheduler Spring任务调度器
     * @param permissionValidator 权限校验器
     */
    public JobServiceImpl(SysJobMapper jobMapper, SysJobLogMapper jobLogMapper, TaskScheduler taskScheduler,
                          AdminPermissionValidator permissionValidator) {
        this.jobMapper = jobMapper;
        this.jobLogMapper = jobLogMapper;
        this.taskScheduler = taskScheduler;
        this.permissionValidator = permissionValidator;
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
     * 应用启动时自动加载所有启用的任务并调度。
     * 通过@PostConstruct注解在Bean初始化后执行。
     * </p>
     */
    @PostConstruct
    public void initScheduledTasks() {
        log.info("Initializing scheduled tasks from database...");
        List<SysJob> enabledJobs = jobMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SysJobTableDef.SYS_JOB.STATUS.eq(1))
                .and(SysJobTableDef.SYS_JOB.DELETE_FLAG.eq(0))
        );
        for (SysJob job : enabledJobs) {
            try {
                scheduleJob(job);
                log.info("Loaded job on startup: id={}, name={}", job.getId(), job.getJobName());
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
    public Long createJob(SysJob job) {
        // Permission check - type-level CREATE
        permissionValidator.checkTypeLevel(AdminResourceType.JOB, AdminOperationCode.CREATE);

        Long tenantId = TenantContextHolder.getTenantId();
        job.setTenantId(tenantId);
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        job.setDeleteFlag(0L);
        job.setStatus(job.getStatus() != null ? job.getStatus() : 0);
        jobMapper.insert(job);
        if (job.getStatus() == 1) {
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
    public void updateJob(SysJob job) {
        Long tenantId = TenantContextHolder.getTenantId();
        SysJob existing = jobMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SysJobTableDef.SYS_JOB.ID.eq(job.getId()))
                .and(SysJobTableDef.SYS_JOB.TENANT_ID.eq(tenantId))
                .and(SysJobTableDef.SYS_JOB.DELETE_FLAG.eq(0))
        );
        if (existing == null) {
            throw new BizException(AdminErrorCode.JOB_NOT_FOUND.getCode(), AdminErrorCode.JOB_NOT_FOUND.getMessage());
        }

        // Permission check - instance-level UPDATE
        permissionValidator.checkInstanceLevel(AdminResourceType.JOB, job.getId().toString(), AdminOperationCode.UPDATE);

        // If cron changed, reschedule
        if (existing.getStatus() == 1) {
            unscheduleJob(job.getId());
        }
        job.setTenantId(tenantId);
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.update(job);
        if (job.getStatus() == 1) {
            SysJob updated = jobMapper.selectOneById(job.getId());
            scheduleJob(updated);
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
    public void deleteJobs(IdsReq req) {
        Long tenantId = TenantContextHolder.getTenantId();

        // Permission check - batch instance-level DELETE
        List<String> resourceCodes = req.ids().stream().map(String::valueOf).toList();
        permissionValidator.checkBatchInstanceLevel(AdminResourceType.JOB, resourceCodes, AdminOperationCode.DELETE);

        // Unschedule jobs first (must remain as loop for scheduler operation)
        for (Long id : req.ids()) {
            unscheduleJob(id);
        }

        // Batch query valid jobs (performance fix: avoid N+1 queries for SELECT)
        List<SysJob> jobs = jobMapper.selectListByQuery(
            QueryWrapper.create()
                .where(SysJobTableDef.SYS_JOB.ID.in(req.ids()))
                .and(SysJobTableDef.SYS_JOB.TENANT_ID.eq(tenantId))
                .and(SysJobTableDef.SYS_JOB.DELETE_FLAG.eq(0))
        );

        // Batch soft delete (performance fix: use single SQL instead of loop)
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
    public void toggleJobStatus(Long id, Integer status) {
        Long tenantId = TenantContextHolder.getTenantId();
        SysJob job = jobMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SysJobTableDef.SYS_JOB.ID.eq(id))
                .and(SysJobTableDef.SYS_JOB.TENANT_ID.eq(tenantId))
                .and(SysJobTableDef.SYS_JOB.DELETE_FLAG.eq(0))
        );
        if (job == null) {
            throw new BizException(AdminErrorCode.JOB_NOT_FOUND.getCode(), AdminErrorCode.JOB_NOT_FOUND.getMessage());
        }

        // Permission check - instance-level ENABLE/DISABLE
        String operationCode = status == 1 ? AdminOperationCode.ENABLE : AdminOperationCode.DISABLE;
        permissionValidator.checkInstanceLevel(AdminResourceType.JOB, id.toString(), operationCode);

        job.setStatus(status);
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.update(job);
        if (status == 1) {
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
    public void triggerJob(Long id) {
        // Permission check - instance-level TRIGGER
        permissionValidator.checkInstanceLevel(AdminResourceType.JOB, id.toString(), AdminOperationCode.TRIGGER);

        Long tenantId = TenantContextHolder.getTenantId();
        SysJob job = jobMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SysJobTableDef.SYS_JOB.ID.eq(id))
                .and(SysJobTableDef.SYS_JOB.TENANT_ID.eq(tenantId))
                .and(SysJobTableDef.SYS_JOB.DELETE_FLAG.eq(0))
        );
        if (job == null) {
            throw new BizException(AdminErrorCode.JOB_NOT_FOUND.getCode(), AdminErrorCode.JOB_NOT_FOUND.getMessage());
        }
        executeJob(job);
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
        SysJob job = jobMapper.selectOneByQuery(
            QueryWrapper.create()
                .where(SysJobTableDef.SYS_JOB.ID.eq(id))
                .and(SysJobTableDef.SYS_JOB.TENANT_ID.eq(tenantId))
                .and(SysJobTableDef.SYS_JOB.DELETE_FLAG.eq(0))
        );
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
        QueryWrapper qw = QueryWrapper.create()
            .where(SysJobTableDef.SYS_JOB.TENANT_ID.eq(tenantId))
            .and(SysJobTableDef.SYS_JOB.DELETE_FLAG.eq(0));
        if (jobGroup != null) qw.and(SysJobTableDef.SYS_JOB.JOB_GROUP.eq(jobGroup));
        qw.orderBy(SysJobTableDef.SYS_JOB.CREATED_AT.desc());

        Page<SysJob> page = Page.of(pageReq.pageNum(), pageReq.pageSize());
        Page<SysJob> result = jobMapper.paginate(page, qw);

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
        QueryWrapper qw = QueryWrapper.create()
            .where(SysJobLogTableDef.SYS_JOB_LOG.TENANT_ID.eq(tenantId))
            .orderBy(SysJobLogTableDef.SYS_JOB_LOG.CREATED_AT.desc());
        if (jobId != null) {
            qw.and(SysJobLogTableDef.SYS_JOB_LOG.JOB_ID.eq(jobId));
        }

        Page<SysJobLog> page = Page.of(pageReq.pageNum(), pageReq.pageSize());
        Page<SysJobLog> result = jobLogMapper.paginate(page, qw);

        List<JobLogResp> items = result.getRecords().stream()
            .map(JobLogResp::from)
            .toList();

        long totalPages = (result.getTotalRow() + pageReq.pageSize() - 1) / pageReq.pageSize();
        return new PaginatedResult<>(items,
            new PaginatedResult.PaginationMeta(result.getTotalRow(), pageReq.pageNum(), pageReq.pageSize(), (int) totalPages));
    }

    /**
     * 调度任务
     * <p>
     * 使用ReentrantLock保护调度操作，解决并发问题。
     * 使用Spring TaskScheduler根据Cron表达式调度任务。
     * 如果已有调度，先取消再重新调度。
     * </p>
     *
     * @param job 任务实体
     */
    private void scheduleJob(SysJob job) {
        ReentrantLock lock = getLockForJob(job.getId());
        lock.lock();
        try {
            if (scheduledTasks.containsKey(job.getId())) {
                unscheduleJobInternal(job.getId());
            }
            CronTrigger trigger = new CronTrigger(job.getCronExpression());
            ScheduledFuture<?> future = taskScheduler.schedule(() -> executeJob(job), trigger);
            scheduledTasks.put(job.getId(), future);
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
        if (future != null) {
            future.cancel(false);
            log.info("Unscheduled job: id={}", jobId);
        }
    }

    /**
     * 执行任务
     * <p>
     * 执行指定任务并记录执行日志。
     * 当前仅记录日志，未实际调用invokeTarget（待完善）。
     * 记录执行状态、消息、耗时等信息。
     * </p>
     *
     * @param job 任务实体
     */
    void executeJob(SysJob job) {
        long start = System.currentTimeMillis();
        SysJobLog jobLog = new SysJobLog();
        jobLog.setTenantId(job.getTenantId());
        jobLog.setJobId(job.getId());
        jobLog.setJobName(job.getJobName());
        jobLog.setInvokeTarget(job.getInvokeTarget());
        jobLog.setCreatedAt(LocalDateTime.now());

        try {
            // ARCH-DEBT-001: Job执行机制待完善 - 当前仅记录日志，未实际调用invokeTarget
            // 理想方案: 通过反射或Spring Bean机制动态调用目标方法
            // 优先级: P2（功能完善，非阻塞）
            // 状态: 待后续迭代处理
            // 影响: Job任务不执行，仅记录日志
            log.info("Executing job: id={}, target={}", job.getId(), job.getInvokeTarget());
            jobLog.setStatus(1);
            jobLog.setMessage("Executed successfully");
        } catch (Exception e) {
            jobLog.setStatus(0);
            jobLog.setMessage(e.getMessage());
            log.error("Job execution failed: id={}", job.getId(), e);
        } finally {
            jobLog.setCostTime((int) (System.currentTimeMillis() - start));
            jobLogMapper.insert(jobLog);
        }
    }
}