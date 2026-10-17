package cn.ac.fage.accessmesh.gateway.filter;

import cn.ac.fage.accessmesh.common.security.BrowserSessionPolicy;
import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.stp.StpLogic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class BrowserSessionFilterTest {
    private SaTokenConfig oldConfig;
    private SaTokenDao oldDao;
    private StpLogic oldLogic;
    private SaTokenDaoDefaultImpl dao;
    private final String marker = "a".repeat(43);
    private String token;
    private BrowserSessionFilter filter;
    @BeforeEach void setup() {
        oldConfig = SaManager.getConfig(); oldDao = SaManager.getSaTokenDao(); oldLogic = SaManager.stpLogicMap.get("login");
        SaManager.setConfig(new SaTokenConfig().setIsShare(false).setIsPrint(false));
        dao = new SaTokenDaoDefaultImpl(); SaManager.setSaTokenDao(dao);
        StpLogic logic = new StpLogic("login"); token = logic.createLoginSession(1L);
        logic.getTokenSessionByToken(token).set(BrowserSessionPolicy.SESSION_ATTRIBUTE, marker);
        filter = new BrowserSessionFilter("https://admin.example.test", new ObjectMapper());
    }
    @AfterEach void cleanup() {
        dao.destroy(); SaManager.setConfig(oldConfig); SaManager.setSaTokenDao(oldDao);
        SaManager.removeStpLogic("login"); if (oldLogic != null) SaManager.putStpLogic(oldLogic);
    }
    @Test void adaptsOnlyTheSelectedRealmAndStripsAllBrowserCredentialsFromDownstreamCookies() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/example/hello")
            .header("Origin", "https://admin.example.test").header(BrowserSessionPolicy.REQUEST_HEADER, "1")
            .header(BrowserSessionPolicy.SESSION_HEADER, marker)
            .cookie(new HttpCookie("__Host-am-tenant", token), new HttpCookie("__Host-am-platform", "private-platform-token")));
        AtomicReference<String> forwarded = new AtomicReference<>();
        filter.filter(exchange, next -> {
            forwarded.set(next.getRequest().getHeaders().getFirst("Authorization"));
            assertThat(next.getRequest().getHeaders().getFirst("Cookie")).isNull();
            assertThat(next.getRequest().getHeaders().getFirst(BrowserSessionPolicy.SESSION_HEADER)).isNull();
            return Mono.empty();
        }).block();
        assertThat(forwarded.get()).isEqualTo("Bearer " + token);
    }
    @Test void aNewCookieCannotBeUsedByAnOldPageMarker() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/access/user/page")
            .header("Origin", "https://admin.example.test").header(BrowserSessionPolicy.REQUEST_HEADER, "1")
            .header(BrowserSessionPolicy.SESSION_HEADER, "b".repeat(43)).cookie(new HttpCookie("__Host-am-tenant", token)));
        filter.filter(exchange, next -> { throw new AssertionError("must not forward"); }).block();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(409);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("90002");
        assertThat(exchange.getResponse().getHeaders().getFirst("Set-Cookie")).isNull();
    }
    @Test void aToolBearerCanCoexistWithTheOtherRealmCookieButNotItsOwnRealm() {
        var platformOnly = MockServerWebExchange.from(MockServerHttpRequest.post("/api/example/hello")
            .header("Authorization", "Bearer tool-token").cookie(new HttpCookie("__Host-am-platform", "private")));
        AtomicReference<String> forwarded = new AtomicReference<>();
        filter.filter(platformOnly, next -> { forwarded.set(next.getRequest().getHeaders().getFirst("Authorization")); return Mono.empty(); }).block();
        assertThat(forwarded.get()).isEqualTo("Bearer tool-token");
        var mixed = MockServerWebExchange.from(MockServerHttpRequest.post("/api/example/hello")
            .header("Authorization", "Bearer " + token).cookie(new HttpCookie("__Host-am-tenant", token)));
        filter.filter(mixed, next -> { throw new AssertionError("must reject mixed credentials"); }).block();
        assertThat(mixed.getResponse().getStatusCode().value()).isEqualTo(400);
    }
}
