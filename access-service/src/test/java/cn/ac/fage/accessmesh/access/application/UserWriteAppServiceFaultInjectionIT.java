package cn.ac.fage.accessmesh.access.application;

import cn.ac.fage.accessmesh.access.admin.dto.req.UserCreateReq;
import cn.ac.fage.accessmesh.access.admin.dto.resp.UserCreateResp;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.cache.PermInvalidationPublisher;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService;
import cn.ac.fage.accessmesh.common.exception.SystemException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * T-ACCESS-005 验收「故障注入证明管理事实、权限投影和 permission_change_log 任一步失败都会整体回滚」
 * 与「缓存失效只在事务成功提交后发生，回滚不发布变更」的真实事务验证。
 * <p>
 * 对比纯 Mock 单测（{@link UserWriteAppServiceFaultInjectionTest}）：
 * 本测试经 Spring 事务代理调用真实 {@code UserWriteAppServiceImpl}（@Transactional 生效），
 * 真实 PostgreSQL（Testcontainers）落库，用 @SpyBean 在投影/审计步骤注入故障，
 * 断言管理事实表、投影表、change_log 表全部无残留，且回滚不触发 {@link PermInvalidationPublisher}。
 * Docker 不可用时由 Testcontainers 自动跳过（与既有 17 个 PG 测试一致）。
 * </p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    "accessmesh.sync.scheduler.enabled=false",
    "mybatis-flex.configuration.map-underscore-to-camel-case=true",
    "logging.level.cn.ac.fage.accessmesh=WARN",
    "JWT_SECRET_KEY=test-jwt-secret-for-fault-injection",
    "ACCESSMESH_SIGNATURE_SECRET=test-signature-secret-for-fault-injection",
    "PERM_INTERNAL_SECRET=test-internal-secret-for-fault-injection"
})
class UserWriteAppServiceFaultInjectionIT {

    private static final Long TENANT = 1L;
    private static final Long OPERATOR = 9L;
    private static final Path DDL_PATH = Path.of("..", "docs", "design", "schema", "access-service.sql");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("fault_inject_test")
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

    @BeforeAll
    static void setupSchema() throws Exception {
        // 原样执行权威 DDL + 种子数据（type_definition 等），供真实投影/审计路径落库
        String sql = Files.readString(DDL_PATH, StandardCharsets.UTF_8);
        try (var conn = java.sql.DriverManager.getConnection(
            postgres.getJdbcUrl() + "?stringtype=unspecified", postgres.getUsername(), postgres.getPassword());
             var st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    @Autowired
    private UserWriteAppService userWriteAppService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 投影层 spy：仅指定方法注入故障，其余真实（成功场景全链路落库）。 */
    @SpyBean
    private LocalProjectionDomainService localProjectionDomainService;

    /** 审计层 spy：change_log 注入故障。 */
    @SpyBean
    private AuditDomainService auditDomainService;

    /** 失效广播 mock：断言「回滚不发布变更」。 */
    @MockBean
    private PermInvalidationPublisher publisher;

    /** 门禁 mock（void 方法默认通过）：createUser 不依赖操作者权限数据。 */
    @MockBean
    private AdminPermissionValidator permissionValidator;



    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT);
        AccessRequestContext.bind(RequestContext.user(TENANT, OPERATOR));
        // 清理上个用例残留（成功用例会真实落库；测试间不共享断言状态）
        jdbcTemplate.execute("DELETE FROM permission_change_log WHERE tenant_id = " + TENANT);
        jdbcTemplate.execute("DELETE FROM user_role WHERE tenant_id = " + TENANT);
        jdbcTemplate.execute(
            "DELETE FROM resource_entity WHERE tenant_id = " + TENANT + " AND owner_service_code = 'access-service'");
        jdbcTemplate.execute(
            "DELETE FROM abstract_user WHERE tenant_id = " + TENANT + " AND owner_service_code = 'access-service'");
        jdbcTemplate.execute(
            "DELETE FROM abstract_role WHERE tenant_id = " + TENANT + " AND owner_service_code = 'access-service'");
        jdbcTemplate.execute("DELETE FROM sys_user_org WHERE tenant_id = " + TENANT);
        jdbcTemplate.execute("DELETE FROM sys_user WHERE tenant_id = " + TENANT);
    }

    @AfterEach
    void tearDown() {
        AccessRequestContext.clear();
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("投影失败：管理事实回滚（sys_user 无残留）、change_log 无记录、不发布缓存失效")
    void projectionFailureRollsBackFactAndChangeLog() {
        doThrow(new SystemException(90001, "projection failed"))
            .when(localProjectionDomainService)
            .upsertAdminUser(anyLong(), anyLong(), anyString(), anyBoolean(), any());

        assertThatThrownBy(() -> userWriteAppService.createUser(createReq()))
            .isInstanceOf(SystemException.class)
            .hasMessageContaining("projection failed");

        assertThat(countRows("sys_user")).isZero();
        assertThat(countRows("abstract_user")).isZero();
        assertThat(countRows("resource_entity")).isZero();
        assertThat(countRows("permission_change_log")).isZero();
        // 回滚：afterCommit 不执行 → 缓存失效不发布
        verify(publisher, never()).publish(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("change_log 失败：事实与投影整体回滚，不发布缓存失效")
    void changeLogFailureRollsBackFactAndProjection() {
        doThrow(new SystemException(90001, "change log failed"))
            .when(auditDomainService).recordChangeLog(any(), any());

        assertThatThrownBy(() -> userWriteAppService.createUser(createReq()))
            .isInstanceOf(SystemException.class)
            .hasMessageContaining("change log failed");

        assertThat(countRows("sys_user")).isZero();
        assertThat(countRows("abstract_user")).isZero();
        assertThat(countRows("resource_entity")).isZero();
        assertThat(countRows("permission_change_log")).isZero();
        verify(publisher, never()).publish(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("成功路径：事实/投影/change_log 落库，事务提交后发布缓存失效")
    void successPathCommitsAllAndPublishesInvalidation() {
        UserCreateResp resp = userWriteAppService.createUser(createReq());

        assertThat(resp.id()).isNotNull();
        assertThat(countRows("sys_user")).isEqualTo(1);
        assertThat(countRows("abstract_user")).isEqualTo(1);
        assertThat(countRows("resource_entity")).isEqualTo(1);
        assertThat(countRows("permission_change_log")).isEqualTo(1);
        // 提交后 @PermissionChange afterCommit flush → 广播失效
        verify(publisher, atLeastOnce()).publish(anyLong(), any(), any(), any());
    }

    private static UserCreateReq createReq() {
        return new UserCreateReq("alice", "Alice", null, null, 1, null, null);
    }

    private long countRows(String table) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE tenant_id = " + TENANT, Long.class);
        return count == null ? 0L : count;
    }
}
