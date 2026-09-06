package cn.ac.fage.accessmesh.access.characterization;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.permission.cache.PermCacheCatalog;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermQuery;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.access.permission.dto.req.ApplyGrantPlanReq;
import cn.ac.fage.accessmesh.access.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.access.permission.service.PermissionGrantAppService;
import cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 授权变更 afterCommit 缓存失效特征测试（T-ACCESS-017·链路 5，真实 PostgreSQL + Redis）。
 * <p>
 * 固化完整链路的当前正确行为：{@code PermissionGrantAppService.applyGrantPlan}（removes）真实调用
 * （真实事务 + @PermissionChange 切面）→ 事务提交后 afterCommit flush →
 * EFFECTIVE_ROLES / ROLE_PERM_SNAPSHOT 缓存失效 → 下次权限查询 miss 回源拿到撤销后的新状态。
 * 操作者门禁走真实引擎（scopeAll 的 ROLE:MANAGE 授权）与真实 Resolver 映射。
 * </p>
 * <p>
 * 30 秒 Gateway 撤权总边界的验证归 T-ACCESS-021 E2E，本类只覆盖 access-service 侧。
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
class AuthorizationChangeInvalidationPgIT {

    private static final Long TENANT = 1L;
    private static final Path DDL_PATH = Path.of("..", "docs", "design", "schema", "access-service.sql");

    private static final int USER_TYPE_ADMIN = 3;
    private static final int ROLE_TYPE_BASIC = 6;
    /** resource_type 种子：ROLE = 5、SERVICE = 8；ROLE:MANAGE binary_bit = 16、SERVICE:VIEW = 2 */
    private static final int RESOURCE_TYPE_ROLE = 5;
    private static final int RESOURCE_TYPE_SERVICE = 8;
    private static final long ROLE_MANAGE_BIT = 16L;
    private static final long SERVICE_VIEW_BIT = 2L;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("grant_invalidation_test")
        .withUsername("perm")
        .withPassword("perm");

