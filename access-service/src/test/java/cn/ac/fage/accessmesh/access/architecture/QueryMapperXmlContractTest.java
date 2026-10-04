package cn.ac.fage.accessmesh.access.architecture;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.builder.xml.XMLMapperEntityResolver;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** QueryMapper 的只读、租户、投影与非分页结构；运行语义由 QueryMapperPgIT 验证。 */
class QueryMapperXmlContractTest {

    private record Statement(Path file, String namespace, Element node, Configuration configuration) {
        String id() { return node.getAttribute("id"); }
        String label() { return file + "#" + id(); }
        String sql() { return normalize(node.getTextContent()); }
        String boundSql(Map<String, Object> parameters) {
            return normalize(configuration.getMappedStatement(namespace + "." + id()).getBoundSql(parameters).getSql());
        }
    }

    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private List<Statement> statements() throws IOException {
        List<Statement> statements = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        for (String directory : List.of("org", "menu", "role")) {
            Path path = Path.of("src", "main", "resources", "mapper", directory);
            try (var paths = Files.list(path)) {
                files.addAll(paths.filter(p -> p.getFileName().toString().endsWith("QueryMapper.xml")).sorted().toList());
            }
        }
        assertThat(files).as("org/menu/role 的 QueryMapper XML").hasSize(3);
        for (Path file : files) {
            Configuration configuration = new Configuration();
            try (var input = Files.newInputStream(file)) {
                new XMLMapperBuilder(input, configuration, file.toString(), configuration.getSqlFragments()).parse();
            }
            try (var input = Files.newInputStream(file)) {
                var parser = new XPathParser(input, false, null, new XMLMapperEntityResolver());
                String namespace = parser.evalString("/mapper/@namespace");
                var nodes = parser.evalNodes("/mapper/select | /mapper/insert | /mapper/update | /mapper/delete");
                assertThat(nodes).as("%s 的映射语句", file).isNotEmpty();
                nodes.forEach(node -> statements.add(new Statement(file, namespace, (Element) node.getNode(), configuration)));
            }
        }
        return statements;
    }

    @Test
    @DisplayName("query XML 只包含 select 映射")
    void queryXmlsContainOnlySelects() throws IOException {
        for (Statement statement : statements()) {
            assertThat(statement.node().getTagName()).as(statement.label()).isEqualTo("select");
        }
    }

    @Test
    @DisplayName("每个 statement 显式绑定 tenantId")
    void everySelectCarriesTenantId() throws IOException {
        for (Statement statement : statements()) {
            assertThat(statement.sql()).as(statement.label())
                .containsPattern("\\btenant_id\\s*=\\s*#\\{\\s*tenantid\\s*}");
        }
    }

    @Test
    @DisplayName("全量组合查询不允许 LIMIT 截断")
    void noSelectUsesLimit() throws IOException {
        for (Statement statement : statements()) {
            assertThat(statement.sql()).as(statement.label()).doesNotContainPattern("\\blimit\\b");
        }
    }

    @Test
    @DisplayName("投影列显式声明，不使用 SELECT *")
    void projectionsUseExplicitColumns() throws IOException {
        for (Statement statement : statements()) {
            assertThat(statement.sql()).as(statement.label()).doesNotContainPattern("\\bselect\\s+(?:[a-z_][a-z_0-9]*\\.)?\\*");
        }
    }

    @Test
    @DisplayName("角色投影的有效期和 LEFT JOIN 条件限定在本 statement")
    void userRoleProjectionValidityWindow() throws IOException {
        Statement statement = statements().stream().filter(s -> s.id().equals("selectUserRoleProjections"))
            .findFirst().orElseThrow();
        assertThat(statement.sql()).as(statement.label())
            .containsPattern("ur\\.valid_from\\s*<=\\s*#\\{now}\\s+or\\s+ur\\.valid_from\\s+is\\s+null")
            .containsPattern("ur\\.valid_to\\s*>=\\s*#\\{now}\\s+or\\s+ur\\.valid_to\\s+is\\s+null")
            .containsPattern("left join abstract_role ar on ur\\.target_id\\s*=\\s*ar\\.id")
            .containsPattern("ar\\.tenant_id\\s*=\\s*ur\\.tenant_id")
            .containsPattern("ar\\.delete_flag\\s*=\\s*0")
            .containsPattern("left join abstract_role ar_rel on ur\\.relation_id\\s*=\\s*ar_rel\\.id")
            .containsPattern("ar_rel\\.tenant_id\\s*=\\s*ur\\.tenant_id")
            .containsPattern("ar_rel\\.delete_flag\\s*=\\s*0");
    }

    @Test
    @DisplayName("每个 foreach 的空/null 集合生成拒绝条件，不能借用相邻语句守卫")
    void batchInQueriesGuardEmptyCollections() throws IOException {
        for (Statement statement : statements()) {
            var loops = statement.node().getElementsByTagName("foreach");
            for (int i = 0; i < loops.getLength(); i++) {
                String collection = ((Element) loops.item(i)).getAttribute("collection");
                Map<String, Object> parameters = new HashMap<>();
                parameters.put("tenantId", 1L);
                for (boolean nullCollection : List.of(false, true)) {
                    parameters.put(collection, nullCollection ? null : List.of());
                    assertThat(statement.boundSql(parameters)).as("%s %s null=%s", statement.label(), collection, nullCollection)
                        .containsPattern("\\bid\\s*=\\s*-1\\b").doesNotContainPattern("\\bin\\s*\\(\\s*\\)");
                }
                parameters.put(collection, List.of(1L, 2L));
                assertThat(statement.boundSql(parameters)).as("%s 非空 %s", statement.label(), collection)
                    .containsPattern("\\bin\\s*\\(\\s*\\?\\s*,\\s*\\?\\s*\\)").doesNotContainPattern("\\bid\\s*=\\s*-1\\b");
            }
        }
    }
}
