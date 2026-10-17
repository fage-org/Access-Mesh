package cn.ac.fage.accessmesh.common.security;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static cn.ac.fage.accessmesh.common.security.BrowserSessionPolicy.*;
import static org.assertj.core.api.Assertions.*;

class BrowserSessionPolicyTest {
    private final BrowserSessionPolicy policy = new BrowserSessionPolicy("https://admin.example.test,http://127.0.0.1:8848");
    private final String id = "a".repeat(43);
    private Input request(String authorization, String origin, String session, Map<String, List<String>> cookies) {
        return new Input("POST", "/api/access/user/page", authorization, origin, null, "1", session, cookies);
    }
    private Map<String, List<String>> tenantCookies() {
        return Map.of(cookieName(Realm.TENANT, true), List.of("opaque-token"));
    }
    @Test void selectsOnlyTheRequestedRealmAndSession() {
        var cookies = Map.of(cookieName(Realm.TENANT, true), List.of("tenant-token"),
            cookieName(Realm.PLATFORM, true), List.of("platform-token"));
        assertThat(policy.resolve(request(null, "https://admin.example.test", id, cookies)).token()).isEqualTo("tenant-token");
    }
    @Test void refusesMixedCredentialsEvenWhenTheirValuesAgree() {
        assertThatThrownBy(() -> policy.resolve(request("Bearer opaque-token", "https://admin.example.test", id, tenantCookies())))
            .isInstanceOf(Rejected.class).hasMessageContaining("混用");
    }
    @Test void neverFallsBackToAnotherCookieWhenTheCapturedSessionIsMissing() {
        Candidate candidate = policy.resolve(request(null, "https://admin.example.test", "b".repeat(43), tenantCookies()));
        assertThatThrownBy(() -> validateBinding(candidate, ignored -> id))
            .isInstanceOf(Rejected.class);
    }
    @Test void rejectsMissingAndUntrustedOriginIncludingAnAttackerSuffix() {
        for (String origin : new String[]{null, "null", "https://admin.example.test.attacker.test"}) {
            assertThatThrownBy(() -> policy.resolve(request(null, origin, id, tenantCookies())))
                .isInstanceOf(Rejected.class);
        }
    }
    @Test void acceptsExactRefererFallbackButNotAnExplicitInvalidOrigin() {
        Input referrer = new Input("POST", "/api/access/user/page", null, null,
            "https://admin.example.test/welcome?x=1", "1", id, tenantCookies());
        assertThat(policy.resolve(referrer).token()).isEqualTo("opaque-token");
        assertThatThrownBy(() -> policy.resolve(new Input("POST", "/api/access/user/page", null, "null",
            referrer.referer(), "1", id, tenantCookies()))).isInstanceOf(Rejected.class);
    }
    @Test void preservesBearerWithoutCookieAndIgnoresTheOtherRealmCookie() {
        assertThat(policy.resolve(request("Bearer tool", null, null,
            Map.of(cookieName(Realm.PLATFORM, true), List.of("platform"))))).isNull();
    }
    @Test void rejectsDuplicateAuthenticationCookies() {
        assertThatThrownBy(() -> policy.resolve(request(null, "https://admin.example.test", id,
            Map.of(cookieName(Realm.TENANT, true), List.of("first", "second")))))
            .isInstanceOf(Rejected.class);
    }
    @Test void rejectsInsecurePublicOriginConfiguration() {
        assertThatThrownBy(() -> new BrowserSessionPolicy("http://admin.example.test"))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void aReadableHandleCannotAuthenticateWithoutTheBoundCookieAndServerRecord() {
        assertThatThrownBy(() -> policy.resolve(request(null, "https://admin.example.test", id, Map.of())))
            .isInstanceOf(Rejected.class);
        Candidate candidate = policy.resolve(request(null, "https://admin.example.test", id, tenantCookies()));
        assertThatThrownBy(() -> validateBinding(candidate, key -> null)).isInstanceOf(Rejected.class);
        assertThatThrownBy(() -> validateBinding(candidate, key -> "other-token")).isInstanceOf(Rejected.class);
        assertThatCode(() -> validateBinding(candidate, key -> id)).doesNotThrowAnyException();
        assertThat(candidate.toString()).doesNotContain("opaque-token");
    }
    @Test void preservesExplicitServiceCredentialDispatchWithoutAffectingMixedBearerRejection() {
        Input service = new Input("POST", "/api/access/auth/check", null, null, null, null, null, tenantCookies(), true);
        assertThat(policy.resolve(service)).isNull();
        assertThatThrownBy(() -> policy.resolve(new Input(service.method(), service.path(), "Bearer tool", null, null,
            null, null, tenantCookies(), true))).isInstanceOf(Rejected.class);
    }
    @Test void browserLoginStillRequiresTrustedSourceAndTheCustomRequestHeader() {
        assertThatThrownBy(() -> policy.resolve(new Input("POST", loginPath(Realm.TENANT), null, "https://admin.example.test",
            null, null, null, Map.of()))).isInstanceOf(Rejected.class);
        assertThat(policy.resolve(new Input("POST", loginPath(Realm.TENANT), null, "https://admin.example.test",
            null, "1", null, Map.of())).token()).isNull();
    }
    @Test void loopbackDevelopmentUsesSeparateCookieNames() {
        var cookies = Map.of(cookieName(Realm.TENANT, false), List.of("local-token"));
        assertThat(policy.resolve(request(null, "http://127.0.0.1:8848", id, cookies)).secure()).isFalse();
        assertThatThrownBy(() -> policy.resolve(request(null, "https://admin.example.test", id, cookies)))
            .isInstanceOf(Rejected.class);
    }
    @Test void logoutNeedsTheOriginalCookieAndDoesNotAuthenticateUsingTheMarkerAlone() {
        assertThatThrownBy(() -> policy.resolve(new Input("POST", logoutPath(Realm.TENANT), null,
            "https://admin.example.test", null, "1", id, Map.of()))).isInstanceOf(Rejected.class);
    }
    @Test void eachRealmUsesAFixedCookieNameAcrossLogins() {
        assertThat(cookieName(Realm.TENANT, true)).isEqualTo("__Host-am-tenant");
        assertThat(cookieName(Realm.TENANT, true)).isNotEqualTo(cookieName(Realm.PLATFORM, true));
    }
}
