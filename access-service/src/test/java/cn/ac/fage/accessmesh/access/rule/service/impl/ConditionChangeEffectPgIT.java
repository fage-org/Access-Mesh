package cn.ac.fage.accessmesh.access.rule.service.impl;

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
 * 条件变更组合验收（T-ACCESS-055 acceptance①「条件变更」，承接 S006 评审时未完成的动态检查；
 * 真实 PostgreSQL + Redis + 真权限引擎 + 真条件求值）。
 * <p>
 * 组合链：条件配置（IP 白名单 10.0.0.0/8）→ 授权挂条件（产品通道 apply-grant-plan，
 * conditionCode 非 null 且 canGrant=false——20041 不变量形态）→ auth/check 白名单内
 * IP 命中 allowed=true / 白名单外 IP 拒绝 → permission-condition/update 改规则为
 * 192.168.0.0/16 → 判定双向翻转（原命中 IP 转拒绝、原拒绝 IP 转命中）。
 * </p>
 * <p>
 * 断言本体是 CONDITION_RULES 缓存与判定的串联收敛：条件规则写路径必须使引擎下次
 * 判定装载新规则（evict 链断裂时翻转步必红——旧白名单继续命中/继续放行）。各单元件
 * （求值器/缓存/引擎）各自有测试，本用例只锁组合链，不复制单元矩阵。判定翻转在进程内
 * 请求间完成（evictAfterCommit 即时一致；CONDITION_RULES 为 L2_ONLY 10s TTL，写路径
 * 失效后不依赖 TTL 到期）。
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
    "PERM_INTERNAL_SECRET=test-internal-secret-for-cond-change",
    "ACCESSMESH_SIGNATURE_SECRET=test-signature-secret-for-cond-change",
})
class ConditionChangeEffectPgIT {

    private static final Long TENANT = 1L;
    private static final String ADMIN_PASSWORD = "Ext@2026";
    private static final String INTERNAL_SECRET = "test-internal-secret-for-cond-change";
    private static final String SIGN_SECRET = "test-signature-secret-for-cond-change";

