package cn.ac.fage.accessmesh.access.engine.query;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import cn.ac.fage.accessmesh.access.engine.query.EvaluationCoverage.AuthorizationStage;
import cn.ac.fage.accessmesh.access.engine.query.EvaluationCoverage.ConditionCoverage;
import cn.ac.fage.accessmesh.access.engine.query.EvaluationCoverage.MutexCoverage;
import cn.ac.fage.accessmesh.access.engine.query.EvaluationCoverage.ParentCheckCoverage;
import cn.ac.fage.accessmesh.access.engine.query.EvaluationCoverage.SkipReason;
import cn.ac.fage.accessmesh.access.engine.query.EvaluationCoverage.SubjectResolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 骨架执行器契约单测（T-PERM-082，C01/C04/C06/I07/R03＋未实现阶段 fail-closed）。
 */
class QueryExecutionEngineTest {

    private static final LocalDateTime FIXED_AT = LocalDateTime.of(2026, 9, 25, 2, 15, 30);
    private static final TypeOperation REPORT_VIEW = new TypeOperation("REPORT", "VIEW");

    private final QueryExecutionEngine engine = new QueryExecutionEngine(
        Clock.fixed(Instant.parse("2026-09-25T02:15:30Z"), ZoneOffset.UTC));

    private static QueryRequest request(Subject subject, QueryItem... items) {
        return new QueryRequest(1L, subject, CallerContext.of(null), ReadOptions.defaults(), List.of(items));
    }

    private static QueryItem decisionItem(String key) {
        return QueryItem.decision(key, new TargetSet(
            List.of(new TargetClause(REPORT_VIEW, new ByCode("REPORT_A", null, null))),
            Inheritance.SELF, TypeFallback.ALLOW, null), OutputSpec.minimalWithMatchIds());
    }

    @Test
    void should_returnEmptyResultWithServerClock_whenItemsEmpty() {
        QueryResult result = engine.execute(request(new Roles(Set.of())));

        assertThat(result.orderedResults()).as("空请求零权限 I/O 返回空结果（C01）").isEmpty();
        assertThat(result.evaluatedAt()).isEqualTo(FIXED_AT);
        assertThat(result.executionId()).isNotBlank();
    }

    @Nested
    class NoRoleTerminalByForm {

        @Test
        void should_returnDecisionNoRole_whenRolesSubjectEmpty() {
            QueryResult result = engine.execute(request(new Roles(Set.of()), decisionItem("k1")));

            assertThat(result.orderedResults()).hasSize(1);
            assertThat(result.orderedResults().get(0)).isInstanceOf(DecisionResult.class);
            DecisionResult decision = (DecisionResult) result.orderedResults().get(0);
            assertThat(decision.key()).isEqualTo("k1");
            assertThat(decision.outcome()).isEqualTo(DecisionResult.Decision.DENY);
            assertThat(decision.reason()).isEqualTo(DecisionResult.Reason.NO_ROLE);
            assertThat(decision.details().matchedRoleIds()).as("拒绝项公开命中集保持空").isEmpty();
            assertThat(decision.details().matchedPermissionIds()).isEmpty();
            assertThat(decision.coverage().subjectResolution()).isEqualTo(SubjectResolution.EXPLICIT_ROLES);
            assertThat(decision.coverage().authorizationStage()).isEqualTo(AuthorizationStage.FINAL_DECISION);
            assertThat(decision.coverage().requestedSelectionComplete()).isFalse();
            assertThat(decision.coverage().conditions()).isEqualTo(ConditionCoverage.NO_CANDIDATE);
            assertThat(decision.coverage().permissionMutex()).isEqualTo(MutexCoverage.NO_CANDIDATE);
            assertThat(decision.coverage().parentCheck()).isEqualTo(ParentCheckCoverage.NOT_REQUIRED);
            assertThat(decision.coverage().completedStages()).isEmpty();
            assertThat(decision.coverage().skippedStages())
                .containsEntry(Stage.TYPE_GRANT, SkipReason.NO_ROLE)
                .containsEntry(Stage.INSTANCE, SkipReason.NO_ROLE);
        }

        @Test
        void should_returnGrantSetNoRole_whenRolesSubjectEmptyAndFactsForm() {
            QueryResult result = engine.execute(request(new Roles(Set.of()),
                new QueryItem("k1", new TypeLevel(List.of(REPORT_VIEW)),
                    Evaluation.preserveSkip(), ResultForm.FACTS, OutputSpec.kept())));

            assertThat(result.orderedResults().get(0)).isInstanceOf(GrantSetResult.class);
            GrantSetResult grantSet = (GrantSetResult) result.orderedResults().get(0);
            assertThat(grantSet.collectionStatus()).isEqualTo(GrantSetResult.CollectionStatus.NO_ROLE);
            assertThat(grantSet.coverage().authorizationStage()).isEqualTo(AuthorizationStage.FACT_COLLECTION);
            assertThat(grantSet.coverage().skippedStages())
                .containsOnlyKeys(Stage.TYPE_GRANT)
                .containsValue(SkipReason.NO_ROLE);
        }

