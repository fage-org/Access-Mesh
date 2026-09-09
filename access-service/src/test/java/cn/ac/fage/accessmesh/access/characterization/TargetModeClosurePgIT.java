package cn.ac.fage.accessmesh.access.characterization;

import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermQuery;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * targetMode 三态与判定面继承闭包的引擎级回归锁（T-PERM-057，真实 PostgreSQL 递归 CTE）。
 * <p>
 * 锁定口径（query-engine-unification.md §2/§5/§10.3）：
 * </p>
 * <ul>
 *   <li>TYPE_LEVEL 只消费 scopeAll——实例授权不得放行类型级门禁（三态互不串义）；</li>
 *   <li>INSTANCE 判定面继承：查子目标时 {目标}∪同类型祖先链入查询（授父覆盖子）；
 *       批量拒绝轨闭包回映射（条目挂祖先不得误判 DENIED）；</li>
 *   <li>闭包止步同类型（sync 通道允许跨类型父子边，跨类型祖先不参与闭包）；
 *       软删祖先截断（CTE 过滤 delete_flag=0）；</li>
 *   <li>/auth/check inheritMode 参数接通为目标闭包真实语义（NONE 关 / PARENT 开）。</li>
 * </ul>
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
class TargetModeClosurePgIT {

    private static final Long TENANT = 1L;

    private static final int USER_TYPE_ADMIN = 3;
    private static final int ROLE_TYPE_BASIC = 6;

