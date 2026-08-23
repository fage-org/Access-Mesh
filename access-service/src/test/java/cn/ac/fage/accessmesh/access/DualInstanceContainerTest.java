package cn.ac.fage.accessmesh.access;

import cn.ac.fage.accessmesh.access.admin.cache.AdminCacheCatalog;
import cn.ac.fage.accessmesh.access.admin.service.domain.TaskExecutionDomainService;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * 双实例共享 PostgreSQL/Redis 容器测试（T-ACCESS-011 验收 9）。
 * <p>
 * 同 JVM 内以 {@link SpringApplication} 启动两个完整 access-service
 * ApplicationContext（实例 A 为标准 @SpringBootTest 上下文、实例 B 为手工启动上下文），
 * 共享同一容器 PostgreSQL 与 Redis：每个上下文拥有独立的 Caffeine L1、独立
 * Redisson 客户端（RTopic 广播真实跨实例）与独立任务执行编排，共同指向同一
 * 库表与键空间——即「两个实例」的最小真实形态。
 * </p>
 * <p>
 * 覆盖：权限缓存跨实例失效（L2 共享 + L1 失效广播）、任务并发抢占恰一胜、
 * 租约过期故障接管与原持有者 fencing。接管窗口为毫秒级（过期 UPDATE 后立即
 * 抢占），与 TaskLeaseTakeoverScheduler（首轮启动后 60s、周期 30s）无实际竞争。
 * Docker 可用时运行；本机无 Docker 自动跳过（CI 豁免口径）。
 * </p>
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker = true)
class DualInstanceContainerTest {

    private static final Long TENANT_ID = 1L;
    private static final String OWNER_A = "dual-instance-A";
    private static final String OWNER_B = "dual-instance-B";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("dual_instance_test")
            .withUsername("perm")
            .withPassword("perm");

