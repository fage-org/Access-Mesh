package cn.ac.fage.accessmesh.access;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import cn.ac.fage.accessmesh.common.mybatis.TenantIdProvider;
import org.mockito.Mockito;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;

import static org.junit.jupiter.api.Assertions.assertNotNull;

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
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,org.redisson.spring.starter.RedissonAutoConfigurationV2,com.alibaba.cloud.nacos.NacosConfigAutoConfiguration,com.alibaba.cloud.nacos.NacosDiscoveryAutoConfiguration,com.alibaba.cloud.nacos.discovery.NacosDiscoveryClientConfiguration",
    "mybatis-flex.configuration.map-underscore-to-camel-case=true",
    "logging.level.cn.ac.fage.accessmesh=WARN",
    "JWT_SECRET_KEY=test-jwt-secret-for-context-test-only",
    "ACCESSMESH_SIGNATURE_SECRET=test-signature-secret-for-context-test-only",
    "PERM_INTERNAL_SECRET=test-internal-secret-for-context-test-only"
})
class AccessServiceApplicationTest {

    /**
     * Mock 租户提供器：空租户集合，跳过 @PostConstruct 初始化查询（sys_job/sys_user 等表在空库中不存在）。
     */
    @MockBean
    private TenantIdProvider tenantIdProvider;

    @org.junit.jupiter.api.BeforeEach
    void mockTenantIds() {
        when(tenantIdProvider.getTenantIds()).thenReturn(java.util.Collections.emptySet());
    }

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
        assertNotNull(AccessServiceApplication.class.getAnnotation(EnableFeignClients.class));
        assertNotNull(AccessServiceApplication.class.getAnnotation(EnableAsync.class));
        assertNotNull(AccessServiceApplication.class.getAnnotation(EnableScheduling.class));
    }
}
