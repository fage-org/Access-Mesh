package cn.ac.fage.accessmesh.gateway.service;

import cn.ac.fage.accessmesh.gateway.service.InterfaceAdmissionMatcher.Decision;
import cn.ac.fage.accessmesh.perm.common.dto.resp.AdmissionRequirement;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceAdmissionSnapshotResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceAdmissionSnapshotResp.OperationCandidateEntry;
import cn.ac.fage.accessmesh.perm.common.dto.resp.InterfaceAdmissionSnapshotResp.RouteEntry;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 操作准入快照本地匹配器测试（T-ACCESS-059，契约总册 §25.2 网关本地判定序）。
 * <p>
 * 覆盖 N11（条件不可下发且无其他通过分支→回源）、N12（判定序与分支语义）、
 * N14（多匹配异要求阻断）、N15（同要求去重/无注册拒绝且 ALL 不放行未注册）、
 * N22（schema/模式/时效校验失败→配置故障）。
 * </p>
 */
class InterfaceAdmissionMatcherTest {

    private static final String SERVICE = "example-service";
    private static final AdmissionRequirement REPORT_VIEW = new AdmissionRequirement("REPORT", "VIEW");
    private static final AdmissionRequirement REPORT_EXPORT = new AdmissionRequirement("REPORT", "EXPORT");

    private final LocalDateTime now = LocalDateTime.now();

    private InterfaceAdmissionSnapshotResp snapshot(List<RouteEntry> routes,
                                                    List<OperationCandidateEntry> candidates) {
        return new InterfaceAdmissionSnapshotResp(1, 1L,
            new InterfaceAdmissionSnapshotResp.Subject("USER", "10"), SERVICE,
            now.minusSeconds(1), now.plusSeconds(60), 7L, routes, candidates,
            "OPERATION_ADMISSION", true);
    }

    private static RouteEntry route(String method, String pattern, AdmissionRequirement requirement) {
        return new RouteEntry(method, pattern, requirement);
    }

    private static OperationCandidateEntry unconditional(String type, String op, String kind) {
        return new OperationCandidateEntry(type, op, null, true, kind, null);
    }

    private static OperationCandidateEntry conditional(String type, String op, Long conditionId,
                                                       boolean evaluable, String rules) {
        return new OperationCandidateEntry(type, op, conditionId, evaluable, "INSTANCE", rules);
    }

    @Nested
    class ValidationSequence {

        @Test
        void nullSnapshot_isConfigFault() {
            assertThat(InterfaceAdmissionMatcher.match(null, SERVICE, "GET", "/api/test", null, now))
                .isEqualTo(Decision.CONFIG_FAULT);
        }

        @Test
        void unknownSchemaVersion_isConfigFault_n22() {
            InterfaceAdmissionSnapshotResp snapshot = new InterfaceAdmissionSnapshotResp(99, 1L, null, SERVICE,
                now, now.plusSeconds(60), 0L,
                List.of(route("GET", "/api/test", REPORT_VIEW)),
                List.of(unconditional("REPORT", "VIEW", "ALL")),
                "OPERATION_ADMISSION", true);
            assertThat(InterfaceAdmissionMatcher.match(snapshot, SERVICE, "GET", "/api/test", null, now))
                .isEqualTo(Decision.CONFIG_FAULT);
        }

        @Test
        void expiredSnapshot_isConfigFault_notStaleAllow() {
            InterfaceAdmissionSnapshotResp snapshot = new InterfaceAdmissionSnapshotResp(1, 1L, null, SERVICE,
                now.minusSeconds(120), now.minusSeconds(60), 0L,
                List.of(route("GET", "/api/test", REPORT_VIEW)),
                List.of(unconditional("REPORT", "VIEW", "ALL")),
                "OPERATION_ADMISSION", true);
            assertThat(InterfaceAdmissionMatcher.match(snapshot, SERVICE, "GET", "/api/test", null, now))
                .isEqualTo(Decision.CONFIG_FAULT);
        }

        @Test
        void wrongAuthorizationStage_isConfigFault() {
            InterfaceAdmissionSnapshotResp snapshot = new InterfaceAdmissionSnapshotResp(1, 1L, null, SERVICE,
                now, now.plusSeconds(60), 0L,
                List.of(route("GET", "/api/test", REPORT_VIEW)),
                List.of(unconditional("REPORT", "VIEW", "ALL")),
                "LEGACY_API", true);
            assertThat(InterfaceAdmissionMatcher.match(snapshot, SERVICE, "GET", "/api/test", null, now))
                .isEqualTo(Decision.CONFIG_FAULT);
        }

