package cn.ac.fage.accessmesh.permission.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 权限中心集成测试类
 * <p>
 * 使用 Testcontainers 启动 PostgreSQL 和 Redis 容器进行集成测试。
 * 验证应用程序能否正确连接数据库和缓存服务。
 * 测试仅在 Docker 可用时运行（disabledWithoutDocker = true）。
 * </p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class PermissionCenterIntegrationTest {

    /** PostgreSQL 测试容器 */
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("perm_test")
        .withUsername("perm")
        .withPassword("perm");

    /** Redis 测试容器 */
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    /**
     * 配置测试环境动态属性
     * <p>
     * 将 Testcontainers 的连接信息注入 Spring Boot 测试环境，
     * 动态设置数据源和 Redis 连接配置。
     * </p>
     *
     * @param registry 动态属性注册器
     */
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    /** JDBC 数据访问模板 */
    @Autowired
    JdbcTemplate jdbcTemplate;

    /** Redis 字符串操作模板 */
    @Autowired
    StringRedisTemplate redisTemplate;

    /**
     * 测试 PostgreSQL 和 Redis 连接
     * <p>
     * 验证应用程序能否正确执行数据库查询和 Redis 缓存操作。
     * 数据库测试：执行简单查询验证连接。
     * Redis 测试：写入并读取键值对验证连接。
     * </p>
     */
    @Test
    void shouldConnectPostgresAndRedis() {
        Integer one = jdbcTemplate.queryForObject("select 1", Integer.class);
        assertEquals(1, one);

        redisTemplate.opsForValue().set("perm:test:key", "ok");
        assertEquals("ok", redisTemplate.opsForValue().get("perm:test:key"));
        assertNotNull(redisTemplate.getConnectionFactory());
    }
}
