package cn.ac.fage.accessmesh.gateway.service;

import cn.ac.fage.accessmesh.gateway.service.InterfaceSnapshotMatcher.Decision;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceSnapshotResp.ApiPermissionEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link InterfaceSnapshotMatcher} 单元测试（T-PERM-017 C4）
 * <p>
 * 覆盖三态决策：ALLOW / FALLBACK / DENY，以及 OR 合并语义。
 * </p>
 */
class InterfaceSnapshotMatcherTest {

    private static final String SERVICE = "admin-service";
    private static final String PATH = "/api/order";
    private static final String METHOD = "POST";
    private static final String IP_IN = "10.0.0.5";
    private static final String IP_OUT = "192.168.1.1";

    private static final String RULES_IP_WHITELIST_10 = """
        {"logic":"AND","items":[
          {"type":"IP_WHITELIST","params":{"cidrs":["10.0.0.0/8"]}}
        ]}""";

    @Test
    void shouldDeny_whenSnapshotEmpty() {
        InterfaceSnapshotResp snapshot = new InterfaceSnapshotResp(List.of());
        assertThat(InterfaceSnapshotMatcher.match(snapshot, SERVICE, METHOD, PATH, IP_IN))
            .isEqualTo(Decision.DENY);
    }

    @Test
    void shouldDeny_whenSnapshotNull() {
        assertThat(InterfaceSnapshotMatcher.match(null, SERVICE, METHOD, PATH, IP_IN))
            .isEqualTo(Decision.DENY);
    }

    @Test
    void shouldAllow_whenUnconditionalEntryMatches() {
        ApiPermissionEntry entry = new ApiPermissionEntry(
            SERVICE, METHOD, PATH, false, null, null, false);
        InterfaceSnapshotResp snapshot = new InterfaceSnapshotResp(List.of(entry));

        assertThat(InterfaceSnapshotMatcher.match(snapshot, SERVICE, METHOD, PATH, IP_IN))
            .isEqualTo(Decision.ALLOW);
    }

    @Test
    void shouldAllow_whenScopeAllUnconditionalMatches() {
        ApiPermissionEntry entry = new ApiPermissionEntry(
            SERVICE, null, null, false, null, null, true);
        InterfaceSnapshotResp snapshot = new InterfaceSnapshotResp(List.of(entry));

        assertThat(InterfaceSnapshotMatcher.match(snapshot, SERVICE, METHOD, PATH, IP_IN))
            .isEqualTo(Decision.ALLOW);
    }

    @Test
    void shouldDeny_whenNoRouteMatches() {
        ApiPermissionEntry entry = new ApiPermissionEntry(
            SERVICE, "GET", "/api/other", false, null, null, false);
        InterfaceSnapshotResp snapshot = new InterfaceSnapshotResp(List.of(entry));

        assertThat(InterfaceSnapshotMatcher.match(snapshot, SERVICE, METHOD, PATH, IP_IN))
            .isEqualTo(Decision.DENY);
    }

    @Test
    void shouldDeny_whenServiceCodeMismatch() {
        ApiPermissionEntry entry = new ApiPermissionEntry(
            "other-service", METHOD, PATH, false, null, null, false);
        InterfaceSnapshotResp snapshot = new InterfaceSnapshotResp(List.of(entry));

        assertThat(InterfaceSnapshotMatcher.match(snapshot, SERVICE, METHOD, PATH, IP_IN))
            .isEqualTo(Decision.DENY);
    }

    // ===== 含条件 + 内联 conditionRules 本地重评 =====

    @Test
    void shouldAllow_whenConditionalEntryLocalEvalPasses() {
        ApiPermissionEntry entry = new ApiPermissionEntry(
            SERVICE, METHOD, PATH, true, 1L, RULES_IP_WHITELIST_10, false);
        InterfaceSnapshotResp snapshot = new InterfaceSnapshotResp(List.of(entry));

        assertThat(InterfaceSnapshotMatcher.match(snapshot, SERVICE, METHOD, PATH, IP_IN))
            .isEqualTo(Decision.ALLOW);
    }

    @Test
    void shouldDeny_whenConditionalEntryLocalEvalFailsAndNoOtherMatch() {
        ApiPermissionEntry entry = new ApiPermissionEntry(
            SERVICE, METHOD, PATH, true, 1L, RULES_IP_WHITELIST_10, false);
        InterfaceSnapshotResp snapshot = new InterfaceSnapshotResp(List.of(entry));

        // clientIp 不在白名单 → 本地评失败，无其他 entry 兜底 → DENY
        assertThat(InterfaceSnapshotMatcher.match(snapshot, SERVICE, METHOD, PATH, IP_OUT))
            .isEqualTo(Decision.DENY);
    }

    // ===== 含条件但 conditionRules 未下发（gateway_evaluable=false） =====

