package cn.ac.fage.accessmesh.access.admin.service.domain.impl;

import cn.ac.fage.accessmesh.access.admin.entity.SysJobLog;
import cn.ac.fage.accessmesh.access.admin.mapper.SysJobLogMapper;
import cn.ac.fage.accessmesh.access.admin.service.domain.JobLogDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 任务执行日志领域服务实现类
 * <p>
 * 封装任务执行日志的数据访问逻辑，提供独立短事务写入。
 * 定时任务执行日志用于任务执行监控和故障排查。
 * </p>
 * <p>
 * 独立短事务（T-ACCESS-007 §8.2）：{@link #recordJobLog} 使用 REQUIRES_NEW
 * 在独立事务写入任务执行日志，任务执行主流程（调度器线程，无外部事务）不受影响。
 * 方法体不吞异常：REQUIRES_NEW 异常（含 Spring 代理层 commit 阶段的
 * 连接中断/rollback-only）自然传播到调用方，由调用方（JobServiceImpl.executeJob finally）
 * 统一 try-catch 兜底隔离，日志失败不影响任务执行。
 * </p>
 */
@Service
public class JobLogDomainServiceImpl implements JobLogDomainService {

    private final SysJobLogMapper jobLogMapper;

    /**
     * 构造函数注入依赖
     *
     * @param jobLogMapper 任务执行日志数据访问层
     */
    public JobLogDomainServiceImpl(SysJobLogMapper jobLogMapper) {
        this.jobLogMapper = jobLogMapper;
    }

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
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordJobLog(Long tenantId, Long jobId, String jobName, String invokeTarget,
                             Integer status, String message, Integer costTime) {
        SysJobLog jobLog = new SysJobLog();
        jobLog.setTenantId(tenantId);
        jobLog.setJobId(jobId);
        jobLog.setJobName(jobName);
        jobLog.setInvokeTarget(invokeTarget);
        jobLog.setStatus(status);
        jobLog.setMessage(message);
        jobLog.setCostTime(costTime);
        jobLog.setCreatedAt(LocalDateTime.now());
        jobLogMapper.insert(jobLog);
    }
}
