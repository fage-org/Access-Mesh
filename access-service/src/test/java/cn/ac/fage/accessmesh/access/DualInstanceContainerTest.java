package cn.ac.fage.accessmesh.access;

import cn.ac.fage.accessmesh.access.admin.cache.AdminCacheCatalog;
import cn.ac.fage.accessmesh.access.admin.service.domain.TaskExecutionDomainService;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
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
@SpringBootTest
@ActiveProfiles("test")
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

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
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
        app.setDefaultProperties(Map.of(
            "spring.datasource.url", postgres.getJdbcUrl(),
            "spring.datasource.username", postgres.getUsername(),
            "spring.datasource.password", postgres.getPassword(),
            "spring.datasource.driver-class-name", postgres.getDriverClassName(),
            "spring.data.redis.host", redis.getHost(),
            "spring.data.redis.port", redis.getMappedPort(6379)));
        instanceB = app.run();
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
        Map<String, Long> value = Map.of("ADMIN_USER:VIEW", 1L);

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
    @DisplayName("任务并发抢占：两实例抢占同一执行键，恰一胜")
    void concurrentClaim_exactlyOneInstanceWins() {
        String key = "dual-instance-claim-" + System.nanoTime();

        Integer attemptA = taskExecutionA.tryClaim(TENANT_ID, key, OWNER_A);
        Integer attemptB = taskExecutionB.tryClaim(TENANT_ID, key, OWNER_B);

        assertThat(attemptA == null || attemptB == null)
            .as("同一执行键两实例并发抢占必须恰一胜，A=%s B=%s", attemptA, attemptB)
            .isTrue();
        assertThat(attemptA).as("先抢占方（A）必须成功").isNotNull();
        assertThat(attemptB).as("后抢占方（B）必须失败").isNull();
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
