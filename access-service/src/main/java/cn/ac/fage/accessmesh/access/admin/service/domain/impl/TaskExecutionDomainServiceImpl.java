package cn.ac.fage.accessmesh.access.admin.service.domain.impl;

import cn.ac.fage.accessmesh.access.admin.service.domain.TaskExecutionDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.entity.SysTaskExecution;
import cn.ac.fage.accessmesh.access.infrastructure.mapper.SysTaskExecutionMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/**
 * 任务执行租约领域服务实现（T-ACCESS-009）
 * <p>
 * 全部并发正确性由 {@code SysTaskExecutionMapper} 的单条原子条件 SQL
 * （数据库时间基准）保证；本类只做参数装配，不声明事务。
 * </p>
 */
@Service
public class TaskExecutionDomainServiceImpl implements TaskExecutionDomainService {

    /** 执行键中的计划时间格式：秒级截断，保证各实例对同一触发时刻生成相同键 */
    private static final DateTimeFormatter KEY_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    private final SysTaskExecutionMapper taskExecutionMapper;

    public TaskExecutionDomainServiceImpl(SysTaskExecutionMapper taskExecutionMapper) {
        this.taskExecutionMapper = taskExecutionMapper;
    }

    @Override
    public String buildScheduledKey(Long jobId, LocalDateTime scheduledTime) {
        // 截断到秒：各实例调度器微秒级触发差异不得影响执行键
        return "job:" + jobId + ":" + scheduledTime.withNano(0).format(KEY_TIME);
    }

    @Override
    public String buildManualKey(Long jobId) {
        // UUID 后缀防同毫秒并发触发碰撞（碰撞会被唯一约束静默吞掉一次手动执行）
        return "job:" + jobId + ":manual:" + System.currentTimeMillis()
            + "-" + UUID.randomUUID();
    }

    @Override
    public Long parseJobId(String executionKey) {
        // 格式 job:{jobId}:...；manual 键与计划键共享 job: 前缀，均可解析
        if (executionKey == null || !executionKey.startsWith("job:")) {
            return null;
        }
        int secondColon = executionKey.indexOf(':', 4);
        if (secondColon < 0) {
            return null;
        }
        try {
            return Long.valueOf(executionKey.substring(4, secondColon));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public LocalDateTime parseScheduledTime(String executionKey) {
        // 计划键 job:{id}:{yyyyMMdd'T'HHmmss} 尾段反解；manual 键/异形键返回 null
        if (executionKey == null || executionKey.contains(":manual:")) {
            return null;
        }
        int lastColon = executionKey.lastIndexOf(':');
        if (lastColon < 0) {
            return null;
        }
        try {
            return LocalDateTime.parse(executionKey.substring(lastColon + 1), KEY_TIME);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    @Override
    public Integer tryClaim(Long tenantId, String executionKey, String leaseOwner) {
        return taskExecutionMapper.tryClaimExecution(
            tenantId, executionKey, leaseOwner, LEASE_SECONDS, MAX_ATTEMPTS);
    }

    @Override
    public boolean renewLease(Long tenantId, String executionKey, String leaseOwner, int attempt) {
        return taskExecutionMapper.renewLease(
            tenantId, executionKey, leaseOwner, attempt, LEASE_SECONDS) == 1;
    }

    @Override
    public boolean complete(Long tenantId, String executionKey, String leaseOwner, int attempt,
                            boolean success, String lastError) {
        String error = lastError != null && lastError.length() > 1024
            ? lastError.substring(0, 1024) : lastError;
        return taskExecutionMapper.completeExecution(
            tenantId, executionKey, leaseOwner, attempt, success, error) == 1;
    }

    @Override
    public List<SysTaskExecution> findRetryable(int limit) {
        return taskExecutionMapper.selectRetryable(MAX_ATTEMPTS, limit);
    }

    @Override
    public int failExpiredOverMaxAttempts() {
        return taskExecutionMapper.failExpiredOverMaxAttempts(
            MAX_ATTEMPTS, "exceeded max attempts " + MAX_ATTEMPTS + ", expired lease abandoned");
    }

    @Override
    public int abandonExecution(SysTaskExecution execution, String reason) {
        String error = reason != null && reason.length() > 1024
            ? reason.substring(0, 1024) : reason;
        return taskExecutionMapper.abandonExecution(
            execution.getTenantId(), execution.getExecutionKey(), execution.getStatus(),
            execution.getAttemptCount() != null ? execution.getAttemptCount() : 0,
            MAX_ATTEMPTS, error);
    }

    @Override
    public SysTaskExecution findByExecutionKey(Long tenantId, String executionKey) {
        return taskExecutionMapper.selectByTenantAndExecutionKey(tenantId, executionKey);
    }
}
