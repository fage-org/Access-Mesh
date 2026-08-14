package cn.ac.fage.accessmesh.gateway;

import cn.ac.fage.accessmesh.gateway.cache.PermInvalidationSubscriber;
import cn.ac.fage.accessmesh.gateway.config.GatewayProperties;
import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.TestPropertySource;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gateway 配置加载上下文测试（T-ACCESS-003 评审 P1 修复，2026-08-14）。
 * <p>
 * 背景：Boot 3 / Spring Cloud 2023 默认不加载 bootstrap.yml（无 spring-cloud-starter-bootstrap、
 * 无 bootstrap.enabled=true），原 gateway 配置（sa-token/Redis/路由/Nacos）实际全部失效——
 * Gateway 用默认 token-name=satoken 读不到 access-service 写入的 Authorization:login:* 会话。
 * 本任务将 bootstrap.yml 迁移为 application.yml（Boot 3 标准 ConfigData + spring.config.import）。
 * 本测试证明：迁移后 sa-token 权威值、Redis DB 0、路由定义与 gateway.* 自定义配置确实加载。
 * </p>
 */
@SpringBootTest(
    classes = {GatewayApplication.class, GatewayApplicationConfigTest.TestInfraConfig.class},
    // MOCK：保留 reactive Web 上下文（WebFlux），ServerProperties 等 Web 自动配置生效
    // （Spring Cloud Gateway 的 NettyConfiguration 依赖 ServerProperties；NONE 下不加载导致启动失败）
    webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@TestPropertySource(properties = {
    // Nacos ConfigData 隔离：覆盖 application.yml 的 nacos import，测试不连接 Nacos
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    // 排除 Redis/Nacos 自动配置（基础设施由 TestInfraConfig 提供）
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration,com.alibaba.cloud.nacos.NacosConfigAutoConfiguration,com.alibaba.cloud.nacos.NacosDiscoveryAutoConfiguration,com.alibaba.cloud.nacos.discovery.NacosDiscoveryClientConfiguration",
    "logging.level.cn.ac.fage.accessmesh=WARN",
    "ACCESSMESH_SIGNATURE_SECRET=test-signature-secret-for-gateway-test",
    "PERM_INTERNAL_SECRET=test-internal-secret-for-gateway-test"
})
class GatewayApplicationConfigTest {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * Mock 权限失效订阅器：SmartLifecycle start() 在上下文刷新时执行 Redis pub/sub 订阅，
     * 测试环境不连真实 Redis，替换为 mock 避免订阅逻辑在测试上下文执行。
     */
    @MockBean
    private PermInvalidationSubscriber permInvalidationSubscriber;

    /**
     * 测试基础设施：mock Redis 连接工厂，满足 sa-token-redis-jackson 自动配置
     * （saTokenRedisTemplate 创建）与 SaTokenConfig.saTokenDao 的依赖。
     */
    @TestConfiguration
    static class TestInfraConfig {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return Mockito.mock(RedisConnectionFactory.class);
        }

        @Bean
        ReactiveRedisConnectionFactory reactiveRedisConnectionFactory() {
            return Mockito.mock(ReactiveRedisConnectionFactory.class);
        }
    }

    @Test
    @DisplayName("迁移后配置加载成功，Sa-Token 权威值与 access-service 一致")
    void saTokenConfigMatchesAuthority() {
        // application.yml（原 bootstrap.yml）迁移后 sa-token 块必须真正加载：
        // 未加载时 SaManager.getConfig() 为默认值（token-name=satoken、timeout=86400、token-style=uuid 默认空）
        SaTokenConfig config = SaManager.getConfig();
        assertTrue("Authorization".equals(config.getTokenName()),
            "token-name 必须为 Authorization（未加载时默认 satoken），实际 " + config.getTokenName());
        assertTrue("Bearer".equals(config.getTokenPrefix()),
            "token-prefix 必须为 Bearer，实际 " + config.getTokenPrefix());
        assertTrue("uuid".equals(config.getTokenStyle()),
            "token-style 必须为 uuid，实际 " + config.getTokenStyle());
        assertTrue(7200 == config.getTimeout(),
            "timeout 必须为 7200（2 小时），实际 " + config.getTimeout());
        assertTrue(1800 == config.getActiveTimeout(),
            "active-timeout 必须为 1800（30 分钟滑动续期），实际 " + config.getActiveTimeout());
        assertTrue(config.getIsConcurrent(), "is-concurrent 必须为 true");
        assertTrue(!config.getIsShare(), "is-share 必须为 false");
    }

    @Test
    @DisplayName("Redis 配置加载：database=0（与 access-service 共享会话键命名空间的前提）")
    void redisConfigLoadsDatabaseZero() {
        String db = applicationContext.getEnvironment().getProperty("spring.data.redis.database");
        assertTrue("0".equals(db), "Redis database 必须为 0（与 access-service 共享会话存储），实际 " + db);
    }

    /**
     * T-ACCESS-003 第三轮评审 P2 修复（2026-08-14）：日志实现回归校验。
     * 若 spring-boot-starter-logging（Logback）再次进入 classpath（与 log4j2 双 Provider 并存时
     * SLF4J 实际选择 Logback），log4j2-spring.xml 配置会被忽略——此断言确保 SLF4J 绑定 Log4j2。
     */
    @Test
    @DisplayName("日志实现为 Log4j2（log4j2-spring.xml 生效的前提，防 Logback 回归）")
    void loggingImplementationIsLog4j2() {
        org.slf4j.ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        assertTrue(factory instanceof org.apache.logging.slf4j.Log4jLoggerFactory,
            "SLF4J 必须绑定 Log4j2（若为 Logback 则 log4j2-spring.xml 被忽略），实际 " + factory.getClass().getName());
        // Logback 已从依赖树排除（pom 排除 spring-boot-starter-logging），其类不在 classpath，无需反向断言
    }

    @Test
    @DisplayName("路由定义加载：admin-service/permission-center/example-service/auth-routes 4 条")
    void routesAreDefined() {
        RouteDefinitionLocator locator = applicationContext.getBean(RouteDefinitionLocator.class);
        assertNotNull(locator, "RouteDefinitionLocator 必须存在");
        long count = locator.getRouteDefinitions().collectList().block().size();
        assertTrue(count >= 4, "至少 4 条路由（admin-service/permission-center/example-service/auth-routes），实际 " + count);
    }

    @Test
    @DisplayName("gateway.* 自定义配置加载：permission.service-url 与 whitelist")
    void gatewayCustomPropertiesLoad() {
        GatewayProperties props = applicationContext.getBean(GatewayProperties.class);
        assertNotNull(props, "GatewayProperties 必须存在");
        assertTrue("lb://permission-center".equals(props.getPermission().getServiceUrl()),
            "permission.service-url 必须为 lb://permission-center，实际 " + props.getPermission().getServiceUrl());
        assertTrue(props.getWhitelist().getPaths().contains("/auth/**"),
            "whitelist 必须包含 /auth/**");
        // Spring 应用名（Nacos 服务名）配置加载
        String appName = applicationContext.getEnvironment().getProperty("spring.application.name");
        assertTrue("gateway".equals(appName), "spring.application.name 必须为 gateway，实际 " + appName);
    }
}
