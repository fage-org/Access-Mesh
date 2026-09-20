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
 * 当配置了perm.internal-secret时，将X-Internal-Secret请求头注入到下游请求中。
 * 用于后端服务验证请求来源为Gateway，防止外部直接访问后端服务。
 * T-PERM-070 收窄：请求携带任一凭证头（X-Credential-Id/X-Credential-Secret）时
 * <b>不注入</b>（无凭证头时兜底注入）——凭证请求由 access-service ServiceAuthArbiter
 * 按凭证链终验，「凭证+密钥并存」由仲裁器凭证优先规则消解（service-authentication.md
 * §3.5 上线序：服务端先行，窗口期并存行为已定义）。
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
        if (hasCredentialHeader(exchange)) {
            // T-PERM-070：凭证请求不注入旧密钥（无凭证头才兜底注入）
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

    private static boolean hasCredentialHeader(ServerWebExchange exchange) {
        return hasValue(exchange, M2mCredentialFilter.HEADER_CREDENTIAL_ID)
            || hasValue(exchange, M2mCredentialFilter.HEADER_CREDENTIAL_SECRET);
    }

    /** 取值非空判定（与仲裁器/M2M 过滤器的 isBlank 语义对齐——空白头值三处一致视为缺失）。 */
    private static boolean hasValue(ServerWebExchange exchange, String header) {
        String value = exchange.getRequest().getHeaders().getFirst(header);
        return value != null && !value.isBlank();
    }
}