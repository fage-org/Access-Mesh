package cn.ac.fage.accessmesh.access.auth.service;

import static cn.ac.fage.accessmesh.access.it.GatewayTestSignatures.hmac;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import cn.ac.fage.accessmesh.access.auth.service.impl.OAuth2AppServiceImpl;
import cn.ac.fage.accessmesh.access.bootstrap.AccessBootstrapInitializer;
import cn.ac.fage.accessmesh.access.bootstrap.BootstrapGraphDefinition;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.it.TenantTestSupport;
import cn.ac.fage.accessmesh.access.support.OAuth2CredentialFixtures;
import cn.dev33.satoken.secure.BCrypt;

/**
 * OAuth2 客户端租户内唯一（T-ACCESS-097 / Q-069）回归锁。
 * <p>
 * client_id 由全局唯一改 (tenant_id, client_id) 复合唯一后：两租户可注册同名客户端，
 * 认证链（token/refresh 预读凭据定租户、JWT 按载荷 claim）跨租户互不可见、不误绑，
 * 重复注册仅对租户内重复提示 CLIENT_ID_EXISTS（不再泄露他租户占用）。
 * 主断言在旧全局唯一 DDL 下必红：第二个同名行 insert 触发唯一冲突 / 管理面注册
 * 收到数据库错误而非成功。Docker 不可用时由 Testcontainers 自动跳过（容器轨道）。
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
    "PERM_INTERNAL_SECRET=test-internal-secret-for-oauth2-uniq",
    "ACCESSMESH_SIGNATURE_SECRET=test-signature-secret-for-oauth2-uniq",
    "JWT_SECRET_KEY=test-jwt-secret-for-oauth2-tenant-uniq",
})
class Oauth2ClientTenantUniquenessPgIT {

    private static final String JWT_SECRET = "test-jwt-secret-for-oauth2-tenant-uniq";
    private static final String INTERNAL_SECRET = "test-internal-secret-for-oauth2-uniq";
    private static final String SIGN_SECRET = "test-signature-secret-for-oauth2-uniq";
    private static final String ADMIN_PASSWORD = "Ext@2026";
    private static final long TENANT_1 = 1L;
    private static final long TENANT_2 = 2L;
    private static final String REDIRECT_URI = "https://app.example/callback";

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, Oauth2ClientTenantUniquenessPgIT.class);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AccessBootstrapInitializer initializer;
    @Autowired
    private OAuth2AppServiceImpl oauth2Service;
    @Autowired
    private StringRedisTemplate redisTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void enableTenantFixtures() {
        TenantTestSupport.enableFixture(jdbc, redisTemplate, TENANT_1);
        TenantTestSupport.enableFixture(jdbc, redisTemplate, TENANT_2);
    }

    /** 生产契约形状直插客户端行（软删语义列齐全）。 */
    private long insertClient(long tenantId, String clientId, String plainSecret) {
        jdbc.update("""
            INSERT INTO sys_oauth2_client
                (tenant_id, client_id, client_secret, client_type, client_name, grant_types,
                 redirect_uris, scopes, access_token_ttl, refresh_token_ttl, status,
                 created_at, updated_at, delete_flag)
            VALUES (?, ?, ?, 'CONFIDENTIAL', ?, 'authorization_code,refresh_token', ?, NULL,
                    3600, 604800, 1, ?, ?, 0)
            """,
            tenantId, clientId, BCrypt.hashpw(plainSecret), "client-" + tenantId,
            REDIRECT_URI, LocalDateTime.now(), LocalDateTime.now());
        return jdbc.queryForObject(
            "SELECT id FROM sys_oauth2_client WHERE tenant_id = ? AND client_id = ? AND delete_flag = 0",
            Long.class, tenantId, clientId);
    }

    /** tenant1 直插启用测试用户（无强制改密标记；密码=夹具已知值，指纹可构造）。 */
    private long insertTenant1TestUser(String username) {
        jdbc.update("""
            INSERT INTO sys_user (id, tenant_id, username, password, name, status, user_type,
                force_reset_pwd, created_at, updated_at, delete_flag)
            VALUES (?, ?, ?, ?, ?, 1, 1, false, ?, ?, 0)
            """,
            System.nanoTime(), TENANT_1, username, OAuth2CredentialFixtures.PASSWORD_HASH,
            "同名隔离测试用户", LocalDateTime.now(), LocalDateTime.now());
        return jdbc.queryForObject(
            "SELECT id FROM sys_user WHERE tenant_id = ? AND username = ? AND delete_flag = 0",
            Long.class, TENANT_1, username);
    }

    @Test
    @DisplayName("两租户同名注册成功 + 租户内重复 10801（旧全局唯一 DDL 下第二个同名行必冲突红）")
    void sameClientIdRegistrableInTwoTenants_andDuplicateOnlyRejectedWithinTenant() throws Exception {
        initializer.initialize(TENANT_1, ADMIN_PASSWORD);
        long adminUserId = jdbc.queryForObject(
            "SELECT id FROM sys_user WHERE tenant_id = ? AND username = ? AND delete_flag = 0",
            Long.class, TENANT_1, BootstrapGraphDefinition.ADMIN_USERNAME);
        String sharedClientId = "admin-web-" + UUID.randomUUID().toString().substring(0, 8);

        // 租户 2 先占用同名标识（旧 DDL 全局唯一索引下此 insert 冲突 → 本用例红）
        insertClient(TENANT_2, sharedClientId, "t2-secret");

        // 租户 1 管理面注册同名 → 成功（旧 DDL 下查重带租户通过、insert 撞全局唯一 → 信封非 200 红）
        ObjectNode createReq = obj()
            .put("clientId", sharedClientId)
            .put("clientSecret", "t1-secret")
            .put("clientName", "租户一同名客户端")
            .put("grantTypes", "authorization_code,refresh_token")
            .put("redirectUris", REDIRECT_URI)
            .put("scopes", "profile");
        long createdId = postAsUser("/api/access/oauth2/client/create", adminUserId, createReq, 200).asLong();
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM sys_oauth2_client WHERE client_id = ? AND delete_flag = 0 AND tenant_id IN (1, 2)",
            Long.class, sharedClientId)).as("两租户同名行并存").isEqualTo(2L);

        // 租户内重复 → 10801 CLIENT_ID_EXISTS（仅租户内语义，不泄露他租户占用——他租户占用时上方已注册成功）
        assertThat(performRawCode("/api/access/oauth2/client/create", adminUserId, createReq))
            .isEqualTo(10801);
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM sys_oauth2_client WHERE tenant_id = ? AND client_id = ? AND delete_flag = 0",
            Long.class, TENANT_1, sharedClientId)).as("重复注册未产生新行").isEqualTo(1L);
    }

    @Test
    @DisplayName("token 兑换按授权码租户选行：他租户同名客户端凭自身 secret 不误绑（隔离+正向）")
    void tokenExchangeSelectsRowByCodeTenant() throws Exception {
        long userId = insertTenant1TestUser("oauth2-uniq-" + UUID.randomUUID().toString().substring(0, 8));
        String sharedClientId = "dup-" + UUID.randomUUID().toString().substring(0, 8);
        insertClient(TENANT_1, sharedClientId, "t1-secret");
        insertClient(TENANT_2, sharedClientId, "t2-secret");

        // 构造租户 1 签发的授权码（生产 Redis 形态：oauth2:code:<uuid>，一次性 TTL）
        OAuth2AppServiceImpl.AuthCodeData codeData = OAuth2CredentialFixtures.authCode();
        codeData.setClientId(sharedClientId);
        codeData.setUserId(userId);
        codeData.setTenantId(TENANT_1);
        codeData.setRedirectUri(REDIRECT_URI);
        String code = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set("oauth2:code:" + code,
            mapper.writeValueAsString(codeData), 300, TimeUnit.SECONDS);

        // 反向：租户 2 同名客户端的 secret 兑租户 1 的码 → 按码租户选中租户 1 行，
        // secret 不匹配拒绝 CLIENT_INVALID（跨租户同名不误绑）
        String rejected;
        try {
            oauth2Service.token(new cn.ac.fage.accessmesh.access.auth.dto.TokenReq(
                "authorization_code", sharedClientId, "t2-secret", code, REDIRECT_URI, null, null));
            rejected = "ok";
        } catch (cn.ac.fage.accessmesh.common.exception.BizException e) {
            rejected = String.valueOf(e.getErrorCode());
        }
        assertThat(rejected).as("他租户同名 secret 兑换必须拒绝")
            .isEqualTo(String.valueOf(cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode
                .OAUTH2_CLIENT_INVALID.getCode()));

        // 正向：租户 1 自身 secret 兑换成功（预读+选行+消费全链真实 Redis/PG）
        var resp = oauth2Service.token(new cn.ac.fage.accessmesh.access.auth.dto.TokenReq(
            "authorization_code", sharedClientId, "t1-secret", code, REDIRECT_URI, null, null));
        assertThat(resp.tokenType()).isEqualTo("Bearer");

        // 同码不可重放（一次性消费语义保持）
        String replay;
        try {
            oauth2Service.token(new cn.ac.fage.accessmesh.access.auth.dto.TokenReq(
                "authorization_code", sharedClientId, "t1-secret", code, REDIRECT_URI, null, null));
            replay = "ok";
        } catch (cn.ac.fage.accessmesh.common.exception.BizException e) {
            replay = String.valueOf(e.getErrorCode());
        }
        assertThat(replay).isEqualTo(String.valueOf(
            cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode.OAUTH2_CODE_INVALID.getCode()));
    }

    @Test
    @DisplayName("userinfo JWT 按载荷 tenant_id 解析客户端行：两租户同名行并存下仍正确放行本租户链")
    void userinfoJwtResolvesClientRowByTenantClaim() throws Exception {
        long userId = insertTenant1TestUser("oauth2-jwt-" + UUID.randomUUID().toString().substring(0, 8));
        String sharedClientId = "jwt-" + UUID.randomUUID().toString().substring(0, 8);
        insertClient(TENANT_1, sharedClientId, "t1-secret");
        insertClient(TENANT_2, sharedClientId, "t2-secret");

        long ts = System.currentTimeMillis() / 1000;
        String jwt = cn.dev33.satoken.jwt.SaJwtUtil.createToken("oauth2", userId, "oauth2", 3600,
            OAuth2CredentialFixtures.claims(Map.of(
                "tenant_id", "1", "jti", "jti-uniq-" + ts, "client_id", sharedClientId)),
            JWT_SECRET);

        var request = post("/api/access/auth/oauth2/userinfo")
            .contentType(MediaType.APPLICATION_JSON).content("{}")
            .header("Authorization", "Bearer " + jwt);
        MvcResult result = mockMvc.perform(request).andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(result.getResponse().getStatus()).as("userinfo 响应体：%s", body).isEqualTo(200);
        JsonNode envelope = mapper.readTree(body);
        assertThat(envelope.path("data").path("sub").asText()).isEqualTo(String.valueOf(userId));
    }

    private static ObjectNode obj() {
        return JsonNodeFactory.instance.objectNode();
    }

    private JsonNode postAsUser(String path, long operatorUserId, ObjectNode body, int expectedCode)
        throws Exception {
        String raw = performRaw(path, operatorUserId, body);
        JsonNode envelope = mapper.readTree(raw);
        assertThat(envelope.path("code").asInt())
            .as("业务信封码必须匹配，path=%s，响应：%s", path, raw).isEqualTo(expectedCode);
        return envelope.path("data");
    }

    private int performRawCode(String path, long operatorUserId, ObjectNode body) throws Exception {
        return mapper.readTree(performRaw(path, operatorUserId, body)).path("code").asInt();
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
            "X-Tenant-Id", String.valueOf(TENANT_1),
            "X-User-Id", userId,
            "X-User-Signature", hmac(SIGN_SECRET, userId, String.valueOf(TENANT_1), ts),
            "X-Signature-Timestamp", String.valueOf(ts));
    }
}
