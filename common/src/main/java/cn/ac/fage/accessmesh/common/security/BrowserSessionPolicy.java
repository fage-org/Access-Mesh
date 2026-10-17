package cn.ac.fage.accessmesh.common.security;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.Function;

/** Browser authentication protocol shared by the gateway and servlet adapter. */
public final class BrowserSessionPolicy {
    public static final String SESSION_HEADER = "X-AM-Session";
    public static final String REQUEST_HEADER = "X-AM-Browser-Request";
    public static final String SESSION_ATTRIBUTE = "browserSessionId";
    public static final String DEFAULT_ORIGINS = "http://localhost:8848,http://127.0.0.1:8848,http://localhost:8890,http://127.0.0.1:8890,http://127.0.0.1";
    public enum Realm { TENANT, PLATFORM }
    public record Input(String method, String path, String authorization, String origin, String referer,
                        String browserRequest, String sessionId, Map<String, List<String>> cookies, boolean serviceCredential) {
        public Input(String method, String path, String authorization, String origin, String referer,
                     String browserRequest, String sessionId, Map<String, List<String>> cookies) {
            this(method, path, authorization, origin, referer, browserRequest, sessionId, cookies, false);
        }
        @Override public String toString() { return "BrowserSessionInput[redacted]"; }
    }
    public record Candidate(Realm realm, String sessionId, String token, boolean secure) {
        @Override public String toString() { return "BrowserSessionCandidate[redacted]"; }
    }
    public static final class Rejected extends RuntimeException {
        private final int status;
        public Rejected(int status, String message) { super(message); this.status = status; }
        public int status() { return status; }
        public int code() { return status == 409 ? cn.ac.fage.accessmesh.common.enums.GlobalErrorCode.BROWSER_SESSION_CHANGED.code() : status; }
    }
    private final Set<String> origins;
    private static final Set<String> LEGACY_PUBLIC = Set.of(
        "/api/access/auth/captcha", "/api/access/auth/login", "/api/access/auth/logout",
        "/api/access/platform-auth/captcha", "/api/access/platform-auth/login", "/api/access/platform-auth/logout",
        "/api/access/auth/oauth2/token", "/api/access/auth/oauth2/refresh", "/api/access/auth/oauth2/revoke");

    public BrowserSessionPolicy(String origins) {
        this.origins = Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty())
            .map(value -> normalizeOrigin(value, false)).collect(Collectors.toUnmodifiableSet());
        for (String origin : this.origins) {
            URI uri = URI.create(origin);
            if (!"https".equals(uri.getScheme()) && !Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost()))
                throw new IllegalArgumentException("浏览器 HTTP Origin 只允许回环主机");
        }
    }
    public Candidate resolve(Input input) {
        if (!input.path().startsWith("/api/") || LEGACY_PUBLIC.contains(input.path())) return null;
        Realm realm = realm(input.path());
        boolean entry = isLogin(input.path()) || isLogout(input.path());
        boolean hasCookie = input.cookies().keySet().stream().anyMatch(name -> isCookie(name, realm));
        if (!entry && !hasCookie && input.sessionId() == null) return null;
        if ((hasCookie || entry) && input.authorization() != null && !input.authorization().isBlank())
            throw new Rejected(400, "Cookie 与 Bearer 不得混用，请只选择一种登录凭证");
        if (input.serviceCredential() && M2mCredentialEndpoints.matches(input.method(), input.path())) return null;
        if (input.authorization() != null && !input.authorization().isBlank())
            return null; // Without a matching Cookie this remains the explicit credential channel.
        String origin;
        try {
            origin = input.origin() != null ? normalizeOrigin(input.origin(), false) : normalizeOrigin(input.referer(), true);
        } catch (IllegalArgumentException exception) {
            throw new Rejected(403, "浏览器请求来源缺失或无效");
        }
        if (!origins.contains(origin) || !"POST".equals(input.method()) || !"1".equals(input.browserRequest()))
            throw new Rejected(403, "浏览器请求来源或防伪造校验失败");
        boolean secure = origin.startsWith("https:");
        if (isLogin(input.path())) return new Candidate(realm, null, null, secure);
        requireId(input.sessionId());
        List<String> tokens = input.cookies().get(cookieName(realm, secure));
        if (tokens == null || tokens.size() != 1 || tokens.getFirst() == null || tokens.getFirst().isBlank())
            throw new Rejected(409, "页面会话与浏览器凭据不一致，请重新载入");
        return new Candidate(realm, input.sessionId(), tokens.getFirst(), secure);
    }
    public static Realm realm(String path) {
        return PlatformEndpoints.containsPath(path) || path.equals(loginPath(Realm.PLATFORM)) || path.equals(logoutPath(Realm.PLATFORM))
            ? Realm.PLATFORM : Realm.TENANT;
    }
    public static String loginPath(Realm realm) { return basePath(realm) + "/browser-login"; }
    public static String logoutPath(Realm realm) { return basePath(realm) + "/browser-logout"; }
    private static String basePath(Realm realm) {
        return realm == Realm.TENANT ? "/api/access/auth" : "/api/access/platform-auth";
    }
    public static boolean isLogin(String path) { return path.equals(loginPath(Realm.TENANT)) || path.equals(loginPath(Realm.PLATFORM)); }
    public static boolean isLogout(String path) { return path.equals(logoutPath(Realm.TENANT)) || path.equals(logoutPath(Realm.PLATFORM)); }
    public static void requireId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{43}")) throw new Rejected(409, "页面会话标记无效，请重新登录");
    }
    public static void validateBinding(Candidate candidate, Function<Candidate, String> lookup) {
        if (candidate == null || candidate.token() == null) return;
        String expected = lookup.apply(candidate);
        if (expected == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), candidate.sessionId().getBytes(StandardCharsets.UTF_8)))
            throw new Rejected(409, "页面会话已失效，请重新登录");
    }
    public static boolean isCookie(String name, Realm realm) {
        return name.equals(cookieName(realm, true)) || name.equals(cookieName(realm, false));
    }
    public static boolean isCookie(String name) { return isCookie(name, Realm.TENANT) || isCookie(name, Realm.PLATFORM); }
    private static String normalizeOrigin(String value, boolean referer) {
        if (value == null) throw new IllegalArgumentException("missing origin");
        URI uri = URI.create(value);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if ((!scheme.equals("https") && !scheme.equals("http")) || uri.getHost() == null || uri.getRawUserInfo() != null
            || (!referer && ((uri.getRawPath() != null && !uri.getRawPath().isEmpty()) || uri.getRawQuery() != null || uri.getRawFragment() != null)))
            throw new IllegalArgumentException("invalid origin");
        int port = uri.getPort();
        if (port < -1 || port == 0 || port > 65535) throw new IllegalArgumentException("invalid port");
        boolean defaultPort = port == -1 || (scheme.equals("https") && port == 443) || (scheme.equals("http") && port == 80);
        return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + (defaultPort ? "" : ":" + port);
    }
    public static String cookieName(Realm realm, boolean secure) {
        return (secure ? "__Host-am-" : "am-local-") + (realm == Realm.TENANT ? "tenant" : "platform");
    }
}
