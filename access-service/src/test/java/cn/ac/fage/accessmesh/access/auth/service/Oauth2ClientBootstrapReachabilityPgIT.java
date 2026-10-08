package cn.ac.fage.accessmesh.access.auth.service;

import static cn.ac.fage.accessmesh.access.it.GatewayTestSignatures.hmac;

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
 * OAuth2 客户端管理默认图可达性验收（2026-10-06 逐任务评审 P1-4，用户拍板「全档补齐」
 * ——ADMIN_NOTICE 先例）：bootstrap 固定图此前无 oauth2 路由/授权/派生映射三处，默认部署
 * create 的类型级 CREATE 与 update/delete 的实例级门禁恒拒——T-ACCESS-084 交付的公开客户端
 * 在默认部署不可注册（本类主断言在旧图必红）。
 * <p>
 * 真权限引擎链（门禁不 mock）；update/delete 实例级校验由类型级 scopeAll 授权覆盖
 * （TYPE_DEFINITION/RESOURCE 先例口径）——客户端无资源投影，实例级授权不可构造为
 * 已知终态（同 ADMIN_NOTICE 口径）。未授权用户负向锁证明门禁在岗，防「删门禁也绿」。
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
    "accessmesh.sync.scheduler.enabled=false", "access.tenant.gate-repair.enabled=false",
    "mybatis-flex.configuration.map-underscore-to-camel-case=true",
    "logging.level.cn.ac.fage.accessmesh=WARN",
    "PERM_INTERNAL_SECRET=test-internal-secret-for-oauth2-reach",
    "ACCESSMESH_SIGNATURE_SECRET=test-signature-secret-for-oauth2-reach",
})
class Oauth2ClientBootstrapReachabilityPgIT {
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.data.redis.core.StringRedisTemplate tenantFixtureRedis;
    @org.junit.jupiter.api.BeforeEach
    void enableTenantFixture() {
        cn.ac.fage.accessmesh.access.it.TenantTestSupport.enableFixture(jdbc,tenantFixtureRedis,1L);
    }


    private static final Long TENANT = 1L;
    private static final String ADMIN_PASSWORD = "Ext@2026";
    private static final String INTERNAL_SECRET = "test-internal-secret-for-oauth2-reach";
    private static final String SIGN_SECRET = "test-signature-secret-for-oauth2-reach";

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, Oauth2ClientBootstrapReachabilityPgIT.class);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AccessBootstrapInitializer initializer;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("默认图 admin 可注册/更新/删除 OAuth2 客户端（旧图无授权起点=403 必红）；未授权用户 403")
    void adminCanManageOauth2ClientsOnDefaultBootstrapGraph() throws Exception {
        initializer.initialize(1L, ADMIN_PASSWORD);
        long adminUserId = jdbc.queryForObject(
            "SELECT id FROM sys_user WHERE tenant_id = ? AND username = ? AND delete_flag = 0",
            Long.class, TENANT, BootstrapGraphDefinition.ADMIN_USERNAME);
        long rootOrgId = jdbc.queryForObject(
            "SELECT root_org_id FROM sys_org_tree_config WHERE tenant_id = ? AND is_default = true AND delete_flag = 0",
            Long.class, TENANT);
        String marker = UUID.randomUUID().toString().substring(0, 8);

        // 未授权普通用户（无任何角色）：create 恒 403——负向锁证明门禁在岗
        JsonNode plain = postAsAdmin("/api/access/user/create", adminUserId,
            obj().put("username", "oauth2-plain-" + marker).put("name", "未授权用户").put("orgId", rootOrgId));
        long plainUserId = plain.path("id").asLong();

        ObjectNode createReq = obj()
            .put("clientId", "spa-" + marker)
            .put("clientType", "PUBLIC")
            .put("clientName", "可达性探针客户端")
            .put("grantTypes", "authorization_code,refresh_token")
            .put("redirectUris", "https://app.example/callback")
            .put("scopes", "profile");
        assertThat(performRawCode("/api/access/oauth2/client/create", plainUserId, createReq))
            .as("未授权用户 create 必须被类型级 CREATE 门禁拒绝（信封 403）").isEqualTo(403);

        // 主断言（旧图必红）：admin 全链放行——create 类型级 CREATE
        long clientId = postAsAdmin("/api/access/oauth2/client/create", adminUserId, createReq).asLong();

        // update 实例级 UPDATE（类型级 scopeAll 覆盖——无资源投影下的终态形态）
        postAsAdmin("/api/access/oauth2/client/update", adminUserId,
            obj().put("id", clientId).put("clientName", "改名后"), 200);
        assertThat(jdbc.queryForObject(
            "SELECT client_name FROM sys_oauth2_client WHERE tenant_id = ? AND id = ? AND delete_flag = 0",
            String.class, TENANT, clientId)).isEqualTo("改名后");

        // delete 批量实例级 DELETE
        ObjectNode deleteReq = obj();
        deleteReq.putArray("ids").add(clientId);
        postAsAdmin("/api/access/oauth2/client/delete", adminUserId, deleteReq, 200);
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM sys_oauth2_client WHERE tenant_id = ? AND id = ? AND delete_flag = 0",
            Long.class, TENANT, clientId)).as("删除为软删").isZero();
    }

    private static ObjectNode obj() {
        return JsonNodeFactory.instance.objectNode();
    }

    private JsonNode postAsAdmin(String path, long operatorUserId, ObjectNode body) throws Exception {
        return performAndUnwrap(path, operatorUserId, body, 200);
    }

    private JsonNode postAsAdmin(String path, long operatorUserId, ObjectNode body, int expectedCode)
        throws Exception {
        return performAndUnwrap(path, operatorUserId, body, expectedCode);
    }

    private JsonNode performAndUnwrap(String path, long operatorUserId, ObjectNode body,
                                      int expectedEnvelopeCode) throws Exception {
        String raw = performRaw(path, operatorUserId, body);
        JsonNode envelope = mapper.readTree(raw);
        assertThat(envelope.path("code").asInt())
            .as("业务信封码必须匹配，path=%s，响应：%s", path, raw).isEqualTo(expectedEnvelopeCode);
        return envelope.path("data");
    }

    /** 负向锁用：返回业务信封码，不断言。 */
    private int performRawCode(String path, long operatorUserId, ObjectNode body) throws Exception {
        String raw = performRaw(path, operatorUserId, body);
        return mapper.readTree(raw).path("code").asInt();
    }

    private String performRaw(String path, long operatorUserId, ObjectNode body) throws Exception {
        long ts = System.currentTimeMillis() / 1000;
        String userId = String.valueOf(operatorUserId);
        var request = post(path).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(body));
        commonHeaders(userId, ts).forEach(request::header);
        MvcResult result = mockMvc.perform(request).andReturn();
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private Map<String, String> commonHeaders(String userId, long ts) throws Exception {
        return Map.of(
            "X-Internal-Secret", INTERNAL_SECRET,
            "X-Tenant-Id", String.valueOf(TENANT),
            "X-User-Id", userId,
            "X-User-Signature", hmac(SIGN_SECRET, userId, String.valueOf(TENANT), ts),
            "X-Signature-Timestamp", String.valueOf(ts));
    }
}
