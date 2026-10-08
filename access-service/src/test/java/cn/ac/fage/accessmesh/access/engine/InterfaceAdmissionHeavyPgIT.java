package cn.ac.fage.accessmesh.access.engine;

import cn.ac.fage.accessmesh.access.engine.service.PermissionAdmissionAppService;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceAdmissionResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceAdmissionSnapshotResp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 操作准入大规模验证（T-ACCESS-061 N27，设计 §10.3）。服务端执行/快照预算由
 * EngineLimits 控制（T-PERM-093），网关解码及回源截止、构建重试保护继续有效。
 * <p>
 * 规模面：300 启用路由（两种要求交替）×2001 授权行（1000 实例 VIEW＋1000 实例 EXPORT＋
 * 1 类型级 EXPORT）。锁定三条结构性质：①快照 routes 全量携带不截断；②候选投影按
 * 「类型-操作×条件身份×候选类别」合并——2000 条实例授权不逐实例展开为 2000 个候选；
 *③在线准入对大规模实例集仍按「存在候选资格」一次判定，不做逐实例最终鉴权（最终鉴权
 * 归业务服务）。挂 {@code testcontainers-heavy} 标签（T-PERM-079 拆档口径：日常
 * {@code -DskipHeavyIT=true} 跳过、收口必跑）。
 * </p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("testcontainers")
@Tag("testcontainers-heavy")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false", "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false", "accessmesh.sync.scheduler.enabled=false", "access.tenant.gate-repair.enabled=false",
    "access.platform.bootstrap.enabled=false", "mybatis-flex.configuration.map-underscore-to-camel-case=true",
    "logging.level.cn.ac.fage.accessmesh=WARN"
})
class InterfaceAdmissionHeavyPgIT {

    private static final Long TENANT = 1L;
    private static final String SERVICE = "admission-heavy-it";
    private static final String TYPE = "ADMIT_HEAVY";
    private static final int TYPE_VALUE = 971;
    /** 独占 ID 段（与 InterfaceAdmissionPgIT 的 966x 段隔离）。 */
    private static final long USER_ID = 9_671_001L;
    private static final long ROLE_ID = 9_672_001L;

