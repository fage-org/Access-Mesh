package cn.ac.fage.accessmesh.gateway.filter;

import cn.ac.fage.accessmesh.gateway.config.GatewayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HeaderCleanFilter 回归锁（T-GW-008：IP 条件信任面收口）。
 * <p>
 * 验收第 3 条：携带伪造 X-Forwarded-For 的请求经 Gateway 后下游可见 IP =
 * 真实 remoteAddr（伪造值不透传）；正常直连与经代理两类来源各钉一例。
 * 以 Gateway 默认 clean 列表构造（同时锁默认列表必须含 X-Forwarded-For / X-Real-IP），
 * 在 filter chain 中捕获传给下游的最终 request 断言其头。
 * </p>
 * <p>
 * 附带锁：头名匹配大小写不敏感——HTTP 头名按 RFC 7230 大小写不敏感，
 * 小写变体（x-forwarded-for / x-internal-secret）同样被清洗，堵住整个
 * 绕过清洗列表的变体缝隙（T-GW-008 实施发现，惠及 clean 列表既有全部内部头）。
 * </p>
 */
class HeaderCleanFilterTest {

    /** Gateway 自身观测的直连对端地址（经代理拓扑下=代理出口 IP） */
    private static final String GATEWAY_OBSERVED_IP = "10.0.0.99";

    /** 攻击者伪造声称的客户端 IP（不得透传下游） */
    private static final String SPOOFED_IP = "203.0.113.7";

    private HeaderCleanFilter filter;
    private final AtomicReference<org.springframework.http.server.reactive.ServerHttpRequest> downstream =
        new AtomicReference<>();

    @BeforeEach
    void setUp() {
        filter = new HeaderCleanFilter(new GatewayProperties());
        downstream.set(null);
    }

    @Test
    @DisplayName("直连来源（无 XFF）：下游可见 XFF=Gateway 观测的 remoteAddr 单值")
    void directConnectionWithoutXffDownstreamSeesRemoteAddr() {
        ServerWebExchange exchange = exchangeWithRemote(MockServerHttpRequest.post("/admin/api/user/page")
            .header("Authorization", "Bearer token")
            .header("User-Agent", "curl/8.0"));

        runFilter(exchange);

        HttpHeaders downstreamHeaders = downstream.get().getHeaders();
        assertThat(downstreamHeaders.getFirst("X-Forwarded-For")).isEqualTo(GATEWAY_OBSERVED_IP);
        assertThat(downstreamHeaders.getOrEmpty("X-Forwarded-For")).hasSize(1);
    }

    @Test
    @DisplayName("经代理来源（伪造 XFF 链）：下游可见 XFF=remoteAddr，伪造链不透传")
    void proxiedSourceWithSpoofedXffChainNotForwarded() {
        ServerWebExchange exchange = exchangeWithRemote(MockServerHttpRequest.post("/admin/api/user/page")
            .header("X-Forwarded-For", SPOOFED_IP + ", 10.0.0.5"));

        runFilter(exchange);

        HttpHeaders downstreamHeaders = downstream.get().getHeaders();
        assertThat(downstreamHeaders.getFirst("X-Forwarded-For")).isEqualTo(GATEWAY_OBSERVED_IP);
        assertThat(downstreamHeaders.getOrEmpty("X-Forwarded-For")).hasSize(1);
        // 伪造链任何一段都不得出现在下游可见值中
        assertThat(downstreamHeaders.getFirst("X-Forwarded-For")).doesNotContain(SPOOFED_IP);
    }

    @Test
    @DisplayName("小写变体伪造 XFF 同样清洗重建（头名大小写不敏感）")
    void lowercaseXffVariantAlsoCleaned() {
        ServerWebExchange exchange = exchangeWithRemote(MockServerHttpRequest.post("/admin/api/user/page")
            .header("x-forwarded-for", SPOOFED_IP));

        runFilter(exchange);

        HttpHeaders downstreamHeaders = downstream.get().getHeaders();
        // 重建值覆盖任何变体形式（HttpHeaders 查询大小写不敏感，任何形式残留都视为未清洗）
        assertThat(downstreamHeaders.getFirst("X-Forwarded-For")).isEqualTo(GATEWAY_OBSERVED_IP);
        assertThat(downstreamHeaders.getOrEmpty("X-Forwarded-For")).hasSize(1);
    }

