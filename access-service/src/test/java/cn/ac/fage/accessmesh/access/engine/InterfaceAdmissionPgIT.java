package cn.ac.fage.accessmesh.access.engine;

import cn.ac.fage.accessmesh.access.engine.service.PermissionAdmissionAppService;
import cn.ac.fage.accessmesh.access.engine.util.InterfaceAdmissionSnapshotAssembler;
import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.cache.PermInvalidationPublisher;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.resource.dto.RequiredPermission;
import cn.ac.fage.accessmesh.access.resource.dto.req.ApiMappingAddReq;
import cn.ac.fage.accessmesh.access.resource.dto.req.ServiceConfigReq;
import cn.ac.fage.accessmesh.access.resource.entity.ServiceConfig;
import cn.ac.fage.accessmesh.access.resource.mapper.ServiceConfigMapper;
import cn.ac.fage.accessmesh.access.resource.service.ResourceManageAppService;
import cn.ac.fage.accessmesh.access.resource.service.ServiceConfigAppService;
import cn.ac.fage.accessmesh.access.resource.service.domain.ResourceApiMappingDomainService;
import cn.ac.fage.accessmesh.access.rule.dto.req.ConditionUpdateReq;
import cn.ac.fage.accessmesh.access.rule.service.ConditionAppService;
import cn.ac.fage.accessmesh.common.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 操作准入端点与快照构建的真实数据库验收（T-ACCESS-059，契约总册 §25.2）。
 * <p>
 * 覆盖：快照全量路由与候选投影（ALL/条件内联/CONTEXT_DEFERRED）、在线判定四态与
 * reason 词表、20070 路由歧义 / 20071 配置故障（引用悬空/LEGACY 模式）、配置代次
 * 逐写入口递增回归锁、N21 构建期自一致（真库并发写路径——代次复读经 flushCache 落库，
 * 非 SpyBean 喂数列）、停用/未登记空路由快照（外评拍板：网关 DENY 语义对称）、
 * 保存不回写代次（整实体回写倒退锁）、凭证服务归属约束、HTTP 契约冒烟、
 * T-ACCESS-060 N19 条件/操作类型超集反查广播与 N23 模式切换演练。
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false", "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false", "accessmesh.sync.scheduler.enabled=false",
    "access.bootstrap.enabled=false", "mybatis-flex.configuration.map-underscore-to-camel-case=true",
    "logging.level.cn.ac.fage.accessmesh=WARN"
})
class InterfaceAdmissionPgIT {

