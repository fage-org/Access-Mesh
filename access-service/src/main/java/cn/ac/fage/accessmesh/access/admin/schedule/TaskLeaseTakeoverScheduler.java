package cn.ac.fage.accessmesh.access.admin.schedule;

import cn.ac.fage.accessmesh.access.admin.service.JobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 任务租约接管扫描器（T-ACCESS-009 用户决策：接管扫描器 + advisory lock 协调）
 * <p>
 * 每实例周期触发一轮扫描（{@link JobService#takeoverExpiredExecutions()}，
 * 调度层编排）：收敛超过最大尝试次数的过期执行为 FAILED，随后对可重试执行
 * （RUNNING 租约过期 / FAILED 未超限）原子接管重试。
 * </p>
 * <p>
 * 扫描器自身多实例并发由 PostgreSQL 会话级 advisory lock
 * （{@code pg_try_advisory_lock}）协调（用户决策）：抢到锁的实例本轮扫描，
 * 未抢到则跳过本轮。advisory lock 仅为效率优化——接管动作本身已全部经
 * {@code sys_task_execution} 执行键 + 唯一约束原子竞争（任务卡「数据库执行键
 * 竞争同一次计划执行」由被接管的任务执行承载），锁不可用（非 PostgreSQL 等）
 * 时所有实例都扫描，正确性不受影响。advisory lock 为会话级，
 * 加锁/解锁在同一 pooled 连接上执行，避免连接池下 unlock 落到别的连接。
 * </p>
 * <p>
 * 该组件是 access-service 内保留的系统维护调度任务之一（另一个为
 * {@code JobScheduleReconciler}）：为多实例任务执行权提供故障接管与失败重试
 * （外部场景，非旧内部同步兜底，见 access-service-architecture §8.1）。
 * </p>
 */
@Component
public class TaskLeaseTakeoverScheduler {

    private static final Logger log = LoggerFactory.getLogger(TaskLeaseTakeoverScheduler.class);

    /** advisory lock 键（任意固定 bigint，仅本扫描器使用） */
    private static final long ADVISORY_LOCK_KEY = 8_009_001L;

    private final JobService jobService;
    private final JdbcTemplate jdbcTemplate;

    public TaskLeaseTakeoverScheduler(JobService jobService, JdbcTemplate jdbcTemplate) {
        this.jobService = jobService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 每 30 秒一轮：advisory lock 协调单实例扫描；抢锁失败跳过本轮（下轮再试）。
     */
    @Scheduled(fixedDelayString = "PT30S", initialDelayString = "PT60S")
    public void scanAndTakeover() {
        try {
            jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
                Boolean locked = tryAdvisoryLock(connection);
                if (locked == null) {
                    // 锁不可用（非 PostgreSQL 等）：不承担正确性，直接扫描
                    doScan();
                } else if (locked) {
                    try {
                        doScan();
                    } finally {
                        unlockAdvisory(connection);
                    }
                }
                // locked=false：其他实例正在扫描，本轮跳过
                return null;
            });
        } catch (Exception e) {
            log.error("Takeover scan pass failed", e);
        }
    }

    private void doScan() {
        jobService.takeoverExpiredExecutions();
    }

    /** @return true=抢到锁；false=他人持有；null=锁功能不可用 */
    private Boolean tryAdvisoryLock(java.sql.Connection connection) {
        try (PreparedStatement lockStmt = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            lockStmt.setLong(1, ADVISORY_LOCK_KEY);
            try (ResultSet rs = lockStmt.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        } catch (SQLException e) {
            log.debug("advisory lock unavailable: {}", e.getMessage());
            return null;
        }
    }

    private void unlockAdvisory(java.sql.Connection connection) {
        try (PreparedStatement unlockStmt = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            unlockStmt.setLong(1, ADVISORY_LOCK_KEY);
            unlockStmt.executeQuery();
        } catch (SQLException e) {
            log.warn("advisory unlock failed (auto-released on connection close): {}", e.getMessage());
        }
    }
}
