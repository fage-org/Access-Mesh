package cn.ac.fage.accessmesh.access.bootstrap;

import cn.ac.fage.accessmesh.access.admin.dto.auth.LoginReq;
import cn.ac.fage.accessmesh.access.admin.dto.auth.LoginResp;
import cn.ac.fage.accessmesh.access.admin.service.AuthService;
import cn.ac.fage.accessmesh.access.application.bootstrap.AccessBootstrapInitializer;
import cn.ac.fage.accessmesh.access.application.bootstrap.BootstrapGraphDefinition;
import cn.ac.fage.accessmesh.access.permission.service.domain.BootstrapSeedWriter;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService;
import cn.dev33.satoken.secure.BCrypt;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.StringRedisTemplate;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/**
 * 空库 bootstrap 幂等三状态验收（T-ACCESS-020，真实 PostgreSQL + Redis，Testcontainers；
 * architecture §14.2/§14.3）。
 * <p>
 * 固定顺序执行：状态①单事务创建固定图（前置：写入链中段与末段两点注入故障证明整体回滚）→ 首管理员
 * 真实登录（验证码经 Redis）→ 状态②整体 no-op（预改密码哈希后重跑，绝不重置）→ 状态③冲突
 * fail-fast（绑定缺失 / 授权缺失 / 映射 serviceCode / ROLE 投影 / 主体禁用 / 主体身份漂移 /
 * API·SERVICE 资源停用 / 固定业务键被其他角色类型占用）→ 类型种子缺失显式报错。Runner 装配与
 * 密码 fail-fast 由 {@code AccessBootstrapRunnerTest} 单测覆盖；本 IT 以 enabled=false 上下文手动调
 * initializer（同一 Spring 代理，事务与 @PermissionChange 语义一致）。
 * </p>
 */
@Tag("testcontainers")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(properties = {
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    "accessmesh.sync.scheduler.enabled=false",
    "access.bootstrap.enabled=false",
    "mybatis-flex.configuration.map-underscore-to-camel-case=true",
    "logging.level.cn.ac.fage.accessmesh=WARN",
})
class AccessBootstrapPgIT {

    private static final Long TENANT = BootstrapGraphDefinition.TENANT_ID;
    private static final String BOOTSTRAP_PASSWORD = "Bootstrap-IT-2026!";
    private static final Path DDL_PATH = Path.of("..", "docs", "design", "schema", "access-service.sql");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("bootstrap_test")
        .withUsername("perm")
        .withPassword("perm");

