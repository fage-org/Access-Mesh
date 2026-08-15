package cn.ac.fage.accessmesh.access.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * QueryMapper XML 契约静态测试（T-ACCESS-006 验收标准 2/3/4）。
 * <p>
 * 断言专用 QueryMapper（resources/mapper/query/）：
 * 1. 只包含 select 标签，禁止 insert/update/delete（验收标准 2「只包含 SELECT」+ 验收标准 5「禁止写 SQL」）；
 * 2. 每个 select 显式携带 tenant_id 条件（验收标准 3「列表、树和详情查询显式包含 tenant_id 条件」）；
 * 3. 分页查询带 ORDER BY + LIMIT/OFFSET（分页语义完整，总数由调用方按同一条件统计时与结果一致）；
 * 4. 投影返回列显式声明（不 SELECT *，避免领域实体列漂移）。
 * </p>
 */
class QueryMapperXmlContractTest {

    private static final Pattern SQL_STATEMENT = Pattern.compile(
        "<(insert|update|delete|select)\\s", Pattern.CASE_INSENSITIVE);
    private static final Pattern SELECT_OPEN = Pattern.compile(
        "<select\\s", Pattern.CASE_INSENSITIVE);
    private static final Pattern SELECT_CLOSE = Pattern.compile(
        "</select>", Pattern.CASE_INSENSITIVE);

    private List<String> queryXmlFiles() throws IOException {
        Path mapperDir = Paths.get("src", "main", "resources", "mapper", "query");
        if (!Files.isDirectory(mapperDir)) {
            fail("query mapper 目录不存在: " + mapperDir.toAbsolutePath());
        }
        try (Stream<Path> paths = Files.list(mapperDir)) {
            return paths.filter(p -> p.toString().endsWith(".xml")).map(Path::toString).toList();
        }
    }

    @Test
    @DisplayName("query XML 只包含 select 标签，禁止任何写 SQL")
    void queryXmlsContainOnlySelects() throws IOException {
        List<String> files = queryXmlFiles();
        assertThat(files).as("query mapper XML 文件应存在").isNotEmpty();

        for (String file : files) {
            String content = Files.readString(Paths.get(file));
            Matcher matcher = SQL_STATEMENT.matcher(content);
            List<String> statements = new ArrayList<>();
            while (matcher.find()) {
                statements.add(matcher.group(1));
            }
            assertThat(statements)
                .as("文件 %s 只允许 select 语句", file)
                .allMatch("select"::equalsIgnoreCase);
        }
    }

    @Test
    @DisplayName("每个 select 显式包含 tenant_id 条件")
    void everySelectCarriesTenantId() throws IOException {
        List<String> files = queryXmlFiles();
        for (String file : files) {
            String content = Files.readString(Paths.get(file));
            Matcher open = SELECT_OPEN.matcher(content);
            List<Integer> selectStarts = new ArrayList<>();
            while (open.find()) {
                selectStarts.add(open.start());
            }
            Matcher close = SELECT_CLOSE.matcher(content);
            List<Integer> selectEnds = new ArrayList<>();
            while (close.find()) {
                selectEnds.add(close.start());
            }
            assertThat(selectStarts).as("文件 %s select 标签数", file).hasSize(selectEnds.size());

            for (int i = 0; i < selectStarts.size(); i++) {
                String sql = content.substring(selectStarts.get(i), selectEnds.get(i) + "</select>".length());
                assertThat(sql)
                    .as("文件 %s 第 %d 个 select 必须显式携带 tenant_id 条件", file, i + 1)
                    .contains("tenant_id = #{tenantId}");
            }
        }
    }

