package cn.ac.fage.accessmesh.access.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JWT 签名密钥强度 fail-fast 边界锁（release-preview 双轨评审 P3-3，2026-09-16 用户拍板）。
 * <p>
 * sa-token-jwt 对 HS256 密钥长度零校验——短密钥静默签发弱签名；OAuth2AppServiceImpl
 * 启动 @PostConstruct 经 {@link OAuth2JwtSupport#validateHmacSecretStrength(String)} 拒绝。
 * 边界取 31/32 精确两侧（非默认值边界，防未来阈值漂移无感）。
 * </p>
 */
class OAuth2JwtSupportSecretStrengthTest {

    @Test
    void secretBelow32CharsShouldFailFast() {
        assertThatThrownBy(() -> OAuth2JwtSupport.validateHmacSecretStrength("x".repeat(31)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(String.valueOf(OAuth2JwtSupport.MIN_SECRET_LENGTH));
    }

    @Test
    void blankOrNullSecretShouldFailFast() {
        assertThatThrownBy(() -> OAuth2JwtSupport.validateHmacSecretStrength(null))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> OAuth2JwtSupport.validateHmacSecretStrength("   "))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void secretOfExactly32CharsShouldPass() {
        assertThatCode(() -> OAuth2JwtSupport.validateHmacSecretStrength("x".repeat(32)))
            .doesNotThrowAnyException();
    }
}
