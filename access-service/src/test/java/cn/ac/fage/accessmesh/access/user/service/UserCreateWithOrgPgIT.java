package cn.ac.fage.accessmesh.access.user.service;

import cn.ac.fage.accessmesh.access.engine.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.projection.LocalProjectionDomainService;
import cn.ac.fage.accessmesh.access.user.dto.req.UserCreateReq;
import cn.ac.fage.accessmesh.access.user.dto.resp.UserCreateResp;
import org.junit.jupiter.api.AfterEach;
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
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R6 同事务可见性入口级行为锁（Q-009 收敛产物 T-ACCESS-046 外评 codex P3 补测）：
 * 真实 {@code UserWriteAppService.createUser(orgId 非空)} 全链路——同事务先
 * {@code createLocalUserSubject} 插入 abstract_user，再 {@code bindUserOrg} 经
 * {@code TypeResolutionService.resolveUserId}（真实 bean，非 mock）读回刚插入的行
 * 并产出 user_role 绑定。
 * <p>
 * 该链若被加上结果缓存、REQUIRES_NEW 或调整「先建主体后绑定」顺序，读回将得空 →
 * 20046 抛错整体回滚，本用例必红（此前仅 LocalProjectionDomainServiceImplTest 以
 * mock TypeResolutionService 覆盖组件级语义，无入口级真实链锁）。门禁经
 * {@code @MockBean AdminPermissionValidator} 放行（先例 UserOrgWriteAppServiceFaultInjectionIT），
 * 组织/投影/解析全部真实落库（真实 PostgreSQL + Redis，Testcontainers）。
 * Docker 不可用时自动跳过。
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
    "JWT_SECRET_KEY=test-jwt-secret-for-user-create-with-org",
    "ACCESSMESH_SIGNATURE_SECRET=test-signature-secret-for-user-create-with-org",
    "PERM_INTERNAL_SECRET=test-internal-secret-for-user-create-with-org"
})
class UserCreateWithOrgPgIT {

    private static final Long TENANT = 1L;
    private static final Long OPERATOR = 9L;
    private static final long ORG_ID = 30001L;
    private static final long TREE_CFG_ID = 31001L;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, UserCreateWithOrgPgIT.class);
    }

    @Autowired
    private UserWriteAppService userWriteAppService;

    @Autowired
    private LocalProjectionDomainService localProjectionDomainService;

    @Autowired
    private JdbcTemplate jdbc;

    /** 门禁 mock（void 方法默认通过）：本用例聚焦投影/解析真实链，不装配操作者授权。 */
    @MockBean
    private AdminPermissionValidator permissionValidator;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT);
        AccessRequestContext.bind(RequestContext.user(TENANT, OPERATOR));
        jdbc.execute("DELETE FROM permission_change_log WHERE tenant_id = " + TENANT);
        jdbc.execute("DELETE FROM user_role WHERE tenant_id = " + TENANT);
        jdbc.execute("DELETE FROM sys_user_org WHERE tenant_id = " + TENANT);
        jdbc.execute("DELETE FROM sys_user WHERE tenant_id = " + TENANT);
        jdbc.execute("DELETE FROM sys_org_tree_config WHERE tenant_id = " + TENANT);
        jdbc.execute("DELETE FROM sys_org WHERE tenant_id = " + TENANT);
        jdbc.execute(
            "DELETE FROM resource_entity WHERE tenant_id = " + TENANT + " AND owner_service_code = 'access-service'");
        jdbc.execute(
            "DELETE FROM abstract_user WHERE tenant_id = " + TENANT + " AND owner_service_code = 'access-service'");
        jdbc.execute(
            "DELETE FROM abstract_role WHERE tenant_id = " + TENANT + " AND owner_service_code = 'access-service'");
    }

    @AfterEach
    void tearDown() {
        AccessRequestContext.clear();
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("createUser(orgId)：同事务插 abstract_user 后经真实 TypeResolutionService 读回并产出 user_role 绑定（R6 红线锁）")
    void createUserWithOrgShouldReadBackSubjectAndBindUserRoleInSameTransaction() {
        // 组织事实 + 默认树配置（createUser 的 validateOrgInDefaultTree 消费）
        jdbc.update(
            "INSERT INTO sys_org (id, tenant_id, parent_id, org_type, code, name, level, sort_order, status, delete_flag)"
                + " VALUES (?, ?, NULL, '1', 'r06-org', 'R06组织', 1, 0, 1, 0)",
            ORG_ID, TENANT);
        jdbc.update(
            "INSERT INTO sys_org_tree_config (id, tenant_id, root_org_id, tree_name, tree_type, is_default)"
                + " VALUES (?, ?, ?, '默认树', 'ORG', true)",
            TREE_CFG_ID, TENANT, ORG_ID);
        // 组织角色投影（bindUserOrg 的 target/relation 解析依赖；返回 ORG 角色 id）
        Long orgRoleId = localProjectionDomainService.upsertAdminOrg(
            TENANT, ORG_ID, "1", "R06组织", null, null, 1);
        assertThat(orgRoleId).isNotNull();

        // 生产入口：同事务 createLocalUserSubject（插 abstract_user）→ bindUserOrg
        //（requireUserProjectionId → 真实 TypeResolutionService.resolveUserId 读回）→ 插 user_role
        UserCreateResp resp = userWriteAppService.createUser(
            new UserCreateReq("r06-user", "R06用户", null, null, 1, ORG_ID, null));
        Long subjectId = resp.id();
        assertThat(subjectId).isNotNull();

        // 管理事实：sys_user 与主体同 ID（T-ORG-001 显式同 ID 写两表）
        Map<String, Object> sysUser = jdbc.queryForMap(
            "SELECT id, username, status FROM sys_user WHERE tenant_id = ? AND username = ?",
            TENANT, "r06-user");
        assertThat(((Number) sysUser.get("id")).longValue()).isEqualTo(subjectId);
        assertThat(((Number) sysUser.get("status")).intValue()).isEqualTo(1);

        // 主体投影行（createLocalUserSubject 产物，external_id=主体 ID 字符串化）
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM abstract_user WHERE tenant_id = ? AND external_id = ? AND enabled = true",
            Long.class, TENANT, String.valueOf(subjectId))).isEqualTo(1L);

        // 组织归属事实（orgId 分支产物；primaryOrg 缺省 true）
        Map<String, Object> userOrg = jdbc.queryForMap(
            "SELECT org_id, is_primary FROM sys_user_org WHERE tenant_id = ? AND user_id = ?",
            TENANT, subjectId);
        assertThat(((Number) userOrg.get("org_id")).longValue()).isEqualTo(ORG_ID);
        assertThat((Boolean) userOrg.get("is_primary")).isTrue();

        // R6 红线核心断言：user_role 绑定行存在且 target/relation 均指向组织角色——
        // 证明 bindUserOrg 在同一事务内读回了刚插入的 abstract_user（读回失败=20046 整体回滚，
        // 本断言及其上方全部事实断言都不可达）
        Map<String, Object> userRole = jdbc.queryForMap(
            "SELECT target_id, relation_id, delete_flag FROM user_role"
                + " WHERE tenant_id = ? AND abstract_user_id = ? AND delete_flag = 0",
            TENANT, subjectId);
        assertThat(((Number) userRole.get("target_id")).longValue()).isEqualTo(orgRoleId);
        assertThat(((Number) userRole.get("relation_id")).longValue()).isEqualTo(orgRoleId);
    }
}
