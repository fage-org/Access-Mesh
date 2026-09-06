package cn.ac.fage.accessmesh.access.characterization;

import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * USER/ROLE 实例门禁业务编码语义（T-PERM-042，真实 PostgreSQL + Redis，Testcontainers）。
 * <p>
 * 任务卡验收：修复前生产代码把 {@code abstract_user.id}/{@code abstract_role.id} 直接当
 * {@code resource_entity.id} 传入实例门禁（ID 空间错位，跨空间可能撞值）。本类固化修复后的
 * 正确预期——门禁走业务编码轨（{@code getDeniedResourceCodes}/{@code hasPermissionByCode}），
 * {@code resource_entity(USER).code = subjectId}、{@code resource_entity(ROLE).code = roleId}
 * （architecture §12.3）。测试自装配 resource_entity 投影 fixtures（显式高位 id，保证与主体/角色
 * id 空间无碰撞），不依赖生产写路径投影（真实写路径投影归 T-ACCESS-019）。
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
class InstanceGateBusinessCodePgIT {

    private static final Long TENANT = 1L;
    private static final Path DDL_PATH = Path.of("..", "docs", "design", "schema", "access-service.sql");

    /** type_definition 种子：user_type/LOCAL_USER = 3；role_type/BASIC_ROLE = 6 */
    private static final int USER_TYPE_ADMIN = 3;
    private static final int ROLE_TYPE_BASIC = 6;
    /** resource_type 种子：USER = 6、ROLE = 5；操作种子：USER:MANAGE bit=16、ROLE:MANAGE bit=16 */
    private static final int RESOURCE_TYPE_USER = 6;
    private static final int RESOURCE_TYPE_ROLE = 5;
    private static final long MANAGE_BIT = 16L;

    /** 自装配投影显式 id 段：远离 abstract_user/abstract_role 序列，杜绝跨空间撞值干扰断言 */
    private static final long USER_ENTITY_OPERATOR = 900101L;
    private static final long USER_ENTITY_TARGET = 900102L;
    private static final long ROLE_ENTITY_GRANTED = 900201L;
    private static final long ROLE_ENTITY_UNGRANTED = 900202L;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("instance_gate_code_test")
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
    private PermQueryEngine permQueryEngine;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("USER 实例门禁：业务编码（subjectId 字符串）经投影解析命中实例授权，未授权用户被拒")
    void userInstanceGateShouldMatchInstanceGrantByBusinessCode() {
        Long operator = insertAbstractUser("920001", "门禁测试-操作者");
        Long targetUser = insertAbstractUser("920002", "门禁测试-目标用户");
        Long role = insertAbstractRole("920101", "门禁测试-授权角色");
        insertUserRole(operator, role);
        // 自装配 USER 投影：code = subjectId（§12.3 业务编码语义）
        insertResourceEntity(USER_ENTITY_OPERATOR, RESOURCE_TYPE_USER, String.valueOf(operator), "门禁测试-操作者投影");
        insertResourceEntity(USER_ENTITY_TARGET, RESOURCE_TYPE_USER, String.valueOf(targetUser), "门禁测试-目标用户投影");
        // 实例授权只挂在操作者自己的投影上
        insertRolePerm(role, RESOURCE_TYPE_USER, MANAGE_BIT, USER_ENTITY_OPERATOR);

        // 被授权编码（操作者自身）放行，未授权编码（目标用户）拒绝
        assertThat(permQueryEngine.getDeniedResourceCodes(
            TENANT, operator, "USER",
            Set.of(String.valueOf(operator), String.valueOf(targetUser)), "MANAGE"))
            .containsExactly(String.valueOf(targetUser));
        assertThat(permQueryEngine.hasPermissionByCode(
            TENANT, operator, "USER", String.valueOf(operator), "MANAGE")).isTrue();
        assertThat(permQueryEngine.hasPermissionByCode(
            TENANT, operator, "USER", String.valueOf(targetUser), "MANAGE")).isFalse();

        // 错参防线：修复前 abstract_user.id 被当 resource_entity.id 直查——显式高位投影 id 装配下，
        // 主体 id 落在 entity 空间必为未授权实体 → 全量拒绝（fail-closed，不再可能跨空间撞值误放行）
        assertThat(permQueryEngine.getDeniedEntityIds(
            TENANT, operator, "USER", Set.of(operator, targetUser), "MANAGE"))
            .containsExactlyInAnyOrder(operator, targetUser);
    }

