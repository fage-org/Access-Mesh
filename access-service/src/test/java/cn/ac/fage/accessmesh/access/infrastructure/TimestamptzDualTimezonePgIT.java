package cn.ac.fage.accessmesh.access.infrastructure;

import cn.ac.fage.accessmesh.access.infrastructure.entity.SystemConfig;
import cn.ac.fage.accessmesh.access.infrastructure.mapper.SystemConfigMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-ACCESS-024 双时区验证（容器轨道，用户决策：测试内切换默认时区）。
 * <p>
 * 同一测试 JVM 内将默认时区在 Asia/Shanghai（UTC+8）与 UTC 间切换，各轮经
 * 全局注册的 {@link TimestamptzLocalDateTimeTypeHandler}（真实 MyBatis-Flex
 * mapper 链路）对同一 PostgreSQL 写入/读取相同 {@code LocalDateTime}：
 * </p>
 * <ul>
 *   <li>任一时区轮内写入的固定墙钟，读回应等于原值（往返一致）</li>
 *   <li>UTC 轮读取 +8 轮写入的行，墙钟不变（跨部署时区读一致）</li>
 *   <li>两轮写入同一墙钟，库内瞬时（绕过 handler 直读 OffsetDateTime）相同
 *       且等于该墙钟按 UTC 解释的瞬时——TIMESTAMPTZ 语义与 JVM 时区解耦</li>
 * </ul>
 * <p>
 * 注意：生产点墙钟正确性由 common {@code UtcTimezoneEnvironmentPostProcessor}
 * 强制 JVM UTC 保证，本类对默认时区的人为切换只服务于 handler 解耦性验证，
 * 不代表允许的部署形态。
 * </p>
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    "accessmesh.sync.scheduler.enabled=false",
    "mybatis-flex.configuration.map-underscore-to-camel-case=true",
    "logging.level.cn.ac.fage.accessmesh=WARN",
    "JWT_SECRET_KEY=test-jwt-secret-for-dual-timezone",
    "ACCESSMESH_SIGNATURE_SECRET=test-signature-secret-for-dual-timezone",
    "PERM_INTERNAL_SECRET=test-internal-secret-for-dual-timezone"
})
class TimestamptzDualTimezonePgIT {

    private static final Long TENANT = 1L;
    private static final Path DDL_PATH = Path.of("..", "docs", "design", "schema", "access-service.sql");

    /** 固定墙钟标记（带纳秒位，防止秒级截断掩盖偏移） */
    private static final LocalDateTime MARKER =
        LocalDateTime.of(2026, 8, 25, 12, 34, 56, 789_000_000);

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("dual_timezone_test")
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
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl() + "?stringtype=unspecified");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> REDIS_TEST_PASSWORD);
    }

    @Autowired
    private SystemConfigMapper systemConfigMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static TimeZone originalZone;

    @BeforeAll
    static void setupSchema() throws Exception {
        originalZone = TimeZone.getDefault();
        // 原样执行权威 DDL（system_config 为独立表，无投影/变更日志副作用）
        String sql = Files.readString(DDL_PATH, StandardCharsets.UTF_8);
        try (var conn = java.sql.DriverManager.getConnection(
            postgres.getJdbcUrl() + "?stringtype=unspecified", postgres.getUsername(), postgres.getPassword());
             var st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    @AfterAll
    static void restoreDefaultZone() {
        TimeZone.setDefault(originalZone);
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("双时区一致性：UTC+8 与 UTC 轮次对同一 PG 写读相同 LocalDateTime 结果一致")
    void sameLocalDateTimeRoundTripsIdenticallyUnderDifferentJvmTimezones() {
        TenantContextHolder.setTenantId(TENANT);
        try {
            // —— 轮次 A：JVM 默认时区 Asia/Shanghai（UTC+8）——
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            Long idA = insertMarkerRow("utc-it.marker.a");
            assertThat(readCreatedAt(idA))
                .as("UTC+8 轮内写入读回应等于原墙钟")
                .isEqualTo(MARKER);

            // —— 轮次 B：JVM 默认时区切到 UTC ——
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            assertThat(readCreatedAt(idA))
                .as("UTC 轮读取 UTC+8 轮写入的行，墙钟应不变（跨部署时区读一致）")
                .isEqualTo(MARKER);

            Long idB = insertMarkerRow("utc-it.marker.b");
            assertThat(readCreatedAt(idB))
                .as("UTC 轮内写入读回应等于原墙钟")
                .isEqualTo(MARKER);

            // —— 库内瞬时直证：同一墙钟两轮写入的 TIMESTAMPTZ 瞬时相同，且等于墙钟按 UTC 解释 ——
            OffsetDateTime storedA = readStoredInstant(idA);
            OffsetDateTime storedB = readStoredInstant(idB);
            assertThat(storedA).isEqualTo(storedB);
            assertThat(storedA.atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime())
                .as("库内瞬时应等于墙钟按 UTC 解释（与 JVM 时区无关）")
                .isEqualTo(MARKER);
        } finally {
            TimeZone.setDefault(originalZone);
        }
    }

    private Long insertMarkerRow(String configKey) {
        SystemConfig config = new SystemConfig();
        config.setTenantId(TENANT);
        config.setConfigKey(configKey);
        config.setConfigValue("{}");
        config.setConfigName("T-ACCESS-024 双时区验证标记");
        config.setIsSystem(false);
        config.setDeleteFlag(0L);
        config.setCreatedAt(MARKER);
        config.setUpdatedAt(MARKER);
        systemConfigMapper.insert(config);
        assertThat(config.getId()).isNotNull();
        return config.getId();
    }

    private LocalDateTime readCreatedAt(Long id) {
        SystemConfig reloaded = systemConfigMapper.selectOneById(id);
        assertThat(reloaded).isNotNull();
        return reloaded.getCreatedAt();
    }

    /** 绕过 TypeHandler，经 pgjdbc 原生 OffsetDateTime 直读库内瞬时 */
    private OffsetDateTime readStoredInstant(Long id) {
        return jdbcTemplate.query(
            "SELECT created_at FROM system_config WHERE id = ?",
            rs -> rs.next() ? rs.getObject(1, OffsetDateTime.class) : null,
            id);
    }
}
