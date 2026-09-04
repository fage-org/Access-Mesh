package cn.ac.fage.accessmesh.access.characterization;

import cn.ac.fage.accessmesh.access.admin.mapper.SysMenuMapper;
import cn.ac.fage.accessmesh.access.admin.mapper.SysOrgMapper;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.admin.dto.req.MenuCreateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.MenuUpdateReq;
import cn.ac.fage.accessmesh.access.admin.service.domain.MenuDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.OrgDomainService;
import cn.ac.fage.accessmesh.access.application.MenuWriteAppService;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.common.exception.BizException;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 四棵树 move 并发成环窗口与递归 CTE 遇环收敛统一加固的真实 PostgreSQL 验证
 * （T-PERM-044，Testcontainers，Docker 可用时执行）。
 * <p>
 * 方案（用户决策 2026-09-04）：树级 {@code pg_advisory_xact_lock} 根治写窗口 +
 * 递归 CTE UNION 去重（subtreeHeight 深度上限）止损 + 内存递归 visited 防环。
 * 本 PgIT 用 JDBC 直接制造 2-环脏数据，锁定：
 * </p>
 * <ul>
 *   <li>四棵树的子孙/祖先递归 CTE 在 2-环上返回且结果确定（不挂死连接）</li>
 *   <li>组织/菜单祖先链内存上溯在环上返回截断链（JVM 不死循环）</li>
 *   <li>树写锁真实互斥且经事务 afterCompletion 释放（解锁不先于提交）</li>
 *   <li>双线程同瞬交叉移动同一对节点：恰好一成一败，环无法落库</li>
 *   <li>环检测订正 SQL（与 access-service-rebuild-runbook 同源）能定位环节点</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker = true)
