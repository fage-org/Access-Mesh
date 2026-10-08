package cn.ac.fage.accessmesh.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;
import static org.assertj.core.api.Assertions.assertThat;

/** 产品入口构造租户夹具；不插入租户主数据，也不绕过首次改密。 */
final class E2eTenantSupport {
    static final String TENANT_CODE = "e2e-customer";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private E2eTenantSupport() {}

    static String platformLogin(String base, String password, Function<String,String> readCaptcha) {
        JsonNode captcha = post(base + "/api/access/platform-auth/captcha", null, Map.of());
        String id = captcha.path("captchaId").asText();
        return post(base + "/api/access/platform-auth/login", null,
            Map.of("username", "admin", "password", password, "captchaId", id, "captchaCode", readCaptcha.apply(id)))
            .path("accessToken").asText();
    }

    static long openTenant(String base, String rootPassword, String code, String customerPassword, Function<String,String> readCaptcha) {
        String operator = platformLogin(base, rootPassword, readCaptcha);
        JsonNode opened = post(base + "/api/access/tenant/create", operator, Map.of("code", code, "name", code));
        JsonNode first = login(base, code, "admin", opened.path("initialPassword").asText(), readCaptcha);
        assertThat(first.path("forceResetPwd").asBoolean()).isTrue();
        post(base + "/api/access/user/reset-password", first.path("accessToken").asText(),
            Map.of("userId", first.path("userId").asLong(), "newPassword", customerPassword));
        JsonNode fresh = login(base, code, "admin", customerPassword, readCaptcha);
        assertThat(fresh.path("forceResetPwd").asBoolean()).isFalse();
        return opened.path("tenant").path("id").asLong();
    }

    static String finishForcedLogin(String base, JsonNode login, String username, Function<String,String> readCaptcha) {
        if (!login.path("forceResetPwd").asBoolean()) return login.path("accessToken").asText();
        String changed = "E2e-Customer-Changed123!";
        post(base + "/api/access/user/reset-password", login.path("accessToken").asText(),
            Map.of("userId", login.path("userId").asLong(), "newPassword", changed));
        return login(base, TENANT_CODE, username, changed, readCaptcha).path("accessToken").asText();
    }

    static JsonNode login(String base, String code, String username, String password, Function<String,String> readCaptcha) {
        JsonNode captcha = post(base + "/api/access/auth/captcha", null, Map.of());
        String id = captcha.path("captchaId").asText();
        return post(base + "/api/access/auth/login", null, Map.of("tenantCode", code, "username", username,
            "password", password, "captchaId", id, "captchaCode", readCaptcha.apply(id)));
    }

    static JsonNode post(String url, String token, Object body) {
        try {
            var builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            if (token != null) builder.header("Authorization", "Bearer " + token);
            var response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode envelope = JSON.readTree(response.body());
            assertThat(response.statusCode()).as("HTTP %s", url).isEqualTo(200);
            assertThat(envelope.path("code").asInt()).as("%s: %s", url, envelope.path("message")).isEqualTo(200);
            return envelope.path("data");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw new IllegalStateException(exception);
        } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
    }
}
