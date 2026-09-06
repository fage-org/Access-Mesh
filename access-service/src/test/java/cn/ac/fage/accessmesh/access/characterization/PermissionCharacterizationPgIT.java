package cn.ac.fage.accessmesh.access.characterization;

import cn.ac.fage.accessmesh.access.permission.cache.PermCacheCatalog;
import cn.ac.fage.accessmesh.access.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.common.cache.CacheService;
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
 * 权限读链路特征测试（T-ACCESS-017，真实 PostgreSQL + Redis，Testcontainers）。
 * <p>
 * 固化模型收敛 Epic（T-PERM-042 起）触碰的读链路的当前正确行为
 * （T-ORG-001：原 sys_user→abstract_user 转换链路随主体 ID 统一删除，
 *  {@code TypeResolutionService.resolveUserId} 的单元覆盖见 TypeResolutionServiceImplTest）：
 * </p>
 * <ol>
 *   <li><b>有效角色展开</b>：{@code resolveEffectiveRoles} 缓存 miss 回源回填、hit 复用、
 *       失效后回源可见 DB 变化；GROUP_ROLE 树展开 + 停用角色过滤。</li>
 *   <li><b>scopeAll 类型级放行</b>：{@code scope_all=true} 授权行放行该资源类型的
 *       任意实例（{@code hasPermissionByCode}/{@code getDeniedEntityIds}）。</li>
 * </ol>
 * <p>
 * USER/ROLE 实例门禁业务编码语义测试见 InstanceGateBusinessCodePgIT（T-PERM-042）；
 * 主体 ID 统一（同 ID 双表插入/不碰撞）测试见 LocalSubjectIdUnificationPgIT（T-ORG-001）。
 * Docker 不可用时由 Testcontainers 自动跳过（容器轨道）。
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
class PermissionCharacterizationPgIT {

    private static final Long TENANT = 1L;
    private static final Path DDL_PATH = Path.of("..", "docs", "design", "schema", "access-service.sql");

    /** type_definition 种子：user_type / LOCAL_USER = 3；role_type / GROUP_ROLE = 5、BASIC_ROLE = 6 */
    private static final int USER_TYPE_ADMIN = 3;
    private static final int ROLE_TYPE_GROUP = 5;
    private static final int ROLE_TYPE_BASIC = 6;
    /** resource_type 种子：SERVICE = 8；operation 种子：SERVICE:VIEW binary_bit = 2 */
    private static final int RESOURCE_TYPE_SERVICE = 8;
    private static final long SERVICE_VIEW_BIT = 2L;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("perm_read_char_test")
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
    private SubjectDomainService subjectDomainService;
    @Autowired
    private PermQueryEngine permQueryEngine;
    @Autowired
    private CacheService cacheService;
    @Autowired
    private JdbcTemplate jdbc;

    // ===== 链路 3：resolveEffectiveRoles 缓存 hit/miss 两态 =====

    @Test
    @DisplayName("有效角色：miss 回源回填 L2；hit 期间 DB 变更不可见；失效后回源见到新状态")
    void resolveEffectiveRolesShouldBackfillOnMissAndReuseOnHit() {
        Long user = insertAbstractUser("911001", "特征测试-角色用户");
        Long role = insertAbstractRole(ROLE_TYPE_BASIC, "911101", "特征测试-基础角色", 1, null);
        insertUserRole(user, "ROLE", role);

        // miss：首查回源 DB，结果回填 L2（EFFECTIVE_ROLES）
        assertThat(subjectDomainService.resolveEffectiveRoles(TENANT, user)).containsExactlyInAnyOrder(role);
        assertThat(cacheService.getBatch(PermCacheCatalog.EFFECTIVE_ROLES, TENANT, Set.of(user)))
            .containsKey(user);

        // hit 证明：不失效缓存的情况下软删关系——二次读仍返回缓存的旧值；
        // 若实现绕过缓存回源 DB，将得到空集，本断言即失败
        jdbc.update("UPDATE user_role SET delete_flag = id, deleted_at = now() "
            + "WHERE tenant_id = ? AND abstract_user_id = ? AND delete_flag = 0", TENANT, user);
        assertThat(subjectDomainService.resolveEffectiveRoles(TENANT, user)).containsExactlyInAnyOrder(role);

        // 失效后回源见到 DB 新状态：空集（afterCommit 自动失效的特征见
        // AuthorizationChangeInvalidationPgIT，此处固化失效 API + 回源语义）
        subjectDomainService.invalidateRoleCacheBatch(TENANT, Set.of(user));
        assertThat(subjectDomainService.resolveEffectiveRoles(TENANT, user)).isEmpty();
    }

