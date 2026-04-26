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
 * Global exception handler for WebFlux gateway.
 * Catches all unhandled exceptions and returns standardized JSON responses.
 */
@Component
@Order(-2) // Run before default Spring error handlers
public class GlobalExceptionHandler implements WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ObjectMapper objectMapper;
    private final Tracer tracer;

    public GlobalExceptionHandler(ObjectMapper objectMapper, Tracer tracer) {
        this.objectMapper = objectMapper;
        this.tracer = tracer;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        // Disable default error handling
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

        // Set requestId from exchange attribute
        Object requestId = exchange.getAttribute("requestId");
        if (requestId != null) {
            resp.setRequestId(requestId.toString());
        }

        // Set traceId from current span
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
