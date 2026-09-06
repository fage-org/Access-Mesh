package cn.ac.fage.accessmesh.access.admin.service.impl;

import cn.ac.fage.accessmesh.access.admin.entity.SysJob;
import cn.ac.fage.accessmesh.access.admin.mapper.SysJobMapper;
import cn.ac.fage.accessmesh.access.admin.service.domain.TaskExecutionDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.entity.SysTaskExecution;
import cn.ac.fage.accessmesh.access.infrastructure.mapper.SysTaskExecutionMapper;
import cn.ac.fage.accessmesh.access.infrastructure.task.JobInvocable;
import cn.ac.fage.accessmesh.access.infrastructure.task.TaskExecutionContext;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据库任务租约 PostgreSQL 并发集成测试（T-ACCESS-009 验收）。
 * <p>
 * 「双实例」以不同 lease_owner 标识 + 并发线程模拟，正确性由数据库原子条件 SQL
 * 保证，与实例数无关。覆盖验收项：并发抢占、续租、租约过期接管（含同实例
 * 接管自己的过期任务——attempt 级 fencing）、旧持有者不可覆盖新结果、成功后
 * 不可重复抢占、失败后至少一次重试、抢占最大尝试次数上限与编排级幂等。
 * Docker 可用时运行；CI 无 Docker 时自动跳过。
 * </p>
 * <p>
 * 建表在 {@code @BeforeAll}（容器启动后、Spring 上下文创建前）以裸 JDBC 完成：
 * {@code JobServiceImpl} 的 {@code @PostConstruct} 会在上下文启动时跨租户查询
 * {@code sys_job}，表必须先存在。
 * </p>
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker = true)
class TaskExecutionLeaseConcurrencyTest {

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        // 空库通道（fromTemplate=false）：@BeforeAll 自建 sys_task_execution/sys_job/sys_job_log 局部表
        ItInfra.register(registry, TaskExecutionLeaseConcurrencyTest.class, false);
    }

    /** 测试任务 Bean：记录每次调用的执行键，模拟外部副作用按执行键去重 */
    @TestConfiguration
    static class JobBeanConfig {
        @Bean
        TaskLeaseTestJobBean taskLeaseTestJobBean() {
            return new TaskLeaseTestJobBean();
        }
    }

    // 必须 public：JobInvokeDomainServiceImpl 反射跨包调用，包私有类会 IllegalAccessException
    public static class TaskLeaseTestJobBean {
        final List<String> invokedExecutionKeys = new CopyOnWriteArrayList<>();
        final Map<String, AtomicInteger> invocationsPerKey = new ConcurrentHashMap<>();
        volatile Long lastSeenTenantId;
        volatile Long lastSeenJobId;

        @JobInvocable
        public void run(TaskExecutionContext context) {
            invokedExecutionKeys.add(context.executionKey());
            invocationsPerKey.computeIfAbsent(context.executionKey(), k -> new AtomicInteger())
                .incrementAndGet();
            lastSeenTenantId = context.tenantId();
            lastSeenJobId = context.jobId();
        }
    }

    private static final Long TENANT_ID = 1L;
    private static final String BEAN = "taskLeaseTestJobBean";

    @Autowired
    private SysTaskExecutionMapper taskExecutionMapper;
    @Autowired
    private TaskExecutionDomainService taskExecutionDomainService;
    @Autowired
    private JobServiceImpl jobService;
    @Autowired
    private SysJobMapper jobMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TaskLeaseTestJobBean testJobBean;

    @BeforeAll
    static void initSchema() throws Exception {
        // @BeforeAll 先于 Spring 上下文装配执行：先占位建库（空库，register 复用同一绑定）
        ItInfra.prepare(TaskExecutionLeaseConcurrencyTest.class, false);
        // 容器已启动、Spring 上下文尚未创建：先建全部依赖表
        try (Connection conn = DriverManager.getConnection(
                 ItInfra.jdbcUrl(TaskExecutionLeaseConcurrencyTest.class), ItInfra.username(), ItInfra.password());
             Statement st = conn.createStatement()) {
            st.execute("""
                    CREATE TABLE sys_task_execution (
                        id            BIGSERIAL PRIMARY KEY,
                        tenant_id     BIGINT NOT NULL,
                        execution_key VARCHAR(192) NOT NULL,
                        status        VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                        lease_owner   VARCHAR(128),
                        lease_until   TIMESTAMPTZ,
                        attempt_count INT NOT NULL DEFAULT 0,
                        last_error    VARCHAR(1024),
                        started_at    TIMESTAMPTZ,
                        finished_at   TIMESTAMPTZ,
                        created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
                        updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
                        deleted_at    TIMESTAMPTZ,
                        delete_flag   BIGINT NOT NULL DEFAULT 0
                    )
                    """);
            st.execute("""
                    CREATE UNIQUE INDEX uk_task_execution
                    ON sys_task_execution (tenant_id, execution_key) WHERE delete_flag = 0
                    """);
            st.execute("""
                    CREATE TABLE sys_job (
                        id              BIGSERIAL PRIMARY KEY,
                        tenant_id       BIGINT NOT NULL,
                        job_name        VARCHAR(128) NOT NULL,
                        job_group       VARCHAR(64),
                        invoke_target   VARCHAR(256) NOT NULL,
                        cron_expression VARCHAR(128) NOT NULL,
                        misfire_policy  SMALLINT NOT NULL DEFAULT 0,
                        run_as_user_id  BIGINT,
                        status          SMALLINT NOT NULL DEFAULT 1,
                        remark          VARCHAR(512),
                        created_by      BIGINT, updated_by      BIGINT, deleted_by BIGINT,
                        created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                        updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                        deleted_at      TIMESTAMPTZ,
                        delete_flag     BIGINT NOT NULL DEFAULT 0
                    )
                    """);
            st.execute("""
                    CREATE TABLE sys_job_log (
                        id            BIGSERIAL PRIMARY KEY,
                        tenant_id     BIGINT NOT NULL,
                        job_id        BIGINT NOT NULL,
                        job_name      VARCHAR(128),
                        invoke_target VARCHAR(256),
                        status        SMALLINT NOT NULL DEFAULT 0,
                        message       VARCHAR(2000),
                        cost_time     BIGINT,
                        created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
                    )
                    """);
        }
    }

    @BeforeEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM sys_task_execution");
        jdbcTemplate.update("DELETE FROM sys_job");
        jdbcTemplate.update("DELETE FROM sys_job_log");
        testJobBean.invokedExecutionKeys.clear();
        testJobBean.invocationsPerKey.clear();
    }

    private Long insertJob(String invokeTarget) {
        jdbcTemplate.update(
            "INSERT INTO sys_job (tenant_id, job_name, invoke_target, cron_expression, status, delete_flag) "
                + "VALUES (?, 'test-job', ?, '0 0 0 * * *', 1, 0)", TENANT_ID, invokeTarget);
        return jdbcTemplate.queryForObject("SELECT max(id) FROM sys_job", Long.class);
    }

    /** 轮询等待异步执行完成（执行线程写回终态） */
    private SysTaskExecution awaitTerminal(String executionKey) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            SysTaskExecution row =
                taskExecutionDomainService.findByExecutionKey(TENANT_ID, executionKey);
            if (row != null && ("SUCCESS".equals(row.getStatus()) || "FAILED".equals(row.getStatus()))) {
                return row;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        throw new AssertionError("execution did not reach terminal state: " + executionKey);
    }

    @Test
    void concurrentClaimSameKeyExactlyOneWinner() throws Exception {
        String key = "job:9001:20260821T120000";
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                String owner = "instance-" + i;
                futures.add(pool.submit(() -> {
                    start.await();
                    return taskExecutionDomainService.tryClaim(TENANT_ID, key, owner);
                }));
            }
            start.countDown();
            int wins = 0;
            for (Future<Integer> f : futures) {
                if (f.get(30, TimeUnit.SECONDS) != null) {
                    wins++;
                }
            }
            assertThat(wins).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        SysTaskExecution row = taskExecutionDomainService.findByExecutionKey(TENANT_ID, key);
        assertThat(row.getStatus()).isEqualTo("RUNNING");
        assertThat(row.getAttemptCount()).isEqualTo(1);
        assertThat(row.getLeaseOwner()).startsWith("instance-");
    }

    @Test
    void renewOnlySucceedsForCurrentHolderAndAttempt() {
        String key = "job:9002:20260821T120000";
        Integer attempt = taskExecutionDomainService.tryClaim(TENANT_ID, key, "owner-A");
        assertThat(attempt).isEqualTo(1);
        assertThat(taskExecutionDomainService.renewLease(TENANT_ID, key, "owner-A", 1)).isTrue();
        // 他实例续租失败
        assertThat(taskExecutionDomainService.renewLease(TENANT_ID, key, "owner-B", 1)).isFalse();
        // fencing：错误尝试号续租失败
        assertThat(taskExecutionDomainService.renewLease(TENANT_ID, key, "owner-A", 2)).isFalse();
    }

    @Test
    void takeoverAfterExpiryPreventsOldHolderFromOverwriting() throws Exception {
        String key = "job:9003:20260821T120000";
        // 1s 短租约模拟持有者崩溃后过期
        assertThat(taskExecutionMapper.tryClaimExecution(TENANT_ID, key, "dead-instance", 1, 3))
            .isEqualTo(1);
        TimeUnit.MILLISECONDS.sleep(1200);

        // 新实例原子接管，重试计数 +1
        Integer attempt = taskExecutionDomainService.tryClaim(TENANT_ID, key, "owner-B");
        assertThat(attempt).isEqualTo(2);

        // 旧持有者续租失败、结果写回失败（不能覆盖新尝试）
        assertThat(taskExecutionDomainService.renewLease(TENANT_ID, key, "dead-instance", 1)).isFalse();
        assertThat(taskExecutionDomainService.complete(
            TENANT_ID, key, "dead-instance", 1, true, null)).isFalse();

        // 新持有者正常完成
        assertThat(taskExecutionDomainService.complete(
            TENANT_ID, key, "owner-B", 2, true, null)).isTrue();
        SysTaskExecution row = taskExecutionDomainService.findByExecutionKey(TENANT_ID, key);
        assertThat(row.getStatus()).isEqualTo("SUCCESS");
        assertThat(row.getAttemptCount()).isEqualTo(2);
        assertThat(row.getLeaseOwner()).isEqualTo("owner-B");

        // 已成功的执行不可再次抢占（幂等：同一计划只执行一次）
        assertThat(taskExecutionDomainService.tryClaim(TENANT_ID, key, "owner-C")).isNull();
    }

    @Test
    void sameInstanceTakeoverFencedByAttempt() throws Exception {
        String key = "job:9005:20260821T120000";
        // 同一实例（owner-A）第一次抢占，1s 短租约过期后又被自己接管：
        // owner 不变，attempt 递增——旧尝试必须被 fencing 挡住
        assertThat(taskExecutionMapper.tryClaimExecution(TENANT_ID, key, "owner-A", 1, 3))
            .isEqualTo(1);
        TimeUnit.MILLISECONDS.sleep(1200);
        Integer secondAttempt = taskExecutionMapper.tryClaimExecution(TENANT_ID, key, "owner-A", 1, 3);
        assertThat(secondAttempt).isEqualTo(2);

        // 旧尝试（attempt=1）续租/完成均失败
        assertThat(taskExecutionDomainService.renewLease(TENANT_ID, key, "owner-A", 1)).isFalse();
        assertThat(taskExecutionDomainService.complete(
            TENANT_ID, key, "owner-A", 1, true, null)).isFalse();
        // 新尝试（attempt=2）正常持有并完成
        assertThat(taskExecutionDomainService.renewLease(TENANT_ID, key, "owner-A", 2)).isTrue();
        assertThat(taskExecutionDomainService.complete(
            TENANT_ID, key, "owner-A", 2, true, null)).isTrue();
        SysTaskExecution row = taskExecutionDomainService.findByExecutionKey(TENANT_ID, key);
        assertThat(row.getStatus()).isEqualTo("SUCCESS");
        assertThat(row.getAttemptCount()).isEqualTo(2);
    }

    @Test
    void claimBlockedOverMaxAttempts() throws Exception {
        String key = "job:9006:20260821T120000";
        // 已尝试 3 次（MAX_ATTEMPTS）的过期执行不可再抢占
        assertThat(taskExecutionMapper.tryClaimExecution(TENANT_ID, key, "owner-A", 1, 3))
            .isEqualTo(1);
        jdbcTemplate.update("UPDATE sys_task_execution SET attempt_count = 3 "
            + "WHERE tenant_id = ? AND execution_key = ?", TENANT_ID, key);
        TimeUnit.MILLISECONDS.sleep(1200);

        assertThat(taskExecutionMapper.tryClaimExecution(TENANT_ID, key, "owner-B", 1, 3)).isNull();
        assertThat(taskExecutionDomainService.findRetryable(10)).isEmpty();

        // 超限的过期 RUNNING 收敛为终态 FAILED
        assertThat(taskExecutionDomainService.failExpiredOverMaxAttempts()).isEqualTo(1);
        SysTaskExecution row = taskExecutionDomainService.findByExecutionKey(TENANT_ID, key);
        assertThat(row.getStatus()).isEqualTo("FAILED");
        assertThat(row.getFinishedAt()).isNotNull();
    }

    @Test
    void failedExecutionRetriedAtLeastOnce() throws Exception {
        Long jobId = insertJob("noSuchBean.run");
        LocalDateTime scheduledTime = LocalDateTime.of(2026, 8, 21, 12, 0, 0);
        String executionKey = taskExecutionDomainService.buildScheduledKey(jobId, scheduledTime);

        // 业务失败写回 FAILED
        jobService.executeJob(jobMapper.selectValidById(TENANT_ID, jobId), scheduledTime);
        SysTaskExecution failed = awaitTerminal(executionKey);
        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getAttemptCount()).isEqualTo(1);

        // FAILED 且未超限 → 可重试候选；换成可成功目标后由扫描轮完成重试
        assertThat(taskExecutionDomainService.findRetryable(10))
            .extracting(SysTaskExecution::getExecutionKey).contains(executionKey);
        jdbcTemplate.update("UPDATE sys_job SET invoke_target = ? WHERE id = ?",
            BEAN + ".run", jobId);
        jobService.takeoverExpiredExecutions();
        SysTaskExecution retried = awaitTerminal(executionKey);
        assertThat(retried.getStatus()).isEqualTo("SUCCESS");
        assertThat(retried.getAttemptCount()).isEqualTo(2);
    }

    @Test
    void scheduledExecutionRunsOncePerKeyAndCarriesIdempotencyKey() throws Exception {
        Long jobId = insertJob(BEAN + ".run");
        SysJob job = jobMapper.selectValidById(TENANT_ID, jobId);
        LocalDateTime scheduledTime = LocalDateTime.of(2026, 8, 21, 12, 0, 0);
        String executionKey = taskExecutionDomainService.buildScheduledKey(jobId, scheduledTime);

        // 多实例对同一计划时刻各自触发两遍：只执行一次
        jobService.executeJob(job, scheduledTime);
        jobService.executeJob(job, scheduledTime);
        SysTaskExecution terminal = awaitTerminal(executionKey);

        assertThat(terminal.getStatus()).isEqualTo("SUCCESS");
        assertThat(terminal.getAttemptCount()).isEqualTo(1);
        assertThat(testJobBean.invocationsPerKey.get(executionKey)).hasValue(1);
        assertThat(testJobBean.lastSeenTenantId).isEqualTo(TENANT_ID);
        assertThat(testJobBean.lastSeenJobId).isEqualTo(jobId);
        // 已成功的执行键再次触发不再执行
        jobService.executeJob(job, scheduledTime);
        TimeUnit.MILLISECONDS.sleep(500);
        assertThat(testJobBean.invocationsPerKey.get(executionKey)).hasValue(1);
        // 独立短事务执行日志落库
        Integer logCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM sys_job_log WHERE tenant_id = ? AND job_id = ?",
            Integer.class, TENANT_ID, jobId);
        assertThat(logCount).isEqualTo(1);
    }

    @Test
    void takeoverReexecutesWithSameIdempotencyKey() throws Exception {
        Long jobId = insertJob(BEAN + ".run");
        // 模拟持有者崩溃：1s 短租约过期后由扫描轮接管
        String executionKey = "job:" + jobId + ":20260821T130000";
        assertThat(taskExecutionMapper.tryClaimExecution(
            TENANT_ID, executionKey, "dead-instance", 1, 3)).isEqualTo(1);
        TimeUnit.MILLISECONDS.sleep(1200);

        jobService.takeoverExpiredExecutions();

        SysTaskExecution terminal = awaitTerminal(executionKey);
        assertThat(terminal.getStatus()).isEqualTo("SUCCESS");
        assertThat(terminal.getAttemptCount()).isEqualTo(2);
        // 接管重试与首次执行携带同一执行键（外部副作用按该键去重）
        assertThat(testJobBean.invokedExecutionKeys).containsExactly(executionKey);
    }

    @Test
    void takeoverAbandonsExecutionForDeletedJob() throws Exception {
        // 任务配置已删除的 FAILED 执行：接管时收敛（attempt 拉满），
        // 不再进入重试候选——否则僵尸记录每轮占据接管批次导致有效重试饥饿
        String executionKey = "job:999999:20260821T140000";
        assertThat(taskExecutionMapper.tryClaimExecution(
            TENANT_ID, executionKey, "dead-instance", 1, 3)).isEqualTo(1);
        jdbcTemplate.update(
            "UPDATE sys_task_execution SET status = 'FAILED', lease_until = NULL "
                + "WHERE tenant_id = ? AND execution_key = ?", TENANT_ID, executionKey);
        assertThat(taskExecutionDomainService.findRetryable(10)).hasSize(1);

        jobService.takeoverExpiredExecutions();

        SysTaskExecution row = taskExecutionDomainService.findByExecutionKey(TENANT_ID, executionKey);
        assertThat(row.getAttemptCount()).isEqualTo(3);
        assertThat(row.getLastError()).contains("deleted or disabled");
        assertThat(taskExecutionDomainService.findRetryable(10)).isEmpty();
    }

    @Test
    void abandonFencedAgainstConcurrentClaimAndSuccess() throws Exception {
        String executionKey = "job:999998:20260821T150000";
        // 快照：FAILED attempt 1
        assertThat(taskExecutionMapper.tryClaimExecution(
            TENANT_ID, executionKey, "dead-instance", 1, 3)).isEqualTo(1);
        jdbcTemplate.update(
            "UPDATE sys_task_execution SET status = 'FAILED', lease_until = NULL "
                + "WHERE tenant_id = ? AND execution_key = ?", TENANT_ID, executionKey);
        SysTaskExecution staleSnapshot =
            taskExecutionDomainService.findByExecutionKey(TENANT_ID, executionKey);

        // 快照读取后另一实例抢占新尝试（attempt 2，持有有效租约）
        assertThat(taskExecutionDomainService.tryClaim(TENANT_ID, executionKey, "owner-B"))
            .isEqualTo(2);

        // 旧快照的收敛更新必须不生效：不破坏新尝试的 attempt 与后续续租/完成
        assertThat(taskExecutionDomainService.abandonExecution(
            staleSnapshot, "stale abandon must not apply")).isZero();
        SysTaskExecution row = taskExecutionDomainService.findByExecutionKey(TENANT_ID, executionKey);
        assertThat(row.getAttemptCount()).isEqualTo(2);
        assertThat(row.getStatus()).isEqualTo("RUNNING");
        assertThat(taskExecutionDomainService.renewLease(TENANT_ID, executionKey, "owner-B", 2))
            .isTrue();
        assertThat(taskExecutionDomainService.complete(
            TENANT_ID, executionKey, "owner-B", 2, true, null)).isTrue();

        // 对已成功行的收敛同样不生效（SUCCESS 不可被 abandon 篡改）
        SysTaskExecution successSnapshot =
            taskExecutionDomainService.findByExecutionKey(TENANT_ID, executionKey);
        assertThat(taskExecutionDomainService.abandonExecution(
            successSnapshot, "success must not be abandoned")).isZero();
        SysTaskExecution after = taskExecutionDomainService.findByExecutionKey(TENANT_ID, executionKey);
        assertThat(after.getStatus()).isEqualTo("SUCCESS");
        assertThat(after.getAttemptCount()).isEqualTo(2);
    }
}
