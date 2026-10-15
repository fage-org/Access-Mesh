package cn.ac.fage.accessmesh.access.infrastructure.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 密码规则字节边界回归（T-PERM-110）。
 * <p>
 * BCrypt 只参与前 72 字节：字符数规则（8-32 位含字母数字）允许的 26 字符
 * 汉字+字母+数字密码 UTF-8 可达 74 字节，尾部 2 字节被静默忽略——两个
 * 「不同」密码哈希后相同。锁 exceedsBcryptByteLimit 的字节边界与字符规则的关系。
 * </p>
 */
class CredentialPasswordsTest {

    private static final String CJK = "密";

    @Test
    void shouldAcceptPasswordAtExactByteBoundary() {
        // 23 汉字(69B) + a1!(3B) = 72 字节：字符规则与字节规则同时满足
        String password = CJK.repeat(23) + "a1!";
        assertThat(password.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(72);
        assertThat(CredentialPasswords.isValid(password)).isTrue();
        assertThat(CredentialPasswords.exceedsBcryptByteLimit(password)).isFalse();
    }

    @Test
    void shouldRejectPasswordJustOverByteBoundary() {
        // 24 汉字(72B) + a1(2B) = 74 字节：26 字符、含字母数字——字符规则通过、字节规则拒绝
        String password = CJK.repeat(24) + "a1";
        assertThat(password.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(74);
        assertThat(CredentialPasswords.isValid(password)).isTrue();
        assertThat(CredentialPasswords.exceedsBcryptByteLimit(password)).isTrue();
    }

    @Test
    void shouldNeverFlagPureAsciiWithinCharacterRule() {
        // 纯 ASCII 32 字符 = 32 字节，字符规则上限内永不触字节边界
        String password = "a".repeat(16) + "1".repeat(16);
        assertThat(CredentialPasswords.exceedsBcryptByteLimit(password)).isFalse();
        assertThat(CredentialPasswords.isValid(password)).isTrue();
    }

    @Test
    void shouldTreatNullAndShortAsNotExceeding() {
        // null/短密码不走字节通道（字符规则另行拒绝），边界判定只对超限负责
        assertThat(CredentialPasswords.exceedsBcryptByteLimit(null)).isFalse();
        assertThat(CredentialPasswords.exceedsBcryptByteLimit("a1")).isFalse();
    }

    @Test
    void shouldKeepCharacterRuleUnchangedForWeakPasswords() {
        // 字节达标但字符规则失败（无数字）仍由 isValid 拒绝：两条规则独立生效
        String password = CJK.repeat(23) + "ab";
        assertThat(CredentialPasswords.exceedsBcryptByteLimit(password)).isFalse();
        assertThat(CredentialPasswords.isValid(password)).isFalse();
    }
}
