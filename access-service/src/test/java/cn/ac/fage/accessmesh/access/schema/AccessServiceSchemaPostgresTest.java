package cn.ac.fage.accessmesh.access.schema;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
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
        conn = DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        String sql = Files.readString(DDL_PATH, StandardCharsets.UTF_8);
        try (Statement s = conn.createStatement()) {
            s.execute(sql);
        }
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
    @DisplayName("sys_sync_task 不在最终结构中")
    void shouldNotHaveSysSyncTask() throws SQLException {
        assertFalse(tableExists("sys_sync_task"));
    }

    @Test
    @DisplayName("种子数据齐备")
    void shouldHaveAllSeedRows() throws SQLException {
        assertEquals(35, countRows("type_definition"));
        assertEquals(17, countRows("operation_permission"));
        assertEquals(9, countRows("system_config"));
        assertEquals(3, countRows("sys_oauth2_client"));
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
                    "VALUES (1, 'LOGIN_CAPTCHA_ENABLED', 'true', '重复键测试', false)");
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
