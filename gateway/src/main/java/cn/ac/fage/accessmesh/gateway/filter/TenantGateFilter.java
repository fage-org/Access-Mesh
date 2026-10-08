package cn.ac.fage.accessmesh.gateway.filter;

import cn.ac.fage.accessmesh.common.security.TenantGateProtocol;
import cn.ac.fage.accessmesh.common.security.TenantGateState;
import cn.ac.fage.accessmesh.common.security.TenantGateSnapshot;
import cn.ac.fage.accessmesh.common.security.TenantSessionStamp;
import cn.ac.fage.accessmesh.gateway.model.GatewayResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/** 在权限快照查询之前读取共享生命周期门禁，不缓存可放行结果。 */
@Component
public class TenantGateFilter implements GlobalFilter, Ordered {
    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper json;

    public TenantGateFilter(ReactiveStringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (Boolean.TRUE.equals(exchange.getAttribute("skipAuth"))) return chain.filter(exchange);
        Object tenant = exchange.getAttribute("tenantId");
        TenantSessionStamp stamp = exchange.getAttribute("tenantSessionStamp");
        long tenantId;
        try {
            tenantId = Long.parseLong(String.valueOf(tenant));
            if (tenantId <= 0 || stamp == null) throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, 401, "登录已过期，请重新登录");
        }
        return Mono.defer(() -> redis.execute(TenantGateProtocol.SCRIPT,
                List.of(TenantGateProtocol.key(tenantId)), List.of("READ_SESSION")).next())
            .map(TenantGateSnapshot::fromWire)
            .defaultIfEmpty(TenantGateSnapshot.fromWire(null))
            .onErrorReturn(TenantGateSnapshot.fromWire(null))
            .flatMap(snapshot -> {
                var state = snapshot.state();
                if (state.status() == TenantGateState.Status.UNAVAILABLE)
                    return reject(exchange, HttpStatus.SERVICE_UNAVAILABLE, 11113, "租户门禁暂不可用");
                if (state.status() == TenantGateState.Status.DISABLED)
                    return reject(exchange, HttpStatus.FORBIDDEN, 11112, "租户已停用");
                if (!state.permitsSession(stamp.epoch()) || !stamp.redisProcessId().equals(snapshot.redisProcessId()))
                    return reject(exchange, HttpStatus.UNAUTHORIZED, 401, "登录已过期，请重新登录");
                if (stamp.forceResetPwd() && !TenantSessionStamp.allowsForcedReset(exchange.getRequest().getPath().value()))
                    return reject(exchange, HttpStatus.FORBIDDEN, 10012, "请先修改初始密码");
                return chain.filter(exchange);
            });
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, int code, String message) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        var body = GatewayResponse.error(code, message);
        Object requestId = exchange.getAttribute("requestId");
        if (requestId != null) {
            body.setRequestId(requestId.toString());
            body.setTraceId(requestId.toString());
        }
        try {
            return response.writeWith(Mono.just(response.bufferFactory().wrap(json.writeValueAsBytes(body))));
        } catch (JsonProcessingException exception) {
            return Mono.error(exception);
        }
    }

    @Override
    public int getOrder() { return -65; }
}