    /** Redis 容器与客户端密码必须对齐（同 PermissionCharacterizationPgIT） */
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
    private PermissionGrantAppService permissionGrantAppService;
    @Autowired
    private SubjectDomainService subjectDomainService;
    @Autowired
    private cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine permQueryEngine;
    @Autowired
    private CacheService cacheService;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("applyGrantPlan removes：事务提交后 afterCommit 失效缓存，重查回源拿到撤销后的新状态")
    void batchRevokeShouldInvalidateCachesAfterCommitAndReloadFromDb() {
        // -- 装配：操作者（scopeAll 的 ROLE:MANAGE 授权）+ 目标角色 + 受影响用户 --
        Long operatorSysUserId = 920001L;
        // T-ORG-001：统一主体 ID——操作者主体显式同 ID 落库（operatorId 即主体 ID，无转换层）
        Long operatorSubject = insertAbstractUserWithId(operatorSysUserId, "特征测试-撤销操作者");
        Long operatorRole = insertAbstractRole("op-role-920101", "特征测试-操作者角色");
        insertUserRole(operatorSubject, "ROLE", operatorRole);
        insertRolePerm(operatorRole, RESOURCE_TYPE_ROLE, ROLE_MANAGE_BIT, true, null);

        Long affectedUser = insertAbstractUser("920002", "特征测试-受影响用户");
        Long targetRole = insertAbstractRole("target-role-920201", "特征测试-目标角色");
        insertUserRole(affectedUser, "ROLE", targetRole);
        // 被撤销的授权行 P + 保留的授权行 P2（revoke 后角色仍有权限，forUserView 可断言新状态）
        Long revokedPermId = insertRolePerm(targetRole, RESOURCE_TYPE_SERVICE, SERVICE_VIEW_BIT, false, 929001L);
        Long keptPermId = insertRolePerm(targetRole, RESOURCE_TYPE_SERVICE, SERVICE_VIEW_BIT, false, 929002L);

        // -- 预热缓存：EFFECTIVE_ROLES 走真实回源；ROLE_PERM_SNAPSHOT 写入撤销前旧值（含 P）--
        assertThat(subjectDomainService.resolveEffectiveRoles(TENANT, affectedUser))
            .containsExactlyInAnyOrder(targetRole);
        RolePermEntry revokedEntry = new RolePermEntry(revokedPermId, targetRole, 929001L, null,
            RESOURCE_TYPE_SERVICE, SERVICE_VIEW_BIT, null, SERVICE_VIEW_BIT, "MANUAL", false, null, false, null, false);
        cacheService.put(PermCacheCatalog.ROLE_PERM_SNAPSHOT, TENANT, targetRole, List.of(revokedEntry));
        assertThat(cacheService.getBatch(PermCacheCatalog.EFFECTIVE_ROLES, TENANT, Set.of(affectedUser)))
            .containsKey(affectedUser);
        assertThat(cacheService.getBatch(PermCacheCatalog.ROLE_PERM_SNAPSHOT, TENANT, Set.of(targetRole)))
            .containsKey(targetRole);

        // -- 真实调用：操作者上下文绑定 + batchRevoke（真实事务 + 切面 + afterCommit）--
        AccessRequestContext.bind(RequestContext.user(TENANT, operatorSysUserId));
        try {
            permissionGrantAppService.applyGrantPlan(TENANT, new ApplyGrantPlanReq(
                null, "BASIC_ROLE", "target-role-920201",
                new ApplyGrantPlanReq.GrantPlan(null, null, List.of(revokedPermId))));
        } finally {
            AccessRequestContext.clear();
        }

        // -- DB 断言：P 软删、P2 保留 --
        Long revokedFlag = jdbc.queryForObject(
            "SELECT delete_flag FROM role_resource_permission WHERE id = ?", Long.class, revokedPermId);
        assertThat(revokedFlag).isEqualTo(revokedPermId);
        Long keptFlag = jdbc.queryForObject(
            "SELECT delete_flag FROM role_resource_permission WHERE id = ?", Long.class, keptPermId);
        assertThat(keptFlag).isZero();

        // -- afterCommit 失效断言：两类缓存均被清除（方法返回时 afterCommit 已同步执行）--
        assertThat(cacheService.getBatch(PermCacheCatalog.EFFECTIVE_ROLES, TENANT, Set.of(affectedUser)))
            .doesNotContainKey(affectedUser);
        assertThat(cacheService.getBatch(PermCacheCatalog.ROLE_PERM_SNAPSHOT, TENANT, Set.of(targetRole)))
            .doesNotContainKey(targetRole);

        // -- 重查回源断言：有效角色重建回填；引擎视角权限快照为撤销后的新状态（无 P、有 P2）--
        assertThat(subjectDomainService.resolveEffectiveRoles(TENANT, affectedUser))
            .containsExactlyInAnyOrder(targetRole);
        assertThat(cacheService.getBatch(PermCacheCatalog.EFFECTIVE_ROLES, TENANT, Set.of(affectedUser)))
            .containsKey(affectedUser);

        PermResult afterRevoke = permQueryEngine.query(PermQuery.forUserView(TENANT, affectedUser));
        assertThat(afterRevoke.allowed()).isTrue();
        assertThat(afterRevoke.allEntries())
            .extracting(RolePermEntry::permissionId)
            .containsExactlyInAnyOrder(keptPermId)
            .doesNotContain(revokedPermId);
    }

