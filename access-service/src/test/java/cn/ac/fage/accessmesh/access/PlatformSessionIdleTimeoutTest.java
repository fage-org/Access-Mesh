package cn.ac.fage.accessmesh.access;

import cn.ac.fage.accessmesh.access.user.entity.SysUser;
import cn.ac.fage.accessmesh.access.audit.service.domain.LoginLogDomainService;
import cn.ac.fage.accessmesh.access.auth.service.domain.OAuth2ClientDomainService;
import cn.ac.fage.accessmesh.access.user.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.access.org.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.access.menu.service.UserMenuQueryAppService;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.dao.SaTokenDao;
import java.time.Duration;
import static org.awaitility.Awaitility.await;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 平台用户会话「无操作失效」语义测试（T-ACCESS-011 验收 8，与
 * {@link PlatformSessionAbsoluteTimeoutTest} 成对）。
 * <p>
 * 对称缩短配置：active-timeout=2（30 分钟无操作失效的生产语义）、timeout=30 放宽。
 * 断言：活跃期内请求正常；静置超过 active-timeout 后 401——此时令牌绝对 TTL（30s）
 * 尚未到期，失效只能来自无操作超时。持续活跃续命语义（滑动窗口）一并验证：
 * 每次请求刷新 last-active，连续活跃跨过单个 active-timeout 窗口仍 200。
 * </p>
 */
@SpringBootTest(
    classes = {AccessServiceApplication.class, AccessServiceApplicationTest.TestDataSourceConfig.class,
        PlatformSessionIdleTimeoutTest.InMemorySessionDaoConfig.class},
    webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    "accessmesh.sync.scheduler.enabled=false", "access.tenant.gate-repair.enabled=false",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,org.redisson.spring.starter.RedissonAutoConfigurationV2,com.alibaba.cloud.nacos.NacosConfigAutoConfiguration,com.alibaba.cloud.nacos.NacosDiscoveryAutoConfiguration,com.alibaba.cloud.nacos.discovery.NacosDiscoveryClientConfiguration",
    "mybatis-flex.configuration.map-underscore-to-camel-case=true",
    "logging.level.cn.ac.fage.accessmesh=WARN",
    "JWT_SECRET_KEY=test-jwt-secret-for-idle-timeout",
    "ACCESSMESH_SIGNATURE_SECRET=test-signature-secret-for-idle-timeout",
    "PERM_INTERNAL_SECRET=test-internal-secret-for-idle-timeout",
    // 对称缩短：2 秒无操作失效；timeout 放宽 30s 隔离 active 语义
    "sa-token.timeout=30",
    "sa-token.active-timeout=2"
})
class PlatformSessionIdleTimeoutTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PASSWORD = "Pass@123";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserDomainService userDomainService;
    @MockBean
    private UserOrgDomainService userOrgDomainService;
    @MockBean
    private OAuth2ClientDomainService oauth2ClientDomainService;
    @MockBean
    private LoginLogDomainService loginLogDomainService;
    @MockBean
    private UserMenuQueryAppService userMenuQueryService;
    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private cn.ac.fage.accessmesh.access.tenant.service.domain.TenantDomainService tenants;
    @MockBean
    private cn.ac.fage.accessmesh.access.tenant.service.TenantAccessGuard tenantAccess;
    @org.junit.jupiter.api.BeforeEach
    void activeTenantFixture() {
        org.mockito.Mockito.when(tenantAccess.captureLoginProcess(org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.anyLong()))
            .thenReturn("a".repeat(40));
        org.mockito.Mockito.when(tenants.findByCode(cn.ac.fage.accessmesh.access.it.TenantTestSupport.CODE))
            .thenReturn(cn.ac.fage.accessmesh.access.it.TenantTestSupport.activeTenant());
    }

    @TestConfiguration
    static class InMemorySessionDaoConfig {
        @Bean
        @Primary
        SaTokenDao saTokenDao() {
            return new SaTokenDaoDefaultImpl();
        }
    }

    private String login() throws Exception {
        SysUser user = new SysUser();
        user.setId(9L);
        user.setTenantId(1L);
        user.setUsername("alice");
        user.setPassword(cn.dev33.satoken.secure.BCrypt.hashpw(PASSWORD));
        user.setStatus(1);
        user.setForceResetPwd(false);
        when(userDomainService.lockValidByUsername(1L, "alice")).thenReturn(user);
        when(userDomainService.selectValidById(anyLong(), anyLong())).thenReturn(user);
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList())).thenReturn("8888");
        // 登录链路锁定判定/清除走 LoginFailureStore（opsForValue.get / delete），mock 掉（get 默认 null=未锁定）
        org.springframework.data.redis.core.ValueOperations<String, String> valueOperations =
            org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class);
        org.mockito.Mockito.lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        org.mockito.Mockito.lenient().when(valueOperations.get(anyString())).thenReturn(null);

        MvcResult result = mockMvc.perform(post("/api/access/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(java.util.Map.of(
                    "tenantCode", cn.ac.fage.accessmesh.access.it.TenantTestSupport.CODE, "username", "alice", "password", PASSWORD,
                    "captchaId", "cap-1", "captchaCode", "8888", "clientId", "console"))))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(body.get("code").asInt()).isEqualTo(200);
        return body.get("data").get("accessToken").asText();
    }

    private int userinfoStatus(String token) throws Exception {
        return mockMvc.perform(post("/api/access/auth/userinfo")
                                .header("Authorization", "Bearer " + token))
            .andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("无操作失效：只读轮询至冻结，绝对 TTL 仍有效时请求 401")
    void idleTimeout_invalidatesAfterInactivity() throws Exception {
        String token = login();
        assertThat(userinfoStatus(token)).isEqualTo(200);
        // 只读剩余活动时间，不通过 HTTP 轮询续写 last-active。
        await().pollInSameThread().pollInterval(Duration.ofMillis(100)).atMost(Duration.ofSeconds(10))
            .until(() -> StpUtil.stpLogic.getTokenActiveTimeoutByToken(token) == SaTokenDao.NOT_VALUE_EXPIRE);
        assertThat(StpUtil.getTokenTimeout(token)).as("绝对 TTL 尚未到期").isPositive();
        assertThat(userinfoStatus(token)).isEqualTo(401);
    }

    @Test
    @DisplayName("持续请求真实续写 last-active，并跨过原始闲置失效窗口")
    void continuousActivity_staysAlive() throws Exception {
        String token = login();
        long firstActive = StpUtil.stpLogic.getTokenLastActiveTime(token);
        long idleWindowMillis = (StpUtil.stpLogic.getTokenUseActiveTimeoutOrGlobalConfig(token) + 1) * 1000;
        await().pollInSameThread().pollInterval(Duration.ofMillis(100)).atMost(Duration.ofSeconds(10))
            .until(() -> {
                long requestStarted = System.currentTimeMillis();
                assertThat(userinfoStatus(token)).isEqualTo(200);
                long refreshed = StpUtil.stpLogic.getTokenLastActiveTime(token);
                assertThat(refreshed).as("本次请求确实续写活动时间").isGreaterThanOrEqualTo(requestStarted);
                return refreshed - firstActive >= idleWindowMillis;
            });
    }
}
