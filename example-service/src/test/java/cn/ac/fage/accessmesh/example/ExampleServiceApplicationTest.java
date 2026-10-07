package cn.ac.fage.accessmesh.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 瘦身后应用上下文与受保护接口链路测试（T-API-001）。
 *
 * <p>验证两点：① 历史 T-API-001 批次删除 perm-client 等未消费依赖后应用可启动
 * （该口径已被 T-ACCESS-061 重新引入 starter 取代——现 pom 依赖 perm-client，
 * 本测试仍验证轻量上下文可起）；（common 统一响应体/全局异常处理器/UTC 装配经自动配置生效，无数据源
 * 也能起）；② /api/example/demo/hello 经 Mock 身份头调用返回统一信封与身份回显，
 * 参数非法经全局异常处理器映射为 30001（HTTP 200 + 信封 code=30001）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.config.import-check.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.config.import=optional:classpath:/test-nope.yml",
        "management.server.port=0",
        "perm.client.signature.secret=test-signature-secret"
    })
class ExampleServiceApplicationTest {

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
            org.springframework.boot.actuate.endpoint.web.servlet.WebMvcEndpointHandlerMapping.class)).isEmpty();
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

    private final ObjectMapper json = new ObjectMapper();

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
