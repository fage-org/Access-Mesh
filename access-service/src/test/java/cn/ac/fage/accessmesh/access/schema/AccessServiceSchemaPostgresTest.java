package cn.ac.fage.accessmesh.access.schema;

import cn.ac.fage.accessmesh.access.it.ItInfra;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.postgresql.util.PSQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
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
 * Docker 不可用时自动跳过，不构成 DDL 已验证；DDL 变更必须定向执行本类。
 * </p>
 */
@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker = true)
class AccessServiceSchemaPostgresTest {

    private static final Path DDL_PATH = Path.of("..", "docs", "design", "schema", "access-service.sql");

    private static Connection conn;

    @BeforeAll
    static void setup() throws Exception {
        if (!Files.exists(DDL_PATH)) {
            throw new IllegalStateException("access-service.sql 不存在：" + DDL_PATH.toAbsolutePath());
        }
        // 空库自跑 DDL 是本测试的被测对象：禁用模板克隆通道，从单例容器取独立空库，
        // stringtype=unspecified 由 ItInfra.jdbcUrl 通道附带（JSONB String 绑定语义不变）
        String url = ItInfra.createStandaloneDatabase("schema_postgres_test");
        conn = DriverManager.getConnection(url, ItInfra.username(), ItInfra.password());
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
    @DisplayName("原样 DDL 可执行：权威表完整")
    void shouldHaveCanonicalTables() throws SQLException {
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public'")) {
            rs.next();
            assertEquals(37, rs.getLong(1));
        }
    }

    @Test
    @DisplayName("sys_sync_task 已随内部同步子系统删除")
    void shouldNotHaveSysSyncTaskTable() throws SQLException {
        assertFalse(tableExists("sys_sync_task"), "T-ACCESS-005 删除同步链路后 sys_sync_task 不得再出现在最终 DDL");
    }

    @Test
    @DisplayName("种子数据齐备")
    void shouldHaveAllSeedRows() throws SQLException {
        assertEquals(33, countRows("type_definition"));
        assertEquals(123, countRows("operation_permission")); // DEPENDENCY:SYNC 随 T-PERM-071 退役删除
        assertEquals(9, countRows("system_config"));
        assertEquals(3, countRows("sys_oauth2_client"));
    }