@org.springframework.test.annotation.DirtiesContext(
    classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@org.springframework.test.context.TestPropertySource(properties = {
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    "accessmesh.sync.scheduler.enabled=false",
    "mybatis-flex.configuration.map-underscore-to-camel-case=true",
    "logging.level.cn.ac.fage.accessmesh=WARN",
    "JWT_SECRET_KEY=test-jwt-secret-for-tree-cycle",
    "ACCESSMESH_SIGNATURE_SECRET=test-signature-secret-for-tree-cycle",
    "PERM_INTERNAL_SECRET=test-internal-secret-for-tree-cycle"
})
class TreeCycleHardeningPgIT {

    private static final Long TENANT = 1L;
    private static final Long OPERATOR = 9L;
    private static final Path DDL_PATH = Path.of("..", "docs", "design", "schema", "access-service.sql");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("tree_cycle_test")
        .withUsername("perm")
        .withPassword("perm");

    /** Redis 容器与客户端密码必须对齐（空串密码会触发 AUTH 被无密码 Redis 拒绝） */
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
        // 原样执行权威 DDL + 种子数据（type_definition MENU=1 供菜单投影解析）
        String sql = Files.readString(DDL_PATH, StandardCharsets.UTF_8);
        try (var conn = java.sql.DriverManager.getConnection(
            postgres.getJdbcUrl() + "?stringtype=unspecified", postgres.getUsername(), postgres.getPassword());
             var st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    @Autowired
    private AbstractRoleMapper abstractRoleMapper;
    @Autowired
    private SysOrgMapper sysOrgMapper;
    @Autowired
    private SysMenuMapper sysMenuMapper;
    @Autowired
    private ResourceEntityMapper resourceEntityMapper;
    @Autowired
    private OrgDomainService orgDomainService;
    @Autowired
    private MenuDomainService menuDomainService;
    @Autowired
    private MenuWriteAppService menuWriteAppService;
    @Autowired
    private TreeWriteLockSupport treeWriteLockSupport;
    @Autowired
    private org.redisson.api.RedissonClient redissonClient;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    /** 门禁 mock（void 方法默认通过）：交叉移动用例不依赖操作者权限数据。 */
    @MockBean
    private AdminPermissionValidator permissionValidator;

    /**
     * parent 环检测订正 SQL（与 docs/design/access-service-rebuild-runbook「树 parent 环订正」
     * 同源；表名按树替换）：每个节点沿 parent 链上溯，depth 上限 200 防 CTE 自身遇环不收敛。
     */
    private static final String MENU_CYCLE_DETECTION_SQL = """
        WITH RECURSIVE up AS (
            SELECT id, parent_id, id AS origin, 0 AS depth FROM sys_menu
            WHERE tenant_id = ? AND delete_flag = 0
            UNION ALL
            SELECT m.id, m.parent_id, up.origin, up.depth + 1 FROM sys_menu m
            JOIN up ON m.id = up.parent_id
            WHERE up.depth < 200
        )
        SELECT DISTINCT origin FROM up WHERE id = origin AND depth > 0
        """;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT);
        AccessRequestContext.bind(RequestContext.user(TENANT, OPERATOR));
        jdbcTemplate.update("DELETE FROM sys_menu WHERE tenant_id = ?", TENANT);
        jdbcTemplate.update("DELETE FROM sys_org WHERE tenant_id = ?", TENANT);
        jdbcTemplate.update("DELETE FROM abstract_role WHERE tenant_id = ?", TENANT);
        jdbcTemplate.update("DELETE FROM resource_entity WHERE tenant_id = ?", TENANT);
    }

    @AfterEach
    void tearDown() {
        AccessRequestContext.clear();
        TenantContextHolder.clear();
    }

    /** 在 sys_menu 制造 2-环（a.parent=b 且 b.parent=a），返回 [aId, bId]。 */
    private Long[] seedMenuCycle() {
        jdbcTemplate.update(
            "INSERT INTO sys_menu (tenant_id, display_name, menu_type, parent_id, status, sort_order, delete_flag)"
                + " VALUES (?, 'cycle-a', 'MENU', 0, 1, 0, 0)", TENANT);
        Long a = jdbcTemplate.queryForObject(
            "SELECT id FROM sys_menu WHERE tenant_id = ? AND display_name = 'cycle-a'", Long.class, TENANT);
        jdbcTemplate.update(
            "INSERT INTO sys_menu (tenant_id, display_name, menu_type, parent_id, status, sort_order, delete_flag)"
                + " VALUES (?, 'cycle-b', 'MENU', ?, 1, 0, 0)", TENANT, a);
        Long b = jdbcTemplate.queryForObject(
            "SELECT id FROM sys_menu WHERE tenant_id = ? AND display_name = 'cycle-b'", Long.class, TENANT);
        jdbcTemplate.update("UPDATE sys_menu SET parent_id = ? WHERE id = ?", b, a);
        return new Long[]{a, b};
    }

    @Test
    @DisplayName("SQL 侧：四树 2-环下子孙/祖先递归 CTE 返回且结果确定（UNION 去重自终止）")
    void recursiveCtesTerminateOnTwoNodeCycles() {
        // —— abstract_role（role_type=5 供祖先 GROUP_ROLE 过滤命中）——
        jdbcTemplate.update(
            "INSERT INTO abstract_role (tenant_id, role_type, external_id, name, status, parent_id, delete_flag)"
                + " VALUES (?, 5, 'cyc-a', 'A', 1, NULL, 0)", TENANT);
        Long roleA = jdbcTemplate.queryForObject(
            "SELECT id FROM abstract_role WHERE tenant_id = ? AND external_id = 'cyc-a'", Long.class, TENANT);
        jdbcTemplate.update(
            "INSERT INTO abstract_role (tenant_id, role_type, external_id, name, status, parent_id, delete_flag)"
                + " VALUES (?, 5, 'cyc-b', 'B', 1, ?, 0)", TENANT, roleA);
        Long roleB = jdbcTemplate.queryForObject(
            "SELECT id FROM abstract_role WHERE tenant_id = ? AND external_id = 'cyc-b'", Long.class, TENANT);
        jdbcTemplate.update("UPDATE abstract_role SET parent_id = ? WHERE id = ?", roleB, roleA);

        assertThat(abstractRoleMapper.selectDescendantIdsBatch(TENANT, Set.of(roleA)))
            .containsExactly(roleB);
        assertThat(abstractRoleMapper.selectAncestorGroupRoleIdsBatch(TENANT, Set.of(roleA)))
            .containsExactly(roleB);

        // —— sys_org ——
        jdbcTemplate.update(
            "INSERT INTO sys_org (tenant_id, code, name, org_type, parent_id, level, status, sort_order, delete_flag)"
                + " VALUES (?, 'cyc-a', 'A', '1', 0, 1, 1, 0, 0)", TENANT);
        Long orgA = jdbcTemplate.queryForObject(
            "SELECT id FROM sys_org WHERE tenant_id = ? AND code = 'cyc-a'", Long.class, TENANT);
        jdbcTemplate.update(
            "INSERT INTO sys_org (tenant_id, code, name, org_type, parent_id, level, status, sort_order, delete_flag)"
                + " VALUES (?, 'cyc-b', 'B', '1', ?, 2, 1, 0, 0)", TENANT, orgA);
        Long orgB = jdbcTemplate.queryForObject(
            "SELECT id FROM sys_org WHERE tenant_id = ? AND code = 'cyc-b'", Long.class, TENANT);
        jdbcTemplate.update("UPDATE sys_org SET parent_id = ? WHERE id = ?", orgB, orgA);

        assertThat(sysOrgMapper.selectDescendantIdsIncludingSelf(TENANT, orgA))
            .containsExactlyInAnyOrder(orgA, orgB);
        assertThat(sysOrgMapper.selectDescendantIds(TENANT, orgA)).containsExactly(orgB);

        // —— sys_menu + subtreeHeight 深度上限（depth 列每层递增，UNION 去重无效，上限 100 截断）——
        Long[] menuIds = seedMenuCycle();
        Long menuA = menuIds[0];
        Long menuB = menuIds[1];

        assertThat(sysMenuMapper.selectDescendantIdsIncludingSelf(TENANT, menuA))
            .containsExactlyInAnyOrder(menuA, menuB);
        assertThat(sysMenuMapper.selectDescendantIds(TENANT, menuA)).containsExactly(menuB);
        assertThat(sysMenuMapper.selectSubtreeHeight(TENANT, menuA)).isEqualTo(100);

        // —— resource_entity ——
        jdbcTemplate.update(
            "INSERT INTO resource_entity (tenant_id, resource_type, code, name, status, parent_id, delete_flag)"
                + " VALUES (?, 3, 'cyc-a', 'A', 1, NULL, 0)", TENANT);
        Long resA = jdbcTemplate.queryForObject(
            "SELECT id FROM resource_entity WHERE tenant_id = ? AND code = 'cyc-a'", Long.class, TENANT);
        jdbcTemplate.update(
            "INSERT INTO resource_entity (tenant_id, resource_type, code, name, status, parent_id, delete_flag)"
                + " VALUES (?, 3, 'cyc-b', 'B', 1, ?, 0)", TENANT, resA);
        Long resB = jdbcTemplate.queryForObject(
            "SELECT id FROM resource_entity WHERE tenant_id = ? AND code = 'cyc-b'", Long.class, TENANT);
        jdbcTemplate.update("UPDATE resource_entity SET parent_id = ? WHERE id = ?", resB, resA);

        List<ResourceEntityMapper.DescendantResult> resDescendants =
            resourceEntityMapper.selectDescendantIdsBatch(TENANT, Set.of(resA));
        assertThat(resDescendants).hasSize(1);
        assertThat(resDescendants.get(0).getResourceId()).isEqualTo(resA);
        assertThat(resDescendants.get(0).getDescendantId()).isEqualTo(resB);
    }

    @Test
    @DisplayName("内存侧：组织/菜单祖先链在 2-环上返回截断链（visited 重访截断，不无限上溯）")
    void ancestorChainsTerminateOnCycle() {
        Long[] menuIds = seedMenuCycle();
        Long menuA = menuIds[0];
        Long menuB = menuIds[1];

        // 截断链语义：A → B → A（重访即停），calculateDepth = 祖先数 + 1 = 3
        assertThat(menuDomainService.getAncestorIds(TENANT, menuA)).containsExactly(menuB, menuA);
        assertThat(menuDomainService.calculateDepth(TENANT, menuA)).isEqualTo(3);

        jdbcTemplate.update(
            "INSERT INTO sys_org (tenant_id, code, name, org_type, parent_id, level, status, sort_order, delete_flag)"
                + " VALUES (?, 'cyc-a', 'A', '1', 0, 1, 1, 0, 0)", TENANT);
        Long orgA = jdbcTemplate.queryForObject(
            "SELECT id FROM sys_org WHERE tenant_id = ? AND code = 'cyc-a'", Long.class, TENANT);
        jdbcTemplate.update(
            "INSERT INTO sys_org (tenant_id, code, name, org_type, parent_id, level, status, sort_order, delete_flag)"
                + " VALUES (?, 'cyc-b', 'B', '1', ?, 2, 1, 0, 0)", TENANT, orgA);
        Long orgB = jdbcTemplate.queryForObject(
            "SELECT id FROM sys_org WHERE tenant_id = ? AND code = 'cyc-b'", Long.class, TENANT);
        jdbcTemplate.update("UPDATE sys_org SET parent_id = ? WHERE id = ?", orgB, orgA);

        assertThat(orgDomainService.getAncestorIds(TENANT, orgA)).containsExactly(orgB, orgA);
    }

    @Test
    @DisplayName("树写锁互斥且经事务 afterCompletion 释放（解锁不先于提交）")
    void redisLockHeldUntilTransactionCompletion() throws Exception {
        String lockKey = "accessmesh:tree-write-lock:"
            + TreeWriteLockSupport.TreeLockTarget.SYS_MENU.key() + ":" + TENANT;
        org.redisson.api.RLock probeLock = redissonClient.getLock(lockKey);

        TransactionTemplate txn = new TransactionTemplate(transactionManager);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch locked = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            Future<?> holder = pool.submit(() -> {
                TenantContextHolder.setTenantId(TENANT);
                try {
                    txn.executeWithoutResult(status -> {
                        treeWriteLockSupport.lockTreeWrites(TENANT,
                            TreeWriteLockSupport.TreeLockTarget.SYS_MENU);
                        locked.countDown();
                        try {
                            release.await(5, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                } finally {
                    TenantContextHolder.clear();
                }
            });

            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

            // 持锁事务进行中（未提交）：同键互斥
            assertThat(probeLock.tryLock()).as("事务提交前同键不可获取").isFalse();

            release.countDown();
            holder.get(5, TimeUnit.SECONDS);

            // 事务结束后 afterCompletion 已释放：同键可获取（tryLock 立即返回并清理）
            assertThat(probeLock.tryLock()).as("事务结束后同键可获取").isTrue();
            probeLock.unlock();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("交叉移动窗口：双线程同瞬 A→B 下 / B→A 下，恰好一成一败，环无法落库")
    void crossMoveCannotCreateCycle() throws Exception {
        Long aId = menuWriteAppService.createMenu(new MenuCreateReq(
            "MENU", "A", 0L, "/cycle/a", null, null, 1, null, null, null));
        Long bId = menuWriteAppService.createMenu(new MenuCreateReq(
            "MENU", "B", 0L, "/cycle/b", null, null, 1, null, null, null));

        CountDownLatch start = new CountDownLatch(1);
        Callable<Object> moveAUnderB = contextualizedMove(start, aId, bId, "a-to-b");
        Callable<Object> moveBUnderA = contextualizedMove(start, bId, aId, "b-to-a");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> f1 = pool.submit(moveAUnderB);
            Future<Object> f2 = pool.submit(moveBUnderA);
            start.countDown();
            Object r1 = f1.get(10, TimeUnit.SECONDS);
            Object r2 = f2.get(10, TimeUnit.SECONDS);

            // 无论两请求以何种次序进入锁，后进锁者校验看到先进锁者已提交的 parent → 拒绝；
            // 两个都成功即窗口未收口（旧实现交叉通过校验时的结果）
            long successes = List.of(r1, r2).stream().filter("ok"::equals).count();
            assertThat(successes).as("恰好一个移动成功（r1=%s, r2=%s）", r1, r2).isEqualTo(1);
            long rejections = List.of(r1, r2).stream()
                .filter(o -> o instanceof BizException b
                    && b.getErrorCode() == 10207)
                .count();
            assertThat(rejections).as("另一个被 MENU_PARENT_INVALID(10207) 拒绝（r1=%s, r2=%s）", r1, r2).isEqualTo(1);

            // 最终 parent 关系不构成环
            Long aParent = jdbcTemplate.queryForObject(
                "SELECT parent_id FROM sys_menu WHERE id = ?", Long.class, aId);
            Long bParent = jdbcTemplate.queryForObject(
                "SELECT parent_id FROM sys_menu WHERE id = ?", Long.class, bId);
            assertThat(aParent.equals(bId) && bParent.equals(aId))
                .as("2-环未落库").isFalse();
        } finally {
            pool.shutdownNow();
        }
    }

    /** 子线程移动任务：绑定租户/操作者上下文（ThreadLocal），异常按对象返回供断言。 */
    private Callable<Object> contextualizedMove(CountDownLatch start, Long id, Long newParent, String tag) {
        return () -> {
            TenantContextHolder.setTenantId(TENANT);
            AccessRequestContext.bind(RequestContext.user(TENANT, OPERATOR));
            try {
                start.await();
                menuWriteAppService.updateMenu(new MenuUpdateReq(
                    id, null, null, newParent, null, null, null, null, null, null));
                return "ok";
            } catch (Exception e) {
                return e;
            } finally {
                AccessRequestContext.clear();
                TenantContextHolder.clear();
            }
        };
    }

    @Test
    @DisplayName("环检测订正 SQL（runbook 同源）定位 2-环的全部环节点")
    void cycleDetectionSqlLocatesCycleNodes() {
        Long[] menuIds = seedMenuCycle();

        List<Long> cycleNodes = jdbcTemplate.queryForList(
            MENU_CYCLE_DETECTION_SQL, Long.class, TENANT);

        assertThat(cycleNodes).containsExactlyInAnyOrder(menuIds);
    }
}
