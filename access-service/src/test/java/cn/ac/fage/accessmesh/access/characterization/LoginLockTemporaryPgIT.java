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
 * 登录临时锁定语义特征测试（T-ADMIN-022，真实 PostgreSQL + Redis）。
 * <p>
 * 固化「计数键即锁」终态：连续失败达阈值（5）后仅凭 Redis 计数键拒绝登录，
 * sys_user.status 全程保持 0/1 不被锁定改写；键过期（DEL 模拟 TTL 到期）自动
 * 恢复可登录；锁定拒绝补记 sys_login_log；/user/update 的 status 仅接纳 0/1。
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
class LoginLockTemporaryPgIT {

    private static final Long TENANT = 1L;
    private static final String PASSWORD = "Pass@123";
    /** 每用例独立用户名（UUID），避免 Redis 失败计数与租户内唯一约束跨用例冲突 */
    private static final java.util.concurrent.atomic.AtomicLong USER_SEQ = new java.util.concurrent.atomic.AtomicLong();

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        ItInfra.register(registry, LoginLockTemporaryPgIT.class);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    /** 真实验证码 + 单次登录请求，返回信封 body。 */
    private JsonNode login(String username, String password) throws Exception {
        String captchaId = UUID.randomUUID().toString();
        String captchaCode = "5926";
        stringRedisTemplate.opsForValue().set("captcha:" + captchaId, captchaCode, 5, TimeUnit.MINUTES);
        MvcResult result = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "tenantId", "1", "username", username, "password", password,
                    "captchaId", captchaId, "captchaCode", captchaCode, "clientId", "console"))))
            .andExpect(status().isOk())
            .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** 每用例独立用户（独立 id + 独立 username），返回 userId。 */
    private long insertUser(String name) {
        long id = 920600L + USER_SEQ.incrementAndGet();
        String username = "pglock_" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update(
            "INSERT INTO sys_user (id, tenant_id, username, password, name, status, user_type, force_reset_pwd) "
                + "VALUES (?, ?, ?, ?, ?, 1, 3, false)",
            id, TENANT, username, cn.dev33.satoken.secure.BCrypt.hashpw(PASSWORD), name);
        return id;
    }

    private String usernameOf(long userId) {
        return jdbc.queryForObject(
            "SELECT username FROM sys_user WHERE id = ? AND tenant_id = ?", String.class, userId, TENANT);
    }

    private Integer dbStatus(long userId) {
        return jdbc.queryForObject(
            "SELECT status FROM sys_user WHERE id = ? AND tenant_id = ?", Integer.class, userId, TENANT);
    }

    @Test
    @DisplayName("失败 5 次锁拒（10004）不落库；键过期自动恢复；锁定补记登录日志")
    void lockIsTemporaryCountKeyOnlyAndRecoversAfterExpiry() throws Exception {
        long userId = insertUser("特征测试-临时锁定");
        String username = usernameOf(userId);
        String lockKey = "login:fail:1:" + username;

        // 连续 5 次错误密码：计数键 1→5，sys_user.status 不被任何一次失败改写
        for (int i = 1; i <= 5; i++) {
            JsonNode fail = login(username, "wrong-" + i);
            assertThat(fail.get("code").asInt()).isEqualTo(10005);
            assertThat(dbStatus(userId)).as("第 %d 次失败后 status 仍为 1（锁定不落库）", i).isEqualTo(1);
        }
        assertThat(stringRedisTemplate.opsForValue().get(lockKey)).isEqualTo("5");
        // 计数键必须带 TTL（Lua INCR+EXPIRE）：剩余 TTL 即剩余锁定时长，永不过期键会使锁定变永久
        assertThat(stringRedisTemplate.getExpire(lockKey, TimeUnit.SECONDS))
            .as("失败计数键应带正 TTL").isPositive();

        // 第 6 次正确密码：凭计数键拒绝（10004），仍不落库，且补记锁定失败日志
        JsonNode locked = login(username, PASSWORD);
        assertThat(locked.get("code").asInt()).isEqualTo(10004);
        assertThat(locked.get("message").asText()).contains("30分钟").contains("临时锁定");
        assertThat(dbStatus(userId)).isEqualTo(1);
        Integer lockLogs = jdbc.queryForObject(
            "SELECT COUNT(*) FROM sys_login_log WHERE tenant_id = ? AND username = ? "
                + "AND status = 0 AND fail_reason LIKE '%临时锁定%'",
            Integer.class, TENANT, username);
        assertThat(lockLogs).as("锁定拒绝补记 sys_login_log").isEqualTo(1);

        // 键过期（DEL 模拟 TTL 到期）→ 正确密码自动恢复可登录，成功后清除计数键
        stringRedisTemplate.delete(lockKey);
        JsonNode recovered = login(username, PASSWORD);
        assertThat(recovered.get("code").asInt()).isEqualTo(200);
        assertThat(stringRedisTemplate.opsForValue().get(lockKey)).isNull();
        assertThat(dbStatus(userId)).isEqualTo(1);
    }

    @Test
    @DisplayName("/user/update status 仅接纳 0/1：status=2 拒绝（10008）且库值不变")
    void userUpdateRejectsStatusOutsideZeroAndOne() throws Exception {
        long userId = insertUser("特征测试-status收口");
        String username = usernameOf(userId);

        JsonNode loginBody = login(username, PASSWORD);
        assertThat(loginBody.get("code").asInt()).isEqualTo(200);
        String token = loginBody.get("data").get("accessToken").asText();

        // 自我修改豁免门禁（operatorId == id），直接命中 status 校验
        MvcResult rejected = mockMvc.perform(post("/user/update")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("id", userId, "status", 2))))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode rejectBody = mapper.readTree(
            rejected.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(rejectBody.get("code").asInt()).isEqualTo(10008);
        assertThat(rejectBody.get("message").asText()).contains("0(停用)或1(启用)");
        assertThat(dbStatus(userId)).as("status=2 被拒后库值不变").isEqualTo(1);

        // 合法值 1 幂等写回成功（证明拒绝只针对非法值而非接口本身）
        MvcResult accepted = mockMvc.perform(post("/user/update")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("id", userId, "status", 1))))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode acceptBody = mapper.readTree(
            accepted.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(acceptBody.get("code").asInt()).isEqualTo(200);
        assertThat(dbStatus(userId)).isEqualTo(1);
    }
}