        @Test
        void serviceCodeMismatch_isConfigFault() {
            InterfaceAdmissionSnapshotResp snapshot =
                snapshot(List.of(route("GET", "/api/test", REPORT_VIEW)),
                    List.of(unconditional("REPORT", "VIEW", "ALL")));
            assertThat(InterfaceAdmissionMatcher.match(snapshot, "other-service", "GET", "/api/test", null, now))
                .isEqualTo(Decision.CONFIG_FAULT);
        }
    }

    @Nested
    class RouteMatching {

        @Test
        void unregisteredPath_denied_allDoesNotBypass_n15() {
            InterfaceAdmissionSnapshotResp snapshot =
                snapshot(List.of(route("GET", "/api/test", REPORT_VIEW)),
                    List.of(unconditional("REPORT", "VIEW", "ALL")));
            // 未注册路径：即使主体有 ALL 候选也拒绝
            assertThat(InterfaceAdmissionMatcher.match(snapshot, SERVICE, "GET", "/api/other", null, now))
                .isEqualTo(Decision.DENY);
        }

        @Test
        void methodMismatch_denied() {
            InterfaceAdmissionSnapshotResp snapshot =
                snapshot(List.of(route("GET", "/api/test", REPORT_VIEW)),
                    List.of(unconditional("REPORT", "VIEW", "ALL")));
            assertThat(InterfaceAdmissionMatcher.match(snapshot, SERVICE, "POST", "/api/test", null, now))
                .isEqualTo(Decision.DENY);
        }

        @Test
        void wildcardPattern_matches() {
            InterfaceAdmissionSnapshotResp snapshot =
                snapshot(List.of(route("GET", "/api/reports/**", REPORT_VIEW)),
                    List.of(unconditional("REPORT", "VIEW", "ALL")));
            assertThat(InterfaceAdmissionMatcher.match(snapshot, SERVICE, "GET", "/api/reports/123", null, now))
                .isEqualTo(Decision.ALLOW);
        }

        @Test
        void sameRequirementDeduped_multiMatch_n15() {
            InterfaceAdmissionSnapshotResp snapshot =
                snapshot(List.of(route("GET", "/api/reports/**", REPORT_VIEW),
                        route("GET", "/api/reports/export", REPORT_VIEW)),
                    List.of(unconditional("REPORT", "VIEW", "ALL")));
            // 多匹配同要求：去重为一个要求，正常判定
            assertThat(InterfaceAdmissionMatcher.match(snapshot, SERVICE, "GET", "/api/reports/export", null, now))
                .isEqualTo(Decision.ALLOW);
        }

        @Test
        void ambiguousRequirement_configFault_n14() {
            InterfaceAdmissionSnapshotResp snapshot =
                snapshot(List.of(route("GET", "/api/reports/**", REPORT_VIEW),
                        route("GET", "/api/reports/export", REPORT_EXPORT)),
                    List.of(unconditional("REPORT", "VIEW", "ALL"),
                        unconditional("REPORT", "EXPORT", "ALL")));
            // 多匹配异要求：配置故障阻断——不能因用户只有 VIEW 就漏掉 EXPORT 规则挑较弱者
            assertThat(InterfaceAdmissionMatcher.match(snapshot, SERVICE, "GET", "/api/reports/export", null, now))
                .isEqualTo(Decision.CONFIG_FAULT);
        }
    }

    @Nested
    class CandidateBranches {

        @Test
        void unconditionalCandidate_allows() {
            InterfaceAdmissionSnapshotResp snapshot =
                snapshot(List.of(route("GET", "/api/test", REPORT_VIEW)),
                    List.of(unconditional("REPORT", "VIEW", "ALL")));
            assertThat(InterfaceAdmissionMatcher.match(snapshot, SERVICE, "GET", "/api/test", null, now))
                .isEqualTo(Decision.ALLOW);
        }

        @Test
        void otherRequirementCandidates_ignored() {
            InterfaceAdmissionSnapshotResp snapshot =
                snapshot(List.of(route("GET", "/api/test", REPORT_VIEW),
                        route("POST", "/api/export", REPORT_EXPORT)),
                    List.of(unconditional("REPORT", "VIEW", "ALL")));
            // 要求 EXPORT 的路由未命中；VIEW 候选不串用
            assertThat(InterfaceAdmissionMatcher.match(snapshot, SERVICE, "POST", "/api/export", null, now))
                .isEqualTo(Decision.DENY);
        }