    @Test
    @DisplayName("ROLE 实例门禁：业务编码（roleId 字符串）命中实例授权；无投影/未授权编码 fail-closed")
    void roleInstanceGateShouldMatchInstanceGrantByBusinessCode() {
        Long operator = insertAbstractUser("920003", "门禁测试-角色操作者");
        Long grantedRole = insertAbstractRole("920102", "门禁测试-被授权角色");
        Long ungrantedRole = insertAbstractRole("920103", "门禁测试-未授权角色");
        Long noProjectionRole = insertAbstractRole("920104", "门禁测试-无投影角色");
        insertUserRole(operator, grantedRole);
        // 自装配 ROLE 投影：code = roleId（§12.3）；920104 无投影行
        insertResourceEntity(ROLE_ENTITY_GRANTED, RESOURCE_TYPE_ROLE, String.valueOf(grantedRole), "门禁测试-被授权角色投影");
        insertResourceEntity(ROLE_ENTITY_UNGRANTED, RESOURCE_TYPE_ROLE, String.valueOf(ungrantedRole), "门禁测试-未授权角色投影");
        insertRolePerm(grantedRole, RESOURCE_TYPE_ROLE, MANAGE_BIT, ROLE_ENTITY_GRANTED);

        Set<String> denied = permQueryEngine.getDeniedResourceCodes(
            TENANT, operator, "ROLE",
            Set.of(String.valueOf(grantedRole), String.valueOf(ungrantedRole), String.valueOf(noProjectionRole)),
            "MANAGE");

        // 未授权角色与无投影角色均 fail-closed 拒绝
        assertThat(denied).containsExactlyInAnyOrder(
            String.valueOf(ungrantedRole), String.valueOf(noProjectionRole));
        assertThat(permQueryEngine.hasPermissionByCode(
            TENANT, operator, "ROLE", String.valueOf(grantedRole), "MANAGE")).isTrue();
    }

    @Test
    @DisplayName("无角色主体：USER/ROLE 业务编码全量拒绝（fail-closed）")
    void subjectWithoutRoleShouldBeDeniedOnAllCodes() {
        Long outsider = insertAbstractUser("920004", "门禁测试-无角色用户");
        insertResourceEntity(900103L, RESOURCE_TYPE_USER, String.valueOf(outsider), "门禁测试-无角色用户投影");

        assertThat(permQueryEngine.getDeniedResourceCodes(
            TENANT, outsider, "USER", Set.of(String.valueOf(outsider)), "MANAGE"))
            .containsExactly(String.valueOf(outsider));
        assertThat(permQueryEngine.hasPermissionByCode(
            TENANT, outsider, "USER", String.valueOf(outsider), "MANAGE")).isFalse();
    }

    @Test
    @DisplayName("scopeAll 类型级授权放行无投影业务编码（评审 P1：批量门禁不被投影缺失误拒）")
    void scopeAllShouldAllowCodesWithoutProjection() {
        Long operator = insertAbstractUser("920005", "门禁测试-scopeAll操作者");
        Long role = insertAbstractRole("920105", "门禁测试-scopeAll角色");
        insertUserRole(operator, role);
        // 仅类型级 scope_all=true 授权（resource_entity_id 必须为 NULL），不做任何实例投影授权
        insertScopeAllRolePerm(role, RESOURCE_TYPE_USER, MANAGE_BIT);
        Long projected = insertAbstractUser("920006", "门禁测试-有投影用户");
        insertResourceEntity(900104L, RESOURCE_TYPE_USER, String.valueOf(projected), "门禁测试-有投影用户投影");

        // 有投影 + 无投影编码混合：scopeAll 命中全部放行（投影缺失不得误拒）
        assertThat(permQueryEngine.getDeniedResourceCodes(
            TENANT, operator, "USER",
            Set.of(String.valueOf(projected), "920099"), "MANAGE")).isEmpty();
        assertThat(permQueryEngine.hasPermissionByCode(
            TENANT, operator, "USER", "920099", "MANAGE")).isTrue();
    }

    // ===== 数据装配（自装配投影，不依赖生产写路径） =====

    private Long insertAbstractUser(String externalId, String name) {
        return jdbc.queryForObject(
            "INSERT INTO abstract_user (tenant_id, user_type, external_id, name, enabled, extra, owner_service_code) "
                + "VALUES (?, ?, ?, ?, true, '{}', NULL) RETURNING id",
            Long.class, TENANT, USER_TYPE_ADMIN, externalId, name);
    }

    private Long insertAbstractRole(String externalId, String name) {
        return jdbc.queryForObject(
            "INSERT INTO abstract_role (tenant_id, role_type, external_id, name, status, parent_id, extra) "
                + "VALUES (?, ?, ?, ?, 1, NULL, '{}') RETURNING id",
            Long.class, TENANT, ROLE_TYPE_BASIC, externalId, name);
    }

    private void insertUserRole(Long abstractUserId, Long targetId) {
        jdbc.update(
            "INSERT INTO user_role (tenant_id, abstract_user_id, target_type, target_id) VALUES (?, ?, 'ROLE', ?)",
            TENANT, abstractUserId, targetId);
    }

    private void insertResourceEntity(long id, int resourceType, String code, String name) {
        jdbc.update(
            "INSERT INTO resource_entity (id, tenant_id, resource_type, code, code_type, name, status, extra) "
                + "VALUES (?, ?, ?, ?, 'default', ?, 1, '{}')",
            id, TENANT, resourceType, code, name);
    }

    private void insertRolePerm(Long roleId, int resourceType, long grantedBits, long resourceEntityId) {
        jdbc.update(
            "INSERT INTO role_resource_permission "
                + "(tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type, scope_all, grant_source) "
                + "VALUES (?, ?, ?, ?, ?, false, 'MANUAL')",
            TENANT, roleId, resourceEntityId, grantedBits, resourceType);
    }

    private void insertScopeAllRolePerm(Long roleId, int resourceType, long grantedBits) {
        jdbc.update(
            "INSERT INTO role_resource_permission "
                + "(tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type, scope_all, grant_source) "
                + "VALUES (?, ?, NULL, ?, ?, true, 'MANUAL')",
            TENANT, roleId, grantedBits, resourceType);
    }
}
