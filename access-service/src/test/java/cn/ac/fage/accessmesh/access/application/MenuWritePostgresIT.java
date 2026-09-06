package cn.ac.fage.accessmesh.access.application;

import cn.ac.fage.accessmesh.access.admin.dto.req.MenuCreateReq;
import cn.ac.fage.accessmesh.access.admin.dto.req.MenuUpdateReq;
import cn.ac.fage.accessmesh.access.admin.enums.AdminErrorCode;
import cn.ac.fage.accessmesh.access.admin.security.AdminPermissionValidator;
import cn.ac.fage.accessmesh.access.admin.service.domain.MenuDomainService;
import cn.ac.fage.accessmesh.access.application.query.mapper.UserMenuQueryMapper;
import cn.ac.fage.accessmesh.access.application.query.projection.MenuProjection;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder;
import cn.ac.fage.accessmesh.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

/**
 * 菜单 CRUD 写链路真实 PostgreSQL 验证（T-ACCESS-015，Testcontainers，
 * Docker 可用时执行）。
 * <p>
 * 原样执行权威 DDL（docs/design/schema/access-service.sql，v3.5 菜单零权限化
 * 终态：display_name / menu_type 五值枚举 / resource link，无 perm_code /
 * component / visible 列），验证：
 * </p>
 * <ul>
 *   <li>create/update/delete 全链路落库（sys_menu 新列 + MENU 投影 + change_log）</li>
 *   <li>uk_sys_menu_tenant_path / uk_sys_menu_tenant_resource 唯一索引冲突路径
 *       （预查报错 + 并发窗口兜底按约束名映射 10205/10206）</li>
 *   <li>写链路落的行可被读链路（UserMenuQueryMapper.selectMenus）正确消费</li>
 *   <li>软删后唯一索引释放（delete_flag 填本行 id，部分索引不再命中）</li>
 * </ul>
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
    "JWT_SECRET_KEY=test-jwt-secret-for-menu-write",
    "ACCESSMESH_SIGNATURE_SECRET=test-signature-secret-for-menu-write",
    "PERM_INTERNAL_SECRET=test-internal-secret-for-menu-write"
})
class MenuWritePostgresIT {

    private static final Long TENANT = 1L;
    private static final Long OPERATOR = 9L;
    private static final Path DDL_PATH = Path.of("..", "docs", "design", "schema", "access-service.sql");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("menu_write_test")
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
        // 原样执行权威 DDL + 种子数据（type_definition MENU=1 供投影解析）
        String sql = Files.readString(DDL_PATH, StandardCharsets.UTF_8);
        try (var conn = java.sql.DriverManager.getConnection(
            urlWithStringtype(), postgres.getUsername(), postgres.getPassword());
             var st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    @Autowired
    private MenuWriteAppService menuWriteAppService;

    @Autowired
    private UserMenuQueryMapper userMenuQueryMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 门禁 mock（void 方法默认通过）：写链路验证不依赖操作者权限数据。 */
    @MockBean
    private AdminPermissionValidator permissionValidator;

    /** 领域层 spy：并发窗口用例让唯一性预查失效，其余真实。 */
    @SpyBean
    private MenuDomainService menuDomainService;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT);
        AccessRequestContext.bind(RequestContext.user(TENANT, OPERATOR));
        // 清理上个用例残留（成功用例会真实落库）
        jdbcTemplate.execute("DELETE FROM permission_change_log WHERE tenant_id = " + TENANT);
        jdbcTemplate.execute("DELETE FROM sys_menu WHERE tenant_id = " + TENANT);
        jdbcTemplate.execute("DELETE FROM resource_entity WHERE tenant_id = " + TENANT
            + " AND resource_type = 1");
    }

    @AfterEach
    void tearDown() {
        AccessRequestContext.clear();
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("创建：新列落库（display_name/menu_type/resource link/source_service）+ MENU 投影 + change_log")
    void createPersistsTerminalColumnsAndProjection() {
        Long id = menuWriteAppService.createMenu(new MenuCreateReq(
            "MENU", "用户管理", 0L, "/system/user", "user-icon", 5, 1,
            "USER", "5", null));

        assertThat(id).isNotNull();
        var row = jdbcTemplate.queryForMap(
            "SELECT display_name, menu_type, path, icon, sort_order, status,"
                + " resource_type, resource_code, source_service, delete_flag"
                + " FROM sys_menu WHERE id = ?", id);
        assertThat(row.get("display_name")).isEqualTo("用户管理");
        assertThat(row.get("menu_type")).isEqualTo("MENU");
        assertThat(row.get("path")).isEqualTo("/system/user");
        assertThat(row.get("resource_type")).isEqualTo("USER");
        assertThat(row.get("resource_code")).isEqualTo("5");
        assertThat(row.get("source_service")).isEqualTo("access-service"); // 缺省值
        assertThat(((Number) row.get("delete_flag")).longValue()).isZero();

        // MENU 投影（resource_type=1，external_id=sys_menu.id）与 change_log
        Integer projections = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM resource_entity WHERE tenant_id = ? AND resource_type = 1"
                + " AND code = ? AND delete_flag = 0", Integer.class, TENANT, String.valueOf(id));
        assertThat(projections).isEqualTo(1);
        Integer changeLogs = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM permission_change_log WHERE tenant_id = ?", Integer.class, TENANT);
        assertThat(changeLogs).isEqualTo(1);
    }

    @Test
    @DisplayName("创建：HIDDEN 类型同样维护投影（五值枚举无 BUTTON 短路）")
    void createHiddenMenuAlsoProjects() {
        Long id = menuWriteAppService.createMenu(new MenuCreateReq(
            "HIDDEN", "隐藏路由", null, "/hidden/detail", null, null, null, null, null, null));

        Integer projections = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM resource_entity WHERE tenant_id = ? AND resource_type = 1"
                + " AND code = ? AND delete_flag = 0", Integer.class, TENANT, String.valueOf(id));
        assertThat(projections).isEqualTo(1);
    }

    @Test
    @DisplayName("唯一冲突-预查：同租户同 path 二次创建抛 MENU_PATH_EXISTS(10205)")
    void createDuplicatePathRejectedByPrecheck() {
        menuWriteAppService.createMenu(new MenuCreateReq(
            "MENU", "菜单A", null, "/dup/path", null, null, null, null, null, null));

        assertThatThrownBy(() -> menuWriteAppService.createMenu(new MenuCreateReq(
            "MENU", "菜单B", null, "/dup/path", null, null, null, null, null, null)))
            .isInstanceOf(BizException.class)
            .extracting(e -> ((BizException) e).getErrorCode())
            .isEqualTo(AdminErrorCode.MENU_PATH_EXISTS.getCode());
        // 第二条不落库
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_menu WHERE tenant_id = ? AND path = '/dup/path'",
            Integer.class, TENANT);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("唯一冲突-预查：同资源关联二次挂菜单抛 MENU_RESOURCE_EXISTS(10206)")
    void createDuplicateResourceRejectedByPrecheck() {
        menuWriteAppService.createMenu(new MenuCreateReq(
            "MENU", "菜单A", null, "/a", null, null, null, "ORG", "7", null));

        assertThatThrownBy(() -> menuWriteAppService.createMenu(new MenuCreateReq(
            "MENU", "菜单B", null, "/b", null, null, null, "ORG", "7", null)))
            .isInstanceOf(BizException.class)
            .extracting(e -> ((BizException) e).getErrorCode())
            .isEqualTo(AdminErrorCode.MENU_RESOURCE_EXISTS.getCode());
    }

    @Test
    @DisplayName("唯一冲突-并发窗口兜底：预查失效时唯一索引拦截并按约束名映射 10205")
    void concurrentPathConflictMappedByIndexName() {
        menuWriteAppService.createMenu(new MenuCreateReq(
            "MENU", "菜单A", null, "/race/path", null, null, null, null, null, null));

        // 模拟并发窗口：预查恒 false，insert 撞 uk_sys_menu_tenant_path → 约束名映射
        doReturn(false).when(menuDomainService).pathExists(anyLong(), anyString(), any());

        assertThatThrownBy(() -> menuWriteAppService.createMenu(new MenuCreateReq(
            "MENU", "菜单B", null, "/race/path", null, null, null, null, null, null)))
            .isInstanceOf(BizException.class)
            .extracting(e -> ((BizException) e).getErrorCode())
            .isEqualTo(AdminErrorCode.MENU_PATH_EXISTS.getCode());

        // 事务回滚：第二条不落库
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_menu WHERE tenant_id = ? AND path = '/race/path'",
            Integer.class, TENANT);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("唯一冲突-并发窗口兜底：资源关联撞 uk_sys_menu_tenant_resource 映射 10206")
    void concurrentResourceConflictMappedByIndexName() {
        menuWriteAppService.createMenu(new MenuCreateReq(
            "MENU", "菜单A", null, "/race/a", null, null, null, "ORG", "7", null));

        // 预查失效（path 与 resource 均不拦截），resource 撞唯一索引
        doReturn(false).when(menuDomainService).pathExists(anyLong(), anyString(), any());
        doReturn(false).when(menuDomainService).resourceExists(anyLong(), anyString(), anyString(), any());

        assertThatThrownBy(() -> menuWriteAppService.createMenu(new MenuCreateReq(
            "MENU", "菜单B", null, "/race/b", null, null, null, "ORG", "7", null)))
            .isInstanceOf(BizException.class)
            .extracting(e -> ((BizException) e).getErrorCode())
            .isEqualTo(AdminErrorCode.MENU_RESOURCE_EXISTS.getCode());
    }

    @Test
    @DisplayName("更新：部分字段更新落库 + 投影同步刷新（display_name/status）")
    void updatePersistsAndSyncsProjection() {
        Long id = menuWriteAppService.createMenu(new MenuCreateReq(
            "MENU", "旧名称", null, "/upd", null, 3, 1, null, null, null));

        menuWriteAppService.updateMenu(new MenuUpdateReq(
            id, null, "新名称", null, null, null, 9, 0, null, null));

        var row = jdbcTemplate.queryForMap(
            "SELECT display_name, sort_order, status, path FROM sys_menu WHERE id = ?", id);
        assertThat(row.get("display_name")).isEqualTo("新名称");
        assertThat(((Number) row.get("sort_order")).intValue()).isEqualTo(9);
        assertThat(((Number) row.get("status")).intValue()).isZero(); // DISABLED
        assertThat(row.get("path")).isEqualTo("/upd"); // 未提供 → 保留

        // 投影同步：name 刷新、status 置 DISABLED
        var projection = jdbcTemplate.queryForMap(
            "SELECT name, status FROM resource_entity WHERE tenant_id = ? AND resource_type = 1"
                + " AND code = ? AND delete_flag = 0", TENANT, String.valueOf(id));
        assertThat(projection.get("name")).isEqualTo("新名称");
        assertThat(((Number) projection.get("status")).intValue()).isZero();
    }

    @Test
    @DisplayName("删除：sys_menu 软删（delete_flag=id）+ 投影软删 + 软删后 path 唯一性释放")
    void deleteSoftDeletesAndReleasesUniqueness() {
        Long id = menuWriteAppService.createMenu(new MenuCreateReq(
            "EXTERNAL", "外链菜单", null, "/del/path", null, null, null, null, null, null));

        menuWriteAppService.deleteMenu(id);

        var row = jdbcTemplate.queryForMap(
            "SELECT delete_flag, deleted_at FROM sys_menu WHERE id = ?", id);
        assertThat(((Number) row.get("delete_flag")).longValue()).isEqualTo(id);
        assertThat(row.get("deleted_at")).isNotNull();
        Integer activeProjections = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM resource_entity WHERE tenant_id = ? AND resource_type = 1"
                + " AND code = ? AND delete_flag = 0", Integer.class, TENANT, String.valueOf(id));
        assertThat(activeProjections).isZero();

        // 部分唯一索引（WHERE delete_flag = 0）：软删后同 path 可重新创建
        Long recreated = menuWriteAppService.createMenu(new MenuCreateReq(
            "MENU", "复用路径", null, "/del/path", null, null, null, null, null, null));
        assertThat(recreated).isNotNull();
    }

    @Test
    @DisplayName("读链路消费回归：写链路落的行经 UserMenuQueryMapper.selectMenus 正确映射")
    void writePathConsumedByUserMenuQueryMapper() {
        menuWriteAppService.createMenu(new MenuCreateReq(
            "DIR", "系统管理", 0L, "/system", "sys-icon", 1, 1, null, null, null));
        menuWriteAppService.createMenu(new MenuCreateReq(
            "MENU", "用户管理", 0L, "/system/user", null, 2, 1,
            "USER", "5", null));

        List<MenuProjection> menus = userMenuQueryMapper.selectMenus(TENANT);

        assertThat(menus).hasSize(2);
        MenuProjection dir = menus.stream()
            .filter(m -> "DIR".equals(m.menuType())).findFirst().orElseThrow();
        assertThat(dir.name()).isEqualTo("系统管理");
        assertThat(dir.icon()).isEqualTo("sys-icon");
        assertThat(((Number) dir.sortOrder()).intValue()).isEqualTo(1);
        MenuProjection biz = menus.stream()
            .filter(m -> "MENU".equals(m.menuType())).findFirst().orElseThrow();
        assertThat(biz.name()).isEqualTo("用户管理");
        assertThat(biz.resourceType()).isEqualTo("USER");
        assertThat(biz.resourceCode()).isEqualTo("5");
        assertThat(((Number) biz.status()).intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("空白规范化：两条菜单 path 均传空串 → 落库 NULL（部分唯一索引不命中，互不冲突）")
    void blankPathNormalizedToNullDoesNotConflict() {
        Long idA = menuWriteAppService.createMenu(new MenuCreateReq(
            "MENU", "菜单A", null, "  ", null, null, null, "", "", null));
        Long idB = menuWriteAppService.createMenu(new MenuCreateReq(
            "MENU", "菜单B", null, "", null, null, null, "", "", null));

        Integer nullPaths = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_menu WHERE tenant_id = ? AND id IN (?, ?) AND path IS NULL",
            Integer.class, TENANT, idA, idB);
        assertThat(nullPaths).isEqualTo(2);
        Integer nullResourceTypes = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_menu WHERE tenant_id = ? AND id IN (?, ?)"
                + " AND resource_type IS NULL AND resource_code IS NULL",
            Integer.class, TENANT, idA, idB);
        assertThat(nullResourceTypes).isEqualTo(2);
    }

    @Test
    @DisplayName("父校验：正数 parentId 不存在 → 拒绝（10201，无孤儿节点写入）")
    void nonexistentParentRejected() {
        assertThatThrownBy(() -> menuWriteAppService.createMenu(new MenuCreateReq(
            "MENU", "孤儿", 99999L, "/orphan", null, null, null, null, null, null)))
            .isInstanceOf(BizException.class)
            .extracting(e -> ((BizException) e).getErrorCode())
            .isEqualTo(AdminErrorCode.MENU_NOT_FOUND.getCode());
        Integer orphans = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_menu WHERE tenant_id = ? AND parent_id = 99999",
            Integer.class, TENANT);
        assertThat(orphans).isZero();
    }

    @Test
    @DisplayName("换父防环：目标父为自身（10207）；树未成环，祖先链查询仍可收敛")
    void moveToSelfRejected() {
        Long id = menuWriteAppService.createMenu(new MenuCreateReq(
            "MENU", "菜单", null, "/cycle/self", null, null, null, null, null, null));

        assertThatThrownBy(() -> menuWriteAppService.updateMenu(
            new MenuUpdateReq(id, null, null, id, null, null, null, null, null, null)))
            .isInstanceOf(BizException.class)
            .extracting(e -> ((BizException) e).getErrorCode())
            .isEqualTo(AdminErrorCode.MENU_PARENT_INVALID.getCode());
        // parent 未被改写（仍为根级 0）
        Long parent = jdbcTemplate.queryForObject(
            "SELECT parent_id FROM sys_menu WHERE id = ?", Long.class, id);
        assertThat(parent).isZero();
    }

    @Test
    @DisplayName("换父防环：目标父为后代（10207），父孙互挂被拒")
    void moveToDescendantRejected() {
        Long parent = menuWriteAppService.createMenu(new MenuCreateReq(
            "DIR", "父", null, "/cycle/p", null, null, null, null, null, null));
        Long child = menuWriteAppService.createMenu(new MenuCreateReq(
            "MENU", "子", parent, "/cycle/c", null, null, null, null, null, null));

        assertThatThrownBy(() -> menuWriteAppService.updateMenu(
            new MenuUpdateReq(parent, null, null, child, null, null, null, null, null, null)))
            .isInstanceOf(BizException.class)
            .extracting(e -> ((BizException) e).getErrorCode())
            .isEqualTo(AdminErrorCode.MENU_PARENT_INVALID.getCode());
    }

    @Test
    @DisplayName("换父子树边界：带一层子菜单（高度2）移到第 4 层父下 → 子将达第 6 层，拒绝 10203")
    void moveSubtreeBreakingDepthLimitRejected() {
        // 四层链 lv1→lv2→lv3→lv4，待移动子树 root(带一个子)
        Long lv1 = menuWriteAppService.createMenu(new MenuCreateReq(
            "DIR", "lv1", null, "/mv/1", null, null, null, null, null, null));
        Long lv2 = menuWriteAppService.createMenu(new MenuCreateReq(
            "DIR", "lv2", lv1, "/mv/2", null, null, null, null, null, null));
        Long lv3 = menuWriteAppService.createMenu(new MenuCreateReq(
            "DIR", "lv3", lv2, "/mv/3", null, null, null, null, null, null));
        Long lv4 = menuWriteAppService.createMenu(new MenuCreateReq(
            "MENU", "lv4", lv3, "/mv/4", null, null, null, null, null, null));
        Long root = menuWriteAppService.createMenu(new MenuCreateReq(
            "DIR", "root", null, "/mv/r", null, null, null, null, null, null));
        menuWriteAppService.createMenu(new MenuCreateReq(
            "MENU", "leaf", root, "/mv/l", null, null, null, null, null, null));

        // root（高度 2）移到 lv4（深度 4）下：子将达 4+2=6 层 → 拒绝
        assertThatThrownBy(() -> menuWriteAppService.updateMenu(
            new MenuUpdateReq(root, null, null, lv4, null, null, null, null, null, null)))
            .isInstanceOf(BizException.class)
            .extracting(e -> ((BizException) e).getErrorCode())
            .isEqualTo(AdminErrorCode.MENU_DEPTH_EXCEEDED.getCode());

        // 移到 lv3（深度 3）下：子恰为第 5 层 → 允许
        menuWriteAppService.updateMenu(
            new MenuUpdateReq(root, null, null, lv3, null, null, null, null, null, null));
        var rootRow = jdbcTemplate.queryForMap(
            "SELECT parent_id FROM sys_menu WHERE id = ?", root);
        assertThat(rootRow.get("parent_id")).isEqualTo(lv3);
    }

    @Test
    @DisplayName("换父子树边界：高度 5 子树下挂第 1 层父拒绝（10203）；脏数据挂深后移回顶级放行（父深度按 0）")
    void moveFullHeightSubtreeTopLevelBoundary() {
        // s1..s5 顶级链（合法，s1 子树高度 5，s5 在第 5 层）+ 顶级节点 tmp
        Long s1 = menuWriteAppService.createMenu(new MenuCreateReq(
            "DIR", "s1", null, "/mv2/1", null, null, null, null, null, null));
        Long prev = s1;
        for (int i = 2; i <= 5; i++) {
            prev = menuWriteAppService.createMenu(new MenuCreateReq(
                "MENU", "s" + i, prev, "/mv2/" + i, null, null, null, null, null, null));
        }
        Long tmp = menuWriteAppService.createMenu(new MenuCreateReq(
            "DIR", "tmp", null, "/mv2/t", null, null, null, null, null, null));

        // s1（高度 5）移到 tmp（第 1 层）下：1 + 5 = 6 层 → 拒绝
        assertThatThrownBy(() -> menuWriteAppService.updateMenu(
            new MenuUpdateReq(s1, null, null, tmp, null, null, null, null, null, null)))
            .isInstanceOf(BizException.class)
            .extracting(e -> ((BizException) e).getErrorCode())
            .isEqualTo(AdminErrorCode.MENU_DEPTH_EXCEEDED.getCode());

        // 模拟脏数据（绕过 API 直改 parent）：s1 挂到 tmp 下，s5 暂处第 6 层
        jdbcTemplate.update("UPDATE sys_menu SET parent_id = ? WHERE id = ? AND tenant_id = ?",
            tmp, s1, TENANT);
        // 通过 API 移回顶级：父深度按 0，0 + 5 = 5 ≤ 5 → 放行（修复后 s5 回到第 5 层）
        menuWriteAppService.updateMenu(
            new MenuUpdateReq(s1, null, null, 0L, null, null, null, null, null, null));
        var row = jdbcTemplate.queryForMap("SELECT parent_id FROM sys_menu WHERE id = ?", s1);
        assertThat(row.get("parent_id")).isEqualTo(0L);
    }

    @Test
    @DisplayName("深度限制：第 6 层创建被拒（MENU_DEPTH_EXCEEDED 10203）")
    void depthLimitEnforced() {
        Long parent = null;
        for (int depth = 0; depth < 5; depth++) {
            parent = menuWriteAppService.createMenu(new MenuCreateReq(
                "DIR", "层级" + depth, parent, "/lv" + depth, null, null, null, null, null, null));
        }
        final Long fifthLevelParent = parent;
        assertThatThrownBy(() -> menuWriteAppService.createMenu(new MenuCreateReq(
            "MENU", "第6层", fifthLevelParent, "/lv5", null, null, null, null, null, null)))
            .isInstanceOf(BizException.class)
            .extracting(e -> ((BizException) e).getErrorCode())
            .isEqualTo(AdminErrorCode.MENU_DEPTH_EXCEEDED.getCode());
    }
}
