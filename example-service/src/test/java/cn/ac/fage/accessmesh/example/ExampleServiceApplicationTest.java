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
 * <p>验证两点：① 删除 perm-client/perm-data/MyBatis-Flex/PostgreSQL/Redis 等未消费依赖后，
 * 应用仍能正常启动（common 统一响应体/全局异常处理器/UTC 装配经自动配置生效，无数据源
 * 也能起）；② /api/example/demo/hello 经 Mock 身份头调用返回统一信封与身份回显，
 * 参数非法经全局异常处理器映射为 30001（HTTP 200 + 信封 code=30001）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.config.import-check.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.config.import=optional:classpath:/test-nope.yml"
    })
class ExampleServiceApplicationTest {

    @Autowired
    private TestRestTemplate rest;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("上下文启动 + hello 接口：统一信封 200 与身份回显")
    void helloReturnsEnvelopeAndEchoesIdentity() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", "42");
        headers.set("X-Tenant-Id", "1");

        String raw = rest.postForObject("/api/example/demo/hello",
            new HttpEntity<>(Map.of("name", "AccessMesh"), headers), String.class);

        JsonNode envelope = json.readTree(raw);
        assertThat(envelope.path("code").asInt()).as("信封 code 必须 200，响应：" + raw).isEqualTo(200);
        assertThat(envelope.path("data").path("greeting").asText()).isEqualTo("hello, AccessMesh");
        assertThat(envelope.path("data").path("userId").asText()).isEqualTo("42");
        assertThat(envelope.path("data").path("tenantId").asText()).isEqualTo("1");
        assertThat(envelope.path("requestId")).as("PermResultResponseAdvice 自动填充 requestId").isNotNull();
    }

    @Test
    @DisplayName("name 为空白：全局异常处理器映射为信封 code=30001")
    void blankNameMappedTo30001ByGlobalHandler() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", "42");
        headers.set("X-Tenant-Id", "1");

        String raw = rest.postForObject("/api/example/demo/hello",
            new HttpEntity<>(Map.of("name", "  "), headers), String.class);

        JsonNode envelope = json.readTree(raw);
        assertThat(envelope.path("code").asInt()).as("业务错误必须映射为 30001，响应：" + raw).isEqualTo(30001);
    }
}
