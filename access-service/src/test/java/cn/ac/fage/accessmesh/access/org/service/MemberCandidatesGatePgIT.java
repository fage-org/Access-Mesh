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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 组织/岗位成员候选与分配门禁统一验收（T-ORG-003，F005；真实 PostgreSQL + Redis，
 * 真权限引擎链——门禁不 mock，授权事实经 apply-grant-plan 真实落库）。
 * <p>
 * 固化「仅持成员动作权的有限管理员」完整组合链：bootstrap 首管理员建默认树+岗位 →
 * 有限管理员（BASIC_ROLE，仅 VIEW@默认根 + ASSIGN_POSITION_USER@岗位，无任何
 * UPDATE 类操作码）经真实登录拿会话 → member-candidates 出候选（默认树可见范围
 * 裁剪 + 排除已绑定）→ user-org/assign 挂载成功（投影落库）→ 候选排除已绑定成员 →
 * org/update 改结构拒绝（403，成员动作权不等于结构编辑权）。
 * 负向锁：仅 VIEW 无成员动作权 403；越租户目标组织 ORG_NOT_FOUND。
 * 旧实现 member-candidates 固定 ORG:UPDATE——有限管理员（无 UPDATE）在候选一步即
 * 403，本链主用例必红（首管理员全码掩盖了该分叉，F005）。
 * 浏览器分配链证据由 T-ACCESS-055 组合验收承接（2026-09-22 用户拍板，验收④改写）。
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
    "PERM_INTERNAL_SECRET=test-internal-secret-for-member-gate",
    "ACCESSMESH_SIGNATURE_SECRET=test-signature-secret-for-member-gate",
})
class MemberCandidatesGatePgIT {

