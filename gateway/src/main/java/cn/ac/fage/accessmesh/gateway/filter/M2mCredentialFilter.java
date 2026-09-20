package cn.ac.fage.accessmesh.gateway.filter;

import cn.ac.fage.accessmesh.common.security.M2mCredentialEndpoints;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * M2M 凭证识别过滤器（T-PERM-070，service-authentication.md §3.3「M2M 放行链」）。
 * <p>
 * <b>完整凭证头 + 精确 M2M 路径清单</b>（单源 {@link M2mCredentialEndpoints}，与服务端
 * 仲裁器白名单同一份）命中时置 skipAuth 语义——跳过用户认证（AuthTokenFilter）与
 * 用户权限过滤（PermissionFilter），仅透传、由 access-service 仲裁器终验（防 Gateway
 * 侧验证逻辑与白名单双源漂移）。禁止把 /api/access/** 整体加入白名单。
 * </p>
 * <p>
 * 不识别的形态回落既有链：半头/缺头凭证请求（不满足「完整凭证头」）不置 skipAuth，
 * 由 AuthTokenFilter 按「无 Bearer」401 拒绝；完整凭证头但非 M2M 路径的请求同样回落
 * （401 或——若带会话——按会话链处理），服务端仲裁器白名单 403 兜底（SDK 直连同款）。
 * 执行顺序：-75（WhitelistFilter -80 之后、AuthTokenFilter -70 之前）。
 * </p>
 */
@Component
public class M2mCredentialFilter implements GlobalFilter, Ordered {

    static final String HEADER_CREDENTIAL_ID = "X-Credential-Id";
    static final String HEADER_CREDENTIAL_SECRET = "X-Credential-Secret";
    private static final String SKIP_AUTH_ATTR = "skipAuth";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String credentialId = exchange.getRequest().getHeaders().getFirst(HEADER_CREDENTIAL_ID);
        String credentialSecret = exchange.getRequest().getHeaders().getFirst(HEADER_CREDENTIAL_SECRET);
        boolean completeCredentialHeaders = credentialId != null && !credentialId.isBlank()
            && credentialSecret != null && !credentialSecret.isBlank();
        if (completeCredentialHeaders && M2mCredentialEndpoints.matches(
            exchange.getRequest().getMethod().name(), exchange.getRequest().getURI().getPath())) {
            exchange.getAttributes().put(SKIP_AUTH_ATTR, true);
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -75;
    }
}
