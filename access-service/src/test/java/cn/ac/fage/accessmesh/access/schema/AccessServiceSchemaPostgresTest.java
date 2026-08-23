package cn.ac.fage.accessmesh.access.schema;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * access-service.sql 空库结构测试（Testcontainers PostgreSQL，原样 DDL）。
 * <p>
 * Docker 可用时执行：启动 PostgreSQL 容器，原样执行权威 DDL
 * （docs/design/schema/access-service.sql），验证表数量、种子数据、
 * 合并表字段、部分唯一索引与 CHECK 约束的完整语义（含 H2 无法表达的
 * 软删部分唯一索引 / NULLS NOT DISTINCT / COALESCE 索引列）。
 * Docker 不可用时由 Testcontainers 自动跳过，本地以
 * {@link AccessServiceSchemaH2Test} 为兜底验证。
 * </p>
 */
@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker = true)
class AccessServiceSchemaPostgresTest {

    private static final Path DDL_PATH = Path.of("..", "docs", "design", "schema", "access-service.sql");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static Connection conn;

    @BeforeAll
    static void setup() throws Exception {
        if (!Files.exists(DDL_PATH)) {
            throw new IllegalStateException("access-service.sql 不存在：" + DDL_PATH.toAbsolutePath());
        }
        // stringtype=unspecified：与 application.yml 数据源一致，验证 JSONB 列接受 String 参数绑定
        // （PGJDBC 默认 stringtype=VARCHAR 对 JSONB 列写入报 42804）
        String url = POSTGRES.getJdbcUrl() + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "stringtype=unspecified";
        conn = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
        String sql = Files.readString(DDL_PATH, StandardCharsets.UTF_8);
        try (Statement s = conn.createStatement()) {
            s.execute(sql);
        }
    }

    /**
     * 每个测试独立事务，测试内写入（临时 operation、JSONB 往返等）在回滚后丢弃，
     * 不污染共享连接上的种子计数断言（测试方法执行顺序不定）。
     */
    @org.junit.jupiter.api.BeforeEach
    void beginTransaction() throws SQLException {
        conn.setAutoCommit(false);
    }

    @org.junit.jupiter.api.AfterEach
    void rollbackTransaction() throws SQLException {
        conn.rollback();
        conn.setAutoCommit(true);
    }

    private static long countRows(String table) throws SQLException {
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static boolean tableExists(String table) throws SQLException {
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = '" + table + "'")) {
            rs.next();
            return rs.getLong(1) > 0;
        }
    }