    @Test
    @DisplayName("端到端场景 1（成功提交）：权限事实落库 + 恰好一条 §6.8 聚合 permission_change_log + 缓存失效")
    void applyGrantPlanSuccessShouldPersistFactsAndSingleAggregateChangeLog() throws Exception {
        Long operatorSubject = insertAbstractUserWithId(920011L, "特征测试-成功提交操作者");
        Long operatorRole = insertAbstractRole("op-role-920111", "特征测试-成功提交操作者角色");
        insertUserRole(operatorSubject, "ROLE", operatorRole);
        // 操作者持 ROLE:MANAGE scopeAll 可转授 + SERVICE:VIEW scopeAll 可转授（update 委托需要）
        jdbc.update("UPDATE role_resource_permission SET can_grant = true WHERE id = ?",
            insertRolePerm(operatorRole, RESOURCE_TYPE_ROLE, ROLE_MANAGE_BIT, true, null));
        jdbc.update("UPDATE role_resource_permission SET can_grant = true WHERE id = ?",
            insertRolePerm(operatorRole, RESOURCE_TYPE_SERVICE, SERVICE_VIEW_BIT, true, null));

        Long affectedUser = insertAbstractUser("920012", "特征测试-成功提交受影响用户");
        Long targetRole = insertAbstractRole("target-role-920211", "特征测试-成功提交目标角色");
        insertUserRole(affectedUser, "ROLE", targetRole);
        // update 路径按严格口径解析权限业务键，被更新行必须引用真实资源实体
        jdbc.update("INSERT INTO resource_entity (id, tenant_id, resource_type, code, code_type, name, status) "
            + "VALUES (929011, ?, ?, 'svc-929011', 'default', '特征测试-成功提交资源', 1)", TENANT, RESOURCE_TYPE_SERVICE);
        Long updatedPermId = insertRolePerm(targetRole, RESOURCE_TYPE_SERVICE, SERVICE_VIEW_BIT, false, 929011L);

        // 预热缓存（afterCommit 失效断言用）
        assertThat(subjectDomainService.resolveEffectiveRoles(TENANT, affectedUser))
            .containsExactlyInAnyOrder(targetRole);
        assertThat(cacheService.getBatch(PermCacheCatalog.EFFECTIVE_ROLES, TENANT, Set.of(affectedUser)))
            .containsKey(affectedUser);

        AccessRequestContext.bind(RequestContext.user(TENANT, 920011L));
        try {
            permissionGrantAppService.applyGrantPlan(TENANT, new ApplyGrantPlanReq(
                null, "BASIC_ROLE", "target-role-920211",
                new ApplyGrantPlanReq.GrantPlan(
                    List.of(new ApplyGrantPlanReq.CreateItem(
                        new ApplyGrantPlanReq.GrantRecordKey(
                            "ROLE", null, null, "MANAGE", ScopeMode.ALL, null, false),
                        null, List.of())),
                    List.of(new ApplyGrantPlanReq.UpdateItem(updatedPermId, Boolean.TRUE, null)),
                    List.of())));
        } finally {
            AccessRequestContext.clear();
        }

        // 权限事实落库：create（ROLE ALL MANAGE）+ update（canGrant=true）
        Integer createdCount = jdbc.queryForObject(
            "SELECT count(*) FROM role_resource_permission WHERE tenant_id = ? AND abstract_role_id = ? "
                + "AND delete_flag = 0 AND resource_type = ? AND scope_all = true",
            Integer.class, TENANT, targetRole, RESOURCE_TYPE_ROLE);
        assertThat(createdCount).isEqualTo(1);
        Boolean updatedCanGrant = jdbc.queryForObject(
            "SELECT can_grant FROM role_resource_permission WHERE id = ?", Boolean.class, updatedPermId);
        assertThat(updatedCanGrant).isTrue();

        // 恰好一条 §6.8 聚合 permission_change_log（eventType + items[].permission/role）
        Integer logCount = jdbc.queryForObject(
            "SELECT count(*) FROM permission_change_log WHERE tenant_id = ? AND entity_id = ?",
            Integer.class, TENANT, targetRole);
        assertThat(logCount).isEqualTo(1);
        String diff = jdbc.queryForObject(
            "SELECT diff_snapshot FROM permission_change_log WHERE tenant_id = ? AND entity_id = ?",
            String.class, TENANT, targetRole);
        com.fasterxml.jackson.databind.JsonNode snapshot =
            new com.fasterxml.jackson.databind.ObjectMapper().readTree(diff);
        assertThat(snapshot.path("eventType").asText()).isEqualTo("ROLE_PERMISSION_CHANGE");
        assertThat(snapshot.path("items").size()).isEqualTo(2);
        assertThat(snapshot.path("items")).anyMatch(item ->
            "ADD".equals(item.path("changeType").asText())
                && "ROLE".equals(item.path("permission").path("resourceTypeCode").asText())
                && "ALL".equals(item.path("permission").path("scopeMode").asText()));
        assertThat(snapshot.path("items")).anyMatch(item ->
            "UPDATE".equals(item.path("changeType").asText())
                && "target-role-920211".equals(item.path("role").path("roleExternalId").asText()));

        // afterCommit 缓存失效
        assertThat(cacheService.getBatch(PermCacheCatalog.EFFECTIVE_ROLES, TENANT, Set.of(affectedUser)))
            .doesNotContainKey(affectedUser);
        assertThat(cacheService.getBatch(PermCacheCatalog.ROLE_PERM_SNAPSHOT, TENANT, Set.of(targetRole)))
            .doesNotContainKey(targetRole);
    }

