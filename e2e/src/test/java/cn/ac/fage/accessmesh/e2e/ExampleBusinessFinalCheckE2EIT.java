package cn.ac.fage.accessmesh.e2e;

import tools.jackson.databind.json.JsonMapper;

import cn.ac.fage.accessmesh.e2e.E2eProcessSupport.ServiceHandle;
import static cn.ac.fage.accessmesh.e2e.E2eProcessSupport.filterOutTestClasses;
import static cn.ac.fage.accessmesh.e2e.E2eProcessSupport.freePort;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.IntPredicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 业务最终检查与模式切换垂直切片 E2E（T-ACCESS-061，设计 §8.6/§8.5、契约 §25.7）。
 *
 * <p>验收面：①<b>两层判定双路</b>（N01 业务半边）——网关操作准入与业务实例检查为两次
 * 执行、不共享跨 HTTP 运行状态：用户仅持 report-1 的 EXAMPLE:VIEW 时，网关对
 * /report/view 恒 MAY_ENTER（类型-操作候选存在），业务最终检查对 report-2 拒绝
 * （30004 信封）对 report-1 放行；②独立批量混入（N24 批量半边：逐项结果，任一允许
 * 不放行整批）；③列表范围与 total 同口径（B 精确不可见）；④CREATE=TYPE_LEVEL
 * （实例 CREATE 授权过网关候选但业务类型级检查拒绝）；⑤上下文子权限真实父
 * （对父放行/错父拒绝）；⑥异步导出提交/执行时点双鉴权（提交后撤权→执行时点拒绝）；
 * ⑦直连伪造身份链阻断（N25）；⑧PERM_MUTEX 业务半边（N04 跨实例不误拒 / N05 同实例
 * 真互斥：准入恒 MAY_ENTER、业务最终检查拒绝）；⑨服务级暂停切换 runbook 演练
 * （暂停→恢复：代次单调递增、空快照 DENY、恢复以库为准）。
 *
 * <p><b>example-service 消费 perm-client SDK</b>（T-ACCESS-061 拍板）：业务最终检查经
 * PermissionFeignClient 调 auth/check 族端点（内部密钥通道），Feign 目标 access-service
 * 经 SimpleDiscoveryClient 静态实例解析（与 Gateway 同款免 Nacos 直连）。
 *
 * <p>拓扑同 {@link ExampleProtectedApiE2EIT}：PG/Redis Testcontainers + 三服务子进程；
 * e2e 模块整轨即 E2E（日常 -DskipE2E=true 跳过、收口必跑）。
 */
