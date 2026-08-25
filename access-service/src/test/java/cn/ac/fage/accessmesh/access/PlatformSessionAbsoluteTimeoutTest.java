package cn.ac.fage.accessmesh.access;

import cn.ac.fage.accessmesh.access.admin.entity.SysUser;
import cn.ac.fage.accessmesh.access.admin.service.domain.LoginLogDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.OAuth2ClientDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.access.application.query.UserMenuQueryService;
import cn.dev33.satoken.dao.SaTokenDao;
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
 * 平台用户会话生命周期测试——登录签发、身份绑定、绝对失效、注销（T-ACCESS-011 验收 8）。
 * <p>
 * 用户决策（2026-08-22）：运行时失效语义用缩短配置验证（真实等待 30 分钟/2 小时不可行），
 * 生产权威值 7200/1800 由 AccessServiceApplicationTest / GatewayApplicationConfigTest
 * 的配置级断言钉住。本类 sa-token.timeout=4（绝对）、active-timeout=10（放宽），
 * 隔离验证「绝对失效」语义：持续活跃也不能续命超过 timeout。
 * 无操作 30 分钟失效语义由 {@link PlatformSessionIdleTimeoutTest} 以
 * active-timeout=2 放宽 timeout 的对称配置验证。
 * </p>
 * <p>
 * 会话存储使用内存 SaTokenDao（@Primary 替换 redis-jackson dao）——
 * 令牌校验/会话读取键语义与共享 Redis 一致（同一 token-name/loginType），
 * Gateway 侧对等校验见 gateway AuthTokenFilterTest（双端结果一致性的组合证据）。
 * </p>
 */
@SpringBootTest(
    classes = {AccessServiceApplication.class, AccessServiceApplicationTest.TestDataSourceConfig.class,
        PlatformSessionAbsoluteTimeoutTest.InMemorySessionDaoConfig.class},
    webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    "accessmesh.sync.scheduler.enabled=false",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,org.redisson.spring.starter.RedissonAutoConfigurationV2,com.alibaba.cloud.nacos.NacosConfigAutoConfiguration,com.alibaba.cloud.nacos.NacosDiscoveryAutoConfiguration,com.alibaba.cloud.nacos.discovery.NacosDiscoveryClientConfiguration",
    "mybatis-flex.configuration.map-underscore-to-camel-case=true",
    "logging.level.cn.ac.fage.accessmesh=WARN",
    "JWT_SECRET_KEY=test-jwt-secret-for-session-lifecycle",
    "ACCESSMESH_SIGNATURE_SECRET=test-signature-secret-for-session-lifecycle",
    "PERM_INTERNAL_SECRET=test-internal-secret-for-session-lifecycle",
    // 缩短配置（用户决策）：4 秒绝对失效；active-timeout 放宽到 10s 隔离绝对语义
    "sa-token.timeout=4",
    "sa-token.active-timeout=10"
})
class PlatformSessionAbsoluteTimeoutTest {

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
    private UserMenuQueryService userMenuQueryService;
    /** 验证码/失败计数 Redis：mock（验证码脚本返回固定值）。 */
    @MockBean
    private StringRedisTemplate stringRedisTemplate;


    @TestConfiguration
    static class InMemorySessionDaoConfig {
        /** @Primary：SaBeanInject 按类型注入 dao，内存实现优先于 redis-jackson（mock 连接无真实存储）。 */
        @Bean
        @Primary
        SaTokenDao saTokenDao() {
            return new SaTokenDaoDefaultImpl();
        }
    }

    private SysUser mockUser() {
        SysUser user = new SysUser();
        user.setId(9L);
        user.setTenantId(1L);
        user.setUsername("alice");
        user.setPassword(cn.dev33.satoken.secure.BCrypt.hashpw(PASSWORD));
        user.setStatus(1);
        user.setForceResetPwd(false);
        return user;
    }

