package cn.ac.fage.accessmesh.access.characterization;

import cn.ac.fage.accessmesh.access.it.ItInfra;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登录与会话建立特征测试（T-ACCESS-017·链路 1，真实 PostgreSQL + Redis）。
 * <p>
 * 固化 {@code POST /auth/login} 的当前正确行为：真实 sys_user 表校验（BCrypt）、
 * 真实 Redis 验证码（Lua GET+DEL）与失败计数、Sa-Token 会话建立（redis-jackson dao）、
 * token 颁发（expiresIn = sa-token.timeout 单一权威来源）、userinfo 会话消费、logout 失效。
 * 单测层等价特征断言已由 PlatformSessionIdleTimeoutTest / PlatformSessionAbsoluteTimeoutTest
 * 覆盖（内存 SaTokenDao + mock 领域服务），本类补真实持久层两侧。
 * </p>
 * <p>
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
})
class LoginSessionPgIT {

    private static final Long TENANT = 1L;
    private static final String PASSWORD = "Pass@123";
    /** 每次运行独立用户名与验证码，避免 Redis 失败计数跨运行累积 */
    private static final String USERNAME = "pgit_" + UUID.randomUUID().toString().substring(0, 8);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, LoginSessionPgIT.class);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("登录：真实库用户 + 真实 Redis 验证码 → 会话建立 → userinfo 消费 → logout 失效")
    void loginShouldEstablishSessionAgainstRealDatabaseAndRedis() throws Exception {
        // 真实 sys_user 行（BCrypt 密码，登录链路走 UserDomainService 真实 SQL）
        // T-ORG-001：sys_user.id 已去自增（显式主体 ID），fixture 显式给 id
        jdbc.update(
            "INSERT INTO sys_user (id, tenant_id, username, password, name, status, user_type, force_reset_pwd) "
                + "VALUES (?, ?, ?, ?, '特征测试-登录用户', 1, 3, false)",
            910501L, TENANT, USERNAME, cn.dev33.satoken.secure.BCrypt.hashpw(PASSWORD));

        // 真实 Redis 验证码（validateCaptcha 的 Lua GET+DEL 一次性消费）
        String captchaId = UUID.randomUUID().toString();
        String captchaCode = "3141";
        stringRedisTemplate.opsForValue().set("captcha:" + captchaId, captchaCode, 5, TimeUnit.MINUTES);

        // 登录成功：code=200 + accessToken + expiresIn = sa-token.timeout（单一权威来源）
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "tenantId", "1", "username", USERNAME, "password", PASSWORD,
                    "captchaId", captchaId, "captchaCode", captchaCode, "clientId", "console"))))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode loginBody = mapper.readTree(
            loginResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(loginBody.get("code").asInt()).isEqualTo(200);
        String token = loginBody.get("data").get("accessToken").asText();
        assertThat(token).isNotBlank();
        assertThat(loginBody.get("data").get("expiresIn").asLong())
            .isEqualTo(cn.dev33.satoken.SaManager.getConfig().getTimeout());

        // 验证码一次性消费：同 captchaId 二次登录必失败（Lua 已 DEL）
        MvcResult replay = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "tenantId", "1", "username", USERNAME, "password", PASSWORD,
                    "captchaId", captchaId, "captchaCode", captchaCode, "clientId", "console"))))
            .andReturn();
        JsonNode replayBody = mapper.readTree(
            replay.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(replayBody.get("code").asInt()).isNotEqualTo(200);

        // 会话消费：userinfo 200（RequestContextInterceptor 经 Sa-Token 会话绑定身份）
        mockMvc.perform(post("/auth/userinfo")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        // 注销后立即失效：userinfo 401
        mockMvc.perform(post("/auth/logout")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        mockMvc.perform(post("/auth/userinfo")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().is(401));
    }
}
