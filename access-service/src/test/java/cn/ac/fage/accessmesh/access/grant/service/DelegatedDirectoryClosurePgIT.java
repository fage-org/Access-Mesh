package cn.ac.fage.accessmesh.access.grant.service;

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
import org.springframework.data.redis.core.StringRedisTemplate;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 实例委派目录闭环端到端验收（T-ACCESS-052，D001；真实 PostgreSQL + Redis + 真权限引擎，
 * 委派构造必须走产品通道 apply-grant-plan——「不能直接灌授权证明可用」）。
 * <p>
 * 主链（service-a 负责人）：bootstrap（新种子 SERVICE:MANAGE canGrant=true）→ 首管理员经
 * apply-grant-plan 构造「SERVICE:access-service 实例 MANAGE」限定角色（旧种子 canGrant=false
 * 此步 20040 必红①）→ 负责人真实登录 → service-config/list 实例过滤只见 access-service
 * 不泄露 review-b（旧实现类型级 VIEW 403 必红②）→ user-menu 派生含服务页菜单（类型页菜单
 * 实例准入，旧实现必红③）→ 越界服务 detail 403 → apply-grant-plan 撤权后目录回 403、
 * 菜单消失（进程内 evictAfterCommit 即时一致）。
 * 副链（部门成员管理员）：MANAGE_MEMBER@部门 实例 + USER:VIEW 类型级门票（均经
 * apply-grant-plan）→ org/tree 组织轨只见可见子树+祖先导航链（旧实现类型级 403 必红④）→
 * user/page 门票放行且内容按可见组织裁剪（不泄露其他部门成员）。
 * 主体/角色行 jdbc 直插（MemberCandidatesGatePgIT 模式），授权事实全部走产品 API。
 * Docker 不可用时由 Testcontainers 自动跳过（容器轨道）。
 * </p>
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
    "PERM_INTERNAL_SECRET=test-internal-secret-for-delegated-dir",
    "ACCESSMESH_SIGNATURE_SECRET=test-signature-secret-for-delegated-dir",
})
class DelegatedDirectoryClosurePgIT {