    @Test
    @DisplayName("外部 X-Real-IP（含小写变体）删除，不重建")
    void externalRealIpHeaderRemoved() {
        ServerWebExchange exchange = exchangeWithRemote(MockServerHttpRequest.post("/admin/api/user/page")
            .header("X-Real-IP", SPOOFED_IP)
            .header("x-real-ip", SPOOFED_IP));

        runFilter(exchange);

        assertThat(downstream.get().getHeaders().containsKey("X-Real-IP")).isFalse();
    }

    @Test
    @DisplayName("内部头小写变体同样清除（变体绕过缝隙锁，惠及存量 clean 列表）")
    void lowercaseInternalHeaderVariantAlsoCleaned() {
        ServerWebExchange exchange = exchangeWithRemote(MockServerHttpRequest.post("/admin/api/user/page")
            .header("x-internal-secret", "spoofed-secret")
            .header("X-USER-ID", "1"));

        runFilter(exchange);

        HttpHeaders downstreamHeaders = downstream.get().getHeaders();
        assertThat(downstreamHeaders.containsKey("X-Internal-Secret")).isFalse();
        assertThat(downstreamHeaders.containsKey("X-User-Id")).isFalse();
    }

    @Test
    @DisplayName("其余转发声明头（X-Forwarded-Host/Port/Proto/Prefix/Forwarded，含小写变体）删除不透传")
    void forwardedVariantHeadersRemoved() {
        ServerWebExchange exchange = exchangeWithRemote(MockServerHttpRequest.post("/admin/api/user/page")
            .header("X-Forwarded-Host", "evil.com")
            .header("x-forwarded-proto", "https")
            .header("X-Forwarded-Port", "8443")
            .header("X-Forwarded-Prefix", "/spoof")
            .header("Forwarded", "for=" + SPOOFED_IP));

        runFilter(exchange);

        HttpHeaders downstreamHeaders = downstream.get().getHeaders();
        assertThat(downstreamHeaders.containsKey("X-Forwarded-Host")).isFalse();
        assertThat(downstreamHeaders.containsKey("X-Forwarded-Proto")).isFalse();
        assertThat(downstreamHeaders.containsKey("X-Forwarded-Port")).isFalse();
        assertThat(downstreamHeaders.containsKey("X-Forwarded-Prefix")).isFalse();
        assertThat(downstreamHeaders.containsKey("Forwarded")).isFalse();
        // XFF 重建不受转发声明头清洗影响
        assertThat(downstreamHeaders.getFirst("X-Forwarded-For")).isEqualTo(GATEWAY_OBSERVED_IP);
    }

    @Test
    @DisplayName("无关头原样透传，不被清洗波及")
    void unrelatedHeadersPassedThrough() {
        ServerWebExchange exchange = exchangeWithRemote(MockServerHttpRequest.post("/admin/api/user/page")
            .header("Authorization", "Bearer token")
            .header("User-Agent", "curl/8.0")
            .header("Content-Type", "application/json"));

        runFilter(exchange);

        HttpHeaders downstreamHeaders = downstream.get().getHeaders();
        assertThat(downstreamHeaders.getFirst("Authorization")).isEqualTo("Bearer token");
        assertThat(downstreamHeaders.getFirst("User-Agent")).isEqualTo("curl/8.0");
        assertThat(downstreamHeaders.getFirst("Content-Type")).isEqualTo("application/json");
    }

    @Test
    @DisplayName("remoteAddr 不可得时不写回 XFF（防御分支，交由下游自身兜底）")
    void noXffWrittenWhenRemoteAddrUnavailable() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/admin/api/user/page")
            .header("X-Forwarded-For", SPOOFED_IP)
            .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);

        runFilter(exchange);

        // 外部伪造值已清洗，且无重建值：下游不见任何 XFF
        assertThat(downstream.get().getHeaders().containsKey("X-Forwarded-For")).isFalse();
    }

    private ServerWebExchange exchangeWithRemote(MockServerHttpRequest.BaseBuilder<?> builder) {
        MockServerHttpRequest request = builder.remoteAddress(remoteAddress()).build();
        return MockServerWebExchange.from(request);
    }

    private InetSocketAddress remoteAddress() {
        try {
            return new InetSocketAddress(
                InetAddress.getByAddress(new byte[]{10, 0, 0, (byte) 99}), 12345);
        } catch (UnknownHostException e) {
            throw new IllegalStateException("字面 IP 解析不应失败", e);
        }
    }

    private void runFilter(ServerWebExchange exchange) {
        GatewayFilterChain chain = ex -> {
            downstream.set(ex.getRequest());
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
    }
}
