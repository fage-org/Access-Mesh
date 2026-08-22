package cn.ac.fage.accessmesh.gateway.filter;

import cn.ac.fage.accessmesh.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * OAuth2 委托令牌透传过滤器（T-ACCESS-013；评审 P1 修复：仅委托 JWT 启用透传）
 * <p>
 * 命中 {@code gateway.oauth2.passthrough-paths} 配置路径<b>且 Authorization 携带
 * Bearer 三段式 JWT</b>时设置 skipAuth=true：AuthTokenFilter（只认平台 uuid 会话，
 * 否则 OAuth2 JWT 被 401）、PermissionFilter、HeaderEnrichFilter（会话身份头注入）
 * 与 SignatureEnrichFilter 全部跳过身份逻辑，Authorization 头原样透传下游
 * （HeaderCleanFilter 清单不含 Authorization），由下游 access-service 的 OAuth2 JWT
 * 认证分支完成验签 + 撤销黑名单 + 客户端启用校验 + 开放路径门禁
 * （scope/audience/clientIds，默认拒绝）。
 * </p>
 * <p>
 * JWT 识别口径与下游 RequestContextInterceptor JWT 分支一致（Bearer 前缀 + 含 '.'），
 * 仅做形态识别不验签（验签由下游完成，伪造 JWT 透传后下游 401）。<b>非 JWT 请求不设
 * skipAuth</b>（评审 P1：平台用户 uuid 会话令牌若也跳过 AuthTokenFilter/PermissionFilter，
 * 会被下游共享 Redis 会话分支接受——平台会话绕过 Gateway 接口权限）：uuid 会话令牌走
 * 正常 AuthTokenFilter 会话校验 + PermissionFilter 快照鉴权，平台会话认证路径不变；
 * 无 Authorization 头由 AuthTokenFilter 401。
 * </p>
 * <p>
 * 与 WhitelistFilter（匿名公开白名单）语义不同：本过滤器放行的是"由下游验证的
 * 委托令牌流量"，默认清单为空（无业务路径默认开放）；/auth/** 前缀已由白名单
 * 覆盖（userinfo 等端点无需重复配置）。路径为 Gateway 外部口径
 * （如 /admin/api/**，StripPrefix=1 后下游按 /api/** 匹配自身白名单）。
 * 执行顺序：-79（白名单之后、认证过滤器之前）。
 * </p>
 */
@Component
public class OAuth2PassthroughFilter implements GlobalFilter, Ordered {

    private static final String SKIP_AUTH_ATTR = "skipAuth";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final List<String> passthroughPaths;

    public OAuth2PassthroughFilter(GatewayProperties gatewayProperties) {
        this.passthroughPaths = gatewayProperties.getOauth2().getPassthroughPaths();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isOAuth2JwtBearerRequest(exchange)) {
            for (String pattern : passthroughPaths) {
                if (pathMatcher.match(pattern, path)) {
                    exchange.getAttributes().put(SKIP_AUTH_ATTR, true);
                    break;
                }
            }
        }
        return chain.filter(exchange);
    }

    /**
     * 识别 OAuth2 JWT 委托令牌请求（形态判定，与下游 JWT 分支识别口径一致）：
     * Authorization = "Bearer xxx.yyy.zzz"（含 '.'）。uuid 会话令牌 / 其他形态 / 缺头
     * 均返回 false——它们必须走正常会话校验与 Gateway 接口鉴权（评审 P1）。
     */
    private boolean isOAuth2JwtBearerRequest(ServerWebExchange exchange) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return false;
        }
        String token = authorization.substring(BEARER_PREFIX.length());
        return !token.isBlank() && token.indexOf('.') >= 0;
    }

    @Override
    public int getOrder() {
        return -79;
    }
}
