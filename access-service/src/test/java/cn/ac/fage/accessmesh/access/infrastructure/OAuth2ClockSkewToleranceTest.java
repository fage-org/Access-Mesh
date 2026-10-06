package cn.ac.fage.accessmesh.access.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OAuth2 链签发时间未来偏移容忍窗语义锁（2026-10-06 拍板：默认 3 秒可配、配 0 关闭）。
 * <p>
 * 旧实现 issuedAt &lt;= now 无容忍——多实例节点间 NTP 级时钟漂移下，慢时钟节点拒绝
 * 稍早签发的链（秒级可用性毛刺、重试即成功）。红跑实证：旧语义下「签发于 now+2
 * 且容忍 3 放行」用例失败。过期判定不放宽（expiresAt 严格 &gt; now）。
 * </p>
 */
class OAuth2ClockSkewToleranceTest {

    /** 60 字符、$2 开头的 BCrypt 形态哈希（指纹取前 29 位；29+31=60）。 */
    private static final String HASH = "$2a$10$abcdefghijklmnopqrstuv1111111111111111111111111111111";

    private static String fingerprint() {
        return OAuth2JwtSupport.passwordFingerprint(HASH);
    }

    @Test
    @DisplayName("容忍窗内：签发于 now+2、容忍 3 → 放行（旧实现拒绝）")
    void withinToleranceAccepted() {
        long now = System.currentTimeMillis() / 1000;
        assertThat(OAuth2JwtSupport.isCurrentCredential(HASH, fingerprint(),
            now + 2, now + 3600, 3)).isTrue();
    }

    @Test
    @DisplayName("容忍窗外拒绝且过期判定不放宽")
    void beyondToleranceAndExpiryRejected() {
        long now = System.currentTimeMillis() / 1000;
        assertThat(OAuth2JwtSupport.isCurrentCredential(HASH, fingerprint(),
            now + 4, now + 3600, 3)).isFalse();
        // 已过期链即使容忍窗再大也拒绝
        assertThat(OAuth2JwtSupport.isCurrentCredential(HASH, fingerprint(),
            now - 10, now - 1, 60)).isFalse();
    }

    @Test
    @DisplayName("配 0 关闭：now+1 即拒绝；正常 now 签发不受影响")
    void zeroToleranceDisables() {
        long now = System.currentTimeMillis() / 1000;
        assertThat(OAuth2JwtSupport.isCurrentCredential(HASH, fingerprint(),
            now + 1, now + 3600, 0)).isFalse();
        assertThat(OAuth2JwtSupport.isCurrentCredential(HASH, fingerprint(),
            now, now + 3600, 0)).isTrue();
    }

    @Test
    @DisplayName("Map 载荷重载同语义：chain_iat=now+2 容忍 3 放行、容忍 0 拒绝")
    void payloadOverloadSameSemantics() {
        long now = System.currentTimeMillis() / 1000;
        Map<String, Object> payloads = Map.of(
            OAuth2JwtSupport.PASSWORD_FINGERPRINT_CLAIM, fingerprint(),
            OAuth2JwtSupport.CHAIN_ISSUED_AT_CLAIM, String.valueOf(now + 2),
            OAuth2JwtSupport.CHAIN_EXPIRES_AT_CLAIM, String.valueOf(now + 3600));
        assertThat(OAuth2JwtSupport.isCurrentCredential(HASH, payloads, 3)).isTrue();
        assertThat(OAuth2JwtSupport.isCurrentCredential(HASH, payloads, 0)).isFalse();
    }
}
