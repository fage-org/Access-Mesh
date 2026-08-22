package cn.ac.fage.accessmesh.gateway.filter;

import cn.dev33.satoken.SaManager;import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.context.SaTokenContextForThreadLocal;
import cn.dev33.satoken.context.SaTokenContextForThreadLocalStorage;
import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.context.model.SaResponse;
import cn.dev33.satoken.context.model.SaStorage;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AuthTokenFilter 平台用户会话校验测试（T-ACCESS-011 验收 8：Gateway 校验）。
 * <p>
 * P0 回归锚点：sa-token 1.38.0 默认 StpLogic 的 {@code StpUtil.getExtra(loginId,key)}
 * 无条件抛 ApiDisabledException（仅 sa-token-jwt 插件支持），原实现导致所有合法令牌
 * 请求恒 401「租户信息缺失」。修复后身份读取走共享 Redis 会话
 * {@code StpUtil.getSessionByLoginId(loginId,false)}，键为 access-service
 * AuthServiceImpl 登录时写入的 tenantId/subjectTypeCode/operatorName。
 * </p>
 * <p>
 * 本测试以 SaTokenDaoDefaultImpl（内存）+ ThreadLocal 上下文模拟 access-service 登录
 * （与生产唯一的差异是 dao 存储；令牌校验/会话读取路径与共享 Redis 语义一致——
 * 同一 token-name/loginType/键格式，由双端 sa-token 配置一致性测试钉住）。
 * SaManager 为进程级静态门面，@BeforeEach 保存并在 @AfterEach 恢复，避免污染其他测试。
 * </p>
 */
class AuthTokenFilterTest {

    private static final long LOGIN_ID = 1001L;
    private static final long TENANT_ID = 42L;

    private AuthTokenFilter filter;
    private SaTokenDao originalDao;
    private SaTokenConfig originalConfig;
    private SaTokenContext originalContext;

    @BeforeEach
    void setUp() {
        filter = new AuthTokenFilter(new ObjectMapper());
        originalDao = SaManager.getSaTokenDao();
        originalConfig = SaManager.getConfig();
        originalContext = SaManager.getSaTokenContext();

        // 与 access-service/gateway 生产配置一致的权威值（application.yml 双端断言钉住）
        SaTokenConfig config = new SaTokenConfig();
        config.setTimeout(7200);
        config.setActiveTimeout(1800);
        config.setTokenName("Authorization");
        config.setTokenPrefix("Bearer");
        config.setTokenStyle("uuid");
        config.setIsReadCookie(false);
        config.setIsReadHeader(true);
        SaManager.setConfig(config);
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        SaManager.setSaTokenContext(new SaTokenContextForThreadLocal());
    }

    @AfterEach
    void tearDown() {
        SaManager.setSaTokenDao(originalDao);
        SaManager.setConfig(originalConfig);
        SaManager.setSaTokenContext(originalContext);
        SaTokenContextForThreadLocalStorage.clearBox();
    }

    /**
     * 模拟 access-service AuthServiceImpl.login 的会话写入：
     * StpUtil.login(userId) + session.set(tenantId/subjectTypeCode/operatorName)。
     */
    private String loginAsAccessService(boolean withTenantId, boolean withSubjectTypeCode,
                                        boolean withOperatorName) {
        SaTokenContextForThreadLocalStorage.setBox(mock(SaRequest.class),
            mock(SaResponse.class), new MapBackedStorage());
        StpUtil.login(LOGIN_ID);
        SaSession session = StpUtil.getSessionByLoginId(LOGIN_ID, false);
        if (withTenantId) {
            session.set("tenantId", TENANT_ID);
        }
        if (withSubjectTypeCode) {
            session.set("subjectTypeCode", "ADMIN_USER");
        }
        if (withOperatorName) {
            session.set("operatorName", "alice");
        }
        String token = StpUtil.getTokenValue();
        SaTokenContextForThreadLocalStorage.clearBox();
        return token;
    }

    private MockServerWebExchange exchangeWithBearer(String token) {
        MockServerHttpRequest request = MockServerHttpRequest.post("/admin/user/page")
            .header("Authorization", "Bearer " + token)
            .build();
        return MockServerWebExchange.from(request);
    }