    @Test
    void shouldFallback_whenConditionalEntryMissingRules() {
        ApiPermissionEntry entry = new ApiPermissionEntry(
            SERVICE, METHOD, PATH, true, 1L, null, false);
        InterfaceSnapshotResp snapshot = new InterfaceSnapshotResp(List.of(entry));

        assertThat(InterfaceSnapshotMatcher.match(snapshot, SERVICE, METHOD, PATH, IP_IN))
            .isEqualTo(Decision.FALLBACK);
    }

    @Test
    void shouldFallback_whenConditionalEntryHasBlankRules() {
        ApiPermissionEntry entry = new ApiPermissionEntry(
            SERVICE, METHOD, PATH, true, 1L, "   ", false);
        InterfaceSnapshotResp snapshot = new InterfaceSnapshotResp(List.of(entry));

        assertThat(InterfaceSnapshotMatcher.match(snapshot, SERVICE, METHOD, PATH, IP_IN))
            .isEqualTo(Decision.FALLBACK);
    }

    // ===== OR 合并语义（P1-② 修复） =====

    @Test
    void shouldAllow_whenUnconditionalEntryOverridesFailingConditional() {
        // 关键：同 API 两条授权——含条件评失败，但无条件兜底
        ApiPermissionEntry conditional = new ApiPermissionEntry(
            SERVICE, METHOD, PATH, true, 1L, RULES_IP_WHITELIST_10, false);
        ApiPermissionEntry unconditional = new ApiPermissionEntry(
            SERVICE, METHOD, PATH, false, null, null, false);
        InterfaceSnapshotResp snapshot = new InterfaceSnapshotResp(List.of(conditional, unconditional));

        assertThat(InterfaceSnapshotMatcher.match(snapshot, SERVICE, METHOD, PATH, IP_OUT))
            .isEqualTo(Decision.ALLOW);
    }

    @Test
    void shouldAllow_whenAnyConditionalEvalPasses() {
        // 同 API 两条含条件授权：第一条评失败，第二条评通过
        ApiPermissionEntry conditional1 = new ApiPermissionEntry(
            SERVICE, METHOD, PATH, true, 1L,
            "{\"logic\":\"AND\",\"items\":[{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"172.16.0.0/12\"]}}]}",
            false);
        ApiPermissionEntry conditional2 = new ApiPermissionEntry(
            SERVICE, METHOD, PATH, true, 2L, RULES_IP_WHITELIST_10, false);
        InterfaceSnapshotResp snapshot = new InterfaceSnapshotResp(List.of(conditional1, conditional2));

        assertThat(InterfaceSnapshotMatcher.match(snapshot, SERVICE, METHOD, PATH, IP_IN))
            .isEqualTo(Decision.ALLOW);
    }

    @Test
    void shouldFallback_whenAllLocalEvalFailButOneEntryMissingRules() {
        // 一条内联评失败，一条未下发 rules → 没有 ALLOW 命中，需要 fallback 兜底
        ApiPermissionEntry localFail = new ApiPermissionEntry(
            SERVICE, METHOD, PATH, true, 1L, RULES_IP_WHITELIST_10, false);
        ApiPermissionEntry needFallback = new ApiPermissionEntry(
            SERVICE, METHOD, PATH, true, 2L, null, false);
        InterfaceSnapshotResp snapshot = new InterfaceSnapshotResp(List.of(localFail, needFallback));

        assertThat(InterfaceSnapshotMatcher.match(snapshot, SERVICE, METHOD, PATH, IP_OUT))
            .isEqualTo(Decision.FALLBACK);
    }

    // ===== 异常 JSON 防御 =====

    @Test
    void shouldNotAllow_whenConditionalEntryHasMalformedJson() {
        ApiPermissionEntry entry = new ApiPermissionEntry(
            SERVICE, METHOD, PATH, true, 1L, "{ not valid json", false);
        InterfaceSnapshotResp snapshot = new InterfaceSnapshotResp(List.of(entry));

        // 解析失败 → 该 entry 不通过 ALLOW；无其他 entry → DENY
        assertThat(InterfaceSnapshotMatcher.match(snapshot, SERVICE, METHOD, PATH, IP_IN))
            .isEqualTo(Decision.DENY);
    }

    @Test
    void shouldEvalOrLogic() {
        // OR 逻辑：任一 item 命中即通过
        String orRules = "{\"logic\":\"OR\",\"items\":["
            + "{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"172.16.0.0/12\"]}},"
            + "{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"10.0.0.0/8\"]}}"
            + "]}";
        ApiPermissionEntry entry = new ApiPermissionEntry(
            SERVICE, METHOD, PATH, true, 1L, orRules, false);
        InterfaceSnapshotResp snapshot = new InterfaceSnapshotResp(List.of(entry));

        // IP_IN=10.0.0.5 命中第二条 → ALLOW
        assertThat(InterfaceSnapshotMatcher.match(snapshot, SERVICE, METHOD, PATH, IP_IN))
            .isEqualTo(Decision.ALLOW);
    }
}
