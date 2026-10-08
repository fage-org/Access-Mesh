package cn.ac.fage.accessmesh.access.auth.service;

import cn.ac.fage.accessmesh.access.bootstrap.PlatformBootstrapInitializer;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.auth.security.LoginFailureStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("testcontainers")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false", "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false", "accessmesh.sync.scheduler.enabled=false", "access.tenant.gate-repair.enabled=false",
    "access.platform.bootstrap.enabled=false"
})
class PlatformAuthenticationPgIT {
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) { ItInfra.register(registry, PlatformAuthenticationPgIT.class); }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired PlatformBootstrapInitializer bootstrap;

    @BeforeEach
    void initializePlatform() {
        jdbc.update("DELETE FROM platform_audit_log");
        jdbc.update("DELETE FROM platform_account");
        bootstrap.initialize("root-operator", "Root-pass123");
    }

    @Test
    void shouldAuthenticateOnlyPlatformTokensOnPlatformEndpoints() throws Exception {
        JsonNode login = login("root-operator", "Root-pass123");
        String platformToken = login.path("accessToken").asText();
        long id = login.path("account").path("id").asLong();
        String tenantToken = StpUtil.getStpLogic().createLoginSession(id);
        assertThat(call("/api/access/platform-auth/me", platformToken, Map.of()).getResponse().getStatus()).isEqualTo(200);
        assertThat(call("/api/access/platform-auth/me", tenantToken, Map.of()).getResponse().getStatus()).isEqualTo(401);
        assertThat(call("/api/access/auth/userinfo", platformToken, Map.of()).getResponse().getStatus()).isEqualTo(401);
        assertThat(call("/api/access/platform-account/page", null, Map.of()).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void shouldForceNewCredentialsAndRevokeOldTokensOnRecovery() throws Exception {
        String root = login("root-operator", "Root-pass123").path("accessToken").asText();
        JsonNode created = success(call("/api/access/platform-account/create", root,
            Map.of("username", "operator-new", "name", "New operator")));
        long id = created.path("account").path("id").asLong();
        String initial = created.path("initialPassword").asText();
        JsonNode initialLogin = login("operator-new", initial);
        assertThat(initialLogin.path("account").path("forceResetPwd").asBoolean()).isTrue();
        String first = initialLogin.path("accessToken").asText();
        assertThat(call("/api/access/platform-account/page", first, Map.of()).getResponse().getStatus()).isEqualTo(403);
        JsonNode rejected = body(call("/api/access/platform-auth/change-password", first,
            Map.of("oldPassword", initial, "newPassword", initial)));
        assertThat(rejected.path("code").asInt()).isEqualTo(10011);
        success(call("/api/access/platform-auth/change-password", first,
            Map.of("oldPassword", initial, "newPassword", "Changed-pass123")));
        assertThat(call("/api/access/platform-auth/me", first, Map.of()).getResponse().getStatus()).isEqualTo(401);
        String current = login("operator-new", "Changed-pass123").path("accessToken").asText();
        success(call("/api/access/platform-account/page", current, Map.of()));
        redis.opsForValue().set(LoginFailureStore.platformKey("operator-new"), "5");
        String resetPassword = success(call("/api/access/platform-account/reset-password", root, Map.of("id", id)))
            .path("password").asText();
        assertThat(redis.hasKey(LoginFailureStore.platformKey("operator-new"))).isFalse();
        assertThat(call("/api/access/platform-auth/me", current, Map.of()).getResponse().getStatus()).isEqualTo(401);
        assertThat(login("operator-new", resetPassword).path("account").path("forceResetPwd").asBoolean()).isTrue();
    }

    private JsonNode login(String username, String password) throws Exception {
        JsonNode captcha = success(call("/api/access/platform-auth/captcha", null, Map.of()));
        String captchaId = captcha.path("captchaId").asText();
        String code = redis.opsForValue().get("captcha:" + captchaId);
        return success(call("/api/access/platform-auth/login", null,
            Map.of("username", username, "password", password, "captchaId", captchaId, "captchaCode", code)));
    }
    private MvcResult call(String path, String token, Object value) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(value));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mvc.perform(request).andReturn();
    }
    private JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsByteArray()); }
    private JsonNode success(MvcResult result) throws Exception {
        JsonNode body = body(result);
        assertThat(result.getResponse().getStatus()).as(body.toString()).isEqualTo(200);
        assertThat(body.path("code").asInt()).as(body.toString()).isEqualTo(200);
        return body.path("data");
    }
}
