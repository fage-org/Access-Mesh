package cn.ac.fage.accessmesh.gateway.handler;

import cn.ac.fage.accessmesh.gateway.model.GatewayResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

/**
 * 网关全局异常处理器
 * <p>
 * WebFlux网关的全局异常处理器，捕获所有未处理的异常并返回标准化JSON响应。
 * 优先级设置为-2，确保在默认Spring错误处理器之前执行。
 * </p>
 */
@Component
@Order(-2) // 在默认Spring错误处理器之前运行
public class GlobalExceptionHandler implements WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ObjectMapper objectMapper;
    private final Tracer tracer;

    /**
     * 构造全局异常处理器
     * <p>
     * 注入ObjectMapper用于JSON序列化，Tracer用于获取链路追踪ID。
     * </p>
     *
     * @param objectMapper JSON序列化器
     * @param tracer       链路追踪器
     */
    public GlobalExceptionHandler(ObjectMapper objectMapper, Tracer tracer) {
        this.objectMapper = objectMapper;
        this.tracer = tracer;
    }

    /**
     * 处理异常
     * <p>
     * 根据异常类型返回相应的HTTP状态码和错误消息：
     * - ResponseStatusException: 使用异常中的状态码和原因
     * - TimeoutException: 504网关超时
     * - NotFoundException: 502服务不可用
     * - 其他异常: 500内部错误并记录日志
     * </p>
     *
     * @param exchange 服务器Web交换对象
     * @param ex       异常对象
     * @return 完成信号
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        // 禁用默认错误处理
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        int code;
        String message;
        HttpStatus status;

        if (ex instanceof org.springframework.web.server.ResponseStatusException rse) {
            status = HttpStatus.valueOf(rse.getStatusCode().value());
            code = status.value();
            message = rse.getReason() != null ? rse.getReason() : status.getReasonPhrase();
        } else if (ex instanceof java.util.concurrent.TimeoutException) {
            status = HttpStatus.GATEWAY_TIMEOUT;
            code = 504;
            message = "服务响应超时";
        } else if (ex instanceof org.springframework.cloud.gateway.support.NotFoundException) {
            status = HttpStatus.BAD_GATEWAY;
            code = 502;
            message = "服务暂时不可用";
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            code = 99999;
            message = "网关内部异常";
            log.error("Gateway internal error: {}", ex.getMessage(), ex);
        }

        response.setStatusCode(status);

        GatewayResponse resp = GatewayResponse.error(code, message);

        // 从交换属性设置请求ID
        Object requestId = exchange.getAttribute("requestId");
        if (requestId != null) {
            resp.setRequestId(requestId.toString());
        }

        // 从当前span设置追踪ID
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            resp.setTraceId(currentSpan.context().traceId());
        }

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(resp);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer))
                .onErrorResume(e -> {
                    log.error("Failed to write error response", e);
                    return response.setComplete();
                });
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize error response", e);
            return response.setComplete();
        }
    }
}