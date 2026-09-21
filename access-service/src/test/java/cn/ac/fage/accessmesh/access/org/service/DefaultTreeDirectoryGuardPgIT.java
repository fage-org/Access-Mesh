package cn.ac.fage.accessmesh.access.org.service;

import cn.ac.fage.accessmesh.access.engine.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.infrastructure.cache.PermInvalidationPublisher;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.org.service.domain.OrgTreeConfigDomainService;
import cn.ac.fage.accessmesh.access.projection.LocalProjectionDomainService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
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
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * 默认树身份目录守卫容器验收（T-ORG-002，真实 PostgreSQL；F001 隔离库动态证据的回归锁）。
 * <p>
 * 覆盖任务验收：默认根无子节点时删除仍明确拒绝且零副作用；删默认树叶子致成员失去最后
 * 归属时拒绝（级联删除与直接移除同一业务结果=同码 11013）；普通（非默认树）可删组织
 * 成功路径；另一租户同 ID 拒绝；守卫通过后投影故障事务回滚；默认根墓碑定点恢复演练
 * （恢复 SQL 可操作性副本验证——业务删除记录不被启动逻辑自动复活，恢复为人工 SQL）。
 * 门禁 mock 放行（守卫语义不依赖操作者权限数据，权限矩阵另由 SecurityMatrixIT 覆盖）。
 * </p>
 */
@Tag("testcontainers")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    "accessmesh.sync.scheduler.enabled=false",
    "access.bootstrap.enabled=false",
    "mybatis-flex.configuration.map-underscore-to-camel-case=true",
    "logging.level.cn.ac.fage.accessmesh=WARN",
    "JWT_SECRET_KEY=test-jwt-secret-for-default-tree-guard",
    "ACCESSMESH_SIGNATURE_SECRET=test-signature-secret-for-default-tree-guard",
    "PERM_INTERNAL_SECRET=test-internal-secret-for-default-tree-guard"
})
class DefaultTreeDirectoryGuardPgIT {

