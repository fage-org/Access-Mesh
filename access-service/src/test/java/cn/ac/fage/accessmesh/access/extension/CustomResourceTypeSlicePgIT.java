package cn.ac.fage.accessmesh.access.extension;

import cn.ac.fage.accessmesh.access.application.bootstrap.AccessBootstrapInitializer;
import cn.ac.fage.accessmesh.access.application.bootstrap.BootstrapGraphDefinition;
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
 * 自有资源类型端到端链路验证（T-FE-023 扩展点验证，真实 PostgreSQL + Redis）。
 * <p>
 * 固化「接入方声明自有数据维度」的完整扩展场景（extension-guide §资源类型扩展的回归锁）：
 * 管理员注册外部服务 → 声明 SYNC 所有权资源类型（type_definition.extra）→
 * 定义该类型的操作（操作位按类型隔离）→ 外部服务身份经 resource-entity/sync 同步资源
 * （X-Internal-Secret 凭证 + sourceService 一致性校验）→ 管理员经 apply-grant-plan 授予
 * BASIC_ROLE 实例级权限 → 接入方经 auth/check 得到引擎判定（allowed）。
 * 负向锁：未绑定角色的主体拒绝、未同步资源编码 fail-closed 拒绝。
 * 主体/角色装配走 jdbc 直插（FileServiceSecurityPgIT 模式），被测对象是 HTTP API 链路本身。
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
    "PERM_INTERNAL_SECRET=test-internal-secret-for-custom-type-slice",
    "ACCESSMESH_SIGNATURE_SECRET=test-signature-secret-for-custom-type-slice",
})
class CustomResourceTypeSlicePgIT {

    private static final Long TENANT = 1L;
    private static final String ADMIN_PASSWORD = "Ext@2026";
    private static final String INTERNAL_SECRET = "test-internal-secret-for-custom-type-slice";
    private static final String SIGN_SECRET = "test-signature-secret-for-custom-type-slice";