        @Test
        void passingConditionalBranch_allows_ipWhitelist() {
            InterfaceAdmissionSnapshotResp snapshot =
                snapshot(List.of(route("GET", "/api/test", REPORT_VIEW)),
                    List.of(conditional("REPORT", "VIEW", 5L, true,
                        "{\"logic\":\"AND\",\"items\":[{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"10.0.0.0/8\"]}}]}")));
            assertThat(InterfaceAdmissionMatcher.match(snapshot, SERVICE, "GET", "/api/test", "10.1.2.3", now))
                .isEqualTo(Decision.ALLOW);
        }

        @Test
        void failingConditionalBranch_alone_denies() {
            InterfaceAdmissionSnapshotResp snapshot =
                snapshot(List.of(route("GET", "/api/test", REPORT_VIEW)),
                    List.of(conditional("REPORT", "VIEW", 5L, true,
                        "{\"logic\":\"AND\",\"items\":[{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"10.0.0.0/8\"]}}]}")));
            assertThat(InterfaceAdmissionMatcher.match(snapshot, SERVICE, "GET", "/api/test", "192.168.1.1", now))
                .isEqualTo(Decision.DENY);
        }

        @Test
        void notLocallyEvaluableCandidate_fallsBack_n11() {
            InterfaceAdmissionSnapshotResp snapshot =
                snapshot(List.of(route("GET", "/api/test", REPORT_VIEW)),
                    List.of(conditional("REPORT", "VIEW", 5L, false, null)));
            // 条件不可下发且无其他通过分支 → 回源在线判定（不因缺规则变成无条件放行/拒绝）
            assertThat(InterfaceAdmissionMatcher.match(snapshot, SERVICE, "GET", "/api/test", "10.1.2.3", now))
                .isEqualTo(Decision.FALLBACK);
        }

        @Test
        void inlinedRulesMissing_whenEvaluable_fallsBack() {
            InterfaceAdmissionSnapshotResp snapshot =
                snapshot(List.of(route("GET", "/api/test", REPORT_VIEW)),
                    List.of(new OperationCandidateEntry("REPORT", "VIEW", 5L, true, "INSTANCE", null)));
            // gatewayEvaluable=true 但 conditionRules 缺失＝不可用分支（N11 同形态）
            assertThat(InterfaceAdmissionMatcher.match(snapshot, SERVICE, "GET", "/api/test", "10.1.2.3", now))
                .isEqualTo(Decision.FALLBACK);
        }

        @Test
        void unconditionalPlusFailingConditional_orSemanticsAllows() {
            InterfaceAdmissionSnapshotResp snapshot =
                snapshot(List.of(route("GET", "/api/test", REPORT_VIEW)),
                    List.of(
                        conditional("REPORT", "VIEW", 5L, true,
                            "{\"logic\":\"AND\",\"items\":[{\"type\":\"IP_WHITELIST\",\"params\":{\"cidrs\":[\"10.0.0.0/8\"]}}]}"),
                        unconditional("REPORT", "VIEW", "INSTANCE")));
            // 一条失败条件不能覆盖另一条无条件来源（OR 分支独立保留）
            assertThat(InterfaceAdmissionMatcher.match(snapshot, SERVICE, "GET", "/api/test", "192.168.1.1", now))
                .isEqualTo(Decision.ALLOW);
        }

        @Test
        void contextDeferredUnconditional_fallsBack_notTreatedAsMain() {
            InterfaceAdmissionSnapshotResp snapshot =
                snapshot(List.of(route("GET", "/api/test", REPORT_VIEW)),
                    List.of(unconditional("REPORT", "VIEW", "CONTEXT_DEFERRED")));
            // 仅上下文子行候选：不伪装成主授权（父运行时判定归在线/业务）
            assertThat(InterfaceAdmissionMatcher.match(snapshot, SERVICE, "GET", "/api/test", null, now))
                .isEqualTo(Decision.FALLBACK);
        }

        @Test
        void noCandidates_denied() {
            InterfaceAdmissionSnapshotResp snapshot =
                snapshot(List.of(route("GET", "/api/test", REPORT_VIEW)), List.of());
            assertThat(InterfaceAdmissionMatcher.match(snapshot, SERVICE, "GET", "/api/test", null, now))
                .isEqualTo(Decision.DENY);
        }

        @Test
        void malformedRules_failClose_toFallbackOrDeny() {
            InterfaceAdmissionSnapshotResp malformed =
                snapshot(List.of(route("GET", "/api/test", REPORT_VIEW)),
                    List.of(conditional("REPORT", "VIEW", 5L, true, "not-json")));
            // 解析失败＝不可用分支：无其他通过分支 → 回源（不转为无条件）
            assertThat(InterfaceAdmissionMatcher.match(malformed, SERVICE, "GET", "/api/test", null, now))
                .isEqualTo(Decision.FALLBACK);
        }
    }
}
