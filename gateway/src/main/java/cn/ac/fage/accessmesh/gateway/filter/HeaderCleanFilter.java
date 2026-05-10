package cn.ac.fage.accessmesh.gateway.filter;

import cn.ac.fage.accessmesh.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 请求头清理过滤器
 * <p>
 * 移除外部请求中可能被伪造的内部请求头，防止请求头注入攻击。
 * 清理的请求头列表由配置文件指定。
 * 执行顺序：-90（在请求ID生成之后）
 * </p>
 */
@Component
public class HeaderCleanFilter implements GlobalFilter, Ordered {

    private final List<String> headersToClean;

    public HeaderCleanFilter(GatewayProperties gatewayProperties) {
        this.headersToClean = gatewayProperties.getHeader().getClean();
    }

    /**
     * 执行过滤器逻辑
     * <p>
     * 过滤掉配置中指定的敏感请求头，防止外部请求伪造内部请求头。
     * </p>
     *
     * @param exchange 服务器Web交换对象
     * @param chain    过滤器链
     * @return Mono完成信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpHeaders filteredHeaders = new HttpHeaders();
        HttpHeaders original = exchange.getRequest().getHeaders();
        original.forEach((key, values) -> {
            if (!headersToClean.contains(key)) {
                values.forEach(v -> filteredHeaders.add(key, v));
            }
        });
        return chain.filter(exchange.mutate()
            .request(exchange.getRequest().mutate()
                .headers(h -> h.putAll(filteredHeaders))
                .build())
            .build());
    }

    /**
     * 获取过滤器执行顺序
     * <p>
     * 返回-90，确保在请求ID生成之后、其他过滤器之前执行。
     * </p>
     *
     * @return 过滤器顺序值
     */
    @Override
    public int getOrder() {
        return -90;
    }
}