    @Test
    @DisplayName("分页查询带 ORDER BY 与 LIMIT/OFFSET，非分页查询无 LIMIT")
    void pagedSelectsCarryOrderAndLimit() throws IOException {
        List<String> files = queryXmlFiles();
        for (String file : files) {
            String content = Files.readString(Paths.get(file));
            // selectFunctionalRoles 是唯一分页查询：ORDER BY id LIMIT #{limit} OFFSET #{offset}
            int idx = content.indexOf("selectFunctionalRoles");
            if (idx >= 0) {
                String sql = content.substring(idx, Math.min(content.length(), idx + 1200));
                assertThat(sql)
                    .as("selectFunctionalRoles 必须按 id 排序")
                    .contains("ORDER BY id");
                assertThat(sql)
                    .as("selectFunctionalRoles 必须 LIMIT/OFFSET 分页")
                    .contains("LIMIT #{limit}").contains("OFFSET #{offset}");
            }
            // 其他 select 不允许出现 LIMIT（组合查询无分页时全量返回，避免隐式截断）
            for (String other : new String[]{"selectMenus", "selectUserOrgsByUserIds", "selectUserRoleProjections",
                "selectOrgBriefsByIds", "selectDefaultTreeRootOrgIds", "selectDescendantOrgIds"}) {
                int oi = content.indexOf(other);
                if (oi >= 0) {
                    String sql = content.substring(oi, Math.min(content.length(), oi + 1200));
                    assertThat(sql)
                        .as("%s 不应包含 LIMIT 分页（调用方全量消费）", other)
                        .doesNotContain("LIMIT");
                }
            }
        }
    }

    @Test
    @DisplayName("投影列显式声明，禁止 SELECT *（避免领域实体列漂移）")
    void projectionsUseExplicitColumns() throws IOException {
        List<String> files = queryXmlFiles();
        for (String file : files) {
            String content = Files.readString(Paths.get(file));
            assertThat(content)
                .as("文件 %s 禁止 SELECT * 隐式列", file)
                .doesNotContain("SELECT *")
                .doesNotContain("select *");
        }
    }

    @Test
    @DisplayName("selectUserRoleProjections 有效期窗口与 LEFT JOIN 语义被静态钉住")
    void userRoleProjectionValidityWindow() throws IOException {
        List<String> files = queryXmlFiles();
        String file = files.stream()
            .filter(f -> f.endsWith("UserRoleQueryMapper.xml"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("UserRoleQueryMapper.xml 不存在"));
        String content = Files.readString(Paths.get(file));
        int idx = content.indexOf("selectUserRoleProjections");
        String sql = content.substring(idx, Math.min(content.length(), idx + 1600));

        // 有效期窗口：valid_from/valid_to 与 now 比较，NULL 放行（与旧 selectValidByUserIdWithValidity 一致）
        assertThat(sql)
            .as("有效期窗口条件（valid_from）")
            .contains("ur.valid_from &lt;= #{now} OR ur.valid_from IS NULL");
        assertThat(sql)
            .as("有效期窗口条件（valid_to）")
            .contains("ur.valid_to &gt;= #{now} OR ur.valid_to IS NULL");
        // target/relation 均 LEFT JOIN 且限定租户与删除标记（target 缺失保留行语义）
        assertThat(sql)
            .as("target 角色 LEFT JOIN 含租户/删除条件")
            .contains("LEFT JOIN abstract_role ar ON ur.target_id = ar.id")
            .contains("ar.tenant_id = ur.tenant_id")
            .contains("ar.delete_flag = 0");
        assertThat(sql)
            .as("relation 角色 LEFT JOIN 含租户/删除条件")
            .contains("LEFT JOIN abstract_role ar_rel ON ur.relation_id = ar_rel.id")
            .contains("ar_rel.tenant_id = ur.tenant_id")
            .contains("ar_rel.delete_flag = 0");
    }

    @Test
    @DisplayName("批量 IN 查询空集合有守卫（避免 IN () 语法错误）")
    void batchInQueriesGuardEmptyCollections() throws IOException {
        List<String> files = queryXmlFiles();
        for (String file : files) {
            String content = Files.readString(Paths.get(file));
            // 每个含 <foreach> 的 IN 查询都必须在同一 select 内有空集合守卫
            if (content.contains("<foreach")) {
                assertThat(content)
                    .as("文件 %s 的 IN 批量查询需有空集合守卫", file)
                    .contains("size() > 0")
                    .contains("id = -1");
            }
        }
    }
}
