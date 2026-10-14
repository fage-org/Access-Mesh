package cn.ac.fage.accessmesh.perm.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CIDR 参数校验回归锁（T-PERM-109）：非法前缀（负数/超地址族位数）与非法地址字面量
 * 令整个 IP 条件不满足（fail-closed）——白名单负前缀不再「匹配一切」扩大适用范围，
 * 黑名单超界值不再经「无匹配→反转」退化为放行；数值解析仅接受字面量形态
 * （去除 InetAddress.getByName 的主机名解析副作用）。
 */
class ConditionEvalUtilsCidrGuardTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 白名单条件是否满足（clientIp 在 10.x 网段） */
    private boolean whitelistSatisfied(String cidrText) throws Exception {
        return ConditionEvalUtils.evalIpList(
            JSON.readTree("[\"" + cidrText + "\"]"), "10.1.2.3", true);
    }

    /** 黑名单条件是否满足（clientIp 在 10.x 网段） */
    private boolean blacklistSatisfied(String cidrText) throws Exception {
        return ConditionEvalUtils.evalIpList(
            JSON.readTree("[\"" + cidrText + "\"]"), "10.1.2.3", false);
    }

    @Test
    @DisplayName("白名单 /-1：非法负前缀不满足（旧实现匹配一切→满足，红跑实证）")
    void whitelistNegativePrefixNotSatisfied() throws Exception {
        assertThat(whitelistSatisfied("192.168.0.0/-1")).isFalse();
    }

    @Test
    @DisplayName("白名单 /-100：深负前缀同样不满足（旧实现红）")
    void whitelistDeepNegativePrefixNotSatisfied() throws Exception {
        assertThat(whitelistSatisfied("10.0.0.0/-100")).isFalse();
    }

    @Test
    @DisplayName("黑名单 IPv4 /33：超地址族位数不满足（旧实现无匹配→反转放行，红跑实证）")
    void blacklistOverFamilyBitsNotSatisfied() throws Exception {
        assertThat(blacklistSatisfied("192.168.0.0/33")).isFalse();
    }

    @Test
    @DisplayName("合法 /0 与 /32 用途保持：/0 匹配同族全部、/32 精确单址、/24 前缀匹配")
    void legitPrefixesPreserved() throws Exception {
        assertThat(whitelistSatisfied("0.0.0.0/0")).isTrue();
        assertThat(whitelistSatisfied("10.1.2.3/32")).isTrue();
        assertThat(whitelistSatisfied("10.1.2.0/24")).isTrue();
        assertThat(whitelistSatisfied("10.1.2.4/32")).isFalse();
        // 黑名单合法无匹配 → 允许（既有语义保持）
        assertThat(blacklistSatisfied("192.168.0.0/16")).isTrue();
    }

    @Test
    @DisplayName("列表含任一非法项 → 整条 IP 条件不满足（白/黑名单同 fail-closed）")
    void anyInvalidEntryFailsWholeCondition() throws Exception {
        boolean whitelist = ConditionEvalUtils.evalIpList(
            JSON.readTree("[\"10.0.0.0/8\",\"192.168.0.0/-1\"]"), "10.1.2.3", true);
        assertThat(whitelist).isFalse();

        boolean blacklist = ConditionEvalUtils.evalIpList(
            JSON.readTree("[\"172.16.0.0/12\",\"192.168.0.0/33\"]"), "10.1.2.3", false);
        assertThat(blacklist).isFalse();
    }

    @Test
    @DisplayName("主机名样串/带符号前缀/非数字前缀：形态拒绝（无 DNS 解析路径）")
    void hostnameAndMalformedPrefixesRejected() throws Exception {
        for (String bad : new String[] {
            "evil.example.com", "evil.example.com/24", "10.0.0.0/+8", "10.0.0.0/ 8",
            "10.0.0.0/8x", "10.0.0.0/", "10.0.0.256/24", "999.1.1.1",
            "10.0.0.0/1234", "not-an-ip"}) {
            assertThat(ConditionEvalUtils.isValidCidr(bad)).as("isValidCidr(%s)", bad).isFalse();
        }
        // 客户端值同样须为字面量：主机名样串不做 DNS 解析、不匹配
        assertThat(ConditionEvalUtils.ipMatchesCidr("evil.example.com", "10.0.0.0/8")).isFalse();
    }

    @Test
    @DisplayName("isValidCidr 合法形态：单地址、/0、IPv6 字面量与 /128；IPv4 /33、IPv6 /129 拒绝")
    void validFormsAccepted() {
        assertThat(ConditionEvalUtils.isValidCidr("10.1.2.3")).isTrue();
        assertThat(ConditionEvalUtils.isValidCidr("0.0.0.0/0")).isTrue();
        assertThat(ConditionEvalUtils.isValidCidr("10.0.0.0/8")).isTrue();
        assertThat(ConditionEvalUtils.isValidCidr("192.168.1.0/32")).isTrue();
        assertThat(ConditionEvalUtils.isValidCidr("2001:db8::/32")).isTrue();
        assertThat(ConditionEvalUtils.isValidCidr("::1/128")).isTrue();
        assertThat(ConditionEvalUtils.isValidCidr("192.168.1.0/33")).isFalse();
        assertThat(ConditionEvalUtils.isValidCidr("2001:db8::/129")).isFalse();
        assertThat(ConditionEvalUtils.isValidCidr("::1/-1")).isFalse();
    }

    @Test
    @DisplayName("IPv6 匹配与跨族不匹配保持：同族前缀匹配、IPv4 条目不匹配 IPv6 客户端")
    void ipv6MatchingPreserved() {
        assertThat(ConditionEvalUtils.ipMatchesCidr("2001:db8::1", "2001:db8::/32")).isTrue();
        assertThat(ConditionEvalUtils.ipMatchesCidr("2001:db8::1", "2001:db9::/32")).isFalse();
        assertThat(ConditionEvalUtils.ipMatchesCidr("2001:db8::1", "10.0.0.0/8")).isFalse();
    }
}
