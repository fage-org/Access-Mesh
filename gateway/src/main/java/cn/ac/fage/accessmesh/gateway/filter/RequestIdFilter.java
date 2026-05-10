package cn.ac.fage.accessmesh.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 请求ID生成过滤器
 * <p>
 * 为每个请求生成或传递X-Request-Id请求头。
 * 用于请求追踪和日志关联。
 * 执行顺序：-100（在自定义过滤器链中最先执行）
 * </p>
 */
@Component
public class RequestIdFilter implements GlobalFilter, Ordered {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    /**
     * 执行过滤器逻辑
     * <p>
     * 检查请求是否已有有效的X-Request-Id，如果没有则生成新的UUID。
     * 将请求ID存储到交换属性中，供后续过滤器使用。
     * </p>
     *
     * @param exchange 服务器Web交换对象
     * @param chain    过滤器链
     * @return Mono完成信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank() || !isValidUuid(requestId)) {
            requestId = UUID.randomUUID().toString();
        }
        exchange.getAttributes().put("requestId", requestId);
        return chain.filter(exchange.mutate()
            .request(exchange.getRequest().mutate()
                .header(REQUEST_ID_HEADER, requestId)
                .build())
            .build());
    }

    /**
     * 获取过滤器执行顺序
     * <p>
     * 返回-100，确保在所有自定义过滤器之前执行。
     * </p>
     *
     * @return 过滤器顺序值
     */
    @Override
    public int getOrder() {
        return -100;
    }

    /**
     * 验证UUID格式是否有效
     * <p>
     * 检查字符串是否为有效的UUID格式。
     * </p>
     *
     * @param requestId 待验证的请求ID字符串
     * @return 有效返回true，否则返回false
     */
    private boolean isValidUuid(String requestId) {
        try {
            UUID.fromString(requestId);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}