    @Test
    @DisplayName("有效角色：GROUP_ROLE 按角色树展开为基本角色，停用角色被过滤")
    void resolveEffectiveRolesShouldExpandGroupRoleTreeAndFilterDisabled() {
        Long user = insertAbstractUser("911002", "特征测试-组角色用户");
        Long enabledBasic = insertAbstractRole(ROLE_TYPE_BASIC, "911201", "特征测试-启用基本角色", 1, null);
        Long disabledBasic = insertAbstractRole(ROLE_TYPE_BASIC, "911202", "特征测试-停用基本角色", 0, null);
        Long group = insertAbstractRole(ROLE_TYPE_GROUP, "911203", "特征测试-分组角色", 1, null);
        // 组角色的树形子节点承载基本角色（parent_id 挂载）
        jdbc.update("UPDATE abstract_role SET parent_id = ? WHERE id IN (?, ?)", group, enabledBasic, disabledBasic);
        insertUserRole(user, "GROUP_ROLE", group);

        assertThat(subjectDomainService.resolveEffectiveRoles(TENANT, user))
            .containsExactlyInAnyOrder(enabledBasic)
            .doesNotContain(disabledBasic);
    }

    // ===== 链路 4：scopeAll 类型级放行 =====

    @Test
    @DisplayName("scopeAll：scope_all=true 授权放行资源类型全部实例；无授权用户全拒")
    void scopeAllGrantShouldAllowTypeLevelAccess() {
        Long grantee = insertAbstractUser("912001", "特征测试-scopeAll用户");
        Long role = insertAbstractRole(ROLE_TYPE_BASIC, "912101", "特征测试-scopeAll角色", 1, null);
        insertUserRole(grantee, "ROLE", role);
        insertRolePerm(role, RESOURCE_TYPE_SERVICE, SERVICE_VIEW_BIT, true, null);

        // 类型级放行：任意实例业务编码（含不存在的 999）均通过 hasPermissionByCode
        assertThat(permQueryEngine.hasPermissionByCode(TENANT, grantee, "SERVICE", "999", "VIEW")).isTrue();
        // 批量门禁：scopeAll 命中 → 空拒绝集
        assertThat(permQueryEngine.getDeniedEntityIds(TENANT, grantee, "SERVICE", Set.of(1L, 2L, 3L), "VIEW")).isEmpty();

        // 对照组：无授权用户 fail-closed
        Long outsider = insertAbstractUser("912002", "特征测试-无授权用户");
        assertThat(permQueryEngine.hasPermissionByCode(TENANT, outsider, "SERVICE", "1", "VIEW")).isFalse();
        assertThat(permQueryEngine.getDeniedEntityIds(TENANT, outsider, "SERVICE", Set.of(1L, 2L), "VIEW"))
            .containsExactlyInAnyOrder(1L, 2L);
    }

    // ===== 数据装配 =====

    private Long insertAbstractUser(String externalId, String name) {
        return jdbc.queryForObject(
            "INSERT INTO abstract_user (tenant_id, user_type, external_id, name, enabled, extra, owner_service_code) "
                + "VALUES (?, ?, ?, ?, true, '{}', NULL) RETURNING id",
            Long.class, TENANT, USER_TYPE_ADMIN, externalId, name);
    }

    private Long insertAbstractRole(int roleType, String externalId, String name, int status, Long parentId) {
        return jdbc.queryForObject(
            "INSERT INTO abstract_role (tenant_id, role_type, external_id, name, status, parent_id, extra) "
                + "VALUES (?, ?, ?, ?, ?, ?, '{}') RETURNING id",
            Long.class, TENANT, roleType, externalId, name, status, parentId);
    }

    private void insertUserRole(Long abstractUserId, String targetType, Long targetId) {
        jdbc.update(
            "INSERT INTO user_role (tenant_id, abstract_user_id, target_type, target_id) VALUES (?, ?, ?, ?)",
            TENANT, abstractUserId, targetType, targetId);
    }

    private Long insertRolePerm(Long roleId, int resourceType, long grantedBits, boolean scopeAll, Long resourceEntityId) {
        return jdbc.queryForObject(
            "INSERT INTO role_resource_permission "
                + "(tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type, scope_all, grant_source) "
                + "VALUES (?, ?, ?, ?, ?, ?, 'MANUAL') RETURNING id",
            Long.class, TENANT, roleId, resourceEntityId, grantedBits, resourceType, scopeAll);
    }
}
