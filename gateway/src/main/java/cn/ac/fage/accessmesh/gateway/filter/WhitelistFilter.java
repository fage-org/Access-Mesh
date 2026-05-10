package cn.ac.fage.accessmesh.gateway.filter;

import cn.ac.fage.accessmesh.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 白名单过滤器
 * <p>
 * 匹配请求路径与白名单配置。如果匹配成功，设置skipAuth=true，
 * 跳过后续的认证和权限校验过滤器。
 * 执行顺序：-80
 * </p>
 */
@Component
public class WhitelistFilter implements GlobalFilter, Ordered {

    private static final String SKIP_AUTH_ATTR = "skipAuth";
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final List<String> whitelistPaths;

    public WhitelistFilter(GatewayProperties gatewayProperties) {
        this.whitelistPaths = gatewayProperties.getWhitelist().getPaths();
    }

    /**
     * 执行过滤器逻辑
     * <p>
     * 检查请求路径是否匹配白名单配置。匹配时设置skipAuth属性，
     * 允许后续认证过滤器跳过权限校验。
     * </p>
     *
     * @param exchange 服务器Web交换对象
     * @param chain    过滤器链
     * @return Mono完成信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        for (String pattern : whitelistPaths) {
            if (pathMatcher.match(pattern, path)) {
                exchange.getAttributes().put(SKIP_AUTH_ATTR, true);
                break;
            }
        }
        return chain.filter(exchange);
    }

    /**
     * 获取过滤器执行顺序
     * <p>
     * 返回-80，确保在请求头清理之后、认证过滤器之前执行。
     * </p>
     *
     * @return 过滤器顺序值
     */
    @Override
    public int getOrder() {
        return -80;
    }
}