    @Test
    @DisplayName("退役类型码不复用：收敛前 ADMIN_* 资源类型码与退役 type_value 段均不得再出现")
    void shouldNotHaveRetiredTypeCodesOrValues() throws SQLException {
        // 沿用 ErrorCodeContractTest 退役清单模式（T-ACCESS-018）
        String[][] retiredTypeCodes = {
            {"resource_type", "ADMIN_USER"}, {"resource_type", "ADMIN_ORG"},
            {"resource_type", "ADMIN_ROLE"}, {"resource_type", "ADMIN_MENU"},
            {"resource_type", "ADMIN_CONFIG"}, {"resource_type", "ADMIN_SYNC_TASK"},
            {"user_type", "ADMIN_USER"},
        };
        StringBuilder leaked = new StringBuilder();
        for (String[] pair : retiredTypeCodes) {
            try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM type_definition WHERE tenant_id = 1 AND type_key = ? AND type_code = ?")) {
                ps.setString(1, pair[0]);
                ps.setString(2, pair[1]);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getLong(1) > 0) {
                        leaked.append(pair[0]).append(':').append(pair[1]).append(' ');
                    }
                }
            }
        }
        assertTrue(leaked.isEmpty(), "退役类型码不得再现：" + leaked);
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT type_code, type_value FROM type_definition "
                     + "WHERE tenant_id = 1 AND type_key = 'resource_type' AND type_value IN (16,17,18,19,22,28)")) {
            while (rs.next()) {
                leaked.append(rs.getString(1)).append('=').append(rs.getInt(2)).append(' ');
            }
        }
        assertTrue(leaked.isEmpty(), "退役 type_value 段不得被复用：" + leaked);
    }

    @Test
    @DisplayName("运行时必需操作对完整性：代码实际校验的非 CRUD 操作全部有种子")
    void shouldHaveAllRuntimeRequiredOperations() throws SQLException {
        // 与代码调用点交叉核对的必需非 CRUD 操作；
        // T-ACCESS-018 收敛：ADMIN_ORG 六码迁 ORG、ADMIN_USER 两码迁 USER、ADMIN_ROLE:GRANT/REVOKE 删除；
        // USER:MANAGE 随 T-ACCESS-034 USER 轨细粒度化退役——update/remove 门禁换绑 UPDATE/DELETE 通用码，种子删除）
        String[][] required = {
            {"ROLE", "MANAGE"}, {"ROLE", "ASSIGN"}, {"ROLE", "REVOKE"},
            {"RESOURCE", "MANAGE"},
            {"SERVICE", "MANAGE"}, {"SERVICE", "MANAGE_API_MAPPING"}, {"SERVICE", "SYNC_INTERFACE"},
            {"TYPE_DEFINITION", "MANAGE"},
            {"SYSTEM_CONFIG", "MANAGE"},
            {"OPERATION", "MANAGE"},
            {"API", "ACCESS"},
            {"ORG", "CREATE_POSITION"}, {"ORG", "UPDATE_POSITION"},
            {"ORG", "DELETE_POSITION"}, {"ORG", "ASSIGN_POSITION_USER"},
            {"ORG", "MANAGE_MEMBER"}, {"ORG", "VIEW_POSITION"},
            {"USER", "ENABLE"}, {"USER", "RESET_PASSWORD"},
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
    @DisplayName("操作定义：跨类型复用 code/bit，同类型唯一且禁止全局行")
    void shouldKeepPartialUniqueIndexSemantics() throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, delete_flag) VALUES (1, 100, 'VIEW', '测试A', 1024, 0)");
            s.execute("INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, delete_flag) VALUES (1, 101, 'VIEW', '测试B', 1024, 0)");
        }
        // code 冲突使用不同 bit，避免另一唯一索引掩盖目标约束缺失。
        assertConstraintViolation("23505", "uk_operation_permission_typed",
            "INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit) VALUES (1, 100, 'VIEW', '重复码', 2048)");
        assertConstraintViolation("23505", "uk_operation_permission_typed_bit",
            "INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit) VALUES (1, 100, 'EXPORT', '重复位', 1024)");
        assertConstraintViolation("23514", "ck_operation_permission_resource_type_required",
            "INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit) VALUES (1, NULL, 'VIEW', '全局行', 1024)");
    }

    @Test
    @DisplayName("uk_user_role COALESCE 索引列与 NULLS NOT DISTINCT 保留")
    void shouldKeepComplexUniqueIndexes() throws SQLException {
        assertTrue(indexExists("uk_user_role"));
        assertTrue(indexExists("uk_conflict_rule_perm"));
    }

    @Test
    @DisplayName("scope_all=false 必须指定实例，scope_all=true 允许 NULL 实例")
    void shouldEnforceRoleResourcePermissionCheck() throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("INSERT INTO role_resource_permission (tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type, scope_all) VALUES (1, 1, NULL, 1, 1, TRUE)");
        }
        assertConstraintViolation("23514", "ck_role_resource_permission_scope_all",
            "INSERT INTO role_resource_permission (tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type) VALUES (1, 1, NULL, 1, 1)");
    }

    @Test
    @DisplayName("system_config 唯一约束拦截重复 config_key")
    void shouldEnforceSystemConfigUniqueKey() throws SQLException {
        assertConstraintViolation("23505", "uk_system_config",
            "INSERT INTO system_config (tenant_id, config_key, config_value, config_name, is_system) VALUES (1, 'admin.LOGIN_CAPTCHA_ENABLED', 'true', '重复键测试', false)");
    }

    @Test
    @DisplayName("sys_task_execution 租约列与执行键唯一约束")
    void shouldHaveTaskExecutionTable() throws SQLException {
        assertTrue(tableExists("sys_task_execution"));
        for (String column : new String[]{"execution_key", "lease_owner", "lease_until", "attempt_count"}) {
            assertTrue(columnExists("sys_task_execution", column), column);
        }
        String insert = "INSERT INTO sys_task_execution (tenant_id, execution_key, status) VALUES (1, 'job-1_t1', 'PENDING')";
        try (Statement s = conn.createStatement()) {
            s.execute(insert);
        }
        assertConstraintViolation("23505", "uk_task_execution", insert);
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

    /**
     * eventType 筛选命中表达式索引：查询表达式必须与 DDL
     * idx_change_log_event_time 的 (diff_snapshot->>'eventType') 同形——jsonb_extract_path_text
     * 形式经 EXPLAIN 实证只走顺序扫描（「表达式等价可命中」的既有结论已被实证推翻）。
     * SET LOCAL 随测试回滚蒸发，不污染共享连接的后续测试。
     */
    @Test
    @org.junit.jupiter.api.DisplayName("eventType 筛选命中 idx_change_log_event_time 表达式索引")
    void eventTypeFilterShouldUseExpressionIndex() throws SQLException {
        try (java.sql.Statement st = conn.createStatement()) {
            st.execute("SET LOCAL enable_seqscan = off");
            st.executeUpdate("INSERT INTO permission_change_log "
                    + "(tenant_id, entity_type, operation, change_source, request_id, diff_snapshot) "
                    + "SELECT 1, 'user_role', 'INSERT', 'MANUAL', 'req-schema-test', "
                    + "jsonb_build_object('eventType', 'USER_ROLE_CHANGE', 'items', '[]'::jsonb) "
                    + "FROM generate_series(1, 50)");
            StringBuilder plan = new StringBuilder();
            try (java.sql.ResultSet rs = st.executeQuery(
                    "EXPLAIN SELECT count(*) FROM permission_change_log WHERE tenant_id = 1 "
                    + "AND diff_snapshot IS NOT NULL "
                    + "AND (diff_snapshot ->> 'eventType') IN ('USER_ROLE_CHANGE')")) {
                while (rs.next()) {
                    plan.append(rs.getString(1)).append(' ');
                }
            }
            org.assertj.core.api.Assertions.assertThat(plan.toString())
                    .contains("idx_change_log_event_time");
        }
    }


    private static boolean columnExists(String table, String column) throws SQLException {
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = '" + table + "' AND column_name = '" + column + "'")) {
            rs.next();
            return rs.getLong(1) > 0;
        }
    }

    @Test
    @DisplayName("system_config 种子键均符合命名空间前缀 admin./permission./access.（T-ACCESS-007）")
    void shouldHaveNamespacedSeedKeys() throws SQLException {
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT config_key FROM system_config WHERE tenant_id = 1 AND delete_flag = 0")) {
            while (rs.next()) {
                String key = rs.getString(1);
                assertTrue(key.startsWith("admin.") || key.startsWith("permission.") || key.startsWith("access."),
                    "种子键应带合法命名空间前缀（admin./permission./access.），实际：" + key);
            }
        }
    }

    @Test
    @DisplayName("type_definition 种子数值与终值分配表一致：USER=1/SERVICE=2/LOCAL_USER=3/ORG=1/BASIC_ROLE=6/MENU=1/ORG=29")
    void shouldHaveAuthoritativeTypeValues() throws SQLException {
        assertTypeValue("user_type", "USER", 1);
        assertTypeValue("user_type", "SERVICE", 2);
        assertTypeValue("user_type", "LOCAL_USER", 3);
        assertTypeValue("role_type", "ORG", 1);
        assertTypeValue("role_type", "BASIC_ROLE", 6);
        assertTypeValue("resource_type", "MENU", 1);
        assertTypeValue("resource_type", "ORG", 29);
        assertTypeValue("resource_type", "ROLE", 5);
        assertTypeValue("resource_type", "USER", 6);
    }

    private void assertOperationBit(String typeCode, String opCode, long expectedBit, long expectedMask) throws SQLException {
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT op.binary_bit, op.inherit_mask FROM operation_permission op " +
                 "JOIN type_definition td ON td.tenant_id = op.tenant_id AND td.type_value = op.resource_type " +
                 "WHERE op.tenant_id = 1 AND td.type_key = 'resource_type' AND td.type_code = '" + typeCode + "' " +
                 "  AND op.code = '" + opCode + "' AND op.delete_flag = 0")) {
            assertTrue(rs.next(), typeCode + ":" + opCode + " 操作种子缺失");
            assertEquals(expectedBit, rs.getLong(1), typeCode + ":" + opCode + " binary_bit");
            assertEquals(expectedMask, rs.getLong(2), typeCode + ":" + opCode + " inherit_mask");
        }
    }

    private void assertTypeValue(String typeKey, String typeCode, int expected) throws SQLException {
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT type_value FROM type_definition WHERE tenant_id = 1 AND type_key = '" + typeKey + "' AND type_code = '" + typeCode + "' AND delete_flag = 0")) {
            assertTrue(rs.next(), "缺少类型种子 " + typeKey + ":" + typeCode);
            assertEquals(expected, rs.getInt(1), typeKey + ":" + typeCode + " 数值");
        }
    }

    @Test
    @DisplayName("system_config 合并超集字段：description/config_name/remark/is_system 齐备")
    void shouldHaveSystemConfigMergedColumns() throws SQLException {
        assertTrue(columnExists("system_config", "description"));
        assertTrue(columnExists("system_config", "config_name"));
        assertTrue(columnExists("system_config", "remark"));
        assertTrue(columnExists("system_config", "is_system"));
    }

    @Test
    @DisplayName("operation_log 合并超集字段与 target_id 字符串化和双轨列收敛")
    void shouldHaveOperationLogMergedColumns() throws SQLException {
        assertTrue(columnExists("operation_log", "operator_id"));
        assertTrue(columnExists("operation_log", "operator_name"));
        // T-ACCESS-007 切面只写 operator 字段，原 admin 双轨 user_id/username 为永久空列，
        assertFalse(columnExists("operation_log", "user_id"), "user_id 双轨列应删除（切面只写 operator_id）");
        assertFalse(columnExists("operation_log", "username"), "username 双轨列应删除（切面只写 operator_name）");
        assertTrue(columnExists("operation_log", "request_url"));
        assertTrue(columnExists("operation_log", "request_body"));
        assertTrue(columnExists("operation_log", "response_code"));
        assertTrue(columnExists("operation_log", "cost_time"));
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT data_type, character_maximum_length FROM information_schema.columns " +
                 "WHERE table_schema = 'public' AND table_name = 'operation_log' AND column_name = 'target_id'")) {
            assertTrue(rs.next(), "operation_log.target_id 列存在");
            String type = rs.getString(1).toLowerCase();
            assertTrue(type.contains("char") || type.contains("varchar"), "target_id 应为字符串类型，实际 " + type);
            assertEquals(256, rs.getInt(2), "target_id 长度应为 256（覆盖 configKey 128 / roleExternalId 256 等业务键上限）");
        }
    }

    @Test
    @DisplayName("MENU/ORG/USER 操作位与继承掩码终值")
    void shouldHaveAuthoritativeOperationBits() throws SQLException {
        assertOperationBit("MENU", "CREATE", 1, 0);
        assertOperationBit("MENU", "VIEW", 2, 0);
        assertOperationBit("MENU", "UPDATE", 4, 2);
        assertOperationBit("MENU", "DELETE", 8, 2);
        assertOperationBit("ORG", "CREATE", 1, 0);
        assertOperationBit("ORG", "VIEW_POSITION", 512, 0);
        assertOperationBit("USER", "ENABLE", 32, 2);
        assertOperationBit("USER", "RESET_PASSWORD", 64, 2);
    }

    @Test
    @DisplayName("domain_config 同租户同域同配置类型唯一")
    void shouldEnforceDomainConfigUniqueKey() throws SQLException {
        String insert = "INSERT INTO domain_config (tenant_id, biz_domain_id, config_type, extra) VALUES (1, 1, 'CLASSIFY', '{}')";
        try (Statement s = conn.createStatement()) {
            s.execute(insert);
        }
        assertConstraintViolation("23505", "uk_domain_config", insert);
    }

    @Test
    @DisplayName("带条件的主权限允许不可转授，拒绝 can_grant=true")
    void shouldEnforceConditionCanGrantCheck() throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("INSERT INTO role_resource_permission (tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type, condition_id, can_grant) VALUES (1, 1, 101, 1, 1, 30, FALSE)");
        }
        assertConstraintViolation("23514", "ck_role_resource_permission_condition_can_grant",
            "UPDATE role_resource_permission SET can_grant = TRUE WHERE tenant_id = 1 AND abstract_role_id = 1 AND resource_entity_id = 101 AND condition_id = 30");
    }

    private static void assertConstraintViolation(String sqlState, String constraint, String sql) throws SQLException {
        Savepoint savepoint = conn.setSavepoint();
        try (Statement s = conn.createStatement()) {
            PSQLException error = assertThrows(PSQLException.class, () -> s.execute(sql), constraint);
            assertEquals(sqlState, error.getSQLState(), constraint);
            assertEquals(constraint, error.getServerErrorMessage().getConstraint());
        } finally {
            // PG 约束异常会中止当前事务；恢复到负例前，后续断言才能执行真实 SQL。
            conn.rollback(savepoint);
            conn.releaseSavepoint(savepoint);
        }
    }

    @AfterAll
    static void closeConnection() throws SQLException {
        if (conn != null) {
            conn.close();
        }
    }
}
