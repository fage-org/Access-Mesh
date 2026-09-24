package cn.ac.fage.accessmesh.access.characterization;

import cn.ac.fage.accessmesh.access.bootstrap.AccessBootstrapInitializer;
import cn.ac.fage.accessmesh.access.bootstrap.BootstrapGraphDefinition;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 双租户相同业务码隔离组合验收（T-ACCESS-055 acceptance②，承接 S011「两租户有实际同编码
 * 数据的完整场景尚未动态验收」；真实 PostgreSQL + Redis + 真权限引擎）。
 * <p>
 * 夹具（「使用现有隔离基建构造完整类型/身份/角色/资源夹具」——不宣称租户开通 UI 产品化，
 * 租户开通方向 2026-09-17 已停）：tenant 1 经 bootstrap + 产品 API 建同码三件（用户/角色/
 * 资源）；tenant 2 复制 tenant 1 的 type_definition + operation_permission 类型夹具
 * （type_value 语义租户内独立）后 jdbc 直插同码三件。租户即各表 tenant_id 列（无租户
 * 注册表），断言全部按 fixture 收窄（本类独占库，PgIT 类内共享库纪律）。
 * </p>
 * <p>
 * 验证面：①跨读——tenant 1 判定上下文解析 tenant 2 主体 id 得 USER_NOT_FOUND（主体按
 * 租户隔离，S011 原文方向）；②跨写——tenant 1 产品通道对同码资源授权命中 tenant 1 资源行
 * （同码不串租户）；③授权隔离——tenant 1 授权不改变 tenant 2 同码判定（false），tenant 2
 * 夹具补授权行后 tenant 2 判定翻 true 且 tenant 1 判定不受影响。
 * </p>
 * <p>
 * 实现依赖注记：阶段 7 直插授权行后立即可见依赖 auth/check INSTANCE 判定面为实时 SQL
 * （queryInstance 直查 rolePermMapper，不经 ROLE_PERM_SNAPSHOT 缓存——该缓存仅 LIST 面
 * 消费）；若未来 INSTANCE 判定改走快照缓存，直插无失效广播会使隔离②断言假红，届时夹具
 * 需改产品通道或显式清缓存。
 * </p>
 * Docker 不可用时由 Testcontainers 自动跳过（容器轨道）。
 */
@Tag("testcontainers")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
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
    "PERM_INTERNAL_SECRET=test-internal-secret-for-dual-tenant",
    "ACCESSMESH_SIGNATURE_SECRET=test-signature-secret-for-dual-tenant",
})
class DualTenantSameCodeIsolationPgIT {

    private static final Long TENANT_A = 1L;
    private static final Long TENANT_B = 2L;
    private static final String ADMIN_PASSWORD = "Ext@2026";
    private static final String INTERNAL_SECRET = "test-internal-secret-for-dual-tenant";
    private static final String SIGN_SECRET = "test-signature-secret-for-dual-tenant";

