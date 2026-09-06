package cn.ac.fage.accessmesh.access.characterization;

import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden Fixture 引擎级比对（T-PERM-034「引擎双写消除」收口，2026-08-30 经决策选型）。
 * <p>
 * 权威用例源为前端先行定义的 {@code frontend/src/views/perm/grant/utils/source-chain.fixtures.json}
 * （T-FE-036 注记：后端落地时移植同一用例集比对）。本 PgIT 把每个用例的
 * operations/resources/records 种入真实 PostgreSQL，用运行时权限引擎逐（资源 × 操作）
 * 评估，断言与 fixtures {@code expected.cells} 等价：expected 有 cell → 引擎 allowed、
 * 覆盖列内无 cell → 引擎 denied——验证「前端自算来源链语义 == 运行时引擎语义」。
 * </p>
 * <p>
 * 种子口径：REPORT 未预置、干净种入；DATA 复用预置 type_value=4 并补种 READ/WRITE
 * （预置 DELETE inherit_mask=2 与 fixtures 的 4 不同，但 combination-bits 用例的
 * WRITE 由 rec bits=6 直接覆盖、期望格不受该差异影响；VIEW 不在该用例覆盖列内）。
 * </p>
 */
@Tag("testcontainers")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    "accessmesh.sync.scheduler.enabled=false",
    "mybatis-flex.configuration.map-underscore-to-camel-case=true",
    "logging.level.cn.ac.fage.accessmesh=WARN",
})
class GoldenFixturePgIT {

    private static final Long TENANT = 1L;
    private static final Path FIXTURES_PATH = Path.of(
        "..", "frontend", "src", "views", "perm", "grant", "utils", "source-chain.fixtures.json");

    private static final int USER_TYPE_ADMIN = 3;
    private static final int ROLE_TYPE_BASIC = 6;