@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExampleBusinessFinalCheckE2EIT {

    private static final Path DDL_PATH = Path.of("..", "docs", "design", "schema", "access-service.sql");
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

    private static final String SERVICE = "example-service";
    private static final String TYPE = "EXAMPLE";
    private static final String ROLE_TYPE = "BASIC_ROLE";
    private static final String ROLE_EXTERNAL_ID = "e2e-finalcheck-role";
    private static final String TARGET_USERNAME = "e2e-finalcheck-target";
    /** 撤权窗口演示的导出延迟（毫秒）——远大于失效广播传播耗时，提交后撤权可确定性命中执行时点重查。 */
    private static final long EXPORT_DELAY_MS = 4000;

    /** 30 秒陈旧窗口（授/撤权生效上界，与既有 E2E 同口径）。 */
    private static final Duration STALE_WINDOW = Duration.ofSeconds(30);

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
        .withDatabaseName("access_db")
        .withUsername("postgres")
        .withPassword("postgres");

    private static final String REDIS_PASSWORD = "accessmesh-dev";

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withCommand("redis-server", "--requirepass", REDIS_PASSWORD)
        .withExposedPorts(6379);

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();
    private static final ObjectMapper JSON = JsonMapper.builder().configureForJackson2().build();

    private static ServiceHandle accessService;
    private static ServiceHandle gatewayService;
    private static ServiceHandle exampleService;
    private static Map<String,String> exampleEnvironment;
    private static List<String> exampleArguments;

    private static String adminToken;
    private static long targetUserId;
    private static String targetInitialPassword;
    private static String targetToken;
    private static String dataImportKey;

    @BeforeAll
    static void bootStack() throws Exception {
        String ddl = Files.readString(DDL_PATH, StandardCharsets.UTF_8);
        try (var conn = java.sql.DriverManager.getConnection(
            postgres.getJdbcUrl() + "?stringtype=unspecified", postgres.getUsername(), postgres.getPassword());
             var st = conn.createStatement()) {
            st.execute(ddl);
            st.execute("INSERT INTO service_config (tenant_id, service_code, name, status) VALUES ("
                + "1, 'example-service', 'Example Service', 1)");
            st.execute("INSERT INTO service_config (tenant_id, service_code, name, status) VALUES ("
                + "2, 'example-service', 'Shared Example Tenant Two', 1)");
            try (var ps = conn.prepareStatement("INSERT INTO service_credential "
                + "(tenant_id, service_code, credential_id, secret_hash, status) VALUES (?, 'example-service', ?, ?, 1)")) {
                for (int tenant = 1; tenant <= 2; tenant++) {
                    ps.setLong(1, tenant);
                    ps.setString(2, "sc-example-e2e-" + tenant);
                    ps.setString(3, "sha256-v1$" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(("sk-example-e2e-" + tenant).getBytes(StandardCharsets.UTF_8))));
                    ps.executeUpdate();
                }
            }
        }

        var exampleDatabase = E2eExampleDatabase.create(postgres);

        int accessPort = freePort();
        int gatewayPort = freePort();
        int gatewayMgmtPort = freePort();
        int examplePort = freePort();

        accessService = startService("access-service", ACCESS_MAIN_CLASS,
            accessServiceArgs(accessPort), accessServiceEnv(),
            URI.create("http://localhost:" + accessPort + "/api/access/auth/captcha"), ACCESS_SERVICE_CLASSES_DIR);
        waitAdminSeeded();

        exampleEnvironment=exampleDatabase.environment(Map.of("JWT_SECRET_KEY", JWT_SECRET,
                "ACCESSMESH_SIGNATURE_SECRET", SIGNATURE_SECRET,
                "SPRING_APPLICATION_JSON", JSON.writeValueAsString(Map.of("example", Map.of("permission", Map.of(
                    "allow-insecure", true,
                    "tenant-credentials", Map.of(
                        "1", Map.of("credential-id", "sc-example-e2e-1", "credential-secret", "sk-example-e2e-1"),
                        "2", Map.of("credential-id", "sc-example-e2e-2", "credential-secret", "sk-example-e2e-2"))))))));
        exampleArguments=exampleServiceArgs(examplePort,accessPort);
        exampleService=startService("example-service",EXAMPLE_MAIN_CLASS,exampleArguments,exampleEnvironment,
            URI.create("http://localhost:"+examplePort+"/api/example/demo/hello"),EXAMPLE_SERVICE_CLASSES_DIR);

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
    // 固定步骤
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("① 管理员登录 + 登记示例族：EXAMPLE 类型/四操作/报表资源/七路由（sync-v2 版本化声明）")
    void step1_seedExampleFamilyAndRoutes() {
        adminToken = realLogin("admin", BOOTSTRAP_ADMIN_PASSWORD);
        assertThat(adminToken).isNotBlank();

        // EXAMPLE 类型：自动预置 CRUD 四操作 CREATE(1)/VIEW(2)/UPDATE(4,inherit2)/DELETE(8,inherit2)；
        // 仅补登记 EXPORT/SUB_VIEW 两操作（位 16/32 无 uk_typed_bit 冲突，无继承掩码）
        postForData(gateway() + "/api/access/type-definition/create", adminToken,
            JSON.createObjectNode()
                .put("typeKey", "resource_type")
                .put("typeCode", TYPE)
                .put("name", "E2E 业务最终检查示例类型"));
        postForData(gateway() + "/api/access/operation-permission/create", adminToken,
            JSON.createObjectNode().put("resourceTypeCode", TYPE).put("code", "EXPORT").put("name", "导出").put("binaryBit", 16));
        postForData(gateway() + "/api/access/operation-permission/create", adminToken,
            JSON.createObjectNode().put("resourceTypeCode", TYPE).put("code", "SUB_VIEW").put("name", "子权限查看").put("binaryBit", 32));

        for (String code : List.of("report-1", "report-2", "report-3", "report-1-detail")) {
            postForData(gateway() + "/api/access/resource-entity/create", adminToken,
                JSON.createObjectNode().put("resourceTypeCode", TYPE).put("code", code).put("name", "E2E " + code));
        }

        // 七路由映射（sync-v2 FULL，示例服务一次声明；各路由独立准入要求）
        var apis = JSON.createArrayNode();
        apis.add(api("view", "POST", "/api/example/report/view", "VIEW"));
        apis.add(api("batch-view", "POST", "/api/example/report/batch-view", "VIEW"));
        apis.add(api("list", "POST", "/api/example/report/list", "VIEW"));
        apis.add(api("create", "POST", "/api/example/report/create", "CREATE"));
        apis.add(api("sub-view", "POST", "/api/example/report/sub-view", "SUB_VIEW"));
        apis.add(api("export-submit", "POST", "/api/example/report/export/submit", "EXPORT"));
        apis.add(api("export-status", "POST", "/api/example/report/export/status", "VIEW"));
        JsonNode syncResp = postForData(gateway() + "/api/access/service-config/sync-v2", adminToken,
            JSON.createObjectNode()
                .put("serviceCode", SERVICE)
                .put("basePath", "")
                .put("syncMode", "FULL")
                .set("groups", JSON.createArrayNode().add(JSON.createObjectNode()
                    .put("groupCode", "report")
                    .put("groupName", "报表示例")
                    .set("apis", apis))));
        assertThat(syncResp.path("createdMappings").asLong())
            .as("七条路由映射必须创建成功，响应：" + syncResp).isEqualTo(7);
    }

    private static tools.jackson.databind.node.ObjectNode api(String name, String method, String path, String op) {
        return JSON.createObjectNode()
            .put("name", name)
            .put("httpMethod", method)
            .put("path", path)
            .put("resourceCode", "example:report:" + name)
            .put("description", "E2E " + name)
            .set("requiredPermission", JSON.createObjectNode()
                .put("resourceTypeCode", TYPE)
                .put("operationCode", op));
    }

    @Test
    @Order(2)
    @DisplayName("② 目标用户+空权限角色；授予 report-1 的 EXAMPLE:VIEW 后登录")
    void step2_createTargetUserAndGrantViewOnReport1() {
        JsonNode created = postForData(gateway() + "/api/access/user/create", adminToken,
            JSON.createObjectNode().put("username", TARGET_USERNAME).put("name", "E2E FinalCheck Target"));
        targetUserId = created.path("id").asLong();
        targetInitialPassword = created.path("initialPassword").asText();
        postForData(gateway() + "/api/access/abstract-role/create", adminToken,
            JSON.createObjectNode()
                .put("roleTypeCode", ROLE_TYPE)
                .put("externalId", ROLE_EXTERNAL_ID)
                .put("name", "E2E FinalCheck Role"));
        postForData(gateway() + "/api/access/user-role/assign", adminToken,
            JSON.createObjectNode().set("items", JSON.createArrayNode().add(JSON.createObjectNode()
                .put("subjectTypeCode", "LOCAL_USER")
                .put("subjectExternalId", String.valueOf(targetUserId))
                .putNull("domainCode")
                .put("roleTypeCode", ROLE_TYPE)
                .put("roleExternalId", ROLE_EXTERNAL_ID))));

        grant("VIEW", "INSTANCE", "report-1");
        targetToken = realLogin(TARGET_USERNAME, targetInitialPassword);
        assertThat(targetToken).isNotBlank();
    }

    @Test
    @Order(3)
    @DisplayName("③ N01 双路：网关准入恒 MAY_ENTER，业务最终检查 A 放行/B 拒绝（两次执行不共享状态）")
    void step3_dualPathAdmissionPassesBusinessDifferentiates() throws IOException {
        // 授权生效等待：report-1 放行（网关准入 + 业务检查双双通过）——成功即证明网关候选已生效
        JsonNode allowed = awaitBusinessResult("/api/example/report/view",
            "{\"reportCode\":\"report-1\"}", body -> body.path("code").asInt() == 200,
            "report-1 必须在 30 秒窗口内放行（信封 200）");
        assertThat(allowed.path("data").path("reportCode").asText()).isEqualTo("report-1");
        assertThat(allowed.path("data").path("content").asText()).contains("report-1");

        // 同一路由同一时刻：report-2 过网关（同路由同要求 EXAMPLE:VIEW，report-1 候选在——
        // 准入按类型-操作候选判定，与目标无关），业务最终检查拒绝（30004 信封，HTTP 200）
        JsonNode denied = awaitBusinessResult("/api/example/report/view",
            "{\"reportCode\":\"report-2\"}", body -> body.path("code").asInt() == 30004,
            "report-2 必须被业务最终检查拒绝（30004，网关已放行的分层行为）");
        assertThat(denied.path("data").isNull()).isTrue();
    }

    @Test
    @Order(4)
    @DisplayName("④ N24 批量混入：逐项独立——report-1 带数据、report-2 拒绝零数据")
    void step4_batchMixedTargetsPerItemIndependent() throws IOException {
        JsonNode body = awaitBusinessResult("/api/example/report/batch-view",
            "{\"reportCodes\":[\"report-1\",\"report-2\"]}",
            b -> b.path("code").asInt() == 200, "批量查看必须成功");
        JsonNode items = body.path("data").path("items");
        assertThat(items).hasSize(2);
        JsonNode item1 = items.get(0);
        JsonNode item2 = items.get(1);
        assertThat(item1.path("reportCode").asText()).isEqualTo("report-1");
        assertThat(item1.path("allowed").asBoolean()).isTrue();
        assertThat(item1.path("content").asText()).contains("report-1");
        assertThat(item2.path("reportCode").asText()).isEqualTo("report-2");
        assertThat(item2.path("allowed").asBoolean()).isFalse();
        assertThat(item2.path("content").isNull()).isTrue();
        assertThat(item2.path("name").isNull()).isTrue();
        assertThat(body.path("data").path("allowedCount").asInt()).isEqualTo(1);
        assertThat(body.path("data").path("deniedCount").asInt()).isEqualTo(1);
    }

    @Test
    @Order(5)
    @DisplayName("⑤ 列表范围与 total 同口径：仅 report-1 可见（B 精确不可见）")
    void step5_listScopeSameCaliber() throws IOException {
        JsonNode body = awaitBusinessResult("/api/example/report/list",
            "{\"keyword\":null,\"pageNum\":1,\"pageSize\":10}", b -> b.path("code").asInt() == 200, "列表必须成功");
        // report-1-detail 是 SUB_VIEW 子权限资源，VIEW 范围不含它（detail 行独立资源码）
        assertThat(body.path("data").path("total").asLong()).isEqualTo(1);
        assertThat(body.path("data").path("items")).hasSize(1);
        assertThat(body.path("data").path("items").get(0).path("reportCode").asText()).isEqualTo("report-1");
    }

    @Test
    @Order(6)
    @DisplayName("⑥ N25 直连伪造身份头：签名校验拒绝（30003），不依赖『只能从网关进入』假设")
    void step6_directConnectForgedIdentityRejected() throws IOException {
        EnvelopeResult forged = postEnvelope(
            "http://localhost:" + exampleService.port() + "/api/example/report/view",
            null, "{\"reportCode\":\"report-1\"}",
            Map.of("X-User-Id", String.valueOf(targetUserId), "X-Tenant-Id", TENANT_ID));
        assertThat(forged.status()).isEqualTo(200);
        assertThat(parseEnvelope(forged).path("code").asInt()).isEqualTo(30003);
    }

    @Test
    @Order(7)
    @DisplayName("⑦ CREATE=TYPE_LEVEL：实例 CREATE 授权过网关候选但业务类型级检查拒绝；类型级授予后放行")
    void step7_createRequiresTypeLevel() throws IOException {
        // 实例 CREATE 授权（网关 /create 路由候选成立）
        grant("CREATE", "INSTANCE", "report-1");
        JsonNode denied = awaitBusinessResult("/api/example/report/create",
            "{\"name\":\"不应创建\"}", b -> b.path("code").asInt() == 30004,
            "实例 CREATE 不得授予类型创建权（业务 TYPE_LEVEL 检查必须拒绝）");
        assertThat(denied.path("data").isNull()).isTrue();

        // 类型级 CREATE（scopeMode=ALL，resourceCode 空）
        grant("CREATE", "ALL", null);
        JsonNode created = awaitBusinessResult("/api/example/report/create",
            "{\"name\":\"E2E 新报表\"}", b -> b.path("code").asInt() == 200, "类型级 CREATE 必须放行");
        assertThat(created.path("data").path("reportCode").asText()).isEqualTo("report-101");
    }

    @Test
    @Order(8)
    @DisplayName("⑧ 上下文子权限：depend_on 子行真实父——对父放行/错父拒绝")
    void step8_subViewRealParentBinding() throws Exception {
        // depend_on 子行（SUB_VIEW@report-1-detail，父=report-1 VIEW 行）：授权写路径的子类型
        // 许可来自 manifest 依赖声明通道（20011 ALLOW_NONE/CONFIG_MISSING），本切片以 SQL 直插
        // 夹具（BatchAuthCheckPgIT 先例——depend_on 无 FK，引擎只比较其与父命中集）
        insertSubViewChildGrant("report-1-detail", "report-1");

        JsonNode ok = awaitBusinessResult("/api/example/report/sub-view",
            "{\"subReportCode\":\"report-1-detail\",\"parentReportCode\":\"report-1\"}",
            b -> b.path("code").asInt() == 200, "正确父上下文必须放行");
        assertThat(ok.path("data").path("content").asText()).contains("report-1");

        // 错父（report-2 非子行绑定父）：父判定不命中 → 拒绝
        JsonNode wrong = awaitBusinessResult("/api/example/report/sub-view",
            "{\"subReportCode\":\"report-1-detail\",\"parentReportCode\":\"report-2\"}",
            b -> b.path("code").asInt() == 30004, "错父必须被业务最终检查拒绝");
        assertThat(wrong.path("data").isNull()).isTrue();

        // 授权绑定允许跨业务父：引擎可放行 report-2 上下文，业务仍须拒绝错父。
        grant("VIEW", "INSTANCE", "report-2");
        insertSubViewChildGrant("report-1-detail", "report-2");
        String checkBody = JSON.writeValueAsString(Map.of(
            "subjectTypeCode", "LOCAL_USER", "subjectExternalId", Long.toString(targetUserId),
            "resourceTypeCode", TYPE, "resourceCode", "report-1-detail", "operationCode", "SUB_VIEW",
            "parentResourceTypeCode", TYPE, "parentResourceCode", "report-2", "parentOperationCodes", List.of("VIEW")));
        org.awaitility.Awaitility.await().atMost(STALE_WINDOW).untilAsserted(() -> {
            JsonNode check = parseEnvelope(postEnvelope("http://localhost:" + accessService.port()
                + "/api/access/auth/check", null, checkBody,
                Map.of("X-Credential-Id", "sc-example-e2e-1", "X-Credential-Secret", "sk-example-e2e-1")));
            assertThat(check.path("code").asInt()).isEqualTo(200);
            assertThat(check.path("data").path("allowed").asBoolean()).isTrue();
        });
        JsonNode wrongBusinessParent = parseEnvelope(postEnvelope(gateway() + "/api/example/report/sub-view", targetToken,
            "{\"subReportCode\":\"report-1-detail\",\"parentReportCode\":\"report-2\"}"));
        assertThat(wrongBusinessParent.path("code").asInt()).isEqualTo(30004);
        assertThat(wrongBusinessParent.path("data").isNull()).isTrue();
    }

    @Test
    @Order(9)
    @DisplayName("⑨ N24 异步半边：提交时点检查 EXPORT；提交后撤权→执行时点重查拒绝（不永久复用提交结论）")
    void step9_exportSubmitAndExecutionTimeRecheck() throws IOException {
        grant("EXPORT", "INSTANCE", "report-3");
        // 授权生效：首次提交成功（提交时点检查通过）
        JsonNode submitted = awaitBusinessResult("/api/example/report/export/submit",
            "{\"reportCode\":\"report-3\"}", b -> b.path("code").asInt() == 200, "EXPORT 授权后提交必须成功");
        String firstJobId = submitted.path("data").path("jobId").asText();
        awaitJobTerminal(firstJobId, "DONE", "授权期提交的作业必须执行成功");

        // 撤权窗口：再提交（仍成功——授权尚未撤销），随即撤权，执行时点（+4s）重查必须拒绝
        long exportGrantId = findGrantId("EXPORT", "report-3");
        JsonNode second = awaitBusinessResult("/api/example/report/export/submit",
            "{\"reportCode\":\"report-3\"}", b -> b.path("code").asInt() == 200, "撤权前提交必须成功");
        String secondJobId = second.path("data").path("jobId").asText();
        revoke(exportGrantId);
        awaitJobTerminal(secondJobId, "DENIED", "提交后撤权的作业必须在执行时点被重查拒绝（N24）");

        // 撤权后新提交：候选转移（EXPORT 授到 report-2——/export/submit 网关候选仍在而放行），
        // 业务提交时点检查 report-3 无 EXPORT → 30004（两层分层行为，同 N01 形态）
        grant("EXPORT", "INSTANCE", "report-2");
        awaitBusinessResult("/api/example/report/export/submit",
            "{\"reportCode\":\"report-3\"}", b -> b.path("code").asInt() == 30004,
            "撤权后提交必须在提交时点被拒绝（候选转移下网关放行、业务拒绝）");
    }

    @Test
    @Order(12)
    @DisplayName("⑫ N04/N05 业务半边：跨实例不误拒；同实例真互斥业务拒绝（准入恒 MAY_ENTER）——置于 runbook 演练后（互斥拒绝不污染恢复断言）")
    void step10_mutexBusinessHalves() throws Exception {
        // 取操作行 ID（冲突规则按内部主键登记；typeValue 直接读库——type-definition/detail
        // 无 bootstrap 映射〔授权页由 list 行展开〕经网关必 403）
        long viewOpId = operationId("VIEW");
        long updateOpId = operationId("UPDATE");
        int typeValue = typeValue();
        // N04 前置：互斥第二端须覆盖被检操作（设计原文「UPDATE 覆盖 VIEW」）——用类型预置
        // UPDATE(4, inherit 2)；EXPORT(16, 无继承) 不覆盖 VIEW，不构成同场两端。
        // 建权限互斥规则（同类型 VIEW×UPDATE）
        postForData(gateway() + "/api/access/conflict-rule/create", adminToken,
            JSON.createObjectNode()
                .put("conflictType", "PERM_MUTEX")
                .put("firstOperationPermissionId", viewOpId)
                .put("secondOperationPermissionId", updateOpId)
                .put("resourceTypeValue", typeValue)
                .put("description", "E2E N04/N05 业务半边"));

        // N04：UPDATE 授在 report-2（覆盖 VIEW 的另一端在不同实例）——view report-1 不被误拒
        grant("UPDATE", "INSTANCE", "report-2");
        awaitBusinessResult("/api/example/report/view", "{\"reportCode\":\"report-1\"}",
            b -> b.path("code").asInt() == 200, "N04：跨实例互斥端不得误拒 report-1");

        // N05：UPDATE 再授 report-1——同实例两端齐备（VIEW 行＋覆盖 VIEW 的 UPDATE 行），
        // 业务最终检查必须拒绝；网关准入恒 MAY_ENTER（30004 信封本身即网关已放行的证据）
        grant("UPDATE", "INSTANCE", "report-1");
        JsonNode mutexDenied = awaitBusinessResult("/api/example/report/view",
            "{\"reportCode\":\"report-1\"}", b -> b.path("code").asInt() == 30004,
            "N05：同实例真互斥必须被业务最终检查拒绝（准入 MAY_ENTER 的分层行为）");
        assertThat(mutexDenied.path("message").asText()).contains("reason=");

        // 跨实例面不受牵连：report-1 上的 EXPORT 检查（经导出提交路由）两端同实例也互斥——
        // 但撤掉 report-1 的 EXPORT 前不测导出，避免用例耦合；N04 半边已由上行锁定
    }

    @Test
    @Order(11)
    @DisplayName("⑪ 服务级暂停切换 runbook 演练：暂停→恢复，代次单调递增+恢复以库为准")
    void step11_servicePauseSwitchResumeDrill() throws Exception {
        // 基线代次（步骤⑦已把 report-1 家族资源建齐；此处只动 service_config）
        long g0 = generation();
        // ① 暂停（status=0）：启停变化同事务 +1
        saveServiceConfig(0);
        long g1 = generation();
        assertThat(g1).as("暂停必须递增配置代次").isGreaterThan(g0);
        // 空路由快照（停用服务）→ 网关本地无命中 DENY 403（容忍过渡期 200/503）
        awaitHttpStatus("/api/example/report/view", "{\"reportCode\":\"report-1\"}", 403,
            s -> s == 200 || s == 503, "暂停后请求必须回到 403（空快照 DENY）");

        // 恢复：启停同事务递增代次，恢复以库中状态为准。
        saveServiceConfig(1);
        long g3 = generation();
        assertThat(g3).as("恢复必须递增配置代次").isGreaterThan(g1);
        JsonNode restored = awaitBusinessResult("/api/example/report/view",
            "{\"reportCode\":\"report-1\"}", b -> b.path("code").asInt() == 200,
            "恢复后必须在 30 秒窗口内回到放行（以库为准，不等快照 TTL 兜底）");
        assertThat(restored.path("data").path("reportCode").asText()).isEqualTo("report-1");
    }

    // ------------------------------------------------------------------
    // 授权/撤权辅助（apply-grant-plan 唯一写入口）
    // ------------------------------------------------------------------

    private static tools.jackson.databind.node.ObjectNode grantKey(
            String op, String scopeMode, String resourceCode) {
        var key = JSON.createObjectNode();
        key.put("resourceTypeCode", TYPE);
        key.put("resourceCode", resourceCode);
        key.put("codeType", "INSTANCE".equals(scopeMode) ? "default" : null);
        key.put("operationCode", op);
        key.put("scopeMode", scopeMode);
        key.putNull("conditionCode");
        key.put("canGrant", false);
        return key;
    }

    /** 单条授权（creates）；ALL 语义 resourceCode=null/codeType=null。 */
    private static JsonNode grant(String op, String scopeMode, String resourceCode) {
        return postForData(gateway() + "/api/access/role-resource-permission/apply-grant-plan", adminToken,
            grantPlan(List.of(grantKey(op, scopeMode, resourceCode)), List.of()));
    }

    private static tools.jackson.databind.node.ObjectNode grantPlan(
            List<tools.jackson.databind.node.ObjectNode> creates,
            List<Long> removes) {
        var plan = JSON.createObjectNode();
        var createsArr = JSON.createArrayNode();
        for (tools.jackson.databind.node.ObjectNode key : creates) {
            var item = JSON.createObjectNode();
            item.set("key", key);
            createsArr.add(item);
        }
        plan.set("creates", createsArr);
        plan.putNull("updates");
        if (removes.isEmpty()) {
            plan.putNull("removes");
        } else {
            var rm = JSON.createArrayNode();
            removes.forEach(rm::add);
            plan.set("removes", rm);
        }
        var req = JSON.createObjectNode();
        req.putNull("domainCode");
        req.put("roleTypeCode", ROLE_TYPE);
        req.put("roleExternalId", ROLE_EXTERNAL_ID);
        req.set("plan", plan);
        return req;
    }

    /** 撤销授权行（apply-grant-plan removes）。 */
    private static void revoke(long permissionId) {
        postForData(gateway() + "/api/access/role-resource-permission/apply-grant-plan", adminToken,
            grantPlan(List.of(), List.of(permissionId)));
    }

    /** 定位角色当前 EXAMPLE 类型指定操作/资源的授权行 id（授权列表按业务键匹配）。 */
    private static long findGrantId(String op, String resourceCode) {
        JsonNode items = postForData(gateway() + "/api/access/role-resource-permission/list", adminToken,
            JSON.createObjectNode()
                .putNull("domainCode")
                .put("roleTypeCode", ROLE_TYPE)
                .put("roleExternalId", ROLE_EXTERNAL_ID)
                .put("resourceTypeCode", TYPE)
                .put("includeChildren", false)).path("items");
        for (JsonNode item : items) {
            if (TYPE.equals(item.path("resourceTypeCode").asText())
                && op.equals(item.path("operationCode").asText())
                && java.util.Objects.equals(item.path("resourceCode").asText(null),
                    resourceCode == null ? null : resourceCode)) {
                return item.path("id").asLong();
            }
        }
        throw new IllegalStateException("未找到授权行: " + op + "@" + resourceCode + "，items=" + items);
    }

    private static long operationId(String code) {
        JsonNode items = postForData(gateway() + "/api/access/operation-permission/list", adminToken,
            JSON.createObjectNode().put("resourceTypeCode", TYPE)).path("items");
        for (JsonNode op : items) {
            if (code.equals(op.path("code").asText())) {
                return op.path("id").asLong();
            }
        }
        throw new IllegalStateException("未找到操作行: " + code);
    }

    // ------------------------------------------------------------------
    // 服务切换与代次
    // ------------------------------------------------------------------

    private static void saveServiceConfig(Integer status) {
        var req = JSON.createObjectNode()
            .put("serviceCode", SERVICE)
            .put("name", "Example Service");
        if (status != null) {
            req.put("status", status);
        }
        postForData(gateway() + "/api/access/service-config/save", adminToken, req);
    }

    /** SQL 直插 depend_on 子授权行（夹具形态；业务键定位子实体/父授权行/角色/类型值）。 */
    private static void insertSubViewChildGrant(String subCode, String parentCode) throws Exception {
        try (var conn = java.sql.DriverManager.getConnection(
            postgres.getJdbcUrl() + "?stringtype=unspecified", postgres.getUsername(), postgres.getPassword());
             var st = conn.createStatement()) {
            long roleId = scalar(st, "SELECT id FROM abstract_role WHERE tenant_id = 1 AND role_type = 6"
                + " AND external_id = '" + ROLE_EXTERNAL_ID + "' AND delete_flag = 0");
            long typeId = scalar(st, "SELECT type_value FROM type_definition WHERE tenant_id = 1"
                + " AND type_key = 'resource_type' AND type_code = '" + TYPE + "' AND delete_flag = 0");
            long subEntityId = scalar(st, "SELECT id FROM resource_entity WHERE tenant_id = 1"
                + " AND resource_type = " + typeId + " AND code = '" + subCode + "' AND delete_flag = 0");
            // 父行必须真正持有 VIEW 位：JOIN 操作定义需带位过滤（仅 JOIN code='VIEW' 不过滤 granted_bits，
            // 同角色同实例多操作行并存时 LIMIT 1 无序选行可能选中不含 VIEW 位的行——2026-10-01 执行计划
            // 变化实证选中 CREATE 行致 depend_on 指错父，判定恒 DEPENDENT_NOT_IN_PARENT_CONTEXT）
            long parentId = scalar(st,
                "SELECT rrp.id FROM role_resource_permission rrp"
                + " JOIN resource_entity re ON re.id = rrp.resource_entity_id AND re.code = '" + parentCode + "'"
                + " JOIN operation_permission op ON op.resource_type = rrp.resource_type AND op.code = 'VIEW'"
                + " AND (rrp.granted_bits & op.binary_bit) <> 0"
                + " WHERE rrp.tenant_id = 1 AND rrp.abstract_role_id = " + roleId
                + " AND rrp.resource_type = " + typeId + " AND rrp.delete_flag = 0 LIMIT 1");
            long subViewOpBit = scalar(st, "SELECT binary_bit FROM operation_permission WHERE tenant_id = 1"
                + " AND resource_type = " + typeId + " AND code = 'SUB_VIEW' AND delete_flag = 0");
            st.executeUpdate("INSERT INTO role_resource_permission"
                + " (tenant_id, abstract_role_id, resource_entity_id, granted_bits, resource_type, scope_all,"
                + " condition_id, depend_on, grant_source, created_at, updated_at, delete_flag)"
                + " VALUES (1, " + roleId + ", " + subEntityId + ", " + subViewOpBit + ", " + typeId
                + ", false, NULL, " + parentId + ", 'MANUAL', now(), now(), 0)");
        }
    }

    private static long scalar(java.sql.Statement st, String sql) throws java.sql.SQLException {
        try (var rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    @Order(12)
    @DisplayName("共享 example：第二租户真实凭证查询成功，错主体/未接入租户拒绝，吊销不回落默认凭证")
    void sharedInstance_selectsTenantCredentialForActualFinalCheck() throws Exception {
        long secondUser = 9_820_001L;
        assertThat(E2eTenantSupport.openTenant(gateway(),BOOTSTRAP_ADMIN_PASSWORD,"e2e-second",
            BOOTSTRAP_ADMIN_PASSWORD,ExampleBusinessFinalCheckE2EIT::readCaptchaFromRedis)).isEqualTo(2L);
        try (var conn = java.sql.DriverManager.getConnection(
            postgres.getJdbcUrl() + "?stringtype=unspecified", postgres.getUsername(), postgres.getPassword());
             var st = conn.createStatement()) {
            st.executeUpdate("INSERT INTO type_definition (tenant_id,type_key,type_code,type_value,name,is_system,sort_order,extra) "
                + "SELECT 2,type_key,type_code,type_value,name,is_system,sort_order,extra FROM type_definition WHERE tenant_id=1 AND delete_flag=0 AND type_code='EXAMPLE'");
            st.executeUpdate("INSERT INTO operation_permission (tenant_id,resource_type,code,name,binary_bit,inherit_mask) "
                + "SELECT 2,resource_type,code,name,binary_bit,inherit_mask FROM operation_permission WHERE tenant_id=1 AND delete_flag=0 AND resource_type=(SELECT type_value FROM type_definition WHERE tenant_id=1 AND type_code='EXAMPLE' AND type_key='resource_type' AND delete_flag=0)");
            st.executeUpdate("INSERT INTO abstract_user(id,tenant_id,user_type,external_id,name,enabled) "
                + "VALUES (9820001,2,3,'9820001','shared tenant user',true)");
            long role = scalar(st, "INSERT INTO abstract_role(tenant_id,role_type,external_id,name,status) "
                + "VALUES (2,6,'shared-role','shared role',1) RETURNING id");
            st.executeUpdate("INSERT INTO user_role(tenant_id,abstract_user_id,target_type,target_id) VALUES (2,9820001,'ROLE'," + role + ")");
            long type = scalar(st, "SELECT type_value FROM type_definition WHERE tenant_id=2 AND type_key='resource_type' AND type_code='EXAMPLE'");
            st.executeUpdate("INSERT INTO resource_entity(tenant_id,resource_type,code,code_type,name,status) "
                + "VALUES (2," + type + ",'report-1','default','tenant two report',1)");
            long view = scalar(st, "SELECT binary_bit FROM operation_permission WHERE tenant_id=2 AND resource_type=" + type + " AND code='VIEW'");
            st.executeUpdate("INSERT INTO role_resource_permission(tenant_id,abstract_role_id,resource_type,granted_bits,scope_all,grant_source) "
                + "VALUES (2," + role + "," + type + "," + view + ",true,'MANUAL')");
        }
        String url = "http://localhost:" + exampleService.port() + "/api/example/report/view";
        String body = "{\"reportCode\":\"report-1\"}";
        var allowed = parseEnvelope(postEnvelope(url, null, body, signedExampleIdentity("2", secondUser)));
        assertThat(allowed.path("code").asInt()).as(allowed.toString()).isEqualTo(200);
        assertThat(allowed.path("data").path("reportCode").asText()).isEqualTo("report-1");
        assertThat(parseEnvelope(postEnvelope(url, null, body, signedExampleIdentity("1", secondUser))).path("code").asInt()).isEqualTo(30004);
        assertThat(parseEnvelope(postEnvelope(url, null, body, signedExampleIdentity("3", secondUser))).path("code").asInt()).isEqualTo(30004);
        try (var conn = java.sql.DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var st = conn.createStatement()) {
            st.executeUpdate("UPDATE service_credential SET status=0 WHERE tenant_id=2");
        }
        assertThat(parseEnvelope(postEnvelope(url, null, body, signedExampleIdentity("2", secondUser))).path("code").asInt()).isEqualTo(30005);
    }

    @Test
    @Order(13)
    void persistentMetadataPublishesThroughServiceCredentialsAndSupportsScopedFieldGrants() throws Exception {
        for (String type : List.of("DEMO_REPORT", "DEMO_REPORT_FIELD", "DEMO_DATA_SCOPE", "DEMO_POLICY", "DEMO_DATASET", "DEMO_TASK")) {
            String extra = List.of("DEMO_POLICY", "DEMO_DATASET", "DEMO_TASK").contains(type) ? "{\"managedMode\":\"MANAGED\"}"
                : "{\"managedMode\":\"SYNC\",\"syncSourceService\":\"example-service\"}";
            postForData(gateway()+"/api/access/type-definition/create", adminToken,
                JSON.createObjectNode().put("typeKey","resource_type").put("typeCode",type).put("name",type).put("extra",extra));
            postForData(gateway()+"/api/access/operation-permission/create", adminToken,
                JSON.createObjectNode().put("resourceTypeCode",type).put("code",type.equals("DEMO_TASK") ? "TRIGGER" : List.of("DEMO_POLICY", "DEMO_DATASET").contains(type) ? "MANAGE" : "DATA_READ")
                    .put("name","demo operation").put("binaryBit",16).put("inheritMask",0));
            if(type.equals("DEMO_REPORT")) {
                postForData(gateway()+"/api/access/operation-permission/create",adminToken,
                    JSON.createObjectNode().put("resourceTypeCode",type).put("code","EXPORT").put("name","export data").put("binaryBit",64).put("inheritMask",0));
            }
            if(type.equals("DEMO_TASK")) {
                postForData(gateway()+"/api/access/operation-permission/create",adminToken,
                    JSON.createObjectNode().put("resourceTypeCode",type).put("code","ENABLE").put("name","enable task").put("binaryBit",32).put("inheritMask",0));
            }
            if(List.of("DEMO_REPORT","DEMO_REPORT_FIELD","DEMO_DATA_SCOPE").contains(type)) {
                postForData(gateway()+"/api/access/operation-permission/create",adminToken,
                    JSON.createObjectNode().put("resourceTypeCode",type).put("code","DATA_EDIT").put("name","edit data")
                        .put("binaryBit",32).put("inheritMask",0));
            }
        }
        postForData(gateway()+"/api/access/biz-domain/create", adminToken,
            JSON.createObjectNode().put("code","EXAMPLE_DEMO").put("name","Example demo"));
        for (var config : Map.of("CLASSIFY", "{\"resourceTypeCodes\":[\"DEMO_REPORT\",\"DEMO_REPORT_FIELD\",\"DEMO_DATA_SCOPE\",\"DEMO_POLICY\",\"DEMO_DATASET\",\"DEMO_TASK\"]}",
                "SUB_PERM", "{\"allowed\":[{\"parent_type\":\"DEMO_REPORT\",\"child_types\":[\"DEMO_REPORT_FIELD\",\"DEMO_DATA_SCOPE\"]}]}").entrySet()) {
            postForData(gateway()+"/api/access/domain-config/save",adminToken,
                JSON.createObjectNode().put("domainCode","EXAMPLE_DEMO").put("configType",config.getKey()).put("extra",config.getValue()));
        }
        var manifest=JSON.readTree(Files.readString(Path.of("..","example-service","examples","permission-demo-interfaces.json")));
        var oldMappings=postForData(gateway()+"/api/access/service-config/apis",adminToken,JSON.createObjectNode().put("serviceCode",SERVICE)).path("items");
        for (var declaration : manifest.path("groups").get(0).path("apis")) {
            for (var mapping : oldMappings) {
                if (("/api/example"+declaration.path("path").asText()).equals(mapping.path("pathPattern").asText())) {
                    ((tools.jackson.databind.node.ObjectNode)declaration).put("resourceCode",mapping.path("resourceCode").asText());
                }
            }
        }
        var headers=Map.of("X-Credential-Id","sc-example-e2e-1","X-Credential-Secret","sk-example-e2e-1");
        var sync=postEnvelope("http://localhost:"+accessService.port()+"/api/access/service-config/sync-v2", null, manifest.toString(),headers);
        assertThat(parseEnvelope(sync).path("code").asInt()).isEqualTo(200);
        var denied=postEnvelope(gateway()+"/api/example/demo-report/create",targetToken,
            "{\"reportCode\":\"forbidden\",\"name\":\"denied\",\"datasetCode\":\"sales-records\",\"fieldResourceTypeCode\":\"DEMO_REPORT_FIELD\"}");
        assertThat(denied.status()).isEqualTo(403);
        demoAdminPost("/demo-report/create",JSON.createObjectNode().put("reportCode","persistent")
            .put("name","Persistent report").put("datasetCode","sales-records").put("fieldResourceTypeCode","DEMO_REPORT_FIELD"));
        awaitDemoPublication("DEMO_REPORT","persistent");
        var registered=demoAdminPost("/demo-field/register", JSON.createObjectNode().put("reportCode","persistent")
            .put("expectedCatalogVersion",0).set("fieldKeys",JSON.createArrayNode().add("amount"))).path("data");
        String fieldCode=registered.path("items").get(0).path("resourceCode").asText();
        awaitDemoPublication("DEMO_REPORT_FIELD",fieldCode);
        var parentKey=JSON.createObjectNode().put("resourceTypeCode","DEMO_REPORT").put("resourceCode","persistent")
            .put("codeType","default").put("operationCode","DATA_READ").put("scopeMode","INSTANCE").put("canGrant",false);
        var childKey=JSON.createObjectNode().put("resourceTypeCode","DEMO_REPORT_FIELD").put("resourceCode",fieldCode)
            .put("codeType","default").put("operationCode","DATA_READ").put("scopeMode","INSTANCE").put("canGrant",false);
        var create=JSON.createObjectNode(); create.set("key",parentKey);
        create.set("children",JSON.createArrayNode().add(childKey));
        var plan=JSON.createObjectNode().set("creates",JSON.createArrayNode().add(create));
        postForData(gateway()+"/api/access/role-resource-permission/apply-grant-plan",adminToken,
            JSON.createObjectNode().put("roleTypeCode",ROLE_TYPE).put("roleExternalId",ROLE_EXTERNAL_ID).set("plan",plan));
        var check=JSON.createObjectNode().put("subjectTypeCode","LOCAL_USER").put("subjectExternalId",Long.toString(targetUserId))
            .put("resourceTypeCode","DEMO_REPORT_FIELD").put("resourceCode",fieldCode).put("operationCode","DATA_READ")
            .put("parentResourceTypeCode","DEMO_REPORT").put("parentResourceCode","persistent")
            .set("parentOperationCodes",JSON.createArrayNode().add("DATA_READ"));
        org.awaitility.Awaitility.await().atMost(STALE_WINDOW).untilAsserted(() -> {
            var result=parseEnvelope(postEnvelope("http://localhost:"+accessService.port()+"/api/access/auth/check",null,check.toString(),headers));
            assertThat(result.path("code").asInt()).isEqualTo(200);
            assertThat(result.path("data").path("allowed").asBoolean()).isTrue();
        });
        var wrongChannel=postEnvelope("http://localhost:"+accessService.port()+"/api/access/type-definition/create",null,
            "{\"typeKey\":\"resource_type\",\"typeCode\":\"FORBIDDEN\",\"name\":\"forbidden\"}",headers);
        assertThat(wrongChannel.status()).isEqualTo(403);
        demoAdminPost("/demo-report/update",JSON.createObjectNode().put("reportCode","persistent")
            .put("expectedVersion",1).put("name","Disabled report").put("enabled",false));
        awaitDemoPublication("DEMO_REPORT","persistent");
        demoAdminPost("/demo-report/update",JSON.createObjectNode().put("reportCode","persistent")
            .put("expectedVersion",2).put("name","Restored report").put("enabled",true));
        awaitDemoPublication("DEMO_REPORT","persistent");
        // Optional local verification of the PowerShell delivery artifact; the core E2E is portable.
        String scriptHost=System.getProperty("example.demo.scriptHost");
        if (scriptHost != null) {
            for (int replay=0; replay<2; replay++) {
                Path log=Path.of("target","e2e","permission-demo-setup-"+replay+".log").toAbsolutePath();
                var builder=new ProcessBuilder(scriptHost,"-NoProfile","-NonInteractive","-File",
                    Path.of("..","tools","init-example-permission-demo.ps1").toAbsolutePath().toString(),
                    "-BaseUrl",gateway(),"-AllowInsecure");
                builder.environment().put("ADMIN_TOKEN",adminToken);
                builder.environment().put("EXAMPLE_CREDENTIAL_ID","sc-example-e2e-1");
                builder.environment().put("EXAMPLE_CREDENTIAL_SECRET","sk-example-e2e-1");
                var process=builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
                try {
                    assertThat(process.waitFor(150,TimeUnit.SECONDS)).as("setup script completed").isTrue();
                    assertThat(process.exitValue()).as("setup script: %s",new String(Files.readAllBytes(log),StandardCharsets.UTF_8)).isZero();
                } finally { if (process.isAlive()) process.destroyForcibly(); }
            }
            var empty=demoAdminPost("/demo-data/page",JSON.createObjectNode().put("reportCode","sales-report").put("pageNum",1).put("pageSize",20));
            assertThat(empty.path("data").path("state").asText()).isEqualTo("EMPTY_ROWS");
            assertThat(empty.path("data").path("page").path("total").asLong()).isZero();
            assertThat(empty.path("data").path("columns").size()).isEqualTo(9);
        }

    }

    @Test
    @Order(14)
    void csvImportAndRequestIdentitySurviveServiceRestartAndRevocation() throws Exception {
        ensureDataSetup();
        String key=java.util.UUID.randomUUID().toString();
        String csv="recordCode,ownerUserId,teamCode,departmentCode,regionCode,saleDate,productName,amount,remark\r\n"
            +"restart-row,"+targetUserId+",T1,D1,R1,2026-10-19,Restart,1.25,note\r\n";
        var imported=uploadCsv(adminToken,"sales-report",key,csv);
        assertThat(imported.path("code").asInt()).isEqualTo(200);
        assertThat(imported.path("data").path("recordCount").asInt()).isEqualTo(1);
        assertThat(uploadCsv(adminToken,"sales-report",key,csv).path("data").path("status").asText()).isEqualTo("IMPORTED");
        int port=exampleService.port(); exampleService.destroy();
        exampleService=startService("example-service",EXAMPLE_MAIN_CLASS,exampleArguments,exampleEnvironment,
            URI.create("http://localhost:"+port+"/api/example/demo/hello"),EXAMPLE_SERVICE_CLASSES_DIR);
        var status=demoAdminPost("/demo-data/import-status",JSON.createObjectNode().put("importKey",key));
        assertThat(status.path("data").path("status").asText()).isEqualTo("IMPORTED");
        var rows=demoAdminPost("/demo-data/page",JSON.createObjectNode().put("reportCode","sales-report"));
        assertThat(rows.path("data").path("page").path("total").asInt()).isEqualTo(1);
        assertThat(rows.path("data").path("page").path("items").get(0).path("recordCode").asText()).isEqualTo("restart-row");
        demoAdminPost("/demo-data/revoke-import",JSON.createObjectNode().put("importKey",key));
        assertThat(uploadCsv(adminToken,"sales-report",key,csv).path("data").path("status").asText()).isEqualTo("REVOKED");
        String next=java.util.UUID.randomUUID().toString();
        assertThat(uploadCsv(adminToken,"sales-report",next,csv).path("data").path("status").asText()).isEqualTo("IMPORTED");
        demoAdminPost("/demo-data/revoke-import",JSON.createObjectNode().put("importKey",next));
    }

    @Test
    @Order(15)
    void ordinaryUserPermissionsControlRealRowsColumnsAndWrites() throws Exception {
        ensureDataSetup();
        var fieldRows=demoAdminPost("/demo-field/list",JSON.createObjectNode().put("reportCode","sales-report")).path("data").path("items");
        Map<String,JsonNode> fields=new java.util.HashMap<>(); fieldRows.forEach(f -> fields.put(f.path("fieldKey").asText(),f));
        long beta=postForData(gateway()+"/api/access/user/create",adminToken,JSON.createObjectNode().put("username","data-beta").put("name","数据乙")).path("id").asLong();
        long gamma=postForData(gateway()+"/api/access/user/create",adminToken,JSON.createObjectNode().put("username","data-gamma").put("name","数据丙")).path("id").asLong();
        long delta=postForData(gateway()+"/api/access/user/create",adminToken,JSON.createObjectNode().put("username","data-delta").put("name","数据丁")).path("id").asLong();
        createDataParent("sales-report","DATA_READ",List.of(dataKey("DEMO_DATA_SCOPE","team-t1","DATA_READ","INSTANCE"),
            dataKey("DEMO_REPORT_FIELD",fields.get("recordCode").path("resourceCode").asText(),"DATA_READ","INSTANCE"),
            dataKey("DEMO_REPORT_FIELD",fields.get("productName").path("resourceCode").asText(),"DATA_READ","INSTANCE")));
        var noEdit=postEnvelope(gateway()+"/api/example/demo-data/update",targetToken,"{\"reportCode\":\"sales-report\",\"recordCode\":\"A\",\"remark\":\"not allowed\"}");
        assertThat(noEdit.status()).isEqualTo(403);
        createDataGrant(dataKey("DEMO_DATASET",null,"MANAGE","ALL"),null);
        List<tools.jackson.databind.node.ObjectNode> edits=new java.util.ArrayList<>();
        edits.add(dataKey("DEMO_DATA_SCOPE",null,"DATA_EDIT","ALL"));
        fields.values().forEach(f -> edits.add(dataKey("DEMO_REPORT_FIELD",f.path("resourceCode").asText(),"DATA_EDIT","INSTANCE")));
        createDataParent("sales-report","DATA_EDIT",edits);
        dataImportKey=java.util.UUID.randomUUID().toString();
        String csv="recordCode,ownerUserId,teamCode,departmentCode,regionCode,saleDate,productName,amount,remark\n"
            +"A,"+targetUserId+",T1,D1,R1,2026-10-19,Alpha,10.00,ra\n"
            +"B,"+beta+",T1,D1,R2,2026-10-19,Beta,20.00,rb\n"
            +"C,"+gamma+",T2,D1,R1,2026-10-19,Gamma,30.00,rc\n"
            +"D,"+delta+",T3,D2,R2,2026-10-19,Delta,40.00,rd\n";
        org.awaitility.Awaitility.await().atMost(STALE_WINDOW).untilAsserted(() -> assertThat(uploadCsv(targetToken,"sales-report",dataImportKey,csv).path("code").asInt()).isEqualTo(200));
        assertDataCodes(List.of("A","B"));
        var hidden=postEnvelope(gateway()+"/api/example/demo-data/page",targetToken,JSON.createObjectNode().put("reportCode","sales-report")
            .set("filters",JSON.createArrayNode().add(JSON.createObjectNode().put("fieldId",fields.get("amount").path("fieldId").asLong()).put("operator","EQ").put("value",10))).toString());
        assertThat(parseEnvelope(hidden).path("code").asInt()).isEqualTo(30004);
        long readParent=dataGrantId("DEMO_REPORT","sales-report","DATA_READ",null);
        long editParent=dataGrantId("DEMO_REPORT","sales-report","DATA_EDIT",null);
        createDataGrant(dataKey("DEMO_REPORT_FIELD",null,"DATA_READ","ALL"),readParent);
        awaitBusinessResult("/api/example/demo-data/page","{\"reportCode\":\"sales-report\"}",
            r -> r.path("code").asInt()==200 && r.path("data").path("columns").size()==9 && r.path("data").path("page").path("total").asInt()==2,
            "field ALL must not widen row scope");
        removeDataGrant(dataGrantId("DEMO_REPORT_FIELD",null,"DATA_READ",readParent));
        assertDataCodes(List.of("A","B"));
        createDataGrant(dataKey("DEMO_DATA_SCOPE",null,"DATA_READ","ALL"),readParent);
        assertDataCodes(List.of("A","B","C","D"));
        removeDataGrant(dataGrantId("DEMO_DATA_SCOPE",null,"DATA_READ",readParent));
        assertDataCodes(List.of("A","B"));
        removeDataGrant(dataGrantId("DEMO_DATA_SCOPE","team-t1","DATA_READ",readParent));
        awaitBusinessResult("/api/example/demo-data/page","{\"reportCode\":\"sales-report\"}",r -> r.path("code").asInt()==30004,"row grant removal must deny business data");
        createDataGrant(dataKey("DEMO_DATA_SCOPE","team-t1","DATA_READ","INSTANCE"),readParent);
        assertDataCodes(List.of("A","B"));
        String productCode=fields.get("productName").path("resourceCode").asText();
        removeDataGrant(dataGrantId("DEMO_REPORT_FIELD",productCode,"DATA_READ",readParent));
        awaitBusinessResult("/api/example/demo-data/page","{\"reportCode\":\"sales-report\"}",r -> r.path("code").asInt()==200 && r.path("data").path("columns").size()==1,"field revoke must remove its descriptor and values");
        var explicit=postEnvelope(gateway()+"/api/example/demo-data/page",targetToken,JSON.createObjectNode().put("reportCode","sales-report")
            .set("fieldIds",JSON.createArrayNode().add(fields.get("productName").path("fieldId").asLong())).toString());
        assertThat(parseEnvelope(explicit).path("code").asInt()).isEqualTo(30004);
        createDataGrant(dataKey("DEMO_REPORT_FIELD",productCode,"DATA_READ","INSTANCE"),readParent);
        String recordCode=fields.get("recordCode").path("resourceCode").asText();
        removeDataGrant(dataGrantId("DEMO_REPORT_FIELD",recordCode,"DATA_READ",readParent));
        var hiddenNumber=awaitBusinessResult("/api/example/demo-data/get","{\"reportCode\":\"sales-report\",\"recordCode\":\"A\"}",
            r -> r.path("code").asInt()==200 && r.path("data").path("record").has("productName") && !r.path("data").path("record").has("recordCode"),"known record remains addressable without exposing its number");
        assertThat(hiddenNumber.path("data").path("record").size()).isEqualTo(1);
        createDataGrant(dataKey("DEMO_REPORT_FIELD",recordCode,"DATA_READ","INSTANCE"),readParent);
        removeDataGrant(dataGrantId("DEMO_DATA_SCOPE",null,"DATA_EDIT",editParent));
        createDataGrant(dataKey("DEMO_DATA_SCOPE","team-t1","DATA_EDIT","INSTANCE"),editParent);
        var mixed="{\"reportCode\":\"sales-report\",\"changes\":[{\"recordCode\":\"A\",\"remark\":\"changed\"},{\"recordCode\":\"C\",\"remark\":\"forbidden\"}]}";
        awaitBusinessResult("/api/example/demo-data/batch-update",mixed,r -> r.path("code").asInt()==30004,"mixed write must roll back");
        var restrictedRevoke=parseEnvelope(postEnvelope(gateway()+"/api/example/demo-data/revoke-import",targetToken,JSON.createObjectNode().put("importKey",dataImportKey).toString()));
        assertThat(restrictedRevoke.path("code").asInt()).isEqualTo(30004);
        assertThat(uploadCsv(targetToken,"sales-report",java.util.UUID.randomUUID().toString(),csv).path("code").asInt()).isEqualTo(30004);
        assertThat(demoAdminPost("/demo-data/get",JSON.createObjectNode().put("reportCode","sales-report").put("recordCode","A")).path("data").path("record").path("remark").asText()).isEqualTo("ra");
        var updated=awaitBusinessResult("/api/example/demo-data/update","{\"reportCode\":\"sales-report\",\"recordCode\":\"A\",\"remark\":\"blind edit\"}",r -> r.path("code").asInt()==200,"edit grant need not imply remark read grant");
        assertThat(updated.path("data").has("remark")).isFalse();
        String remarkCode=fields.get("remark").path("resourceCode").asText();
        removeDataGrant(dataGrantId("DEMO_REPORT_FIELD",remarkCode,"DATA_EDIT",editParent));
        awaitBusinessResult("/api/example/demo-data/update","{\"reportCode\":\"sales-report\",\"recordCode\":\"A\",\"remark\":\"denied\"}",r -> r.path("code").asInt()==30004,"field edit revocation must reject");
        createDataGrant(dataKey("DEMO_REPORT_FIELD",remarkCode,"DATA_EDIT","INSTANCE"),editParent);
        removeDataGrant(dataGrantId("DEMO_DATA_SCOPE","team-t1","DATA_EDIT",editParent));
        createDataGrant(dataKey("DEMO_DATA_SCOPE",null,"DATA_EDIT","ALL"),editParent);
        // Same physical field under another report does not inherit sales-report's field identity.
        demoAdminPost("/demo-report/create",JSON.createObjectNode().put("reportCode","isolated-view").put("name","Isolated").put("datasetCode","sales-records").put("fieldResourceTypeCode","DEMO_REPORT_FIELD"));
        awaitDemoPublication("DEMO_REPORT","isolated-view");
        var isolated=demoAdminPost("/demo-field/register",JSON.createObjectNode().put("reportCode","isolated-view").put("expectedCatalogVersion",0)
            .set("fieldKeys",JSON.createArrayNode().add("productName"))).path("data").path("items").get(0);
        awaitDemoPublication("DEMO_REPORT_FIELD",isolated.path("resourceCode").asText());
        createDataParent("isolated-view","DATA_READ",List.of(dataKey("DEMO_DATA_SCOPE","team-t1","DATA_READ","INSTANCE"),dataKey("DEMO_REPORT_FIELD",productCode,"DATA_READ","INSTANCE")));
        awaitBusinessResult("/api/example/demo-data/page","{\"reportCode\":\"isolated-view\"}",r -> r.path("code").asInt()==200 && r.path("data").path("state").asText().equals("EMPTY_FIELDS"),"foreign report field cannot reveal values");
        long isolatedParent=dataGrantId("DEMO_REPORT","isolated-view","DATA_READ",null);
        removeDataGrant(dataGrantId("DEMO_REPORT_FIELD",productCode,"DATA_READ",isolatedParent));
        createDataGrant(dataKey("DEMO_REPORT_FIELD",isolated.path("resourceCode").asText(),"DATA_READ","INSTANCE"),isolatedParent);
        awaitBusinessResult("/api/example/demo-data/page","{\"reportCode\":\"isolated-view\"}",r -> r.path("code").asInt()==200 && r.path("data").path("page").path("total").asInt()==2,"correct report field must allow");
        demoAdminPost("/demo-field/remove",JSON.createObjectNode().put("reportCode","isolated-view").put("expectedCatalogVersion",1)
            .set("fieldIds",JSON.createArrayNode().add(isolated.path("fieldId").asLong())));
        var recreated=demoAdminPost("/demo-field/register",JSON.createObjectNode().put("reportCode","isolated-view").put("expectedCatalogVersion",2)
            .set("fieldKeys",JSON.createArrayNode().add("productName"))).path("data").path("items").get(0);
        assertThat(recreated.path("fieldId").asLong()).isNotEqualTo(isolated.path("fieldId").asLong());
        awaitDemoPublication("DEMO_REPORT_FIELD",recreated.path("resourceCode").asText());
        var oldField=postEnvelope(gateway()+"/api/example/demo-data/page",targetToken,JSON.createObjectNode().put("reportCode","isolated-view")
            .set("fieldIds",JSON.createArrayNode().add(isolated.path("fieldId").asLong())).toString());
        assertThat(parseEnvelope(oldField).path("code").asInt()).isEqualTo(30004);
        createDataGrant(dataKey("DEMO_DATA_SCOPE","region-r1","DATA_READ","INSTANCE"),readParent);
        removeDataGrant(dataGrantId("DEMO_DATA_SCOPE","team-t1","DATA_READ",readParent));
        assertDataCodes(List.of("A","C"));
        var policy=demoAdminPost("/demo-policy/get",JSON.createObjectNode().put("datasetCode","sales-records")).path("data");
        var configuration=policy.path("configuration").deepCopy();
        for(var binding:configuration.path("bindings")) if(binding.path("resourceCode").asText().equals("region-r1"))
            ((tools.jackson.databind.node.ObjectNode)binding.path("predicates").get(0)).put("value","R2");
        demoAdminPost("/demo-policy/save",JSON.createObjectNode().put("datasetCode","sales-records").put("expectedVersion",policy.path("version").asLong()).set("configuration",configuration));
        assertDataCodes(List.of("B","D"));
        demoAdminPost("/demo-policy/save",JSON.createObjectNode().put("datasetCode","sales-records").put("expectedVersion",policy.path("version").asLong()+1).set("configuration",policy.path("configuration")));
        removeDataGrant(dataGrantId("DEMO_DATA_SCOPE","region-r1","DATA_READ",readParent));
        var conditional=dataKey("DEMO_DATA_SCOPE","team-t1","DATA_READ","INSTANCE");
        String future=java.time.LocalDate.now(java.time.ZoneOffset.UTC).plusYears(5).toString();
        String rules="{\"logic\":\"AND\",\"items\":[{\"type\":\"DATE_RANGE\",\"params\":{\"start\":\""+future+"\",\"end\":\""+future+"\"}}]}";
        conditional.set("inlineCondition",JSON.createObjectNode().put("name","future only").put("conditionRules",rules));
        createDataGrant(conditional,null);
        awaitBusinessResult("/api/example/demo-data/page","{\"reportCode\":\"sales-report\"}",r -> r.path("code").asInt()==200 && r.path("data").path("state").asText().equals("EMPTY_ROWS"),"condition-filtered scope must be EMPTY rather than DENIED or all rows");
        removeDataGrant(dataGrantId("DEMO_DATA_SCOPE","team-t1","DATA_READ",null));
        createDataGrant(dataKey("DEMO_DATA_SCOPE","team-t1","DATA_READ","INSTANCE"),readParent);
        assertDataCodes(List.of("A","B"));
    }

    @Test
    @Order(16)
    void sameRecordCodeAndImportKeyRemainIsolatedAcrossRealTenants() throws Exception {
        var login=E2eTenantSupport.login(gateway(),"e2e-second","admin",BOOTSTRAP_ADMIN_PASSWORD,ExampleBusinessFinalCheckE2EIT::readCaptchaFromRedis);
        String token=login.path("accessToken").asText(); long owner=login.path("userId").asLong();
        assertThat(token).isNotBlank(); assertThat(owner).isPositive();
        postForData(gateway()+"/api/access/service-config/save",token,JSON.createObjectNode().put("serviceCode",SERVICE).put("name","Tenant two example").put("status",1));
        var credentials=postForData(gateway()+"/api/access/service-credential/list",token,JSON.createObjectNode().put("serviceCode",SERVICE)).path("items");
        long credential=0;
        for(var entry:credentials) if(entry.path("credentialId").asText().equals("sc-example-e2e-2")) credential=entry.path("id").asLong();
        assertThat(credential).isPositive();
        postForData(gateway()+"/api/access/service-credential/update",token,JSON.createObjectNode().put("id",credential).put("status",1));
        for(String type:List.of("DEMO_REPORT","DEMO_REPORT_FIELD","DEMO_DATA_SCOPE","DEMO_POLICY","DEMO_DATASET","DEMO_TASK")) {
            boolean synced=List.of("DEMO_REPORT","DEMO_REPORT_FIELD","DEMO_DATA_SCOPE").contains(type);
            String extra=synced?"{\"managedMode\":\"SYNC\",\"syncSourceService\":\"example-service\"}":"{\"managedMode\":\"MANAGED\"}";
            postForData(gateway()+"/api/access/type-definition/create",token,JSON.createObjectNode().put("typeKey","resource_type").put("typeCode",type).put("name",type).put("extra",extra));
            var operations=synced?List.of("DATA_READ","DATA_EDIT"):type.equals("DEMO_TASK")?List.of("TRIGGER","ENABLE"):List.of("MANAGE");
            long bit=16;
            for(String operation:operations) {
                postForData(gateway()+"/api/access/operation-permission/create",token,JSON.createObjectNode().put("resourceTypeCode",type).put("code",operation).put("name",operation).put("binaryBit",bit).put("inheritMask",0)); bit<<=1;
            }
        }
        var manifest=JSON.readTree(Files.readString(Path.of("..","example-service","examples","permission-demo-interfaces.json")));
        var synced=postEnvelope("http://localhost:"+accessService.port()+"/api/access/service-config/sync-v2",null,manifest.toString(),
            Map.of("X-Credential-Id","sc-example-e2e-2","X-Credential-Secret","sk-example-e2e-2"));
        assertThat(parseEnvelope(synced).path("code").asInt()).isEqualTo(200);
        ensureDataSetup(token);
        String csv="recordCode,ownerUserId,teamCode,departmentCode,regionCode,saleDate,productName,amount,remark\n"
            +"A,"+owner+",T1,D1,R1,2026-10-19,TenantTwo,99.00,private\n";
        assertThat(uploadCsv(token,"sales-report",dataImportKey,csv).path("code").asInt()).isEqualTo(200);
        var second=demoPost(token,"/demo-data/page",JSON.createObjectNode().put("reportCode","sales-report")).path("data").path("page");
        assertThat(second.path("total").asInt()).isEqualTo(1);
        assertThat(second.path("items").get(0).path("productName").asText()).isEqualTo("TenantTwo");
        assertDataCodes(List.of("A","B"));
        var first=awaitBusinessResult("/api/example/demo-data/get","{\"reportCode\":\"sales-report\",\"recordCode\":\"A\"}",r -> r.path("code").asInt()==200,"tenant one stays readable");
        assertThat(first.path("data").path("record").path("productName").asText()).isEqualTo("Alpha");
        assertThat(demoPost(token,"/demo-data/import-status",JSON.createObjectNode().put("importKey",dataImportKey)).path("data").path("recordCount").asInt()).isEqualTo(1);
        var own=postEnvelope(gateway()+"/api/example/demo-data/import-status",targetToken,JSON.createObjectNode().put("importKey",dataImportKey).toString());
        assertThat(parseEnvelope(own).path("data").path("recordCount").asInt()).isEqualTo(4);
    }

    private static tools.jackson.databind.node.ObjectNode dataKey(String type,String code,String operation,String mode) {
        var key=JSON.createObjectNode().put("resourceTypeCode",type).put("operationCode",operation).put("scopeMode",mode).put("canGrant",false);
        if(code==null) { key.putNull("resourceCode"); key.putNull("codeType"); }
        else { key.put("resourceCode",code); key.put("codeType","default"); }
        return key;
    }
    @Test
    @Order(17)
    void persistentTasksRecheckRealGrantsAndPreserveCompletedArtifactsAcrossRestart() throws Exception {
        for(String operation:List.of("CREATE","VIEW","UPDATE","ENABLE","TRIGGER")) createDataGrant(dataKey("DEMO_TASK",null,operation,"ALL"),null);
        createDataGrant(dataKey("DEMO_REPORT","sales-report","EXPORT","INSTANCE"),null);
        var selection=JSON.createObjectNode().put("reportCode","sales-report");
        var task=targetDemo("/demo-task/create",JSON.createObjectNode().put("name","owned snapshot").put("cron","0 0 0 * * *").set("selection",selection));
        long taskId=task.path("taskId").asLong(); assertThat(task.path("enabled").asBoolean()).isFalse();
        task=targetDemo("/demo-task/set-enabled",JSON.createObjectNode().put("taskId",taskId).put("expectedVersion",task.path("version").asLong()).put("enabled",true));
        var trigger=JSON.createObjectNode().put("taskId",taskId).put("triggerKey",java.util.UUID.randomUUID().toString());
        var queued=targetDemo("/demo-task/trigger",trigger); String key=queued.path("executionKey").asText();
        var completed=awaitExecution(key,"SUCCESS"); assertThat(completed.path("recordCount").asInt()).isEqualTo(2);
        assertThat(targetDemo("/demo-task/trigger",trigger).path("executionKey").asText()).isEqualTo(key);
        byte[] original=downloadSnapshot(key);
        assertThat(new String(original,StandardCharsets.UTF_8)).contains("Alpha","Beta","productName").doesNotContain("Gamma","amount");
        var foreign=parseEnvelope(postEnvelope(gateway()+"/api/example/demo-task/get",adminToken,JSON.createObjectNode().put("taskId",taskId).toString()));
        assertThat(foreign.path("code").asInt()).isEqualTo(30004);
        var fieldRows=demoAdminPost("/demo-field/list",JSON.createObjectNode().put("reportCode","sales-report")).path("data").path("items");
        JsonNode product=null; for(var field:fieldRows) if(field.path("fieldKey").asText().equals("productName")) product=field;
        assertThat(product).isNotNull();
        long parent=dataGrantId("DEMO_REPORT","sales-report","DATA_READ",null);
        String productCode=product.path("resourceCode").asText();
        restartExampleWorker(false);
        assertThat(targetDemo("/demo-task/get",JSON.createObjectNode().put("taskId",taskId)).path("name").asText()).isEqualTo("owned snapshot");
        assertThat(downloadSnapshot(key)).containsExactly(original);
        var explicit=JSON.createObjectNode().put("reportCode","sales-report").set("fieldIds",JSON.createArrayNode().add(product.path("fieldId").asLong()));
        var frozen=targetDemo("/demo-task/create",JSON.createObjectNode().put("name","frozen field").put("cron","0 0 0 * * *").set("selection",explicit));
        long frozenId=frozen.path("taskId").asLong();
        targetDemo("/demo-task/set-enabled",JSON.createObjectNode().put("taskId",frozenId).put("expectedVersion",frozen.path("version").asLong()).put("enabled",true));
        var pending=targetDemo("/demo-task/trigger",JSON.createObjectNode().put("taskId",frozenId).put("triggerKey",java.util.UUID.randomUUID().toString()));
        assertThat(pending.path("status").asText()).isEqualTo("PENDING");
        removeDataGrant(dataGrantId("DEMO_REPORT_FIELD",productCode,"DATA_READ",parent));
        awaitBusinessResult("/api/example/demo-data/page",selection.toString(),r->r.path("code").asInt()==200 && r.path("data").path("columns").size()==1,"field removal must be visible before restart");
        restartExampleWorker(true);
        awaitExecution(pending.path("executionKey").asText(),"DENIED");
        assertThat(downloadSnapshot(key)).containsExactly(original);
        createDataGrant(dataKey("DEMO_REPORT_FIELD",productCode,"DATA_READ","INSTANCE"),parent);
        awaitBusinessResult("/api/example/demo-data/page",selection.toString(),r->r.path("code").asInt()==200 && r.path("data").path("columns").size()==2,"restored field must be visible");
        // The same actor follows SELF -> department -> team, with the same records and SQL configuration.
        removeDataGrant(dataGrantId("DEMO_DATA_SCOPE","team-t1","DATA_READ",parent));
        createDataGrant(dataKey("DEMO_DATA_SCOPE","self","DATA_READ","INSTANCE"),parent); assertDataCodes(List.of("A"));
        removeDataGrant(dataGrantId("DEMO_DATA_SCOPE","self","DATA_READ",parent));
        createDataGrant(dataKey("DEMO_DATA_SCOPE","department-d1","DATA_READ","INSTANCE"),parent); assertDataCodes(List.of("A","B","C"));
        removeDataGrant(dataGrantId("DEMO_DATA_SCOPE","department-d1","DATA_READ",parent));
        var conditional=dataKey("DEMO_DATA_SCOPE","team-t1","DATA_READ","INSTANCE");
        String rules="{\"logic\":\"AND\",\"items\":[{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"127.0.0.1\",\"::1\"]}}]}";
        conditional.set("inlineCondition",JSON.createObjectNode().put("name","online loopback only").put("conditionRules",rules));
        createDataGrant(conditional,null); assertDataCodes(List.of("A","B"));
        var noIp=targetDemo("/demo-task/trigger",JSON.createObjectNode().put("taskId",taskId).put("triggerKey",java.util.UUID.randomUUID().toString()));
        awaitExecution(noIp.path("executionKey").asText(),"EMPTY");
        removeDataGrant(dataGrantId("DEMO_DATA_SCOPE","team-t1","DATA_READ",null));
        createDataGrant(dataKey("DEMO_DATA_SCOPE","team-t1","DATA_READ","INSTANCE"),parent); assertDataCodes(List.of("A","B"));
        String scriptHost=System.getProperty("example.demo.scriptHost");
        if(scriptHost!=null && !scriptHost.isBlank()) {
            Path output=Path.of("target","e2e","snapshot-"+java.util.UUID.randomUUID()+".csv").toAbsolutePath();
            var builder=new ProcessBuilder(scriptHost,"-NoProfile","-File",Path.of("..","tools","export-example-snapshot.ps1").toAbsolutePath().toString(),
                "-BaseUrl",gateway(),"-AllowInsecure","-SelectionPath",Path.of("..","example-service","examples","permission-demo-selection.json").toAbsolutePath().toString(),"-OutputPath",output.toString());
            builder.environment().put("EXAMPLE_OPERATOR_TOKEN",targetToken);
            builder.redirectErrorStream(true).redirectOutput(Path.of(output.toString()+".log").toFile());
            var process=builder.start();
            try {
                assertThat(process.waitFor(150,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThat(process.exitValue()).as("snapshot script: %s",Files.readString(Path.of(output.toString()+".log"))).isZero();
                assertThat(Files.readString(output)).contains("Alpha","Beta").doesNotContain("Gamma","amount");
            } finally { if(process.isAlive()) process.destroyForcibly(); }
        }
        // Real automatic cron dispatch, bounded by one minute and polled by its durable execution record.
        var automatic=targetDemo("/demo-task/create",JSON.createObjectNode().put("name","minute cron").put("cron","0 * * * * *").set("selection",selection));
        long automaticId=automatic.path("taskId").asLong();
        var enabled=targetDemo("/demo-task/set-enabled",JSON.createObjectNode().put("taskId",automaticId).put("expectedVersion",automatic.path("version").asLong()).put("enabled",true));
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(90)).untilAsserted(()->{
            var executions=targetDemo("/demo-execution/page",JSON.createObjectNode().put("taskId",automaticId).put("pageSize",20)).path("items");
            assertThat(executions.isEmpty()).isFalse();
            var execution=executions.get(0); assertThat(execution.path("status").asText()).isEqualTo("SUCCESS");
            assertThat(execution.path("scheduledTime").asText()).isNotBlank(); assertThat(execution.path("recordCount").asInt()).isEqualTo(2);
        });
        targetDemo("/demo-task/set-enabled",JSON.createObjectNode().put("taskId",automaticId).put("expectedVersion",enabled.path("version").asLong()).put("enabled",false));
    }

    private static JsonNode targetDemo(String path,JsonNode body) throws Exception {
        return awaitBusinessResult("/api/example"+path,body.toString(),r->r.path("code").asInt()==200,"target task operation "+path).path("data");
    }
    private static JsonNode awaitExecution(String key,String state) throws Exception {
        return awaitBusinessResult("/api/example/demo-execution/get",JSON.createObjectNode().put("executionKey",key).toString(),
            r->r.path("code").asInt()==200 && state.equals(r.path("data").path("status").asText()),"execution reaches "+state).path("data");
    }
    private static byte[] downloadSnapshot(String key) throws Exception {
        var response=HTTP.send(HttpRequest.newBuilder(URI.create(gateway()+"/api/example/demo-execution/download"))
            .header("Authorization","Bearer "+targetToken).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JSON.createObjectNode().put("executionKey",key).toString())).build(),HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("text/csv"); return response.body();
    }
    private static void restartExampleWorker(boolean enabled) throws Exception {
        int port=exampleService.port(); exampleService.destroy();
        var args=new java.util.ArrayList<>(exampleArguments); args.add("--example.execution.enabled="+enabled);
        exampleService=startService("example-service",EXAMPLE_MAIN_CLASS,args,exampleEnvironment,
            URI.create("http://localhost:"+port+"/api/example/demo/hello"),EXAMPLE_SERVICE_CLASSES_DIR);
    }

    private static void createDataParent(String report,String operation,List<tools.jackson.databind.node.ObjectNode> children) {
        var create=JSON.createObjectNode(); create.set("key",dataKey("DEMO_REPORT",report,operation,"INSTANCE"));
        var nested=JSON.createArrayNode(); children.forEach(nested::add); create.set("children",nested);
        dataGrantPlan(JSON.createArrayNode().add(create),JSON.createArrayNode());
    }
    private static void createDataGrant(tools.jackson.databind.node.ObjectNode key,Long parent) {
        var create=JSON.createObjectNode(); create.set("key",key); if(parent!=null) create.put("parentPermissionId",parent);
        dataGrantPlan(JSON.createArrayNode().add(create),JSON.createArrayNode());
    }
    private static void removeDataGrant(long id) { dataGrantPlan(JSON.createArrayNode(),JSON.createArrayNode().add(id)); }
    private static void dataGrantPlan(JsonNode creates,JsonNode removes) {
        var plan=JSON.createObjectNode(); plan.set("creates",creates); plan.set("removes",removes);
        postForData(gateway()+"/api/access/role-resource-permission/apply-grant-plan",adminToken,
            JSON.createObjectNode().put("roleTypeCode",ROLE_TYPE).put("roleExternalId",ROLE_EXTERNAL_ID).set("plan",plan));
    }
    private static long dataGrantId(String type,String code,String operation,Long parent) {
        var rows=postForData(gateway()+"/api/access/role-resource-permission/list",adminToken,
            JSON.createObjectNode().put("roleTypeCode",ROLE_TYPE).put("roleExternalId",ROLE_EXTERNAL_ID).put("includeChildren",true)).path("items");
        for(var row:rows) if(row.path("resourceTypeCode").asText().equals(type) && Objects.equals(row.hasNonNull("resourceCode")?row.path("resourceCode").asText():null,code)
            && row.path("operationCode").asText().equals(operation) && Objects.equals(row.hasNonNull("dependOn")?row.path("dependOn").asLong():null,parent)) return row.path("id").asLong();
        throw new AssertionError("Grant not found: "+type+"/"+operation);
    }
    private static void assertDataCodes(List<String> expected) throws Exception {
        var result=awaitBusinessResult("/api/example/demo-data/page","{\"reportCode\":\"sales-report\",\"pageSize\":20}",r -> {
            List<String> codes=new java.util.ArrayList<>(); r.path("data").path("page").path("items").forEach(item -> codes.add(item.path("recordCode").asText()));
            boolean noAmount=true; for(var item:r.path("data").path("page").path("items")) if(item.has("amount")) noAmount=false;
            return r.path("code").asInt()==200 && codes.equals(expected) && noAmount;
        },"actual SQL rows must match granted scopes");
        for(var row:result.path("data").path("page").path("items")) assertThat(row.has("amount")).isFalse();
    }

    private static void ensureDataSetup() throws Exception { ensureDataSetup(adminToken); }

    private static void ensureDataSetup(String token) throws Exception {
        var current=parseEnvelope(postEnvelope(gateway()+"/api/example/demo-policy/get",token,"{\"datasetCode\":\"sales-records\"}"));
        if(current.path("code").asInt()==200) return;
        assertThat(current.path("code").asInt()).isEqualTo(30102);
        var create=parseEnvelope(postEnvelope(gateway()+"/api/example/demo-report/create",token,
            "{\"reportCode\":\"sales-report\",\"name\":\"Sales\",\"datasetCode\":\"sales-records\",\"fieldResourceTypeCode\":\"DEMO_REPORT_FIELD\"}"));
        assertThat(create.path("code").asInt()).isIn(200,30103);
        awaitDemoPublication(token,"DEMO_REPORT","sales-report");
        var report=demoPost(token,"/demo-report/get",JSON.createObjectNode().put("reportCode","sales-report")).path("data");
        var currentFields=demoPost(token,"/demo-field/list",JSON.createObjectNode().put("reportCode","sales-report")).path("data").path("items");
        var configured=JSON.readTree(Files.readString(Path.of("..","example-service","examples","permission-demo-policy.json")));
        var keys=JSON.createArrayNode();
        for(var field:configured.path("fields")) {
            boolean exists=false; for(var registered:currentFields) if(registered.path("fieldKey").asText().equals(field.path("fieldKey").asText())) exists=true;
            if(!exists) keys.add(field.path("fieldKey").asText());
        }
        if(!keys.isEmpty()) demoPost(token,"/demo-field/register",JSON.createObjectNode().put("reportCode","sales-report")
            .put("expectedCatalogVersion",report.path("catalogVersion").asLong()).set("fieldKeys",keys));
        var fields=demoPost(token,"/demo-field/list",JSON.createObjectNode().put("reportCode","sales-report")).path("data").path("items");
        for(var field:fields) awaitDemoPublication(token,"DEMO_REPORT_FIELD",field.path("resourceCode").asText());
        for(var binding:configured.path("bindings")) {
            String code=binding.path("resourceCode").asText();
            demoPost(token,"/demo-scope/save",JSON.createObjectNode().put("resourceTypeCode","DEMO_DATA_SCOPE").put("resourceCode",code)
                .put("name",code).put("enabled",true).put("expectedVersion",0));
            awaitDemoPublication(token,"DEMO_DATA_SCOPE",code);
        }
        demoPost(token,"/demo-policy/save",JSON.createObjectNode().put("datasetCode","sales-records").put("expectedVersion",0).set("configuration",configured));
    }

    private static JsonNode uploadCsv(String token,String report,String key,String csv) throws Exception {
        String boundary="example-"+java.util.UUID.randomUUID();
        String request=JSON.createObjectNode().put("reportCode",report).put("importKey",key).toString();
        String body="--"+boundary+"\r\nContent-Disposition: form-data; name=\"request\"\r\nContent-Type: application/json\r\n\r\n"+request
            +"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"records.csv\"\r\nContent-Type: text/csv\r\n\r\n"+csv+"\r\n--"+boundary+"--\r\n";
        var response=HTTP.send(HttpRequest.newBuilder(URI.create(gateway()+"/api/example/demo-data/import"))
            .timeout(Duration.ofSeconds(30)).header("Authorization","Bearer "+token).header("Content-Type","multipart/form-data; boundary="+boundary)
            .POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)).build(),HttpResponse.BodyHandlers.ofString());
        return JSON.readTree(response.body());
    }

    private static JsonNode demoAdminPost(String path,JsonNode body) { return demoPost(adminToken,path,body); }
    private static void awaitDemoPublication(String type,String code) { awaitDemoPublication(adminToken,type,code); }

    private static JsonNode demoPost(String token,String path, JsonNode body) {
        var response=new java.util.concurrent.atomic.AtomicReference<JsonNode>();
        org.awaitility.Awaitility.await().atMost(STALE_WINDOW).untilAsserted(() -> {
            var result=parseEnvelope(postEnvelope(gateway()+"/api/example"+path,token,body.toString()));
            assertThat(result.path("code").asInt()).as("demo admin API %s: %s",path,result).isEqualTo(200);
            response.set(result);
        });
        return response.get();
    }

    private static void awaitDemoPublication(String token,String type, String code) {
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            var result=parseEnvelope(postEnvelope(gateway()+"/api/example/demo-publication/get",token,
                JSON.createObjectNode().put("resourceTypeCode",type).put("resourceCode",code).toString()));
            assertThat(result.path("code").asInt()).isEqualTo(200);
            assertThat(result.path("data").path("status").asText()).as("publication result: %s",result).isEqualTo("PUBLISHED");
        });
    }

    private static Map<String, String> signedExampleIdentity(String tenant, long user) throws Exception {
        long timestamp = System.currentTimeMillis() / 1000;
        var mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(SIGNATURE_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = java.util.HexFormat.of().formatHex(mac.doFinal(
            (user + "|" + tenant + "|" + timestamp).getBytes(StandardCharsets.UTF_8)));
        return Map.of("X-User-Id", Long.toString(user), "X-Tenant-Id", tenant,
            "X-User-Signature", signature, "X-Signature-Timestamp", Long.toString(timestamp));
    }

    private static int typeValue() throws Exception {
        try (var conn = java.sql.DriverManager.getConnection(
            postgres.getJdbcUrl() + "?stringtype=unspecified", postgres.getUsername(), postgres.getPassword());
             var st = conn.createStatement();
             var rs = st.executeQuery(
                "SELECT type_value FROM type_definition WHERE tenant_id = 1 AND type_key = 'resource_type' AND type_code = '"
                + TYPE + "' AND delete_flag = 0")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static long generation() throws Exception {
        try (var conn = java.sql.DriverManager.getConnection(
            postgres.getJdbcUrl() + "?stringtype=unspecified", postgres.getUsername(), postgres.getPassword());
             var st = conn.createStatement();
             var rs = st.executeQuery(
                "SELECT config_generation FROM service_config WHERE tenant_id = 1 AND service_code = 'example-service'")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    // ------------------------------------------------------------------
    // 业务等待辅助
    // ------------------------------------------------------------------

    /** 30 秒窗口内轮询业务接口直至信封 code 命中期望（容忍 403/503/30004 过渡态）。 */
    private static JsonNode awaitBusinessResult(String path, String body,
                                                java.util.function.Predicate<JsonNode> expectation,
                                                String failMessage) throws IOException {
        long deadline = System.nanoTime() + STALE_WINDOW.toNanos();
        IOException lastError = null;
        JsonNode last = null;
        while (System.nanoTime() < deadline) {
            try {
                EnvelopeResult r = postEnvelope(gateway() + path, targetToken, body);
                if (r.status() != 200) lastError = new IOException("HTTP " + r.status() + ": " + r.rawBody());
                if (r.status() == 200) {
                    last = parseEnvelope(r);
                    if (expectation.test(last)) {
                        return last;
                    }
                }
            } catch (IOException e) {
                lastError = e;
            }
            sleepQuiet(1000);
        }
        throw new AssertionError(failMessage + "（30 秒窗口耗尽；最后信封=" + last + "，最后错误=" + lastError + "）");
    }

    /** 30 秒窗口内轮询导出作业至期望终态。 */
    private static void awaitJobTerminal(String jobId, String expectedStatus, String failMessage) throws IOException {
        long deadline = System.nanoTime() + STALE_WINDOW.toNanos();
        JsonNode last = null;
        while (System.nanoTime() < deadline) {
            EnvelopeResult r = postEnvelope(gateway() + "/api/example/report/export/status",
                targetToken, "{\"jobId\":\"" + jobId + "\"}");
            if (r.status() == 200) {
                last = parseEnvelope(r).path("data");
                if (expectedStatus.equals(last.path("status").asText())) {
                    return;
                }
                assertThat(last.path("status").asText())
                    .as(failMessage + "（不允许先进入其他终态，最后状态=" + last + "）")
                    .isIn("PENDING", "RUNNING", expectedStatus);
            }
            sleepQuiet(500);
        }
        throw new AssertionError(failMessage + "（30 秒窗口耗尽；最后状态=" + last + "）");
    }

    /** 30 秒窗口内轮询至期望 HTTP 状态（容忍集外立即失败）。 */
    private static void awaitHttpStatus(String path, String body, int expectedStatus,
                                        IntPredicate tolerated, String failMessage) throws IOException {
        long deadline = System.nanoTime() + STALE_WINDOW.toNanos();
        while (System.nanoTime() < deadline) {
            EnvelopeResult r = postEnvelope(gateway() + path, targetToken, body);
            if (r.status() == expectedStatus) {
                return;
            }
            assertThat(tolerated.test(r.status()))
                .as(failMessage + "（等待期状态 " + r.status() + " 超出容忍集，响应：" + r.rawBody() + "）")
                .isTrue();
            sleepQuiet(1000);
        }
        throw new AssertionError(failMessage + "（30 秒窗口耗尽）");
    }

    // ------------------------------------------------------------------
    // 子进程服务管理（与 ExampleProtectedApiE2EIT 同模式）
    // ------------------------------------------------------------------

    private static List<String> accessServiceArgs(int port) {
        return List.of(
            "--server.port=" + port,
            // T-PERM-094：access-service 引入 actuator 后管理端口默认固定 9101，
            // 并发 E2E/本机 dev 服务会冲突——子进程随机分配（就绪探针走业务端点，不探管理端口）
            "--management.server.port=0",
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
            "--spring.cloud.gateway.server.webflux.enabled=false",
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
                        "--perm.client.enabled=false");
    }

    private static Map<String, String> accessServiceEnv() {
        return Map.of(
            "ACCESS_PLATFORM_BOOTSTRAP_ENABLED", "true",
            "ACCESS_PLATFORM_ADMIN_PASSWORD", BOOTSTRAP_ADMIN_PASSWORD,
            "JWT_SECRET_KEY", JWT_SECRET,
            "ACCESSMESH_SIGNATURE_SECRET", SIGNATURE_SECRET,
            "PERM_INTERNAL_SECRET", INTERNAL_SECRET);
    }

    /**
     * example-service 子进程参数：共享类路径带入 gateway/access 依赖树自动配置（非本服务职责
     * 的须排除，同既有形态）；<b>Feign/LoadBalancer 保持启用</b>（业务最终检查经 perm-client
     * SDK 调 access-service），access-service 经 SimpleDiscoveryClient 静态实例解析。
     */
    private static List<String> exampleServiceArgs(int port, int accessPort) {
        return List.of(
            "--spring.main.web-application-type=servlet",
            "--server.port=" + port,
            "--spring.config.import=optional:classpath:/e2e-nope.yml",
            "--spring.cloud.nacos.config.enabled=false",
            "--spring.cloud.nacos.config.import-check.enabled=false",
            "--spring.cloud.nacos.discovery.enabled=false",
            "--spring.cloud.gateway.server.webflux.enabled=false",
            "--example.export.delay-ms=" + EXPORT_DELAY_MS,
            "--spring.cloud.discovery.client.simple.instances.access-service[0].uri=http://localhost:" + accessPort,
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
                + "org.redisson.spring.starter.RedissonAutoConfigurationV4,"
                + "cn.ac.fage.accessmesh.common.cache.RedissonCacheAutoConfiguration");
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
            // The shared E2E classpath contains Tomcat; production Gateway uses Netty only.
            "--spring.autoconfigure.exclude=org.springframework.boot.tomcat.autoconfigure.reactive.TomcatReactiveWebServerAutoConfiguration,"
                + "org.springframework.boot.tomcat.autoconfigure.actuate.web.server.TomcatReactiveManagementContextAutoConfiguration,"
                + "org.redisson.spring.starter.RedissonAutoConfigurationV4,"
                + "cn.ac.fage.accessmesh.common.cache.RedissonCacheAutoConfiguration,"
                + "cn.dev33.satoken.spring.SaTokenContextRegister,"
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration,"
                + "com.mybatisflex.spring.boot.v4.MybatisFlexAutoConfiguration",
            "--spring.cloud.discovery.client.simple.instances.access-service[0].uri=http://localhost:" + accessPort,
            "--spring.cloud.discovery.client.simple.instances.example-service[0].uri=http://localhost:" + examplePort,
            "--perm.client.enabled=false");
    }

    private static Map<String, String> gatewayEnv() {
        return Map.of(
            "ACCESSMESH_SIGNATURE_SECRET", SIGNATURE_SECRET,
            "PERM_INTERNAL_SECRET", INTERNAL_SECRET);
    }

    private static ServiceHandle startService(String name, String mainClass, List<String> args,
                                              Map<String, String> env, URI readinessUrl,
                                              String prependClassesDir) throws Exception {
        if (!Files.exists(Path.of(ACCESS_SERVICE_CLASSES_DIR))) {
            throw new IllegalStateException("缺少 access-service 生产 classes（" + ACCESS_SERVICE_CLASSES_DIR
                + "）——请先构建上游模块");
        }
        if (!Files.exists(Path.of(EXAMPLE_SERVICE_CLASSES_DIR))) {
            throw new IllegalStateException("缺少 example-service 生产 classes（" + EXAMPLE_SERVICE_CLASSES_DIR
                + "）——请先构建上游模块");
        }
        String baseClasspath = filterOutTestClasses(System.getProperty("java.class.path"));
        String childClasspath = prependClassesDir != null
            ? prependClassesDir + java.io.File.pathSeparator + baseClasspath
            : baseClasspath;
        ServiceHandle handle = E2eProcessSupport.launch(name, mainClass, args, env, childClasspath);
        return E2eProcessSupport.readyOrDestroy(handle, () -> waitReady(name, readinessUrl, handle));
    }

    private static void waitReady(String name, URI url, ServiceHandle handle) throws InterruptedException {
        boolean postProbe = url.getPath().endsWith("/api/access/auth/captcha")
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
                "SELECT count(*) FROM platform_account WHERE username = 'admin' AND delete_flag = 0")) {
                if (rs.next() && rs.getInt(1) == 1) {
                    long tenant = E2eTenantSupport.openTenant("http://localhost:" + accessService.port(),
                        BOOTSTRAP_ADMIN_PASSWORD,E2eTenantSupport.TENANT_CODE,BOOTSTRAP_ADMIN_PASSWORD,ExampleBusinessFinalCheckE2EIT::readCaptchaFromRedis);
                    assertThat(tenant).isEqualTo(1L);
                    return;
                }
            } catch (java.sql.SQLException e) {
                // 容器就绪瞬间连接抖动可容忍，下一轮重试
            }
            Thread.sleep(1000);
        }
        throw new IllegalStateException("平台初始管理员 2 分钟内未落库");
    }

    // ------------------------------------------------------------------
    // HTTP / Redis 辅助
    // ------------------------------------------------------------------

    private static String gateway() {
        return "http://localhost:" + gatewayService.port();
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
        } catch (tools.jackson.core.JacksonException e) {
            throw new IllegalStateException("响应不是合法 JSON：" + r.rawBody(), e);
        }
    }

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
        JsonNode envelope;
        try {
            envelope = JSON.readTree(raw);
        } catch (tools.jackson.core.JacksonException e) {
            throw new IllegalStateException("响应不是合法 JSON: " + raw, e);
        }
        assertThat(envelope.path("code").asInt())
            .as("管理链路 " + url + " 信封 code 必须 200，响应：" + raw).isEqualTo(200);
        return envelope.path("data");
    }

    private static String realLogin(String username, String password) {
        JsonNode captcha = postForData(gateway() + "/api/access/auth/captcha", null, JSON.createObjectNode());
        String captchaId = captcha.path("captchaId").asText();
        assertThat(captchaId).as("验证码必须真实签发").isNotBlank();
        String code = readCaptchaFromRedis(captchaId);
        assertThat(code).as("Redis 必须存有验证码答案（真实签发链路）").isNotBlank();

        JsonNode login = postForData(gateway() + "/api/access/auth/login", null,
            JSON.createObjectNode()
                .put("tenantCode", E2eTenantSupport.TENANT_CODE)
                .put("username", username)
                .put("password", password)
                .put("captchaId", captchaId)
                .put("captchaCode", code)
                .put("clientId", CLIENT_ID));
        return E2eTenantSupport.finishForcedLogin(gateway(),login,username,ExampleBusinessFinalCheckE2EIT::readCaptchaFromRedis);
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