    /** SERVICE:VIEW 操作位（DDL 预置 bit 2）。 */
    private static final long SERVICE_VIEW_BIT = 2L;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, DualTenantSameCodeIsolationPgIT.class);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AccessBootstrapInitializer initializer;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("双租户同码夹具：跨读 USER_NOT_FOUND/跨写同码不串/授权隔离互不影响")
    void dualTenantSameCodeShouldStayIsolatedAcrossReadWriteAndGrant() throws Exception {
        // —— 阶段 0：空库 bootstrap（tenant 1 固定图） ——
        initializer.initialize(ADMIN_PASSWORD);
        long adminUserId = jdbc.queryForObject(
            "SELECT id FROM sys_user WHERE tenant_id = ? AND username = ? AND delete_flag = 0",
            Long.class, TENANT_A, BootstrapGraphDefinition.ADMIN_USERNAME);
        String marker = UUID.randomUUID().toString().substring(0, 8);
        String sameUsername = "samecode-user-" + marker;
        String sameRoleExternalId = "samecode-role-" + marker;
        String sameResourceCode = "samecode-svc-" + marker;

        // —— 阶段 1：tenant 2 类型夹具（复制 tenant 1 的 type_definition + operation_permission，
        //     排除自增 id 与审计列；type_value 语义租户内独立） ——
        jdbc.update(
            "INSERT INTO type_definition (tenant_id, type_key, type_code, type_value, name, description, "
                + "is_system, sort_order, extra, delete_flag) "
                + "SELECT ?, type_key, type_code, type_value, name, description, is_system, sort_order, extra, 0 "
                + "FROM type_definition WHERE tenant_id = ? AND delete_flag = 0",
            TENANT_B, TENANT_A);
        jdbc.update(
            "INSERT INTO operation_permission (tenant_id, resource_type, code, name, binary_bit, inherit_mask, delete_flag) "
                + "SELECT ?, resource_type, code, name, binary_bit, inherit_mask, 0 "
                + "FROM operation_permission WHERE tenant_id = ? AND delete_flag = 0",
            TENANT_B, TENANT_A);

        // —— 阶段 2：tenant 1 同码三件（资源/角色经产品 API + jdbc 绑定，授权随后走产品通道） ——
        postAsAdmin("/api/access/resource-entity/create", adminUserId,
            JSON.objectNode()
                .put("resourceTypeCode", "SERVICE").put("code", sameResourceCode)
                .put("name", "同码服务A"));
        Long roleAId = jdbc.queryForObject(
            "INSERT INTO abstract_role (tenant_id, role_type, external_id, name, status, parent_id, extra) "
                + "VALUES (?, 6, ?, '同码角色A', 1, NULL, '{}') RETURNING id",
            Long.class, TENANT_A, sameRoleExternalId);
        JsonNode userA = postAsAdmin("/api/access/user/create", adminUserId,
            JSON.objectNode()
                .put("username", sameUsername)
                .put("name", "同码用户A")
                .put("orgId", jdbc.queryForObject(
                    "SELECT root_org_id FROM sys_org_tree_config WHERE tenant_id = ? AND is_default = true AND delete_flag = 0",
                    Long.class, TENANT_A)));
        long userAId = userA.path("id").asLong();
        jdbc.update(
            "INSERT INTO user_role (tenant_id, abstract_user_id, target_type, target_id) VALUES (?, ?, 'ROLE', ?)",
            TENANT_A, userAId, roleAId);
        Long resourceAId = jdbc.queryForObject(
            "SELECT re.id FROM resource_entity re JOIN type_definition td "
                + "ON td.tenant_id = re.tenant_id AND td.type_value = re.resource_type "
                + "AND td.type_key = 'resource_type' AND td.type_code = 'SERVICE' "
                + "WHERE re.tenant_id = ? AND re.code = ? AND re.delete_flag = 0",
            Long.class, TENANT_A, sameResourceCode);

        // —— 阶段 3：tenant 2 同码三件（夹具直插：类型基建之上构造同码身份/角色/资源） ——
        // sys_user.id 无自增（应用侧雪花生成），显式段位插入（本类独占库，TargetModeClosurePgIT 显式段同款口径）
        long userBId = 9_820_001L;
        jdbc.update(
            "INSERT INTO sys_user (id, tenant_id, username, password, name, status, user_type, force_reset_pwd, delete_flag) "
                + "VALUES (?, ?, ?, 'x', '同码用户B', 1, 3, false, 0)",
            userBId, TENANT_B, sameUsername);
        // abstract_user.id 显式与 sys_user.id 同值（LocalProjectionDomainService 投影约定 id=N/external_id=N）
        jdbc.update(
            "INSERT INTO abstract_user (id, tenant_id, user_type, external_id, name, enabled, extra, delete_flag) "
                + "VALUES (?, ?, 3, ?, '同码用户B', true, '{}', 0)",
            userBId, TENANT_B, String.valueOf(userBId));
        Long roleBId = jdbc.queryForObject(
            "INSERT INTO abstract_role (tenant_id, role_type, external_id, name, status, parent_id, extra) "
                + "VALUES (?, 6, ?, '同码角色B', 1, NULL, '{}') RETURNING id",
            Long.class, TENANT_B, sameRoleExternalId);
        jdbc.update(
            "INSERT INTO user_role (tenant_id, abstract_user_id, target_type, target_id) VALUES (?, ?, 'ROLE', ?)",
            TENANT_B, userBId, roleBId);
        Integer serviceTypeValueB = jdbc.queryForObject(
            "SELECT type_value FROM type_definition WHERE tenant_id = ? AND type_key = 'resource_type' "
                + "AND type_code = 'SERVICE' AND delete_flag = 0",
            Integer.class, TENANT_B);
        Long resourceBId = jdbc.queryForObject(
            "INSERT INTO resource_entity (tenant_id, resource_type, code, code_type, name, status, extra, "
                + "maintain_source, delete_flag) VALUES (?, ?, ?, 'default', '同码服务B', 1, '{}', 'MANUAL', 0) RETURNING id",
            Long.class, TENANT_B, serviceTypeValueB, sameResourceCode);

        // —— 阶段 4：跨读——tenant 1 上下文解析 tenant 2 主体（S011：USER_NOT_FOUND 方向） ——
        JsonNode crossRead = authCheck(TENANT_A, String.valueOf(userBId), sameResourceCode);
        assertThat(crossRead.path("allowed").asBoolean())
            .as("跨租户主体不得在 tenant 1 判定：%s", crossRead).isFalse();
        assertThat(crossRead.path("reason").asText())
            .as("跨租户主体必须按不存在拒绝（USER_NOT_FOUND），而非误装配 tenant 2 身份：%s", crossRead)
            .isEqualTo("USER_NOT_FOUND");

        // —— 阶段 5：跨写——tenant 1 产品通道对同码资源授权，必须命中 tenant 1 资源行 ——
        JsonNode grantItems = postAsAdmin("/api/access/role-resource-permission/apply-grant-plan", adminUserId,
                grantPlan(sameRoleExternalId, sameResourceCode))
            .path("items");
        assertThat(grantItems.isArray() && grantItems.size() == 1)
            .as("tenant 1 同码授权必须经产品通道成功（同码资源在 tenant 1 可解析）：%s", grantItems).isTrue();
        Long grantedResourceId = jdbc.queryForObject(
            "SELECT p.resource_entity_id FROM role_resource_permission p "
                + "WHERE p.tenant_id = ? AND p.abstract_role_id = ? AND p.delete_flag = 0 "
                + "AND p.granted_bits = ? AND p.scope_all = false",
            Long.class, TENANT_A, roleAId, SERVICE_VIEW_BIT);
        assertThat(grantedResourceId)
            .as("tenant 1 授权行必须指向 tenant 1 资源行（同码不串租户）")
            .isEqualTo(resourceAId);

        // —— 阶段 6：授权隔离①——tenant 1 授权不得改变 tenant 2 同码判定 ——
        assertThat(authCheck(TENANT_B, String.valueOf(userBId), sameResourceCode).path("allowed").asBoolean())
            .as("tenant 1 的授权行不得对 tenant 2 判定生效").isFalse();

        // —— 阶段 7：授权隔离②——tenant 2 夹具补授权行后自身判定翻 true，tenant 1 判定不受影响 ——
        jdbc.update(
            "INSERT INTO role_resource_permission (tenant_id, abstract_role_id, resource_entity_id, "
                + "granted_bits, resource_type, scope_all, can_grant, grant_source, delete_flag) "
                + "VALUES (?, ?, ?, ?, ?, false, false, 'MANUAL', 0)",
            TENANT_B, roleBId, resourceBId, SERVICE_VIEW_BIT, serviceTypeValueB);
        assertThat(authCheck(TENANT_B, String.valueOf(userBId), sameResourceCode).path("allowed").asBoolean())
            .as("tenant 2 自身授权后同码判定必须放行（夹具身份/角色/资源装配正确性自证）").isTrue();
        assertThat(authCheck(TENANT_A, String.valueOf(userAId), sameResourceCode).path("allowed").asBoolean())
            .as("tenant 2 的授权行不得反向影响 tenant 1 判定（tenant 1 判定维持自身授权事实）").isTrue();
        // 租户计数收窄断言：两租户同码资源各恰一行、授权各恰一行（按 fixture 收窄，防邻类残留误读）
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM resource_entity WHERE code = ? AND delete_flag = 0", Long.class, sameResourceCode))
            .isEqualTo(2L);
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM role_resource_permission WHERE delete_flag = 0 AND granted_bits = ? "
                + "AND abstract_role_id IN (?, ?)", Long.class, SERVICE_VIEW_BIT, roleAId, roleBId))
            .isEqualTo(2L);
    }

    // ===== 请求构造 =====

    /** tenant 1 实例级授权计划（SERVICE:VIEW@同码资源）。 */
    private ObjectNode grantPlan(String roleExternalId, String resourceCode) {
        var key = JSON.objectNode();
        key.put("resourceTypeCode", "SERVICE");
        key.put("resourceCode", resourceCode);
        key.put("codeType", "default");
        key.put("operationCode", "VIEW");
        key.put("scopeMode", "INSTANCE");
        key.putNull("conditionCode");
        key.put("canGrant", false);
        var createItem = JSON.objectNode();
        createItem.set("key", key);
        var plan = JSON.objectNode();
        plan.set("creates", JSON.arrayNode().add(createItem));
        plan.putNull("updates");
        plan.putNull("removes");
        var req = JSON.objectNode();
        req.putNull("domainCode");
        req.put("roleTypeCode", "BASIC_ROLE");
        req.put("roleExternalId", roleExternalId);
        req.set("plan", plan);
        return req;
    }

    /** 运行时判定请求（服务调用形态：内部密钥 + 指定租户上下文；LOCAL_USER 主体）。 */
    private JsonNode authCheck(Long tenantId, String subjectExternalId, String resourceCode) throws Exception {
        var body = JSON.objectNode()
            .put("subjectTypeCode", "LOCAL_USER")
            .put("subjectExternalId", subjectExternalId)
            .put("resourceTypeCode", "SERVICE")
            .put("resourceCode", resourceCode)
            .put("operationCode", "VIEW");
        return performAndUnwrap("/api/access/auth/check", body,
            Map.of("X-Internal-Secret", INTERNAL_SECRET, "X-Tenant-Id", String.valueOf(tenantId)), 200);
    }

    // ===== 请求辅助（DelegatedDirectoryClosurePgIT 模式） =====

    private JsonNode postAsAdmin(String path, long operatorUserId, ObjectNode body) throws Exception {
        long ts = System.currentTimeMillis() / 1000;
        String userId = String.valueOf(operatorUserId);
        return performAndUnwrap(path, body, Map.of(
            "X-Internal-Secret", INTERNAL_SECRET,
            "X-Tenant-Id", String.valueOf(TENANT_A),
            "X-User-Id", userId,
            "X-User-Signature", hmac(userId, String.valueOf(TENANT_A), ts),
            "X-Signature-Timestamp", String.valueOf(ts)), 200);
    }

    private JsonNode performAndUnwrap(String path, ObjectNode body, Map<String, String> headers,
                                      int expectedEnvelopeCode) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(body));
        headers.forEach(request::header);
        MvcResult result = mockMvc.perform(request).andReturn();
        String raw = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode envelope = mapper.readTree(raw);
        assertThat(envelope.path("code").asInt())
            .as("业务信封码必须匹配，path=%s，响应：%s", path, raw).isEqualTo(expectedEnvelopeCode);
        return envelope.path("data");
    }

    private static String hmac(String userId, String tenantId, long timestamp) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(
            SIGN_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String payload = userId + "|" + tenantId + "|" + timestamp;
        return java.util.HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
}