    @Test
    @DisplayName("合法令牌 + 完整会话：放行并绑定 userId/tenantId/subjectTypeCode/userName")
    void validToken_bindsIdentityAttributes() {
        String token = loginAsAccessService(true, true, true);
        MockServerWebExchange exchange = exchangeWithBearer(token);
        AtomicBoolean chained = new AtomicBoolean(false);
        GatewayFilterChain chain = chainOf(chained);

        filter.filter(exchange, chain).block();

        assertThat(chained.get()).isTrue();
        // getLoginIdByToken 经 dao 反序列化返回 String 形式的 loginId
        assertThat((Object) exchange.getAttribute("userId")).isEqualTo(String.valueOf(LOGIN_ID));
        assertThat((Object) exchange.getAttribute("tenantId")).isEqualTo(TENANT_ID);
        assertThat((Object) exchange.getAttribute("subjectTypeCode")).isEqualTo("ADMIN_USER");
        assertThat((Object) exchange.getAttribute("userName")).isEqualTo("alice");
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("P0 回归：合法令牌不再因 getExtra 恒 401（修复后读 SaSession）")
    void validToken_noLongerRejectedByExtraApi() {
        String token = loginAsAccessService(true, true, true);
        MockServerWebExchange exchange = exchangeWithBearer(token);
        AtomicBoolean chained = new AtomicBoolean(false);

        filter.filter(exchange, chainOf(chained)).block();

        assertThat(chained.get()).as("完整会话的合法令牌必须放行（原 getExtra 实现恒 401）").isTrue();
    }

    @Test
    @DisplayName("无令牌：401 未登录")
    void missingToken_rejected401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/admin/user/page").build());
        AtomicBoolean chained = new AtomicBoolean(false);

        filter.filter(exchange, chainOf(chained)).block();

        assertThat(chained.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("无效令牌：401 登录已过期（getLoginIdByToken 返回 null，不得 NPE→500）")
    void invalidToken_rejected401WithoutNpe() {
        MockServerWebExchange exchange = exchangeWithBearer("a-not-existing-token");
        AtomicBoolean chained = new AtomicBoolean(false);

        filter.filter(exchange, chainOf(chained)).block();

        assertThat(chained.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("会话缺 tenantId：401 租户信息缺失（fail-closed）")
    void sessionWithoutTenantId_rejected401() {
        String token = loginAsAccessService(false, true, true);
        MockServerWebExchange exchange = exchangeWithBearer(token);
        AtomicBoolean chained = new AtomicBoolean(false);

        filter.filter(exchange, chainOf(chained)).block();

        assertThat(chained.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("会话缺/空白 subjectTypeCode：401 登录信息缺失（fail-closed）")
    void sessionWithoutSubjectType_rejected401() {
        String token = loginAsAccessService(true, false, true);
        MockServerWebExchange exchange = exchangeWithBearer(token);
        AtomicBoolean chained = new AtomicBoolean(false);

        filter.filter(exchange, chainOf(chained)).block();

        assertThat(chained.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("会话整体缺失（令牌有效但 session 被逐出）：401 租户信息缺失，不创建幽灵会话")
    void sessionEvicted_rejected401WithoutCreatingSession() {
        String token = loginAsAccessService(true, true, true);
        // 仅逐出会话（保留 token→loginId 映射）：getSessionByLoginId(id,false) 必须返回 null
        SaManager.getSaTokenDao().deleteSession(StpUtil.stpLogic.splicingKeySession(LOGIN_ID));

        MockServerWebExchange exchange = exchangeWithBearer(token);
        AtomicBoolean chained = new AtomicBoolean(false);

        filter.filter(exchange, chainOf(chained)).block();

        assertThat(chained.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // false（不回建）：会话键仍不存在
        assertThat(SaManager.getSaTokenDao().getSession(
            StpUtil.stpLogic.splicingKeySession(LOGIN_ID))).isNull();
    }

    @Test
    @DisplayName("operatorName 缺失：不阻断（软失败），仅不设置 userName 属性")
    void operatorNameMissing_softFail() {
        String token = loginAsAccessService(true, true, false);
        MockServerWebExchange exchange = exchangeWithBearer(token);
        AtomicBoolean chained = new AtomicBoolean(false);

        filter.filter(exchange, chainOf(chained)).block();

        assertThat(chained.get()).isTrue();
        assertThat((Object) exchange.getAttribute("userName")).isNull();
    }

    @Test
    @DisplayName("skipAuth 白名单属性：直接放行，不校验令牌")
    void skipAuthAttribute_passesThrough() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/admin/auth/login").build());
        exchange.getAttributes().put("skipAuth", Boolean.TRUE);
        AtomicBoolean chained = new AtomicBoolean(false);

        filter.filter(exchange, chainOf(chained)).block();

        assertThat(chained.get()).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Cookie 备选提取：Authorization Cookie 中的裸令牌可完成校验")
    void cookieFallback_extractsToken() {
        String token = loginAsAccessService(true, true, true);
        MockServerHttpRequest request = MockServerHttpRequest.post("/admin/user/page")
            .cookie(new org.springframework.http.HttpCookie("Authorization", token))
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean chained = new AtomicBoolean(false);

        filter.filter(exchange, chainOf(chained)).block();

        assertThat(chained.get()).isTrue();
        assertThat((Object) exchange.getAttribute("tenantId")).isEqualTo(TENANT_ID);
    }

    @Test
    @DisplayName("401 响应体为统一 GatewayResponse JSON 结构")
    void unauthorizedBody_isUnifiedJson() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.post("/admin/user/page").build());
        AtomicBoolean chained = new AtomicBoolean(false);

        filter.filter(exchange, chainOf(chained)).block();
        String body = exchange.getResponse().getBodyAsString().block();

        assertThat(body).isNotNull();
        assertThat(body).contains("\"code\":401");
        assertThat(body).contains("未登录");
    }

    private GatewayFilterChain chainOf(AtomicBoolean chained) {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(inv -> {
            chained.set(true);
            return Mono.empty();
        });
        return chain;
    }

    /**
     * HashMap 支撑的 SaStorage 测试替身（登录路径经 SaHolder.getStorage 写 justCreated 标记）。
     */
    private static final class MapBackedStorage implements SaStorage {
        private final Map<String, Object> data = new HashMap<>();

        @Override
        public Object getSource() {
            return data;
        }

        @Override
        public Object get(String key) {
            return data.get(key);
        }

        @Override
        public SaStorage set(String key, Object value) {
            data.put(key, value);
            return this;
        }

        @Override
        public SaStorage delete(String key) {
            data.remove(key);
            return this;
        }
    }
}
