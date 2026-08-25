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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.layout.JsonLayout;

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
    // T-ACCESS-021：access-service 以 test 依赖引入后 webmvc 进入测试类路径，
    // Boot 默认推导翻成 SERVLET（触发 Gateway MvcFoundOnClasspathException、路由
    // 自动配置整体失效）——显式恢复 reactive MOCK 上下文语义
    "spring.main.web-application-type=reactive",
    // 排除 Redis/Nacos 自动配置（基础设施由 TestInfraConfig 提供）；
    // T-ACCESS-021 追加排除 access-service 依赖树新带入的自动配置：Redisson 客户端
    // 会真实连接 Redis（本测试无 Redis）；common 缓存框架的 RedissonCacheAutoConfiguration
    // 仅在 Redisson 类存在时激活（生产 Gateway 无 Redisson），激活后要求 RedissonClient
    // bean——一并排除以恢复生产等效装配（L1-only CacheService）；sa-token servlet 版
    // starter（access-service 传递）的 SaTokenContextRegister 会挤掉 reactor 版导致
    // SaTokenContext 缺失，排除后由 reactor 版提供；DataSource/JdbcTemplate
    // 在无数据源配置的 gateway 上下文中无意义
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration,com.alibaba.cloud.nacos.NacosConfigAutoConfiguration,com.alibaba.cloud.nacos.NacosDiscoveryAutoConfiguration,com.alibaba.cloud.nacos.discovery.NacosDiscoveryClientConfiguration,org.redisson.spring.starter.RedissonAutoConfigurationV2,cn.ac.fage.accessmesh.common.cache.RedissonCacheAutoConfiguration,cn.dev33.satoken.spring.SaTokenContextRegister,org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration",
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

    /**
     * T-ACCESS-003 第四轮评审 P2 修复（2026-08-14）：配置解析与布局回归校验。
     * 原配置将 Property 直接放根节点（Log4j2 报 Unknown object Property 后忽略），
     * ${LOG_PATTERN} 输出为字面量导致日志内容丢失——该错误不阻断启动、仅靠 Provider
     * 测试无法捕获。此断言校验实际 Appender 存在且 Layout 为 JsonLayout
     * （project-rules §4.2 全环境 JSON）：配置错误或布局回退（PatternLayout）时失败。
     */
    @Test
    @DisplayName("Log4j2 配置成功解析且 Layout 为 JSON（防 Property 字面量/布局回归）")
    void log4j2ConfigParsesWithJsonLayout() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration cfg = ctx.getConfiguration();
        Appender console = cfg.getRootLogger().getAppenders().get("Console");
        assertNotNull(console,
            "Console Appender 必须存在（log4j2-spring.xml 配置解析失败时 Appenders 为空）");
        assertTrue(console.getLayout() instanceof JsonLayout,
            "Layout 必须为 JsonLayout（project-rules §4.2），实际 " + console.getLayout().getClass().getName());
    }

    @Test
    @DisplayName("路由契约：恰为 access-service/example-service/auth-routes 3 条，旧路径行为不变（T-ACCESS-010）")
    void routesAreDefined() {
        RouteDefinitionLocator locator = applicationContext.getBean(RouteDefinitionLocator.class);
        assertNotNull(locator, "RouteDefinitionLocator 必须存在");
        var routes = locator.getRouteDefinitions().collectList().block();
        assertNotNull(routes, "路由定义必须可加载");
        // T-ACCESS-010：/admin/** 与 /perm/** 合并为一条 access-service 路由（StripPrefix=1），
        // /auth/** 独立（StripPrefix=0）；旧服务名不得残留为路由 id 或发现目标
        var byId = new java.util.HashSet<String>();
        var byUri = new java.util.HashSet<String>();
        routes.forEach(r -> {
            byId.add(r.getId());
            byUri.add(r.getUri().toString());
        });
        // 路由定义总数直接断言（byId 是 Set，重复 ID 的额外路由会被折叠，Set 大小证明不了定义数）
        assertTrue(routes.size() == 3, "路由定义总数必须恰为 3（access-service/example-service/auth-routes，防增删路由静默漂移），实际 " + routes.size());
        assertTrue(byId.size() == 3, "路由 ID 不得重复（重复 ID 的多条定义会被 Set 折叠），实际不同 ID " + byId);
        assertTrue(byId.contains("access-service"), "必须存在合并路由 access-service，实际 " + byId);
        assertTrue(byId.contains("example-service"), "必须存在路由 example-service，实际 " + byId);
        assertTrue(byId.contains("auth-routes"), "必须存在路由 auth-routes，实际 " + byId);
        assertTrue(byUri.contains("lb://access-service"), "发现目标必须包含 lb://access-service，实际 " + byUri);
        routes.stream().filter(r -> "access-service".equals(r.getId())).findFirst().ifPresent(r -> {
            assertTrue(r.getUri().toString().equals("lb://access-service"),
                "access-service 路由目标必须为 lb://access-service，实际 " + r.getUri());
            assertTrue(r.getPredicates().stream()
                    .anyMatch(p -> "Path".equals(p.getName())
                        && p.getArgs().containsValue("/admin/**") && p.getArgs().containsValue("/perm/**")),
                "合并路由必须同时覆盖 /admin/** 与 /perm/**，实际 " + r.getPredicates());
            assertTrue(r.getFilters().stream()
                    .anyMatch(f -> "StripPrefix".equals(f.getName()) && "1".equals(f.getArgs().get("_genkey_0"))),
                "合并路由 StripPrefix 必须为 1（原路径行为不变），实际 " + r.getFilters());
            assertTrue("access-service".equals(r.getMetadata().get("serviceCode")),
                "合并路由 metadata.serviceCode 必须为 access-service，实际 " + r.getMetadata().get("serviceCode"));
        });
        // 评审 P2：auth-routes 与 example-service 的原路径行为（Path + StripPrefix）一并固化
        routes.stream().filter(r -> "auth-routes".equals(r.getId())).findFirst().ifPresent(r -> {
            assertTrue(r.getUri().toString().equals("lb://access-service"),
                "auth-routes 目标必须为 lb://access-service（原 lb://admin-service），实际 " + r.getUri());
            assertTrue(r.getPredicates().stream()
                    .anyMatch(p -> "Path".equals(p.getName()) && p.getArgs().containsValue("/auth/**")),
                "auth-routes 必须覆盖 /auth/**（原路径行为不变），实际 " + r.getPredicates());
            assertTrue(r.getFilters().stream()
                    .anyMatch(f -> "StripPrefix".equals(f.getName()) && "0".equals(f.getArgs().get("_genkey_0"))),
                "auth-routes StripPrefix 必须为 0（/auth/** 不剥前缀直传，原路径行为不变），实际 " + r.getFilters());
        });
        routes.stream().filter(r -> "example-service".equals(r.getId())).findFirst().ifPresent(r -> {
            assertTrue(r.getPredicates().stream()
                    .anyMatch(p -> "Path".equals(p.getName()) && p.getArgs().containsValue("/example/**")),
                "example-service 必须覆盖 /example/**，实际 " + r.getPredicates());
            assertTrue(r.getFilters().stream()
                    .anyMatch(f -> "StripPrefix".equals(f.getName()) && "1".equals(f.getArgs().get("_genkey_0"))),
                "example-service StripPrefix 必须为 1，实际 " + r.getFilters());
        });
        assertTrue(byUri.stream().noneMatch(u -> u.contains("admin-service") || u.contains("permission-center")),
            "路由发现目标不得残留旧服务名，实际 " + byUri);
        assertTrue(byId.stream().noneMatch(id -> id.equals("admin-service") || id.equals("permission-center")),
            "路由 id 不得残留旧服务名，实际 " + byId);
    }

    @Test
    @DisplayName("gateway.* 自定义配置加载：permission.service-url 与 whitelist")
    void gatewayCustomPropertiesLoad() {
        GatewayProperties props = applicationContext.getBean(GatewayProperties.class);
        assertNotNull(props, "GatewayProperties 必须存在");
        assertTrue("lb://access-service".equals(props.getPermission().getServiceUrl()),
            "permission.service-url 必须为 lb://access-service（T-ACCESS-010 切换），实际 " + props.getPermission().getServiceUrl());
        assertTrue(props.getWhitelist().getPaths().contains("/auth/**"),
            "whitelist 必须包含 /auth/**");
        // T-GW-007：主端口白名单不得含任何 /actuator 路径（actuator 经独立管理端口提供）
        assertTrue(props.getWhitelist().getPaths().stream().noneMatch(p -> p.startsWith("/actuator")),
            "whitelist 不得包含 /actuator/**（T-GW-007 移至管理端口），实际 " + props.getWhitelist().getPaths());
        // Spring 应用名（Nacos 服务名）配置加载
        String appName = applicationContext.getEnvironment().getProperty("spring.application.name");
        assertTrue("gateway".equals(appName), "spring.application.name 必须为 gateway，实际 " + appName);
    }

    @Test
    @DisplayName("T-GW-007 CORS 环境化绑定：默认 localhost 列表 + credentials + 管理端口分离")
    void corsEnvironmentalizedAndManagementPortSplit() {
        org.springframework.cloud.gateway.config.GlobalCorsProperties cors =
            applicationContext.getBean(org.springframework.cloud.gateway.config.GlobalCorsProperties.class);
        org.springframework.web.cors.CorsConfiguration cfg =
            cors.getCorsConfigurations().get("/**");
        assertNotNull(cfg, "globalcors /** 配置必须存在（yml 键 '[/**]' 经 Binder 绑定后 key 为 /**）");
        assertTrue(java.util.List.of("http://localhost:5173").equals(cfg.getAllowedOriginPatterns()),
            "allowed-origin-patterns 默认必须为明确 localhost 列表（开发直连调试；T-GW-007）,实际 "
                + cfg.getAllowedOriginPatterns());
        assertTrue(Boolean.TRUE.equals(cfg.getAllowCredentials()), "allow-credentials 默认 true");

        var env = applicationContext.getEnvironment();
        assertTrue("8081".equals(env.getProperty("management.server.port")),
            "management.server.port 默认 8081（独立管理端口），实际 " + env.getProperty("management.server.port"));
        assertTrue("127.0.0.1".equals(env.getProperty("management.server.address")),
            "management.server.address 默认 127.0.0.1（仅同机可达），实际 " + env.getProperty("management.server.address"));
    }
}
