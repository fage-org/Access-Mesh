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
    "accessmesh.sync.scheduler.enabled=false",
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
    private UserMenuQueryService userMenuQueryService;
    @MockBean
    private StringRedisTemplate stringRedisTemplate;

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
        when(userDomainService.findByUsername(1L, "alice")).thenReturn(user);
        when(userDomainService.selectValidById(anyLong(), anyLong())).thenReturn(user);
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList())).thenReturn("8888");
        // 登录链路 checkAccountLocked/recordLoginFail 走 opsForValue（increment/get/set），mock 掉
        org.springframework.data.redis.core.ValueOperations<String, String> valueOperations =
            org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class);
        org.mockito.Mockito.lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        org.mockito.Mockito.lenient().when(valueOperations.increment(anyString(), anyLong())).thenReturn(0L);

        MvcResult result = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(java.util.Map.of(
                    "tenantId", "1", "username", "alice", "password", PASSWORD,
                    "captchaId", "cap-1", "captchaCode", "8888", "clientId", "console"))))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(body.get("code").asInt()).isEqualTo(200);
        return body.get("data").get("accessToken").asText();
    }

    private int userinfoStatus(String token) throws Exception {
        return mockMvc.perform(post("/auth/userinfo")
                .header("Authorization", "Bearer " + token))
            .andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("无操作失效：活跃期 200，静置超过 active-timeout=2（含整秒除法余量）后 401")
    void idleTimeout_invalidatesAfterInactivity() throws Exception {
        String token = login();
        assertThat(userinfoStatus(token)).as("活跃期内应 200").isEqualTo(200);

        // sa-token 1.38 剩余时间 = activeTimeout - 整秒除法(idleMs/1000)，且 -1 是
        // 「未启用检查」哨兵：剩余 <= -2 才判冻结。active=2s 时需静置 >= 4s 稳定触发
        Thread.sleep(4500);

        assertThat(userinfoStatus(token))
            .as("静置 4.5s（>active-timeout=2 且 <timeout=30）：401 只能来自无操作失效")
            .isEqualTo(401);
    }

    @Test
    @DisplayName("滑动续命：1.2s 间隔连续活跃 3 次（单窗口 2s 内不空闲）始终 200")
    // 覆盖生产语义：30 分钟窗口内持续操作的用户不会被无操作超时踢出
    void continuousActivity_staysAlive() throws Exception {
        String token = login();
        for (int i = 1; i <= 3; i++) {
            Thread.sleep(1200);
            assertThat(userinfoStatus(token))
                .as("第 %d 次活跃请求（间隔 1.2s < active-timeout 2s）应续命 200", i)
                .isEqualTo(200);
        }
    }
}