    /** 接入方服务与自有类型/资源/操作的业务键（与固定图种子无碰撞） */
    private static final String SOURCE_SERVICE = "e2e-order-service";
    private static final String CUSTOM_TYPE = "E2E_ORDER";
    private static final String RESOURCE_CODE = "ORDER-1001";
    /** 超出 CRUD 预置的自定义操作（resource_type 创建即预置 CREATE/VIEW/UPDATE/DELETE，此处验证按需追加） */
    private static final String OP_EXPORT = "EXPORT";

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, CustomResourceTypeSlicePgIT.class);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AccessBootstrapInitializer initializer;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("自有资源类型扩展链路：注册服务→声明 SYNC 类型→定义操作→服务身份同步→授权→引擎判定 allowed；未绑定主体与未同步资源 fail-closed")
    void customResourceTypeExtensionSlice_fullChain() throws Exception {
        // —— 阶段 0：空库 bootstrap 首管理员（管理面请求按 Gateway 转发形态：内部凭证 + 验签 X-User-Id） ——
        initializer.initialize(ADMIN_PASSWORD);
        long adminUserId = jdbc.queryForObject(
            "SELECT id FROM sys_user WHERE tenant_id = ? AND username = ? AND delete_flag = 0",
            Long.class, TENANT, BootstrapGraphDefinition.ADMIN_USERNAME);
        assertThat(adminUserId).as("bootstrap 必须建出首管理员").isPositive();

        // —— 阶段 1：注册接入方服务（status=1 启用；SYNC 类型声明的来源门禁前置） ——
        JsonNode service = postAsAdmin("/api/perm/service-config/save", adminUserId,
            JSON.objectNode()
                .put("serviceCode", SOURCE_SERVICE)
                .put("name", "订单服务（扩展链路验证）")
                .put("basePath", "")
                .put("status", 1));
        assertThat(service.path("serviceCode").asText()).isEqualTo(SOURCE_SERVICE);

        // —— 阶段 2：声明自有资源类型（SYNC 所有权 + 来源服务） ——
        postAsAdmin("/api/perm/type-definition/create", adminUserId,
            JSON.objectNode()
                .put("typeKey", "resource_type")
                .put("typeCode", CUSTOM_TYPE)
                .put("name", "订单（扩展链路验证）")
                .put("extra", "{\"managedMode\":\"SYNC\",\"syncSourceService\":\"" + SOURCE_SERVICE + "\"}"));
        Integer typeValue = jdbc.queryForObject(
            "SELECT type_value FROM type_definition WHERE tenant_id = ? AND type_key = 'resource_type' "
                + "AND type_code = ? AND delete_flag = 0",
            Integer.class, TENANT, CUSTOM_TYPE);
        assertThat(typeValue).as("自定义资源类型必须成功建号（type_value 自动分配）").isNotNull();

        // —— 阶段 3：为自有类型追加自定义操作（创建类型时已自动预置 CRUD 四操作，断言预置后再追加 EXPORT；
        //     操作位空间按类型隔离，binaryBit 类型内唯一且避开预置位 1/2/4/8） ——
        Integer seededView = jdbc.queryForObject(
            "SELECT binary_bit FROM operation_permission WHERE tenant_id = ? AND resource_type = ? "
                + "AND code = 'VIEW' AND delete_flag = 0",
            Integer.class, TENANT, typeValue);
        assertThat(seededView).as("resource_type 创建必须自动预置 CRUD 操作（VIEW 在列）").isEqualTo(2);
        postAsAdmin("/api/perm/operation-permission/create", adminUserId,
            JSON.objectNode()
                .put("resourceTypeCode", CUSTOM_TYPE)
                .put("code", OP_EXPORT)
                .put("name", "导出订单")
                .put("binaryBit", 16L)
                .put("inheritMask", 0L));

        // —— 阶段 4：接入方服务身份同步资源（内部凭证 → SERVICE 上下文 + sourceService 一致性） ——
        JsonNode syncResult = postAsService("/api/perm/resource-entity/sync",
            JSON.objectNode()
                .put("operation", "UPSERT")
                .put("resourceTypeCode", CUSTOM_TYPE)
                .put("resourceCode", RESOURCE_CODE)
                .put("name", "测试订单 1001")
                .put("sourceService", SOURCE_SERVICE)
                .set("syncVersion", JSON.objectNode()
                    .put("occurredAt", "2026-09-12T00:00:00")
                    .put("sequenceNo", 1L)));
        assertThat(syncResult.path("accepted").asBoolean()).as("同步必须被接受：" + syncResult).isTrue();
        assertThat(syncResult.path("applied").asBoolean()).as("同步必须实际生效：" + syncResult).isTrue();
        assertThat(syncResult.path("stale").asBoolean()).as("首次同步不得判陈旧：" + syncResult).isFalse();

        String maintainSource = jdbc.queryForObject(
            "SELECT re.maintain_source FROM resource_entity re WHERE re.tenant_id = ? AND re.code = ? "
                + "AND re.resource_type = ? AND re.delete_flag = 0",
            String.class, TENANT, RESOURCE_CODE, typeValue);
        assertThat(maintainSource)
            .as("同步落库行必须携带 SYNC 归属标记（类型级所有权的行级记录值）")
            .isEqualTo("SYNC");

        // —— 阶段 5：主体装配（jdbc 直插：本地用户 + BASIC_ROLE + 绑定 + 对照裸用户） ——
        long grantedUserId = insertLocalUserWithBasicRole("ext-slice-granted");
        long bareUserId = insertLocalUser("ext-slice-bare");
        String roleExternalId = jdbc.queryForObject(
            "SELECT r.external_id FROM abstract_role r JOIN user_role ur ON ur.tenant_id = r.tenant_id "
                + "AND ur.target_type = 'ROLE' AND ur.target_id = r.id "
                + "WHERE ur.tenant_id = ? AND ur.abstract_user_id = ? AND r.delete_flag = 0",
            String.class, TENANT, grantedUserId);

        // —— 阶段 5a：未种子先授权 → 20040（首笔授权引导的必要性锁：checkCanGrant 严格无旁路，
        //     若实现侧引入操作者豁免/旁路，本断言失败——extension-guide §3.5 承诺的回归锁） ——
        performExpectCode("/api/perm/role-resource-permission/apply-grant-plan", adminUserId,
            grantPlanReq(roleExternalId), 20040);

        // —— 阶段 5b：部署方种子——给管理员引导角色（bootstrap-admin）插新类型类型级可转授权
        //     （EXPORT 位 16）。全新类型上无人持有可转授覆盖权限，首笔授权必须由部署方种子引导
        //     （固定图只覆盖种子类型；extension-guide §3.5 同口径） ——
        Long adminRoleId = jdbc.queryForObject(
            "SELECT r.id FROM abstract_role r JOIN user_role ur ON ur.tenant_id = r.tenant_id "
                + "AND ur.target_type = 'ROLE' AND ur.target_id = r.id "
                + "WHERE ur.tenant_id = ? AND ur.abstract_user_id = ? AND r.external_id = ?",
            Long.class, TENANT, adminUserId, BootstrapGraphDefinition.ADMIN_ROLE_EXTERNAL_ID);
        assertThat(adminRoleId).as("bootstrap 管理员必须绑定引导角色（bootstrap-admin）").isNotNull();
        jdbc.update(
            "INSERT INTO role_resource_permission "
                + "(tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type, scope_all, can_grant, grant_source) "
                + "VALUES (?, ?, NULL, 16, ?, true, true, 'MANUAL')",
            TENANT, adminRoleId, typeValue);

        // —— 阶段 6：管理员经授权页同源写入口授予实例级 EXPORT（种子后转授资格成立） ——
        JsonNode grantItems = postAsAdmin("/api/perm/role-resource-permission/apply-grant-plan", adminUserId,
                grantPlanReq(roleExternalId))
            .path("items");
        assertThat(grantItems.isArray() && grantItems.size() == 1)
            .as("授权计划必须对自定义类型资源产生恰好一条记录").isTrue();

        // —— 阶段 7：接入方经 auth/check 取得引擎判定 ——
        JsonNode allowed = postAsService("/api/perm/auth/check",
            checkReq(String.valueOf(grantedUserId), RESOURCE_CODE));
        assertThat(allowed.path("allowed").asBoolean())
            .as("绑定角色 + 实例级授权后必须 allowed：" + allowed).isTrue();

        // —— 负向锁①：未绑定任何角色的主体拒绝（reason=NO_ROLE 钉死主体装配正确——
        //     主体装配失败走 USER_NOT_FOUND，同样 allowed=false 但语义不同） ——
        JsonNode bareDenied = postAsService("/api/perm/auth/check",
            checkReq(String.valueOf(bareUserId), RESOURCE_CODE));
        assertThat(bareDenied.path("allowed").asBoolean())
            .as("裸用户必须被拒绝：" + bareDenied).isFalse();
        assertThat(bareDenied.path("reason").asText())
            .as("裸用户拒绝原因必须是零角色而非主体缺失").isEqualTo("NO_ROLE");

        // —— 负向锁②：未同步的资源编码 fail-closed 拒绝（资源不存在不给权限） ——
        JsonNode unknownDenied = postAsService("/api/perm/auth/check",
            checkReq(String.valueOf(grantedUserId), "ORDER-9999"));
        assertThat(unknownDenied.path("allowed").asBoolean())
            .as("未同步资源编码必须 fail-closed 拒绝：" + unknownDenied).isFalse();
    }

    // ===== 请求助手 =====

    /** apply-grant-plan 请求体（BASIC_ROLE + 目标资源实例级 EXPORT 单条 create）。 */
    private ObjectNode grantPlanReq(String roleExternalId) {
        var key = JSON.objectNode();
        key.put("resourceTypeCode", CUSTOM_TYPE);
        key.put("resourceCode", RESOURCE_CODE);
        key.put("codeType", "default");
        key.put("operationCode", OP_EXPORT);
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

    private ObjectNode checkReq(String subjectExternalId, String resourceCode) {
        return JSON.objectNode()
            // 本地登录用户的主体类型是 LOCAL_USER（user_type=3）；USER(user_type=1) 是族内另一类型
            .put("subjectTypeCode", "LOCAL_USER")
            .put("subjectExternalId", subjectExternalId)
            .put("resourceTypeCode", CUSTOM_TYPE)
            .put("resourceCode", resourceCode)
            .put("operationCode", OP_EXPORT);
    }

    /** 管理员操作请求，断言业务信封 200 并返回 data 节点（Gateway 转发形态：内部凭证 + HMAC 验签 X-User-Id → USER 上下文）。 */
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

    /** 管理员操作请求，断言业务信封为指定错误码（负向锁）。 */
    private void performExpectCode(String path, long operatorUserId, ObjectNode body, int expectedCode) throws Exception {
        long ts = System.currentTimeMillis() / 1000;
        String userId = String.valueOf(operatorUserId);
        performAndUnwrap(path, body, Map.of(
            "X-Internal-Secret", INTERNAL_SECRET,
            "X-Tenant-Id", String.valueOf(TENANT),
            "X-User-Id", userId,
            "X-User-Signature", hmac(userId, String.valueOf(TENANT), ts),
            "X-Signature-Timestamp", String.valueOf(ts)), expectedCode);
    }

    /** 接入方服务身份请求（X-Internal-Secret 凭证 + X-Service-Code + X-Tenant-Id → SERVICE 上下文），断言业务信封 200。 */
    private JsonNode postAsService(String path, ObjectNode body) throws Exception {
        return performAndUnwrap(path, body, Map.of(
            "X-Internal-Secret", INTERNAL_SECRET,
            "X-Service-Code", SOURCE_SERVICE,
            "X-Tenant-Id", String.valueOf(TENANT)), 200);
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

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    /** Gateway 转发签名（与 SecurityMatrixIT 同算法：HmacSHA256("userId|tenantId|timestamp") 十六进制）。 */
    private static String hmac(String userId, String tenantId, long timestamp) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(
            SIGN_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String payload = userId + "|" + tenantId + "|" + timestamp;
        return java.util.HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    // ===== 数据装配（jdbc 直插，先于相关主体首次引擎调用） =====

    /** 仅 sys_user + abstract_user 的本地用户（无角色无授权 → 引擎 fail-closed）。 */
    private long insertLocalUser(String marker) {
        long id = 941500L + SEQ.incrementAndGet();
        String username = marker + "-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update(
            "INSERT INTO sys_user (id, tenant_id, username, password, name, status, user_type, force_reset_pwd) "
                + "VALUES (?, ?, ?, ?, ?, 1, 3, false)",
            id, TENANT, username, cn.dev33.satoken.secure.BCrypt.hashpw("Ext@2026"), marker);
        jdbc.update(
            "INSERT INTO abstract_user (id, tenant_id, user_type, external_id, name, enabled, extra) "
                + "VALUES (?, ?, 3, ?, ?, true, '{}')",
            id, TENANT, String.valueOf(id), marker);
        return id;
    }

    /** 本地用户 + BASIC_ROLE（role_type=6 种子值）+ user_role 绑定。 */
    private long insertLocalUserWithBasicRole(String marker) {
        long userId = insertLocalUser(marker);
        String roleExternalId = marker + "-role-" + UUID.randomUUID().toString().substring(0, 8);
        Long roleId = jdbc.queryForObject(
            "INSERT INTO abstract_role (tenant_id, role_type, external_id, name, status, parent_id, extra) "
                + "VALUES (?, 6, ?, ?, 1, NULL, '{}') RETURNING id",
            Long.class, TENANT, roleExternalId, marker + "-角色");
        jdbc.update(
            "INSERT INTO user_role (tenant_id, abstract_user_id, target_type, target_id) VALUES (?, ?, 'ROLE', ?)",
            TENANT, userId, roleId);
        return userId;
    }

    private static final java.util.concurrent.atomic.AtomicLong SEQ = new java.util.concurrent.atomic.AtomicLong();
}
