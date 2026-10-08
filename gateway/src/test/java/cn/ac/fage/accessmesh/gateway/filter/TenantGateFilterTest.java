package cn.ac.fage.accessmesh.gateway.filter;

import cn.ac.fage.accessmesh.common.security.TenantGateProtocol;
import cn.ac.fage.accessmesh.common.security.TenantSessionStamp;
import cn.ac.fage.accessmesh.common.security.PlatformEndpoints;
import cn.ac.fage.accessmesh.gateway.config.GatewayProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TenantGateFilterTest {
    @ParameterizedTest
    @CsvSource({"ENABLED|7,7,false,200", "DISABLED|8,7,false,403", "ENABLED|8,7,false,401",
        "UNAVAILABLE,7,false,503", "malformed,7,false,503", "ENABLED|7,7,true,403"})
    void checksLiveGateBeforePermissionChain(String wire, long epoch, boolean forced, int status) {
        var redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.execute(TenantGateProtocol.SCRIPT, List.of(TenantGateProtocol.key(2)), List.of("READ_SESSION")))
            .thenReturn(Flux.just("a".repeat(40)+"|"+wire));
        var exchange = exchange("/api/access/user/page", epoch, forced);
        var chained = new AtomicBoolean();
        new TenantGateFilter(redis, new ObjectMapper()).filter(exchange, e -> {
            chained.set(true); return Mono.empty();
        }).block();
        assertThat(chained.get()).isEqualTo(status == 200);
        if (status != 200) assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(status);
    }

    @Test
    void redisRestartRejectsRestoredSessionEvenWhenTenantEpochMatches() {
        var redis=mock(ReactiveStringRedisTemplate.class);
        when(redis.execute(TenantGateProtocol.SCRIPT,List.of(TenantGateProtocol.key(2)),List.of("READ_SESSION")))
            .thenReturn(Flux.just("b".repeat(40)+"|ENABLED|7"));
        var exchange=exchange("/api/access/user/page",7,false);
        new TenantGateFilter(redis,new ObjectMapper()).filter(exchange,e->{ throw new AssertionError("restored session must not reach permissions"); }).block();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void redisFailureCannotFallThroughToCachedPermissions() {
        var redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.execute(TenantGateProtocol.SCRIPT, List.of(TenantGateProtocol.key(2)), List.of("READ_SESSION")))
            .thenReturn(Flux.error(new IllegalStateException("unavailable")));
        var exchange = exchange("/api/access/user/page", 7, false);
        new TenantGateFilter(redis, new ObjectMapper()).filter(exchange, e -> {
            throw new AssertionError("must not query permissions");
        }).block();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(503);
    }

    @Test
    void forcedUserCanReachPasswordChangeButStillNeedsLiveTenant() {
        var redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.execute(TenantGateProtocol.SCRIPT, List.of(TenantGateProtocol.key(2)), List.of("READ_SESSION")))
            .thenReturn(Flux.just("a".repeat(40)+"|ENABLED|7"), Flux.just("a".repeat(40)+"|DISABLED|8"));
        var filter = new TenantGateFilter(redis, new ObjectMapper());
        var chained = new AtomicBoolean();
        filter.filter(exchange("/api/access/user/reset-password", 7, true), e -> {
            chained.set(true); return Mono.empty();
        }).block();
        assertThat(chained.get()).isTrue();
        var disabled = exchange("/api/access/user/reset-password", 7, true);
        filter.filter(disabled, e -> { throw new AssertionError("tenant disabled"); }).block();
        assertThat(disabled.getResponse().getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void exactPlatformPathsUseIndependentBackendAuthentication() {
        var whitelist = new WhitelistFilter(new GatewayProperties());
        var redis = mock(ReactiveStringRedisTemplate.class);
        var gate = new TenantGateFilter(redis, new ObjectMapper());
        for (String path : PlatformEndpoints.paths()) {
            var exchange = MockServerWebExchange.from(MockServerHttpRequest.post(path));
            var chained = new AtomicBoolean();
            whitelist.filter(exchange, e -> gate.filter(e, next -> {
                chained.set(true); return Mono.empty();
            })).block();
            assertThat(chained.get()).as(path).isTrue();
        }
        var unknown = MockServerWebExchange.from(MockServerHttpRequest.post("/api/access/tenant/unknown"));
        whitelist.filter(unknown, e -> Mono.empty()).block();
        assertThat((Object) unknown.getAttribute("skipAuth")).isNull();
        verifyNoInteractions(redis);
    }

    private MockServerWebExchange exchange(String path, long epoch, boolean forced) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post(path));
        exchange.getAttributes().put("tenantId", 2L);
        exchange.getAttributes().put("tenantSessionStamp", new TenantSessionStamp(epoch, forced,"a".repeat(40)));
        return exchange;
    }
}