    private static final Long TENANT = 1L;
    private static final Long OPERATOR = 9L;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, DefaultTreeDirectoryGuardPgIT.class);
    }

    @Autowired
    private OrgWriteAppService orgWriteAppService;
    @Autowired
    private UserOrgWriteAppService userOrgWriteAppService;
    @Autowired
    private OrgTreeConfigDomainService orgTreeConfigDomainService;
    /** 投影层 spy：故障注入用例在 batchUnbindUserOrg 注入异常，其余用例真实执行。 */
    @SpyBean
    private LocalProjectionDomainService localProjectionDomainService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 失效广播 mock：拒绝路径断言「不发布变更」。 */
    @MockBean
    private PermInvalidationPublisher publisher;

    /** 门禁 mock（void 方法默认通过）：守卫语义不依赖操作者权限数据。 */
    @MockBean
    private AdminPermissionValidator permissionValidator;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT);
        AccessRequestContext.bind(RequestContext.user(TENANT, OPERATOR));
        jdbcTemplate.execute("DELETE FROM permission_change_log WHERE tenant_id IN (1, 2)");
        jdbcTemplate.execute("DELETE FROM user_role WHERE tenant_id IN (1, 2)");
        jdbcTemplate.execute("DELETE FROM sys_user_org WHERE tenant_id IN (1, 2)");
        jdbcTemplate.execute("DELETE FROM sys_org_tree_config WHERE tenant_id IN (1, 2)");
        jdbcTemplate.execute(
            "DELETE FROM resource_entity WHERE tenant_id IN (1, 2) AND owner_service_code = 'access-service'");
        jdbcTemplate.execute(
            "DELETE FROM abstract_user WHERE tenant_id IN (1, 2) AND owner_service_code = 'access-service'");
        jdbcTemplate.execute(
            "DELETE FROM abstract_role WHERE tenant_id IN (1, 2) AND owner_service_code = 'access-service'");
        jdbcTemplate.execute("DELETE FROM sys_org WHERE tenant_id IN (1, 2)");
    }

    @AfterEach
    void tearDown() {
        AccessRequestContext.clear();
        TenantContextHolder.clear();
    }

    /**
     * 默认树骨架：根(rootId，parent_id=0 对齐生产顶级口径——bootstrap 固定图与 createOrg
     * 缺省均写 0，bootstrap 校验并要求 0；schema 注释「NULL=根节点」与实现相反属存量漂移)
     * + 两个叶子，默认配置指根。
     */
    private void seedDefaultTree(long rootId, long leafA, long leafB) {
        insertOrg(rootId, 0L, "root-" + rootId);
        insertOrg(leafA, rootId, "leaf-" + leafA);
        insertOrg(leafB, rootId, "leaf-" + leafB);
        jdbcTemplate.update(
            "INSERT INTO sys_org_tree_config (tenant_id, root_org_id, tree_name, tree_type, is_default, single_assoc, delete_flag)"
                + " VALUES (?, ?, '默认树', 'DEFAULT', true, true, 0)",
            TENANT, rootId);
    }

    private void insertOrg(long orgId, Long parentId, String code) {
        jdbcTemplate.update(
            "INSERT INTO sys_org (id, tenant_id, parent_id, org_type, code, name, level, sort_order, status, delete_flag)"
                + " VALUES (?, ?, ?, '1', ?, ?, ?, 0, 1, 0)",
            orgId, TENANT, parentId, code, code + "-name",
            (parentId == null || parentId == 0L) ? 1 : 2);
    }

    private void insertUserOrg(long userId, long orgId) {
        jdbcTemplate.update(
            "INSERT INTO sys_user_org (tenant_id, user_id, org_id, is_primary, delete_flag)"
                + " VALUES (?, ?, ?, false, 0)",
            TENANT, userId, orgId);
    }

    private long count(String table, String where) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE " + where, Long.class);
        return count == null ? 0L : count;
    }

    private long validOrgRows(long orgId) {
        return count("sys_org", "tenant_id = 1 AND id = " + orgId + " AND delete_flag = 0");
    }

    @Test
    @DisplayName("默认根无子节点时删除仍拒绝（11017）且管理事实/配置零变化")
    void deleteDefaultRootRejectedEvenWithoutChildren() {
        // 根 300 独占默认树（无子节点形态）
        insertOrg(300L, null, "solo-root");
        jdbcTemplate.update(
            "INSERT INTO sys_org_tree_config (tenant_id, root_org_id, tree_name, tree_type, is_default, single_assoc, delete_flag)"
                + " VALUES (?, 300, '默认树', 'DEFAULT', true, true, 0)", TENANT);

        assertThatThrownBy(() -> orgWriteAppService.deleteOrg(300L))
            .isInstanceOf(BizException.class)
            .hasMessageContaining(AccessErrorCode.ORG_DEFAULT_ROOT_DELETE_FORBIDDEN.getMessage());

        assertThat(validOrgRows(300L)).isEqualTo(1);
        assertThat(count("sys_org_tree_config", "tenant_id = 1 AND delete_flag = 0")).isEqualTo(1);
        assertThat(count("permission_change_log", "tenant_id = 1")).isZero();
        verify(publisher, never()).publish(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("删默认树叶子致成员失去最后归属 → 拒绝（11013 提示人数），解绑前拦截零副作用")
    void deleteLeafLosingLastHomeRejectedBeforeUnbind() {
        seedDefaultTree(400L, 401L, 402L);
        insertUserOrg(4100L, 401L);

        assertThatThrownBy(() -> orgWriteAppService.deleteOrg(401L))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("1 名成员失去默认组织树最后归属")
            .hasMessageContaining("请先迁移成员");

        assertThat(validOrgRows(401L)).isEqualTo(1);
        assertThat(count("sys_user_org", "tenant_id = 1 AND org_id = 401 AND delete_flag = 0")).isEqualTo(1);
        assertThat(count("permission_change_log", "tenant_id = 1")).isZero();
        verify(publisher, never()).publish(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("级联删除与直接移除同一业务结果：迁移后删叶子成功，剩余唯一归属再移除（无论单移/级联）拒绝 11013")
    void cascadedDeleteAndDirectRemovalShareSameOutcome() {
        seedDefaultTree(500L, 501L, 502L);
        insertUserOrg(5100L, 501L);
        insertUserOrg(5100L, 502L);
        localProjectionDomainService.upsertAdminUser(TENANT, 5100L, "迁移用户", true);
        // 父（根 500）投影先行，子（叶子 501）投影按父链补齐
        localProjectionDomainService.upsertAdminOrg(TENANT, 500L, "1", "根", null, null, 1);
        localProjectionDomainService.upsertAdminOrg(TENANT, 501L, "1", "叶子A", 500L, "1", 1);

        // 成员在默认树仍有其他归属（502）→ 删 501 放行
        orgWriteAppService.deleteOrg(501L);
        assertThat(validOrgRows(501L)).isZero();
        assertThat(count("sys_user_org", "tenant_id = 1 AND user_id = 5100 AND delete_flag = 0")).isEqualTo(1);

        // 剩余唯一归属 502：单移拒绝（直接移除入口）
        assertThatThrownBy(() -> userOrgWriteAppService.removeUserFromOrg(5100L, 502L))
            .isInstanceOf(BizException.class)
            .hasMessageContaining(AccessErrorCode.USER_LOSE_DEFAULT_TREE_HOME.getMessage());
        assertThat(count("sys_user_org", "tenant_id = 1 AND user_id = 5100 AND delete_flag = 0")).isEqualTo(1);
    }

    @Test
    @DisplayName("普通（非默认树）组织删除成功路径：不做身份目录归属检查")
    void nonDefaultTreeOrgDeleteSucceeds() {
        insertOrg(600L, null, "ext-600");
        insertUserOrg(6100L, 600L);
        localProjectionDomainService.upsertAdminUser(TENANT, 6100L, "外部用户", true);
        localProjectionDomainService.upsertAdminOrg(TENANT, 600L, "1", "外部组织", null, null, 1);

        orgWriteAppService.deleteOrg(600L);

        assertThat(validOrgRows(600L)).isZero();
        assertThat(count("sys_user_org", "tenant_id = 1 AND org_id = 600")).isZero();
    }

    @Test
    @DisplayName("另一租户同 ID 组织 → 本租户删除拒绝（ORG_NOT_FOUND，租户隔离）")
    void crossTenantSameIdRejected() {
        jdbcTemplate.update(
            "INSERT INTO sys_org (id, tenant_id, parent_id, org_type, code, name, level, sort_order, status, delete_flag)"
                + " VALUES (700, 2, NULL, '1', 'other-tenant', '他租户组织', 1, 0, 1, 0)");

        assertThatThrownBy(() -> orgWriteAppService.deleteOrg(700L))
            .isInstanceOf(BizException.class)
            .hasMessageContaining(AccessErrorCode.ORG_NOT_FOUND.getMessage());
        assertThat(count("sys_org", "tenant_id = 2 AND id = 700 AND delete_flag = 0")).isEqualTo(1);
    }

    @Test
    @DisplayName("守卫通过后投影故障 → 事务整体回滚（默认树叶子删除无管理事实残留）")
    void guardPassedThenProjectionFaultRollsBack() {
        seedDefaultTree(800L, 801L, 802L);
        insertUserOrg(8100L, 801L);
        insertUserOrg(8100L, 802L);
        doThrow(new BizException(AccessErrorCode.USER_ROLE_RELATION_NOT_FOUND.getCode(),
            "injected: batch unbind failure"))
            .when(localProjectionDomainService).batchUnbindUserOrg(anyLong(), any());
        try {
            assertThatThrownBy(() -> orgWriteAppService.deleteOrg(801L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("injected: batch unbind failure");

            assertThat(validOrgRows(801L)).isEqualTo(1);
            assertThat(count("sys_user_org", "tenant_id = 1 AND org_id = 801 AND delete_flag = 0")).isEqualTo(1);
            assertThat(count("permission_change_log", "tenant_id = 1")).isZero();
            verify(publisher, never()).publish(anyLong(), any(), any(), any());
        } finally {
            reset(localProjectionDomainService);
        }
    }

    @Test
    @DisplayName("非成员请求幂等放行：用户本不属于目标默认树组织 → 不误拒 11013（共享守卫候选集=现成员）")
    void nonMemberRemovalIsIdempotentNoop() {
        seedDefaultTree(1000L, 1001L, 1002L);
        insertUserOrg(11000L, 1002L); // 用户只挂 1002，不属于 1001
        // 投影齐全（unbind 对投影缺失 fail-closed 是另一语义；本用例锁守卫候选集）
        localProjectionDomainService.upsertAdminUser(TENANT, 11000L, "非成员用户", true);
        localProjectionDomainService.upsertAdminOrg(TENANT, 1000L, "1", "根", null, null, 1);
        localProjectionDomainService.upsertAdminOrg(TENANT, 1001L, "1", "叶子A", 1000L, "1", 1);

        // 移除其在 1001 上的（不存在的）关系：旧内联实现会误拒 11013，共享守卫候选集不含该用户 → 幂等放行
        userOrgWriteAppService.removeUserFromOrg(11000L, 1001L);

        assertThat(count("sys_user_org", "tenant_id = 1 AND user_id = 11000 AND delete_flag = 0")).isEqualTo(1);
    }

    @Test
    @DisplayName("恢复演练：默认根墓碑（绕过守卫的事故态）按定点 SQL 恢复，身份目录视图复原")
    void defaultRootTombstoneRecoveryDrill() {
        seedDefaultTree(900L, 901L, 902L);
        insertUserOrg(9100L, 901L);
        // 模拟 F001 事故形态：绕过守卫直改 SQL 软删默认根（业务删除记录不被启动逻辑自动复活）
        jdbcTemplate.update(
            "UPDATE sys_org SET delete_flag = 1 WHERE tenant_id = 1 AND id = 900");
        assertThat(orgTreeConfigDomainService.resolveDefaultTreeOrgIds(TENANT)).isEmpty();

        // 定点恢复（docs/ops/runbook-default-tree-recovery.md 步骤演练）：
        // ①只读诊断：默认配置指向的根为墓碑 ②恢复根行（undo 软删，不自动重建任何缺失子树）
        jdbcTemplate.update(
            "UPDATE sys_org SET delete_flag = 0, updated_at = now() WHERE tenant_id = 1 AND id = 900");

        assertThat(orgTreeConfigDomainService.resolveDefaultTreeOrgIds(TENANT)).isNotEmpty();
        assertThat(validOrgRows(900L)).isEqualTo(1);
        // 既有成员归属随恢复重新可见（901 在恢复后的默认树范围内）
        assertThat(orgTreeConfigDomainService.resolveDefaultTreeOrgIds(TENANT)).contains(901L);
    }
}
