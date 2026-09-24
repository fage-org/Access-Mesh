package cn.ac.fage.accessmesh.access.org.service;

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
 * 岗位停用期成员权限剪枝与恢复后自动生效观测（T-ACCESS-055 acceptance① 岗位恢复句，
 * T-FE-057 2026-09-22 让渡项——浏览器验收只覆盖列表可见性，权限剪枝观测留本卡）。
 * <p>
 * 组合链：建部门+岗位（orgType=2）→ 用户挂岗位（user-org/assign → bindUserOrg 投影
 * user_role → 岗位容器角色）→ 给岗位容器角色授权（产品通道 apply-grant-plan
 * roleTypeCode=POSITION）→ auth/check 放行 → 停用岗位（org/update status=0 →
 * projectOrg 投影 abstract_role.status=0 → 有效角色剪枝）→ auth/check 拒绝 →
 * 恢复岗位（status=1）→ auth/check 自动恢复放行（无任何重新授权动作）。
 * </p>
 * <p>
 * 观测本体是「界面不造本地规则、后端事实链负责语义」（T-FE-057 拍板②）：停用容器的
 * 权限收缩与恢复的权限回归全部由投影+引擎链路自发完成，任何一环断裂（投影未同步
 * role.status / 有效角色解析未剪枝 / 缓存未失效）对应断言必红。恢复步不做任何授权写
 * 操作，证明「恢复后自动生效」。
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
    "PERM_INTERNAL_SECRET=test-internal-secret-for-pos-prune",
    "ACCESSMESH_SIGNATURE_SECRET=test-signature-secret-for-pos-prune",
})
class PositionDisablePruneObservationPgIT {

