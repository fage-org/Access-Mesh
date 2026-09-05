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
 * example-service 受保护接口接入 E2E（T-API-001，Gateway 主线验收链路）。
 *
 * <p>固定 6 步顺序：① 空库 bootstrap 首管理员真实登录 → ② 创建目标用户与空权限
 * BASIC_ROLE 并分配 → ③ 为 example 接口真实创建 API 资源实体与映射（bootstrap 固定图
 * 不含 example 资源）→ ④ 目标用户经 Gateway 调用 /example/api/example/demo/hello 断言
 * 403（授权前拒绝）→ ⑤ 授予 BASIC_ROLE 该 API 的 API:ACCESS → ⑥ 30 秒陈旧窗口内轮询至
 * HTTP 200 + 信封 code=200 + Gateway 身份头回显（userId=目标用户），并验证授权后参数
 * 非法仍返回信封 code=30001（业务错误不因放行被吞）。
 *
 * <p><b>拓扑</b>：PG/Redis 为 Testcontainers；access-service、Gateway 与 example-service
 * 以<b>子进程</b>（独立 JVM、固定随机端口）从本测试的 java.class.path 启动（模式与
 * {@link BasicRoleGrantVerticalSliceE2EIT} 一致）。Gateway 路由 /example/**（StripPrefix=1、
 * serviceCode=example-service）经 SimpleDiscoveryClient 静态实例直连 example-service 子进程；
 * API 映射 pathPattern 按 Gateway 外部路径口径（/example/api/example/demo/hello）创建。
 * 业务服务不做服务内鉴权、不消费 perm-client——保护完全由 Gateway 承担（规范 §2.4）。
 *
 * <p>example-service 无 actuator 依赖，就绪探针直接 POST /api/example/demo/hello：
 * 就绪后无身份头返回 HTTP 200 + 信封 code=30002（BizException 映射 HTTP 200）。
 *
 * <p>注意：本 IT 需在 maven（surefire/IDE 传递完整 java.class.path）下执行；
 * {@code @Tag("testcontainers")} 使其只随容器门控 execution 运行。
 */
