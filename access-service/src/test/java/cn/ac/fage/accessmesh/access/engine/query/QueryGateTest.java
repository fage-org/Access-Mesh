package cn.ac.fage.accessmesh.access.engine.query;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 判定面薄门面测试（T-PERM-089）：四方法 → 新 execute 的请求形状与结果投影；
 * 容器轨语义（fail-closed/继承/scopeAll 先行）由 characterization PgIT 经真实 Bean 锁钉。
 */
@ExtendWith(MockitoExtension.class)
class QueryGateTest {

    private static final long TENANT = 1L;
    private static final long SUBJECT = 501L;

    @Mock private QueryExecutionEngine engine;

    private QueryGate gate() {
        return new QueryGate(engine);
    }

    @Test
    void hasPermissionByCodeMustUseTypeLevelSelectionWhenCodeNull() {
        when(engine.execute(any(QueryRequest.class))).thenReturn(result(allowItem("gate")));

        assertThat(gate().hasPermissionByCode(TENANT, SUBJECT, "REPORT", null, "VIEW")).isTrue();

        QueryRequest request = captured();
        assertThat(request.tenantId()).isEqualTo(TENANT);
        assertThat(request.subject()).isEqualTo(new User(SUBJECT));
        assertThat(request.items()).hasSize(1);
        QueryItem item = request.items().get(0);
        assertThat(item.resultForm()).isEqualTo(ResultForm.DECISION);
        assertThat(item.evaluation()).isEqualTo(Evaluation.full());
        assertThat(item.selection()).isEqualTo(new TypeLevel(List.of(new TypeOperation("REPORT", "VIEW"))));
        assertThat(item.output()).isEqualTo(OutputSpec.minimal());
    }

    @Test
    void hasPermissionByCodeMustUseSingleClauseTargetSetWithJudgementInheritance() {
        when(engine.execute(any(QueryRequest.class)))
            .thenReturn(result(denyItem("gate", DecisionResult.Reason.NO_PERMISSION)));

        assertThat(gate().hasPermissionByCode(TENANT, SUBJECT, "REPORT", "r1", "VIEW")).isFalse();

        QueryRequest request = captured();
        QueryItem item = request.items().get(0);
        assertThat(item.selection()).isEqualTo(new TargetSet(
            List.of(new TargetClause(new TypeOperation("REPORT", "VIEW"), new ByCode("r1", null, null))),
            Inheritance.SELF_AND_ANCESTORS, TypeFallback.ALLOW, null));
    }

    @Test
    void blankCodeMustNormalizeToTypeLevelAndNotFailStructurally() {
        // 外评 P2（2026-09-27 claude+grok）：空白码不得进 ByCode（结构校验会整单拒绝→500）；
        // 归一类型级＝旧 INSTANCE 不可解析形态的可观测等价（门面无父上下文：scopeAll 放行/否则拒）
        when(engine.execute(any(QueryRequest.class))).thenReturn(result(allowItem("gate")));

        assertThat(gate().hasPermissionByCode(TENANT, SUBJECT, "SERVICE", " ", "VIEW")).isTrue();
        assertThat(captured().items().get(0).selection())
            .isEqualTo(new TypeLevel(List.of(new TypeOperation("SERVICE", "VIEW"))));
    }

    @Test
    void getDeniedMustJudgeBlankCodesAsDeniedIndividuallyWithoutSinkingTheBatch() {
        // 单个退化码不得拖垮同批其他目标（外评 P2）：空白码直接 fail-closed 拒绝，
        // 非空白码照常进引擎；引擎只见非空白目标
        when(engine.execute(any(QueryRequest.class))).thenReturn(result(
            allowItem("r1"), denyItem("r2", DecisionResult.Reason.NO_PERMISSION)));

        Set<String> denied = gate().getDeniedResourceCodes(TENANT, SUBJECT, "REPORT",
            new LinkedHashSet<>(List.of("r1", " ", "r2", "")), "VIEW");

        // 两种空白串（" " 与 ""）各按原键 fail-closed 拒绝、保持输入序；引擎只见非空白目标
        assertThat(denied).containsExactly(" ", "", "r2");
        assertThat(captured().items()).extracting(QueryItem::key).containsExactly("r1", "r2");
    }

