package cn.ac.fage.accessmesh.access.infrastructure.config;

import cn.ac.fage.accessmesh.common.model.R;
import cn.ac.fage.accessmesh.common.security.BrowserSessionPolicy;
import cn.ac.fage.accessmesh.common.security.BrowserSessionPolicy.Candidate;
import cn.ac.fage.accessmesh.common.security.BrowserSessionPolicy.Rejected;
import cn.dev33.satoken.SaManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Adapts an explicitly validated browser request to the existing Bearer authentication chain. */
@Component
@Order(-100)
public class BrowserSessionFilter extends OncePerRequestFilter {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BrowserSessionFilter.class);
    public static final String CANDIDATE = BrowserSessionFilter.class.getName() + ".candidate";
    private final BrowserSessionPolicy policy;
    private final ObjectMapper json;
    public BrowserSessionFilter(@Value("${accessmesh.browser-session.allowed-origins:${ACCESS_BROWSER_ALLOWED_ORIGINS:" + BrowserSessionPolicy.DEFAULT_ORIGINS + "}}") String origins,
                                ObjectMapper json) {
        this.policy = new BrowserSessionPolicy(origins); this.json = json;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        Candidate candidate;
        try {
            candidate = policy.resolve(new BrowserSessionPolicy.Input(request.getMethod(), request.getRequestURI(),
                single(request, "Authorization"), single(request, "Origin"), single(request, "Referer"),
                single(request, BrowserSessionPolicy.REQUEST_HEADER), single(request, BrowserSessionPolicy.SESSION_HEADER), cookies(request),
                request.getHeader("X-Credential-Id") != null || request.getHeader("X-Credential-Secret") != null));
            BrowserSessionPolicy.validateBinding(candidate, selected -> {
                    var logic = SaManager.getStpLogic(selected.realm() == BrowserSessionPolicy.Realm.PLATFORM ? "platform-operator" : "login");
                    var session = logic.getTokenSessionByToken(selected.token(), false);
                    Object marker = session == null ? null : session.get(BrowserSessionPolicy.SESSION_ATTRIBUTE);
                    return marker instanceof String value ? value : null;
                });
        } catch (Rejected rejected) {
            response.setStatus(rejected.status());
            response.setContentType("application/json;charset=UTF-8");
            json.writeValue(response.getOutputStream(), R.fail(rejected.code(), rejected.getMessage()));
            return;
        } catch (RuntimeException unavailable) {
            log.warn("Browser session validation unavailable: {}", unavailable.getClass().getSimpleName());
            response.setStatus(503);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            json.writeValue(response.getOutputStream(), R.fail(503, "登录校核暂不可用，请稍后重试"));
            return;
        }
        if (candidate == null) { chain.doFilter(request, response); return; }
        request.setAttribute(CANDIDATE, candidate);
        response.setHeader("Cache-Control", "no-store");
        if (candidate.token() == null) { chain.doFilter(request, response); return; }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public String getHeader(String name) {
                return "Authorization".equalsIgnoreCase(name) ? "Bearer " + candidate.token() : super.getHeader(name);
            }
            @Override public Enumeration<String> getHeaders(String name) {
                return "Authorization".equalsIgnoreCase(name) ? Collections.enumeration(List.of(getHeader(name))) : super.getHeaders(name);
            }
            @Override public Enumeration<String> getHeaderNames() {
                List<String> names = new ArrayList<>(Collections.list(super.getHeaderNames()));
                if (names.stream().noneMatch("Authorization"::equalsIgnoreCase)) names.add("Authorization");
                return Collections.enumeration(names);
            }
        }, response);
    }
    private static String single(HttpServletRequest request, String name) {
        List<String> values = Collections.list(request.getHeaders(name));
        if (values.size() > 1) throw new Rejected(400, "重复的认证或来源请求头");
        return values.isEmpty() ? null : values.getFirst();
    }
    private static Map<String, List<String>> cookies(HttpServletRequest request) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        if (request.getCookies() != null) for (Cookie cookie : request.getCookies())
            values.computeIfAbsent(cookie.getName(), ignored -> new ArrayList<>()).add(cookie.getValue());
        return values;
    }
}