        @Test
        void should_returnAdmissionNoRole_whenRolesSubjectEmptyAndAdmissionForm() {
            QueryResult result = engine.execute(request(new Roles(Set.of()),
                QueryItem.admission("k1", REPORT_VIEW, OutputSpec.minimal())));

            assertThat(result.orderedResults().get(0)).isInstanceOf(AdmissionResult.class);
            AdmissionResult admission = (AdmissionResult) result.orderedResults().get(0);
            assertThat(admission.outcome()).isEqualTo(AdmissionResult.Admission.DENY);
            assertThat(admission.reason()).isEqualTo(AdmissionResult.Reason.NO_ROLE);
            assertThat(admission.finalCheckRequired()).isTrue();
            assertThat(admission.coverage().authorizationStage()).isEqualTo(AuthorizationStage.OPERATION_ADMISSION);
            assertThat(admission.coverage().skippedStages())
                .containsOnlyKeys(Stage.ADMISSION_CANDIDATES)
                .containsValue(SkipReason.NO_ROLE);
        }

        @Test
        void should_markParentNotTriggered_whenNoRoleShortCircuitsParentCarryingItem() {
            ParentRequirement parent = new ParentRequirement("FOLDER",
                new ByCode("F1", null, null), Set.of("VIEW"));
            QueryResult result = engine.execute(request(new Roles(Set.of()), new QueryItem("k1",
                new TargetSet(List.of(new TargetClause(REPORT_VIEW, new ByCode("REPORT_A", null, null))),
                    Inheritance.SELF, TypeFallback.ALLOW, parent),
                Evaluation.full(), ResultForm.DECISION, OutputSpec.minimal())));

            assertThat(((DecisionResult) result.orderedResults().get(0)).coverage().parentCheck())
                .isEqualTo(ParentCheckCoverage.NOT_TRIGGERED);
        }
    }

    @Test
    void should_returnTwoResultsInInputOrder_whenTwoKeysShareSameTarget() {
        QueryResult result = engine.execute(request(new Roles(Set.of()),
            decisionItem("key-a"), decisionItem("key-b")));

        assertThat(result.orderedResults()).as("两个不同 key 指向同目标仍各自返回（C06 顺序/key 半边；"
                + "完整判定版随 T-PERM-085 TYPE_GRANT/INSTANCE 阶段落地补锁）").hasSize(2);
        assertThat(result.orderedResults())
            .extracting(ItemResult::key)
            .containsExactly("key-a", "key-b");
        assertThat(result.orderedResults())
            .allSatisfy(item -> assertThat(((DecisionResult) item).reason()).isEqualTo(DecisionResult.Reason.NO_ROLE));
    }

    @Nested
    class RolesViewpoint {

        @Test
        void should_resolveExplicitRolesVerbatim_whenRolesSubjectWithMutexPair() {
            QueryExecutionEngine.ResolvedSubject resolved =
                QueryExecutionEngine.resolveSubject(new Roles(Set.of(1L, 2L)));

            assertThat(resolved.roles()).as("Roles({R1,R2}) 视角原样采用：不补加角色、不做 ROLE_MUTEX 过滤；"
                    + "互斥对事实完整返回的清单链断言随 T-PERM-086 GRANT_LIST 阶段落地补（R03 事实半边）")
                .containsExactlyInAnyOrder(1L, 2L);
            assertThat(resolved.resolution()).isEqualTo(SubjectResolution.EXPLICIT_ROLES);
        }

        @Test
        void should_failClosedInsteadOfNoRole_whenRolesSubjectNonEmpty() {
            assertThatThrownBy(() -> engine.execute(request(new Roles(Set.of(1L, 2L)), decisionItem("k1"))))
                .as("Roles 非空不落 NO_ROLE（不回退登录用户、不吞角色集）；判定阶段未落地时 fail-closed")
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    void should_createFreshRunStatePerExecute_whenEngineInvokedConsecutively() {
        QueryResult first = engine.execute(request(new Roles(Set.of())));
        QueryResult second = engine.execute(request(new Roles(Set.of())));

        assertThat(first.executionId())
                .as("同一事务先写后新 execute 创建新 RunState（I07 骨架锁：executionId 互异=运行态不复用；"
                    + "写后复读三态记忆版随 T-PERM-084/085 数据面落地补强）")
                .isNotEqualTo(second.executionId());
        assertThat(second.evaluatedAt()).isEqualTo(FIXED_AT);
    }

    @Test
    void should_notChangeExecutionInput_whenSourceCollectionsMutatedAfterConstruction() {
        List<QueryItem> source = new ArrayList<>(List.of(decisionItem("k1")));
        QueryRequest queryRequest = request(new Roles(Set.of()), source.toArray(new QueryItem[0]));
        QueryResult before = engine.execute(queryRequest);

        source.clear();
        source.add(decisionItem("other"));
        QueryResult after = engine.execute(queryRequest);

        assertThat(after.orderedResults()).hasSize(before.orderedResults().size());
        assertThat(after.orderedResults().get(0).key()).isEqualTo("k1");
    }

    @Nested
    class FailClosedUnimplementedRegion {

        @Test
        void should_throwUnsupported_whenUserSubjectResolutionNotImplemented() {
            assertThatThrownBy(() -> engine.execute(request(new User(1L), decisionItem("k1"))))
                .as("User 主体有效角色+ROLE_MUTEX 解析随 T-PERM-084/085 落地（骨架 fail-closed）")
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void should_rejectBeforeExecution_whenStructuralErrorOnValidSubjectPath() {
            assertThatThrownBy(() -> engine.execute(request(new User(1L), new QueryItem("k1",
                new TypeLevel(List.of(REPORT_VIEW)),
                new Evaluation(ConditionMode.PRESERVE, MutexMode.ENFORCE),
                ResultForm.DECISION, OutputSpec.minimal()))))
                .as("结构校验先于执行（C02：结构错误不得进入执行路径）")
                .isInstanceOf(QueryValidationException.class);
        }
    }
}