    @Test
    @DisplayName("端到端场景 2（执行失败）：已完成写入后异常 → 事实与变更日志全部回滚 + 不触发缓存失效")
    void applyGrantPlanFailureShouldRollbackFactsAndSkipChangeLogAndInvalidation() {
        Long operatorSubject = insertAbstractUserWithId(920021L, "特征测试-失败回滚操作者");
        Long operatorRole = insertAbstractRole("op-role-920121", "特征测试-失败回滚操作者角色");
        insertUserRole(operatorSubject, "ROLE", operatorRole);
        jdbc.update("UPDATE role_resource_permission SET can_grant = true WHERE id = ?",
            insertRolePerm(operatorRole, RESOURCE_TYPE_ROLE, ROLE_MANAGE_BIT, true, null));
        // 操作者补持触发位操作的可转授权限（否则预检 20040 先拒绝，到不了写入阶段）
        jdbc.update("INSERT INTO operation_permission "
            + "(tenant_id, resource_type, code, name, binary_bit, inherit_mask, delete_flag) "
            + "VALUES (?, ?, 'TRIGGER_OP', '触发位', 16384, 0, 0)", TENANT, RESOURCE_TYPE_ROLE);
        jdbc.update("UPDATE role_resource_permission SET can_grant = true WHERE id = ?",
            insertRolePerm(operatorRole, RESOURCE_TYPE_ROLE, 16384L, true, null));

        // 故障注入（在操作者权限种子落库之后建触发器，避免误伤种子自身）：
        // granted_bits=16384 的插入触发 DB 异常（第二笔 create 落库时炸）
        jdbc.update("CREATE OR REPLACE FUNCTION golden_fail_insert() RETURNS trigger AS $$ "
            + "BEGIN RAISE EXCEPTION 'injected failure'; END $$ LANGUAGE plpgsql");
        jdbc.update("CREATE TRIGGER trg_golden_fail BEFORE INSERT ON role_resource_permission "
            + "FOR EACH ROW WHEN (NEW.granted_bits = 16384) EXECUTE FUNCTION golden_fail_insert()");
        try {
            Long affectedUser = insertAbstractUser("920022", "特征测试-失败回滚受影响用户");
            Long targetRole = insertAbstractRole("target-role-920221", "特征测试-失败回滚目标角色");
            insertUserRole(affectedUser, "ROLE", targetRole);
            Integer rowsBefore = jdbc.queryForObject(
                "SELECT count(*) FROM role_resource_permission WHERE tenant_id = ? AND abstract_role_id = ? "
                    + "AND delete_flag = 0", Integer.class, TENANT, targetRole);

            // 预热缓存（失败不得失效）
            assertThat(subjectDomainService.resolveEffectiveRoles(TENANT, affectedUser))
                .containsExactlyInAnyOrder(targetRole);
            assertThat(cacheService.getBatch(PermCacheCatalog.EFFECTIVE_ROLES, TENANT, Set.of(affectedUser)))
                .containsKey(affectedUser);

            AccessRequestContext.bind(RequestContext.user(TENANT, 920021L));
            try {
                Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() ->
                    permissionGrantAppService.applyGrantPlan(TENANT, new ApplyGrantPlanReq(
                        null, "BASIC_ROLE", "target-role-920221",
                        new ApplyGrantPlanReq.GrantPlan(List.of(
                            new ApplyGrantPlanReq.CreateItem(new ApplyGrantPlanReq.GrantRecordKey(
                                "ROLE", null, null, "MANAGE", ScopeMode.ALL, null, false), null, List.of()),
                            new ApplyGrantPlanReq.CreateItem(new ApplyGrantPlanReq.GrantRecordKey(
                                "ROLE", null, null, "TRIGGER_OP", ScopeMode.ALL, null, false), null, List.of())),
                            List.of(), List.of()))));
                // 锁定故障来自注入的触发器（第二笔 create 落库时炸），而非预检拒绝
                Throwable root = thrown;
                while (root.getCause() != null) {
                    root = root.getCause();
                }
                assertThat(root.getMessage()).contains("injected failure");
            } finally {
                AccessRequestContext.clear();
            }

            // 事实表回滚：第一笔 create 未残留（行数与调用前一致）
            Integer rowsAfter = jdbc.queryForObject(
                "SELECT count(*) FROM role_resource_permission WHERE tenant_id = ? AND abstract_role_id = ? "
                    + "AND delete_flag = 0", Integer.class, TENANT, targetRole);
            assertThat(rowsAfter).isEqualTo(rowsBefore);
            // 审计表无部分状态
            Integer logCount = jdbc.queryForObject(
                "SELECT count(*) FROM permission_change_log WHERE tenant_id = ? AND entity_id = ?",
                Integer.class, TENANT, targetRole);
            assertThat(logCount).isZero();
            // 失败不触发缓存失效（仍命中预热值）
            assertThat(cacheService.getBatch(PermCacheCatalog.EFFECTIVE_ROLES, TENANT, Set.of(affectedUser)))
                .containsKey(affectedUser);
        } finally {
            jdbc.update("DROP TRIGGER IF EXISTS trg_golden_fail ON role_resource_permission");
            jdbc.update("DROP FUNCTION IF EXISTS golden_fail_insert()");
        }
    }

    // ===== 数据装配 =====

    private Long insertAbstractUserWithId(Long id, String name) {
        return jdbc.queryForObject(
            "INSERT INTO abstract_user (id, tenant_id, user_type, external_id, name, enabled, extra, owner_service_code) "
                + "VALUES (?, ?, ?, ?, ?, true, '{}', NULL) RETURNING id",
            Long.class, id, TENANT, USER_TYPE_ADMIN, String.valueOf(id), name);
    }

    private Long insertAbstractUser(String externalId, String name) {
        return jdbc.queryForObject(
            "INSERT INTO abstract_user (tenant_id, user_type, external_id, name, enabled, extra, owner_service_code) "
                + "VALUES (?, ?, ?, ?, true, '{}', NULL) RETURNING id",
            Long.class, TENANT, USER_TYPE_ADMIN, externalId, name);
    }

    private Long insertAbstractRole(String externalId, String name) {
        return jdbc.queryForObject(
            "INSERT INTO abstract_role (tenant_id, role_type, external_id, name, status, extra) "
                + "VALUES (?, ?, ?, ?, 1, '{}') RETURNING id",
            Long.class, TENANT, ROLE_TYPE_BASIC, externalId, name);
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