    private static final Long TENANT = 1L;
    private static final String ADMIN_PASSWORD = "Ext@2026";
    private static final String INTERNAL_SECRET = "test-internal-secret-for-pos-prune";
    private static final String SIGN_SECRET = "test-signature-secret-for-pos-prune";

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, PositionDisablePruneObservationPgIT.class);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AccessBootstrapInitializer initializer;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("岗位停用→成员权限剪枝→恢复→自动生效（全程无重新授权动作）")
    void positionDisableShouldPruneMemberPermissionAndRestoreShouldRegrantAutomatically() throws Exception {
        // —— 阶段 0：空库 bootstrap ——
        initializer.initialize(ADMIN_PASSWORD);
        long adminUserId = jdbc.queryForObject(
            "SELECT id FROM sys_user WHERE tenant_id = ? AND username = ? AND delete_flag = 0",
            Long.class, TENANT, BootstrapGraphDefinition.ADMIN_USERNAME);
        String marker = UUID.randomUUID().toString().substring(0, 8);
        long rootOrgId = jdbc.queryForObject(
            "SELECT root_org_id FROM sys_org_tree_config WHERE tenant_id = ? AND is_default = true AND delete_flag = 0",
            Long.class, TENANT);

        // —— 阶段 1：判定目标资源 + 部门/岗位骨架 ——
        String resourceCode = "prune-svc-" + marker;
        postAsAdmin("/api/access/resource-entity/create", adminUserId,
            JSON.objectNode()
                .put("resourceTypeCode", "SERVICE").put("code", resourceCode)
                .put("name", "剪枝观测服务"));
        long deptId = postAsAdmin("/api/access/org/create", adminUserId,
                JSON.objectNode().put("orgType", 1).put("orgName", "剪枝观测部门")
                    .put("parentOrgId", rootOrgId).put("code", "prune-dept-" + marker))
            .asLong();
        long positionId = postAsAdmin("/api/access/org/create", adminUserId,
                JSON.objectNode().put("orgType", 2).put("orgName", "剪枝观测岗位")
                    .put("parentOrgId", deptId).put("code", "prune-pos-" + marker))
            .asLong();

        // —— 阶段 2：用户挂岗位（主归属部门 + user-org/assign 岗位 → 投影持有岗位容器角色） ——
        JsonNode user = postAsAdmin("/api/access/user/create", adminUserId,
            JSON.objectNode()
                .put("username", "prune-user-" + marker)
                .put("name", "剪枝观测用户")
                .put("orgId", deptId));
        long userId = user.path("id").asLong();
        postAsAdmin("/api/access/user-org/assign", adminUserId,
            JSON.objectNode().put("userId", userId)
                .set("orgIds", JSON.arrayNode().add(positionId)));
        // 挂载事实自证：user_role 投影行指向岗位容器角色（role_type=2=POSITION）
        Long positionRoleId = jdbc.queryForObject(
            "SELECT r.id FROM user_role ur JOIN abstract_role r "
                + "ON r.tenant_id = ur.tenant_id AND r.id = ur.target_id "
                + "WHERE ur.tenant_id = ? AND ur.abstract_user_id = ? AND r.role_type = 2 "
                + "AND ur.delete_flag = 0 AND r.delete_flag = 0",
            Long.class, TENANT, userId);
        assertThat(positionRoleId).as("挂岗位必须投影为持有岗位容器角色").isNotNull();
        // 容器角色 external_id = 岗位 sys_org.id（upsertAdminOrg 口径），授权计划按此定位
        String containerExternalId = jdbc.queryForObject(
            "SELECT external_id FROM abstract_role WHERE tenant_id = ? AND id = ?",
            String.class, TENANT, positionRoleId);
        assertThat(containerExternalId).isEqualTo(String.valueOf(positionId));

        // —— 阶段 3：给岗位容器角色授权（产品通道，POSITION 角色形态） ——
        JsonNode grantItems = postAsAdmin("/api/access/role-resource-permission/apply-grant-plan", adminUserId,
                positionGrantPlan(containerExternalId, resourceCode))
            .path("items");
        assertThat(grantItems.isArray() && grantItems.size() == 1)
            .as("岗位容器角色授权必须经产品通道构造成功：%s", grantItems).isTrue();

        // —— 阶段 4：挂岗成员经容器角色放行 ——
        assertThat(authCheck(userId, resourceCode).path("allowed").asBoolean())
            .as("岗位启用期成员必须经容器角色获得授权").isTrue();

        // —— 阶段 5：停用岗位 → 投影 role.status=0 → 有效角色剪枝 ——
        postAsAdmin("/api/access/org/update", adminUserId,
            JSON.objectNode().put("id", positionId).put("status", 0));
        Integer projectedStatus = jdbc.queryForObject(
            "SELECT status FROM abstract_role WHERE tenant_id = ? AND id = ?",
            Integer.class, TENANT, positionRoleId);
        assertThat(projectedStatus).as("停用岗位必须投影为容器角色 status=0").isEqualTo(0);
        assertThat(authCheck(userId, resourceCode).path("allowed").asBoolean())
            .as("停用期成员权限必须被剪枝（容器角色经有效角色解析剔除）").isFalse();

        // —— 阶段 6：恢复岗位（仅改状态，无任何重新授权动作）→ 权限自动回归 ——
        postAsAdmin("/api/access/org/update", adminUserId,
            JSON.objectNode().put("id", positionId).put("status", 1));
        assertThat(authCheck(userId, resourceCode).path("allowed").asBoolean())
            .as("恢复岗位后成员权限必须自动生效（恢复步零授权写操作）").isTrue();
    }

    // ===== 请求构造 =====

    /** 岗位容器角色（POSITION）实例级授权计划。 */
    private ObjectNode positionGrantPlan(String containerExternalId, String resourceCode) {
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
        req.put("roleTypeCode", "POSITION");
        req.put("roleExternalId", containerExternalId);
        req.set("plan", plan);
        return req;
    }

    /** 运行时判定请求（服务调用形态：内部密钥；本地登录用户主体类型=LOCAL_USER）。 */
    private JsonNode authCheck(long subjectUserId, String resourceCode) throws Exception {
        var body = JSON.objectNode()
            .put("subjectTypeCode", "LOCAL_USER")
            .put("subjectExternalId", String.valueOf(subjectUserId))
            .put("resourceTypeCode", "SERVICE")
            .put("resourceCode", resourceCode)
            .put("operationCode", "VIEW");
        return performAndUnwrap("/api/access/auth/check", body,
            Map.of("X-Internal-Secret", INTERNAL_SECRET, "X-Tenant-Id", String.valueOf(TENANT)), 200);
    }

    // ===== 请求辅助（DelegatedDirectoryClosurePgIT 模式） =====

    private JsonNode postAsAdmin(String path, long operatorUserId, ObjectNode body) throws Exception {
        long ts = System.currentTimeMillis() / 1000;
        String userId = String.valueOf(operatorUserId);
        return performAndUnwrap(path, body, Map.of(
            "X-Internal-Secret", INTERNAL_SECRET,
            "X-Tenant-Id", String.valueOf(TENANT),
            "X-User-Id", userId,
            "X-User-Signature", hmac(userId, String.valueOf(TENANT), ts),
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
