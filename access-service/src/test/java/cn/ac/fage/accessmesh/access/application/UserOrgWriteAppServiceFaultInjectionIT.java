package cn.ac.fage.accessmesh.access.application;

import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.permission.cache.PermInvalidationPublisher;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * T-ACCESS-005 第十四轮评审 P2 补充：在 UNBIND 的 Spring 事务路径（removeUserFromOrg / deleteOrg）
 * 注入投影缺失故障，真实断言「管理事实整体回滚」。
 * <p>
 * 对比纯 Mock 单测（{@code LocalProjectionDomainServiceImplTest} 只 verify 投影软删未执行）：
 * 本测试经 Spring 事务代理调用真实 {@code UserOrgWriteAppService}/{@code OrgWriteAppService}
 * （@Transactional 生效），真实 PostgreSQL（Testcontainers）落库管理事实（sys_org/sys_user_org），
 * 用 @SpyBean 在 {@code unbindUserOrg}/{@code batchUnbindUserOrg} 注入投影缺失异常，
 * 断言管理事实仍存在（删除被回滚）、permission_change_log 无新增、回滚不发布缓存失效。
 * Docker 不可用时由 Testcontainers 自动跳过。
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
    "JWT_SECRET_KEY=test-jwt-secret-for-user-org-fault-injection",
    "ACCESSMESH_SIGNATURE_SECRET=test-signature-secret-for-user-org-fault-injection",
    "PERM_INTERNAL_SECRET=test-internal-secret-for-user-org-fault-injection"
})
class UserOrgWriteAppServiceFaultInjectionIT {

