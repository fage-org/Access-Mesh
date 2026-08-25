package cn.ac.fage.accessmesh.access;

import cn.ac.fage.accessmesh.access.admin.entity.SysUser;
import cn.ac.fage.accessmesh.access.admin.service.domain.LoginLogDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.LoginLogDomainService.LoginLogEntry;
import cn.ac.fage.accessmesh.access.admin.service.domain.OAuth2ClientDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.UserDomainService;
import cn.ac.fage.accessmesh.access.admin.service.domain.UserOrgDomainService;
import cn.ac.fage.accessmesh.access.application.query.UserMenuQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登录临时锁定语义单测（T-ADMIN-022：计数键即锁，不落库）。
 * <p>
 * 固化四个不变量（内存 SaTokenDao + mock 领域服务，同
 * {@link PlatformSessionIdleTimeoutTest} 模式；真实 PG/Redis 两侧由
 * LoginLockTemporaryPgIT 覆盖）：
 * <ol>
 *   <li>计数达阈值（≥5）→ 拒绝登录（10004），不写 sys_user.status（锁定不再落库），
 *       且补记登录失败日志（failReason=临时锁定）</li>
 *   <li>计数键过期（GET null）→ 可正常登录，登录成功清除计数键</li>
 *   <li>锁定检查只读（GET）：任何路径都不经 increment(key, 0) 产生无 TTL 零值键</li>
 *   <li>阈值未达（计数&lt;5）→ 不拦截，正常走密码校验</li>
 * </ol>
 */
@SpringBootTest(
    classes = {AccessServiceApplication.class, AccessServiceApplicationTest.TestDataSourceConfig.class,
        AuthLoginLockTest.InMemorySessionDaoConfig.class},
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
    "JWT_SECRET_KEY=test-jwt-secret-for-login-lock",
    "ACCESSMESH_SIGNATURE_SECRET=test-signature-secret-for-login-lock",
    "PERM_INTERNAL_SECRET=test-internal-secret-for-login-lock",
    "sa-token.timeout=300",
    "sa-token.active-timeout=300"
})
class AuthLoginLockTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PASSWORD = "Pass@123";
    private static final String USERNAME = "bob";
    private static final String LOCK_KEY = "login:fail:1:" + USERNAME;

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

    private SysUser enabledUser() {
        SysUser user = new SysUser();
        user.setId(7L);
        user.setTenantId(1L);
        user.setUsername(USERNAME);
        user.setPassword(cn.dev33.satoken.secure.BCrypt.hashpw(PASSWORD));
        user.setStatus(1);
        user.setForceResetPwd(false);
        return user;
    }

    /** captcha Lua（2 参 execute）返回 "8888"；锁定检查走 opsForValue.get。 */
    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> stubRedis() {
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList())).thenReturn("8888");
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        return valueOperations;
    }

    private JsonNode login(String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MAPPER.writeValueAsString(java.util.Map.of(
                    "tenantId", "1", "username", USERNAME, "password", password,
                    "captchaId", "cap-1", "captchaCode", "8888", "clientId", "console"))))
            .andExpect(status().isOk())
            .andReturn();
        return MAPPER.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("计数达阈值：正确密码也拒绝（10004），status 不被修改，补记临时锁定失败日志")
    void lockedThresholdRejectsWithoutPersisting() throws Exception {
        SysUser user = enabledUser();
        when(userDomainService.findByUsername(1L, USERNAME)).thenReturn(user);
        ValueOperations<String, String> valueOperations = stubRedis();
        when(valueOperations.get(LOCK_KEY)).thenReturn("5");

        JsonNode body = login(PASSWORD);

        assertThat(body.get("code").asInt()).isEqualTo(10004);
        assertThat(body.get("message").asText()).contains("30分钟").contains("临时锁定");
        // 锁定不再落库：sys_user.status 不被任何路径修改
        verify(userDomainService, never()).update(any(SysUser.class));
        verify(userDomainService, never()).batchUpdateStatus(anyLong(), anyList(), anyInt());
        // 拒绝发生前置，失败计数 Lua（3 参 execute）不应再累加
        verify(stringRedisTemplate, never()).execute(any(DefaultRedisScript.class), anyList(), any());
        // 补记登录失败日志：failReason 表达临时锁定，userId 回填真实用户
        ArgumentCaptor<LoginLogEntry> captor = ArgumentCaptor.forClass(LoginLogEntry.class);
        verify(loginLogDomainService).recordLoginLog(captor.capture());
        assertThat(captor.getValue().failReason()).contains("临时锁定");
        assertThat(captor.getValue().status()).isEqualTo(0);
        assertThat(captor.getValue().userId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("计数键过期（GET null）：锁定自动解除，正确密码可登录且清除计数键")
    void lockExpiryAllowsLoginAndClearsKey() throws Exception {
        SysUser user = enabledUser();
        when(userDomainService.findByUsername(1L, USERNAME)).thenReturn(user);
        ValueOperations<String, String> valueOperations = stubRedis();
        when(valueOperations.get(LOCK_KEY)).thenReturn(null);

        JsonNode body = login(PASSWORD);

        assertThat(body.get("code").asInt()).isEqualTo(200);
        verify(stringRedisTemplate).delete(LOCK_KEY);
    }

    @Test
    @DisplayName("锁定检查只读：不存在用户登录不产生无 TTL 零值键（increment(key,0) 回归保护）")
    void unknownUserNeverCreatesZeroValueKey() throws Exception {
        when(userDomainService.findByUsername(1L, USERNAME)).thenReturn(null);
        ValueOperations<String, String> valueOperations = stubRedis();
        when(valueOperations.get(LOCK_KEY)).thenReturn(null);

        JsonNode body = login("whatever");

        assertThat(body.get("code").asInt()).isEqualTo(10001);
        // GET 只读不建键；失败计数经 Lua INCR+EXPIRE（3 参 execute）恰好一次
        verify(valueOperations, never()).increment(anyString(), anyLong());
        verify(stringRedisTemplate, times(1)).execute(any(DefaultRedisScript.class), anyList(), any());
    }

    @Test
    @DisplayName("阈值未达（计数<5）：不拦截，正常走密码校验")
    void belowThresholdDoesNotBlock() throws Exception {
        SysUser user = enabledUser();
        when(userDomainService.findByUsername(1L, USERNAME)).thenReturn(user);
        ValueOperations<String, String> valueOperations = stubRedis();
        when(valueOperations.get(LOCK_KEY)).thenReturn("4");

        JsonNode wrongPassword = login("wrong-password");
        assertThat(wrongPassword.get("code").asInt()).isEqualTo(10005);

        JsonNode correct = login(PASSWORD);
        assertThat(correct.get("code").asInt()).isEqualTo(200);
    }
}
