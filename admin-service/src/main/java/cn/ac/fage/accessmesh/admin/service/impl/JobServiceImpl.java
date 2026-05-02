package cn.ac.fage.accessmesh.admin.service.impl;

import cn.ac.fage.accessmesh.admin.dto.req.IdReq;
import cn.ac.fage.accessmesh.admin.dto.req.IdsReq;
import cn.ac.fage.accessmesh.admin.dto.req.JobLogPageReq;
import cn.ac.fage.accessmesh.admin.dto.req.PageReq;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import static cn.ac.fage.accessmesh.admin.entity.table.SysJobLogTableDef.SYS_JOB_LOG;
import static cn.ac.fage.accessmesh.admin.entity.table.SysJobTableDef.SYS_JOB;

@Service
public class JobServiceImpl implements JobService {

    private static final Logger log = LoggerFactory.getLogger(JobServiceImpl.class);

    private final SysJobMapper jobMapper;
    private final SysJobLogMapper jobLogMapper;
    private final TaskScheduler taskScheduler;
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public JobServiceImpl(SysJobMapper jobMapper, SysJobLogMapper jobLogMapper, TaskScheduler taskScheduler) {
        this.jobMapper = jobMapper;
        this.jobLogMapper = jobLogMapper;
        this.taskScheduler = taskScheduler;
    }

    @Override
    @Transactional
    public Long createJob(SysJob job) {
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
    @Transactional
    public void updateJob(SysJob job) {
        SysJob existing = jobMapper.selectOneById(job.getId());
        if (existing == null || existing.getDeleteFlag() != 0L) {
            throw new BizException(AdminErrorCode.JOB_NOT_FOUND.getCode(), AdminErrorCode.JOB_NOT_FOUND.getMessage());
        }
        // If cron changed, reschedule
        if (existing.getStatus() == 1) {
            unscheduleJob(job.getId());
        }
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.update(job);
        if (job.getStatus() == 1) {
            SysJob updated = jobMapper.selectOneById(job.getId());
            scheduleJob(updated);
        }
    }

    @Override
    @Transactional
    public void deleteJobs(IdsReq req) {
        LocalDateTime now = LocalDateTime.now();
        for (Long id : req.ids()) {
            unscheduleJob(id);
            SysJob job = jobMapper.selectOneById(id);
            if (job == null || job.getDeleteFlag() != 0L) continue;
            job.setDeleteFlag(1L);
            job.setDeletedAt(now);
            jobMapper.update(job);
        }
    }

    @Override
    @Transactional
    public void toggleJobStatus(Long id, Integer status) {
        SysJob job = jobMapper.selectOneById(id);
        if (job == null || job.getDeleteFlag() != 0L) {
            throw new BizException(AdminErrorCode.JOB_NOT_FOUND.getCode(), AdminErrorCode.JOB_NOT_FOUND.getMessage());
        }
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
        SysJob job = jobMapper.selectOneById(id);
        if (job == null || job.getDeleteFlag() != 0L) {
            throw new BizException(AdminErrorCode.JOB_NOT_FOUND.getCode(), AdminErrorCode.JOB_NOT_FOUND.getMessage());
        }
        executeJob(job);
    }

    @Override
    public SysJob getJob(Long id) {
        return jobMapper.selectOneById(id);
    }

    @Override
    public PaginatedResult<SysJob> pageJobs(PageReq pageReq, String jobGroup) {
        QueryWrapper<?> qw = QueryWrapper.create().where(SYS_JOB.DELETE_FLAG.eq(0));
        if (jobGroup != null) qw.and(SYS_JOB.JOB_GROUP.eq(jobGroup));
        qw.orderBy(SYS_JOB.CREATED_AT.desc());

        Page<SysJob> page = Page.of(pageReq.pageNum(), pageReq.pageSize());
        Page<SysJob> result = jobMapper.paginate(page, qw);

        long totalPages = (result.getTotalRow() + pageReq.pageSize() - 1) / pageReq.pageSize();
        return new PaginatedResult<>(result.getRecords(),
            new PaginatedResult.PaginationMeta(result.getTotalRow(), pageReq.pageNum(), pageReq.pageSize(), (int) totalPages));
    }

    @Override
    public PaginatedResult<SysJobLog> pageJobLogs(JobLogPageReq pageReq, Long jobId) {
        QueryWrapper<?> qw = QueryWrapper.create().orderBy(SYS_JOB_LOG.CREATED_AT.desc());
        if (jobId != null) {
            qw.where(SYS_JOB_LOG.JOB_ID.eq(jobId));
        }

        Page<SysJobLog> page = Page.of(pageReq.pageNum(), pageReq.pageSize());
        Page<SysJobLog> result = jobLogMapper.paginate(page, qw);

        long totalPages = (result.getTotalRow() + pageReq.pageSize() - 1) / pageReq.pageSize();
        return new PaginatedResult<>(result.getRecords(),
            new PaginatedResult.PaginationMeta(result.getTotalRow(), pageReq.pageNum(), pageReq.pageSize(), (int) totalPages));
    }

    private void scheduleJob(SysJob job) {
        if (scheduledTasks.containsKey(job.getId())) {
            unscheduleJob(job.getId());
        }
        try {
            CronTrigger trigger = new CronTrigger(job.getCronExpression());
            ScheduledFuture<?> future = taskScheduler.schedule(() -> executeJob(job), trigger);
            scheduledTasks.put(job.getId(), future);
            log.info("Scheduled job: id={}, cron={}", job.getId(), job.getCronExpression());
        } catch (Exception e) {
            log.error("Failed to schedule job: id={}", job.getId(), e);
        }
    }

    private void unscheduleJob(Long jobId) {
        ScheduledFuture<?> future = scheduledTasks.remove(jobId);
        if (future != null) {
            future.cancel(false);
            log.info("Unscheduling job: id={}", jobId);
        }
    }

    void executeJob(SysJob job) {
        long start = System.currentTimeMillis();
        SysJobLog jobLog = new SysJobLog();
        jobLog.setJobId(job.getId());
        jobLog.setJobName(job.getJobName());
        jobLog.setInvokeTarget(job.getInvokeTarget());
        jobLog.setCreatedAt(LocalDateTime.now());

        try {
            // TODO: In production, use reflection or a bean invocation mechanism
            // to dynamically invoke the method specified in invokeTarget.
            // For now, log the invocation.
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
