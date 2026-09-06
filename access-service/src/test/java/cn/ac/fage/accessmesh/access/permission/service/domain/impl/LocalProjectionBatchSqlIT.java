package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本地投影批量 SQL 的真实 PostgreSQL 验证（Testcontainers，Docker 可用时执行）。
 * <p>
 * 回归：批量 UPDATE VALUES 子句必须显式 CAST——全 unknown 参数的 VALUES 列表会被
 * PG 推断为 text 列，text→boolean 与 COALESCE(text, jsonb) 均报 42804；stringtype=unspecified
 * 不救 VALUES 推断。本测试在真实 PG 上执行 batchUpsertAdminUsers（已有行路径走 batchUpdateValues）
 * 与 batchDeleteAdminUsers（级联软删 user_role），Docker 不可用时由 Testcontainers 跳过。
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
    "JWT_SECRET_KEY=test-jwt-secret-for-batch-sql",
    "ACCESSMESH_SIGNATURE_SECRET=test-signature-secret-for-batch-sql",
    "PERM_INTERNAL_SECRET=test-internal-secret-for-batch-sql"
})
class LocalProjectionBatchSqlIT {

    private static final Long TENANT = 1L;
    private static final Path DDL_PATH = Path.of("..", "docs", "design", "schema", "access-service.sql");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("batch_sql_test")
        .withUsername("perm")
        .withPassword("perm");

    /** Redis 容器与客户端密码必须对齐：主配置 ${REDIS_PASSWORD:} 解析为空串而非 null，
     * Redisson 对空串仍发 AUTH，无密码 Redis 会拒绝（ERR AUTH called without any password） */
    private static final String REDIS_TEST_PASSWORD = "accessmesh-test";

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withCommand("redis-server", "--requirepass", REDIS_TEST_PASSWORD)
        .withExposedPorts(6379);

    /**
     * Testcontainers 的 getJdbcUrl() 已自带查询参数，直接追加 "?stringtype=unspecified"
     * 会并入前一个参数值被 pgjdbc 静默忽略，按是否已含 "?" 选择分隔符
     * （T-ADMIN-026 订正；先例 KeywordLikeSearchPgIT.urlWithStringtype）。
     */
    static String urlWithStringtype() {
        String url = postgres.getJdbcUrl();
        return url + (url.contains("?") ? "&" : "?") + "stringtype=unspecified";
    }


    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> urlWithStringtype());
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> REDIS_TEST_PASSWORD);
    }

    @BeforeAll
    static void setupSchema() throws Exception {
        String sql = Files.readString(DDL_PATH, StandardCharsets.UTF_8);
        try (var conn = java.sql.DriverManager.getConnection(
            urlWithStringtype(), postgres.getUsername(), postgres.getPassword());
             var st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    @Autowired
    private LocalProjectionDomainService localProjectionDomainService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("batchUpsert 已有行路径：batchUpdateValues 真实 PG 执行（JSONB extra + boolean enabled 不报 42804）")
    void batchUpsertUpdatesExistingRowsOnRealPostgres() {
        Long sysUserId = 900001L;

        // 第一次：插入新投影行（含 JSONB extra）
        Map<Long, Long> first = localProjectionDomainService.batchUpsertAdminUsers(TENANT,
            List.of(new LocalProjectionDomainService.UpsertUserKey(
                sysUserId, "张三", true, "{\"username\":\"zhang\"}")));
        assertThat(first).containsKey(sysUserId);
        Long abstractUserId = first.get(sysUserId);
        assertThat(abstractUserId).isNotNull();

        // 第二次：走已有行批量刷新（batchUpdateValues：CAST 后的 VALUES 单条 SQL）。
        // 若 VALUES 缺 CAST，PG 报 42804 整批回滚 → 本调用抛异常即测试失败
        Map<Long, Long> second = localProjectionDomainService.batchUpsertAdminUsers(TENANT,
            List.of(new LocalProjectionDomainService.UpsertUserKey(
                sysUserId, "张三丰", false, "{\"username\":\"zhang3\"}")));
        assertThat(second).containsEntry(sysUserId, abstractUserId);

        // 落库断言：name/enabled/extra 均已刷新（JSONB 更新生效）
        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT name, enabled, extra FROM abstract_user WHERE id = ?", abstractUserId);
        assertThat(row.get("name")).isEqualTo("张三丰");
        assertThat(row.get("enabled")).isEqualTo(Boolean.FALSE);
        assertThat(String.valueOf(row.get("extra"))).contains("zhang3");

        Map<String, Object> resRow = jdbcTemplate.queryForMap(
            "SELECT name, status FROM resource_entity WHERE tenant_id = ? AND resource_type = 6"
                + " AND code = ? AND code_type = 'default' AND delete_flag = 0",
            TENANT, String.valueOf(sysUserId));
        assertThat(resRow.get("name")).isEqualTo("张三丰");
        assertThat(resRow.get("status")).isEqualTo(0);
    }

    @Test
    @DisplayName("batchDelete 级联：abstract_user + 全部 user_role + USER 资源同一事务软删")
    void batchDeleteCascadesUserRoles() {
        Long sysUserId = 900002L;
        Map<Long, Long> ids = localProjectionDomainService.batchUpsertAdminUsers(TENANT,
            List.of(new LocalProjectionDomainService.UpsertUserKey(sysUserId, "级联测试", true, null)));
        Long abstractUserId = ids.get(sysUserId);

        // 造一条功能角色关系（非 ORG/POSITION 成员关系，验证级联覆盖）
        jdbcTemplate.update(
            "INSERT INTO user_role (tenant_id, abstract_user_id, target_type, target_id, relation_id,"
                + " owner_service_code, created_at, updated_at, delete_flag)"
                + " VALUES (?, ?, 'ROLE', 5001, 5001, 'access-service', now(), now(), 0)",
            TENANT, abstractUserId);

        localProjectionDomainService.batchDeleteAdminUsers(TENANT, Set.of(sysUserId));

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM abstract_user WHERE id = ? AND delete_flag = 0", Integer.class, abstractUserId))
            .isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_role WHERE abstract_user_id = ? AND delete_flag = 0",
            Integer.class, abstractUserId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM resource_entity WHERE tenant_id = ? AND resource_type = 6"
                + " AND code = ? AND code_type = 'default' AND delete_flag = 0",
            Integer.class, TENANT, String.valueOf(sysUserId))).isZero();
    }
}
