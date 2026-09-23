package cn.ac.fage.accessmesh.gateway.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GlobalExceptionHandler 错误信封渲染行为锁（T-GW-010 claude 外评 P3 处置）。
 * <p>
 * quickstart「常见问题」诊断表以「网关 404 = JSON 信封 code=404、无 requestId」为
 * 「路由错误」判别特征（与业务 401/403 信封含 requestId 区分）——本锁钉住该形态，
 * 防渲染路径变化后诊断表口径悬空（该类此前零测试）。未匹配路由在 WebFlux 6.1.5 下
 * 以 ResponseStatusException(404, "No static resource …") 到达本 handler
 * （NoStaticResourceException 为 6.2 引入，本地版本该 message 由 ResponseStatusException 承载）。
 * </p>
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(new ObjectMapper());

    @Test
    @DisplayName("未匹配路由 404 → JSON 信封 code=404、message 路由文案、无 requestId")
    void unmatchedRouteRendersJsonEnvelope404() throws Exception {
        MockServerWebExchange exchange =
            MockServerWebExchange.from(MockServerHttpRequest.post("/api/nonexist/foo").build());
        handler.handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND, "No static resource api/nonexist/foo.")).block();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(404);
        assertThat(exchange.getResponse().getHeaders().getContentType())
            .isEqualTo(org.springframework.http.MediaType.APPLICATION_JSON);
        String body = exchange.getResponse().getBodyAsString().block();
        JsonNode json = new ObjectMapper().readTree(body);
        assertThat(json.get("code").asInt()).isEqualTo(404);
        assertThat(json.get("message").asText()).contains("No static resource");
        assertThat(json.hasNonNull("requestId")).as("网关自身 404 不带 requestId（业务信封才回填）").isFalse();
    }

    @Test
    @DisplayName("exchange 携带 requestId 属性时回填 requestId/traceId（信封双别名）")
    void requestIdAttributeEchoedIntoEnvelope() throws Exception {
        MockServerWebExchange exchange =
            MockServerWebExchange.from(MockServerHttpRequest.post("/api/x").build());
        exchange.getAttributes().put("requestId", "req-42");
        handler.handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND, "No static resource api/x")).block();

        String body = exchange.getResponse().getBodyAsString().block();
        JsonNode json = new ObjectMapper().readTree(body);
        assertThat(json.get("requestId").asText()).isEqualTo("req-42");
        assertThat(json.get("traceId").asText()).isEqualTo("req-42");
    }
}
