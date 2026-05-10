package cn.ac.fage.accessmesh.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 内部密钥注入过滤器
 * <p>
 * 当配置了perm.internal-secret时，将X-Internal-Secret请求头注入到所有下游请求中。
 * 用于后端服务验证请求来源为Gateway，防止外部直接访问后端服务。
 * 执行顺序：-40（在HeaderEnrichFilter之后）
 * </p>
 */
@Component
public class InternalSecretFilter implements GlobalFilter, Ordered {

    private static final String SECRET_HEADER = "X-Internal-Secret";

    @Value("${perm.internal-secret:}")
    private String internalSecret;

    /**
     * 执行过滤器逻辑
     * <p>
     * 如果配置了内部密钥，则注入到请求头中。
     * 未配置时跳过注入。
     * </p>
     *
     * @param exchange 服务器Web交换对象
     * @param chain    过滤器链
     * @return Mono完成信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (internalSecret == null || internalSecret.isBlank()) {
            // 未配置密钥时跳过注入
            return chain.filter(exchange);
        }

        return chain.filter(exchange.mutate()
            .request(exchange.getRequest().mutate()
                .headers(headers -> headers.set(SECRET_HEADER, internalSecret))
                .build())
            .build());
    }

    /**
     * 获取过滤器执行顺序
     * <p>
     * 返回-40，确保在HeaderEnrichFilter之后执行。
     * </p>
     *
     * @return 过滤器顺序值
     */
    @Override
    public int getOrder() {
        return -40;
    }
}