    /** 显式 ID 基数（避开 DDL/运行时种子自增段，GoldenFixturePgIT 同款口径） */
    private static final long RESOURCE_ID_BASE = 9_700_000L;
    private static final long SUBJECT_ID_BASE = 9_710_000L;
    private static final int TYPE_VALUE_BASE = 951;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, TargetModeClosurePgIT.class);
    }

    @Autowired
    private PermQueryEngine permQueryEngine;
    @Autowired
    private JdbcTemplate jdbc;

    // PER_METHOD 生命周期下跨用例递增（每用例新建实例，实例字段会重置）
    private static int nextTypeValue = TYPE_VALUE_BASE;
    private static long nextSubjectId = SUBJECT_ID_BASE;
    private static long nextResourceId = RESOURCE_ID_BASE;

    @Test
    @DisplayName("TYPE_LEVEL 互不串义：实例授权不放行类型级门禁，scopeAll 行放行")
    void typeLevelMustNotBeSatisfiedByInstanceOnlyGrant() {
        Integer type = ensureResourceType("TMCL_A");
        ensureOperation(type, "MANAGE", 16L, 0L);
        long subjectId = nextSubjectId++;
        long roleId = insertRoleAndBind(subjectId, "tmcl-a");
        long resourceId = insertResource(type, "tmcl-a-res", null);

        // 仅有实例授权行：类型级门禁拒绝（MANAGE 实例授权不构成类型级 MANAGE）
        insertPerm(roleId, type, resourceId, 16L, false);
        assertThat(permQueryEngine.hasPermissionByCode(TENANT, subjectId, "TMCL_A", null, "MANAGE"))
            .as("实例授权不得放行 TYPE_LEVEL 门禁").isFalse();

        // 补 scopeAll 行：类型级门禁放行
        insertPerm(roleId, type, null, 16L, true);
        assertThat(permQueryEngine.hasPermissionByCode(TENANT, subjectId, "TMCL_A", null, "MANAGE"))
            .as("scopeAll 行放行 TYPE_LEVEL 门禁").isTrue();
    }

    @Test
    @DisplayName("INSTANCE 判定面继承：授父覆盖子（单点）+ 批量拒绝闭包回映射（条目挂祖先不误判）")
    void instanceClosureCoversChildAndBackMapsBatch() {
        Integer type = ensureResourceType("TMCL_B");
        ensureOperation(type, "MANAGE", 16L, 0L);
        long subjectId = nextSubjectId++;
        long roleId = insertRoleAndBind(subjectId, "tmcl-b");
        long parentId = insertResource(type, "tmcl-b-parent", null);
        long childA = insertResource(type, "tmcl-b-child-a", parentId);
        long childB = insertResource(type, "tmcl-b-child-b", parentId);
        long orphan = insertResource(type, "tmcl-b-orphan", null);

        // 授权只挂父实体
        insertPerm(roleId, type, parentId, 16L, false);

        // 单点：查子目标经闭包命中父授权（四入口默认开——管理面写门禁矩阵）
        assertThat(permQueryEngine.hasPermissionByEntityId(TENANT, subjectId, "TMCL_B", childA, "MANAGE"))
            .as("授父 MANAGE → 查子单点判定放行（判定面继承）").isTrue();
        assertThat(permQueryEngine.hasPermissionByEntityId(TENANT, subjectId, "TMCL_B", parentId, "MANAGE"))
            .as("父目标直接命中").isTrue();

        // 批量拒绝闭包回映射：条目挂祖先实体，请求目标不在条目实体集不得误判 DENIED
        assertThat(permQueryEngine.getDeniedEntityIds(TENANT, subjectId, "TMCL_B",
                java.util.Set.of(childA, childB), "MANAGE"))
            .as("批量轨闭包回映射：授父 → 子目标全部允许").isEmpty();
        assertThat(permQueryEngine.getDeniedEntityIds(TENANT, subjectId, "TMCL_B",
                java.util.Set.of(childA, childB, orphan), "MANAGE"))
            .as("无授权孤儿仍拒绝").containsExactlyInAnyOrder(orphan);
    }

    @Test
    @DisplayName("闭包止步同类型：跨类型父边（sync 通道形态）上父授权不覆盖异类型子目标")
    void closureStopsAtTypeBoundary() {
        Integer parentType = ensureResourceType("TMCL_C1");
        Integer childType = ensureResourceType("TMCL_C2");
        ensureOperation(parentType, "MANAGE", 16L, 0L);
        ensureOperation(childType, "MANAGE", 16L, 0L);
        long subjectId = nextSubjectId++;
        long roleId = insertRoleAndBind(subjectId, "tmcl-c");
        long parentId = insertResource(parentType, "tmcl-c-parent", null);
        // 跨类型父子边（sync 通道允许形态，moveResource 写通道会拦但存量/外部同步存在）
        long crossChild = insertResource(childType, "tmcl-c-cross-child", parentId);

        // 授权挂父（父类型行）
        insertPerm(roleId, parentType, parentId, 16L, false);

        assertThat(permQueryEngine.hasPermissionByEntityId(TENANT, subjectId, "TMCL_C2", crossChild, "MANAGE"))
            .as("跨类型父授权不覆盖异类型子目标（闭包止步同类型）").isFalse();
        assertThat(permQueryEngine.hasPermissionByEntityId(TENANT, subjectId, "TMCL_C1", parentId, "MANAGE"))
            .as("父类型目标自身判定不受影响").isTrue();
    }

    @Test
    @DisplayName("软删祖先截断：父实体软删后子目标闭包不再包含父（祖先链 delete_flag=0 过滤）")
    void softDeletedAncestorTruncatesClosure() {
        Integer type = ensureResourceType("TMCL_D");
        ensureOperation(type, "MANAGE", 16L, 0L);
        long subjectId = nextSubjectId++;
        long roleId = insertRoleAndBind(subjectId, "tmcl-d");
        long parentId = insertResource(type, "tmcl-d-parent", null);
        long child = insertResource(type, "tmcl-d-child", parentId);
        insertPerm(roleId, type, parentId, 16L, false);

        assertThat(permQueryEngine.hasPermissionByEntityId(TENANT, subjectId, "TMCL_D", child, "MANAGE"))
            .as("软删前：授父覆盖子").isTrue();

        jdbc.update("UPDATE resource_entity SET delete_flag = 1 WHERE tenant_id = ? AND id = ?", TENANT, parentId);

        assertThat(permQueryEngine.hasPermissionByEntityId(TENANT, subjectId, "TMCL_D", child, "MANAGE"))
            .as("软删后：闭包截断，子目标仅剩自身判定（授权行挂软删父不生效）").isFalse();
    }

    @Test
    @DisplayName("/auth/check inheritMode 接通闭包真实语义：NONE 关 / PARENT 开")
    void authCheckInheritModeParameterWiresClosure() {
        Integer type = ensureResourceType("TMCL_E");
        ensureOperation(type, "VIEW", 1L, 0L);
        long subjectId = nextSubjectId++;
        long roleId = insertRoleAndBind(subjectId, "tmcl-e");
        long parentId = insertResource(type, "tmcl-e-parent", null);
        long child = insertResource(type, "tmcl-e-child", parentId);
        insertPerm(roleId, type, parentId, 1L, false);

        // NONE（缺省）：判定面继承关——「对单点判定结论无效」的旧形态已被接通为真实语义
        PermQuery none = PermQuery.forAuthCheck(TENANT, subjectId, "TMCL_E", "tmcl-e-child", "VIEW");
        assertThat(permQueryEngine.query(none).allowed())
            .as("inheritMode 缺省（关）：授父不覆盖子判定").isFalse();

        // PARENT：闭包开
        PermQuery parent = PermQuery.forAuthCheck(TENANT, subjectId, "TMCL_E", "tmcl-e-child", "VIEW");
        parent.setInheritMode("PARENT");
        PermResult parentResult = permQueryEngine.query(parent);
        assertThat(parentResult.allowed())
            .as("inheritMode=PARENT：目标闭包接通，授父覆盖子判定").isTrue();
    }

    // ===== 种子方法（GoldenFixturePgIT 同款口径） =====

    private long insertRoleAndBind(long subjectId, String caseName) {
        jdbc.update(
            "INSERT INTO abstract_user (id, tenant_id, user_type, external_id, name, enabled, extra, owner_service_code) "
                + "VALUES (?, ?, ?, ?, ?, true, '{}', NULL)",
            subjectId, TENANT, USER_TYPE_ADMIN, String.valueOf(subjectId), "tmcl-" + caseName);
        Long roleId = jdbc.queryForObject(
            "INSERT INTO abstract_role (tenant_id, role_type, external_id, name, status, extra) "
                + "VALUES (?, ?, ?, ?, 1, '{}') RETURNING id",
            Long.class, TENANT, ROLE_TYPE_BASIC, "tmcl-role-" + caseName, "tmcl-" + caseName);
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
            TENANT, typeCode, allocated, "tmcl-" + typeCode);
        return allocated;
    }

    private void ensureOperation(Integer resourceTypeValue, String code, long binaryBit, long inheritMask) {
        Long existing = jdbc.query(
            "SELECT id FROM operation_permission WHERE tenant_id = ? AND resource_type IS NOT DISTINCT FROM ? "
                + "AND code = ? AND delete_flag = 0",
            (rs, i) -> Long.valueOf(rs.getLong("id")), TENANT, resourceTypeValue, code)
            .stream().findFirst().orElse(null);
        if (existing != null) {
            return;
        }
        jdbc.update("UPDATE operation_permission SET delete_flag = id WHERE tenant_id = ? AND resource_type IS NOT DISTINCT FROM ? "
                + "AND binary_bit = ? AND code <> ? AND delete_flag = 0",
            TENANT, resourceTypeValue, binaryBit, code);
        jdbc.update(
            "INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask, delete_flag) "
                + "VALUES (?, ?, ?, ?, ?, ?, 0)",
            TENANT, resourceTypeValue, code, "tmcl-" + code, binaryBit, inheritMask);
    }

    private long insertResource(Integer typeValue, String code, Long parentId) {
        long id = nextResourceId++;
        jdbc.update(
            "INSERT INTO resource_entity (id, tenant_id, parent_id, resource_type, code, code_type, name, status) "
                + "VALUES (?, ?, ?, ?, ?, 'default', ?, 1)",
            id, TENANT, parentId, typeValue, code, "tmcl-" + code);
        return id;
    }

    private void insertPerm(long roleId, Integer typeValue, Long resourceEntityId, long grantedBits, boolean scopeAll) {
        jdbc.update(
            "INSERT INTO role_resource_permission "
                + "(tenant_id, abstract_role_id, resource_entity_id, resource_type, granted_bits, scope_all, "
                + "can_grant, grant_source, delete_flag) VALUES (?, ?, ?, ?, ?, ?, false, 'MANUAL', 0)",
            TENANT, roleId, resourceEntityId, typeValue, grantedBits, scopeAll);
    }
}