    /** Redis 容器与客户端密码必须对齐（见 PermissionCharacterizationPgIT 同款说明） */
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
        String sql = Files.readString(DDL_PATH, StandardCharsets.UTF_8);
        try (var conn = java.sql.DriverManager.getConnection(
            postgres.getJdbcUrl() + "?stringtype=unspecified", postgres.getUsername(), postgres.getPassword());
             var st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    @Autowired
    private AccessBootstrapInitializer initializer;
    @Autowired
    private AuthService authService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private JdbcTemplate jdbc;

    /** 写入组件 spy：两个回滚用例分别在中段 insertGrants 与末段 bindUserOrg 注入故障，其余用例真实执行（用毕 reset）。 */
    @SpyBean
    private BootstrapSeedWriter seedWriter;

    @SpyBean
    private LocalProjectionDomainService localProjectionDomainService;

    @Test
    @Order(0)
    @DisplayName("事务性：创建链中段（insertGrants）注入故障 → 单事务整体回滚，全部固定图表无残留")
    void midCreationFailureRollsBackEntireGraph() {
        doThrow(new IllegalStateException("injected: insertGrants failure"))
            .when(seedWriter).insertGrants(anyLong(), anyLong(), any());
        try {
            assertThatThrownBy(() -> initializer.initialize(BOOTSTRAP_PASSWORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("injected: insertGrants failure");

            snapshotRowCounts().forEach((table, count) -> assertThat(count)
                .as("回滚后 %s 应无残留行", table).isZero());
        } finally {
            reset(seedWriter);
        }
    }

    @Test
    @Order(0)
    @DisplayName("事务性：创建链末段（默认树 user_role 投影 bindUserOrg）注入故障 → 单事务整体回滚，全部固定图表无残留")
    void tailCreationFailureRollsBackEntireGraph() {
        // 尾链注入：bindUserOrg 前已写入菜单/MENU 投影/根组织/ORG 投影/树配置/admin 直绑行——
        // 证明末段与 insertGrants 前段同处一个事务（尾段脱离事务时本用例才可能失败）
        doThrow(new IllegalStateException("injected: bindUserOrg failure"))
            .when(localProjectionDomainService).bindUserOrg(anyLong(), anyLong(), anyLong(), anyString(), any());
        try {
            assertThatThrownBy(() -> initializer.initialize(BOOTSTRAP_PASSWORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("injected: bindUserOrg failure");

            snapshotRowCounts().forEach((table, count) -> assertThat(count)
                .as("回滚后 %s 应无残留行", table).isZero());
        } finally {
            reset(localProjectionDomainService);
        }
    }

    @Test
    @Order(1)
    @DisplayName("状态①：空库单事务创建完整固定图（主体链/角色/绑定/SERVICE+API 资源/映射/授权/菜单/默认组织树，总量以本测试计数断言为准）")
    void createsFullGraphOnEmptyDatabase() {
        initializer.initialize(BOOTSTRAP_PASSWORD);

        // 首管理员同 ID 主体链（sys_user.id = abstract_user.id，external_id/code = 主体 ID 字符串化）
        Long subjectId = jdbc.queryForObject(
            "SELECT id FROM sys_user WHERE tenant_id = ? AND username = ?",
            Long.class, TENANT, BootstrapGraphDefinition.ADMIN_USERNAME);
        assertThat(subjectId).isNotNull();
        assertThat(jdbc.queryForObject(
            "SELECT external_id FROM abstract_user WHERE tenant_id = ? AND id = ?",
            String.class, TENANT, subjectId)).isEqualTo(String.valueOf(subjectId));
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM resource_entity WHERE tenant_id = ? "
                + "AND resource_type = (SELECT type_value FROM type_definition WHERE tenant_id = 1 AND type_key = 'resource_type' AND type_code = 'USER') "
                + "AND code = ? AND delete_flag = 0",
            Long.class, TENANT, String.valueOf(subjectId))).isEqualTo(1L);

        // 密码 BCrypt 哈希可校验；不强制改密（管理员自设密码）
        String passwordHash = jdbc.queryForObject(
            "SELECT password FROM sys_user WHERE id = ?", String.class, subjectId);
        assertThat(BCrypt.checkpw(BOOTSTRAP_PASSWORD, passwordHash)).isTrue();
        assertThat(jdbc.queryForObject(
            "SELECT force_reset_pwd FROM sys_user WHERE id = ?", Boolean.class, subjectId)).isFalse();

        // 管理角色（BASIC_ROLE、启用）+ 绑定
        Long roleId = jdbc.queryForObject(
            "SELECT id FROM abstract_role WHERE tenant_id = ? AND external_id = ? AND delete_flag = 0",
            Long.class, TENANT, BootstrapGraphDefinition.ADMIN_ROLE_EXTERNAL_ID);
        assertThat(roleId).isNotNull();
        assertThat(jdbc.queryForObject(
            "SELECT role_type FROM abstract_role WHERE id = ?", Integer.class, roleId))
            .isEqualTo(jdbc.queryForObject(
                "SELECT type_value FROM type_definition WHERE tenant_id = 1 AND type_key = 'role_type' AND type_code = 'BASIC_ROLE'",
                Integer.class));
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM user_role WHERE tenant_id = ? AND abstract_user_id = ? "
                + "AND target_type = 'ROLE' AND target_id = ? AND delete_flag = 0",
            Long.class, TENANT, subjectId, roleId)).isEqualTo(1L);

        // SERVICE 资源 + API 资源 + 映射（目标接口无映射；各联调任务按页消费端点
        // 在 BootstrapGraphDefinition.apiRoutes() 逐条精确注册——Gateway 未映射路径
        // fail-closed；原已在册的端点不重复注册。总量以本测试计数断言为准）
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM resource_entity WHERE tenant_id = ? "
                + "AND resource_type = (SELECT type_value FROM type_definition WHERE tenant_id = 1 AND type_key = 'resource_type' AND type_code = 'SERVICE') "
                + "AND code = 'access-service' AND delete_flag = 0", Long.class, TENANT)).isEqualTo(1L);

