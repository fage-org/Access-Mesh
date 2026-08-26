package cn.ac.fage.accessmesh.access;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.layout.JsonLayout;
import org.mockito.Mockito;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * access-service Spring Context 启动验证测试。
 * <p>
 * 使用 H2 (PostgreSQL 兼容模式) 替代外部数据库，禁用 Redis / Nacos / Feign 客户端注册，
 * 验证归并后的单模块 Spring Context 可以成功启动、无 Bean 名冲突。
 * </p>
 */
@SpringBootTest(
    classes = {AccessServiceApplication.class, AccessServiceApplicationTest.TestDataSourceConfig.class},
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@TestPropertySource(properties = {
    // Nacos Config Data 阶段隔离：覆盖 application.yml 的 nacos import，测试不连接 Nacos
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false",
    "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false",
    // 定时任务隔离：关闭会查空库的调度器，避免污染后续测试
    // 排除 Redis/Nacos 自动配置
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,org.redisson.spring.starter.RedissonAutoConfigurationV2,com.alibaba.cloud.nacos.NacosConfigAutoConfiguration,com.alibaba.cloud.nacos.NacosDiscoveryAutoConfiguration,com.alibaba.cloud.nacos.discovery.NacosDiscoveryClientConfiguration",
    "mybatis-flex.configuration.map-underscore-to-camel-case=true",
    "logging.level.cn.ac.fage.accessmesh=WARN",
    "JWT_SECRET_KEY=test-jwt-secret-for-context-test-only",
    "ACCESSMESH_SIGNATURE_SECRET=test-signature-secret-for-context-test-only",
    "PERM_INTERNAL_SECRET=test-internal-secret-for-context-test-only"
})
class AccessServiceApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 测试数据源配置：提供 H2 内存数据库，满足 MybatisFlexAutoConfiguration 的
     * @ConditionalOnSingleCandidate(DataSource.class) 条件。
     */
    @TestConfiguration
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    static class TestDataSourceConfig {

        @Bean
        @org.springframework.context.annotation.Primary
        DataSource dataSource() {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setUrl("jdbc:h2:mem:access_db;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
            ds.setUser("sa");
            ds.setPassword("");
            return ds;
        }

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return Mockito.mock(RedisConnectionFactory.class);
        }

        @Bean
        StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
            StringRedisTemplate template = new StringRedisTemplate();
            template.setConnectionFactory(factory);
            return template;
        }

        @Bean
        RedissonClient redissonClient() {
            return Mockito.mock(RedissonClient.class);
        }
    }

    @Test
    @DisplayName("Spring Context 启动成功，无 Bean 名冲突")
    void contextLoads() {
        // 验证归并后的 Spring Context 可以成功启动
    }

    @Test
    @DisplayName("启动类注解配置正确")
    void applicationAnnotationsAreCorrect() {
        assertNotNull(AccessServiceApplication.class.getAnnotation(
            org.springframework.boot.autoconfigure.SpringBootApplication.class));
        assertNotNull(AccessServiceApplication.class.getAnnotation(
            org.mybatis.spring.annotation.MapperScan.class));
        assertNotNull(AccessServiceApplication.class.getAnnotation(EnableAsync.class));
        assertNotNull(AccessServiceApplication.class.getAnnotation(EnableScheduling.class));
    }

    /**
     * T-ACCESS-003 验收：基础设施 Bean 唯一（单数据源、单事务管理器、唯一 CacheService、唯一 ObjectMapper）。
     */
    @Test
    @DisplayName("T-ACCESS-003：基础设施 Bean 唯一（数据源/事务/CacheService/ObjectMapper）")
    void infrastructureBeansAreUnique() {
        // 单数据源
        assertNotNull(applicationContext.getBean(DataSource.class));
        assertTrue(applicationContext.getBeanNamesForType(DataSource.class).length == 1,
            "数据源必须唯一，实际 " + applicationContext.getBeanNamesForType(DataSource.class).length + " 个");

        // 单事务管理器（MyBatis-Flex starter 自动配置的 FlexTransactionManager，唯一）
        org.springframework.transaction.PlatformTransactionManager tm =
            applicationContext.getBean(org.springframework.transaction.PlatformTransactionManager.class);
        assertNotNull(tm, "事务管理器必须存在");
        assertTrue(tm instanceof com.mybatisflex.spring.FlexTransactionManager,
            "事务管理器应为 MyBatis-Flex FlexTransactionManager，实际 " + tm.getClass().getName());
        assertTrue(applicationContext.getBeanNamesForType(
            org.springframework.transaction.PlatformTransactionManager.class).length == 1,
            "事务管理器必须唯一");

        // 唯一 CacheService（common 统一缓存框架）
        assertNotNull(applicationContext.getBean(CacheService.class));
        assertTrue(applicationContext.getBeanNamesForType(CacheService.class).length == 1,
            "CacheService 必须唯一");

        // 唯一 ObjectMapper：删除 permission RedisConfig 裸 ObjectMapper 后，
        // 全局唯一实例由 common 缓存框架 CacheAutoConfiguration.cacheObjectMapper 提供
        // （条件评估顺序：common jar 在 classpath 前部先注册，Boot 的 jacksonObjectMapper 回退）。
        // 注：spring.jackson.* 配置不驱动全局（评审 P2 结论，用户决策接受现状），
        // 该 mapper 已注册 JavaTimeModule + 禁用时间戳，LocalDateTime 输出 ISO-8601。
        assertNotNull(applicationContext.getBean(ObjectMapper.class));
        assertTrue(applicationContext.getBeanNamesForType(ObjectMapper.class).length == 1,
            "ObjectMapper 必须唯一");
    }

    /**
     * T-ACCESS-003 验收：唯一 ObjectMapper 序列化可用（JavaTimeModule 生效，
     * LocalDateTime 输出 ISO-8601 —— 精确断言，评审 P2 修正）。
     */
    @Test
    @DisplayName("T-ACCESS-003：ObjectMapper JavaTimeModule 生效，LocalDateTime 输出 ISO-8601")
    void objectMapperSerializesLocalDateTime() throws Exception {
        ObjectMapper mapper = applicationContext.getBean(ObjectMapper.class);
        String json = mapper.writeValueAsString(LocalDateTime.of(2026, 8, 13, 10, 30, 0));
        assertTrue(json.contains("2026-08-13T10:30:00"),
            "LocalDateTime 应输出 ISO-8601（2026-08-13T10:30:00），实际 " + json);
    }

    /**
     * T-ACCESS-003 验收：Sa-Token 权威配置生效（timeout=7200/active-timeout=1800/token-style=uuid/token-name/token-prefix）。
     * login-type 无配置键，两侧均为 StpUtil.login() 默认类型 "login"（文档口径）。
     * token-prefix=Bearer 是评审修复（P1）：前端恒发 "Authorization: Bearer <token>"，缺失时整个
     * "Bearer <uuid>" 被当作 token 查 Redis 导致全部会话校验 401（/auth/userinfo 等白名单直连接口）。
     */
    @Test
    @DisplayName("T-ACCESS-003：Sa-Token 平台用户会话权威配置生效（2h + 30min 滑动 + uuid + Bearer 前缀）")
    void saTokenConfigMatchesAuthority() {
        SaTokenConfig config = SaManager.getConfig();
        assertTrue(7200 == config.getTimeout(), "timeout 必须为 7200（2 小时），实际 " + config.getTimeout());
        assertTrue(1800 == config.getActiveTimeout(), "active-timeout 必须为 1800（30 分钟滑动续期），实际 " + config.getActiveTimeout());
        assertTrue("Authorization".equals(config.getTokenName()), "token-name 必须为 Authorization");
        assertTrue("Bearer".equals(config.getTokenPrefix()), "token-prefix 必须为 Bearer（与 Gateway/前端一致），实际 " + config.getTokenPrefix());
        assertTrue("uuid".equals(config.getTokenStyle()), "token-style 必须为 uuid（与 Gateway 统一），实际 " + config.getTokenStyle());
    }

    /**
     * T-ACCESS-003 评审 P2 修复（2026-08-14）：expiresIn 单一权威来源防漂移。
     * AuthServiceImpl 的 LoginResp.expiresIn 直接读 SaManager.getConfig().getTimeout()（sa-token.timeout），
     * 无独立 expiresIn 配置键——Nacos 只覆盖 sa-token.timeout 时展示自动跟随真实 TTL。
     * 此断言确保 sa-token.timeout 为权威值（见 saTokenConfigMatchesAuthority），并确认独立键已移除
     * （防旧配置残留漂移）。
     */
    @Test
    @DisplayName("T-ACCESS-003：expiresIn 无独立配置键（单一来源 sa-token.timeout）")
    void expiresInHasSingleAuthoritySource() {
        String staleKey = applicationContext.getEnvironment().getProperty("access.session.expires-in-seconds");
        assertTrue(staleKey == null, "access.session.expires-in-seconds 独立键已移除（评审 P2 修复），残留 " + staleKey);
    }

    /**
     * T-ACCESS-003 第四轮评审 P2 修复（2026-08-14，用户决策两端同步 JSON 化）：
     * access-service 的 log4j2-spring.xml 布局改为 JsonLayout（project-rules §4.2 全环境 JSON），
     * 与 Gateway 日志口径一致。此断言防布局回归（文本 PatternLayout 或配置错误时失败）。
     */
    @Test
    @DisplayName("T-ACCESS-003：access-service Log4j2 布局为 JSON（规范 §4.2）")
    void log4j2ConfigUsesJsonLayout() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration cfg = ctx.getConfiguration();
        Appender console = cfg.getRootLogger().getAppenders().get("Console");
        assertNotNull(console, "Console Appender 必须存在（log4j2-spring.xml 配置解析失败时 Appenders 为空）");
        assertTrue(console.getLayout() instanceof JsonLayout,
            "Layout 必须为 JsonLayout（project-rules §4.2 全环境 JSON），实际 " + console.getLayout().getClass().getName());
    }

    /**
     * T-ACCESS-024 验收：common 的 {@code UtcTimezoneEnvironmentPostProcessor} 经
     * spring.factories 注册，@SpringBootTest 上下文环境准备阶段即强制 JVM 默认时区 UTC
     * （测试 JVM 不经 main()，部署级方案罩不住的场景）。注册发现断言与宿主时区无关
     * （默认时区即 UTC 的环境下时区断言无判别力，注册丢失只能由 loader 断言暴露）；
     * 时区断言在非 UTC 宿主上证明 EPP 已实际执行。
     */
    @Test
    @DisplayName("T-ACCESS-024：EPP spring.factories 注册可发现，上下文 JVM 默认时区强制 UTC")
    void utcTimezoneEnforcedByEnvironmentPostProcessor() throws java.io.IOException {
        // 枚举类路径上全部 META-INF/spring.factories 查找注册（与 SpringFactoriesLoader 合并语义
        // 一致，不依赖类路径顺序；load() 会实例化全部 EPP——CloudFoundry 等第三方 EPP 在测试
        // 环境不可实例化，不适合作为发现断言载体）
        boolean found = false;
        java.util.List<String> files = new java.util.ArrayList<>();
        var urls = Thread.currentThread().getContextClassLoader()
            .getResources("META-INF/spring.factories");
        while (urls.hasMoreElements()) {
            java.net.URL url = urls.nextElement();
            java.util.Properties factories = new java.util.Properties();
            try (var in = url.openStream()) {
                factories.load(in);
            }
            files.add(url.toString());
            String registered = factories.getProperty(EnvironmentPostProcessor.class.getName(), "");
            if (registered.contains(
                    "cn.ac.fage.accessmesh.common.timezone.UtcTimezoneEnvironmentPostProcessor")) {
                found = true;
            }
        }
        assertTrue(found, "META-INF/spring.factories 必须注册 UtcTimezoneEnvironmentPostProcessor，"
            + "扫描到的文件：" + files);
        assertTrue("UTC".equals(java.util.TimeZone.getDefault().getID()),
            "Spring 上下文加载后 JVM 默认时区必须为 UTC，实际 "
                + java.util.TimeZone.getDefault().getID());
    }

    /**
     * T-ACCESS-024 验收：唯一 ObjectMapper 时区为 UTC（防御性兜底，见
     * {@link cn.ac.fage.accessmesh.common.cache.CacheAutoConfiguration#cacheObjectMapper()}）。
     * bean 名断言钉死该唯一实例即 cacheObjectMapper 本体（兼任 HTTP 序列化），
     * 排除 Boot 默认 jacksonObjectMapper 竞争胜出的歧义（两者时区同为 UTC，TZ 断言无法区分）。
     */
    @Test
    @DisplayName("T-ACCESS-024：全局 ObjectMapper 时区为 UTC（Jackson 序列化时区统一）")
    void objectMapperUsesUtcTimezone() {
        String[] mapperBeanNames = applicationContext.getBeanNamesForType(ObjectMapper.class);
        assertTrue(mapperBeanNames.length == 1 && "cacheObjectMapper".equals(mapperBeanNames[0]),
            "唯一 ObjectMapper 必须为 common cacheObjectMapper（兼任 HTTP 序列化），实际 "
                + java.util.Arrays.toString(mapperBeanNames));
        ObjectMapper mapper = applicationContext.getBean(ObjectMapper.class);
        assertTrue("UTC".equals(mapper.getSerializationConfig().getTimeZone().getID()),
            "全局 ObjectMapper 时区必须为 UTC，实际 "
                + mapper.getSerializationConfig().getTimeZone().getID());
    }

    /**
     * T-ACCESS-003 验收：租户解析可用（infrastructure 唯一 TenantContextHolder + MybatisFlexTenantConfig）。
     */
    @Test
    @DisplayName("T-ACCESS-003：租户上下文设置与清理可用")
    void tenantContextResolvesAndClears() {
        cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder.setTenantId(42L);
        assertTrue(42L == cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder.getTenantId(),
            "租户 ID 应可设置与读取");
        cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder.clear();
        assertTrue(cn.ac.fage.accessmesh.access.infrastructure.TenantContextHolder.getTenantId() == null,
            "清理后租户上下文应为空");
        // 租户配置类存在且已装配（@Configuration 生效）
        assertTrue(applicationContext.getBeanNamesForType(
            cn.ac.fage.accessmesh.access.infrastructure.MybatisFlexTenantConfig.class).length == 1,
            "MybatisFlexTenantConfig 必须装配");
    }
}
