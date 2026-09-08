package cn.ac.fage.accessmesh.access.it;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 容器轨道共享测试基础设施（T-ACCESS-030）：单例 PG + Redis 容器、按类建库（模板克隆）、
 * 按类 Redis 逻辑库索引。
 *
 * <p>取代此前「每测试类各起一对容器 + 各自全量 DDL」的形态（28 类 × 15-30s 基建成本）：
 * <ul>
 *   <li>PG/Redis 每会话各启动一次；类库经 {@code CREATE DATABASE ... TEMPLATE it_tpl} 秒级克隆，
 *       权威 DDL（docs/design/schema/access-service.sql）每会话仅在模板构建时执行一次；</li>
 *   <li>会话首启清理上次会话遗留 {@code it_} 类库——reusable 容器跨 JVM 存活时不携带脏数据；</li>
 *   <li>Redis 单例上按类分配逻辑库索引（fork 槽位分段内轮转，取用时 FLUSHDB），键空间隔离等价于
 *       独容器。每槽位可分配索引 15 个（段首一格留缓冲），同 fork 测试类超过 15 个时轮转回绕：
 *       安全性依赖已完结类不再写 Redis——无
 *       @DirtiesContext 的常驻缓存上下文当前经核无后台 Redis 写入方，若未来引入会话清扫/缓存预热
 *       类后台任务需重审本前提；pub/sub 通道全局可见（跨库），广播敏感类由 {@code @Isolated}
 *       名单隔离（仅串行化同 fork 内邻类，跨 fork 互扰接受现状）；</li>
 *   <li>自建局部表 / 自证 DDL 的偏差类走 {@code fromTemplate=false} 空库通道，自建逻辑原地保留。</li>
 * </ul>
 *
 * <p>容器声明 {@code withReuse(true)}：仅当本机 ~/.testcontainers.properties 存在
 * {@code testcontainers.reuse.enable=true} 时生效（配置哈希固定：镜像/环境/端口/命令不变），
 * CI / 他人环境无此文件自动回退普通起停。Docker 不可用时的跳过仍由各类自有的
 * {@code @Testcontainers(disabledWithoutDocker = true)} 承担（先于上下文装配，不会触达本类）。
 *
 * <p>使用方式：测试类 {@code @DynamicPropertySource} 方法体内调用
 * {@code ItInfra.register(registry, X.class);}（每类各自声明方法保证 Spring 上下文缓存键互异，
 * 维持按类独立上下文）；类内需要裸 JDBC 时用 {@link #jdbcUrl(Class)} / {@link #username()} 等访问器。
 */
public final class ItInfra {

    private static final Logger log = LoggerFactory.getLogger(ItInfra.class);

    private static final Path DDL_PATH = Path.of("..", "docs", "design", "schema", "access-service.sql");

    /** Redis 容器与客户端密码必须对齐（历史口径沿用）：主配置 ${REDIS_PASSWORD:} 解析为空串而非 null，Redisson 对空串仍发 AUTH */
    public static final String REDIS_TEST_PASSWORD = "accessmesh-test";
    public static final String PG_USERNAME = "perm";
    public static final String PG_PASSWORD = "perm";

    private static final String HUB_DATABASE = "it_hub";
    /**
     * fork 标记（surefire.forkNumber 在 systemPropertyVariables 中插值为空串不可用，第五轮实证）：
     * 启动时以文件锁抢占 1..4 槽位（确定性、跨会话稳定、锁随 JVM 释放；-Dit.forkNumber 显式指定 1..4 优先，
     * IDE 单 JVM 直跑可固定；槽位探测失败 fail-fast 不静默回退——回退槽会与其他 JVM 同名互删类库）。
     * 类库/模板库名与 Redis 索引段都按 fork 隔离，避免并行 fork 间同名库 DROP 互踩与索引段
     * FLUSHDB 互踩；会话首启清理也只清本 fork 的遗留库（其他 fork 的库属并行邻进程）。
     */
    private static final int FORK_NUMBER = forkNumber();
    private static final String FORK_TAG = "f" + FORK_NUMBER;
    private static final String TEMPLATE_DATABASE = "it_tpl_" + FORK_TAG;
    /**
     * Redis 逻辑库索引：容器开 --databases 64，槽位 s（1..4）独占 16 索引段 (s-1)*16 .. (s-1)*16+15
     * （2026-09-06 用户定案：利用 Redis 多 database 消除槽间段共享；每槽位可分配索引 15 个
     * ——段首一格留缓冲——同 fork 类数 ≤15 时轮转不回绕，超过则回绕）。
     * 段首一格留缓冲不分配；回绕或跨会话脏键由取用时 FLUSHDB 兜底。
     */
    private static final int REDIS_INDEX_BASE = (FORK_NUMBER - 1) * 16 + 1;
    private static final int REDIS_INDEX_MAX = FORK_NUMBER * 16 - 1;

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName(HUB_DATABASE)
        .withUsername(PG_USERNAME)
        .withPassword(PG_PASSWORD)
        .withReuse(true);

    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
        .withCommand("redis-server", "--requirepass", REDIS_TEST_PASSWORD, "--databases", "64")
        .withExposedPorts(6379)
        .withReuse(true);

    private static final Object START_LOCK = new Object();
    private static volatile boolean infraReady;

    /** 建库（含并发模板克隆）串行化：PG 并发克隆同一模板会报模板被占用的间歇错误，2 线程下串行开销可忽略 */
    private static final Object DB_LOCK = new Object();
    private static final AtomicInteger REDIS_INDEX_SEQ = new AtomicInteger();
    private static final Map<Class<?>, Binding> BINDINGS = new ConcurrentHashMap<>();

    private record Binding(String database, int redisDatabase, boolean fromTemplate) {
    }

    private ItInfra() {
    }

    // ==== 注册入口 ====

    /** 标准注册：按类克隆模板库（全量 DDL 已就位，类内不再执行 DDL）+ 独立 Redis 逻辑库。 */
    public static void register(DynamicPropertyRegistry registry, Class<?> testClass) {
        register(registry, testClass, true);
    }

    /**
     * @param fromTemplate false 给自建局部表 / 自证 DDL 的偏差类：空库，建表逻辑留在类内
     */
    public static void register(DynamicPropertyRegistry registry, Class<?> testClass, boolean fromTemplate) {
        prepare(testClass, fromTemplate);
        Binding binding = BINDINGS.get(testClass);
        String url = jdbcUrl(binding.database(), true);
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> PG_USERNAME);
        registry.add("spring.datasource.password", () -> PG_PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> REDIS_TEST_PASSWORD);
        registry.add("spring.data.redis.database", () -> binding.redisDatabase());
    }

    /**
     * 上下文创建前的建库入口：@BeforeAll（静态、先于 Spring 上下文装配）里就需要类库 URL 的
     * 空库偏差类（DualInstanceContainerTest / TaskExecutionLeaseConcurrencyTest）先调用本方法，
     * 后续 register 复用同一绑定（fromTemplate 口径必须一致）。
     */
    public static void prepare(Class<?> testClass, boolean fromTemplate) {
        start();
        BINDINGS.computeIfAbsent(testClass, c -> createBinding(c, fromTemplate));
        // 口径护栏：同一类先 prepare 后 register 时两处 fromTemplate 必须一致，
        // 静默复用首绑会让「模板库缺表」以离因很远的下游 SQL 错误形态出现
        if (BINDINGS.get(testClass).fromTemplate() != fromTemplate) {
            throw new IllegalStateException(testClass.getSimpleName()
                + " 的 prepare/register fromTemplate 口径不一致: " + fromTemplate);
        }
    }

    // ==== 裸 JDBC / 手工实例访问器（测试体内直连类库用） ====

    /** 类库 JDBC URL（含 stringtype=unspecified，与标准类历史口径一致）。 */
    public static String jdbcUrl(Class<?> testClass) {
        return jdbcUrl(binding(testClass).database(), true);
    }

    public static String username() {
        return PG_USERNAME;
    }

    public static String password() {
        return PG_PASSWORD;
    }

    public static String redisHost() {
        return REDIS.getHost();
    }

    public static int redisPort() {
        return REDIS.getMappedPort(6379);
    }

    /** 手工启动的第二实例（如 DualInstanceContainerTest）必须与主上下文同库同索引，否则双实例不共享键空间。 */
    public static int redisDatabase(Class<?> testClass) {
        return binding(testClass).redisDatabase();
    }

    /** schema 自证类（AccessServiceSchemaPostgresTest）无 Spring 上下文：直接用单例 PG 起空库自跑 DDL。 */
    public static String createStandaloneDatabase(String nameHint) {
        start();
        String database = "it_" + FORK_TAG + "_" + sanitize(nameHint);
        synchronized (DB_LOCK) {
            try (Connection admin = adminConnection()) {
                exec(admin, "DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
                exec(admin, "CREATE DATABASE " + database);
            } catch (SQLException e) {
                throw new IllegalStateException("独立空库创建失败: " + database, e);
            }
        }
        return jdbcUrl(database, true);
    }

    // ==== 内部实现 ====

    private static Binding createBinding(Class<?> testClass, boolean fromTemplate) {
        String database = "it_" + FORK_TAG + "_" + sanitize(testClass.getSimpleName());
        log.info("ItInfra createBinding class={} db={} fromTemplate={} pid={}",
            testClass.getSimpleName(), database, fromTemplate, ProcessHandle.current().pid());
        int redisDatabase;
        synchronized (DB_LOCK) {
            try (Connection admin = adminConnection()) {
                exec(admin, "DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
                exec(admin, "CREATE DATABASE " + database
                    + (fromTemplate ? " TEMPLATE " + TEMPLATE_DATABASE : ""));
            } catch (SQLException e) {
                throw new IllegalStateException("按类建库失败: " + database, e);
            }
            redisDatabase = acquireRedisDatabase();
        }
        return new Binding(database, redisDatabase, fromTemplate);
    }

    private static Binding binding(Class<?> testClass) {
        Binding binding = BINDINGS.get(testClass);
        if (binding == null) {
            throw new IllegalStateException(testClass.getSimpleName()
                + " 尚未注册 ItInfra（须先在 @DynamicPropertySource 中调用 register）");
        }
        return binding;
    }

    private static void start() {
        if (infraReady) {
            return;
        }
        synchronized (START_LOCK) {
            if (infraReady) {
                return;
            }
            POSTGRES.start();
            REDIS.start();
            rebuildTemplate();
            infraReady = true;
        }
    }

    /** 会话首启：清掉本 fork 上次会话遗留类库（reusable 容器跨 JVM 存活；其他 fork 的库属并行邻进程不碰），重建模板库并执行一次权威 DDL。 */
    private static void rebuildTemplate() {
        String stalePattern = "it\\_" + FORK_TAG + "\\_%";
        try (Connection admin = adminConnection()) {
            List<String> stale = new ArrayList<>();
            try (PreparedStatement ps = admin.prepareStatement(
                "SELECT datname FROM pg_database WHERE datname LIKE ? OR datname = ?")) {
                ps.setString(1, stalePattern);
                ps.setString(2, TEMPLATE_DATABASE);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        stale.add(rs.getString(1));
                    }
                }
            }
            for (String database : stale) {
                log.info("ItInfra session-cleanup drop {} (fork={} pid={})",
                    database, FORK_TAG, ProcessHandle.current().pid());
                exec(admin, "DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
            }
            exec(admin, "CREATE DATABASE " + TEMPLATE_DATABASE);
        } catch (SQLException e) {
            throw new IllegalStateException("模板库构建失败", e);
        }
        applyDdl(TEMPLATE_DATABASE);
    }

    /** 原样整文件执行权威 DDL（与历史各类 setupSchema 同机制：单 Statement 多语句）。 */
    private static void applyDdl(String database) {
        String sql;
        try {
            sql = Files.readString(DDL_PATH, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("权威 DDL 不存在: " + DDL_PATH.toAbsolutePath(), e);
        }
        try (Connection conn = DriverManager.getConnection(jdbcUrl(database, true), PG_USERNAME, PG_PASSWORD);
             Statement st = conn.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("DDL 执行失败: " + database, e);
        }
    }

    /** Redis 逻辑库索引本 fork 独占段内轮转 + 取用时 FLUSHDB（新类拿到的键空间保证为空）。 */
    private static int acquireRedisDatabase() {
        int index = REDIS_INDEX_SEQ.updateAndGet(
            i -> (i >= REDIS_INDEX_MAX || i < REDIS_INDEX_BASE) ? REDIS_INDEX_BASE : i + 1);
        RedisURI uri = RedisURI.builder()
            .withHost(REDIS.getHost())
            .withPort(REDIS.getMappedPort(6379))
            .withPassword(REDIS_TEST_PASSWORD.toCharArray())
            .withDatabase(index)
            .build();
        RedisClient client = RedisClient.create(uri);
        try (StatefulRedisConnection<String, String> conn = client.connect()) {
            conn.sync().flushdb();
        } finally {
            client.shutdown();
        }
        return index;
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl(HUB_DATABASE, false), PG_USERNAME, PG_PASSWORD);
    }

    private static String jdbcUrl(String database, boolean stringtype) {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432)
            + "/" + database + (stringtype ? "?stringtype=unspecified" : "");
    }

    private static void exec(Connection connection, String sql) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute(sql);
        }
    }

    private static String sanitize(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static int forkNumber() {
        String explicit = System.getProperty("it.forkNumber");
        if (explicit != null && !explicit.isBlank()) {
            try {
                int value = Integer.parseInt(explicit.trim());
                if (value < 1 || value > 4) {
                    // 越界值会产出非法库名（负号）或撞回退段，fail-loud 好于静默碰撞
                    throw new IllegalStateException("it.forkNumber 显式值须为 1..4: " + value);
                }
                // 显式槽位同样必须持有文件锁：绕锁直用会在并行 JVM 显式同值时共享
                // 库名前缀与 Redis 索引段，一方 DROP DATABASE/FLUSHDB 即互毁对方会话
                if (!tryAcquireSlot(value)) {
                    throw new IllegalStateException("it.forkNumber 显式槽位 " + value
                        + " 已被其他测试 JVM 占用，换一个 1..4 空闲槽位或等待其结束");
                }
                return value;
            } catch (NumberFormatException e) {
                throw new IllegalStateException("it.forkNumber 显式值须为 1..4 整数: " + explicit, e);
            }
        }
        return forkSlotNumber();
    }

    /** 槽位锁持有引用：局部变量会被 GC 关闭通道释放锁（第九轮实证双 fork 同槽），必须静态持有到 JVM 退出 */
    private static FileChannel slotChannel;
    private static FileLock slotLock;

    /** 文件锁抢占 1..4 槽位：并发 fork JVM 各得不同槽位；锁与通道持有到 JVM 退出不释放。
     *  锁文件必须放 user.home 固定路径——surefire 为每个 fork 分配独立 java.io.tmpdir，
     *  按 tmpdir 放锁时两 fork 各锁各的文件、双双拿到槽位 1，后启动 fork 的会话清理会
     *  互删先启动 fork 的在用类库（第八轮实证，双 pid 同 it_f1_ 前缀）。
     *  探测失败 fail-fast：静默回退共享槽会互删在用类库（第九轮实证），宁可拒绝启动。 */
    private static int forkSlotNumber() {
        for (int slot = 1; slot <= 4; slot++) {
            if (tryAcquireSlot(slot)) {
                return slot;
            }
        }
        throw new IllegalStateException("ItInfra fork 槽位 1..4 全被占用（并发测试 JVM 过多），"
            + "等待在跑构建结束或 -Dit.forkNumber=1..4 指定空闲槽位");
    }

    /** 尝试锁定指定槽位：成功则静态持有 channel/lock 并返回 true；被占用或打开失败关闭本次 channel 返回 false。 */
    private static boolean tryAcquireSlot(int slot) {
        Path dir = Path.of(System.getProperty("user.home"), ".accessmesh");
        try {
            Files.createDirectories(dir);
            FileChannel channel = FileChannel.open(dir.resolve("it-slot-" + slot + ".lck"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ);
            FileLock lock = channel.tryLock();
            if (lock != null) {
                slotChannel = channel;
                slotLock = lock;
                return true;
            }
            channel.close();
        } catch (IOException e) {
            throw new IllegalStateException("ItInfra fork 槽位探测失败 (slot=" + slot + ")", e);
        }
        return false;
    }
}