    private static final Long TENANT = 1L;
    private static final Long OPERATOR = 9L;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, UserOrgWriteAppServiceFaultInjectionIT.class);
    }

    @Autowired
    private UserOrgWriteAppService userOrgWriteAppService;

    @Autowired
    private OrgWriteAppService orgWriteAppService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 投影层 spy：仅指定方法注入故障，其余真实（成功用例全链路落库）。 */
    @SpyBean
    private LocalProjectionDomainService localProjectionDomainService;

    /** 失效广播 mock：断言「回滚不发布变更」。 */
    @MockBean
    private PermInvalidationPublisher publisher;

    /** 门禁 mock（void 方法默认通过）：解绑/删组织不依赖操作者权限数据。 */
    @MockBean
    private AdminPermissionValidator permissionValidator;


    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT);
        AccessRequestContext.bind(RequestContext.user(TENANT, OPERATOR));
        jdbcTemplate.execute("DELETE FROM permission_change_log WHERE tenant_id = " + TENANT);
        jdbcTemplate.execute("DELETE FROM user_role WHERE tenant_id = " + TENANT);
        jdbcTemplate.execute(
            "DELETE FROM resource_entity WHERE tenant_id = " + TENANT + " AND owner_service_code = 'access-service'");
        jdbcTemplate.execute(
            "DELETE FROM abstract_user WHERE tenant_id = " + TENANT + " AND owner_service_code = 'access-service'");
        jdbcTemplate.execute(
            "DELETE FROM abstract_role WHERE tenant_id = " + TENANT + " AND owner_service_code = 'access-service'");
        jdbcTemplate.execute("DELETE FROM sys_user_org WHERE tenant_id = " + TENANT);
        jdbcTemplate.execute("DELETE FROM sys_org WHERE tenant_id = " + TENANT);
    }

    @AfterEach
    void tearDown() {
        AccessRequestContext.clear();
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("removeUserFromOrg：投影缺失抛错 → 管理事实 sys_user_org 回滚仍存在、change_log 无新增、不发布缓存失效")
    void unbindProjectionMissingRollsBackFactAndChangeLog() {
        long userId = 10001L;
        long orgId = 20001L;
        insertOrg(orgId, "code-20001");
        insertUserOrg(userId, orgId);

        // 投影层注入「用户/角色投影缺失」故障（与单测 fail-closed 语义一致，但经真实 Spring 事务路径）
        doThrow(new BizException(PermissionErrorCode.USER_ROLE_RELATION_NOT_FOUND.getCode(),
            "local projection missing for user-org unbind"))
            .when(localProjectionDomainService)
            .unbindUserOrg(anyLong(), anyLong(), anyLong(), anyString(), any());

        assertThatThrownBy(() -> userOrgWriteAppService.removeUserFromOrg(userId, orgId))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("local projection missing");

        // 管理事实删除被回滚：关系行与组织行均仍存在（delete_flag=0）
        assertThat(countRows("sys_user_org")).isEqualTo(1);
        assertThat(countRows("sys_org")).isEqualTo(1);
        assertThat(countRows("permission_change_log")).isZero();
        assertThat(countRows("user_role")).isZero();
        // 回滚：afterCommit 不执行 → 缓存失效不发布
        verify(publisher, never()).publish(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("deleteOrg：批量解绑投影缺失抛错 → 管理事实 sys_org/sys_user_org 回滚仍存在、change_log 无新增、不发布缓存失效")
    void orgDeleteUnbindProjectionMissingRollsBackFacts() {
        long userId = 10002L;
        long orgId = 20002L;
        insertOrg(orgId, "code-20002");
        insertUserOrg(userId, orgId);

        doThrow(new BizException(PermissionErrorCode.USER_ROLE_RELATION_NOT_FOUND.getCode(),
            "local projection missing for user-org unbind"))
            .when(localProjectionDomainService)
            .batchUnbindUserOrg(anyLong(), any());

        assertThatThrownBy(() -> orgWriteAppService.deleteOrg(orgId))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("local projection missing");

        // 管理事实删除被回滚：组织软删与成员关系批量删除均未生效
        assertThat(countRows("sys_org")).isEqualTo(1);
        assertThat(countRows("sys_user_org")).isEqualTo(1);
        assertThat(countRows("permission_change_log")).isZero();
        assertThat(countRows("user_role")).isZero();
        verify(publisher, never()).publish(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("成功路径对照：投影完整时解绑落库（事实删除 + change_log + 提交后发布缓存失效）")
    void successPathCommitsAndPublishesInvalidation() {
        long userId = 10003L;
        long orgId = 20003L;
        insertOrg(orgId, "code-20003");
        insertUserOrg(userId, orgId);
        // 准备完整投影（spy 默认走真实实现）：用户/组织角色投影齐全，解绑不会 fail-closed
        localProjectionDomainService.upsertAdminUser(TENANT, userId, "解绑用户", true, null);
        localProjectionDomainService.upsertAdminOrg(TENANT, orgId, "1", "解绑组织", null, null, 1, 0, "{}");

        userOrgWriteAppService.removeUserFromOrg(userId, orgId);

        assertThat(countRows("sys_user_org")).isZero();
        assertThat(countRows("permission_change_log")).isEqualTo(1);
        // 提交后 @PermissionChange afterCommit flush → 广播失效
        verify(publisher, atLeastOnce()).publish(anyLong(), any(), any(), any());
    }

    /** 构造管理事实：sys_org 行（普通组织 orgType=1，非默认树）。 */
    private void insertOrg(long orgId, String code) {
        jdbcTemplate.update(
            "INSERT INTO sys_org (id, tenant_id, parent_id, org_type, code, name, level, sort_order, status, delete_flag)"
                + " VALUES (?, ?, NULL, '1', ?, ?, 1, 0, 1, 0)",
            orgId, TENANT, code, code + "-name");
    }

    /** 构造管理事实：sys_user_org 成员关系行。 */
    private void insertUserOrg(long userId, long orgId) {
        jdbcTemplate.update(
            "INSERT INTO sys_user_org (tenant_id, user_id, org_id, is_primary, delete_flag)"
                + " VALUES (?, ?, ?, false, 0)",
            TENANT, userId, orgId);
    }

    private long countRows(String table) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE tenant_id = " + TENANT, Long.class);
        return count == null ? 0L : count;
    }
}
