package cn.ac.fage.accessmesh.access.engine.query;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 新契约模型层契约单测（T-PERM-082，C07/C08＋受控工厂＋三结果不互冒充＋JSON 值域）。
 */
class QueryContractModelsTest {

    private static final TypeOperation REPORT_VIEW = new TypeOperation("REPORT", "VIEW");

    @Nested
    class DefensiveCopy {

        @Test
        void should_keepItemsUnchanged_whenSourceListMutatedAfterConstruction() {
            List<QueryItem> source = new ArrayList<>();
            source.add(QueryItem.decision("k1",
                new TargetSet(List.of(new TargetClause(REPORT_VIEW, new ByCode("REPORT_A", null, null))),
                    Inheritance.SELF, TypeFallback.ALLOW, null),
                OutputSpec.minimalWithMatchIds()));
            QueryRequest request = new QueryRequest(1L, new Roles(Set.of()), CallerContext.of(null),
                ReadOptions.defaults(), source);

            source.clear();
            source.add(QueryItem.decision("other",
                new TypeLevel(List.of(REPORT_VIEW)), OutputSpec.minimal()));

            assertThat(request.items()).hasSize(1);
            assertThat(request.items().get(0).key()).isEqualTo("k1");
            assertThatThrownBy(() -> request.items().add(source.get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void should_keepNestedContextUnchanged_whenSourceMapsMutatedAfterConstruction() {
            List<Object> nestedList = new ArrayList<>();
            nestedList.add("a");
            Map<String, Object> nestedMap = new LinkedHashMap<>();
            nestedMap.put("dept", "dev");
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("list", nestedList);
            source.put("map", nestedMap);

            CallerContext context = new CallerContext("10.0.0.1", source);

            nestedList.add("b");
            nestedMap.put("extra", "x");
            source.put("list", List.of("replaced"));

            assertThat((List<Object>) context.attributes().get("list")).containsExactly("a");
            assertThat((Map<String, Object>) context.attributes().get("map")).containsOnlyKeys("dept");
            assertThatThrownBy(() -> context.attributes().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void should_copySelectionCollections_whenConstructed() {
            List<TypeOperation> requirements = new ArrayList<>(List.of(REPORT_VIEW));
            TypeLevel typeLevel = new TypeLevel(requirements);
            Set<String> operationCodes = new java.util.HashSet<>(Set.of("VIEW"));
            ParentRequirement parent = new ParentRequirement("FOLDER",
                new ByCode("F1", null, null), operationCodes);

            requirements.clear();
            operationCodes.clear();

            assertThat(typeLevel.requirements()).containsExactly(REPORT_VIEW);
            assertThat(parent.operationCodes()).containsExactly("VIEW");
            assertThatThrownBy(() -> typeLevel.requirements().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void should_copyResultCollections_whenConstructed() {
            java.util.HashSet<Long> roleSource = new java.util.HashSet<>(Set.of(1L, 2L));
            Roles roles = new Roles(roleSource);
            OutputSpec spec = new OutputSpec(FactDetail.KEPT, true, false, false, false,
                new java.util.HashSet<>(Set.of("REPORT:EXPORT")), false);
            ResultDetails details = new ResultDetails(Set.of(ResultDetails.DetailSection.MATCHED_IDS),
                new ArrayList<>(List.of(1L)), new ArrayList<>(List.of(2L)));
            EvaluationCoverage coverage = new EvaluationCoverage(
                EvaluationCoverage.SubjectResolution.EXPLICIT_ROLES,
                EvaluationCoverage.ConditionCoverage.NO_CANDIDATE,
                EvaluationCoverage.MutexCoverage.NO_CANDIDATE,
                EvaluationCoverage.ParentCheckCoverage.NOT_REQUIRED,
                new java.util.HashSet<>(Set.of(Stage.TYPE_GRANT)),
                new HashMap<>(Map.of(Stage.INSTANCE, EvaluationCoverage.SkipReason.NO_ROLE)),
                false, EvaluationCoverage.AuthorizationStage.FINAL_DECISION);
            QueryResult result = new QueryResult("exec-1", LocalDateTime.of(2026, 9, 25, 10, 0),
                new ArrayList<>(List.of(DecisionResult.deny("k1", DecisionResult.Reason.NO_ROLE, coverage, details))));

            roleSource.add(99L);
            assertThat(roles.roleIds()).containsExactlyInAnyOrder(1L, 2L);
            assertThat(spec.extraOperationKeys()).containsExactly("REPORT:EXPORT");
            assertThat(details.matchedRoleIds()).containsExactly(1L);
            assertThat(coverage.completedStages()).containsExactly(Stage.TYPE_GRANT);
            assertThat(result.orderedResults()).hasSize(1);
            assertThatThrownBy(() -> result.orderedResults().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void should_normalizeNullCollectionsToEmpty_whenConstructedWithNull() {
            assertThat(new QueryRequest(1L, new Roles(Set.of()), CallerContext.of(null),
                ReadOptions.defaults(), null).items()).isEmpty();
            assertThat(new CallerContext(null, null).attributes()).isEmpty();
            assertThat(new OutputSpec(FactDetail.NONE, false, false, false, false, null, false)
                .extraOperationKeys()).isEmpty();
            assertThat(new ReadOptions(null).listGrantRead()).isEqualTo(ListGrantRead.ROLE_SNAPSHOT);
            assertThat(new ResultDetails(null, null, null).loadedSections()).isEmpty();
        }

        @Test
        void should_failFastWithNpe_whenNullCollectionPassedToContractRecord() {
            assertThatThrownBy(() -> new Roles(null))
                .as("契约 record 的 null 集合=编程错误（构造期 NPE），与空集合=结构错误分界")
                .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new TypeLevel(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new TargetSet(null, Inheritance.SELF, TypeFallback.ALLOW, null))
                .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new ParentRequirement("FOLDER",
                new ByCode("F1", null, null), null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new Roles(new java.util.HashSet<>(java.util.Arrays.asList(1L, null))))
                .as("null 元素同样在构造期 NPE")
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class ReservedKeysAndJsonValueDomain {

        @Test
        void should_rejectConstruction_whenAttributesForgeReservedKeys() {
            for (String reserved : List.of("clientIp", "evaluatedAt", "timestamp")) {
                Map<String, Object> source = new LinkedHashMap<>();
                source.put(reserved, "forged");
                assertThatThrownBy(() -> new CallerContext("10.0.0.1", source))
                    .as("保留键 %s 不可经 attributes 覆盖正式环境（C08）", reserved)
                    .isInstanceOf(QueryValidationException.class)
                    .hasMessageContaining(reserved);
            }
        }

        @Test
        void should_acceptNestedReservedKeyName_whenNotAtTopLevel() {
            Map<String, Object> nested = new LinkedHashMap<>();
            nested.put("evaluatedAt", "caller-namespace-value");
            CallerContext context = new CallerContext(null, Map.of("custom", nested));

            assertThat((Map<String, Object>) context.attributes().get("custom")).containsKey("evaluatedAt");
        }

        @Test
        void should_rejectConstruction_whenNestedMapKeyNotString() {
            Map<String, Object> nested = new LinkedHashMap<>();
            nested.put(null, "v");
            assertThatThrownBy(() -> new CallerContext(null, Map.of("custom", nested)))
                .as("顶层 null 键静默过滤；嵌套非 String 键（含 null）拒绝——两套边界各安其位")
                .isInstanceOf(QueryValidationException.class)
                .hasMessageContaining("String");
        }

        @Test
        void should_rejectConstruction_whenAttributesContainNonJsonValue() {
            Map<String, Object> withObject = Map.of("bad", new Object());
            assertThatThrownBy(() -> new CallerContext(null, withObject))
                .isInstanceOf(QueryValidationException.class)
                .hasMessageContaining("JSON");

            Map<String, Object> withDate = Map.of("bad", LocalDateTime.of(2026, 9, 25, 0, 0));
            assertThatThrownBy(() -> new CallerContext(null, withDate))
                .isInstanceOf(QueryValidationException.class);

            Map<String, Object> withEnum = Map.of("bad", FactDetail.KEPT);
            assertThatThrownBy(() -> new CallerContext(null, withEnum))
                .isInstanceOf(QueryValidationException.class);

            Map<String, Object> withNan = Map.of("bad", Double.NaN);
            assertThatThrownBy(() -> new CallerContext(null, withNan))
                .isInstanceOf(QueryValidationException.class)
                .hasMessageContaining("有限值");

            Map<String, Object> withInfinity = Map.of("bad", Double.POSITIVE_INFINITY);
            assertThatThrownBy(() -> new CallerContext(null, withInfinity))
                .isInstanceOf(QueryValidationException.class);

            Map<String, Object> withMutableNumber = Map.of("bad", new java.util.concurrent.atomic.AtomicInteger(1));
            assertThatThrownBy(() -> new CallerContext(null, withMutableNumber))
                .as("可变 Number 实现不是 JSON 值——白名单外拒绝（防御性复制不放大可变对象）")
                .isInstanceOf(QueryValidationException.class)
                .hasMessageContaining("JSON");

            Map<String, Object> withCustomNumber = Map.of("bad", new java.util.concurrent.atomic.AtomicLong(1));
            assertThatThrownBy(() -> new CallerContext(null, withCustomNumber))
                .isInstanceOf(QueryValidationException.class);
        }

        @Test
        void should_acceptJsonValueShapes_whenWellFormed() {
            Map<String, Object> nestedMap = Map.of("level", 2, "flag", true);
            Map<String, Object> source = Map.of(
                "str", "v", "int", 1, "long", 2L, "bool", false,
                "short", (short) 3, "bigInt", java.math.BigInteger.TWO,
                "bigDec", new java.math.BigDecimal("1.5"),
                "list", List.of("a", 3, false, List.of("deep")),
                "map", nestedMap);

            CallerContext context = new CallerContext(null, source);

            assertThat(context.attributes()).containsKeys("str", "int", "long", "bool", "short", "bigInt", "bigDec", "list", "map");
            assertThat((List<Object>) context.attributes().get("list")).hasSize(4);
        }

        @Test
        void should_rejectConstruction_whenAttributesContainCycles() {
            List<Object> selfList = new ArrayList<>();
            selfList.add(selfList);
            assertThatThrownBy(() -> new CallerContext(null, Map.of("list", selfList)))
                .isInstanceOf(QueryValidationException.class)
                .hasMessageContaining("循环");

            Map<String, Object> selfMap = new LinkedHashMap<>();
            selfMap.put("self", selfMap);
            assertThatThrownBy(() -> new CallerContext(null, selfMap))
                .isInstanceOf(QueryValidationException.class)
                .hasMessageContaining("循环");

            Map<String, Object> outer = new LinkedHashMap<>();
            List<Object> inner = new ArrayList<>();
            outer.put("inner", inner);
            inner.add(outer);
            assertThatThrownBy(() -> new CallerContext(null, outer))
                .isInstanceOf(QueryValidationException.class)
                .hasMessageContaining("循环");
        }

        @Test
        void should_filterNullKeysAndValues_whenCopyingAttributes() {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("valid", "v");
            source.put("nullValue", null);
            source.put(null, "nullKey");
            source.put("nullElements", new ArrayList<>(java.util.Arrays.asList("a", null, "b")));

            CallerContext context = new CallerContext(null, source);

            assertThat(context.attributes()).containsOnlyKeys("valid", "nullElements");
            assertThat((List<Object>) context.attributes().get("nullElements")).containsExactly("a", "b");
        }
    }

    @Nested
    class ControlledFactories {

        @Test
        void should_pinEvaluationAndForm_whenDecisionFactoryUsed() {
            QueryItem item = QueryItem.decision("k1",
                new TypeLevel(List.of(REPORT_VIEW)), OutputSpec.minimalWithMatchIds());

            assertThat(item.evaluation()).isEqualTo(Evaluation.full());
            assertThat(item.resultForm()).isEqualTo(ResultForm.DECISION);
        }

        @Test
        void should_pinAdmissionPairs_whenAdmissionFactoriesUsed() {
            QueryItem online = QueryItem.admission("k1", REPORT_VIEW, OutputSpec.minimalWithMatchIds());
            QueryItem snapshot = QueryItem.admissionFacts("k2", REPORT_VIEW, OutputSpec.kept());

            assertThat(online.evaluation()).isEqualTo(Evaluation.evaluateSkip());
            assertThat(online.resultForm()).isEqualTo(ResultForm.ADMISSION);
            assertThat(online.selection()).isInstanceOf(OperationAdmission.class);
            assertThat(snapshot.evaluation()).isEqualTo(Evaluation.preserveSkip());
            assertThat(snapshot.resultForm()).isEqualTo(ResultForm.FACTS);
        }

        @Test
        void should_rejectFactoryMisuse_whenSelectionIncompatibleWithForm() {
            OutputSpec output = OutputSpec.minimal();
            assertThatThrownBy(() -> QueryItem.decision("k1", new GrantList(null), output))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> QueryItem.decision("k1",
                new OperationAdmission(REPORT_VIEW), output))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> QueryItem.facts("k1",
                new OperationAdmission(REPORT_VIEW), Evaluation.preserveSkip(), OutputSpec.kept()))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class ResultImpersonationGuards {

        @Test
        void should_notExposeAllowed_whenGrantSetResult() {
            List<String> methodNames = java.util.Arrays.stream(GrantSetResult.class.getMethods())
                .map(java.lang.reflect.Method::getName).toList();
            assertThat(methodNames).doesNotContain("allowed", "isAllowed");
        }

        @Test
        void should_notExposeFinalAuthorizationBoolean_whenAdmissionResult() {
            List<String> methodNames = java.util.Arrays.stream(AdmissionResult.class.getMethods())
                .map(java.lang.reflect.Method::getName).toList();
            assertThat(methodNames).doesNotContain("allowed", "isAllowed");
        }

        @Test
        void should_alwaysRequireFinalCheck_whenAdmissionResultConstructed() {
            EvaluationCoverage coverage = noRoleCoverage();
            assertThat(new AdmissionResult("k1", AdmissionResult.Admission.MAY_ENTER, null, coverage, null)
                .finalCheckRequired()).isTrue();
            assertThat(AdmissionResult.deny("k2", AdmissionResult.Reason.NO_CANDIDATE, coverage, null)
                .finalCheckRequired()).isTrue();
        }

        @Test
        void should_enforceReasonPresenceContract_whenBuildingResults() {
            EvaluationCoverage coverage = noRoleCoverage();
            assertThat(DecisionResult.allow("k1", coverage, null).reason()).isNull();
            assertThatThrownBy(() -> new DecisionResult("k1", DecisionResult.Decision.ALLOW,
                DecisionResult.Reason.NO_PERMISSION, coverage, null))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new DecisionResult("k1", DecisionResult.Decision.DENY,
                null, coverage, null))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new AdmissionResult("k1", AdmissionResult.Admission.MAY_ENTER,
                AdmissionResult.Reason.NO_CANDIDATE, coverage, null))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new AdmissionResult("k1", AdmissionResult.Admission.DENY,
                null, coverage, null))
                .isInstanceOf(IllegalArgumentException.class);
        }

        private EvaluationCoverage noRoleCoverage() {
            return new EvaluationCoverage(EvaluationCoverage.SubjectResolution.EXPLICIT_ROLES,
                EvaluationCoverage.ConditionCoverage.NO_CANDIDATE,
                EvaluationCoverage.MutexCoverage.NO_CANDIDATE,
                EvaluationCoverage.ParentCheckCoverage.NOT_REQUIRED,
                Set.of(), Map.of(), false, EvaluationCoverage.AuthorizationStage.FINAL_DECISION);
        }
    }
}