    private static final Long TENANT = 1L;
    private static final String ADMIN_PASSWORD = "Ext@2026";
    private static final String INTERNAL_SECRET = "test-internal-secret-for-member-gate";
    private static final String SIGN_SECRET = "test-signature-secret-for-member-gate";

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, MemberCandidatesGatePgIT.class);
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
    @DisplayName("有限管理员组合链：仅 ASSIGN_POSITION_USER+VIEW 选人→挂载→排除已绑定；改结构拒；无成员动作权拒；越租户拒")
    void limitedAdminMemberAssignmentCombo() throws Exception {
        // —— 阶段 0：空库 bootstrap（首管理员全码 + 默认树 + 树配置） ——
        initializer.initialize(ADMIN_PASSWORD);
        long adminUserId = jdbc.queryForObject(
            "SELECT id FROM sys_user WHERE tenant_id = ? AND username = ? AND delete_flag = 0",
            Long.class, TENANT, BootstrapGraphDefinition.ADMIN_USERNAME);
        long rootOrgId = jdbc.queryForObject(
            "SELECT root_org_id FROM sys_org_tree_config WHERE tenant_id = ? AND is_default = true AND delete_flag = 0",
            Long.class, TENANT);

        // —— 阶段 1：默认树内建叶子部门 + 岗位（org/create 维护 ORG 投影，实例级门禁可用；
        //     create 响应体即新组织 id；sys_org.code 非空须显式给码） ——
        String marker = UUID.randomUUID().toString().substring(0, 8);
        long leafOrgId = postAsAdmin("/api/access/org/create", adminUserId,
                JSON.objectNode().put("orgType", 1).put("orgName", "研发部")
                    .put("parentOrgId", rootOrgId).put("code", "mcg-leaf-" + marker))
            .asLong();
        long positionOrgId = postAsAdmin("/api/access/org/create", adminUserId,
                JSON.objectNode().put("orgType", 2).put("orgName", "交付岗位")
                    .put("parentOrgId", leafOrgId).put("code", "mcg-pos-" + marker))
            .asLong();

        // —— 阶段 2：两个默认树用户（目标用户 + 有限管理员），初始密码仅创建响应返回 ——
        JsonNode targetUser = postAsAdmin("/api/access/user/create", adminUserId,
            JSON.objectNode()
                .put("username", "mcg-target-" + marker)
                .put("name", "候选目标用户")
                .put("orgId", leafOrgId));
        long targetUserId = targetUser.path("id").asLong();
        JsonNode limitedAdmin = postAsAdmin("/api/access/user/create", adminUserId,
            JSON.objectNode()
                .put("username", "mcg-limited-" + marker)
                .put("name", "有限管理员")
                .put("orgId", leafOrgId));
        long limitedAdminId = limitedAdmin.path("id").asLong();
        String limitedPassword = limitedAdmin.path("initialPassword").asText();

        // —— 阶段 3：有限管理员 BASIC_ROLE + 仅两行授权（VIEW@默认根 实例级·判定面继承
        //     覆盖默认树；ASSIGN_POSITION_USER@岗位 实例级；无任何 UPDATE 类操作码）。
        //     授权行 jdbc 直插（slice PgIT 先例）：bootstrap 固定图业务门禁全 canGrant=false
        //     （转授链仅 API:ACCESS）、ORG 预置类型无 AUTHORITY_ROOT——apply-grant-plan 对
        //     ORG 的转授起点属 T-ACCESS-052「首次委派」射程，本卡测授权消费面 ——
        String roleExternalId = "mcg-role-" + marker;
        Long limitedRoleId = jdbc.queryForObject(
            "INSERT INTO abstract_role (tenant_id, role_type, external_id, name, status, parent_id, extra) "
                + "VALUES (?, 6, ?, '岗位用户运营（有限）', 1, NULL, '{}') RETURNING id",
            Long.class, TENANT, roleExternalId);
        jdbc.update(
            "INSERT INTO user_role (tenant_id, abstract_user_id, target_type, target_id) VALUES (?, ?, 'ROLE', ?)",
            TENANT, limitedAdminId, limitedRoleId);
        long rootEntityId = orgEntityId(rootOrgId);
        long positionEntityId = orgEntityId(positionOrgId);
        insertOrgInstanceGrant(limitedRoleId, rootEntityId, 2L);     // VIEW（DDL bit=2）
        insertOrgInstanceGrant(limitedRoleId, positionEntityId, 128L); // ASSIGN_POSITION_USER（DDL bit=128）

        // —— 阶段 4：有限管理员真实登录拿会话（member-candidates 取操作者走 StpUtil） ——
        String token = loginAs("mcg-limited-" + marker, limitedPassword);

        // —— 主链①：候选查询放行且含默认树可见用户（旧实现固定 ORG:UPDATE，此处必红）——
        //     正向包含双断言（评审 P3-1 补强）：目标用户必须实际出现在候选池，
        //     防「可见性裁剪漏人但下界断言仍过」的弱锁形态
        JsonNode candidates = performAndUnwrap("/api/access/user/member-candidates", limitedAdminId,
            JSON.objectNode().put("targetOrgId", positionOrgId), token, 200);
        java.util.List<Long> candidateIds = new java.util.ArrayList<>();
        candidates.path("items").forEach(i -> candidateIds.add(i.path("id").asLong()));
        assertThat(candidateIds)
            .as("目标用户与有限管理员（均挂默认树叶子）必须在候选列表：%s", candidates)
            .contains(targetUserId, limitedAdminId);

        // —— 主链②：挂载成功（assign 门禁与候选同权，无需 ORG:UPDATE） ——
        ObjectNode assignReq = JSON.objectNode().put("userId", targetUserId);
        assignReq.putArray("orgIds").add(positionOrgId);
        postAsUser("/api/access/user-org/assign", limitedAdminId, assignReq, token, 200);
        Long membership = jdbc.queryForObject(
            "SELECT id FROM sys_user_org WHERE tenant_id = ? AND user_id = ? AND org_id = ? AND delete_flag = 0",
            Long.class, TENANT, targetUserId, positionOrgId);
        assertThat(membership).as("挂载必须落 sys_user_org 行").isNotNull();
        Long positionBinding = jdbc.queryForObject(
            "SELECT ur.id FROM user_role ur JOIN abstract_role r ON r.tenant_id = ur.tenant_id AND r.id = ur.target_id "
                + "WHERE ur.tenant_id = ? AND ur.abstract_user_id = ? AND r.role_type = 2 AND ur.delete_flag = 0",
            Long.class, TENANT, targetUserId);
        assertThat(positionBinding).as("岗位挂载必须产出 POSITION 容器投影绑定（role_type=2）").isNotNull();

        // —— 主链③：候选排除已绑定成员（首管理员与有限管理员仍在候选，目标用户被排除） ——
        JsonNode afterAssign = performAndUnwrap("/api/access/user/member-candidates", limitedAdminId,
            JSON.objectNode().put("targetOrgId", positionOrgId), token, 200);
        java.util.List<Long> afterIds = new java.util.ArrayList<>();
        afterAssign.path("items").forEach(i -> afterIds.add(i.path("id").asLong()));
        assertThat(afterIds)
            .as("挂载后目标用户必须被排除，其余默认树用户保留：%s", afterAssign)
            .doesNotContain(targetUserId)
            .contains(limitedAdminId);

        // —— 负向锁①：同一管理员不能改组织结构（org/update → 403，成员动作权≠结构编辑权） ——
        int deniedCode = performRawCode("/api/access/org/update", limitedAdminId,
            JSON.objectNode().put("id", positionOrgId).put("orgName", "越权改名"), token);
        assertThat(deniedCode).as("有限管理员改组织结构必须 403").isEqualTo(403);

        // —— 负向锁②：仅 VIEW 无成员动作权的管理员候选拒绝（先存在性后判权，不触可见性） ——
        String viewOnlyRoleExternalId = "mcg-view-role-" + marker;
        Long viewRoleId = jdbc.queryForObject(
            "INSERT INTO abstract_role (tenant_id, role_type, external_id, name, status, parent_id, extra) "
                + "VALUES (?, 6, ?, '只读观察员', 1, NULL, '{}') RETURNING id",
            Long.class, TENANT, viewOnlyRoleExternalId);
        insertOrgInstanceGrant(viewRoleId, rootEntityId, 2L); // 仅 VIEW@默认根
        JsonNode viewOnlyUser = postAsAdmin("/api/access/user/create", adminUserId,
            JSON.objectNode()
                .put("username", "mcg-viewonly-" + marker)
                .put("name", "只读观察员")
                .put("orgId", leafOrgId));
        long viewOnlyUserId = viewOnlyUser.path("id").asLong();
        jdbc.update(
            "INSERT INTO user_role (tenant_id, abstract_user_id, target_type, target_id) VALUES (?, ?, 'ROLE', ?)",
            TENANT, viewOnlyUserId, viewRoleId);
        int viewDeniedCode = performRawCode("/api/access/user/member-candidates", viewOnlyUserId,
            JSON.objectNode().put("targetOrgId", positionOrgId), null);
        assertThat(viewDeniedCode).as("无成员动作权（仅 VIEW）候选必须 403").isEqualTo(403);

        // —— 负向锁③：越租户目标组织 → ORG_NOT_FOUND(10101)，不泄露他租户组织存在性 ——
        jdbc.update(
            "INSERT INTO sys_org (id, tenant_id, parent_id, org_type, code, name, level, sort_order, status, delete_flag) "
                + "VALUES (?, 2, 0, '1', 'other-tenant-org', '他租户组织', 1, 0, 1, 0)",
            880001L);
        int crossTenantCode = performRawCode("/api/access/user/member-candidates", limitedAdminId,
            JSON.objectNode().put("targetOrgId", 880001L), token);
        assertThat(crossTenantCode).as("越租户目标必须按组织不存在拒绝（10101）").isEqualTo(10101);
    }

    // ===== 请求辅助（Gateway 转发形态：内部凭证 + 验签 X-User-Id；有限管理员另附会话令牌） =====

    private JsonNode postAsAdmin(String path, long operatorUserId, ObjectNode body) throws Exception {
        return performAndUnwrap(path, operatorUserId, body, null, 200);
    }

    private JsonNode postAsUser(String path, long operatorUserId, ObjectNode body, String token, int expectedCode)
        throws Exception {
        return performAndUnwrap(path, operatorUserId, body, token, expectedCode);
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

    /** 负向锁用：返回业务信封码，不断言（403/错误码共用）。 */
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

    /** 真实登录（LoginSessionPgIT 模式：真实 Redis 验证码 + BCrypt 密码 + Sa-Token 会话）。 */
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
        assertThat(body.path("code").asInt()).as("有限管理员登录必须成功：%s", body).isEqualTo(200);
        return body.path("data").path("accessToken").asText();
    }

    // ===== 授权装配与签名辅助 =====

    /** ORG(29) 实例投影 id（org/create 与 bootstrap 写路径同事务维护 resource_entity 行）。 */
    private long orgEntityId(long orgId) {
        return jdbc.queryForObject(
            "SELECT id FROM resource_entity WHERE tenant_id = ? AND resource_type = 29 AND code = ? AND delete_flag = 0",
            Long.class, TENANT, String.valueOf(orgId));
    }

    /** ORG 实例级 MANUAL 授权行（单操作位，形状对齐 apply-grant-plan 产出行；slice PgIT 直插先例）。 */
    private void insertOrgInstanceGrant(long roleId, long resourceEntityId, long bit) {
        jdbc.update(
            "INSERT INTO role_resource_permission "
                + "(tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type, scope_all, can_grant, grant_source) "
                + "VALUES (?, ?, ?, ?, 29, false, false, 'MANUAL')",
            TENANT, roleId, resourceEntityId, bit);
    }

    /** Gateway 转发签名（与 SecurityMatrixIT 同算法：HmacSHA256("userId|tenantId|timestamp") 十六进制）。 */
    private static String hmac(String userId, String tenantId, long timestamp) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(
            SIGN_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String payload = userId + "|" + tenantId + "|" + timestamp;
        return java.util.HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
}