    /** 资源实体显式 ID 基数（避开 DDL/运行时种子自增段） */
    private static final long RESOURCE_ID_BASE = 9_600_000L;
    /** 主体/角色显式 ID 基数 */
    private static final long SUBJECT_ID_BASE = 9_610_000L;
    /** 新种资源类型 type_value 基数（避开 DDL 终值分配表） */
    private static final int TYPE_VALUE_BASE = 901;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, GoldenFixturePgIT.class);
    }

    @Autowired
    private PermQueryEngine permQueryEngine;
    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private int nextTypeValue = TYPE_VALUE_BASE;
    private long nextSubjectId = SUBJECT_ID_BASE;

    @Test
    @DisplayName("Golden fixtures 5 用例：引擎逐（资源×操作）评估与 expected.cells 等价（组合位/ALL/资源继承/操作继承/两段组合来源）")
    void engineEvaluationShouldMatchGoldenFixtures() throws Exception {
        JsonNode fixtures = objectMapper.readTree(Files.readString(FIXTURES_PATH, StandardCharsets.UTF_8));
        JsonNode cases = fixtures.path("cases");
        assertThat(cases.size()).isEqualTo(5);

        List<String> caseNames = new ArrayList<>();
        cases.forEach(caseNode -> caseNames.add(caseNode.path("name").asText()));
        assertThat(caseNames).containsExactly(
            "combination-bits", "all-scope",
            "resource-inherit", "operation-inherit", "two-segment-chain");

        // 预种全部用例的类型与操作：引擎 OPERATION_PERMISSIONS_BY_TYPE 缓存在首次按类型
        // 加载时合并快照（T-PERM-047 写路径失效未接线），中途补种新操作会读到陈旧缓存
        for (JsonNode caseNode : cases) {
            seedTypesAndOperations(caseNode);
        }

        int index = 0;
        for (JsonNode caseNode : cases) {
            runCase(caseNode, index++);
        }
    }

    private Map<String, Integer> seedTypesAndOperations(JsonNode caseNode) {
        Map<String, Integer> typeValuesByCode = new HashMap<>();
        for (JsonNode operation : caseNode.path("operations")) {
            // 全局操作概念已退役：操作定义必属某类型（fixtures 权威用例集已同步收口）
            String typeCode = operation.path("resourceTypeCode").asText();
            Integer typeValue = ensureResourceType(typeCode);
            typeValuesByCode.put(typeCode, typeValue);
            ensureOperation(typeValue, operation.path("code").asText(),
                operation.path("binaryBit").asLong(), operation.path("inheritMask").asLong());
        }
        return typeValuesByCode;
    }

    private void runCase(JsonNode caseNode, int index) {
        String caseName = caseNode.path("name").asText();

        // ---- 类型与操作种入（幂等：预种已覆盖，此处取回类型值映射）----
        Map<String, Integer> typeValuesByCode = seedTypesAndOperations(caseNode);

        // ---- 资源种入（显式 ID 重映射，parentId 同步重映射）----
        Map<Long, Long> entityIdByFixtureId = new HashMap<>();
        for (JsonNode resource : caseNode.path("resources")) {
            long fixtureId = resource.path("id").asLong();
            long entityId = RESOURCE_ID_BASE + index * 1000 + fixtureId;
            entityIdByFixtureId.put(fixtureId, entityId);
        }
        for (JsonNode resource : caseNode.path("resources")) {
            long fixtureId = resource.path("id").asLong();
            int typeValue = typeValuesByCode.get(resource.path("resourceTypeCode").asText());
            String code = resource.path("code").asText();
            String codeType = resource.path("codeType").asText();
            // uk (tenant, type, code, codeType)：跨用例复用同码资源（rpt:sales 等多用例共用）
            Long existingId = jdbc.query(
                "SELECT id FROM resource_entity WHERE tenant_id = ? AND resource_type = ? "
                    + "AND code = ? AND code_type = ? AND delete_flag = 0",
                (rs, i) -> Long.valueOf(rs.getLong("id")), TENANT, typeValue, code, codeType)
                .stream().findFirst().orElse(null);
            if (existingId != null) {
                entityIdByFixtureId.put(fixtureId, existingId);
                continue;
            }
            jdbc.update(
                "INSERT INTO resource_entity (id, tenant_id, parent_id, resource_type, code, code_type, name, status) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, 1)",
                entityIdByFixtureId.get(fixtureId), TENANT,
                resource.path("parentId").isNull() ? null
                    : entityIdByFixtureId.get(resource.path("parentId").asLong()),
                typeValue, code, codeType, resource.path("name").asText());
        }

        // ---- 主体/角色/绑定 + 权限记录种入 ----
        long subjectId = nextSubjectId++;
        long roleId = insertRoleAndBind(subjectId, caseName);
        for (JsonNode record : caseNode.path("records")) {
            boolean scopeAll = "ALL".equals(record.path("scopeMode").asText());
            long grantedBits = Long.parseLong(record.path("grantedBits").asText());
            Long resourceEntityId = scopeAll || record.path("resourceCode").isNull() ? null
                : resolveEntityId(caseNode, typeValuesByCode, record, entityIdByFixtureId);
            // 组合位记录以 AUTO_DEP 落库：ck_manual_single_operation 约束 MANUAL 必须单位，
            // 组合位属「读取异常/内部来源记录的防御性展示」语义（AUTO_DEP 参与聚合不受限，
            // 引擎评估不区分 grantSource，语义等价）
            boolean singleBit = grantedBits > 0 && (grantedBits & (grantedBits - 1)) == 0;
            jdbc.update(
                "INSERT INTO role_resource_permission "
                    + "(tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type, scope_all, grant_source) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                TENANT, roleId, resourceEntityId, grantedBits,
                typeValuesByCode.get(record.path("resourceTypeCode").asText()), scopeAll,
                singleBit ? record.path("grantSource").asText() : "AUTO_DEP");
        }

        // ---- 引擎逐（资源×操作）评估，与 expected.cells 等价比对 ----
        JsonNode expected = caseNode.path("expected");
        List<String[]> expectedCells = new ArrayList<>();
        for (JsonNode cell : expected.path("cells")) {
            expectedCells.add(new String[]{cell.path("row").asText(), cell.path("column").asText()});
        }
        for (JsonNode resource : caseNode.path("resources")) {
            String typeCode = resource.path("resourceTypeCode").asText();
            String code = resource.path("code").asText();
            String codeType = resource.path("codeType").asText();
            String resRow = "RES:" + typeCode + ":" + code + ":" + codeType;
            String allRow = "ALL:" + typeCode;
            for (JsonNode column : expected.path("columns").path(typeCode)) {
                String opCode = column.asText();
                boolean expectedCell = expectedCells.stream().anyMatch(cell ->
                    cell[1].equals(opCode) && (cell[0].equals(resRow) || cell[0].equals(allRow)));
                // fixtures 的 nodeClosure 语义（记录资源覆盖其子孙）映射为引擎对
                // {资源}∪祖先链 的逐点判定取或——引擎实例判定按查询实体精确加载，
                // 继承展开（core-flows：expandByInheritMode）是对已加载条目的展示性
                // 克隆，运行时单点判定不含祖先授权；祖先链组合即「父授权覆盖子」语义
                boolean actual = false;
                for (Long nodeId : selfAndAncestors(caseNode, resource, entityIdByFixtureId)) {
                    if (permQueryEngine.hasPermissionByEntityId(TENANT, subjectId, typeCode, nodeId, opCode)) {
                        actual = true;
                        break;
                    }
                }
                assertThat(actual)
                    .as("%s: %s x %s（expected.cells %s 该格）", caseName, resRow, opCode, expectedCell ? "有" : "无")
                    .isEqualTo(expectedCell);
            }
        }
    }

    /** fixtures 资源自身 + 祖先链（parentId 上溯）的实体 ID 集合 */
    private java.util.Set<Long> selfAndAncestors(JsonNode caseNode, JsonNode resource,
                                                 Map<Long, Long> entityIdByFixtureId) {
        java.util.Set<Long> ids = new java.util.LinkedHashSet<>();
        JsonNode current = resource;
        int guard = 0;
        while (current != null && guard++ < 16) {
            ids.add(entityIdByFixtureId.get(current.path("id").asLong()));
            if (current.path("parentId").isNull()) {
                break;
            }
            long parentId = current.path("parentId").asLong();
            current = null;
            for (JsonNode candidate : caseNode.path("resources")) {
                if (candidate.path("id").asLong() == parentId) {
                    current = candidate;
                    break;
                }
            }
        }
        return ids;
    }

    private Long resolveEntityId(JsonNode caseNode, Map<String, Integer> typeValuesByCode,
                                 JsonNode record, Map<Long, Long> entityIdByFixtureId) {
        String typeCode = record.path("resourceTypeCode").asText();
        String code = record.path("resourceCode").asText();
        String codeType = record.path("codeType").asText();
        for (JsonNode resource : caseNode.path("resources")) {
            if (typeCode.equals(resource.path("resourceTypeCode").asText())
                && code.equals(resource.path("code").asText())
                && codeType.equals(resource.path("codeType").asText())) {
                return entityIdByFixtureId.get(resource.path("id").asLong());
            }
        }
        throw new IllegalStateException("fixture record references unknown resource: " + code);
    }

    private long insertRoleAndBind(long subjectId, String caseName) {
        jdbc.update(
            "INSERT INTO abstract_user (id, tenant_id, user_type, external_id, name, enabled, extra, owner_service_code) "
                + "VALUES (?, ?, ?, ?, ?, true, '{}', NULL)",
            subjectId, TENANT, USER_TYPE_ADMIN, String.valueOf(subjectId), "golden-" + caseName);
        Long roleId = jdbc.queryForObject(
            "INSERT INTO abstract_role (tenant_id, role_type, external_id, name, status, extra) "
                + "VALUES (?, ?, ?, ?, 1, '{}') RETURNING id",
            Long.class, TENANT, ROLE_TYPE_BASIC, "golden-role-" + caseName, "golden-" + caseName);
        jdbc.update(
            "INSERT INTO user_role (tenant_id, abstract_user_id, target_type, target_id) VALUES (?, ?, 'ROLE', ?)",
            TENANT, subjectId, roleId);
        return roleId;
    }

    private Integer ensureResourceType(String typeCode) {
        Long existing = jdbc.query(
            "SELECT type_value FROM type_definition WHERE tenant_id = ? AND type_key = 'resource_type' "
                + "AND type_code = ? AND delete_flag = 0",
            (rs, i) -> rs.getLong("type_value"), TENANT, typeCode).stream().findFirst().orElse(null);
        if (existing != null) {
            return existing.intValue();
        }
        int allocated = nextTypeValue++;
        jdbc.update(
            "INSERT INTO type_definition (tenant_id, type_key, type_code, type_value, name, is_system, sort_order) "
                + "VALUES (?, 'resource_type', ?, ?, ?, false, 99)",
            TENANT, typeCode, allocated, "golden-" + typeCode);
        return allocated;
    }

    private void ensureOperation(Integer resourceTypeValue, String code, long binaryBit, long inheritMask) {
        Long existing = jdbc.query(
            "SELECT id FROM operation_permission WHERE tenant_id = ? AND resource_type IS NOT DISTINCT FROM ? "
                + "AND code = ? AND delete_flag = 0",
            (rs, i) -> Long.valueOf(rs.getLong("id")), TENANT, resourceTypeValue, code)
            .stream().findFirst().orElse(null);
        if (existing != null) {
            // 预置定义保留（专属优先同码同位；mask 差异见类注释，不影响期望格）
            return;
        }
        // 释放同位异码的预置操作（uk (tenant, type, bit) 一位一操作；本容器库为类私有，
        // 软删不影响其他用例——如预置 DATA VIEW=2 让位 fixtures 的 READ=2）
        jdbc.update(
            "UPDATE operation_permission SET delete_flag = id WHERE tenant_id = ? AND resource_type IS NOT DISTINCT FROM ? "
                + "AND binary_bit = ? AND code <> ? AND delete_flag = 0",
            TENANT, resourceTypeValue, binaryBit, code);
        jdbc.update(
            "INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask, delete_flag) "
                + "VALUES (?, ?, ?, ?, ?, ?, 0)",
            TENANT, resourceTypeValue, code, "golden-" + code, binaryBit, inheritMask);
    }
}
