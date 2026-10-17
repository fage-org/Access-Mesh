package cn.ac.fage.accessmesh.access.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuth2JwtCodecTest {
    private static final String SECRET = "jwt-migration-compatibility-secret-32bytes";
    private static final String HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final String PAYLOAD = "{\"loginType\":\"oauth2\",\"loginId\":100,\"eff\":4102444800000}";

    @Test
    void shouldReadTheFormerIssuerFixtureWithoutChangingClaims() throws Exception {
        // Fixture was signed by the former Sa-Token 1.38 issuer using this synthetic key.
        String token;
        try (var input = getClass().getResourceAsStream("/fixtures/oauth2/legacy-hs256.jwt")) {
            token = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        var claims = OAuth2JwtSupport.getPayloads(token, "oauth2", SECRET);
        assertThat(claims.get("loginId").toString()).isEqualTo("100");
        assertThat(claims).containsEntry("tenant_id", "1").containsEntry("jti", "legacy-jti");
        assertThat(((Number) claims.get("eff")).longValue()).isEqualTo(4102444800000L);
        assertThat(OAuth2JwtSupport.audiencesOf(claims)).containsExactly("report");
    }

    @Test
    void shouldWriteHs256AndPreserveTheTrustedAbsoluteDeadline() throws Exception {
        long deadline = System.currentTimeMillis() + 60_000;
        String token = OAuth2JwtSupport.createToken("oauth2", 100L, "oauth2", 3600,
                Map.of("eff", deadline, "jti", "test-jti"), SECRET);
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);
        assertThat(Base64.getUrlDecoder().decode(parts[2])).isEqualTo(mac(parts[0] + "." + parts[1], SECRET));
        var json = JsonMapper.builder().build();
        assertThat(json.readTree(Base64.getUrlDecoder().decode(parts[0])).path("alg").asText()).isEqualTo("HS256");
        var claims = json.readTree(Base64.getUrlDecoder().decode(parts[1]));
        assertThat(claims.path("eff").asLong()).isEqualTo(deadline);
        assertThat(claims.path("loginType").asText()).isEqualTo("oauth2");
        assertThat(claims.path("device").asText()).isEqualTo("oauth2");
        assertThat(claims.path("jti").asText()).isEqualTo("test-jti");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"loginType\":\"other\",\"eff\":4102444800000}",
            "{\"loginType\":\"oauth2\"}",
            "{\"loginType\":\"oauth2\",\"eff\":0}",
            "{\"loginType\":\"oauth2\",\"eff\":\"invalid\"}"
    })
    void shouldRejectInvalidTypeOrDeadlineDespiteAValidSignature(String payload) throws Exception {
        String token = signed(HEADER, payload, SECRET);
        assertThatThrownBy(() -> OAuth2JwtSupport.getPayloads(token, "oauth2", SECRET)).isInstanceOf(SecurityException.class);
        assertThat(OAuth2JwtSupport.getTimeout(token, "oauth2", SECRET)).isEqualTo(-2);
    }

    @Test
    void shouldRejectWrongAlgorithmAndWrongSigningKey() throws Exception {
        String wrongAlgorithm = signed("{\"alg\":\"HS384\"}", PAYLOAD, SECRET);
        String wrongKey = signed(HEADER, PAYLOAD, "another-signing-secret-with-at-least-32-bytes");
        assertThatThrownBy(() -> OAuth2JwtSupport.getPayloads(wrongAlgorithm, "oauth2", SECRET)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> OAuth2JwtSupport.getPayloads(wrongKey, "oauth2", SECRET)).isInstanceOf(SecurityException.class);
    }

    private static String signed(String header, String payload, String secret) throws Exception {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        String input = encoder.encodeToString(header.getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return input + "." + encoder.encodeToString(mac(input, secret));
    }

    private static byte[] mac(String input, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(input.getBytes(StandardCharsets.US_ASCII));
    }
}
