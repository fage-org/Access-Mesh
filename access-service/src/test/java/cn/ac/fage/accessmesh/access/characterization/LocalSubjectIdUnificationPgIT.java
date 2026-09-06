package cn.ac.fage.accessmesh.access.characterization;

import cn.ac.fage.accessmesh.access.admin.dto.req.UserCreateReq;
import cn.ac.fage.accessmesh.access.admin.dto.resp.UserCreateResp;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.application.UserWriteAppService;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 主体 ID 统一验收（T-ORG-001，真实 PostgreSQL + Redis，Testcontainers；architecture §12）。
 * <p>
 * 验收两条：① 新建本地用户后 {@code sys_user.id == abstract_user.id}（同 ID 双表插入，
 * external_id / resource_entity(USER).code = 主体 ID 字符串化）；② 外部主体与本地用户
 * 共用 {@code abstract_user.id} 序列（本地创建 nextval 预取、外部创建自增取号），
 * 「先外部后本地」与「先本地后外部」两种顺序均不碰撞。
 * </p>
 */
@Tag("testcontainers")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
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
})
class LocalSubjectIdUnificationPgIT {

    private static final Long TENANT = 1L;
    private static final Long OPERATOR = 9L;
    private static final Path DDL_PATH = Path.of("..", "docs", "design", "schema", "access-service.sql");

    /** type_definition 种子：user_type/LOCAL_USER = 3；resource_type 种子：USER = 6 */
    private static final int USER_TYPE_ADMIN = 3;
    private static final int RESOURCE_TYPE_USER = 6;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("subject_unification_test")
        .withUsername("perm")
        .withPassword("perm");

    /** Redis 容器与客户端密码必须对齐（见 PermissionCharacterizationPgIT 同款说明） */
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
    private UserWriteAppService userWriteAppService;
    @Autowired
    private JdbcTemplate jdbc;

    /** 门禁 mock（void 方法默认通过）：本测试不依赖操作者权限数据，聚焦主体 ID 链路。 */
    @MockBean
    private AdminPermissionValidator permissionValidator;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT);
        AccessRequestContext.bind(RequestContext.user(TENANT, OPERATOR));
    }

    @AfterEach
    void tearDown() {
        AccessRequestContext.clear();
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("新建本地用户：sys_user.id == abstract_user.id（同 ID 双表），external_id/资源 code = 主体 ID")
    void localUserCreationWritesSameIdInBothTables() {
        UserCreateResp resp = userWriteAppService.createUser(
            new UserCreateReq("subject-unify-1", "统一主体测试用户", null, null, 1, null, null));

        Long subjectId = resp.id();
        assertThat(subjectId).isNotNull();

        Map<String, Object> sysUser = jdbc.queryForMap(
            "SELECT id, username FROM sys_user WHERE tenant_id = ? AND username = ?", TENANT, "subject-unify-1");
        Map<String, Object> abstractUser = jdbc.queryForMap(
            "SELECT id, external_id, user_type FROM abstract_user WHERE tenant_id = ? AND id = ?", TENANT, subjectId);

        // 验收 ①：两表同 ID；external_id = 主体 ID 字符串化（§12.2/§12.3）
        assertThat(sysUser.get("id")).isEqualTo(subjectId);
        assertThat(abstractUser.get("id")).isEqualTo(subjectId);
        assertThat(abstractUser.get("external_id")).isEqualTo(String.valueOf(subjectId));
        assertThat(abstractUser.get("user_type")).isEqualTo(USER_TYPE_ADMIN);

        // 资源投影业务编码 = 主体 ID 字符串化（USER 门禁编码语义，T-PERM-042 契约）
        Map<String, Object> resource = jdbc.queryForMap(
            "SELECT id, code FROM resource_entity WHERE tenant_id = ? AND resource_type = ? AND code = ?",
            TENANT, RESOURCE_TYPE_USER, String.valueOf(subjectId));
        assertThat(resource.get("code")).isEqualTo(String.valueOf(subjectId));
    }

    @Test
    @DisplayName("先外部主体后本地用户：共用序列不碰撞；反向顺序同样安全（本地先取号占位序列水位）")
    void externalAndLocalSubjectsShareSequenceWithoutCollision() {
        // 先插外部主体（仅 abstract_user，自增取号；user_type=1 外部人员）
        Long externalId = jdbc.queryForObject(
            "INSERT INTO abstract_user (tenant_id, user_type, external_id, name, enabled, extra) "
                + "VALUES (?, 1, 'ext-before-local', '统一测试-先建外部主体', true, '{}') RETURNING id",
            Long.class, TENANT);

        // 再建本地用户（nextval 预取）：新主体 ID 与外部主体 ID 不同且不碰撞
        UserCreateResp local1 = userWriteAppService.createUser(
            new UserCreateReq("subject-unify-2", "统一主体测试用户2", null, null, 1, null, null));
        assertThat(local1.id()).isNotEqualTo(externalId);
        assertThat(local1.id()).isGreaterThan(externalId);

        // 反向顺序：本地用户先创建（占用序列水位），后插外部主体自增取号继续递增，同样不碰撞
        UserCreateResp local2 = userWriteAppService.createUser(
            new UserCreateReq("subject-unify-3", "统一主体测试用户3", null, null, 1, null, null));
        Long externalId2 = jdbc.queryForObject(
            "INSERT INTO abstract_user (tenant_id, user_type, external_id, name, enabled, extra) "
                + "VALUES (?, 1, 'ext-after-local', '统一测试-后建外部主体', true, '{}') RETURNING id",
            Long.class, TENANT);
        assertThat(externalId2).isNotEqualTo(local1.id()).isNotEqualTo(local2.id());
        assertThat(externalId2).isGreaterThan(local2.id());

        // 三个本地/外部主体行互不冲突（uk 完整、无软删）
        Long count = jdbc.queryForObject(
            "SELECT count(*) FROM abstract_user WHERE tenant_id = ? AND delete_flag = 0 AND id IN (?, ?, ?, ?)",
            Long.class, TENANT, externalId, local1.id(), local2.id(), externalId2);
        assertThat(count).isEqualTo(4L);
    }
}