    /** Redis 容器与客户端密码必须对齐：主配置 ${REDIS_PASSWORD:} 解析为空串而非 null，
     * Redisson 对空串仍发 AUTH，无密码 Redis 会拒绝（ERR AUTH called without any password） */
    private static final String REDIS_TEST_PASSWORD = "accessmesh-test";

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withCommand("redis-server", "--requirepass", REDIS_TEST_PASSWORD)
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> REDIS_TEST_PASSWORD);
    }

    /** 实例 B：独立 ApplicationContext，与实例 A 共享同一容器 PG/Redis。 */
    static ConfigurableApplicationContext instanceB;

    // 实例 A（标准测试上下文）
    @Autowired
    private CacheService cacheServiceA;
    @Autowired
    private TaskExecutionDomainService taskExecutionA;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private CacheService cacheServiceB;
    private TaskExecutionDomainService taskExecutionB;

    @BeforeAll
    static void initSchemaAndBootSecondInstance() throws Exception {
        // 容器已启动、两个 Spring 上下文均未创建：先建全部依赖表
        // （JobServiceImpl @PostConstruct 启动时跨租户查询 sys_job）
        try (Connection conn = DriverManager.getConnection(
                 postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
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
                        id              BIGSERIAL PRIMARY KEY,
                        tenant_id       BIGINT NOT NULL,
                        job_id          BIGINT NOT NULL,
                        job_name        VARCHAR(128),
                        invoke_target   VARCHAR(256),
                        status          SMALLINT NOT NULL DEFAULT 0,
                        message         VARCHAR(2000),
                        cost_time       BIGINT,
                        created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
                    )
                    """);
        }
        SpringApplication app = new SpringApplication(AccessServiceApplication.class);
        app.setAdditionalProfiles("test");
        // 必须以命令行参数注入容器地址：setDefaultProperties 优先级最低，
        // 会被 application.yml 的 127.0.0.1 本机地址压过（评审修复）；
        // 命令行参数优先级高于 ConfigData，实例 B 才会真正连接容器
        instanceB = app.run(
            "--spring.datasource.url=" + postgres.getJdbcUrl(),
            "--spring.datasource.username=" + postgres.getUsername(),
            "--spring.datasource.password=" + postgres.getPassword(),
            "--spring.datasource.driver-class-name=" + postgres.getDriverClassName(),
            "--spring.data.redis.host=" + redis.getHost(),
            "--spring.data.redis.port=" + redis.getMappedPort(6379),
            "--spring.data.redis.password=" + REDIS_TEST_PASSWORD);
    }

    @AfterAll
    static void closeSecondInstance() {
        if (instanceB != null) {
            instanceB.close();
        }
    }

    @BeforeEach
    void fetchInstanceBeans() {
        cacheServiceB = instanceB.getBean(CacheService.class);
        taskExecutionB = instanceB.getBean(TaskExecutionDomainService.class);
        jdbcTemplate.update("DELETE FROM sys_task_execution");
    }

    @Test
    @DisplayName("跨实例缓存：A 写入 B 可读（共享 L2），A 失效后 B 读取为 null（L1 广播失效）")
    void crossInstanceCache_sharedL2AndInvalidationBroadcast() throws Exception {
        Object key = "dual-instance-probe";
        Map<String, Long> value = Map.of("USER:VIEW", 1L);

        // 实例 A 写入（L1+L2）；实例 B 首读 miss L1 → 命中共享 L2 并回填 B 的 L1
        cacheServiceA.put(AdminCacheCatalog.OPERATION_CODE, TENANT_ID, key, value);
        Map<String, Long> seenByB = cacheServiceB.get(AdminCacheCatalog.OPERATION_CODE, TENANT_ID, key);
        assertThat(seenByB).as("实例 B 必须经共享 Redis L2 读到实例 A 写入的值").containsExactlyEntriesOf(value);

        // 实例 A 失效（清 L2 + RTopic 广播清各实例 L1）；广播为异步，轮询等待 B 侧清空
        cacheServiceA.evict(AdminCacheCatalog.OPERATION_CODE, TENANT_ID, key);
        long deadline = System.currentTimeMillis() + 5_000;
        Map<String, Long> afterEvict;
        do {
            afterEvict = cacheServiceB.get(AdminCacheCatalog.OPERATION_CODE, TENANT_ID, key);
            if (afterEvict == null) {
                break;
            }
            Thread.sleep(100);
        } while (System.currentTimeMillis() < deadline);
        assertThat(afterEvict).as("A 失效后 B（L1 已回填）必须在 5s 内读到 null——跨实例 L1 失效广播").isNull();
    }

    @Test
    @DisplayName("任务并发抢占：两实例就绪闭栏后同时抢占同一执行键，恰一胜（多轮）")
    void concurrentClaim_exactlyOneInstanceWins() throws Exception {
        // 顺序调用只能证明「先到先得 + 有效租约排他」；以双闭栏（ready=2 就绪确认 +
        // start 放行）保证两实例的 tryClaim 真正并发提交，多轮断言每轮恰一胜。
        // 结果收集必须消歧（评审修复）：抢占失败/线程未完成/线程异常退出三者不得共用 null——
        // outcome.attempt 仅承载返回值、outcome.error 捕获线程内异常并传播为断言失败，
        // join 后检查线程已结束（未卡死），否则一侧异常退出时 winners 仍为 1 会假通过。
        int rounds = 5;
        for (int round = 0; round < rounds; round++) {
            String key = "dual-instance-claim-" + round + "-" + System.nanoTime();
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            ClaimOutcome outcomeA = new ClaimOutcome();
            ClaimOutcome outcomeB = new ClaimOutcome();

            Thread threadA = new Thread(
                () -> runClaim(taskExecutionA, key, OWNER_A, ready, start, outcomeA), "instance-A-claim");
            Thread threadB = new Thread(
                () -> runClaim(taskExecutionB, key, OWNER_B, ready, start, outcomeB), "instance-B-claim");
            threadA.start();
            threadB.start();
            assertThat(ready.await(10, TimeUnit.SECONDS))
                .as("第 %d 轮：两个抢占线程必须先就绪再放行（保证并发提交）", round).isTrue();
            start.countDown();
            threadA.join(10_000);
            threadB.join(10_000);

            assertThat(threadA.isAlive() || threadB.isAlive())
                .as("第 %d 轮：抢占线程必须在超时内完成（未卡死）", round).isFalse();
            assertThat(outcomeA.error)
                .as("第 %d 轮：实例 A 抢占线程异常必须传播为失败", round).isNull();
            assertThat(outcomeB.error)
                .as("第 %d 轮：实例 B 抢占线程异常必须传播为失败", round).isNull();

            int winners = (outcomeA.attempt != null ? 1 : 0) + (outcomeB.attempt != null ? 1 : 0);
            assertThat(winners)
                .as("第 %d 轮：两实例并发抢占同一执行键必须恰一胜，A=%s B=%s",
                    round, outcomeA.attempt, outcomeB.attempt)
                .isEqualTo(1);
        }
    }

    private void runClaim(TaskExecutionDomainService service, String key, String owner,
                          CountDownLatch ready, CountDownLatch start, ClaimOutcome outcome) {
        ready.countDown();
        try {
            start.await();
            outcome.attempt = service.tryClaim(TENANT_ID, key, owner);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            outcome.error = new IllegalStateException("claim 并发门闩被中断", e);
        } catch (RuntimeException e) {
            outcome.error = e;
        }
    }

    /** 抢占结果（线程安全）：attempt 仅承载 tryClaim 返回值（null=正常抢占失败），error 仅承载异常。 */
    private static final class ClaimOutcome {
        volatile Integer attempt;
        volatile RuntimeException error;
    }

    @Test
    @DisplayName("故障接管：A 持有租约过期后 B 接管，原持有者 A 被 fencing 不可续租")
    void leaseExpiry_takeoverBySecondInstanceAndFencing() {
        String key = "dual-instance-takeover-" + System.nanoTime();

        Integer attemptA = taskExecutionA.tryClaim(TENANT_ID, key, OWNER_A);
        assertThat(attemptA).isNotNull();

        // 模拟实例 A 故障：租约到期（直接回写过期时间，毫秒窗口内完成接管，
        // 与 30s 周期的接管扫描器无竞争）
        jdbcTemplate.update(
            "UPDATE sys_task_execution SET lease_until = now() - interval '5 seconds' "
                + "WHERE tenant_id = ? AND execution_key = ?", TENANT_ID, key);

        Integer attemptB = taskExecutionB.tryClaim(TENANT_ID, key, OWNER_B);
        assertThat(attemptB).as("租约过期后实例 B 必须能接管").isNotNull();
        assertThat(attemptB).as("接管方尝试号必须递增").isGreaterThan(attemptA);

        // 原持有者 fencing：A 的续租必须失败（租约已丢失），不得写回结果
        boolean renewedByA = taskExecutionA.renewLease(TENANT_ID, key, OWNER_A, attemptA);
        assertThat(renewedByA).as("被接管的实例 A 必须被 fencing，续租失败").isFalse();

        // 接管方 B 正常续租有效
        boolean renewedByB = taskExecutionB.renewLease(TENANT_ID, key, OWNER_B, attemptB);
        assertThat(renewedByB).as("接管方 B 续租必须成功").isTrue();
    }
}