    /** 路由数（两种要求交替，各 150 条）。 */
    private static final int ROUTE_COUNT = 300;
    /** 实例授权行数（VIEW 1000 + EXPORT 1000）。 */
    private static final int INSTANCE_GRANT_COUNT = 1000;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, InterfaceAdmissionHeavyPgIT.class);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PermissionAdmissionAppService admission;
    @MockBean cn.ac.fage.accessmesh.access.engine.query.QueryGate gate;

    private Long viewOpId;
    private Long exportOpId;

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM resource_api_mapping WHERE service_code = ?", SERVICE);
        jdbc.update("DELETE FROM service_config WHERE service_code = ?", SERVICE);
        jdbc.update("DELETE FROM role_resource_permission WHERE resource_type = ?", TYPE_VALUE);
        jdbc.update("DELETE FROM operation_permission WHERE resource_type = ?", TYPE_VALUE);
        jdbc.update("DELETE FROM resource_entity WHERE resource_type = ?", TYPE_VALUE);
        jdbc.update("DELETE FROM resource_entity WHERE code LIKE 'admit-heavy:%'");
        jdbc.update("DELETE FROM user_role WHERE target_type = 'ROLE' AND target_id = ?", ROLE_ID);
        jdbc.update("DELETE FROM abstract_role WHERE id = ?", ROLE_ID);
        jdbc.update("DELETE FROM abstract_user WHERE id = ?", USER_ID);
        jdbc.update("DELETE FROM type_definition WHERE type_key = 'resource_type' AND type_code = ?", TYPE);

        jdbc.update("INSERT INTO type_definition (tenant_id, type_key, type_code, type_value, name, is_system) "
            + "VALUES (1, 'resource_type', ?, ?, '准入大规模', false)", TYPE, TYPE_VALUE);
        viewOpId = jdbc.queryForObject("INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask) "
            + "VALUES (1, ?, 'VIEW', '查看', 2, 0) RETURNING id", Long.class, TYPE_VALUE);
        exportOpId = jdbc.queryForObject("INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask) "
            + "VALUES (1, ?, 'EXPORT', '导出', 4, 0) RETURNING id", Long.class, TYPE_VALUE);
        jdbc.update("INSERT INTO service_config (tenant_id, service_code, name, status) "
            + "VALUES (1, ?, '准入大规模服务', 1)", SERVICE);
        jdbc.update("INSERT INTO abstract_role (id, tenant_id, role_type, external_id, name, status, extra) "
            + "VALUES (?, 1, 6, 'admit-heavy-role', 'admit-heavy', 1, '{}')", ROLE_ID);
        jdbc.update("INSERT INTO abstract_user (id, tenant_id, user_type, external_id, name, enabled, extra) "
            + "VALUES (?, 1, 1, ?, 'admit-heavy-user', true, '{}')", USER_ID, String.valueOf(USER_ID));
        jdbc.update("INSERT INTO user_role (tenant_id, abstract_user_id, target_type, target_id) "
            + "VALUES (1, ?, 'ROLE', ?)", USER_ID, ROLE_ID);

        // 300 启用路由：VIEW/EXPORT 两种要求交替（同一要求集内去重、无歧义）
        List<Object[]> mappings = new ArrayList<>(ROUTE_COUNT);
        for (int i = 0; i < ROUTE_COUNT; i++) {
            Long apiId = jdbc.queryForObject("INSERT INTO resource_entity (tenant_id, resource_type, code, code_type, name, status) "
                + "SELECT 1, type_value, ?, 'default', ?, 1 FROM type_definition "
                + "WHERE tenant_id = 1 AND type_key = 'resource_type' AND type_code = 'API' AND delete_flag = 0 RETURNING id",
                Long.class, "admit-heavy:api-" + i, "api-" + i);
            Long opId = (i % 2 == 0) ? viewOpId : exportOpId;
            mappings.add(new Object[]{apiId, SERVICE, "POST", "/heavy/r" + i, opId});
        }
        jdbc.batchUpdate("INSERT INTO resource_api_mapping "
            + "(tenant_id, resource_entity_id, service_code, http_method, path_pattern, required_operation_id, maintain_source, enabled, match_order) "
            + "VALUES (1, ?, ?, ?, ?, ?, 'BOOTSTRAP', true, 0)", mappings);

        // 1000 业务实例 + 2000 实例授权行（VIEW 1000 + EXPORT 1000）+ 1 类型级 EXPORT
        List<Object[]> entities = new ArrayList<>(INSTANCE_GRANT_COUNT);
        for (int i = 0; i < INSTANCE_GRANT_COUNT; i++) {
            entities.add(new Object[]{TYPE_VALUE, "admit-heavy:inst-" + i, "实例-" + i});
        }
        jdbc.batchUpdate("INSERT INTO resource_entity (tenant_id, resource_type, code, code_type, name, status) "
            + "VALUES (1, ?, ?, 'default', ?, 1)", entities);
        List<Long> instanceIds = jdbc.queryForList(
            "SELECT id FROM resource_entity WHERE tenant_id = 1 AND resource_type = ? AND code LIKE 'admit-heavy:inst-%' ORDER BY code",
            Long.class, TYPE_VALUE);
        assertThat(instanceIds).hasSize(INSTANCE_GRANT_COUNT);

        List<Object[]> grants = new ArrayList<>(INSTANCE_GRANT_COUNT * 2 + 1);
        for (Long instanceId : instanceIds) {
            grants.add(new Object[]{instanceId, 2L, false});  // VIEW 实例
            grants.add(new Object[]{instanceId, 4L, false});  // EXPORT 实例
        }
        grants.add(new Object[]{null, 4L, true});             // EXPORT 类型级（scope_all）
        jdbc.batchUpdate("INSERT INTO role_resource_permission "
            + "(tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type, scope_all, grant_source) "
            + "VALUES (1, ?, ?, ?, ?, ?, 'MANUAL')",
            grants.stream().map(g -> new Object[]{ROLE_ID, g[0], g[1], TYPE_VALUE, g[2]}).toList());

        when(gate.hasPermissionByCode(any(), any(), any(), any(), any())).thenReturn(true);
        AccessRequestContext.bind(RequestContext.user(TENANT, 100L));
    }

    @AfterEach
    void teardown() {
        AccessRequestContext.clear();
    }

    private cn.ac.fage.accessmesh.perm.common.dto.req.InterfaceAdmissionSnapshotReq snapshotReq() {
        return new cn.ac.fage.accessmesh.perm.common.dto.req.InterfaceAdmissionSnapshotReq(
            "USER", String.valueOf(USER_ID), SERVICE);
    }

    private cn.ac.fage.accessmesh.perm.common.dto.req.InterfaceAdmissionReq admissionReq(String method, String path) {
        return new cn.ac.fage.accessmesh.perm.common.dto.req.InterfaceAdmissionReq(
            "USER", String.valueOf(USER_ID), SERVICE, method, path, Map.of());
    }

    @Test
    void snapshotUnderLargeRouteTableAndGrantVolumeShouldNotTruncateOrExplode() {
        InterfaceAdmissionSnapshotResp snapshot = admission.interfaceAdmissionSnapshot(TENANT, snapshotReq());

        // ① routes 全量携带（300 条不截断、不按主体权限裁剪）
        assertThat(snapshot.routes()).hasSize(ROUTE_COUNT);
        assertThat(snapshot.routes()).extracting("pathPattern")
            .contains("/heavy/r0", "/heavy/r1", "/heavy/r" + (ROUTE_COUNT - 1));
        assertThat(snapshot.routes()).allSatisfy(r -> assertThat(r.requiredPermission()).isNotNull());

        // ② 候选投影按「类型-操作×条件身份×候选类别」合并：2001 条授权（1000 VIEW 实例＋
        // 1000 EXPORT 实例＋1 EXPORT 类型级）→ 恰 3 个候选分支（EXPORT 因实例与类型级两种
        // 候选类别并存保留两条），绝不逐实例展开
        assertThat(snapshot.operationCandidates())
            .extracting("resourceTypeCode", "operationCode", "conditionId", "candidateKind")
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple(TYPE, "VIEW", null, "INSTANCE"),
                org.assertj.core.groups.Tuple.tuple(TYPE, "EXPORT", null, "INSTANCE"),
                org.assertj.core.groups.Tuple.tuple(TYPE, "EXPORT", null, "ALL"));

        assertThat(snapshot.authorizationStage()).isEqualTo("OPERATION_ADMISSION");
        assertThat(snapshot.finalCheckRequired()).isTrue();
    }

    @Test
    void onlineAdmissionUnderLargeInstanceSetShouldDecideByCandidateExistenceNotPerInstance() {
        // VIEW 半边：候选完全来自 1000 条实例授权（无类型级行）——准入按「存在覆盖候选」
        // 一次判定 MAY_ENTER，不逐实例最终鉴权（实例级允许/拒绝是业务服务最终检查的事，N01 分层）
        InterfaceAdmissionResp view = admission.interfaceAdmission(TENANT, admissionReq("POST", "/heavy/r0"));
        assertThat(view.decision()).isEqualTo("MAY_ENTER");
        assertThat(view.requiredPermission().resourceTypeCode()).isEqualTo(TYPE);
        assertThat(view.requiredPermission().operationCode()).isEqualTo("VIEW");

        // EXPORT 半边：类型级 scope_all 候选
        InterfaceAdmissionResp export = admission.interfaceAdmission(TENANT, admissionReq("POST", "/heavy/r1"));
        assertThat(export.decision()).isEqualTo("MAY_ENTER");
        assertThat(export.requiredPermission().operationCode()).isEqualTo("EXPORT");
    }
}