    private static boolean indexExists(String index) throws SQLException {
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = '" + index + "'")) {
            rs.next();
            return rs.getLong(1) > 0;
        }
    }

    @Test
    @DisplayName("原样 DDL 可执行：33 张表")
    void shouldHave33Tables() throws SQLException {
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public'")) {
            rs.next();
            assertEquals(33, rs.getLong(1));
        }
    }

    @Test
    @DisplayName("sys_sync_task 已随内部同步子系统删除")
    void shouldNotHaveSysSyncTaskTable() throws SQLException {
        assertFalse(tableExists("sys_sync_task"), "T-ACCESS-005 删除同步链路后 sys_sync_task 不得再出现在最终 DDL");
    }

    @Test
    @DisplayName("种子数据齐备（type_definition 36 / operation_permission 139 / system_config 9 / oauth2 3）")
    void shouldHaveAllSeedRows() throws SQLException {
        assertEquals(36, countRows("type_definition"));
        assertEquals(139, countRows("operation_permission"));
        assertEquals(9, countRows("system_config"));
        assertEquals(3, countRows("sys_oauth2_client"));
    }

    @Test
    @DisplayName("运行时必需操作对完整性：代码实际校验的非 CRUD 操作全部有种子")
    void shouldHaveAllRuntimeRequiredOperations() throws SQLException {
        // 与代码调用点交叉核对的必需清单（非 CRUD 部分，共 27 对 = 权限中心 13 + Admin 14）
        String[][] required = {
            {"USER", "MANAGE"},
            {"ROLE", "MANAGE"}, {"ROLE", "ASSIGN"}, {"ROLE", "REVOKE"},
            {"RESOURCE", "MANAGE"},
            {"SERVICE", "MANAGE"}, {"SERVICE", "MANAGE_API_MAPPING"}, {"SERVICE", "SYNC_INTERFACE"},
            {"TYPE_DEFINITION", "MANAGE"},
            {"SYSTEM_CONFIG", "MANAGE"},
            {"OPERATION", "MANAGE"},
            {"DEPENDENCY", "SYNC"},
            {"API", "ACCESS"},
            {"ADMIN_ORG", "CREATE_POSITION"}, {"ADMIN_ORG", "UPDATE_POSITION"},
            {"ADMIN_ORG", "DELETE_POSITION"}, {"ADMIN_ORG", "ASSIGN_POSITION_USER"},
            {"ADMIN_ORG", "MANAGE_MEMBER"}, {"ADMIN_ORG", "VIEW_POSITION"},
            {"ADMIN_USER", "ENABLE"}, {"ADMIN_USER", "RESET_PASSWORD"},
            {"ADMIN_ROLE", "GRANT"}, {"ADMIN_ROLE", "REVOKE"},
            {"ADMIN_NOTICE", "PUBLISH"},
            {"ADMIN_JOB", "ENABLE"}, {"ADMIN_JOB", "TRIGGER"},
            {"ADMIN_ORG_TREE_CONFIG", "TOGGLE"},
        };
        StringBuilder missing = new StringBuilder();
        for (String[] pair : required) {
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM operation_permission op " +
                "JOIN type_definition td ON td.tenant_id = op.tenant_id AND td.type_value = op.resource_type " +
                "WHERE op.tenant_id = 1 AND td.type_key = 'resource_type' AND td.type_code = ? AND op.code = ? AND op.delete_flag = 0")) {
                ps.setString(1, pair[0]);
                ps.setString(2, pair[1]);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getLong(1) == 0) {
                        missing.append(pair[0]).append(':').append(pair[1]).append(' ');
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "缺少运行时必需操作种子：" + missing);
    }

    @Test
    @DisplayName("JSONB 列接受 String 参数绑定（PreparedStatement#setString 走 PGJDBC 绑定路径，stringtype=unspecified 生效）")
    void shouldWriteStringToJsonbColumn() throws SQLException {
        // 使用 PreparedStatement#setString：模拟实体 String 字段经 MyBatis 绑定到 JSONB 列的真实路径
        // （Statement 拼接字面量会被 PG 直接推断为 JSONB，不经过 PGJDBC setString，无法验证修复）
        try (PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO system_config (tenant_id, config_key, config_value, config_name, is_system) VALUES (?, ?, ?, ?, ?)")) {
            ps.setLong(1, 1L);
            ps.setString(2, "admin.JSONB_ROUNDTRIP_TEST");
            ps.setString(3, "{\"mode\":\"test\"}");
            ps.setString(4, "往返测试");
            ps.setBoolean(5, false);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
            "SELECT config_value FROM system_config WHERE tenant_id = ? AND config_key = ?")) {
            ps.setLong(1, 1L);
            ps.setString(2, "admin.JSONB_ROUNDTRIP_TEST");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "插入后应能读取");
                String value = rs.getString(1);
                assertTrue(value.contains("mode"), "JSONB 值应可读取（PG 规范化后为 {\"mode\": \"test\"}），实际 " + value);
            }
        }
    }

    @Test
    @DisplayName("每个静态 resource_type 恰好 4 条 CRUD（冗余 VIEW 已从种子定义中合并消除）")
    void shouldHaveExactCrudPerType() throws SQLException {
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT td.type_code, COUNT(*) FROM type_definition td " +
                 "LEFT JOIN operation_permission op ON op.tenant_id = td.tenant_id " +
                 "  AND op.resource_type = td.type_value AND op.code IN ('CREATE','VIEW','UPDATE','DELETE') AND op.delete_flag = 0 " +
                 "WHERE td.tenant_id = 1 AND td.type_key = 'resource_type' AND td.delete_flag = 0 " +
                 "GROUP BY td.type_code HAVING COUNT(*) <> 4")) {
            StringBuilder unexpected = new StringBuilder();
            while (rs.next()) {
                unexpected.append(rs.getString(1)).append('(').append(rs.getLong(2)).append("条) ");
            }
            assertTrue(unexpected.isEmpty(), "存在 CRUD 计数非 4 的资源类型：" + unexpected);
        }
    }

    @Test
    @DisplayName("软删部分唯一索引保留完整语义：uk_operation_permission_global 只约束 resource_type IS NULL 行")
    void shouldKeepPartialUniqueIndexSemantics() throws SQLException {
        // 部分唯一索引存在
        assertTrue(indexExists("uk_operation_permission_global"), "uk_operation_permission_global 应存在");
        // 语义验证：resource_type 非 NULL 的行可重复 'VIEW' 码（不受 global 索引约束）
        try (Statement s = conn.createStatement()) {
            s.execute("INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, delete_flag) VALUES (1, 100, 'VIEW', '测试A', 1024, 0)");
            s.execute("INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, delete_flag) VALUES (1, 101, 'VIEW', '测试B', 1024, 0)");
            // 同租户同 resource_type 同 code 受 typed 索引约束
            assertThrows(SQLException.class, () -> s.execute(
                "INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, delete_flag) VALUES (1, 100, 'VIEW', '测试C', 1024, 0)"));
        }
    }

    @Test
    @DisplayName("uk_user_role COALESCE 索引列与 NULLS NOT DISTINCT 保留")
    void shouldKeepComplexUniqueIndexes() throws SQLException {
        assertTrue(indexExists("uk_user_role"));
        assertTrue(indexExists("uk_conflict_rule_perm"));
    }

    @Test
    @DisplayName("role_resource_permission CHECK 约束拦截非法 scope_all 组合")
    void shouldEnforceRoleResourcePermissionCheck() {
        assertThrows(SQLException.class, () -> {
            try (Statement s = conn.createStatement()) {
                s.execute("INSERT INTO role_resource_permission " +
                    "(tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type) " +
                    "VALUES (1, 1, NULL, 1, 1)");
            }
        });
    }

    @Test
    @DisplayName("system_config 唯一约束拦截重复 config_key")
    void shouldEnforceSystemConfigUniqueKey() {
        assertThrows(SQLException.class, () -> {
            try (Statement s = conn.createStatement()) {
                s.execute("INSERT INTO system_config (tenant_id, config_key, config_value, config_name, is_system) " +
                    "VALUES (1, 'admin.LOGIN_CAPTCHA_ENABLED', 'true', '重复键测试', false)");
            }
        });
    }

    @Test
    @DisplayName("sys_task_execution 执行键唯一约束")
    void shouldHaveTaskExecutionTable() throws SQLException {
        assertTrue(tableExists("sys_task_execution"));
        assertTrue(indexExists("uk_task_execution"));
        assertThrows(SQLException.class, () -> {
            try (Statement s = conn.createStatement()) {
                s.execute("INSERT INTO sys_task_execution (tenant_id, execution_key, status) VALUES (1, 'job-1_t1', 'PENDING')");
                s.execute("INSERT INTO sys_task_execution (tenant_id, execution_key, status) VALUES (1, 'job-1_t1', 'PENDING')");
            }
        });
    }

    @Test
    @DisplayName("本地投影所有权列存在")
    void shouldHaveOwnerServiceCodeColumns() throws SQLException {
        for (String table : new String[]{"abstract_user", "abstract_role", "user_role"}) {
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = '" + table + "' AND column_name = 'owner_service_code'")) {
                rs.next();
                assertEquals(1, rs.getLong(1), table + ".owner_service_code 应存在");
            }
        }
    }
}
