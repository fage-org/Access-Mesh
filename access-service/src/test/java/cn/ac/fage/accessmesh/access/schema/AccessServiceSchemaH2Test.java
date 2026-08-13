package cn.ac.fage.accessmesh.access.schema;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * access-service.sql 空库结构测试（H2 PostgreSQL 兼容模式适配执行）。
 * <p>
 * 权威 DDL（docs/design/schema/access-service.sql）面向 PostgreSQL，H2 2.2.x 对部分
 * 语法不支持（部分索引 WHERE 谓词、NULLS NOT DISTINCT、COALESCE 索引列、GIN、
 * JSONB 路径表达式索引、ON CONFLICT、位运算 CHECK），本测试在执行前做字符串级适配：
 * </p>
 * <ul>
 *   <li>TIMESTAMPTZ → TIMESTAMP WITH TIME ZONE</li>
 *   <li>数组列 BIGINT[] DEFAULT '{}' → BIGINT ARRAY DEFAULT ARRAY[]；去 USING GIN</li>
 *   <li>部分索引（WHERE 谓词）→ 唯一索引去 WHERE 保留唯一语义；普通索引去 WHERE</li>
 *   <li>COALESCE 索引列 / NULLS NOT DISTINCT / JSONB 路径表达式索引 → 删除（H2 无等价表达）</li>
 *   <li>种子 INSERT 的 ON CONFLICT ... DO NOTHING 尾缀 → 删除（单次执行无重复冲突）</li>
 *   <li>CHECK 内位运算 & → BITAND()</li>
 * </ul>
 * <p>
 * 验证表数量、种子数据、合并表字段、新增列、唯一约束与 CHECK 约束行为；
 * 软删部分唯一索引在 H2 中降级为普通唯一索引，软删后业务键复用语义由应用层保证，
 * 由 {@link AccessServiceSchemaPostgresTest}（原样 DDL，Docker 可用时）补充全量验证。
 * </p>
 */
class AccessServiceSchemaH2Test {

    private static final Path DDL_PATH = Path.of("..", "docs", "design", "schema", "access-service.sql");

    private static Connection conn;

    @BeforeAll
    static void setup() throws Exception {
        if (!Files.exists(DDL_PATH)) {
            Assumptions.assumeTrue(false, "access-service.sql 不存在（期望相对 access-service 模块目录 ../docs/design/schema/），跳过");
        }
        conn = DriverManager.getConnection(
            "jdbc:h2:mem:access_schema_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
            "sa", "");
        String original = Files.readString(DDL_PATH, StandardCharsets.UTF_8);
        for (String stmt : splitStatements(adaptForH2(original))) {
            try (Statement s = conn.createStatement()) {
                s.execute(stmt);
            } catch (SQLException e) {
                throw new IllegalStateException("执行 DDL 语句失败: " + stmt, e);
            }
        }
    }

    // ---------------------------------------------------------------------
    // 适配层
    // ---------------------------------------------------------------------

    /** 全局字符串级适配（不依赖语句上下文） */
    private static String adaptForH2(String sql) {
        String s = sql;
        // 1. TIMESTAMPTZ
        s = s.replaceAll("(?i)TIMESTAMPTZ", "TIMESTAMP WITH TIME ZONE");
        // 2. 数组列默认值
        s = s.replaceAll("BIGINT\\[\\] DEFAULT '\\{\\}'", "BIGINT ARRAY DEFAULT ARRAY[]");
        // 3. GIN 索引关键字
        s = s.replaceAll("(?i) USING GIN", "");
        // 4. CHECK 内位运算（H2 不支持 &）
        s = s.replaceAll("\\(granted_bits & \\(granted_bits - 1\\)\\)", "(BITAND(granted_bits, granted_bits - 1))");
        // 5. 种子 INSERT 的 ON CONFLICT 尾缀（单次执行，无需幂等；前面可能是换行）
        s = s.replaceAll("\\s+ON CONFLICT \\([^)]*\\) WHERE [^;]*DO NOTHING", "");
        return s;
    }