        List<String> expectedApiCodes = BootstrapGraphDefinition.apiRoutes().stream()
            .map(route -> BootstrapGraphDefinition.apiResourceCode(route.method(), route.path()))
            .toList();
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM resource_entity WHERE tenant_id = ? "
                + "AND resource_type = (SELECT type_value FROM type_definition WHERE tenant_id = 1 AND type_key = 'resource_type' AND type_code = 'API') "
                + "AND code IN ('" + String.join("','", expectedApiCodes) + "') AND delete_flag = 0",
            Long.class, TENANT)).isEqualTo(66L);
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM resource_api_mapping ram JOIN resource_entity re "
                + "ON ram.resource_entity_id = re.id AND re.tenant_id = ram.tenant_id "
                + "WHERE ram.tenant_id = ? AND ram.delete_flag = 0 "
                + "AND re.resource_type = (SELECT type_value FROM type_definition WHERE tenant_id = 1 AND type_key = 'resource_type' AND type_code = 'API') "
                + "AND re.code LIKE 'POST:%'",
            Long.class, TENANT)).isEqualTo(65L);
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM resource_api_mapping ram JOIN resource_entity re "
                + "ON ram.resource_entity_id = re.id AND re.tenant_id = ram.tenant_id "
                + "WHERE ram.tenant_id = ? AND ram.delete_flag = 0 AND re.code = 'POST:/admin/role/my-info'",
            Long.class, TENANT)).isEqualTo(0L);

        // 授权构成（总量以本测试计数断言为准，不在此注释维护）：业务门禁 scopeAll——
        // 各联调页读写门禁档位（ORG/USER/SYSTEM_CONFIG/RESOURCE/OPERATION 等；菜单种子挂类型走派生
        // 同样要求先持有）；含 SERVICE 四操作类型级 VIEW/MANAGE/MANAGE_API_MAPPING/SYNC_INTERFACE
        // 与 API:ACCESS 类型级+canGrant、OPERATION_LOG:VIEW、PERMISSION_CHANGE_LOG:VIEW、DOMAIN:VIEW、
        // CONFLICT_RULE 与 CONDITION 写各档、DEPENDENCY 各档
        // + 每条在册 API 路由派生一条 API:ACCESS 实例授权（各联调任务按页注册，见
        // BootstrapGraphDefinition.apiRoutes()）；canGrant=true 仅目标 API 实例与 API:ACCESS 类型级
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM role_resource_permission WHERE tenant_id = ? AND abstract_role_id = ? "
                + "AND delete_flag = 0 AND grant_source = 'MANUAL'",
            Long.class, TENANT, roleId)).isEqualTo(113L);
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM role_resource_permission WHERE tenant_id = ? AND abstract_role_id = ? "
                + "AND delete_flag = 0 AND scope_all = true",
            Long.class, TENANT, roleId)).isEqualTo(47L);
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM role_resource_permission WHERE tenant_id = ? AND abstract_role_id = ? "
                + "AND delete_flag = 0 AND can_grant = true",
            Long.class, TENANT, roleId)).isEqualTo(2L);
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM role_resource_permission p JOIN resource_entity re "
                + "ON p.resource_entity_id = re.id AND re.tenant_id = p.tenant_id "
                + "WHERE p.tenant_id = ? AND p.abstract_role_id = ? AND p.delete_flag = 0 "
                + "AND p.can_grant = true AND re.code = 'POST:/admin/role/my-info'",
            Long.class, TENANT, roleId)).isEqualTo(1L);

        // 重复执行不重复建号：admin 唯一、角色唯一
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM sys_user WHERE tenant_id = ? AND username = ? AND delete_flag = 0",
            Long.class, TENANT, BootstrapGraphDefinition.ADMIN_USERNAME)).isEqualTo(1L);

        // 菜单种子 15 行（T-FE-015）：welcome 纯展示 +「系统管理」DIR + 13 业务 MENU；
        // 资源挂接 12 行（权限条件页读取全租户开放挂纯展示）、类型级挂接 resource_code 全空；
        // 13 个业务页全部挂 /system 目录下；MENU 投影全量维护（对齐 MenuWriteAppService 终态）
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM sys_menu WHERE tenant_id = ? AND delete_flag = 0",
            Long.class, TENANT)).isEqualTo(15L);
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM sys_menu WHERE tenant_id = ? AND delete_flag = 0 AND menu_type = 'DIR'",
            Long.class, TENANT)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM sys_menu WHERE tenant_id = ? AND delete_flag = 0 "
                + "AND resource_type IS NOT NULL", Long.class, TENANT)).isEqualTo(12L);
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM sys_menu WHERE tenant_id = ? AND delete_flag = 0 "
                + "AND resource_type IS NOT NULL AND resource_code IS NOT NULL",
            Long.class, TENANT)).isZero();
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM sys_menu WHERE tenant_id = ? AND delete_flag = 0 "
                + "AND parent_id = (SELECT id FROM sys_menu WHERE tenant_id = ? AND path = '/system' "
                + "AND delete_flag = 0)",
            Long.class, TENANT, TENANT)).isEqualTo(13L);
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM resource_entity WHERE tenant_id = ? AND delete_flag = 0 "
                + "AND resource_type = (SELECT type_value FROM type_definition "
                + "WHERE tenant_id = 1 AND type_key = 'resource_type' AND type_code = 'MENU')",
            Long.class, TENANT)).isEqualTo(15L);

        // 默认组织树种子（T-FE-015）：根组织（稳定业务键 root）+ 默认树配置 + admin 直绑根组织
        // （isPrimary）+ ORG 投影 + user_role 投影——/user/page 与 member-candidates 为默认树
        // 身份目录视图，无默认树配置则用户列表恒空（空库首用死锁，故入固定图）
        Long rootOrgId = jdbc.queryForObject(
            "SELECT id FROM sys_org WHERE tenant_id = ? AND code = 'root' AND delete_flag = 0",
            Long.class, TENANT);
        assertThat(rootOrgId).isNotNull();
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM sys_org_tree_config WHERE tenant_id = ? AND is_default = true "
                + "AND root_org_id = ? AND tree_type = 'ORG' AND delete_flag = 0",
            Long.class, TENANT, rootOrgId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM sys_user_org WHERE tenant_id = ? AND user_id = ? AND org_id = ? "
                + "AND is_primary = true AND delete_flag = 0",
            Long.class, TENANT, subjectId, rootOrgId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM resource_entity WHERE tenant_id = ? AND delete_flag = 0 "
                + "AND resource_type = (SELECT type_value FROM type_definition "
                + "WHERE tenant_id = 1 AND type_key = 'resource_type' AND type_code = 'ORG') "
                + "AND code = ?",
            Long.class, TENANT, String.valueOf(rootOrgId))).isEqualTo(1L);
        // 组织型 user_role 投影：admin 直绑根组织产生指向 ORG 投影角色的 user_role 行
        // （bindUserOrg 轨道；/user/page 身份目录视图的数据底座）
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM user_role ur JOIN abstract_role ar "
                + "ON ur.target_id = ar.id AND ar.tenant_id = ur.tenant_id "
                + "JOIN abstract_user au ON ur.abstract_user_id = au.id AND au.tenant_id = ur.tenant_id "
                + "WHERE ur.tenant_id = ? AND ur.delete_flag = 0 AND ur.target_type = 'ROLE' "
                + "AND au.external_id = ? AND ar.role_type = (SELECT type_value FROM type_definition "
                + "WHERE tenant_id = 1 AND type_key = 'role_type' AND type_code = 'ORG') "
                + "AND ar.external_id = ?",
            Long.class, TENANT, String.valueOf(subjectId), String.valueOf(rootOrgId))).isEqualTo(1L);
    }

    @Test
    @Order(2)
    @DisplayName("登录验证：bootstrap 首管理员经真实验证码 + clientId=admin-web 登录成功")
    void bootstrapAdminCanLoginWithRealCaptcha() {
        var captcha = authService.generateCaptcha();
        String captchaCode = stringRedisTemplate.opsForValue().get("captcha:" + captcha.captchaId());
        assertThat(captchaCode).isNotBlank();

        LoginResp resp = authService.login(new LoginReq(
            String.valueOf(TENANT), BootstrapGraphDefinition.ADMIN_USERNAME, BOOTSTRAP_PASSWORD,
            captcha.captchaId(), captchaCode, "admin-web"));
        assertThat(resp.accessToken()).isNotBlank();
        assertThat(resp.forceResetPwd()).isFalse();
    }

    @Test
    @Order(3)
    @DisplayName("状态②：固定图完整匹配 → 整体 no-op，绝不重置密码")
    void completeGraphIsNoOpAndNeverResetsPassword() {
        // 预改密码哈希为固定值：no-op 后必须原样保留（绝不重置）
        jdbc.update("UPDATE sys_user SET password = 'tampered-hash' WHERE username = 'admin'");
        Map<String, Long> before = snapshotRowCounts();

        initializer.initialize(BOOTSTRAP_PASSWORD);

        assertThat(jdbc.queryForObject(
            "SELECT password FROM sys_user WHERE username = 'admin'", String.class))
            .isEqualTo("tampered-hash");
        assertThat(snapshotRowCounts()).isEqualTo(before);
    }

    @Test
    @Order(4)
    @DisplayName("状态③：绑定缺失 → fail-fast 并报告关联缺失")
    void missingBindingFailsFast() {
        jdbc.update("UPDATE user_role SET delete_flag = id, deleted_at = now() WHERE tenant_id = ? "
                + "AND target_type = 'ROLE' "
                + "AND target_id = (SELECT id FROM abstract_role WHERE tenant_id = ? AND external_id = ?)",
            TENANT, TENANT, BootstrapGraphDefinition.ADMIN_ROLE_EXTERNAL_ID);

        assertThatThrownBy(() -> initializer.initialize(BOOTSTRAP_PASSWORD))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("user_role 直绑缺失");
    }

    @Test
    @Order(5)
    @DisplayName("状态③：授权缺失 → fail-fast 并报告授权缺失")
    void missingGrantFailsFast() {
        jdbc.update("UPDATE role_resource_permission SET delete_flag = id, deleted_at = now() WHERE tenant_id = ? "
                + "AND abstract_role_id = (SELECT id FROM abstract_role WHERE tenant_id = ? AND external_id = ?) "
                + "AND scope_all = true",
            TENANT, TENANT, BootstrapGraphDefinition.ADMIN_ROLE_EXTERNAL_ID);

        assertThatThrownBy(() -> initializer.initialize(BOOTSTRAP_PASSWORD))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("授权缺失");
    }

    @Test
    @Order(6)
    @DisplayName("状态③：映射 serviceCode 不匹配（其他服务的同路径映射不能冒充）→ fail-fast 报映射缺失")
    void wrongServiceCodeMappingFailsFast() {
        jdbc.update("UPDATE resource_api_mapping SET service_code = 'example-service' WHERE tenant_id = ? "
            + "AND service_code = 'access-service'", TENANT);

        assertThatThrownBy(() -> initializer.initialize(BOOTSTRAP_PASSWORD))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("API 映射缺失或未启用");
    }

    @Test
    @Order(7)
    @DisplayName("状态③：管理角色 ROLE 资源投影缺失 → fail-fast 报投影缺失")
    void missingRoleProjectionFailsFast() {
        jdbc.update("UPDATE resource_entity SET delete_flag = id, deleted_at = now() WHERE tenant_id = ? "
                + "AND resource_type = (SELECT type_value FROM type_definition WHERE tenant_id = 1 AND type_key = 'resource_type' AND type_code = 'ROLE') "
                + "AND code = (SELECT id::text FROM abstract_role WHERE tenant_id = ? AND external_id = ?)",
            TENANT, TENANT, BootstrapGraphDefinition.ADMIN_ROLE_EXTERNAL_ID);

        assertThatThrownBy(() -> initializer.initialize(BOOTSTRAP_PASSWORD))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("resource_entity(ROLE) 投影缺失");
    }

    @Test
    @Order(8)
    @DisplayName("状态③：admin 主体被禁用（引擎有效角色置空）→ fail-fast 报主体禁用")
    void disabledAdminSubjectFailsFast() {
        jdbc.update("UPDATE abstract_user SET enabled = false WHERE tenant_id = ? "
                + "AND id = (SELECT id FROM sys_user WHERE tenant_id = ? AND username = ?)",
            TENANT, TENANT, BootstrapGraphDefinition.ADMIN_USERNAME);

        assertThatThrownBy(() -> initializer.initialize(BOOTSTRAP_PASSWORD))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("已禁用");
    }

    @Test
    @Order(9)
    @DisplayName("状态③：admin 主体身份漂移（user_type / external_id 背离固定图身份键）→ fail-fast")
    void subjectIdentityDriftFailsFast() {
        jdbc.update("UPDATE abstract_user SET user_type = user_type + 1 WHERE tenant_id = ? "
                + "AND id = (SELECT id FROM sys_user WHERE tenant_id = ? AND username = ?)",
            TENANT, TENANT, BootstrapGraphDefinition.ADMIN_USERNAME);
        assertThatThrownBy(() -> initializer.initialize(BOOTSTRAP_PASSWORD))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("abstract_user.user_type");

        // 还原 user_type、漂移 external_id：两条身份键独立报告
        jdbc.update("UPDATE abstract_user SET user_type = user_type - 1, external_id = 'drifted' "
                + "WHERE tenant_id = ? AND id = (SELECT id FROM sys_user WHERE tenant_id = ? AND username = ?)",
            TENANT, TENANT, BootstrapGraphDefinition.ADMIN_USERNAME);
        assertThatThrownBy(() -> initializer.initialize(BOOTSTRAP_PASSWORD))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("abstract_user.external_id");
    }

    @Test
    @Order(10)
    @DisplayName("状态③：API / SERVICE 资源停用（status=0）→ fail-fast 报资源停用")
    void disabledApiOrServiceResourceFailsFast() {
        jdbc.update("UPDATE resource_entity SET status = 0 WHERE tenant_id = ? "
                + "AND resource_type = (SELECT type_value FROM type_definition WHERE tenant_id = 1 AND type_key = 'resource_type' AND type_code = 'API') "
                + "AND code = 'POST:/admin/user/create'",
            TENANT);
        assertThatThrownBy(() -> initializer.initialize(BOOTSTRAP_PASSWORD))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("API 资源已停用");

        jdbc.update("UPDATE resource_entity SET status = 0 WHERE tenant_id = ? "
                + "AND resource_type = (SELECT type_value FROM type_definition WHERE tenant_id = 1 AND type_key = 'resource_type' AND type_code = 'SERVICE') "
                + "AND code = 'access-service'",
            TENANT);
        assertThatThrownBy(() -> initializer.initialize(BOOTSTRAP_PASSWORD))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SERVICE 资源 'access-service' 已停用");
    }

    @Test
    @Order(11)
    @DisplayName("状态③：固定业务键被其他角色类型占用 → fail-fast 报告占用")
    void occupiedBusinessKeyFailsFast() {
        jdbc.update("UPDATE abstract_role SET role_type = 1 WHERE tenant_id = ? AND external_id = ?",
            TENANT, BootstrapGraphDefinition.ADMIN_ROLE_EXTERNAL_ID);

        assertThatThrownBy(() -> initializer.initialize(BOOTSTRAP_PASSWORD))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("被其他角色类型占用");
    }

    @Test
    @Order(12)
    @DisplayName("状态③：仅 SERVICE 资源残留（其余固定图清空）→ 报\"固定图部分存在\"而非撞唯一约束")
    void partialGraphReportsConflictInsteadOfConstraintViolation() {
        Long adminSubjectId = jdbc.queryForObject(
            "SELECT id FROM sys_user WHERE tenant_id = ? AND username = ?",
            Long.class, TENANT, BootstrapGraphDefinition.ADMIN_USERNAME);

        // 软删固定图全部对象，仅保留 SERVICE 资源
        jdbc.update("UPDATE role_resource_permission SET delete_flag = id, deleted_at = now() WHERE tenant_id = ? "
            + "AND abstract_role_id = (SELECT id FROM abstract_role WHERE tenant_id = ? AND external_id = ?)",
            TENANT, TENANT, BootstrapGraphDefinition.ADMIN_ROLE_EXTERNAL_ID);
        jdbc.update("UPDATE resource_api_mapping SET delete_flag = id, deleted_at = now() WHERE tenant_id = ?", TENANT);
        jdbc.update("UPDATE resource_entity SET delete_flag = id, deleted_at = now() WHERE tenant_id = ? "
                + "AND resource_type = (SELECT type_value FROM type_definition WHERE tenant_id = 1 AND type_key = 'resource_type' AND type_code = 'API')",
            TENANT);
        jdbc.update("UPDATE resource_entity SET delete_flag = id, deleted_at = now() WHERE tenant_id = ? "
                + "AND resource_type = (SELECT type_value FROM type_definition WHERE tenant_id = 1 AND type_key = 'resource_type' AND type_code = 'USER') "
                + "AND code = ?",
            TENANT, String.valueOf(adminSubjectId));
        jdbc.update("UPDATE abstract_role SET delete_flag = id, deleted_at = now() WHERE tenant_id = ? AND external_id = ?",
            TENANT, BootstrapGraphDefinition.ADMIN_ROLE_EXTERNAL_ID);
        jdbc.update("UPDATE abstract_user SET delete_flag = id, deleted_at = now() WHERE tenant_id = ? AND id = ?",
            TENANT, adminSubjectId);
        jdbc.update("UPDATE sys_user SET delete_flag = id, deleted_at = now() WHERE tenant_id = ? AND username = ?",
            TENANT, BootstrapGraphDefinition.ADMIN_USERNAME);

        assertThatThrownBy(() -> initializer.initialize(BOOTSTRAP_PASSWORD))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("固定图部分存在")
            .hasMessageContaining("SERVICE资源=true");
    }

    @Test
    @Order(13)
    @DisplayName("类型种子缺失（DDL 未完整执行）→ 显式 fail-fast 指向权威 DDL")
    void missingTypeSeedFailsFastWithDdlHint() {
        jdbc.update("DELETE FROM type_definition WHERE tenant_id = 1 AND type_key = 'resource_type' "
            + "AND type_code = 'API'");

        assertThatThrownBy(() -> initializer.initialize(BOOTSTRAP_PASSWORD))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("type_definition 种子缺失")
            .hasMessageContaining("access-service.sql");
    }

    /** 固定图相关表的行数快照（no-op 前后对比；user_role 已在 Order(4) 破坏，快照自洽即可）。 */
    private Map<String, Long> snapshotRowCounts() {
        Map<String, Long> counts = new java.util.HashMap<>();
        counts.put("sys_user", jdbc.queryForObject(
            "SELECT count(*) FROM sys_user WHERE tenant_id = ? AND delete_flag = 0", Long.class, TENANT));
        counts.put("abstract_user", jdbc.queryForObject(
            "SELECT count(*) FROM abstract_user WHERE tenant_id = ? AND delete_flag = 0", Long.class, TENANT));
        counts.put("abstract_role", jdbc.queryForObject(
            "SELECT count(*) FROM abstract_role WHERE tenant_id = ? AND delete_flag = 0", Long.class, TENANT));
        counts.put("user_role", jdbc.queryForObject(
            "SELECT count(*) FROM user_role WHERE tenant_id = ? AND delete_flag = 0", Long.class, TENANT));
        counts.put("resource_entity", jdbc.queryForObject(
            "SELECT count(*) FROM resource_entity WHERE tenant_id = ? AND delete_flag = 0", Long.class, TENANT));
        counts.put("resource_api_mapping", jdbc.queryForObject(
            "SELECT count(*) FROM resource_api_mapping WHERE tenant_id = ? AND delete_flag = 0", Long.class, TENANT));
        counts.put("role_resource_permission", jdbc.queryForObject(
            "SELECT count(*) FROM role_resource_permission WHERE tenant_id = ? AND delete_flag = 0", Long.class, TENANT));
        counts.put("sys_menu", jdbc.queryForObject(
            "SELECT count(*) FROM sys_menu WHERE tenant_id = ? AND delete_flag = 0", Long.class, TENANT));
        counts.put("sys_org", jdbc.queryForObject(
            "SELECT count(*) FROM sys_org WHERE tenant_id = ? AND delete_flag = 0", Long.class, TENANT));
        counts.put("sys_org_tree_config", jdbc.queryForObject(
            "SELECT count(*) FROM sys_org_tree_config WHERE tenant_id = ? AND delete_flag = 0", Long.class, TENANT));
        counts.put("sys_user_org", jdbc.queryForObject(
            "SELECT count(*) FROM sys_user_org WHERE tenant_id = ? AND delete_flag = 0", Long.class, TENANT));
        return counts;
    }
}
