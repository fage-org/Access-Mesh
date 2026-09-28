package cn.ac.fage.accessmesh.e2e;

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
 * （暂停→切模式→恢复：代次单调递增、空快照 DENY、恢复以库为准）。
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
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
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
    private static final ObjectMapper JSON = new ObjectMapper();

    private static ServiceHandle accessService;
    private static ServiceHandle gatewayService;
    private static ServiceHandle exampleService;

    private static String adminToken;
    private static long targetUserId;
    private static String targetInitialPassword;
    private static String targetToken;

    @BeforeAll
    static void bootStack() throws Exception {
        String ddl = Files.readString(DDL_PATH, StandardCharsets.UTF_8);
        try (var conn = java.sql.DriverManager.getConnection(
            postgres.getJdbcUrl() + "?stringtype=unspecified", postgres.getUsername(), postgres.getPassword());
             var st = conn.createStatement()) {
            st.execute(ddl);
            st.execute("INSERT INTO service_config (tenant_id, service_code, name, status) VALUES ("
                + "1, 'example-service', 'Example Service', 1)");
        }

        int accessPort = freePort();
        int gatewayPort = freePort();
        int gatewayMgmtPort = freePort();
        int examplePort = freePort();

        accessService = startService("access-service", ACCESS_MAIN_CLASS,
            accessServiceArgs(accessPort), accessServiceEnv(),
            URI.create("http://localhost:" + accessPort + "/api/access/auth/captcha"), ACCESS_SERVICE_CLASSES_DIR);
        waitAdminSeeded();

        exampleService = startService("example-service", EXAMPLE_MAIN_CLASS,
            exampleServiceArgs(examplePort, accessPort),
            Map.of("JWT_SECRET_KEY", JWT_SECRET,
                "ACCESSMESH_SIGNATURE_SECRET", SIGNATURE_SECRET,
                "PERM_INTERNAL_SECRET", INTERNAL_SECRET),
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

    private static com.fasterxml.jackson.databind.node.ObjectNode api(String name, String method, String path, String op) {
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
            "{\"keyword\":null,\"page\":1,\"size\":10}", b -> b.path("code").asInt() == 200, "列表必须成功");
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
    @DisplayName("⑪ 服务级暂停切换 runbook 演练：暂停→切模式→恢复，代次单调递增+恢复以库为准")
    void step11_servicePauseSwitchResumeDrill() throws Exception {
        // 基线代次（步骤⑦已把 report-1 家族资源建齐；此处只动 service_config）
        long g0 = generation();
        // ① 暂停（status=0）：模式/启停变化同事务 +1
        saveServiceConfig(0, null);
        long g1 = generation();
        assertThat(g1).as("暂停必须递增配置代次").isGreaterThan(g0);
        // 空路由快照（停用服务）→ 网关本地无命中 DENY 403（容忍过渡期 200/503）
        awaitHttpStatus("/api/example/report/view", "{\"reportCode\":\"report-1\"}", 403,
            s -> s == 200 || s == 503, "暂停后请求必须回到 403（空快照 DENY）");

        // ② 切模式（暂停态下 LEGACY_API——回退部署形态演练；OPERATION_ADMISSION 端点不服务）
        saveServiceConfig(null, "LEGACY_API");
        long g2 = generation();
        assertThat(g2).as("切模式必须递增配置代次").isGreaterThan(g1);
        // 暂停态不放开流量：仍 403（容忍 503）
        awaitHttpStatus("/api/example/report/view", "{\"reportCode\":\"report-1\"}", 403,
            s -> s == 200 || s == 503, "暂停+切模式期间请求必须保持 403");

        // ③ 恢复（status=1 + 回切 OPERATION_ADMISSION）：代次再 +1，恢复以库为准（广播送达即失效，
        // 不等 TTL）
        saveServiceConfig(1, "OPERATION_ADMISSION");
        long g3 = generation();
        assertThat(g3).as("恢复必须递增配置代次").isGreaterThan(g2);
        JsonNode restored = awaitBusinessResult("/api/example/report/view",
            "{\"reportCode\":\"report-1\"}", b -> b.path("code").asInt() == 200,
            "恢复后必须在 30 秒窗口内回到放行（以库为准，不等快照 TTL 兜底）");
        assertThat(restored.path("data").path("reportCode").asText()).isEqualTo("report-1");
    }

    // ------------------------------------------------------------------
    // 授权/撤权辅助（apply-grant-plan 唯一写入口）
    // ------------------------------------------------------------------

    private static com.fasterxml.jackson.databind.node.ObjectNode grantKey(
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

    private static com.fasterxml.jackson.databind.node.ObjectNode grantPlan(
            List<com.fasterxml.jackson.databind.node.ObjectNode> creates,
            List<Long> removes) {
        var plan = JSON.createObjectNode();
        var createsArr = JSON.createArrayNode();
        for (com.fasterxml.jackson.databind.node.ObjectNode key : creates) {
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

    private static void saveServiceConfig(Integer status, String apiAuthMode) {
        var req = JSON.createObjectNode()
            .put("serviceCode", SERVICE)
            .put("name", "Example Service");
        if (status != null) {
            req.put("status", status);
        }
        if (apiAuthMode != null) {
            req.put("apiAuthMode", apiAuthMode);
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
            long parentId = scalar(st,
                "SELECT rrp.id FROM role_resource_permission rrp"
                + " JOIN resource_entity re ON re.id = rrp.resource_entity_id AND re.code = '" + parentCode + "'"
                + " JOIN operation_permission op ON op.resource_type = rrp.resource_type AND op.code = 'VIEW'"
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
                "SELECT config_generation FROM service_config WHERE service_code = 'example-service'")) {
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
            "--perm.gateway.enabled=false",
            "--perm.client.enabled=false");
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
            "--spring.cloud.gateway.enabled=false",
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
        String javaBin = Path.of(System.getProperty("java.home"), "bin",
            System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java").toString();
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
        } catch (IOException e) {
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