    private static final Long TENANT = 1L;
    private static final String ADMIN_PASSWORD = "Ext@2026";
    private static final String INTERNAL_SECRET = "test-internal-secret-for-delegated-dir";
    private static final String SIGN_SECRET = "test-signature-secret-for-delegated-dir";

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, DelegatedDirectoryClosurePgIT.class);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private AccessBootstrapInitializer initializer;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("委派闭环：首授（产品通道）→目录实例过滤→菜单准入→越界拒→撤权一致；部门管理员树裁剪+用户目录门票")
    void delegatedDirectoryClosureCombo() throws Exception {
        // —— 阶段 0：空库 bootstrap（新种子四条 canGrant=true 随定义生效） ——
        initializer.initialize(ADMIN_PASSWORD);
        long adminUserId = jdbc.queryForObject(
            "SELECT id FROM sys_user WHERE tenant_id = ? AND username = ? AND delete_flag = 0",
            Long.class, TENANT, BootstrapGraphDefinition.ADMIN_USERNAME);
        String marker = UUID.randomUUID().toString().substring(0, 8);

        // —— 阶段 1：服务面数据——SERVICE:access-service 资源行为 bootstrap 固定图所种；
        //     service_config 表行由管理面手工建（MANAGED，固定图不种配置行）：补 access-service
        //     既有行 + 第二服务 review-b（资源行经管理面 create + 配置行 jdbc 直插同形态） ——
        postAsAdmin("/api/access/resource-entity/create", adminUserId,
            JSON.objectNode()
                .put("resourceTypeCode", "SERVICE").put("code", "review-b-" + marker)
                .put("name", "评审服务B"));
        jdbc.update("INSERT INTO service_config (tenant_id, service_code, name, base_path, status, created_by, delete_flag) "
            + "VALUES (?, ?, 'access-service', '/api', 1, ?, 0)",
            TENANT, BootstrapGraphDefinition.SERVICE_RESOURCE_CODE, adminUserId);
        jdbc.update("INSERT INTO service_config (tenant_id, service_code, name, base_path, status, created_by, delete_flag) "
            + "VALUES (?, ?, '评审服务B', '/api', 1, ?, 0)",
            TENANT, "review-b-" + marker, adminUserId);

        // —— 阶段 2：service-a 负责人角色与用户（角色/绑定 jdbc 直插；授权走产品通道） ——
        String ownerRoleExternalId = "ddc-svc-owner-" + marker;
        Long ownerRoleId = jdbc.queryForObject(
            "INSERT INTO abstract_role (tenant_id, role_type, external_id, name, status, parent_id, extra) "
                + "VALUES (?, 6, ?, '服务负责人（有限）', 1, NULL, '{}') RETURNING id",
            Long.class, TENANT, ownerRoleExternalId);
        JsonNode ownerUser = postAsAdmin("/api/access/user/create", adminUserId,
            JSON.objectNode()
                .put("username", "ddc-owner-" + marker)
                .put("name", "服务负责人")
                .put("orgId", jdbc.queryForObject(
                    "SELECT root_org_id FROM sys_org_tree_config WHERE tenant_id = ? AND is_default = true AND delete_flag = 0",
                    Long.class, TENANT)));
        long ownerUserId = ownerUser.path("id").asLong();
        String ownerPassword = ownerUser.path("initialPassword").asText();
        jdbc.update(
            "INSERT INTO user_role (tenant_id, abstract_user_id, target_type, target_id) VALUES (?, ?, 'ROLE', ?)",
            TENANT, ownerUserId, ownerRoleId);

        // —— 阶段 3：产品通道首授——SERVICE:access-service 实例 MANAGE（红①：旧种子 20040） ——
        //     bootstrap 固定图 SERVICE 资源 code=access-service（BootstrapGraphDefinition）
        JsonNode grantItems = postAsAdmin("/api/access/role-resource-permission/apply-grant-plan", adminUserId,
                instanceGrantPlan(ownerRoleExternalId, "SERVICE",
                    BootstrapGraphDefinition.SERVICE_RESOURCE_CODE, "MANAGE"))
            .path("items");
        assertThat(grantItems.isArray() && grantItems.size() == 1)
            .as("实例委派必须经产品通道构造成功（种子 SERVICE:MANAGE canGrant=true）：%s", grantItems).isTrue();

        // —— 阶段 4：负责人登录 → 目录实例过滤（红②：旧实现类型级 VIEW 403） ——
        String ownerToken = loginAs("ddc-owner-" + marker, ownerPassword);
        JsonNode services = performAndUnwrap("/api/access/service-config/list", ownerUserId,
            JSON.objectNode(), ownerToken, 200);
        List<String> serviceCodes = new ArrayList<>();
        services.path("items").forEach(s -> serviceCodes.add(s.path("serviceCode").asText()));
        assertThat(serviceCodes)
            .as("服务目录必须按可见实例裁剪：含 access-service、不泄露 review-b：%s", serviceCodes)
            .contains(BootstrapGraphDefinition.SERVICE_RESOURCE_CODE)
            .doesNotContain("review-b-" + marker);

        // —— 阶段 5：菜单准入（红③：旧实现类型页菜单只认 scopeAll） ——
        JsonNode menus = performAndUnwrap("/api/access/auth/user-menu", ownerUserId,
            JSON.objectNode().put("userId", ownerUserId), ownerToken, 200);
        List<String> menuPaths = new ArrayList<>();
        collectPaths(menus.path("menus"), menuPaths);
        assertThat(menuPaths)
            .as("持 SERVICE 实例授权者必须看到服务页菜单（类型页菜单实例准入）：%s", menuPaths)
            .contains("/system/service-interface");

        // —— 负向：越界服务 detail 403（实例级 VIEW，b 不泄露） ——
        int deniedCode = performRawCode("/api/access/service-config/detail", ownerUserId,
            JSON.objectNode().put("serviceCode", "review-b-" + marker), ownerToken);
        assertThat(deniedCode).as("无实例授权的服务详情必须 403").isEqualTo(403);

        // —— 阶段 6：撤权一致性（apply-grant-plan removes）——目录回 403、菜单入口消失 ——
        Long permissionId = jdbc.queryForObject(
            "SELECT p.id FROM role_resource_permission p JOIN resource_entity re "
                + "ON re.id = p.resource_entity_id AND re.tenant_id = p.tenant_id "
                + "WHERE p.tenant_id = ? AND p.abstract_role_id = ? AND p.delete_flag = 0 "
                + "AND re.code = ?",
            Long.class, TENANT, ownerRoleId, BootstrapGraphDefinition.SERVICE_RESOURCE_CODE);
        postAsAdmin("/api/access/role-resource-permission/apply-grant-plan", adminUserId,
            removePlan(ownerRoleExternalId, permissionId));
        int revokedCode = performRawCode("/api/access/service-config/list", ownerUserId,
            JSON.objectNode(), ownerToken);
        assertThat(revokedCode).as("撤权后目录必须回到 403（零可见实例 fail-closed）").isEqualTo(403);
        JsonNode menusAfter = performAndUnwrap("/api/access/auth/user-menu", ownerUserId,
            JSON.objectNode().put("userId", ownerUserId), ownerToken, 200);
        List<String> menuPathsAfter = new ArrayList<>();
        collectPaths(menusAfter.path("menus"), menuPathsAfter);
        assertThat(menuPathsAfter)
            .as("撤权后菜单入口必须同步消失：%s", menuPathsAfter)
            .doesNotContain("/system/service-interface");

        // ===== 副链：部门成员管理员（组织树裁剪 + 用户目录门票） =====
        long rootOrgId = jdbc.queryForObject(
            "SELECT root_org_id FROM sys_org_tree_config WHERE tenant_id = ? AND is_default = true AND delete_flag = 0",
            Long.class, TENANT);
        long deptAId = postAsAdmin("/api/access/org/create", adminUserId,
                JSON.objectNode().put("orgType", 1).put("orgName", "职责部门A")
                    .put("parentOrgId", rootOrgId).put("code", "ddc-dept-a-" + marker))
            .asLong();
        long deptBId = postAsAdmin("/api/access/org/create", adminUserId,
                JSON.objectNode().put("orgType", 1).put("orgName", "其他部门B")
                    .put("parentOrgId", rootOrgId).put("code", "ddc-dept-b-" + marker))
            .asLong();
        // 两部门各一个用户（B 部门用户不应被部门 A 管理员看到）
        postAsAdmin("/api/access/user/create", adminUserId,
            JSON.objectNode().put("username", "ddc-deptb-user-" + marker)
                .put("name", "B部门用户").put("orgId", deptBId));
        String memberRoleExternalId = "ddc-member-admin-" + marker;
        Long memberRoleId = jdbc.queryForObject(
            "INSERT INTO abstract_role (tenant_id, role_type, external_id, name, status, parent_id, extra) "
                + "VALUES (?, 6, ?, '部门成员管理员（有限）', 1, NULL, '{}') RETURNING id",
            Long.class, TENANT, memberRoleExternalId);
        JsonNode memberAdmin = postAsAdmin("/api/access/user/create", adminUserId,
            JSON.objectNode().put("username", "ddc-member-" + marker)
                .put("name", "部门成员管理员").put("orgId", deptAId));
        long memberAdminId = memberAdmin.path("id").asLong();
        String memberPassword = memberAdmin.path("initialPassword").asText();
        jdbc.update(
            "INSERT INTO user_role (tenant_id, abstract_user_id, target_type, target_id) VALUES (?, ?, 'ROLE', ?)",
            TENANT, memberAdminId, memberRoleId);

        // 产品通道：MANAGE_MEMBER@部门A 实例 + USER:VIEW 类型级门票（红①同款：ORG:MANAGE_MEMBER
        // /USER:VIEW canGrant 种子——旧种子下两条 20040）
        postAsAdmin("/api/access/role-resource-permission/apply-grant-plan", adminUserId,
            instanceGrantPlan(memberRoleExternalId, "ORG", String.valueOf(deptAId), "MANAGE_MEMBER"));
        postAsAdmin("/api/access/role-resource-permission/apply-grant-plan", adminUserId,
            typeLevelGrantPlan(memberRoleExternalId, "USER", "VIEW"));

        String memberToken = loginAs("ddc-member-" + marker, memberPassword);

        // —— 组织树实例裁剪（红④：旧实现类型级 ORG:VIEW 403）——树=A 子树+祖先链，B 支被裁 ——
        JsonNode orgTree = performAndUnwrap("/api/access/org/tree", memberAdminId,
            JSON.objectNode().put("orgType", 1), memberToken, 200);
        List<Long> treeOrgIds = new ArrayList<>();
        collectOrgIds(orgTree.path("items"), treeOrgIds);
        assertThat(treeOrgIds)
            .as("组织树必须裁剪到可见子树+祖先链：含根与 A、不含 B：%s", treeOrgIds)
            .contains(rootOrgId, deptAId)
            .doesNotContain(deptBId);

        // —— 用户目录门票 + 内容裁剪：user/page 200 且不泄露 B 部门用户 ——
        JsonNode userPage = performAndUnwrap("/api/access/user/page", memberAdminId,
            JSON.objectNode().put("pageNum", 1).put("pageSize", 50), memberToken, 200);
        List<Long> pageUserIds = new ArrayList<>();
        userPage.path("items").forEach(i -> pageUserIds.add(i.path("id").asLong()));
        Long deptBUserId = jdbc.queryForObject(
            "SELECT id FROM sys_user WHERE tenant_id = ? AND username = ? AND delete_flag = 0",
            Long.class, TENANT, "ddc-deptb-user-" + marker);
        assertThat(pageUserIds)
            .as("用户目录内容必须按可见组织裁剪（B 部门用户不泄露）：%s", pageUserIds)
            .doesNotContain(deptBUserId);

        // —— org/users 目标可见性校验：不可见部门 → 10101 ——
        int orgUsersCode = performRawCode("/api/access/org/users", memberAdminId,
            JSON.objectNode().put("id", deptBId), memberToken);
        assertThat(orgUsersCode).as("不可见组织的成员名单必须按不存在拒绝（10101）").isEqualTo(10101);
    }

    // ===== 计划构造 =====

    /** 实例级授权计划（scopeMode=INSTANCE，不可转授）。 */
    private ObjectNode instanceGrantPlan(String roleExternalId, String typeCode, String resourceCode,
                                         String operationCode) {
        return grantPlan(roleExternalId, typeCode, resourceCode, operationCode, "INSTANCE");
    }

    /** 类型级（scopeAll）授权计划——部门管理员 USER:VIEW 门票。 */
    private ObjectNode typeLevelGrantPlan(String roleExternalId, String typeCode, String operationCode) {
        return grantPlan(roleExternalId, typeCode, null, operationCode, "ALL");
    }

    private ObjectNode grantPlan(String roleExternalId, String typeCode, String resourceCode,
                                 String operationCode, String scopeMode) {
        var key = JSON.objectNode();
        key.put("resourceTypeCode", typeCode);
        if (resourceCode == null) {
            key.putNull("resourceCode");
        } else {
            key.put("resourceCode", resourceCode);
        }
        if (resourceCode == null) {
            key.putNull("codeType"); // scopeMode=ALL 时 resourceCode/codeType 须为空（20027）
        } else {
            key.put("codeType", "default");
        }
        key.put("operationCode", operationCode);
        key.put("scopeMode", scopeMode);
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

    /** apply-grant-plan removes 单条（removes 为授权行 id 数组）。 */
    private ObjectNode removePlan(String roleExternalId, Long permissionId) {
        var plan = JSON.objectNode();
        plan.putNull("creates");
        plan.putNull("updates");
        plan.set("removes", JSON.arrayNode().add(permissionId));
        var req = JSON.objectNode();
        req.putNull("domainCode");
        req.put("roleTypeCode", "BASIC_ROLE");
        req.put("roleExternalId", roleExternalId);
        req.set("plan", plan);
        return req;
    }

    // ===== 遍历辅助 =====

    private void collectPaths(JsonNode menus, List<String> out) {
        if (menus == null || !menus.isArray()) {
            return;
        }
        for (JsonNode m : menus) {
            if (m.path("path").isTextual()) {
                out.add(m.path("path").asText());
            }
            collectPaths(m.path("children"), out);
        }
    }

    private void collectOrgIds(JsonNode tree, List<Long> out) {
        if (tree == null || !tree.isArray()) {
            return;
        }
        for (JsonNode n : tree) {
            if (n.path("id").canConvertToLong()) {
                out.add(n.path("id").asLong());
            }
            collectOrgIds(n.path("children"), out);
        }
    }

    // ===== 请求辅助（MemberCandidatesGatePgIT 模式） =====

    private JsonNode postAsAdmin(String path, long operatorUserId, ObjectNode body) throws Exception {
        return performAndUnwrap(path, operatorUserId, body, null, 200);
    }

    private JsonNode performAndUnwrap(String path, long operatorUserId, ObjectNode body, String token,
                                      int expectedEnvelopeCode) throws Exception {
        long ts = System.currentTimeMillis() / 1000;
        String userId = String.valueOf(operatorUserId);
        var request = post(path).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(body));
        commonHeaders(userId, ts).forEach(request::header);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(request).andReturn();
        String raw = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode envelope = mapper.readTree(raw);
        assertThat(envelope.path("code").asInt())
            .as("业务信封码必须匹配，path=%s，响应：%s", path, raw).isEqualTo(expectedEnvelopeCode);
        return envelope.path("data");
    }

    private int performRawCode(String path, long operatorUserId, ObjectNode body, String token) throws Exception {
        long ts = System.currentTimeMillis() / 1000;
        String userId = String.valueOf(operatorUserId);
        var request = post(path).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(body));
        commonHeaders(userId, ts).forEach(request::header);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(request).andReturn();
        String raw = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(raw).path("code").asInt();
    }

    private Map<String, String> commonHeaders(String userId, long ts) throws Exception {
        return Map.of(
            "X-Internal-Secret", INTERNAL_SECRET,
            "X-Tenant-Id", String.valueOf(TENANT),
            "X-User-Id", userId,
            "X-User-Signature", hmac(userId, String.valueOf(TENANT), ts),
            "X-Signature-Timestamp", String.valueOf(ts));
    }

    private String loginAs(String username, String password) throws Exception {
        String captchaId = UUID.randomUUID().toString();
        stringRedisTemplate.opsForValue().set("captcha:" + captchaId, "3141", 5, TimeUnit.MINUTES);
        MvcResult result = mockMvc.perform(post("/api/access/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "tenantId", "1", "username", username, "password", password,
                    "captchaId", captchaId, "captchaCode", "3141", "clientId", "console"))))
            .andReturn();
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(body.path("code").asInt()).as("登录必须成功：%s", body).isEqualTo(200);
        return body.path("data").path("accessToken").asText();
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
