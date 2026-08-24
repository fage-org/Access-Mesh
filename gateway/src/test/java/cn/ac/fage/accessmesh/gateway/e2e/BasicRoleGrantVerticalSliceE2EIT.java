package cn.ac.fage.accessmesh.gateway.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BASIC_ROLE 授权垂直切片 E2E（T-ACCESS-021，里程碑 A 唯一产品验收链路）。
 *
 * <p>固定 8 步顺序（T-ACCESS-016 定稿，不可重排）：① 空库 bootstrap 首管理员真实登录 →
 * ② 创建目标用户与空权限 BASIC_ROLE 并分配 → ③ 为 POST /admin/role/my-info 真实创建
 * API 映射（bootstrap 仅预建资源未建映射）→ ④ 目标用户实测 403（授权前拒绝真实执行）→
 * ⑤ 授权（IT 轨经 apply-grant-plan——与授权页同一唯一写入口、同构请求体；授权页 GUI 场景
 * 由受控 runbook 另行覆盖）→ ⑥ 实测 200（30 秒陈旧窗口内轮询）→ ⑦ 重启 access-service
 * 与 Gateway（无人工改 DB/Redis）后仍 200 → ⑧ 撤权后 30 秒内恢复 403（自撤权响应单调计时）。
 * 「权限服务不可用 → Gateway 503 fail-closed」与步骤⑦合并执行：停机断言 503 后重启再断言 200。
 *
 * <p><b>拓扑</b>：PG/Redis 为 Testcontainers；access-service 与 Gateway 以<b>子进程</b>（独立 JVM、
 * 固定随机端口）从本测试的 java.class.path 启动——重启即 kill 后重新 spawn，语义等同真实服务
 * 重启，无静态状态残留（2026-08-24 用户决策）。子进程类路径过滤掉 test-classes，仅携带生产
 * classes 与依赖；Gateway 路由与权限回源客户端经 SimpleDiscoveryClient 静态实例直连
 * access-service（免 Nacos，路由 predicates/filters/serviceCode 元数据不变），真实 Nacos
 * 由 compose 手动 runbook 段覆盖。
 *
 * <p><b>令牌契约</b>：所有令牌经真实 /auth/captcha + /auth/login（tenantId=1、clientId=admin-web、
 * 目标用户使用 UserCreateResp.initialPassword）取得；验证码为真实签发与校验，仅答案由测试
 * 从 Redis 按 captchaId 只读取出（2026-08-24 用户决策，T-ACCESS-020 验收同款）。测试不直接
 * 签发或注入令牌，不旁路平台超管。
 *
 * <p>注意：本 IT 需在 maven（surefire/IDE 传递完整 java.class.path）下执行；
 * {@code @Tag("testcontainers")} 使其只随容器门控 execution 运行
 * （{@code mvn test -pl gateway}，CI 单测 job 经 -DskipTestcontainers=true 排除）。
 */
