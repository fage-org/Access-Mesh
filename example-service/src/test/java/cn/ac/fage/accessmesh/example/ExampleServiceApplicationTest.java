package cn.ac.fage.accessmesh.example;

import tools.jackson.databind.json.JsonMapper;
import cn.ac.fage.accessmesh.example.it.ExamplePgInfra;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Real-PG application wiring, response envelope, signature and management-port isolation. */
@Tag("testcontainers")
@Testcontainers(disabledWithoutDocker = true)
@org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "example.publication.enabled=false","example.execution.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.config.import-check.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.config.import=optional:classpath:/test-nope.yml",
        "management.server.port=0",
        "perm.client.signature.secret=test-signature-secret"
    })
class ExampleServiceApplicationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        ExamplePgInfra.PG.register(registry, ExampleServiceApplicationTest.class);
    }


    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @org.springframework.boot.test.web.server.LocalManagementPort
    private int managementPort;

    @Test
    void healthProbeIsAvailableOnlyOnManagementPort() throws Exception {
        var health = rest.getForEntity("http://127.0.0.1:" + managementPort + "/actuator/health", String.class);
        assertThat(health.getStatusCode().value()).isEqualTo(200);
        assertThat(json.readTree(health.getBody()).path("status").asText()).isEqualTo("UP");
        assertThat(applicationContext.getBeansOfType(
            org.springframework.boot.webmvc.actuate.endpoint.web.WebMvcEndpointHandlerMapping.class)).isEmpty();
    }

    /**
     * Q-064：业务端口不存在的 MVC 路径映射 HTTP 404 + 信封 code=404，不再落 Exception
     * 兜底成 500（旧实现实跑 500——拼错路径被监控误报为服务故障）。管理端口健康检查隔离不受影响。
     */
    @Test
    void unknownRouteOnBusinessPortMapsTo404Not500() throws Exception {
        var resp = rest.getForEntity("/definitely/not-a-route", String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(404);
        assertThat(json.readTree(resp.getBody()).path("code").asInt()).isEqualTo(404);
    }

    private final ObjectMapper json = JsonMapper.builder().configureForJackson2().build();

    @Test void multipartImportUsesJsonRequestPartAndMapsCsvErrors() {
        org.mockito.Mockito.when(permissionChecker.check(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any()))
            .thenReturn(new cn.ac.fage.accessmesh.example.perm.BusinessPermChecker.Decision(true,null));
        HttpHeaders headers=new HttpHeaders(); headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("X-User-Id","42"); headers.set("X-Tenant-Id","9004"); sign(headers,"42","9004");
        var parts=new org.springframework.util.LinkedMultiValueMap<String,Object>();
        var jsonHeaders=new HttpHeaders(); jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        parts.add("request",new HttpEntity<>(Map.of("reportCode","r1","importKey",java.util.UUID.randomUUID().toString()),jsonHeaders));
        parts.add("file",new org.springframework.core.io.ByteArrayResource("unknown\nsecret-value\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            @Override public String getFilename() { return "records.csv"; }
        });
        var response=json.readTree(rest.postForObject("/api/example/demo-data/import",new HttpEntity<>(parts,headers),String.class));
        assertThat(response.path("code").asInt()).isEqualTo(30105);
        assertThat(response.toString()).doesNotContain("secret-value");
        parts.set("file",new org.springframework.core.io.ByteArrayResource(new byte[10*1024*1024+1]) {
            @Override public String getFilename() { return "oversized.csv"; }
        });
        var oversized=json.readTree(rest.postForObject("/api/example/demo-data/import",new HttpEntity<>(parts,headers),String.class));
        assertThat(oversized.path("code").asInt()).isEqualTo(30106);
    }

    @Test
    void dataRoutesRejectMalformedSelectionsAndChangesBeforeBusinessExecution() {
        HttpHeaders headers=new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id","42"); headers.set("X-Tenant-Id","9003"); sign(headers,"42","9003");
        var invalid=java.util.List.of(
            Map.entry("/page","{\"reportCode\":\"r1\",\"pageSize\":201}"),
            Map.entry("/batch-get","{\"reportCode\":\"r1\",\"recordCodes\":[\"A\",\"A\"]}"),
            Map.entry("/update","{\"reportCode\":\"r1\",\"recordCode\":\"A\",\"remark\":\"value\",\"remarkClear\":true}"),
            Map.entry("/update","{\"reportCode\":\"r1\",\"recordCode\":\"A\"}"),
            Map.entry("/page","{\"reportCode\":\"r1\",\"filters\":[{\"fieldId\":1,\"operator\":\"GT\",\"value\":1}]}"));
        for(var input:invalid) {
            var response=rest.postForEntity("/api/example/demo-data"+input.getKey(),new HttpEntity<>(input.getValue(),headers),String.class);
            assertThat(response.getStatusCode().value()).as(input.getKey()).isEqualTo(400);
            assertThat(json.readTree(response.getBody()).path("code").asInt()).isEqualTo(90001);
        }
        headers.remove("X-User-Signature");
        var unsigned=json.readTree(rest.postForObject("/api/example/demo-data/fields",new HttpEntity<>(Map.of("reportCode","r1"),headers),String.class));
        assertThat(unsigned.path("code").asInt()).isEqualTo(30003);
    }

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private cn.ac.fage.accessmesh.example.perm.BusinessPermChecker permissionChecker;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void metadataRoutesUseSignedIdentityValidateRequestsAndPersistPendingEvents() {
        org.mockito.Mockito.when(permissionChecker.check(org.mockito.ArgumentMatchers.eq("9001"),
            org.mockito.ArgumentMatchers.eq("42"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(new cn.ac.fage.accessmesh.example.perm.BusinessPermChecker.Decision(true, null));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", "42"); headers.set("X-Tenant-Id", "9001");
        sign(headers, "42", "9001");
        var created = json.readTree(rest.postForObject("/api/example/demo-report/create", new HttpEntity<>(Map.of(
            "reportCode", "http-report", "name", "报表", "datasetCode", "sales-records",
            "fieldResourceTypeCode", "HTTP_FIELDS", "tenantId", 9999, "userId", 8888), headers), String.class));
        assertThat(created.path("code").asInt()).isEqualTo(200);
        assertThat(created.path("data").path("publicationStatus").asText()).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT tenant_id FROM demo_report WHERE report_code='http-report'", Long.class)).isEqualTo(9001);
        assertThat(jdbc.queryForObject("SELECT created_by FROM demo_publication WHERE tenant_id=9001", Long.class)).isEqualTo(42);
        var fields = json.readTree(rest.postForObject("/api/example/demo-field/register", new HttpEntity<>(Map.of(
            "reportCode", "http-report", "expectedCatalogVersion", 0, "fieldKeys", java.util.List.of("amount", "remark")), headers), String.class));
        assertThat(fields.path("code").asInt()).isEqualTo(200);
        assertThat(fields.path("data").path("catalogVersion").asLong()).isEqualTo(1);
        assertThat(fields.path("data").path("items").size()).isEqualTo(2);
        var invalidPage = rest.postForEntity("/api/example/demo-field/list", new HttpEntity<>(Map.of(
            "reportCode", "http-report", "pageSize", 201), headers), String.class);
        assertThat(invalidPage.getStatusCode().value()).isEqualTo(400);
        var conflicting = json.readTree(rest.postForObject("/api/example/demo-scope/save", new HttpEntity<>(Map.of(
            "resourceTypeCode", "HTTP_FIELDS", "resourceCode", "1", "name", "团队 T1",
            "enabled", true, "expectedVersion", 0), headers), String.class));
        assertThat(conflicting.path("code").asInt()).isEqualTo(30102);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM demo_scope WHERE tenant_id=9001", Long.class)).isZero();
    }

    @Test
    void metadataDeniedOrUnsignedRequestsCannotCreateBusinessFacts() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", "42"); headers.set("X-Tenant-Id", "9002");
        var body = Map.of("reportCode", "denied", "name", "报表", "datasetCode", "sales-records",
            "fieldResourceTypeCode", "FIELDS");
        var unsigned = json.readTree(rest.postForObject("/api/example/demo-report/create", new HttpEntity<>(body, headers), String.class));
        assertThat(unsigned.path("code").asInt()).isEqualTo(30003);
        org.mockito.Mockito.verifyNoInteractions(permissionChecker);
        sign(headers, "42", "9002");
        org.mockito.Mockito.when(permissionChecker.check(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(new cn.ac.fage.accessmesh.example.perm.BusinessPermChecker.Decision(false, "NO_PERMISSION"));
        var denied = json.readTree(rest.postForObject("/api/example/demo-report/create", new HttpEntity<>(body, headers), String.class));
        assertThat(denied.path("code").asInt()).isEqualTo(30004);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM demo_dataset WHERE tenant_id=9002", Long.class)).isZero();
    }

    /** 按 Gateway SignatureEnrichFilter 同款算法构造签名请求头（HMAC-SHA256，hex） */
    private static void sign(HttpHeaders headers, String userId, String tenantId) {
        long ts = System.currentTimeMillis() / 1000;
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                "test-signature-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal((userId + "|" + tenantId + "|" + ts)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            headers.set("X-User-Signature", java.util.HexFormat.of().formatHex(sig));
            headers.set("X-Signature-Timestamp", String.valueOf(ts));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("上下文启动 + hello 接口：统一信封 200 与身份回显")
    void helloReturnsEnvelopeAndEchoesIdentity() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", "42");
        headers.set("X-Tenant-Id", "1");
        sign(headers, "42", "1");

        String raw = rest.postForObject("/api/example/demo/hello",
            new HttpEntity<>(Map.of("name", "AccessMesh"), headers), String.class);

        JsonNode envelope = json.readTree(raw);
        assertThat(envelope.path("code").asInt()).as("信封 code 必须 200，响应：" + raw).isEqualTo(200);
        assertThat(envelope.path("data").path("greeting").asText()).isEqualTo("hello, AccessMesh");
        assertThat(envelope.path("data").path("userId").asText()).isEqualTo("42");
        assertThat(envelope.path("data").path("tenantId").asText()).isEqualTo("1");
        assertThat(envelope.path("requestId")).as("RResponseAdvice 自动填充 requestId").isNotNull();
    }

    @Test
    @DisplayName("name 为空白：全局异常处理器映射为信封 code=30001")
    void blankNameMappedTo30001ByGlobalHandler() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", "42");
        headers.set("X-Tenant-Id", "1");
        sign(headers, "42", "1");

        String raw = rest.postForObject("/api/example/demo/hello",
            new HttpEntity<>(Map.of("name", "  "), headers), String.class);

        JsonNode envelope = json.readTree(raw);
        assertThat(envelope.path("code").asInt()).as("业务错误必须映射为 30001，响应：" + raw).isEqualTo(30001);
    }

    @Test
    @DisplayName("伪造身份头（无有效签名直连）：签名校验拒绝 30003")
    void forgedIdentityHeaders_rejectedWith30003() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", "42");
        headers.set("X-Tenant-Id", "1");
        // 不带 X-User-Signature（模拟直连伪造）与带错误签名两种形态
        String noSig = rest.postForObject("/api/example/demo/hello",
            new HttpEntity<>(Map.of("name", "AccessMesh"), headers), String.class);
        assertThat(json.readTree(noSig).path("code").asInt())
            .as("伪造身份头必须被签名校验拒绝（30003），响应：" + noSig).isEqualTo(30003);

        headers.set("X-User-Signature", "0000000000000000000000000000000000000000000000000000000000000000");
        headers.set("X-Signature-Timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        String badSig = rest.postForObject("/api/example/demo/hello",
            new HttpEntity<>(Map.of("name", "AccessMesh"), headers), String.class);
        assertThat(json.readTree(badSig).path("code").asInt())
            .as("错误签名必须被拒绝（30003），响应：" + badSig).isEqualTo(30003);
    }

    @Test
    @DisplayName("签名时间戳超窗（过期/未来）：拒绝 30003")
    void staleOrFutureTimestamp_rejectedWith30003() throws Exception {
        for (long ts : new long[] {System.currentTimeMillis() / 1000 - 3600,
                                   System.currentTimeMillis() / 1000 + 3600}) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-Id", "42");
            headers.set("X-Tenant-Id", "1");
            try {
                javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
                mac.init(new javax.crypto.spec.SecretKeySpec(
                    "test-signature-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
                byte[] sig = mac.doFinal(("42|1|" + ts).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                headers.set("X-User-Signature", java.util.HexFormat.of().formatHex(sig));
                headers.set("X-Signature-Timestamp", String.valueOf(ts));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            String raw = rest.postForObject("/api/example/demo/hello",
                new HttpEntity<>(Map.of("name", "AccessMesh"), headers), String.class);
            assertThat(json.readTree(raw).path("code").asInt())
                .as("时间戳超窗（ts=" + ts + "）必须被拒绝（30003），响应：" + raw).isEqualTo(30003);
        }
    }
}
