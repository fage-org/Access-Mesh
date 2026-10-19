package cn.ac.fage.accessmesh.access.it;

import cn.ac.fage.accessmesh.testsupport.PgTestInfra;
import cn.ac.fage.accessmesh.testsupport.TestForkSlot;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Access test facade: caller-owned IAM template/seed and fork-isolated Redis databases.
 * PG lifecycle and fork allocation are shared with example; each test class still owns its context.
 * Redis indexes wrap within the fork segment, so completed contexts must not write in the background.
 * Pub/sub remains global across logical databases; existing isolated-test boundaries still apply.
 */
public final class ItInfra {
    public static final String REDIS_TEST_PASSWORD = "accessmesh-test";
    public static final String PG_USERNAME = PgTestInfra.USERNAME;
    public static final String PG_PASSWORD = PgTestInfra.PASSWORD;
    private static final PgTestInfra PG = new PgTestInfra("access",
        Path.of("..", "docs", "design", "schema", "access-service.sql"),
        "SELECT initialize_tenant_baseline(1)");
    private static final int REDIS_INDEX_BASE = (TestForkSlot.number() - 1) * 16 + 1;
    private static final int REDIS_INDEX_MAX = TestForkSlot.number() * 16 - 1;
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
        .withCommand("redis-server", "--requirepass", REDIS_TEST_PASSWORD, "--databases", "64")
        .withExposedPorts(6379).withReuse(true);
    private static final AtomicInteger REDIS_INDEX_SEQ = new AtomicInteger();
    private static final Map<Class<?>, Integer> REDIS_BINDINGS = new HashMap<>();

    private ItInfra() {}

    public static void register(DynamicPropertyRegistry registry, Class<?> testClass) {
        register(registry, testClass, true);
    }

    public static void register(DynamicPropertyRegistry registry, Class<?> testClass, boolean fromTemplate) {
        prepare(testClass, fromTemplate);
        PG.register(registry, testClass, fromTemplate);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> REDIS_TEST_PASSWORD);
        registry.add("spring.data.redis.database", () -> redisDatabase(testClass));
    }

    public static synchronized void prepare(Class<?> testClass, boolean fromTemplate) {
        PG.prepare(testClass, fromTemplate);
        startRedis();
        REDIS_BINDINGS.computeIfAbsent(testClass, ignored -> acquireRedisDatabase());
    }

    public static String jdbcUrl(Class<?> testClass) { return PG.jdbcUrl(testClass); }
    public static String username() { return PG_USERNAME; }
    public static String password() { return PG_PASSWORD; }
    public static String redisHost() { return REDIS.getHost(); }
    public static int redisPort() { return REDIS.getMappedPort(6379); }

    public static synchronized int redisDatabase(Class<?> testClass) {
        Integer database = REDIS_BINDINGS.get(testClass);
        if (database == null) throw new IllegalStateException("Unregistered ItInfra class: " + testClass.getName());
        return database;
    }

    public static synchronized String createStandaloneDatabase(String nameHint) {
        String url = PG.createStandaloneDatabase(nameHint);
        startRedis();
        return url;
    }

    private static void startRedis() {
        if (!REDIS.isRunning()) REDIS.start();
    }

    private static int acquireRedisDatabase() {
        int index = REDIS_INDEX_SEQ.updateAndGet(
            i -> (i >= REDIS_INDEX_MAX || i < REDIS_INDEX_BASE) ? REDIS_INDEX_BASE : i + 1);
        RedisURI uri = RedisURI.builder().withHost(REDIS.getHost()).withPort(REDIS.getMappedPort(6379))
            .withPassword(REDIS_TEST_PASSWORD.toCharArray()).withDatabase(index).build();
        RedisClient client = RedisClient.create(uri);
        try (StatefulRedisConnection<String, String> connection = client.connect()) {
            connection.sync().flushdb();
        } finally {
            client.shutdown();
        }
        return index;
    }
}