@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BasicRoleGrantVerticalSliceE2EIT {

    // ------------------------------------------------------------------
    // 常量：固定链路键（bootstrap 固定图对齐 BootstrapGraphDefinition）
    // ------------------------------------------------------------------

    private static final Path DDL_PATH = Path.of("..", "docs", "design", "schema", "access-service.sql");
    /**
     * reactor 布局下 access-service 生产 classes（本地仓库 jar 为 fat jar，不可 -cp 加载）。
     * 必须绝对路径——子进程 cwd 在 target/e2e/&lt;name&gt; 下，相对路径无法解析。
     */
    private static final String ACCESS_SERVICE_CLASSES_DIR =
        Path.of("..", "access-service", "target", "classes").toAbsolutePath().toString();
    private static final String BOOTSTRAP_ADMIN_PASSWORD = "E2E-Bootstrap-Admin-2026!";
    private static final String JWT_SECRET = "e2e-jwt-secret-0123456789abcdef0123456789abcdef";
    private static final String SIGNATURE_SECRET = "e2e-signature-secret-0123456789abcdef";
    private static final String INTERNAL_SECRET = "e2e-internal-secret-0123456789abcdef";

    private static final String TENANT_ID = "1";
    private static final String CLIENT_ID = "admin-web";
    private static final String ACCESS_MAIN_CLASS = "cn.ac.fage.accessmesh.access.AccessServiceApplication";
    private static final String GATEWAY_MAIN_CLASS = "cn.ac.fage.accessmesh.gateway.GatewayApplication";

    /** E2E 目标接口（外部路径口径，bootstrap 预建资源未建映射） */
    private static final String TARGET_API_CODE = "POST:/admin/role/my-info";
    private static final String TARGET_API_METHOD = "POST";
    private static final String TARGET_API_PATH = "/admin/role/my-info";

    /** E2E 目标角色/用户（空库每次全新容器，固定业务键无碰撞） */
    private static final String ROLE_TYPE = "BASIC_ROLE";
    private static final String ROLE_EXTERNAL_ID = "e2e-basic-role";
    private static final String TARGET_USERNAME = "e2e-target";

    /** Gateway 快照 L1 TTL 上限 15s（启动校验），等待其过期以强制回源验证 fail-closed */
    private static final Duration SNAPSHOT_STALE_WAIT = Duration.ofSeconds(16);
    /** 30 秒陈旧窗口（授权生效/撤权恢复共用同一上界，超时即失败） */
    private static final Duration STALE_WINDOW = Duration.ofSeconds(30);

    // ------------------------------------------------------------------
    // 基础设施容器
    // ------------------------------------------------------------------

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("access_db")
        .withUsername("perm")
        .withPassword("perm");

    /** 与各服务 REDIS_PASSWORD 默认值一致（Redisson 对空串密码也发 AUTH） */
    private static final String REDIS_PASSWORD = "accessmesh-dev";

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withCommand("redis-server", "--requirepass", REDIS_PASSWORD)
        .withExposedPorts(6379);

    // ------------------------------------------------------------------
    // 子进程服务与共享状态
    // ------------------------------------------------------------------

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
    private static final ObjectMapper JSON = new ObjectMapper();

    private static ServiceHandle accessService;
    private static ServiceHandle gatewayService;

    // 步间共享状态（8 步固定顺序，前序步骤产物为后序输入）
    private static String adminToken;
    private static long targetUserId;
    private static String targetInitialPassword;
    private static String targetToken;
    private static long targetApiResourceId;
    private static long grantedPermissionId;

    @BeforeAll
    static void bootStack() throws Exception {
        // 权威 DDL 一次性执行（空 schema）
        String ddl = Files.readString(DDL_PATH, StandardCharsets.UTF_8);
        try (var conn = java.sql.DriverManager.getConnection(
            postgres.getJdbcUrl() + "?stringtype=unspecified", postgres.getUsername(), postgres.getPassword());
             var st = conn.createStatement()) {
            st.execute(ddl);
        }

        int accessPort = freePort();
        int gatewayPort = freePort();

        // access-service：bootstrap 首启创建固定图（步骤①的空库侧）
        accessService = startService("access-service", ACCESS_MAIN_CLASS,
            accessServiceArgs(accessPort), accessServiceEnv(),
            URI.create("http://localhost:" + accessPort + "/auth/captcha"), true);

        // bootstrap 完成前 Web 已就绪（runner 在 context refresh 后执行），轮询首管理员
        // 主体落库后再进入步骤①，避免登录与种子竞态
        waitAdminSeeded();

        // Gateway：lb://access-service 经 SimpleDiscoveryClient 静态实例直连
        gatewayService = startService("gateway", GATEWAY_MAIN_CLASS,
            gatewayArgs(gatewayPort, accessPort), gatewayEnv(),
            URI.create("http://localhost:" + gatewayPort + "/actuator/health"), false);
    }

    @AfterAll
    static void tearDown() {
        if (gatewayService != null) {
            gatewayService.destroy();
        }
        if (accessService != null) {
            accessService.destroy();
        }
    }

    // ------------------------------------------------------------------
    // 固定 8 步
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("① 空库启动：bootstrap 首管理员经真实验证码链路登录")
    void step1_bootstrapAdminRealLogin() {
        adminToken = realLogin("admin", BOOTSTRAP_ADMIN_PASSWORD);
        assertThat(adminToken).as("首管理员登录必须取得真实令牌").isNotBlank();
    }

    @Test
    @Order(2)
    @DisplayName("② 创建目标用户（initialPassword 取令牌）与空权限 BASIC_ROLE 并分配给目标用户")
    void step2_createTargetUserAndEmptyBasicRole() {
        // 创建目标用户：initialPassword 仅本次返回
        JsonNode created = postForData(gateway() + "/admin/user/create", adminToken,
            JSON.createObjectNode().put("username", TARGET_USERNAME).put("name", "E2E Target User"));
        targetUserId = created.path("id").asLong();
        targetInitialPassword = created.path("initialPassword").asText();
        assertThat(targetUserId).as("目标用户必须取得新 ID").isPositive();
        assertThat(targetInitialPassword).as("initialPassword 必须在创建响应中返回一次").isNotBlank();

        // 创建普通 BASIC_ROLE（不含任何 API 权限）
        JsonNode role = postForData(gateway() + "/perm/api/perm/abstract-role/create", adminToken,
            JSON.createObjectNode()
                .put("roleTypeCode", ROLE_TYPE)
                .put("externalId", ROLE_EXTERNAL_ID)
                .put("name", "E2E Basic Role"));
        assertThat(role.path("externalId").asText())
            .as("BASIC_ROLE 必须按固定业务键创建").isEqualTo(ROLE_EXTERNAL_ID);

        // 分配给目标用户（此时角色不含任何 API 权限）
        JsonNode assignItem = JSON.createObjectNode()
            .put("subjectTypeCode", "LOCAL_USER")
            .put("subjectExternalId", String.valueOf(targetUserId))
            .putNull("domainCode")
            .put("roleTypeCode", ROLE_TYPE)
            .put("roleExternalId", ROLE_EXTERNAL_ID);
        JsonNode assignReq = JSON.createObjectNode().set("items",
            JSON.createArrayNode().add(assignItem));
        postForData(gateway() + "/perm/api/perm/user-role/assign", adminToken, assignReq);
    }

    @Test
    @Order(3)
    @DisplayName("③ 为目标接口 POST /admin/role/my-info 真实创建 API 资源映射（bootstrap 未建映射）")
    void step3_createTargetApiMapping() {
        // 经授权页同款资源树定位 bootstrap 预建的 API 资源（同时验证授权页读链路可用）；
        // 树接口按资源类型返回多棵树（ItemsResp<ResourceTreeResp>），逐棵遍历
        JsonNode treeData = postForData(gateway() + "/perm/api/perm/resource-entity/tree", adminToken,
            JSON.createObjectNode().putNull("resourceTypeCode").putNull("domainCode"));
        for (JsonNode tree : treeData.path("items")) {
            targetApiResourceId = findApiResourceId(tree.path("root"));
            if (targetApiResourceId > 0) {
                break;
            }
        }
        assertThat(targetApiResourceId)
            .as("bootstrap 预建的 my-info API 资源必须可见，实际树响应：%s", treeData)
            .isPositive();

        JsonNode mapping = postForData(gateway() + "/perm/api/perm/resource-api-mapping/create", adminToken,
            JSON.createObjectNode()
                .put("resourceId", targetApiResourceId)
                .put("serviceCode", "access-service")
                .put("httpMethod", TARGET_API_METHOD)
                .put("pathPattern", TARGET_API_PATH)
                .put("enabled", true));
        assertThat(mapping.path("pathPattern").asText())
            .as("目标 API 映射必须按 Gateway 外部路径创建").isEqualTo(TARGET_API_PATH);
    }

    @Test
    @Order(4)
    @DisplayName("④ 目标用户凭 initialPassword 真实登录后调用目标接口，断言 403（授权前拒绝）")
    void step4_targetUserDeniedBeforeGrant() throws IOException {
        targetToken = realLogin(TARGET_USERNAME, targetInitialPassword);
        assertThat(targetToken).as("目标用户必须能以 initialPassword 取得真实令牌").isNotBlank();

        int status = postStatus(gateway() + TARGET_API_PATH, targetToken);
        assertThat(status).as("授权前目标接口必须真实拒绝（403，非 401——令牌有效）").isEqualTo(403);
    }

    @Test
    @Order(5)
    @DisplayName("⑤ 管理员授予 BASIC_ROLE 目标 API 的 API:ACCESS（apply-grant-plan，与授权页同源写入口）")
    void step5_grantApiAccess() {
        var key = JSON.createObjectNode();
        key.put("resourceTypeCode", "API");
        key.put("resourceCode", TARGET_API_CODE);
        key.put("codeType", "default");
        key.put("operationCode", "ACCESS");
        key.put("scopeMode", "INSTANCE");
        key.putNull("conditionCode");
        key.put("canGrant", false);
        var createItem = JSON.createObjectNode();
        createItem.set("key", key);
        var plan = JSON.createObjectNode();
        plan.set("creates", JSON.createArrayNode().add(createItem));
        plan.putNull("updates");
        plan.putNull("removes");
        var req = JSON.createObjectNode();
        req.putNull("domainCode");
        req.put("roleTypeCode", ROLE_TYPE);
        req.put("roleExternalId", ROLE_EXTERNAL_ID);
        req.set("plan", plan);

        JsonNode items = postForData(
            gateway() + "/perm/api/perm/role-resource-permission/apply-grant-plan", adminToken, req)
            .path("items");
        assertThat(items.isArray() && items.size() == 1)
            .as("授权计划必须产生恰好一条授权记录").isTrue();
        grantedPermissionId = items.get(0).path("id").asLong();
        assertThat(grantedPermissionId).as("授权记录 ID 必须返回（撤权输入）").isPositive();
    }

    @Test
    @Order(6)
    @DisplayName("⑥ 目标用户再次调用目标接口，30 秒陈旧窗口内轮询至 200")
    void step6_targetUserAllowedAfterGrant() {
        long deadline = System.nanoTime() + STALE_WINDOW.toNanos();
        Integer got = null;
        IOException lastError = null;
        while (System.nanoTime() < deadline) {
            try {
                int status = postStatus(gateway() + TARGET_API_PATH, targetToken);
                if (status == 200) {
                    got = status;
                    break;
                }
                assertThat(status == 403 || status == 503)
                    .as("授权生效等待期只允许 403（陈旧拒绝）或 503（回源瞬时失败），实际 " + status)
                    .isTrue();
            } catch (IOException e) {
                lastError = e;
            }
            sleepQuiet(1000);
        }
        assertThat(got).withFailMessage(() -> "授权必须在 30 秒陈旧窗口内生效（服务端快照探针："
            + probeInternalSnapshot() + "；DB 关键行：" + dumpGrantChainRows() + "）").isEqualTo(200);
    }

    @Test
    @Order(7)
    @DisplayName("⑦ fail-closed 合并：停 access-service 断言 503 → 重启两服务（无人工改 DB/Redis）断言 200")
    void step7_failClosedThenRestartStillAllowed() throws Exception {
        // 等待 Gateway 快照 L1（≤15s）与 L2 过期，强制下次调用走回源——否则命中本地缓存
        // 放行缓存无法证明权限服务不可用时的行为
        sleepQuiet(SNAPSHOT_STALE_WAIT.toMillis());

        // 停 access-service：权限回源不可达必须固定 fail-closed 503，不得误放行
        accessService.destroy();
        int denied = postStatus(gateway() + TARGET_API_PATH, targetToken);
        assertThat(denied).as("权限服务不可用时 Gateway 必须 fail-closed 503（不误放行）").isEqualTo(503);

        // 重启 access-service 与 Gateway（无人工改 DB/Redis）：授权事实以 DB/Redis 为源，重启后仍 200
        int accessPort = accessService.port();
        accessService = startService("access-service", ACCESS_MAIN_CLASS,
            accessServiceArgs(accessPort), accessServiceEnv(),
            URI.create("http://localhost:" + accessPort + "/auth/captcha"), true);

        int gatewayPort = gatewayService.port();
        gatewayService.destroy();
        gatewayService = startService("gateway", GATEWAY_MAIN_CLASS,
            gatewayArgs(gatewayPort, accessPort), gatewayEnv(),
            URI.create("http://localhost:" + gatewayPort + "/actuator/health"), false);

        long deadline = System.nanoTime() + STALE_WINDOW.toNanos();
        Integer got = null;
        IOException lastError = null;
        while (System.nanoTime() < deadline) {
            try {
                int status = postStatus(gateway() + TARGET_API_PATH, targetToken);
                if (status == 200) {
                    got = status;
                    break;
                }
            } catch (IOException e) {
                lastError = e;
            }
            sleepQuiet(1000);
        }
        assertThat(got).as("重启后目标接口必须仍为 200（最后错误：" + lastError + "）").isEqualTo(200);
    }

    @Test
    @Order(8)
    @DisplayName("⑧ 撤权后自撤权响应单调计时轮询至 403，断言不超过 30 秒")
    void step8_revokeRestoresDenyWithin30s() {
        // 重新以管理员登录（原管理员令牌经两次服务重启仍有效，但按链路重新登录更贴近操作者语义）
        adminToken = realLogin("admin", BOOTSTRAP_ADMIN_PASSWORD);

        JsonNode plan = JSON.createObjectNode()
            .putNull("creates")
            .putNull("updates")
            .set("removes", JSON.createArrayNode().add(grantedPermissionId));
        JsonNode req = JSON.createObjectNode()
            .putNull("domainCode")
            .put("roleTypeCode", ROLE_TYPE)
            .put("roleExternalId", ROLE_EXTERNAL_ID)
            .set("plan", plan);
        postForData(gateway() + "/perm/api/perm/role-resource-permission/apply-grant-plan", adminToken, req);

        // 自撤权响应起单调计时
        long start = System.nanoTime();
        long deadline = start + STALE_WINDOW.toNanos();
        Integer denied = null;
        IOException lastError = null;
        while (System.nanoTime() < deadline) {
            try {
                int status = postStatus(gateway() + TARGET_API_PATH, targetToken);
                if (status == 403) {
                    denied = status;
                    break;
                }
                assertThat(status == 200 || status == 503)
                    .as("撤权等待期只允许 200（陈旧放行）或 503（失效竞争回源瞬时失败），实际 " + status)
                    .isTrue();
            } catch (IOException e) {
                lastError = e;
            }
            sleepQuiet(1000);
        }
        assertThat(denied).as("撤权后 30 秒内必须恢复 403（最后错误：" + lastError + "）").isEqualTo(403);
    }

    // ------------------------------------------------------------------
    // 子进程服务管理
    // ------------------------------------------------------------------

    /** 子进程句柄：端口与日志目录（失败时输出日志尾部辅助定位） */
    private static final class ServiceHandle {
        private final Process process;
        private final int port;
        private final Path workDir;

        private ServiceHandle(Process process, int port, Path workDir) {
            this.process = process;
            this.port = port;
            this.workDir = workDir;
        }

        int port() {
            return port;
        }

        void destroy() {
            process.destroy();
            try {
                if (!process.waitFor(15, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(10, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
            assertThat(process.isAlive()).as("子进程必须已退出").isFalse();
        }

        String logTail() {
            try {
                // 子进程 JVM 启动期错误走平台字符集（Windows GBK），应用日志为 UTF-8——宽容解码
                byte[] bytes = Files.readAllBytes(workDir.resolve("process.log"));
                String text = new String(bytes, StandardCharsets.UTF_8);
                List<String> lines = text.lines().toList();
                return String.join("\n", lines.subList(Math.max(0, lines.size() - 40), lines.size()));
            } catch (IOException e) {
                return "<log unreadable: " + e.getMessage() + ">";
            }
        }
    }

    /** access-service 子进程启动参数（首启与步骤⑦重启共用） */
    private static List<String> accessServiceArgs(int port) {
        return List.of(
            "--server.port=" + port,
            "--spring.datasource.url=" + postgres.getJdbcUrl() + "?stringtype=unspecified",
            "--spring.datasource.username=" + postgres.getUsername(),
            "--spring.datasource.password=" + postgres.getPassword(),
            "--spring.data.redis.host=" + redis.getHost(),
            "--spring.data.redis.port=" + redis.getMappedPort(6379),
            "--spring.data.redis.password=" + REDIS_PASSWORD,
            "--spring.config.import=optional:classpath:/e2e-nope.yml",
            "--spring.cloud.nacos.config.enabled=false",
            "--spring.cloud.nacos.config.import-check.enabled=false",
            "--spring.cloud.nacos.discovery.enabled=false",
            "--accessmesh.sync.scheduler.enabled=false",
            "--file.storage.path=files",
            // 共享类路径带入了 spring-cloud-gateway 自动配置：servlet 上下文会触发
            // MvcFoundOnClasspathException 与 GatewayRedisAutoConfiguration 缺 Bean 失败——
            // enabled=false 只关主装配，Redis/发现/CORS 等独立自动配置需显式排除（本服务不是网关）
            "--spring.cloud.gateway.enabled=false",
            "--spring.autoconfigure.exclude=org.springframework.cloud.gateway.config.GatewayRedisAutoConfiguration,"
                + "org.springframework.cloud.gateway.config.GatewayClassPathWarningAutoConfiguration,"
                + "org.springframework.cloud.gateway.discovery.GatewayDiscoveryClientAutoConfiguration,"
                + "org.springframework.cloud.gateway.config.SimpleUrlHandlerMappingGlobalCorsAutoConfiguration,"
                + "org.springframework.cloud.gateway.config.GatewayReactiveLoadBalancerClientAutoConfiguration,"
                + "org.springframework.cloud.gateway.config.GatewayReactiveOAuth2AutoConfiguration,"
                + "org.springframework.cloud.gateway.config.LocalResponseCacheAutoConfiguration,"
                + "org.springframework.cloud.gateway.config.GatewayNoLoadBalancerClientAutoConfiguration,"
                + "org.springframework.cloud.gateway.config.GatewayMetricsAutoConfiguration,"
                + "org.springframework.cloud.gateway.config.GatewayResilience4JCircuitBreakerAutoConfiguration,"
                // 共享类路径同时有 reactor 版 sa-token starter（网关用）：其注册器同样无条件生效，
                // servlet 侧会出现两个 SaTokenContext Bean（注入歧义启动失败），排除 reactor 版
                + "cn.dev33.satoken.reactor.spring.SaTokenContextRegister",
            // 共享类路径带入了 perm-gateway starter（网关鉴权插件），非网关侧关闭
            "--perm.gateway.enabled=false");
    }

    private static Map<String, String> accessServiceEnv() {
        return Map.of(
            "ACCESS_BOOTSTRAP_ENABLED", "true",
            "ACCESS_BOOTSTRAP_ADMIN_PASSWORD", BOOTSTRAP_ADMIN_PASSWORD,
            "JWT_SECRET_KEY", JWT_SECRET,
            "ACCESSMESH_SIGNATURE_SECRET", SIGNATURE_SECRET,
            "PERM_INTERNAL_SECRET", INTERNAL_SECRET);
    }

    /** Gateway 子进程启动参数（首启与步骤⑦重启共用） */
    private static List<String> gatewayArgs(int port, int accessPort) {
        return List.of(
            // 共享类路径含 webmvc（access-service test 依赖），必须显式 reactive
            "--spring.main.web-application-type=reactive",
            "--server.port=" + port,
            "--spring.data.redis.host=" + redis.getHost(),
            "--spring.data.redis.port=" + redis.getMappedPort(6379),
            "--spring.data.redis.password=" + REDIS_PASSWORD,
            "--spring.config.import=optional:classpath:/e2e-nope.yml",
            "--spring.cloud.nacos.config.enabled=false",
            "--spring.cloud.nacos.config.import-check.enabled=false",
            "--spring.cloud.nacos.discovery.enabled=false",
            // 共享类路径带入了 access-service 依赖树的 Redisson/sa-token-servlet/数据源系自动配置：
            // 生产 Gateway 无 Redisson、只有 reactor 版 sa-token、无 JDBC/MyBatis——排除后保持生产装配面
            "--spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV2,"
                + "cn.ac.fage.accessmesh.common.cache.RedissonCacheAutoConfiguration,"
                + "cn.dev33.satoken.spring.SaTokenContextRegister,"
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration,"
                + "com.mybatisflex.spring.boot.MybatisFlexAutoConfiguration",
            // 路由与权限回源 WebClient 的 lb://access-service 解析目标（免 Nacos 直连）
            "--spring.cloud.discovery.client.simple.instances.access-service[0].uri=http://localhost:" + accessPort);
    }

    private static Map<String, String> gatewayEnv() {
        return Map.of(
            "ACCESSMESH_SIGNATURE_SECRET", SIGNATURE_SECRET,
            "PERM_INTERNAL_SECRET", INTERNAL_SECRET);
    }

    /**
     * 以子进程启动一个服务并等待就绪端点返回 200。
     *
     * <p>类路径取当前测试 JVM 的 java.class.path 并过滤掉 test-classes——子进程只应看到
     * 生产 classes 与依赖；两服务 main class 均在该类路径上（gateway 自身 + access-service
     * test 依赖）。注意两点：① 本地仓库中的 access-service jar 已被 spring-boot 插件
     * repackage 为 fat jar（主类在 BOOT-INF 下、不可经 -cp 加载），须显式加入 reactor 布局
     * 下的 ../access-service/target/classes；② 共享类路径上两服务的 application.yml 同名——
     * access-service 子进程必须把自己的 classes 目录<b>前置</b>（classpath 首个命中），
     * 否则会读到 gateway 的 application.yml（缺 sa-token.jwt-secret-key 等占位符即启动失败）。
     * {@code prependAccessClasses} 仅 access-service 子进程为 true。
     */
    private static ServiceHandle startService(String name, String mainClass, List<String> args,
                                              Map<String, String> env, URI readinessUrl,
                                              boolean prependAccessClasses) throws Exception {
        String javaBin = Path.of(System.getProperty("java.home"), "bin",
            System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java").toString();
        if (!Files.exists(Path.of(ACCESS_SERVICE_CLASSES_DIR))) {
            throw new IllegalStateException("缺少 access-service 生产 classes（" + ACCESS_SERVICE_CLASSES_DIR
                + "）——请先构建上游模块（如 mvn -pl access-service -am install 或全量构建）");
        }
        String baseClasspath = filterOutTestClasses(System.getProperty("java.class.path"));
        String childClasspath = prependAccessClasses
            ? ACCESS_SERVICE_CLASSES_DIR + java.io.File.pathSeparator + baseClasspath
            : baseClasspath + java.io.File.pathSeparator + ACCESS_SERVICE_CLASSES_DIR;
        int port = parsePort(args);

        Path workDir = Path.of("target", "e2e", name + "-" + System.nanoTime());
        Files.createDirectories(workDir);
        Path logFile = workDir.resolve("process.log");

        List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.add("-Xms128m");
        command.add("-Xmx512m");
        command.add("-Dfile.encoding=UTF-8");
        command.add("-cp");
        command.add(childClasspath);
        command.add(mainClass);
        command.addAll(args);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir.toFile());
        pb.environment().putAll(env);
        pb.redirectErrorStream(true);
        pb.redirectOutput(logFile.toFile());
        Process process = pb.start();

        ServiceHandle handle = new ServiceHandle(process, port, workDir);
        waitReady(name, readinessUrl, handle);
        return handle;
    }

    /** 就绪等待：端点轮询至 HTTP 200（/auth/captcha 走 POST，其余按 GET health）；进程提前退出立即失败并带日志尾部 */
    private static void waitReady(String name, URI url, ServiceHandle handle) throws InterruptedException {
        boolean postProbe = "/auth/captcha".equals(url.getPath());
        long deadline = System.nanoTime() + Duration.ofMinutes(4).toNanos();
        IOException lastError = null;
        while (System.nanoTime() < deadline) {
            if (!handle.process.isAlive()) {
                throw new IllegalStateException(name + " 子进程启动即退出（端口 " + handle.port + "），日志尾部：\n"
                    + handle.logTail());
            }
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(5));
                if (postProbe) {
                    builder.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8));
                } else {
                    builder.GET();
                }
                HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return;
                }
                lastError = new IOException("HTTP " + response.statusCode());
            } catch (IOException e) {
                lastError = e;
            }
            Thread.sleep(1000);
        }
        handle.destroy();
        throw new IllegalStateException(name + " 就绪超时（最后错误：" + lastError + "），日志尾部：\n"
            + handle.logTail());
    }

    /** 轮询 bootstrap 首管理员落库（web 就绪可能早于 ApplicationRunner 种子完成） */
    private static void waitAdminSeeded() throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofMinutes(2).toNanos();
        while (System.nanoTime() < deadline) {
            try (var conn = java.sql.DriverManager.getConnection(
                postgres.getJdbcUrl() + "?stringtype=unspecified", postgres.getUsername(), postgres.getPassword());
                 var st = conn.createStatement();
                 var rs = st.executeQuery(
                     "SELECT count(*) FROM sys_user WHERE username = 'admin' AND delete_flag = 0")) {
                if (rs.next() && rs.getInt(1) == 1) {
                    return;
                }
            } catch (java.sql.SQLException e) {
                // 容器就绪瞬间连接抖动可容忍，下一轮重试
            }
            Thread.sleep(1000);
        }
        throw new IllegalStateException("bootstrap 首管理员 2 分钟内未落库");
    }

    /** 从 --server.port=N 参数解析端口 */
    private static int parsePort(List<String> args) {
        return args.stream()
            .filter(a -> a.startsWith("--server.port="))
            .mapToInt(a -> Integer.parseInt(a.substring("--server.port=".length())))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("缺少 --server.port 参数"));
    }

    /** java.class.path 过滤 test-classes 条目（子进程仅携带生产 classes 与依赖） */
    private static String filterOutTestClasses(String classpath) {
        List<String> kept = new ArrayList<>();
        for (String entry : classpath.split(java.io.File.pathSeparator)) {
            String normalized = entry.replace('\\', '/');
            if (!normalized.endsWith("/test-classes")) {
                kept.add(entry);
            }
        }
        return String.join(java.io.File.pathSeparator, kept);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress("localhost", 0));
            return socket.getLocalPort();
        }
    }

    // ------------------------------------------------------------------
    // HTTP / Redis 辅助
    // ------------------------------------------------------------------

    private static String gateway() {
        return "http://localhost:" + gatewayService.port();
    }

    /** POST + JSON Body；返回 HTTP 状态码（用于 403/503 断言） */
    private static int postStatus(String url, String bearerToken) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8));
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        try {
            HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return response.statusCode();
        } catch (HttpTimeoutException e) {
            throw new IOException("请求超时: " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("请求被中断: " + url, e);
        }
    }

    /** POST + JSON Body；断言 HTTP 200 + 信封 code=200，返回 data 节点（失败信息含响应体） */
    private static JsonNode postForData(String url, String bearerToken, JsonNode body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        String raw;
        try {
            HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            raw = response.body();
            assertThat(response.statusCode()).as("管理链路 " + url + " 必须 HTTP 200，实际 " + response.statusCode()
                + "，响应：" + raw).isEqualTo(200);
        } catch (IOException e) {
            throw new IllegalStateException("管理链路请求失败: " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("管理链路请求被中断: " + url, e);
        }
        JsonNode envelope = null;
        try {
            envelope = JSON.readTree(raw);
        } catch (IOException e) {
            throw new IllegalStateException("响应不是合法 JSON: " + raw, e);
        }
        assertThat(envelope.path("code").asInt())
            .as("管理链路 " + url + " 信封 code 必须 200，响应：" + raw).isEqualTo(200);
        return envelope.path("data");
    }

    /**
     * 真实登录：/auth/captcha 签发 → 从 Redis 按 captchaId 只读取码 → /auth/login。
     * 验证码与令牌均为服务真实签发校验，测试不注入任何凭据。
     */
    private static String realLogin(String username, String password) {
        JsonNode captcha = postForData(gateway() + "/auth/captcha", null, JSON.createObjectNode());
        String captchaId = captcha.path("captchaId").asText();
        assertThat(captchaId).as("验证码必须真实签发").isNotBlank();
        String code = readCaptchaFromRedis(captchaId);
        assertThat(code).as("Redis 必须存有验证码答案（真实签发链路）").isNotBlank();

        JsonNode login = postForData(gateway() + "/auth/login", null,
            JSON.createObjectNode()
                .put("tenantId", TENANT_ID)
                .put("username", username)
                .put("password", password)
                .put("captchaId", captchaId)
                .put("captchaCode", code)
                .put("clientId", CLIENT_ID));
        return login.path("accessToken").asText();
    }

    /**
     * 授权链路关键行转储（失败诊断用）：主体/角色/绑定/授权/映射/API 资源六表——
     * 定位服务端快照为空时的事实断点。
     */
    private static String dumpGrantChainRows() {
        String[] queries = {
            "SELECT 'user' t, id, user_type::text, external_id, enabled::text, delete_flag FROM abstract_user WHERE id=" + targetUserId,
            "SELECT 'role' t, id, role_type::text, external_id, status::text, delete_flag FROM abstract_role WHERE external_id='" + ROLE_EXTERNAL_ID + "'",
            "SELECT 'user_role' t, id, abstract_user_id, target_id, relation_id, delete_flag FROM user_role WHERE abstract_user_id=" + targetUserId,
            "SELECT 'grant' t, id, abstract_role_id, resource_entity_id, granted_bits, resource_type, scope_all, condition_id, delete_flag"
                + " FROM role_resource_permission WHERE abstract_role_id=(SELECT id FROM abstract_role WHERE external_id='" + ROLE_EXTERNAL_ID + "')",
            "SELECT 'mapping' t, id, resource_entity_id, service_code, http_method, path_pattern, match_order, enabled, delete_flag"
                + " FROM resource_api_mapping WHERE path_pattern='" + TARGET_API_PATH + "'",
            "SELECT 'api_res' t, id, code, status, delete_flag FROM resource_entity WHERE code='" + TARGET_API_CODE + "'"
        };
        StringBuilder sb = new StringBuilder();
        try (var conn = java.sql.DriverManager.getConnection(
            postgres.getJdbcUrl() + "?stringtype=unspecified", postgres.getUsername(), postgres.getPassword())) {
            for (String q : queries) {
                try (var st = conn.createStatement(); var rs = st.executeQuery(q)) {
                    var md = rs.getMetaData();
                    while (rs.next()) {
                        sb.append('[');
                        for (int i = 1; i <= md.getColumnCount(); i++) {
                            sb.append(md.getColumnLabel(i)).append('=').append(rs.getString(i)).append(' ');
                        }
                        sb.append("] ");
                    }
                }
            }
        } catch (Exception e) {
            sb.append("dump failed: ").append(e.getMessage());
        }
        return sb.toString();
    }

    /** 只读读取验证码答案（键 captcha:{captchaId}，与 AuthServiceImpl 键契约一致） */
    private static String readCaptchaFromRedis(String captchaId) {
        RedisURI uri = RedisURI.builder()
            .withHost(redis.getHost())
            .withPort(redis.getMappedPort(6379))
            .withPassword(REDIS_PASSWORD.toCharArray())
            .build();
        try (RedisClient client = RedisClient.create(uri);
             StatefulRedisConnection<String, String> connection = client.connect()) {
            return connection.sync().get("captcha:" + captchaId);
        }
    }

    /**
     * 服务端快照探针（失败诊断用）：以内部密钥直连 access-service 的
     * /api/perm/auth/interface-snapshot，返回目标用户在 access-service 下的 allowedApis——
     * 用于区分「服务端授权未生效」与「Gateway 快照未刷新」。
     */
    private static String probeInternalSnapshot() {
        try {
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create("http://localhost:" + accessService.port() + "/api/perm/auth/interface-snapshot"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("X-Tenant-Id", TENANT_ID)
                .header("X-Internal-Secret", INTERNAL_SECRET)
                .POST(HttpRequest.BodyPublishers.ofString(
                    "{\"subjectTypeCode\":\"LOCAL_USER\",\"subjectExternalId\":\"" + targetUserId
                        + "\",\"serviceCode\":\"access-service\"}", StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            return "HTTP " + response.statusCode() + " " + (body != null ? body.substring(0, Math.min(body.length(), 400)) : "<empty>");
        } catch (Exception e) {
            return "probe failed: " + e;
        }
    }

    /** 在资源树中按 code 定位 API 资源 id */
    private static long findApiResourceId(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return -1;
        }
        if (TARGET_API_CODE.equals(node.path("code").asText())) {
            return node.path("id").asLong();
        }
        for (JsonNode child : node.path("children")) {
            long found = findApiResourceId(child);
            if (found > 0) {
                return found;
            }
        }
        return -1;
    }

    private static void sleepQuiet(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