@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExampleProtectedApiE2EIT {

    private static final Path DDL_PATH = Path.of("..", "docs", "design", "schema", "access-service.sql");
    /** reactor 布局下各服务生产 classes（本地仓库 jar 为 fat jar，不可 -cp 加载）；必须绝对路径 */
    private static final String ACCESS_SERVICE_CLASSES_DIR =
        Path.of("..", "access-service", "target", "classes").toAbsolutePath().toString();
    private static final String EXAMPLE_SERVICE_CLASSES_DIR =
        Path.of("..", "example-service", "target", "classes").toAbsolutePath().toString();
    private static final String BOOTSTRAP_ADMIN_PASSWORD = "E2E-Bootstrap-Admin-2026!";
    private static final String JWT_SECRET = "e2e-jwt-secret-0123456789abcdef0123456789abcdef";
    private static final String SIGNATURE_SECRET = "e2e-signature-secret-0123456789abcdef";
    private static final String INTERNAL_SECRET = "e2e-internal-secret-0123456789abcdef";

    private static final String TENANT_ID = "1";
    private static final String CLIENT_ID = "admin-web";
    private static final String ACCESS_MAIN_CLASS = "cn.ac.fage.accessmesh.access.AccessServiceApplication";
    private static final String GATEWAY_MAIN_CLASS = "cn.ac.fage.accessmesh.gateway.GatewayApplication";
    private static final String EXAMPLE_MAIN_CLASS = "cn.ac.fage.accessmesh.example.ExampleServiceApplication";

    /** E2E 目标接口（外部路径口径：Gateway 匹配 StripPrefix 前路径，example 内部路径为去 /example 前缀） */
    private static final String TARGET_API_METHOD = "POST";
    private static final String TARGET_API_PATH = "/example/api/example/demo/hello";
    /** API 资源业务码：service-config 声明通道 DTO @Pattern（^[a-zA-Z0-9_:.-]+$）禁斜杠，冒号段式 */
    private static final String TARGET_API_RESOURCE_CODE = "example:demo:hello";

    private static final String ROLE_TYPE = "BASIC_ROLE";
    private static final String ROLE_EXTERNAL_ID = "e2e-example-role";
    private static final String TARGET_USERNAME = "e2e-example-target";

    /** 30 秒陈旧窗口（授权生效上界，超时即失败；与 BasicRoleGrant E2E 同口径） */
    private static final Duration STALE_WINDOW = Duration.ofSeconds(30);

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("access_db")
        .withUsername("perm")
        .withPassword("perm");

    private static final String REDIS_PASSWORD = "accessmesh-dev";

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withCommand("redis-server", "--requirepass", REDIS_PASSWORD)
        .withExposedPorts(6379);

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
    private static final ObjectMapper JSON = new ObjectMapper();

    private static ServiceHandle accessService;
    private static ServiceHandle gatewayService;
    private static ServiceHandle exampleService;

    private static String adminToken;
    private static long targetUserId;
    private static String targetInitialPassword;
    private static String targetToken;
    /** 授权（⑤）响应到达的单调时刻——⑥的 30 秒陈旧窗口自该时刻起算 */
    private static long grantResponseAtNanos;

    @BeforeAll
    static void bootStack() throws Exception {
        String ddl = Files.readString(DDL_PATH, StandardCharsets.UTF_8);
        try (var conn = java.sql.DriverManager.getConnection(
            postgres.getJdbcUrl() + "?stringtype=unspecified", postgres.getUsername(), postgres.getPassword());
             var st = conn.createStatement()) {
            st.execute(ddl);
            // 环境种子：example-service 接入服务注册（service-config/sync 的前置——服务配置须已存在）；
            // 正式接入由管理员经 service-config 管理接口维护。API 资源/映射由第③步
            // service-config/sync 接口声明通道自动创建（T-PERM-052 后 API 类型恒 MANAGED，
            // resource-entity/sync 对其一律拒绝，旧 syncTypes.resourceTypeCodes 白名单已退役）
            st.execute("INSERT INTO service_config (tenant_id, service_code, name, status) VALUES ("
                + "1, 'example-service', 'Example Service', 1)");
        }

        int accessPort = freePort();
        int gatewayPort = freePort();
        int gatewayMgmtPort = freePort();
        int examplePort = freePort();

        accessService = startService("access-service", ACCESS_MAIN_CLASS,
            accessServiceArgs(accessPort), accessServiceEnv(),
            URI.create("http://localhost:" + accessPort + "/auth/captcha"), ACCESS_SERVICE_CLASSES_DIR);
        waitAdminSeeded();

        // ACCESSMESH_SIGNATURE_SECRET 与 Gateway 同源：example 的 GatewaySignatureFilter
        // 复算 Gateway 注入的 X-User-Signature（身份信任链示例）
        exampleService = startService("example-service", EXAMPLE_MAIN_CLASS,
            exampleServiceArgs(examplePort), Map.of("JWT_SECRET_KEY", JWT_SECRET,
                "ACCESSMESH_SIGNATURE_SECRET", SIGNATURE_SECRET),
            URI.create("http://localhost:" + examplePort + "/api/example/demo/hello"),
            EXAMPLE_SERVICE_CLASSES_DIR);

        gatewayService = startService("gateway", GATEWAY_MAIN_CLASS,
            gatewayArgs(gatewayPort, gatewayMgmtPort, accessPort, examplePort), gatewayEnv(),
            URI.create("http://127.0.0.1:" + gatewayMgmtPort + "/actuator/health"), null);
    }

    @AfterAll
    static void tearDown() {
        if (gatewayService != null) {
            gatewayService.destroy();
        }
        if (exampleService != null) {
            exampleService.destroy();
        }
        if (accessService != null) {
            accessService.destroy();
        }
    }

    // ------------------------------------------------------------------
    // 固定 6 步
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
        JsonNode created = postForData(gateway() + "/admin/user/create", adminToken,
            JSON.createObjectNode().put("username", TARGET_USERNAME).put("name", "E2E Example Target"));
        targetUserId = created.path("id").asLong();
        targetInitialPassword = created.path("initialPassword").asText();
        assertThat(targetUserId).as("目标用户必须取得新 ID").isPositive();
        assertThat(targetInitialPassword).as("initialPassword 必须在创建响应中返回一次").isNotBlank();

        JsonNode role = postForData(gateway() + "/perm/api/perm/abstract-role/create", adminToken,
            JSON.createObjectNode()
                .put("roleTypeCode", ROLE_TYPE)
                .put("externalId", ROLE_EXTERNAL_ID)
                .put("name", "E2E Example Role"));
        assertThat(role.path("externalId").asText())
            .as("BASIC_ROLE 必须按固定业务键创建").isEqualTo(ROLE_EXTERNAL_ID);

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
    @DisplayName("③ example 经 service-config 接口声明通道注册 API 资源与 Gateway 映射（FULL）")
    void step3_createExampleApiResourceAndMapping() {
        // API 资源的唯一事实入口是 service-config/sync 接口声明通道（api-contract §6.3）：FULL
        // 上报即完整事实来源，自动创建 API 资源与 resource_api_mapping（owner=example-service、
        // maintainSource=SERVICE_SYNC、pathPattern=basePath+path）。T-PERM-052 类型级所有权后
        // API 类型恒 MANAGED——resource-entity/sync 通道对其一律 RESOURCE_TYPE_OWNERSHIP_DENIED，
        // 旧 syncTypes.resourceTypeCodes 白名单已退役，经 Gateway 走管理面（bootstrap 固定图含本路径）
        JsonNode syncResp = postForData(gateway() + "/perm/api/perm/service-config/sync", adminToken,
            JSON.createObjectNode()
                .put("serviceCode", "example-service")
                .put("basePath", "")
                .put("syncMode", "FULL")
                .set("groups", JSON.createArrayNode().add(JSON.createObjectNode()
                    .put("groupCode", "demo")
                    .put("groupName", "示例接口")
                    .set("apis", JSON.createArrayNode().add(JSON.createObjectNode()
                        .put("name", "example 演示问候接口")
                        .put("httpMethod", TARGET_API_METHOD)
                        .put("path", TARGET_API_PATH)
                        .put("operationCode", "ACCESS")
                        .put("resourceCode", TARGET_API_RESOURCE_CODE)
                        .put("description", "E2E 目标接口"))))));
        assertThat(syncResp.path("createdResources").asLong())
            .as("接口声明通道必须创建 API 资源，响应：" + syncResp).isPositive();
        assertThat(syncResp.path("createdMappings").asLong())
            .as("接口声明通道必须自动创建 Gateway 映射，响应：" + syncResp).isPositive();

        // 经授权页同款资源树定位新资源 id（bootstrap 固定图含 tree），树按资源类型返回多棵
        JsonNode treeData = postForData(gateway() + "/perm/api/perm/resource-entity/tree", adminToken,
            JSON.createObjectNode().putNull("resourceTypeCode").putNull("domainCode"));
        long resourceId = 0;
        for (JsonNode tree : treeData.path("items")) {
            resourceId = findApiResourceId(tree.path("root"));
            if (resourceId > 0) {
                break;
            }
        }
        assertThat(resourceId)
            .as("接口声明注册的 example API 资源必须可见，实际树响应：%s", treeData).isPositive();
    }

    /** 在资源树中按 code 定位 API 资源 id */
    private static long findApiResourceId(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return -1;
        }
        if (TARGET_API_RESOURCE_CODE.equals(node.path("code").asText())) {
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

    @Test
    @Order(4)
    @DisplayName("④ 目标用户凭 initialPassword 真实登录后经 Gateway 调用 example 接口，断言 403（授权前拒绝）")
    void step4_targetUserDeniedBeforeGrant() throws IOException {
        targetToken = realLogin(TARGET_USERNAME, targetInitialPassword);
        assertThat(targetToken).as("目标用户必须能以 initialPassword 取得真实令牌").isNotBlank();

        int status = postStatus(gateway() + TARGET_API_PATH, targetToken, "{\"name\":\"E2E\"}");
        assertThat(status).as("授权前 example 接口必须真实拒绝（403，非 401——令牌有效）").isEqualTo(403);
    }

    @Test
    @Order(5)
    @DisplayName("⑤ 管理员授予 BASIC_ROLE example API 的 API:ACCESS（apply-grant-plan，与授权页同源写入口）")
    void step5_grantApiAccess() {
        var key = JSON.createObjectNode();
        key.put("resourceTypeCode", "API");
        key.put("resourceCode", TARGET_API_RESOURCE_CODE);
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
        grantResponseAtNanos = System.nanoTime();
        assertThat(items.isArray() && items.size() == 1)
            .as("授权计划必须产生恰好一条授权记录").isTrue();
    }

    @Test
    @Order(6)
    @DisplayName("⑥ 授权响应起 30 秒内轮询至真实成功：信封 200 + Gateway 身份回显；参数非法仍 30001")
    void step6_targetUserAllowedAfterGrant() throws IOException {
        long deadline = grantResponseAtNanos + STALE_WINDOW.toNanos();
        JsonNode successEnvelope = null;
        IOException lastError = null;
        while (System.nanoTime() < deadline) {
            try {
                EnvelopeResult r = postEnvelope(gateway() + TARGET_API_PATH, targetToken, "{\"name\":\"E2E\"}");
                if (r.status() == 200) {
                    assertThat(System.nanoTime())
                        .as("授权生效响应必须在 30 秒窗口内到达")
                        .isLessThanOrEqualTo(deadline);
                    successEnvelope = parseEnvelope(r);
                    break;
                }
                assertThat(r.status() == 403 || r.status() == 503)
                    .as("授权生效等待期只允许 403（陈旧拒绝）或 503（回源瞬时失败），实际 " + r.status())
                    .isTrue();
            } catch (IOException e) {
                lastError = e;
            }
            sleepQuiet(1000);
        }
        final IOException finalLastError = lastError;
        assertThat(successEnvelope)
            .withFailMessage(() -> "授权必须在 30 秒陈旧窗口内真实生效（最后错误：" + finalLastError + "）")
            .isNotNull();

        // 信封级成功：code=200 + 问候语 + Gateway 注入身份回显（请求真实穿过 Gateway 到达业务服务）
        assertThat(successEnvelope.path("code").asInt())
            .as("example 接口必须信封 code=200，响应：" + successEnvelope).isEqualTo(200);
        JsonNode data = successEnvelope.path("data");
        assertThat(data.path("greeting").asText())
            .as("必须返回业务问候语，响应：" + successEnvelope).isEqualTo("hello, E2E");
        assertThat(data.path("userId").asLong())
            .as("身份回显必须是 Gateway 注入的目标用户 ID（X-User-Id），响应：" + successEnvelope)
            .isEqualTo(targetUserId);
        assertThat(data.path("tenantId").asText())
            .as("身份回显必须是 Gateway 注入的租户 ID（X-Tenant-Id）").isEqualTo(TENANT_ID);

        // 授权放行后业务参数校验仍生效：name 空白 → HTTP 200 + 信封 code=30001（example 业务域错误码段）
        EnvelopeResult invalid = postEnvelope(gateway() + TARGET_API_PATH, targetToken, "{\"name\":\"  \"}");
        assertThat(invalid.status()).as("参数非法为业务错误，HTTP 仍须 200").isEqualTo(200);
        assertThat(parseEnvelope(invalid).path("code").asInt())
            .as("参数非法必须映射为 example 业务域错误码 30001").isEqualTo(30001);

        // 身份信任链：绕过 Gateway 直连 example 携伪造身份头 → 签名校验拒绝信封 code=30003
        // （GatewaySignatureFilter 复算 X-User-Signature，无有效签名一律 fail-closed）
        EnvelopeResult forged = postEnvelope(
            "http://localhost:" + exampleService.port() + "/api/example/demo/hello",
            null, "{\"name\":\"E2E\"}",
            Map.of("X-User-Id", String.valueOf(targetUserId), "X-Tenant-Id", TENANT_ID));
        assertThat(forged.status()).as("伪造身份头为业务错误，HTTP 仍须 200").isEqualTo(200);
        assertThat(parseEnvelope(forged).path("code").asInt())
            .as("直连伪造身份头必须被签名校验拒绝（30003）").isEqualTo(30003);
    }

    // ------------------------------------------------------------------
    // 子进程服务管理
    // ------------------------------------------------------------------

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
                byte[] bytes = Files.readAllBytes(workDir.resolve("process.log"));
                String text = new String(bytes, StandardCharsets.UTF_8);
                List<String> lines = text.lines().toList();
                return String.join("\n", lines.subList(Math.max(0, lines.size() - 40), lines.size()));
            } catch (IOException e) {
                return "<log unreadable: " + e.getMessage() + ">";
            }
        }
    }

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
            "--file.storage.path=" + Path.of("files").toAbsolutePath(),
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
                + "cn.dev33.satoken.reactor.spring.SaTokenContextRegister",
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

    /**
     * example-service 子进程启动参数。共享类路径（gateway 测试类路径）带入了 gateway /
     * access-service 依赖树的自动配置：Gateway 系（本服务不是网关）、Redisson（无 Redis 消费）、
     * JDBC/MyBatis 系（无数据源）均须显式排除，保持「瘦身 POM」的生产装配面；本服务自身
     * application.yml 已关缓存装配（accessmesh.cache.enabled=false，caffeine 为 common optional 依赖）。
     */
    private static List<String> exampleServiceArgs(int port) {
        return List.of(
            "--spring.main.web-application-type=servlet",
            "--server.port=" + port,
            "--spring.config.import=optional:classpath:/e2e-nope.yml",
            "--spring.cloud.nacos.config.enabled=false",
            "--spring.cloud.nacos.config.import-check.enabled=false",
            "--spring.cloud.nacos.discovery.enabled=false",
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
                + "cn.dev33.satoken.reactor.spring.SaTokenContextRegister,"
                + "org.redisson.spring.starter.RedissonAutoConfigurationV2,"
                + "cn.ac.fage.accessmesh.common.cache.RedissonCacheAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration,"
                + "com.mybatisflex.spring.boot.MybatisFlexAutoConfiguration");
    }

    private static List<String> gatewayArgs(int port, int managementPort, int accessPort, int examplePort) {
        return List.of(
            "--spring.main.web-application-type=reactive",
            "--server.port=" + port,
            "--management.server.port=" + managementPort,
            "--spring.data.redis.host=" + redis.getHost(),
            "--spring.data.redis.port=" + redis.getMappedPort(6379),
            "--spring.data.redis.password=" + REDIS_PASSWORD,
            "--spring.config.import=optional:classpath:/e2e-nope.yml",
            "--spring.cloud.nacos.config.enabled=false",
            "--spring.cloud.nacos.config.import-check.enabled=false",
            "--spring.cloud.nacos.discovery.enabled=false",
            "--spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV2,"
                + "cn.ac.fage.accessmesh.common.cache.RedissonCacheAutoConfiguration,"
                + "cn.dev33.satoken.spring.SaTokenContextRegister,"
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration,"
                + "com.mybatisflex.spring.boot.MybatisFlexAutoConfiguration",
            // /example/** 路由的 lb://example-service 解析目标（免 Nacos 直连子进程）
            "--spring.cloud.discovery.client.simple.instances.access-service[0].uri=http://localhost:" + accessPort,
            "--spring.cloud.discovery.client.simple.instances.example-service[0].uri=http://localhost:" + examplePort);
    }

    private static Map<String, String> gatewayEnv() {
        return Map.of(
            "ACCESSMESH_SIGNATURE_SECRET", SIGNATURE_SECRET,
            "PERM_INTERNAL_SECRET", INTERNAL_SECRET);
    }

    /**
     * 以子进程启动一个服务并等待就绪端点返回 200。
     *
     * <p>类路径取当前测试 JVM 的 java.class.path 并过滤 test-classes；各服务生产 classes
     * 目录前置（application.yml 同名，须让本服务的配置优先命中）；example-service 就绪
     * 探针为其业务接口（无 actuator 依赖，无身份头时 BizException 映射 HTTP 200）。
     */
    private static ServiceHandle startService(String name, String mainClass, List<String> args,
                                              Map<String, String> env, URI readinessUrl,
                                              String prependClassesDir) throws Exception {
        String javaBin = Path.of(System.getProperty("java.home"), "bin",
            System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java").toString();
        if (!Files.exists(Path.of(ACCESS_SERVICE_CLASSES_DIR))) {
            throw new IllegalStateException("缺少 access-service 生产 classes（" + ACCESS_SERVICE_CLASSES_DIR
                + "）——请先构建上游模块（如 mvn -pl access-service -am install 或全量构建）");
        }
        if (!Files.exists(Path.of(EXAMPLE_SERVICE_CLASSES_DIR))) {
            throw new IllegalStateException("缺少 example-service 生产 classes（" + EXAMPLE_SERVICE_CLASSES_DIR
                + "）——请先构建上游模块（如 mvn -pl example-service -am install 或全量构建）");
        }
        String baseClasspath = filterOutTestClasses(System.getProperty("java.class.path"));
        String childClasspath = prependClassesDir != null
            ? prependClassesDir + java.io.File.pathSeparator + baseClasspath
            : baseClasspath;
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

    private static void waitReady(String name, URI url, ServiceHandle handle) throws InterruptedException {
        // /auth/captcha 与 example 就绪探针走 POST（example 探针带合法 name，无身份头 → 200）；
        // Gateway 管理端口 health 探针走 GET
        boolean postProbe = url.getPath().endsWith("/auth/captcha")
            || url.getPath().endsWith("/api/example/demo/hello");
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
                        .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"probe\"}", StandardCharsets.UTF_8));
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

    private static int parsePort(List<String> args) {
        return args.stream()
            .filter(a -> a.startsWith("--server.port="))
            .mapToInt(a -> Integer.parseInt(a.substring("--server.port=".length())))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("缺少 --server.port 参数"));
    }

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
            socket.bind(new InetSocketAddress("127.0.0.1", 0));
            return socket.getLocalPort();
        }
    }

    // ------------------------------------------------------------------
    // HTTP / Redis 辅助
    // ------------------------------------------------------------------

    private static String gateway() {
        return "http://localhost:" + gatewayService.port();
    }

    private static int postStatus(String url, String bearerToken, String body) throws IOException {
        EnvelopeResult r = postEnvelope(url, bearerToken, body);
        return r.status();
    }

    private record EnvelopeResult(int status, String rawBody) {}

    private static EnvelopeResult postEnvelope(String url, String bearerToken, String body) throws IOException {
        return postEnvelope(url, bearerToken, body, null);
    }

    private static EnvelopeResult postEnvelope(String url, String bearerToken, String body,
                                               Map<String, String> extraHeaders) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        if (extraHeaders != null) {
            extraHeaders.forEach(builder::header);
        }
        try {
            HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new EnvelopeResult(response.statusCode(), response.body());
        } catch (HttpTimeoutException e) {
            throw new IOException("请求超时: " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("请求被中断: " + url, e);
        }
    }

    private static JsonNode parseEnvelope(EnvelopeResult r) {
        try {
            return JSON.readTree(r.rawBody());
        } catch (IOException e) {
            throw new IllegalStateException("响应不是合法 JSON：" + r.rawBody(), e);
        }
    }

    private static JsonNode postForData(String url, String bearerToken, JsonNode body) {
        return postForData(url, bearerToken, body, null);
    }

    /** POST + JSON Body（可附额外请求头，如内部同步通道的 X-Internal-Secret/X-Tenant-Id）；
     *  断言 HTTP 200 + 信封 code=200，返回 data 节点 */
    private static JsonNode postForData(String url, String bearerToken, JsonNode body,
                                        Map<String, String> extraHeaders) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        if (extraHeaders != null) {
            extraHeaders.forEach(builder::header);
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
        JsonNode envelope;
        try {
            envelope = JSON.readTree(raw);
        } catch (IOException e) {
            throw new IllegalStateException("响应不是合法 JSON: " + raw, e);
        }
        assertThat(envelope.path("code").asInt())
            .as("管理链路 " + url + " 信封 code 必须 200，响应：" + raw).isEqualTo(200);
        return envelope.path("data");
    }

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

    private static void sleepQuiet(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