    /** 按分号分割语句（DDL 的字符串字面量不含分号，安全；先滤除 -- 注释与空行） */
    private static List<String> splitStatements(String sql) {
        String noComments = sql.replaceAll("(?m)--.*$", "").replaceAll("(?m)^\\s*$\\n?", "");
        List<String> statements = new ArrayList<>();
        for (String raw : noComments.split(";")) {
            String stmt = raw.trim();
            if (stmt.isEmpty()) {
                continue;
            }
            // H2 无法表达的索引（仅限 CREATE [UNIQUE] INDEX 语句）：
            // COALESCE 列 / NULLS NOT DISTINCT / JSONB 路径表达式 → 跳过
            // （注意 CREATE TABLE 的 CHECK 约束也可能含 COALESCE，不能整体跳过）
            if (stmt.matches("(?is)CREATE (UNIQUE )?INDEX .*")
                && (stmt.contains("COALESCE(") || stmt.contains("NULLS NOT DISTINCT") || stmt.contains("->>"))) {
                continue;
            }
            // 部分索引（WHERE 谓词）：
            //   - 普通索引：去 WHERE 保留（覆盖全行，仅性能语义差异）
            //   - 唯一索引：仅含 delete_flag=0 谓词时去 WHERE 保留（软删键复用语义由应用层保证）；
            //     含其他业务谓词（如 resource_type IS NULL / is_default=true / phone IS NOT NULL）时
            //     去 WHERE 会错误收窄或错误放行其他行，H2 无等价表达 → 删除
            int whereIdx = indexOfKeyword(stmt, " WHERE ");
            if (whereIdx > 0 && stmt.matches("(?is)CREATE (UNIQUE )?INDEX .*")) {
                String whereClause = stmt.substring(whereIdx + " WHERE ".length());
                if (stmt.toUpperCase().startsWith("CREATE UNIQUE")) {
                    String rest = whereClause.replaceAll("(?i)delete_flag\\s*=\\s*0", "").trim();
                    if (!rest.isEmpty()) {
                        continue;
                    }
                }
                stmt = stmt.substring(0, whereIdx);
            }
            statements.add(stmt);
        }
        return statements;
    }

    private static int indexOfKeyword(String text, String keyword) {
        String upper = text.toUpperCase();
        int idx = 0;
        while (true) {
            int found = upper.indexOf(keyword, idx);
            if (found < 0) {
                return -1;
            }
            // 跳过括号内的 WHERE（如 CHECK 约束内），仅处理语句顶层
            int open = countChar(text.substring(0, found), '(');
            int close = countChar(text.substring(0, found), ')');
            if (open == close) {
                return found;
            }
            idx = found + keyword.length();
        }
    }