    @Test
    void hasPermissionByEntityIdMustUseEntityIdClauseAndTypeLevelWhenNull() {
        when(engine.execute(any(QueryRequest.class))).thenReturn(result(allowItem("gate")));

        assertThat(gate().hasPermissionByEntityId(TENANT, SUBJECT, "RESOURCE", 200L, "MANAGE")).isTrue();
        assertThat(captured().items().get(0).selection()).isEqualTo(new TargetSet(
            List.of(new TargetClause(new TypeOperation("RESOURCE", "MANAGE"), new ByEntityId(200L))),
            Inheritance.SELF_AND_ANCESTORS, TypeFallback.ALLOW, null));

        assertThat(gate().hasPermissionByEntityId(TENANT, SUBJECT, "RESOURCE", null, "VIEW")).isTrue();
        assertThat(captured().items().get(0).selection())
            .isEqualTo(new TypeLevel(List.of(new TypeOperation("RESOURCE", "VIEW"))));
    }    @Test
    void getDeniedMustReturnEmptyWithoutEngineCallOnNullOrBlankInput() {
        assertThat(gate().getDeniedResourceCodes(TENANT, SUBJECT, "REPORT", null, "VIEW")).isEmpty();
        assertThat(gate().getDeniedResourceCodes(TENANT, SUBJECT, "REPORT", Set.of(), "VIEW")).isEmpty();
        assertThat(gate().getDeniedEntityIds(TENANT, SUBJECT, "RESOURCE", null, "MANAGE")).isEmpty();
        assertThat(gate().getDeniedEntityIds(TENANT, SUBJECT, "RESOURCE", Set.of(), "MANAGE")).isEmpty();
        verify(engine, never()).execute(any());
    }

    @Test
    void getDeniedResourceCodesMustProjectDenyItemsBackToInputKeysInOrder() {
        // 独立目标独立 item（一次 execute）：r1 允许、r2 拒、r3 拒；重复输入去重、原序回映射
        when(engine.execute(any(QueryRequest.class))).thenReturn(result(
            allowItem("r1"), denyItem("r2", DecisionResult.Reason.NO_PERMISSION),
            denyItem("r3", DecisionResult.Reason.CONDITION_NOT_MET_OR_CONFLICT)));

        Set<String> denied = gate().getDeniedResourceCodes(TENANT, SUBJECT, "REPORT",
            new LinkedHashSet<>(List.of("r1", "r2", "r3", "r2")), "VIEW");

        assertThat(denied).containsExactly("r2", "r3");
        QueryRequest request = captured();
        assertThat(request.items()).extracting(QueryItem::key)
            .containsExactly("r1", "r2", "r3");
        assertThat(request.items()).allSatisfy(item -> {
            assertThat(item.resultForm()).isEqualTo(ResultForm.DECISION);
            assertThat(item.selection()).isInstanceOf(TargetSet.class);
        });
    }

    @Test
    void getDeniedEntityIdsMustProjectDenyItemsBackToInputIds() {
        when(engine.execute(any(QueryRequest.class))).thenReturn(result(
            denyItem("10", DecisionResult.Reason.NO_PERMISSION), allowItem("20")));

        Set<Long> denied = gate().getDeniedEntityIds(TENANT, SUBJECT, "RESOURCE",
            new LinkedHashSet<>(List.of(10L, 20L)), "MANAGE");

        assertThat(denied).containsExactly(10L);
        QueryRequest request = captured();
        assertThat(request.items()).extracting(QueryItem::key).containsExactly("10", "20");
        assertThat(((TargetSet) request.items().get(0).selection()).clauses().get(0).resource())
            .isEqualTo(new ByEntityId(10L));
    }

    private QueryRequest captured() {
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(engine, org.mockito.Mockito.atLeastOnce()).execute(captor.capture());
        return captor.getValue();
    }

    private static QueryResult result(DecisionResult... decisions) {
        List<ItemResult> items = List.of(decisions);
        return new QueryResult("exec", LocalDateTime.now(), items);
    }
    private static DecisionResult allowItem(String key) {
        return DecisionResult.allow(key, coverage(), ResultDetails.empty());
    }

    private static DecisionResult denyItem(String key, DecisionResult.Reason reason) {
        return DecisionResult.deny(key, reason, coverage(), ResultDetails.empty());
    }

    private static EvaluationCoverage coverage() {
        return new EvaluationCoverage(EvaluationCoverage.SubjectResolution.USER_EFFECTIVE_WITH_MUTEX,
            EvaluationCoverage.ConditionCoverage.EVALUATED, EvaluationCoverage.MutexCoverage.EVALUATED,
            EvaluationCoverage.ParentCheckCoverage.NOT_REQUIRED, Set.of(), java.util.Map.of(),
            true, EvaluationCoverage.AuthorizationStage.FINAL_DECISION);
    }
}