    /** 走真实 /auth/login 签发链路（验证码脚本 mock、领域服务 mock、真实会话写入）。 */
    private String login() throws Exception {
        SysUser user = mockUser();
        when(userDomainService.findByUsername(1L, "alice")).thenReturn(user);
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList())).thenReturn("8888");
        // 登录链路 isAccountLocked/clearLoginFail 走 opsForValue.get / delete，mock 掉（get 默认 null=未锁定）
        org.springframework.data.redis.core.ValueOperations<String, String> valueOperations =
            org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class);
        org.mockito.Mockito.lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        org.mockito.Mockito.lenient().when(valueOperations.get(anyString())).thenReturn(null);

        MvcResult result = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(java.util.Map.of(
                    "tenantId", "1", "username", "alice", "password", PASSWORD,
                    "captchaId", "cap-1", "captchaCode", "8888", "clientId", "console"))))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(body.get("code").asInt()).isEqualTo(200);
        // expiresIn 单一权威来源 = sa-token.timeout（测试上下文缩短为 4）
        assertThat(body.get("data").get("expiresIn").asLong())
            .as("LoginResp.expiresIn 必须等于 sa-token.timeout（当前测试配置 4s）")
            .isEqualTo(4);
        return body.get("data").get("accessToken").asText();
    }

    private int userinfoStatus(String token) throws Exception {
        return mockMvc.perform(post("/auth/userinfo")
                .header("Authorization", "Bearer " + token))
            .andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("登录签发：/auth/login 200，accessToken 非空且 expiresIn=缩短后的 timeout（单一来源）")
    void loginIssuesToken_expiresInFollowsSaTokenTimeout() throws Exception {
        String token = login();
        assertThat(token).isNotBlank();

        // 立即身份绑定：真实会话经拦截器绑定（非 mock AuthService）
        SysUser user = mockUser();
        when(userDomainService.selectValidById(anyLong(), anyLong())).thenReturn(user);

        int status = userinfoStatus(token);
        assertThat(status).as("刚签发的令牌 userinfo 必须 200（绑定成功），实际 %s", status).isEqualTo(200);
    }

    @Test
    @DisplayName("绝对失效：持续活跃（<active-timeout 间隔续命）也不能超过 timeout=4s，之后 401")
    void absoluteTimeout_invalidatesDespiteContinuousActivity() throws Exception {
        String token = login();
        SysUser user = mockUser();
        when(userDomainService.selectValidById(anyLong(), anyLong())).thenReturn(user);

        long start = System.nanoTime();
        assertThat(userinfoStatus(token)).as("t≈0s 活跃请求应 200").isEqualTo(200);
        Thread.sleep(1200);
        assertThat(userinfoStatus(token)).as("t≈1.2s 活跃请求应 200（活动续命，未到绝对上限）").isEqualTo(200);
        Thread.sleep(1200);
        assertThat(userinfoStatus(token)).as("t≈2.4s 活跃请求应 200（活动续命，未到绝对上限）").isEqualTo(200);
        Thread.sleep(1200);
        assertThat(userinfoStatus(token)).as("t≈3.6s 活跃请求应 200（仍在 4s 绝对窗口内）").isEqualTo(200);

        // 跨过 4s 绝对上限：距上次活跃仅 ~0.8s（远小于 active-timeout=10），仍必须 401
        Thread.sleep(1200);
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        assertThat(elapsed).as("测试时序保障：已跨过 4s 绝对窗口").isGreaterThan(4000);
        assertThat(userinfoStatus(token))
            .as("绝对失效：距上次活跃仅 ~0.8s（active-timeout=10 未到），401 只能来自 timeout=4")
            .isEqualTo(401);
    }

    @Test
    @DisplayName("注销：/auth/logout 后同一令牌立即 401")
    void logout_invalidatesTokenImmediately() throws Exception {
        String token = login();
        SysUser user = mockUser();
        when(userDomainService.selectValidById(anyLong(), anyLong())).thenReturn(user);
        assertThat(userinfoStatus(token)).isEqualTo(200);

        mockMvc.perform(post("/auth/logout")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        assertThat(userinfoStatus(token)).as("注销后同一令牌必须立即 401").isEqualTo(401);
    }
}