    private static int countChar(String text, char c) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == c) {
                count++;
            }
        }
        return count;
    }

    // ---------------------------------------------------------------------
    // 查询辅助
    // ---------------------------------------------------------------------

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
                 "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_schema) = 'public' AND table_name = '" + table + "'")) {
            rs.next();
            return rs.getLong(1) > 0;
        }
    }

    private static boolean columnExists(String table, String column) throws SQLException {
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT COUNT(*) FROM information_schema.columns WHERE LOWER(table_schema) = 'public' AND table_name = '" + table + "' AND column_name = '" + column + "'")) {
            rs.next();
            return rs.getLong(1) > 0;
        }
    }

    // ---------------------------------------------------------------------
    // 验证用例
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("空库执行后共有 34 张表")
    void shouldHave34Tables() throws SQLException {
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_schema) = 'public'")) {
            rs.next();
            assertEquals(34, rs.getLong(1), "表数量应为 34（admin 15 + permission 16 + 合并 2 + 基础设施 1，含 sys_sync_task 过渡表）");
        }
    }

    @Test
    @DisplayName("sys_sync_task 以过渡表保留（T-ACCESS-005 退役）")
    void shouldHaveSysSyncTaskTransitionTable() throws SQLException {
        assertTrue(tableExists("sys_sync_task"), "sys_sync_task 为过渡表，T-ACCESS-005 删除同步链路代码前必须保留（当前代码仍写入本表）");
    }

    @Test
    @DisplayName("种子数据：type_definition 36 行 / operation_permission 139 行 / system_config 9 行 / oauth2 3 行")
    void shouldHaveAllSeedRows() throws SQLException {
        assertEquals(36, countRows("type_definition"), "type_definition 系统种子 36 行（user_type 3 + role_type 5 + resource_type 28）");
        assertEquals(139, countRows("operation_permission"), "operation_permission 种子 139 行（静态类型 CRUD 112 + 非预置扩展 15 + 权限中心运行时必需 12）");
        assertEquals(9, countRows("system_config"), "system_config 种子 9 条（原 sys_config 键名不变）");
        assertEquals(3, countRows("sys_oauth2_client"), "sys_oauth2_client 种子 3 条");
    }

    @Test
    @DisplayName("运行时必需操作对完整性：代码实际校验的非 CRUD 操作全部有种子")
    void shouldHaveAllRuntimeRequiredOperations() throws SQLException {
        // 与代码调用点交叉核对的必需清单（agent 全量扫描 55 对去重，此处为非 CRUD 部分）
        String[][] required = {
            // 权限中心家族（12 对，评审 11 + API:ACCESS 接口鉴权）
            {"USER", "MANAGE"},
            {"ROLE", "MANAGE"}, {"ROLE", "ASSIGN"}, {"ROLE", "REVOKE"},
            {"RESOURCE", "MANAGE"},
            {"SERVICE", "MANAGE"}, {"SERVICE", "MANAGE_API_MAPPING"}, {"SERVICE", "SYNC_INTERFACE"},
            {"TYPE_DEFINITION", "MANAGE"},
            {"SYSTEM_CONFIG", "MANAGE"},
            {"OPERATION", "MANAGE"},
            {"DEPENDENCY", "SYNC"},
            {"API", "ACCESS"},
            // Admin 家族扩展码（15 对）
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
            if (!operationExists(pair[0], pair[1])) {
                missing.append(pair[0]).append(':').append(pair[1]).append(' ');
            }
        }
        assertTrue(missing.isEmpty(), "缺少运行时必需操作种子：" + missing);
    }

    private boolean operationExists(String typeCode, String opCode) throws SQLException {
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT COUNT(*) FROM operation_permission op " +
                 "JOIN type_definition td ON td.tenant_id = op.tenant_id AND td.type_value = op.resource_type " +
                 "WHERE op.tenant_id = 1 AND td.type_key = 'resource_type' AND td.type_code = '" + typeCode + "' " +
                 "  AND op.code = '" + opCode + "' AND op.delete_flag = 0")) {
            rs.next();
            return rs.getLong(1) > 0;
        }
    }

    @Test
    @DisplayName("type_definition 种子数值与枚举一致：USER=1/SERVICE=2/ADMIN_USER=3/ORG=1/BASIC_ROLE=6/MENU=1/ADMIN_ORG=17")
    void shouldHaveAuthoritativeTypeValues() throws SQLException {
        assertTypeValue("user_type", "USER", 1);
        assertTypeValue("user_type", "SERVICE", 2);
        assertTypeValue("user_type", "ADMIN_USER", 3);
        assertTypeValue("role_type", "ORG", 1);
        assertTypeValue("role_type", "BASIC_ROLE", 6);
        assertTypeValue("resource_type", "MENU", 1);
        assertTypeValue("resource_type", "ADMIN_ORG", 17);
        assertTypeValue("resource_type", "ROLE", 5);
    }

    @Test
    @DisplayName("每个静态 resource_type 均预置 CRUD 四操作（CREATE/VIEW/UPDATE/DELETE）")
    void shouldHaveCrudOperationsForEveryStaticResourceType() throws SQLException {
        // 28 个静态 resource_type 全部有 CRUD 四操作（冗余 VIEW 已合并消除，每个类型恰好 4 条）
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT td.type_code, COUNT(*) FROM type_definition td " +
                 "LEFT JOIN operation_permission op ON op.tenant_id = td.tenant_id " +
                 "  AND op.resource_type = td.type_value AND op.code IN ('CREATE','VIEW','UPDATE','DELETE') AND op.delete_flag = 0 " +
                 "WHERE td.tenant_id = 1 AND td.type_key = 'resource_type' AND td.delete_flag = 0 " +
                 "GROUP BY td.type_code HAVING COUNT(*) <> 4")) {
            StringBuilder missing = new StringBuilder();
            while (rs.next()) {
                missing.append(rs.getString(1)).append('(').append(rs.getLong(2)).append("条) ");
            }
            assertTrue(missing.isEmpty(), "存在未完整预置 CRUD 的资源类型：" + missing);
        }
        // 关键类型抽查：MENU=1 与 ADMIN_ORG=17 的 CRUD 位值正确
        assertOperationBit("MENU", "CREATE", 1, 0);
        assertOperationBit("MENU", "VIEW", 2, 0);
        assertOperationBit("MENU", "UPDATE", 4, 2);
        assertOperationBit("MENU", "DELETE", 8, 2);
        assertOperationBit("ADMIN_ORG", "CREATE", 1, 0);
        // 扩展码与 CRUD 位不冲突（ADMIN_ORG:VIEW_POSITION 从 512 起）
        assertOperationBit("ADMIN_ORG", "VIEW_POSITION", 512, 0);
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
    @DisplayName("本地投影所有权列：abstract_user/abstract_role/user_role 均含 owner_service_code")
    void shouldHaveOwnerServiceCodeColumns() throws SQLException {
        assertTrue(columnExists("abstract_user", "owner_service_code"));
        assertTrue(columnExists("abstract_role", "owner_service_code"));
        assertTrue(columnExists("user_role", "owner_service_code"));
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
    @DisplayName("operation_log 合并超集字段与 target_id 字符串化")
    void shouldHaveOperationLogMergedColumns() throws SQLException {
        assertTrue(columnExists("operation_log", "operator_id"));
        assertTrue(columnExists("operation_log", "operator_name"));
        assertTrue(columnExists("operation_log", "user_id"));
        assertTrue(columnExists("operation_log", "username"));
        assertTrue(columnExists("operation_log", "request_url"));
        assertTrue(columnExists("operation_log", "request_body"));
        assertTrue(columnExists("operation_log", "response_code"));
        assertTrue(columnExists("operation_log", "cost_time"));
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(
                 "SELECT data_type, character_maximum_length FROM information_schema.columns " +
                 "WHERE LOWER(table_schema) = 'public' AND table_name = 'operation_log' AND column_name = 'target_id'")) {
            assertTrue(rs.next(), "operation_log.target_id 列存在");
            String type = rs.getString(1).toLowerCase();
            assertTrue(type.contains("char") || type.contains("varchar"), "target_id 应为字符串类型，实际 " + type);
            assertEquals(256, rs.getInt(2), "target_id 长度应为 256（覆盖 configKey 128 / roleExternalId 256 等业务键上限）");
        }
    }

    @Test
    @DisplayName("system_config 唯一约束：同租户同 config_key 重复插入被拒（23505）")
    void shouldEnforceSystemConfigUniqueKey() {
        assertThrows(SQLException.class, () -> {
            try (Statement s = conn.createStatement()) {
                s.execute("INSERT INTO system_config (tenant_id, config_key, config_value, config_name, is_system) " +
                    "VALUES (1, 'LOGIN_CAPTCHA_ENABLED', 'true', '重复键测试', false)");
            }
        }, "uk_system_config (tenant_id, config_key) 应拒绝重复插入");
    }

    @Test
    @DisplayName("role_resource_permission CHECK：scope_all=false 时必须携带 resource_entity_id")
    void shouldEnforceRoleResourcePermissionCheck() {
        assertThrows(SQLException.class, () -> {
            try (Statement s = conn.createStatement()) {
                s.execute("INSERT INTO role_resource_permission " +
                    "(tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type) " +
                    "VALUES (1, 1, NULL, 1, 1)");
            }
        }, "ck_role_resource_permission_scope_all 应拒绝 scope_all=false 且 resource_entity_id 为 NULL 的记录");
    }

    @Test
    @DisplayName("sys_task_execution 表与执行键唯一约束")
    void shouldHaveTaskExecutionTable() throws SQLException {
        assertTrue(tableExists("sys_task_execution"), "sys_task_execution 应存在（T-ACCESS-009 预建）");
        assertTrue(columnExists("sys_task_execution", "execution_key"));
        assertTrue(columnExists("sys_task_execution", "lease_owner"));
        assertTrue(columnExists("sys_task_execution", "lease_until"));
        assertTrue(columnExists("sys_task_execution", "attempt_count"));
        assertThrows(SQLException.class, () -> {
            try (Statement s = conn.createStatement()) {
                s.execute("INSERT INTO sys_task_execution (tenant_id, execution_key, status) VALUES (1, 'job-1_2026-08-12T00:00', 'PENDING')");
                s.execute("INSERT INTO sys_task_execution (tenant_id, execution_key, status) VALUES (1, 'job-1_2026-08-12T00:00', 'PENDING')");
            }
        }, "uk_task_execution 应拒绝同租户同执行键的重复记录");
    }
}
