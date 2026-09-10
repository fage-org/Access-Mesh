package cn.ac.fage.accessmesh.gateway.filter;

import cn.ac.fage.accessmesh.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * 请求头清理过滤器
 * <p>
 * 移除外部请求中可能被伪造的内部请求头，防止请求头注入攻击。
 * 清理的请求头列表由配置文件指定（HTTP 头名大小写不敏感，变体同样清除）。
 * 执行顺序：-90（在请求ID生成之后）
 * </p>
 * <p>
 * T-GW-008 IP 条件信任面收口：清洗后以 Gateway 自身观测的 {@code remoteAddr}
 * 重建 {@code X-Forwarded-For} 写回下游（单一可信来源）——access-service
 * 门禁条件评估与操作/登录日志统一消费重建值；网关自身快照条件重评不读请求头
 * （{@link PermissionFilter} 直用 remoteAddr）。remoteAddr 不可得时不写回，
 * 下游按自身 remoteAddr 兜底。
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
     * 直接从下游请求头中移除配置指定（含任意大小写变体）的敏感请求头，
     * 再以 Gateway 观测的 remoteAddr 重建 X-Forwarded-For。不得走"构建干净头副本
     * 再 putAll"路线——{@code ServerHttpRequest.Builder#headers} 的 consumer 收到的
     * 是原请求头的可写视图，putAll 为叠加语义，清洗项不会被移除（T-GW-008 实测修正：
     * 归并起旧实现即因此从未真正删除过头，仅靠下游注入 filter 的 set 覆盖兜底）。
     * </p>
     *
     * @param exchange 服务器Web交换对象
     * @param chain    过滤器链
     * @return Mono完成信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        String rebuiltXff = remote != null && remote.getAddress() != null
            ? remote.getAddress().getHostAddress() : null;
        return chain.filter(exchange.mutate()
            .request(exchange.getRequest().mutate()
                .headers(h -> {
                    // HttpHeaders 底层大小写不敏感，remove(规范名) 可删除任意大小写变体
                    //（RFC 7230 头名大小写不敏感，小写变体绕过即整个清洗列表失效）
                    for (String cleaned : headersToClean) {
                        h.remove(cleaned);
                    }
                    if (rebuiltXff != null) {
                        // set=替换：即使清洗配置被误删，外部 XFF 任意值/多值也被覆盖为单值观测值
                        h.set("X-Forwarded-For", rebuiltXff);
                    }
                })
                .build())
            .build());
    }

    /**
     * 获取过滤器执行顺序
     * <p>
     * 返回-90，确保在请求ID生成之后、其他过滤器之前执行。
     * </p>
     */
    @Override
    public int getOrder() {
        return -90;
    }
}