    /** 白名单改写前命中的来源 IP（10.0.0.0/8 段内）。 */
    private static final String IP_IN_FIRST_WHITELIST = "10.20.30.40";
    /** 白名单改写前不命中的来源 IP（192.168.0.0/16 段内）。 */
    private static final String IP_IN_SECOND_WHITELIST = "192.168.50.60";

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, ConditionChangeEffectPgIT.class);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AccessBootstrapInitializer initializer;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("条件变更组合：配置→授权挂条件→命中/不命中→改规则→判定双向翻转（缓存串联收敛）")
    void conditionChangeShouldFlipEngineDecision() throws Exception {
        // —— 阶段 0：空库 bootstrap ——
        initializer.initialize(ADMIN_PASSWORD);
        long adminUserId = jdbc.queryForObject(
            "SELECT id FROM sys_user WHERE tenant_id = ? AND username = ? AND delete_flag = 0",
            Long.class, TENANT, BootstrapGraphDefinition.ADMIN_USERNAME);
        String marker = UUID.randomUUID().toString().substring(0, 8);

        // —— 阶段 1：判定目标资源（SERVICE 为 MANAGED 类型，管理面可建） ——
        String resourceCode = "cond-combo-" + marker;
        postAsAdmin("/api/access/resource-entity/create", adminUserId,
            JSON.objectNode()
                .put("resourceTypeCode", "SERVICE").put("code", resourceCode)
                .put("name", "条件组合验收服务"));

        // —— 阶段 2：角色与用户（角色/绑定 jdbc 直插，授权事实走产品通道） ——
        String roleExternalId = "cond-combo-role-" + marker;
        Long roleId = jdbc.queryForObject(
            "INSERT INTO abstract_role (tenant_id, role_type, external_id, name, status, parent_id, extra) "
                + "VALUES (?, 6, ?, '条件组合角色', 1, NULL, '{}') RETURNING id",
            Long.class, TENANT, roleExternalId);
        JsonNode user = postAsAdmin("/api/access/user/create", adminUserId,
            JSON.objectNode()
                .put("username", "cond-user-" + marker)
                .put("name", "条件组合用户")
                .put("orgId", jdbc.queryForObject(
                    "SELECT root_org_id FROM sys_org_tree_config WHERE tenant_id = ? AND is_default = true AND delete_flag = 0",
                    Long.class, TENANT)));
        long userId = user.path("id").asLong();
        jdbc.update(
            "INSERT INTO user_role (tenant_id, abstract_user_id, target_type, target_id) VALUES (?, ?, 'ROLE', ?)",
            TENANT, userId, roleId);

        // —— 阶段 3：条件配置（管理页条件，source=MANAGED；IP 白名单第一段） ——
        String conditionCode = "cond-combo-" + marker;
        postAsAdmin("/api/access/permission-condition/create", adminUserId,
            JSON.objectNode()
                .put("code", conditionCode)
                .put("name", "条件组合-IP白名单")
                .put("conditionRules",
                    "{\"logic\":\"AND\",\"items\":[{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"10.0.0.0/8\"]}}]}")
                .put("enabled", true));

        // —— 阶段 4：产品通道授权挂条件（INSTANCE + conditionCode + canGrant=false——20041 形态） ——
        JsonNode grantItems = postAsAdmin("/api/access/role-resource-permission/apply-grant-plan", adminUserId,
                conditionalGrantPlan(roleExternalId, resourceCode, conditionCode))
            .path("items");
        assertThat(grantItems.isArray() && grantItems.size() == 1)
            .as("挂条件的实例授权必须经产品通道构造成功：%s", grantItems).isTrue();

        // —— 阶段 5：白名单内 IP 命中（条件评估真实发生） ——
        JsonNode hitFirst = authCheck(userId, resourceCode, IP_IN_FIRST_WHITELIST);
        assertThat(hitFirst.path("allowed").asBoolean())
            .as("白名单内 IP 必须放行：%s", hitFirst).isTrue();
        assertThat(hitFirst.path("conditionEvaluated").asBoolean())
            .as("放行必须来自真实条件评估（conditionEvaluated）而非无条件授权：%s", hitFirst).isTrue();

        // —— 阶段 6：白名单外 IP 拒绝（同一授权行、条件不满足） ——
        JsonNode missFirst = authCheck(userId, resourceCode, IP_IN_SECOND_WHITELIST);
        assertThat(missFirst.path("allowed").asBoolean())
            .as("白名单外 IP 必须被条件拒绝：%s", missFirst).isFalse();

        // —— 阶段 7：改条件规则（同一 code，白名单换段） ——
        postAsAdmin("/api/access/permission-condition/update", adminUserId,
            JSON.objectNode()
                .put("code", conditionCode)
                .put("conditionRules",
                    "{\"logic\":\"AND\",\"items\":[{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"192.168.0.0/16\"]}}]}"));

        // —— 阶段 8：判定双向翻转（原拒绝 IP 转命中；原命中 IP 转拒绝） ——
        JsonNode hitSecond = authCheck(userId, resourceCode, IP_IN_SECOND_WHITELIST);
        assertThat(hitSecond.path("allowed").asBoolean())
            .as("改规则后新白名单内 IP 必须放行（条件规则缓存写路径失效串联）：%s", hitSecond).isTrue();
        JsonNode missSecond = authCheck(userId, resourceCode, IP_IN_FIRST_WHITELIST);
        assertThat(missSecond.path("allowed").asBoolean())
            .as("改规则后旧白名单 IP 必须被拒绝（旧规则不得继续命中）：%s", missSecond).isFalse();
    }

    // ===== 请求构造 =====

    /** 实例级挂条件授权计划（conditionCode 非 null 时 canGrant 必须 false——20041 不变量）。 */
    private ObjectNode conditionalGrantPlan(String roleExternalId, String resourceCode, String conditionCode) {
        var key = JSON.objectNode();
        key.put("resourceTypeCode", "SERVICE");
        key.put("resourceCode", resourceCode);
        key.put("codeType", "default");
        key.put("operationCode", "VIEW");
        key.put("scopeMode", "INSTANCE");
        key.put("conditionCode", conditionCode);
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

    /** 运行时判定请求（服务调用形态：内部密钥；本地登录用户主体类型=LOCAL_USER）。 */
    private JsonNode authCheck(long subjectUserId, String resourceCode, String clientIp) throws Exception {
        var body = JSON.objectNode()
            .put("subjectTypeCode", "LOCAL_USER")
            .put("subjectExternalId", String.valueOf(subjectUserId))
            .put("resourceTypeCode", "SERVICE")
            .put("resourceCode", resourceCode)
            .put("operationCode", "VIEW");
        var context = JSON.objectNode().put("clientIp", clientIp);
        body.set("context", context);
        return performAndUnwrap("/api/access/auth/check", body,
            Map.of("X-Internal-Secret", INTERNAL_SECRET, "X-Tenant-Id", String.valueOf(TENANT)), 200);
    }

    // ===== 请求辅助（DelegatedDirectoryClosurePgIT 模式） =====

    private JsonNode postAsAdmin(String path, long operatorUserId, ObjectNode body) throws Exception {
        long ts = System.currentTimeMillis() / 1000;
        String userId = String.valueOf(operatorUserId);
        return performAndUnwrap(path, body, new java.util.LinkedHashMap<>(Map.of(
            "X-Internal-Secret", INTERNAL_SECRET,
            "X-Tenant-Id", String.valueOf(TENANT),
            "X-User-Id", userId,
            "X-User-Signature", hmac(userId, String.valueOf(TENANT), ts),
            "X-Signature-Timestamp", String.valueOf(ts))), 200);
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
