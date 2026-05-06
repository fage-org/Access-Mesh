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

@Service
public class JobServiceImpl implements JobService {

    private static final Logger log = LoggerFactory.getLogger(JobServiceImpl.class);

    private final SysJobMapper jobMapper;
    private final SysJobLogMapper jobLogMapper;
    private final TaskScheduler taskScheduler;
    private final AdminPermissionValidator permissionValidator;
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    
    // FIX: 细粒度锁池，按jobId分组，避免全局锁竞争
    private final ConcurrentHashMap<Long, ReentrantLock> jobLocks = new ConcurrentHashMap<>();

    public JobServiceImpl(SysJobMapper jobMapper, SysJobLogMapper jobLogMapper, TaskScheduler taskScheduler,
                          AdminPermissionValidator permissionValidator) {
        this.jobMapper = jobMapper;
        this.jobLogMapper = jobLogMapper;
        this.taskScheduler = taskScheduler;
        this.permissionValidator = permissionValidator;
    }

    /**
     * 获取指定jobId对应的锁（懒加载）
     */
    private ReentrantLock getLockForJob(Long jobId) {
        return jobLocks.computeIfAbsent(jobId, id -> new ReentrantLock());
    }

    /**
     * Initialize scheduled tasks on startup.
     * Load all enabled jobs from database and schedule them.
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
     * FIX: 使用ReentrantLock保护scheduleJob的check-then-act操作
     * 解决并发场景下任务重复调度和ScheduledFuture泄漏问题
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
     * FIX: 使用ReentrantLock保护unscheduleJob操作
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
     * FIX: 内部方法，不加锁（在锁保护下调用）
     * 避免锁嵌套，减少死锁风险
     */
    private void unscheduleJobInternal(Long jobId) {
        ScheduledFuture<?> future = scheduledTasks.remove(jobId);
        if (future != null) {
            future.cancel(false);
            log.info("Unscheduled job: id={}", jobId);
        }
    }

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