    private static final Long TENANT = 1L;
    private static final String SERVICE = "admission-it";
    /** N19 负向服务：只映射另一类型（ADMIT3）的操作，不受 ADMIT2 条件/操作变更影响。 */
    private static final String SERVICE_OTHER = "admission-it-other";
    private static final String TYPE = "ADMIT2";
    private static final int TYPE_VALUE = 961;
    private static final String TYPE2 = "ADMIT3";
    private static final int TYPE_VALUE2 = 962;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, InterfaceAdmissionPgIT.class);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PermissionAdmissionAppService admission;
    @Autowired ResourceManageAppService resources;
    @Autowired ServiceConfigAppService serviceConfigs;
    @Autowired ConditionAppService conditions;
    @Autowired ResourceApiMappingDomainService apiMappingReads;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean cn.ac.fage.accessmesh.access.engine.query.QueryGate gate;
    @SpyBean ServiceConfigMapper serviceConfigMapper;
    @SpyBean InterfaceAdmissionSnapshotAssembler snapshotAssembler;
    @SpyBean PermInvalidationPublisher invalidationPublisher;

    /** 用例序号（每用例唯一 subject/role ID 段内递增）。 */
    private static final java.util.concurrent.atomic.AtomicLong SEQ = new java.util.concurrent.atomic.AtomicLong();

    private Long viewOpId;
    private Long exportOpId;
    private Long roleId;
    private Long subjectId;
    private Long conditionId;

    @BeforeEach
    void setup() {
        // 本类独占 ItInfra 数据库；每用例重建自身事实（固定私有 ID 段，防跨类冲突）
        jdbc.update("DELETE FROM resource_api_mapping WHERE service_code IN (?, ?)", SERVICE, SERVICE_OTHER);
        jdbc.update("DELETE FROM service_config WHERE service_code IN (?, ?)", SERVICE, SERVICE_OTHER);
        jdbc.update("DELETE FROM role_resource_permission WHERE resource_type IN (?, ?)", TYPE_VALUE, TYPE_VALUE2);
        jdbc.update("DELETE FROM permission_condition WHERE code LIKE 'admit-it:%'");
        jdbc.update("DELETE FROM operation_permission WHERE resource_type IN (?, ?)", TYPE_VALUE, TYPE_VALUE2);
        jdbc.update("DELETE FROM resource_entity WHERE resource_type IN (?, ?)", TYPE_VALUE, TYPE_VALUE2);
        jdbc.update("DELETE FROM resource_entity WHERE code LIKE 'admit-it:%'");
        jdbc.update("DELETE FROM user_role WHERE target_type = 'ROLE' AND target_id BETWEEN 9662001 AND 9662999");
        jdbc.update("DELETE FROM abstract_role WHERE id BETWEEN 9662001 AND 9662999");
        jdbc.update("DELETE FROM abstract_user WHERE id BETWEEN 9661001 AND 9661999");

        jdbc.update("INSERT INTO type_definition (tenant_id, type_key, type_code, type_value, name, is_system) "
            + "VALUES (1, 'resource_type', ?, ?, '准入验收', false) ON CONFLICT DO NOTHING", TYPE, TYPE_VALUE);
        jdbc.update("INSERT INTO type_definition (tenant_id, type_key, type_code, type_value, name, is_system) "
            + "VALUES (1, 'resource_type', ?, ?, '准入负向类型', false) ON CONFLICT DO NOTHING", TYPE2, TYPE_VALUE2);
        viewOpId = jdbc.queryForObject("INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask) "
            + "VALUES (1, ?, 'VIEW', '查看', 2, 0) RETURNING id", Long.class, TYPE_VALUE);
        exportOpId = jdbc.queryForObject("INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask) "
            + "VALUES (1, ?, 'EXPORT', '导出', 4, 2) RETURNING id", Long.class, TYPE_VALUE);
        jdbc.update("INSERT INTO service_config (tenant_id, service_code, name, status) "
            + "VALUES (1, ?, '准入验收服务', 1)", SERVICE);
        // 每用例唯一 subject/role ID：引擎 EFFECTIVE_ROLES/ROLE_PERM_SNAPSHOT 为 L2 缓存，
        // 复用同 ID 会读到前序用例缓存的角色集（空/旧），无法用缓存失效验证判定语义
        long seq = SEQ.incrementAndGet();
        roleId = jdbc.queryForObject("INSERT INTO abstract_role (id, tenant_id, role_type, external_id, name, status, extra) "
            + "VALUES (?, 1, 6, 'admit-it-role-" + seq + "', 'admit-it', 1, '{}') RETURNING id", Long.class, 9_662_000L + seq);
        subjectId = jdbc.queryForObject("INSERT INTO abstract_user (id, tenant_id, user_type, external_id, name, enabled, extra) "
            + "VALUES (?, 1, 1, ?, 'admit-it-user', true, '{}') RETURNING id", Long.class,
            9_661_000L + seq, String.valueOf(9_661_000L + seq));

        when(gate.hasPermissionByCode(any(), any(), any(), any(), any())).thenReturn(true);
        AccessRequestContext.bind(RequestContext.user(TENANT, 100L));
    }

    @AfterEach
    void teardown() {
        AccessRequestContext.clear();
    }

    // ─── 夹具 ───

    private void bindRole() {
        jdbc.update("INSERT INTO user_role (tenant_id, abstract_user_id, target_type, target_id) "
            + "VALUES (1, ?, 'ROLE', ?)", subjectId, roleId);
    }

    private Long grant(Long entityId, long bits, boolean scopeAll, Long conditionId, Long dependOn) {
        return jdbc.queryForObject("INSERT INTO role_resource_permission "
            + "(tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type, scope_all, condition_id, depend_on, grant_source) "
            + "VALUES (1, ?, ?, ?, ?, ?, ?, ?, 'MANUAL') RETURNING id",
            Long.class, roleId, entityId, bits, TYPE_VALUE, scopeAll, conditionId, dependOn);
    }

    private Long registerApi(String routeKey) {
        return jdbc.queryForObject("INSERT INTO resource_entity (tenant_id, resource_type, code, code_type, name, status) "
            + "SELECT 1, type_value, ?, 'default', ?, 1 FROM type_definition "
            + "WHERE tenant_id = 1 AND type_key = 'resource_type' AND type_code = 'API' AND delete_flag = 0 RETURNING id",
            Long.class, "admit-it:" + routeKey, routeKey);
    }

    private void insertMapping(Long apiId, String method, String path, Long operationId) {
        insertMapping(SERVICE, apiId, method, path, operationId);
    }

    private void insertMapping(String serviceCode, Long apiId, String method, String path, Long operationId) {
        jdbc.update("INSERT INTO resource_api_mapping "
            + "(tenant_id, resource_entity_id, service_code, http_method, path_pattern, required_operation_id, maintain_source, enabled, match_order) "
            + "VALUES (1, ?, ?, ?, ?, ?, 'BOOTSTRAP', true, 0)", apiId, serviceCode, method, path, operationId);
    }

    /** 建负向类型（ADMIT3）的 VIEW 操作并返回操作 ID（N19 负向服务映射消费）。 */
    private Long otherTypeViewOp() {
        return jdbc.queryForObject("INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask) "
            + "VALUES (1, ?, 'VIEW', '查看', 2, 0) RETURNING id", Long.class, TYPE_VALUE2);
    }

    /** 捕获最近一次失效广播的 serviceCodes（调用前先 clearInvocations 隔离前序发布）。 */
    @SuppressWarnings("unchecked")
    private Set<String> captureBroadcastServiceCodes() {
        org.mockito.ArgumentCaptor<Set<String>> captor = org.mockito.ArgumentCaptor.forClass(Set.class);
        verify(invalidationPublisher).publish(any(), any(), any(), captor.capture());
        return captor.getValue();
    }

    private Long insertIpWhitelistCondition(String cidr) {
        return jdbc.queryForObject("INSERT INTO permission_condition (tenant_id, code, name, condition_rules, enabled, gateway_evaluable, source) "
            + "VALUES (1, ?, 'admit-it 条件', ?::jsonb, true, true, 'MANAGED') RETURNING id", Long.class,
            "admit-it:cond-" + System.nanoTime(),
            "{\"logic\":\"AND\",\"items\":[{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"" + cidr + "\"]}}]}");
    }

    private cn.ac.fage.accessmesh.perm.common.dto.req.InterfaceAdmissionSnapshotReq snapshotReq() {
        return new cn.ac.fage.accessmesh.perm.common.dto.req.InterfaceAdmissionSnapshotReq(
            "USER", String.valueOf(subjectId), SERVICE);
    }

    private cn.ac.fage.accessmesh.perm.common.dto.req.InterfaceAdmissionReq admissionReq(
            String method, String path, String clientIp) {
        return new cn.ac.fage.accessmesh.perm.common.dto.req.InterfaceAdmissionReq(
            "USER", String.valueOf(subjectId), SERVICE, method, path,
            clientIp == null ? null : Map.of("clientIp", clientIp));
    }

    private long generation() {
        return jdbc.queryForObject("SELECT config_generation FROM service_config WHERE service_code = ?",
            Long.class, SERVICE);
    }

    // ─── 快照构建 ───

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder operationRequest(
            String action, Map<String, String> body) throws Exception {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec("test-signature-secret-for-access-service"
            .getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = java.util.HexFormat.of().formatHex(mac.doFinal(
            (subjectId + "|1|" + timestamp).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return post("/api/access/operation-permission/" + action)
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Internal-Secret", "test-internal-secret-for-access-service")
            .header("X-Tenant-Id", "1")
            .header("X-User-Id", subjectId)
            .header("X-User-Signature", signature)
            .header("X-Signature-Timestamp", timestamp)
            .content(json.writeValueAsString(body));
    }

    @Test
    void should_keepSnapshotBuildable_whenApiRejectsCollidingOperationBits() throws Exception {
        bindRole();
        grant(null, 2L, true, null, null);
        insertMapping(registerApi("single-bit"), "POST", "/api/demo/view", viewOpId);
        // 模拟内置类型：跳过自定义类型的 AUTHORITY_ROOT 补种 CHECK，覆盖原缺口。
        jdbc.update("UPDATE type_definition SET is_system = true WHERE tenant_id = 1 "
            + "AND type_key = 'resource_type' AND type_code = ?", TYPE);
        try {
            var createResult = mvc.perform(operationRequest("create", Map.of("resourceTypeCode", TYPE,
                    "code", "COLLISION", "name", "碰撞位", "binaryBit", "3", "inheritMask", "0")))
                .andExpect(status().isOk()).andReturn();
            // 先走真实快照构建：旧写入口允许位 3 入库，此处会因覆盖 VIEW=2 而失败。
            assertThat(admission.interfaceAdmissionSnapshot(TENANT, snapshotReq()).operationCandidates()).isNotEmpty();
            assertThat(json.readTree(createResult.getResponse().getContentAsString()).path("code").asInt())
                .isEqualTo(20044);
            mvc.perform(operationRequest("update", Map.of("resourceTypeCode", TYPE, "code", "EXPORT", "binaryBit", "3")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(20044));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM operation_permission WHERE resource_type = ? "
                + "AND binary_bit = 3 AND delete_flag = 0", Integer.class, TYPE_VALUE)).isZero();
            assertThat(jdbc.queryForObject("SELECT binary_bit FROM operation_permission WHERE id = ?",
                Long.class, exportOpId)).isEqualTo(4L);
            assertThat(admission.interfaceAdmissionSnapshot(TENANT, snapshotReq()).operationCandidates()).isNotEmpty();
            assertThat(admission.interfaceAdmission(TENANT, admissionReq("POST", "/api/demo/view", null)).decision())
                .isEqualTo("MAY_ENTER");
        } finally {
            jdbc.update("UPDATE type_definition SET is_system = false WHERE tenant_id = 1 "
                + "AND type_key = 'resource_type' AND type_code = ?", TYPE);
        }
    }

    @Test
    void should_report20071OnlyForRelatedDamagedBits_whenBuildingSnapshot() throws Exception {
        bindRole();
        grant(null, 2L, true, null, null);
        insertMapping(registerApi("legacy-bit"), "POST", "/api/demo/view", viewOpId);
        Long damagedId = jdbc.queryForObject("INSERT INTO operation_permission "
            + "(tenant_id, resource_type, code, name, binary_bit, inherit_mask) "
            + "VALUES (1, ?, 'DAMAGED', '存量坏位', 12, 0) RETURNING id", Long.class, TYPE_VALUE);
        assertThat(admission.interfaceAdmissionSnapshot(TENANT, snapshotReq()).operationCandidates()).isNotEmpty();
        jdbc.update("UPDATE operation_permission SET binary_bit = 3 WHERE id = ?", damagedId);
        mvc.perform(post("/api/access/auth/interface-admission-snapshot")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Internal-Secret", "test-internal-secret-for-access-service")
                .header("X-Tenant-Id", "1")
                .content(json.writeValueAsString(snapshotReq())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(20071));
        mvc.perform(post("/api/access/auth/interface-admission")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Internal-Secret", "test-internal-secret-for-access-service")
                .header("X-Tenant-Id", "1")
                .content(json.writeValueAsString(admissionReq("POST", "/api/demo/view", null))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(20071));
    }

    @Test
    void snapshotShouldCarryFullRoutesAndProjectedCandidates() {
        bindRole();
        Long apiView = registerApi("view");
        insertMapping(apiView, "POST", "/api/demo/view", viewOpId);
        Long apiAll = registerApi("wild");
        insertMapping(apiAll, "POST", "/api/demo/**", exportOpId);
        jdbc.update("DELETE FROM role_resource_permission WHERE resource_type = ? AND scope_all = true", TYPE_VALUE);
        grant(null, 2L, true, null, null);                       // 类型级 VIEW（ALL 候选）
        conditionId = insertIpWhitelistCondition("10.0.0.0/8");
        Long instance = jdbc.queryForObject("INSERT INTO resource_entity (tenant_id, resource_type, code, code_type, name, status) "
            + "VALUES (1, ?, 'admit-r1', 'default', '实例', 1) RETURNING id", Long.class, TYPE_VALUE);
        // 条件实例候选：bits=EXPORT 专属位 4（不与类型级 VIEW 位重叠——VIEW 经 inheritMask
        // 覆盖 EXPORT 有效位，若实例行也用位 2 两要求候选会互相串档）
        grant(instance, 4L, false, conditionId, null);

        var snapshot = admission.interfaceAdmissionSnapshot(TENANT, snapshotReq());

        assertThat(snapshot.schemaVersion()).isEqualTo(1);
        assertThat(snapshot.authorizationStage()).isEqualTo("OPERATION_ADMISSION");
        assertThat(snapshot.finalCheckRequired()).isTrue();
        assertThat(snapshot.serviceCode()).isEqualTo(SERVICE);
        assertThat(snapshot.configGeneration()).isGreaterThanOrEqualTo(0L);
        assertThat(snapshot.expiresAt()).isAfter(snapshot.generatedAt());
        // routes 携带完整启用路由（不按主体权限裁剪）
        assertThat(snapshot.routes()).extracting("httpMethod", "pathPattern")
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("POST", "/api/demo/view"),
                org.assertj.core.groups.Tuple.tuple("POST", "/api/demo/**"));
        // 候选投影：VIEW 类型级无条件 ALL 分支 + EXPORT 条件实例分支（规则内联）
        assertThat(snapshot.operationCandidates()).anySatisfy(candidate -> {
            assertThat(candidate.resourceTypeCode()).isEqualTo(TYPE);
            assertThat(candidate.operationCode()).isEqualTo("VIEW");
            assertThat(candidate.conditionId()).isNull();
            assertThat(candidate.candidateKind()).isEqualTo("ALL");
            assertThat(candidate.gatewayEvaluable()).isTrue();
        });
        assertThat(snapshot.operationCandidates()).anySatisfy(candidate -> {
            assertThat(candidate.operationCode()).isEqualTo("EXPORT");
            assertThat(candidate.conditionId()).isEqualTo(conditionId);
            assertThat(candidate.gatewayEvaluable()).isTrue();
            assertThat(candidate.conditionRules()).contains("IP_WHITELIST");
        });
    }

    @Test
    void snapshotShouldKeepContextDeferredCandidateDistinctFromMainAuthorization() {
        bindRole();
        Long api = registerApi("view");
        insertMapping(api, "POST", "/api/demo/view", viewOpId);
        Long parent = grant(null, 2L, true, insertIpWhitelistCondition("10.0.0.0/8"), null);
        jdbc.update("INSERT INTO resource_entity (tenant_id, resource_type, code, code_type, name, status) "
            + "VALUES (1, ?, 'admit-child', 'default', '子行', 1)", TYPE_VALUE);
        Long childEntity = jdbc.queryForObject(
            "SELECT id FROM resource_entity WHERE tenant_id = 1 AND code = 'admit-child'", Long.class);
        grant(childEntity, 2L, false, null, parent);             // 结构合法上下文子行

        var snapshot = admission.interfaceAdmissionSnapshot(TENANT, snapshotReq());
        // 主授权条件分支（可评估，内联）与 CONTEXT_DEFERRED 分支（恒不可本地评估）各自独立
        assertThat(snapshot.operationCandidates()).anySatisfy(candidate -> {
            assertThat(candidate.candidateKind()).isEqualTo("CONTEXT_DEFERRED");
            assertThat(candidate.gatewayEvaluable()).isFalse();
            assertThat(candidate.conditionId()).isNull();
        });
        assertThat(snapshot.operationCandidates()).anySatisfy(candidate ->
            assertThat(candidate.candidateKind()).isEqualTo("ALL"));
    }

    @Test
    void snapshotShouldReturnEmptyCandidatesForSubjectWithoutRole() {
        Long api = registerApi("view");
        insertMapping(api, "POST", "/api/demo/view", viewOpId);
        var snapshot = admission.interfaceAdmissionSnapshot(TENANT, snapshotReq());
        assertThat(snapshot.routes()).hasSize(1);
        assertThat(snapshot.operationCandidates()).isEmpty();
    }


    @Test
    void snapshotShouldReturnEmptySnapshotForDisabledOrUnregisteredService() {
        // 外评拍板（2026-09-28）：停用/未登记＝该服务无参与授权的路由 → 空路由成功快照，
        // 网关本地无命中按无注册匹配拒绝（DENY→403，与在线 notRegistered 语义对称）；
        // 不抛 20071——停用是例行管理操作，抛配置故障会使网关对该服务全量 503 重试风暴
        jdbc.update("UPDATE service_config SET status = 0 WHERE service_code = ?", SERVICE);
        var disabled = admission.interfaceAdmissionSnapshot(TENANT, snapshotReq());
        assertThat(disabled.routes()).isEmpty();
        assertThat(disabled.operationCandidates()).isEmpty();
        assertThat(disabled.authorizationStage()).isEqualTo("OPERATION_ADMISSION");
        assertThat(disabled.expiresAt()).isAfter(disabled.generatedAt());
        jdbc.update("DELETE FROM service_config WHERE service_code = ?", SERVICE);
        var unregistered = admission.interfaceAdmissionSnapshot(TENANT, snapshotReq());
        assertThat(unregistered.routes()).isEmpty();
        assertThat(unregistered.operationCandidates()).isEmpty();
        assertThat(unregistered.configGeneration()).isZero();
    }

    @Test
    void snapshotShouldTreatDanglingOperationReferenceAsConfigFault() {
        Long api = registerApi("view");
        insertMapping(api, "POST", "/api/demo/view", viewOpId);
        jdbc.update("UPDATE resource_api_mapping SET required_operation_id = NULL WHERE service_code = ?", SERVICE);
        assertThatThrownBy(() -> admission.interfaceAdmissionSnapshot(TENANT, snapshotReq()))
            .isInstanceOfSatisfying(BizException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(20071));
    }

    @Test
    void snapshotShouldTreatApiTypeRequirementAsConfigFault() {
        // T-ACCESS-062 后 API 授权全灭：API 类型操作作准入要求恒无候选（绑定即恒 deny 死配置）——
        // 读侧兜底报 20071 配置故障（写侧共用保存入口已拒绝，此处锁存量/直写脏数据形态；
        // 旧实现只识别悬空/损坏引用，对 API 类型要求返回正常快照，本用例在其下失败）
        Long apiAccessOpId = jdbc.queryForObject(
            "SELECT op.id FROM operation_permission op WHERE op.tenant_id = 1 AND op.delete_flag = 0 "
                + "AND op.code = 'ACCESS' AND op.resource_type = "
                + "(SELECT type_value FROM type_definition WHERE tenant_id = 1 AND type_key = 'resource_type' "
                + "AND type_code = 'API' AND delete_flag = 0)", Long.class);
        Long api = registerApi("apireq");
        insertMapping(api, "POST", "/api/demo/apireq", apiAccessOpId);
        assertThatThrownBy(() -> admission.interfaceAdmissionSnapshot(TENANT, snapshotReq()))
            .isInstanceOfSatisfying(BizException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(20071));
    }

    // ─── 在线判定 ───

    @Test
    void onlineAdmissionShouldReturnMayEnterForCoveringCandidate() {
        bindRole();
        Long api = registerApi("view");
        insertMapping(api, "POST", "/api/demo/view", viewOpId);
        grant(null, 2L, true, null, null);

        var resp = admission.interfaceAdmission(TENANT, admissionReq("POST", "/api/demo/view", null));
        assertThat(resp.decision()).isEqualTo("MAY_ENTER");
        assertThat(resp.finalCheckRequired()).isTrue();
        assertThat(resp.reason()).isNull();
        assertThat(resp.requiredPermission()).isEqualTo(
            new cn.ac.fage.accessmesh.perm.common.dto.resp.AdmissionRequirement(TYPE, "VIEW"));
    }

    @Test
    void onlineAdmissionShouldReturnNoRoleAndNoCandidateAndConditionNotMet() {
        Long api = registerApi("view");
        insertMapping(api, "POST", "/api/demo/view", viewOpId);
        // 主体 A（无角色绑定）→ NO_ROLE（EFFECTIVE_ROLES 为 L2 缓存，A 的空集不污染 B）
        assertThat(admission.interfaceAdmission(TENANT, admissionReq("POST", "/api/demo/view", null)).reason())
            .isEqualTo("NO_ROLE");
        // 主体 B（同测试新建、绑定角色）：有角色无候选 → NO_CANDIDATE
        long seq = SEQ.incrementAndGet();
        Long subjectB = jdbc.queryForObject(
            "INSERT INTO abstract_user (id, tenant_id, user_type, external_id, name, enabled, extra) "
                + "VALUES (?, 1, 1, ?, 'admit-it-user-b', true, '{}') RETURNING id",
            Long.class, 9_661_000L + seq, String.valueOf(9_661_000L + seq));
        jdbc.update("INSERT INTO user_role (tenant_id, abstract_user_id, target_type, target_id) "
            + "VALUES (1, ?, 'ROLE', ?)", subjectB, roleId);
        var reqB = new cn.ac.fage.accessmesh.perm.common.dto.req.InterfaceAdmissionReq(
            "USER", String.valueOf(subjectB), SERVICE, "POST", "/api/demo/view", null);
        var reqBip = new cn.ac.fage.accessmesh.perm.common.dto.req.InterfaceAdmissionReq(
            "USER", String.valueOf(subjectB), SERVICE, "POST", "/api/demo/view", Map.of("clientIp", "192.168.1.9"));
        var reqBok = new cn.ac.fage.accessmesh.perm.common.dto.req.InterfaceAdmissionReq(
            "USER", String.valueOf(subjectB), SERVICE, "POST", "/api/demo/view", Map.of("clientIp", "10.1.2.3"));
        assertThat(admission.interfaceAdmission(TENANT, reqB).reason()).isEqualTo("NO_CANDIDATE");
        // 有候选但条件不通过 → CONDITION_NOT_MET
        grant(null, 2L, true, insertIpWhitelistCondition("10.0.0.0/8"), null);
        assertThat(admission.interfaceAdmission(TENANT, reqBip).reason()).isEqualTo("CONDITION_NOT_MET");
        // 同候选同 IP 通过 → MAY_ENTER（本地与在线同规则）
        assertThat(admission.interfaceAdmission(TENANT, reqBok).decision()).isEqualTo("MAY_ENTER");
    }

    @Test
    void onlineAdmissionShouldDenyUnregisteredPathEvenForAllHolder() {
        bindRole();
        grant(null, 2L, true, null, null);
        // 无任何映射：未注册路径拒绝，ALL 不放行未注册
        assertThat(admission.interfaceAdmission(TENANT, admissionReq("POST", "/api/unknown", null)).reason())
            .isEqualTo("API_NOT_REGISTERED");
        Long api = registerApi("view");
        insertMapping(api, "POST", "/api/demo/view", viewOpId);
        // 已注册但不匹配（方法/路径不命中）
        assertThat(admission.interfaceAdmission(TENANT, admissionReq("GET", "/api/demo/view", null)).reason())
            .isEqualTo("API_NOT_REGISTERED");
    }

    @Test
    void onlineAdmissionShouldRejectAmbiguousRequirementsAsConfigFault() {
        Long api = registerApi("view");
        insertMapping(api, "POST", "/api/demo/**", viewOpId);
        Long other = registerApi("export");
        insertMapping(other, "POST", "/api/demo/export", exportOpId);
        // /api/demo/export 同时命中 VIEW 通配与 EXPORT 精确且要求不同 → 20070 配置故障
        assertThatThrownBy(() -> admission.interfaceAdmission(TENANT, admissionReq("POST", "/api/demo/export", null)))
            .isInstanceOfSatisfying(BizException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(20070));
        // 同要求多匹配去重为正常判定（/api/demo/other 仅命中通配）
        bindRole();
        grant(null, 2L, true, null, null);
        assertThat(admission.interfaceAdmission(TENANT, admissionReq("POST", "/api/demo/other", null)).decision())
            .isEqualTo("MAY_ENTER");
    }

    @Test
    void onlineAdmissionShouldDenyUnknownSubject() {
        Long api = registerApi("view");
        insertMapping(api, "POST", "/api/demo/view", viewOpId);
        var resp = admission.interfaceAdmission(TENANT,
            new cn.ac.fage.accessmesh.perm.common.dto.req.InterfaceAdmissionReq(
                "USER", "no-such-subject", SERVICE, "POST", "/api/demo/view", null));
        assertThat(resp.decision()).isEqualTo("DENY");
        assertThat(resp.reason()).isEqualTo("USER_NOT_FOUND");
    }

    // ─── 配置代次（计数列载体回归锁：逐写入口 +1） ───

    @Test
    void configGenerationShouldIncrementOnEveryMappingWritePath() {
        long base = generation();
        Long api = registerApi("gen");
        // ① 手工保存（共用保存入口）
        var saved = resources.addApiMapping(TENANT, new ApiMappingAddReq(api, SERVICE, "POST", "/api/gen", 0, true, null,
            new RequiredPermission(TYPE, "VIEW")));
        assertThat(generation()).isEqualTo(base + 1);
        // ② 更新（保留既有要求，路径变化）
        resources.updateApiMapping(TENANT, new cn.ac.fage.accessmesh.access.resource.dto.req.ApiMappingUpdateReq(
            api, saved.id(), "POST", "/api/gen2", 0, true, null, null, null));
        assertThat(generation()).isEqualTo(base + 2);
        // ③ 删除
        resources.removeApiMappingsByIds(TENANT, List.of(saved.id()), 100L);
        assertThat(generation()).isEqualTo(base + 3);
    }

    @Test
    void snapshotBuildShouldDiscardAndRebuildWhenGenerationChangesMidBuild() throws Exception {
        // N21 真库并发路径（2026-09-28 外评修正：SpyBean doReturn 喂代次序列不经 MyBatis
        // 执行器，测不出 SESSION 级一级缓存吞掉同语句复读——自一致校验曾在生产恒失效）：
        // 装配器首次解析挂起，另一线程独立连接真实提交「新增路由+代次递增」，
        // 构建事务复读（flushCache 落库）必须看到新代次并废弃重建，不得发出混合快照
        Long api = registerApi("view");
        insertMapping(api, "POST", "/api/demo/view", viewOpId);
        Long apiExport = registerApi("export");
        long current = generation();
        CountDownLatch buildStarted = new CountDownLatch(1);
        CountDownLatch concurrentWriteDone = new CountDownLatch(1);
        AtomicBoolean firstBuild = new AtomicBoolean();
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (firstBuild.compareAndSet(false, true)) {
                buildStarted.countDown();
                // 超时防御：写线程异常也放行构建，失败留给断言而非悬挂
                concurrentWriteDone.await(10, TimeUnit.SECONDS);
            }
            return result;
        }).when(snapshotAssembler).resolveRouteRequirements(any(), anyList());

        Thread concurrentWriter = new Thread(() -> {
            try {
                buildStarted.await(10, TimeUnit.SECONDS);
                // 独立线程=独立连接自动提交，模拟并发事务提交（映射写路径同事务递增代次）
                jdbc.update("INSERT INTO resource_api_mapping "
                    + "(tenant_id, resource_entity_id, service_code, http_method, path_pattern, required_operation_id, maintain_source, enabled, match_order) "
                    + "VALUES (1, ?, ?, 'POST', '/api/demo/export', ?, 'BOOTSTRAP', true, 0)",
                    apiExport, SERVICE, exportOpId);
                jdbc.update("UPDATE service_config SET config_generation = config_generation + 1 "
                    + "WHERE tenant_id = 1 AND service_code = ?", SERVICE);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                concurrentWriteDone.countDown();
            }
        });
        concurrentWriter.start();

        var snapshot = admission.interfaceAdmissionSnapshot(TENANT, snapshotReq());
        concurrentWriter.join(10_000);

        // 废弃重建生效：以新代次返回，且路由集含并发提交的新路由（无旧集混合）
        assertThat(snapshot.configGeneration()).isEqualTo(current + 1);
        assertThat(snapshot.routes()).extracting("pathPattern").contains("/api/demo/export");
    }

    @Test
    void serviceConfigSaveShouldNotRegressGenerationOnConcurrentBump() {
        // 外评修正回归锁：保存服务配置不得把加载时的 config_generation 整实体写回库
        // （并发递增后倒退，快照自一致校验依赖代次单调）。落库前在同事务连接先行 +1，
        // 等价复现「写回旧值覆盖新值」的竞争窗口：旧实现 update(config) 提交后倒退为旧值
        long base = generation();
        doAnswer(invocation -> {
            jdbc.update("UPDATE service_config SET config_generation = config_generation + 1 "
                + "WHERE tenant_id = 1 AND service_code = ?", SERVICE);
            return invocation.callRealMethod();
        }).when(serviceConfigMapper).update(any(ServiceConfig.class));
        serviceConfigs.saveServiceConfig(TENANT, new ServiceConfigReq(
            SERVICE, "改名不触代次", null, null, null, null, null, null, null), 100L);
        assertThat(generation()).isEqualTo(base + 1);
    }

    // ─── T-ACCESS-060：失效广播反查（N19）与模式切换演练（N23 access 侧） ───

    @Test
    void conditionUpdateShouldBroadcastMappedServicesByGrantTypeSuperset() {
        // N19：条件同 ID 改规则 → 相关服务准入快照失效。安全超集=「引用条件的授权类型→
        // 该类型所需操作→映射服务」——映射行 resource_entity_id 指向 API 登记实体，旧
        // 「授权资源=API 资源」等值联接在此数据形态下返回空（改条件不广播任何服务）；
        // markConditions 通道反查由 PermissionChangeAspect flush 统一承载（红跑证据：
        // 屏蔽 flush 反查块后本用例 serviceCodes 断言失败，见任务卡验收对照）
        Long apiA = registerApi("n19-a");
        insertMapping(apiA, "POST", "/api/n19/a", viewOpId);
        // 负向服务：只映射 ADMIT3 的操作，不受 ADMIT2 条件变更影响
        insertMapping(SERVICE_OTHER, registerApi("n19-b"), "POST", "/api/n19/b", otherTypeViewOp());

        String condCode = "admit-it:n19-" + SEQ.incrementAndGet();
        Long cond = jdbc.queryForObject(
            "INSERT INTO permission_condition (tenant_id, code, name, condition_rules, enabled, gateway_evaluable, source) "
                + "VALUES (1, ?, 'n19 条件', ?::jsonb, true, true, 'MANAGED') RETURNING id", Long.class, condCode,
            "{\"logic\":\"AND\",\"items\":[{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"10.0.0.0/8\"]}}]}");
        grant(null, 2L, true, cond, null);                        // ADMIT2 类型级 VIEW 带条件

        clearInvocations(invalidationPublisher);
        // 真实改规则（外评 P3 处置：仅改名锁不住「规则变化必须失效」——未来把 mark 收窄为
        // 仅名称变化不登记时本用例须转红），载荷换成不同 CIDR 的合法可下发规则
        conditions.updateCondition(TENANT, new ConditionUpdateReq(condCode, "n19 改规则",
            "{\"logic\":\"AND\",\"items\":[{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"192.168.0.0/16\"]}}]}",
            null, null, null, null), 100L);

        // 广播恰含映射 ADMIT2 操作的正向服务；负向服务不在集合中
        assertThat(captureBroadcastServiceCodes()).containsExactly(SERVICE);
    }

    @Test
    void operationTypeSupersetShouldSelectOnlyServicesMappingThatType() {
        // N19 另半边（操作定义/覆盖变更反查，T-ACCESS-058 已实现、本卡补测试锁）：
        // 「类型→该类型所需操作→映射服务」超集只命中映射该类型操作的服务
        insertMapping(registerApi("op-a"), "POST", "/api/op/a", viewOpId);
        insertMapping(SERVICE_OTHER, registerApi("op-b"), "POST", "/api/op/b", otherTypeViewOp());

        assertThat(apiMappingReads.selectServiceCodesByRequiredOperationTypes(TENANT, Set.of(TYPE_VALUE)))
            .containsExactly(SERVICE);
        assertThat(apiMappingReads.selectServiceCodesByRequiredOperationTypes(TENANT, Set.of(TYPE_VALUE2)))
            .containsExactly(SERVICE_OTHER);
    }


    // ─── 身份约束与 HTTP 契约 ───

    @Test
    void credentialIdentityShouldBeRestrictedToOwnedService() {
        Long api = registerApi("view");
        insertMapping(api, "POST", "/api/demo/view", viewOpId);
        // 凭证=本服务：身份校验通过、判定正常执行（无角色主体 → DENY/NO_ROLE）
        AccessRequestContext.bind(RequestContext.service(TENANT, SERVICE));
        var owned = admission.interfaceAdmission(TENANT, admissionReq("POST", "/api/demo/view", null));
        assertThat(owned.decision()).isEqualTo("DENY");
        assertThat(owned.reason()).isEqualTo("NO_ROLE");
        // 凭证=他服务：两端点均拒绝（sync-v2 先例）
        AccessRequestContext.bind(RequestContext.service(TENANT, "admission-other"));
        assertThatThrownBy(() -> admission.interfaceAdmission(TENANT, admissionReq("POST", "/api/demo/view", null)))
            .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> admission.interfaceAdmissionSnapshot(TENANT, snapshotReq()))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    void endpointsShouldServeInternalSecretFormAndRejectOldSecretSelfReportedService() throws Exception {
        Long api = registerApi("view");
        insertMapping(api, "POST", "/api/demo/view", viewOpId);
        // 平台内部密钥形态（网关：X-Internal-Secret + X-Tenant-Id，无自报服务头）→ 200 信封
        mvc.perform(post("/api/access/auth/interface-admission-snapshot")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Internal-Secret", "test-internal-secret-for-access-service")
                .header("X-Tenant-Id", "1")
                .content(json.writeValueAsString(snapshotReq())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.authorizationStage").value("OPERATION_ADMISSION"))
            .andExpect(jsonPath("$.data.finalCheckRequired").value(true));
        // 旧密钥 + 自报 X-Service-Code（SDK 直连旧形态）→ 403（sync-v2 先例：新端点不扩展旧密钥自报通道）
        mvc.perform(post("/api/access/auth/interface-admission")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Internal-Secret", "test-internal-secret-for-access-service")
                .header("X-Tenant-Id", "1")
                .header("X-Service-Code", SERVICE)
                .content(json.writeValueAsString(admissionReq("POST", "/api/demo/view", null))))
            .andExpect(status().isForbidden());
    }
}
