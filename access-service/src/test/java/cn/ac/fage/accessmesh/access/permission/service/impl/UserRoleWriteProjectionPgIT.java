package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.permission.dto.req.RoleCreateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserCreateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.RoleResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.UserResp;
import cn.ac.fage.accessmesh.access.permission.service.RoleManageAppService;
import cn.ac.fage.accessmesh.access.permission.service.UserManageAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.common.exception.SystemException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

/**
 * T-ACCESS-019 验收：USER/ROLE 真实管理写路径产出 resource_entity 投影，
 * 实例门禁按业务编码（subjectId/roleId，architecture §12.3）经生产投影命中/拒绝，
 * 且投影失败时管理事实整体回滚（真实 PostgreSQL + Redis，Testcontainers）。
 * <p>
 * 与 {@code InstanceGateBusinessCodePgIT}（T-PERM-042，自装配投影 fixtures 只验证引擎语义）
 * 互补：本类经 {@code RoleManageAppService}/{@code UserManageAppService} 生产写路径产生投影，
 * 补上实例授权的写侧闭环。装配策略——创建者（scopeAll CREATE）与管理员（实例 MANAGE，
 * 在全部事实/投影就绪后装配，先于其首次引擎调用）分离，规避中间改授权导致的快照缓存陈旧。
 * Docker 不可用时由 Testcontainers 自动跳过（与既有 PG 测试一致）。
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
class UserRoleWriteProjectionPgIT {

    private static final Long TENANT = 1L;
    private static final Path DDL_PATH = Path.of("..", "docs", "design", "schema", "access-service.sql");

    /** type_definition 种子：user_type/LOCAL_USER=3、USER=1；role_type/BASIC_ROLE=6 */
    private static final int USER_TYPE_LOCAL = 3;
    private static final int USER_TYPE_EXTERNAL = 1;
    private static final int ROLE_TYPE_BASIC = 6;
    /** resource_type 种子：USER=6、ROLE=5；CRUD 预置 CREATE=1，ROLE/USER:MANAGE=16 */
    private static final int RESOURCE_TYPE_USER = 6;
    private static final int RESOURCE_TYPE_ROLE = 5;
    private static final long CREATE_BIT = 1L;
    private static final long MANAGE_BIT = 16L;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("user_role_projection_test")
        .withUsername("perm")
        .withPassword("perm");

    /** Redis 容器与客户端密码必须对齐（见 UserWriteAppServiceFaultInjectionIT 同款说明） */
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
        // 原样执行权威 DDL + 种子数据（type_definition/operation_permission 等）
        String sql = Files.readString(DDL_PATH, StandardCharsets.UTF_8);
        try (var conn = java.sql.DriverManager.getConnection(
            postgres.getJdbcUrl() + "?stringtype=unspecified", postgres.getUsername(), postgres.getPassword());
             var st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    @Autowired
    private RoleManageAppService roleManageAppService;
    @Autowired
    private UserManageAppService userManageAppService;
    @Autowired
    private PermQueryEngine permQueryEngine;
    @Autowired
    private JdbcTemplate jdbc;

    /** 投影层 spy：回滚用例仅指定方法注入故障，其余真实（成功场景全链路落库）。 */
    @SpyBean
    private LocalProjectionDomainService localProjectionDomainService;

    @AfterEach
    void tearDown() {
        AccessRequestContext.clear();
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("ROLE 写路径闭环：createRole 产出投影(code=roleId, owner=access-service)，实例 MANAGE 经生产投影命中，updateRole 镜像 status，deleteRoles 软删投影")
    void roleWritePathShouldProjectAndCloseInstanceGate() {
        Long creator = insertSubject("t019-op-create-role", "角色创建者");
        Long creatorRole = insertBasicRole("t019-holder-create-role", "创建者角色");
        insertUserRole(creator, creatorRole);
        insertScopeAllRolePerm(creatorRole, RESOURCE_TYPE_ROLE, CREATE_BIT);

        bindOperator(creator);
        RoleResp granted = roleManageAppService.createRole(
            TENANT, new RoleCreateReq(null, "BASIC_ROLE", "t019-ext-granted", "被授权角色", null, null), creator);
        RoleResp ungranted = roleManageAppService.createRole(
            TENANT, new RoleCreateReq(null, "BASIC_ROLE", "t019-ext-ungranted", "未授权角色", null, null), creator);

        // 写路径产出的投影：code=roleId、owner=access-service、默认启用
        Map<String, Object> grantedProjection = resourceRow(RESOURCE_TYPE_ROLE, String.valueOf(granted.id()));
        assertThat(grantedProjection.get("owner_service_code")).isEqualTo("access-service");
        assertThat(((Number) grantedProjection.get("status")).intValue()).isEqualTo(1);
        assertThat(((Number) grantedProjection.get("delete_flag")).longValue()).isZero();

        // 管理员在事实/投影就绪后装配（实例 MANAGE 只挂在被授权角色投影上），先于其首次引擎调用
        Long manager = insertSubject("t019-op-manage-role", "角色管理员");
        Long managerRole = insertBasicRole("t019-holder-manage-role", "管理员角色");
        insertUserRole(manager, managerRole);
        insertInstanceRolePerm(managerRole, RESOURCE_TYPE_ROLE, MANAGE_BIT,
            ((Number) grantedProjection.get("id")).longValue());

        bindOperator(manager);
        assertThat(permQueryEngine.hasPermissionByCode(
            TENANT, manager, "ROLE", String.valueOf(granted.id()), "MANAGE")).isTrue();
        assertThat(permQueryEngine.hasPermissionByCode(
            TENANT, manager, "ROLE", String.valueOf(ungranted.id()), "MANAGE")).isFalse();

        // updateRole 实例门禁经生产投影命中（非 scopeAll），status 镜像到投影
        roleManageAppService.updateRole(TENANT, granted.id(), "被授权角色-禁用", 0, null, null, manager);
        assertThat(((Number) resourceRow(RESOURCE_TYPE_ROLE, String.valueOf(granted.id()))
            .get("status")).intValue()).isZero();

        // deleteRoles 软删角色与投影；被删编码经引擎 fail-closed 拒绝
        roleManageAppService.deleteRoles(TENANT, List.of(granted.id()), manager);
        assertThat(((Number) resourceRow(RESOURCE_TYPE_ROLE, String.valueOf(granted.id()))
            .get("delete_flag")).longValue()).isNotZero();
        assertThat(permQueryEngine.hasPermissionByCode(
            TENANT, manager, "ROLE", String.valueOf(granted.id()), "MANAGE")).isFalse();
    }

    @Test
    @DisplayName("USER 写路径闭环：createUser 产出投影(code=subjectId)，实例 MANAGE 经生产投影命中，updateUser 升实例级门禁，deleteUsers 软删投影")
    void userWritePathShouldProjectAndCloseInstanceGate() {
        Long creator = insertSubject("t019-op-create-user", "用户创建者");
        Long creatorRole = insertBasicRole("t019-holder-create-user", "创建者角色");
        insertUserRole(creator, creatorRole);
        insertScopeAllRolePerm(creatorRole, RESOURCE_TYPE_USER, CREATE_BIT);

        bindOperator(creator);
        UserResp target = userManageAppService.createUser(
            TENANT, new UserCreateReq("USER", "t019-ext-target", "目标用户", true, null));
        UserResp other = userManageAppService.createUser(
            TENANT, new UserCreateReq("USER", "t019-ext-other", "其他用户", true, null));

        // 写路径产出的投影：code=subjectId（abstract_user.id）、owner=access-service
        Map<String, Object> targetProjection = resourceRow(RESOURCE_TYPE_USER, String.valueOf(target.id()));
        assertThat(targetProjection.get("owner_service_code")).isEqualTo("access-service");
        assertThat(((Number) targetProjection.get("status")).intValue()).isEqualTo(1);

        Long manager = insertSubject("t019-op-manage-user", "用户管理员");
        Long managerRole = insertBasicRole("t019-holder-manage-user", "管理员角色");
        insertUserRole(manager, managerRole);
        insertInstanceRolePerm(managerRole, RESOURCE_TYPE_USER, MANAGE_BIT,
            ((Number) targetProjection.get("id")).longValue());

        bindOperator(manager);
        assertThat(permQueryEngine.hasPermissionByCode(
            TENANT, manager, "USER", String.valueOf(target.id()), "MANAGE")).isTrue();
        assertThat(permQueryEngine.hasPermissionByCode(
            TENANT, manager, "USER", String.valueOf(other.id()), "MANAGE")).isFalse();

        // updateUser 实例级门禁经生产投影命中（无 MANAGE 实例授权的其他用户被拒）
        userManageAppService.updateUser(TENANT, new UserUpdateReq(target.id(), "目标用户-改名", false, null));
        Map<String, Object> updated = resourceRow(RESOURCE_TYPE_USER, String.valueOf(target.id()));
        assertThat(updated.get("name")).isEqualTo("目标用户-改名");
        assertThat(((Number) updated.get("status")).intValue()).isZero();
        assertThatThrownBy(() -> userManageAppService.updateUser(
            TENANT, new UserUpdateReq(other.id(), "其他用户-改名", null, null)))
            .isInstanceOf(SecurityException.class);

        // deleteUsers 软删主体与投影；被删编码经引擎 fail-closed 拒绝
        userManageAppService.deleteUsers(TENANT, List.of(target.id()));
        assertThat(((Number) resourceRow(RESOURCE_TYPE_USER, String.valueOf(target.id()))
            .get("delete_flag")).longValue()).isNotZero();
        assertThat(permQueryEngine.hasPermissionByCode(
            TENANT, manager, "USER", String.valueOf(target.id()), "MANAGE")).isFalse();
    }

    @Test
    @DisplayName("ROLE 投影失败：角色事实整体回滚（abstract_role 无残留），不落 ROLE 投影与变更日志")
    void roleProjectionFailureShouldRollBackRoleFact() {
        Long creator = insertSubject("t019-op-fault-role", "角色故障注入操作者");
        Long creatorRole = insertBasicRole("t019-holder-fault-role", "故障注入角色");
        insertUserRole(creator, creatorRole);
        insertScopeAllRolePerm(creatorRole, RESOURCE_TYPE_ROLE, CREATE_BIT);
        bindOperator(creator);
        long localProjectionLogsBefore = countLocalProjectionChangeLogs();

        doThrow(new SystemException(90001, "role projection failed"))
            .when(localProjectionDomainService)
            .upsertRoleResource(anyLong(), anyLong(), anyString(), any(), any());

        assertThatThrownBy(() -> roleManageAppService.createRole(
            TENANT, new RoleCreateReq(null, "BASIC_ROLE", "t019-ext-fault-role", "故障角色", null, null), creator))
            .isInstanceOf(SystemException.class)
            .hasMessageContaining("role projection failed");

        assertThat(countRoleByExternalId("t019-ext-fault-role")).isZero();
        assertThat(countLocalProjectionChangeLogs()).isEqualTo(localProjectionLogsBefore);
    }

    @Test
    @DisplayName("USER 投影失败：主体事实整体回滚（abstract_user 无残留），不落 USER 投影")
    void userProjectionFailureShouldRollBackUserFact() {
        Long creator = insertSubject("t019-op-fault-user", "用户故障注入操作者");
        Long creatorRole = insertBasicRole("t019-holder-fault-user", "故障注入角色");
        insertUserRole(creator, creatorRole);
        insertScopeAllRolePerm(creatorRole, RESOURCE_TYPE_USER, CREATE_BIT);
        bindOperator(creator);
        long localProjectionsBefore = countLocalProjections(RESOURCE_TYPE_USER);

        doThrow(new SystemException(90001, "user projection failed"))
            .when(localProjectionDomainService)
            .upsertUserResource(anyLong(), anyLong(), anyString(), anyBoolean());

        assertThatThrownBy(() -> userManageAppService.createUser(
            TENANT, new UserCreateReq("USER", "t019-ext-fault-user", "故障用户", true, null)))
            .isInstanceOf(SystemException.class)
            .hasMessageContaining("user projection failed");

        assertThat(countSubjectByExternalId("t019-ext-fault-user")).isZero();
        assertThat(countLocalProjections(RESOURCE_TYPE_USER)).isEqualTo(localProjectionsBefore);
    }

    @Test
    @DisplayName("ROLE 父镜像：createRole 投影 parent_id 镜像角色树，moveRole 同步迁移；父投影缺失 fail-closed 回滚")
    void roleParentMirrorShouldProjectTreeAndFailClosedWhenParentProjectionMissing() {
        Long creator = insertSubject("t019-op-parent", "父镜像操作者");
        Long creatorRole = insertBasicRole("t019-holder-parent", "父镜像角色");
        insertUserRole(creator, creatorRole);
        // CREATE + MANAGE（scopeAll 各一行——ck_role_resource_permission_manual_single_operation
        // 限制 MANUAL 来源单 bit；移动门禁经 scopeAll，实例门禁命中已由首用例覆盖）
        insertScopeAllRolePerm(creatorRole, RESOURCE_TYPE_ROLE, CREATE_BIT);
        insertScopeAllRolePerm(creatorRole, RESOURCE_TYPE_ROLE, MANAGE_BIT);
        bindOperator(creator);

        RoleResp parentA = roleManageAppService.createRole(
            TENANT, new RoleCreateReq(null, "BASIC_ROLE", "t019-ext-parent-a", "父角色A", null, null), creator);
        RoleResp parentB = roleManageAppService.createRole(
            TENANT, new RoleCreateReq(null, "BASIC_ROLE", "t019-ext-parent-b", "父角色B", null, null), creator);
        RoleResp child = roleManageAppService.createRole(
            TENANT, new RoleCreateReq(parentA.id(), "BASIC_ROLE", "t019-ext-child", "子角色", null, null), creator);

        Long parentAProjectionId = ((Number) resourceRow(RESOURCE_TYPE_ROLE, String.valueOf(parentA.id())).get("id")).longValue();
        Long parentBProjectionId = ((Number) resourceRow(RESOURCE_TYPE_ROLE, String.valueOf(parentB.id())).get("id")).longValue();
        // 创建即镜像父节点（决策 1）
        assertThat(((Number) resourceRow(RESOURCE_TYPE_ROLE, String.valueOf(child.id()))
            .get("parent_id")).longValue()).isEqualTo(parentAProjectionId);

        // moveRole 同步迁移投影父节点（旧父链成员事务内预计算失效，评审 P1-3）
        roleManageAppService.moveRole(TENANT, child.id(), parentB.id(), creator);
        assertThat(((Number) resourceRow(RESOURCE_TYPE_ROLE, String.valueOf(child.id()))
            .get("parent_id")).longValue()).isEqualTo(parentBProjectionId);

        // 父投影缺失 fail-closed：裸插父角色（无投影）下创建子角色整体回滚
        Long bareParent = insertBasicRole("t019-ext-bare-parent", "无投影父角色");
        assertThatThrownBy(() -> roleManageAppService.createRole(
            TENANT, new RoleCreateReq(bareParent, "BASIC_ROLE", "t019-ext-fc-child", "fail-closed子角色", null, null), creator))
            .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class)
            .hasMessageContaining("父资源投影缺失");
        Long residue = jdbc.queryForObject(
            "SELECT COUNT(*) FROM abstract_role WHERE tenant_id = ? AND external_id = 't019-ext-fc-child'",
            Long.class, TENANT);
        assertThat(residue).isZero();
    }

    @Test
    @DisplayName("禁用主体拒鉴（评审 P1-2）：enabled=false 后有效角色置空、门禁全拒；name=null 以 externalId 兜底投影（评审 P1-1）")
    void disabledSubjectShouldBeDeniedAndNullNameFallsBackToExternalId() {
        Long creator = insertSubject("t019-op-disable", "禁用操作者");
        Long creatorRole = insertBasicRole("t019-holder-disable", "禁用角色");
        insertUserRole(creator, creatorRole);
        insertScopeAllRolePerm(creatorRole, RESOURCE_TYPE_USER, CREATE_BIT);
        bindOperator(creator);

        // name=null：abstract_user.name 可空，投影以 externalId 兜底（评审 P1-1）
        UserResp targetResp = userManageAppService.createUser(
            TENANT, new UserCreateReq("USER", "t019-ext-disabled", null, true, null));
        Long target = targetResp.id();
        assertThat(resourceRow(RESOURCE_TYPE_USER, String.valueOf(target)).get("name"))
            .isEqualTo("t019-ext-disabled");

        // 目标主体自身持角色 + 类型级授权（先于其首次引擎调用装配）
        Long targetRole = insertBasicRole("t019-holder-target", "目标主体角色");
        insertUserRole(target, targetRole);
        insertScopeAllRolePerm(targetRole, RESOURCE_TYPE_ROLE, CREATE_BIT);
        assertThat(permQueryEngine.hasPermissionByCode(
            TENANT, target, "ROLE", null, "CREATE")).isTrue();

        // 管理员实例 MANAGE 装配后禁用目标主体
        Long manager = insertSubject("t019-op-disable-mgr", "禁用管理员");
        Long managerRole = insertBasicRole("t019-holder-disable-mgr", "禁用管理员角色");
        insertUserRole(manager, managerRole);
        insertInstanceRolePerm(managerRole, RESOURCE_TYPE_USER, MANAGE_BIT,
            ((Number) resourceRow(RESOURCE_TYPE_USER, String.valueOf(target)).get("id")).longValue());
        bindOperator(manager);
        userManageAppService.updateUser(TENANT, new UserUpdateReq(target, null, false, null));

        // DDL 语义 enabled=false 鉴权不通过：有效角色置空后门禁全拒（评审 P1-2）
        assertThat(permQueryEngine.hasPermissionByCode(
            TENANT, target, "ROLE", null, "CREATE")).isFalse();
        // 禁用镜像到投影 status=0，name 保持兜底值
        Map<String, Object> disabledRow = resourceRow(RESOURCE_TYPE_USER, String.valueOf(target));
        assertThat(((Number) disabledRow.get("status")).intValue()).isZero();
        assertThat(disabledRow.get("name")).isEqualTo("t019-ext-disabled");
    }

    // ===== 数据装配（jdbc 直插事实/授权，先于相关主体首次引擎调用） =====

    private void bindOperator(Long operatorId) {
        TenantContextHolder.setTenantId(TENANT);
        AccessRequestContext.bind(RequestContext.user(TENANT, operatorId));
    }

    private Long insertSubject(String externalId, String name) {
        return jdbc.queryForObject(
            "INSERT INTO abstract_user (tenant_id, user_type, external_id, name, enabled, extra, owner_service_code) "
                + "VALUES (?, ?, ?, ?, true, '{}', NULL) RETURNING id",
            Long.class, TENANT, USER_TYPE_LOCAL, externalId, name);
    }

    private Long insertBasicRole(String externalId, String name) {
        return jdbc.queryForObject(
            "INSERT INTO abstract_role (tenant_id, role_type, external_id, name, status, parent_id, extra) "
                + "VALUES (?, ?, ?, ?, 1, NULL, '{}') RETURNING id",
            Long.class, TENANT, ROLE_TYPE_BASIC, externalId, name);
    }

    private void insertUserRole(Long abstractUserId, Long targetRoleId) {
        jdbc.update(
            "INSERT INTO user_role (tenant_id, abstract_user_id, target_type, target_id) VALUES (?, ?, 'ROLE', ?)",
            TENANT, abstractUserId, targetRoleId);
    }

    private void insertScopeAllRolePerm(Long roleId, int resourceType, long grantedBits) {
        jdbc.update(
            "INSERT INTO role_resource_permission "
                + "(tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type, scope_all, grant_source) "
                + "VALUES (?, ?, NULL, ?, ?, true, 'MANUAL')",
            TENANT, roleId, grantedBits, resourceType);
    }

    private void insertInstanceRolePerm(Long roleId, int resourceType, long grantedBits, long resourceEntityId) {
        jdbc.update(
            "INSERT INTO role_resource_permission "
                + "(tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type, scope_all, grant_source) "
                + "VALUES (?, ?, ?, ?, ?, false, 'MANUAL')",
            TENANT, roleId, resourceEntityId, grantedBits, resourceType);
    }

    private Map<String, Object> resourceRow(int resourceType, String code) {
        return jdbc.queryForMap(
            "SELECT id, name, status, parent_id, owner_service_code, delete_flag FROM resource_entity "
                + "WHERE tenant_id = ? AND resource_type = ? AND code = ? AND code_type = 'default'",
            TENANT, resourceType, code);
    }

    private long countRoleByExternalId(String externalId) {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM abstract_role WHERE tenant_id = ? AND external_id = ?", Long.class, TENANT, externalId);
        return count == null ? 0 : count;
    }

    private long countSubjectByExternalId(String externalId) {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM abstract_user WHERE tenant_id = ? AND external_id = ?", Long.class, TENANT, externalId);
        return count == null ? 0 : count;
    }

    /** 故障用例投影残留计数：owner=access-service 的有效本地投影行（before/after 快照对比） */
    private long countLocalProjections(int resourceType) {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM resource_entity WHERE tenant_id = ? AND resource_type = ? "
                + "AND owner_service_code = 'access-service' AND delete_flag = 0",
            Long.class, TENANT, resourceType);
        return count == null ? 0 : count;
    }

    /** 故障用例变更日志计数：本任务投影写的 change_reason='local-projection'（before/after 快照对比） */
    private long countLocalProjectionChangeLogs() {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM permission_change_log WHERE tenant_id = ? AND change_reason = 'local-projection'",
            Long.class, TENANT);
        return count == null ? 0 : count;
    }
}
