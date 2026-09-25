package cn.ac.fage.accessmesh.access.engine.query;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 结构校验契约单测（T-PERM-082，C02/C03/C05＋合法组合矩阵全行＋首版混批约束）。
 */
class QueryRequestValidationTest {

    private static final TypeOperation REPORT_VIEW = new TypeOperation("REPORT", "VIEW");

    private static QueryRequest request(QueryItem... items) {
        return new QueryRequest(1L, new Roles(Set.of()), CallerContext.of(null),
            ReadOptions.defaults(), List.of(items));
    }

    private static TargetClause reportAClause() {
        return new TargetClause(REPORT_VIEW, new ByCode("REPORT_A", null, null));
    }

    private static QueryItem targetSetItem(String key, ResultForm form, Evaluation evaluation, OutputSpec output) {
        return new QueryItem(key, new TargetSet(List.of(reportAClause()), Inheritance.SELF,
            TypeFallback.ALLOW, null), evaluation, form, output);
    }

    private static QueryItem typeLevelItem(String key, ResultForm form, Evaluation evaluation, OutputSpec output) {
        return new QueryItem(key, new TypeLevel(List.of(REPORT_VIEW)), evaluation, form, output);
    }

    @Nested
    class LegalCombinationMatrix {

        @Test
        void should_pass_whenDecisionWithFullEvaluation() {
            assertThatCode(() -> QueryRequestValidator.validate(request(
                targetSetItem("k1", ResultForm.DECISION, Evaluation.full(), OutputSpec.minimalWithMatchIds()),
                typeLevelItem("k2", ResultForm.DECISION, Evaluation.full(), OutputSpec.minimal()))))
                .doesNotThrowAnyException();
        }

        @Test
        void should_rejectStructure_whenDecisionWithPreserveOrSkip() {
            for (Evaluation illegal : List.of(
                new Evaluation(ConditionMode.PRESERVE, MutexMode.ENFORCE),
                new Evaluation(ConditionMode.EVALUATE, MutexMode.SKIP),
                new Evaluation(ConditionMode.PRESERVE, MutexMode.SKIP))) {
                assertThatThrownBy(() -> QueryRequestValidator.validate(request(
                    targetSetItem("k1", ResultForm.DECISION, illegal, OutputSpec.minimal()))))
                    .as("DECISION+%s 必须执行前结构错误、不能返回 ALLOW（C02）", illegal)
                    .isInstanceOf(QueryValidationException.class)
                    .hasMessageContaining("EVALUATE+ENFORCE");
                assertThatThrownBy(() -> QueryRequestValidator.validate(request(
                    typeLevelItem("k1", ResultForm.DECISION, illegal, OutputSpec.minimal()))))
                    .isInstanceOf(QueryValidationException.class);
            }
        }

        @Test
        void should_pass_whenFactsWithAnyCombinationOnNormalSelections() {
            List<Evaluation> combos = List.of(
                new Evaluation(ConditionMode.EVALUATE, MutexMode.ENFORCE),
                new Evaluation(ConditionMode.EVALUATE, MutexMode.SKIP),
                new Evaluation(ConditionMode.PRESERVE, MutexMode.ENFORCE),
                new Evaluation(ConditionMode.PRESERVE, MutexMode.SKIP));
            for (Evaluation evaluation : combos) {
                assertThatCode(() -> QueryRequestValidator.validate(request(
                    typeLevelItem("k1", ResultForm.FACTS, evaluation, OutputSpec.kept()))))
                    .as("TYPE_LEVEL+FACTS+%s 合法", evaluation)
                    .doesNotThrowAnyException();
                assertThatCode(() -> QueryRequestValidator.validate(request(
                    targetSetItem("k1", ResultForm.FACTS, evaluation, OutputSpec.rawAndKept()))))
                    .as("TARGET_SET+FACTS+%s 合法", evaluation)
                    .doesNotThrowAnyException();
                assertThatCode(() -> QueryRequestValidator.validate(request(
                    QueryItem.grantListFacts("k1", null, evaluation, OutputSpec.kept()))))
                    .as("GRANT_LIST+FACTS+%s 合法", evaluation)
                    .doesNotThrowAnyException();
            }
        }

        @Test
        void should_pass_whenAdmissionFormsUsePinnedEvaluations() {
            assertThatCode(() -> QueryRequestValidator.validate(request(
                QueryItem.admission("k1", REPORT_VIEW, OutputSpec.minimal()))))
                .doesNotThrowAnyException();
            assertThatCode(() -> QueryRequestValidator.validate(request(
                QueryItem.admissionFacts("k1", REPORT_VIEW, OutputSpec.kept()))))
                .doesNotThrowAnyException();
        }

        @Test
        void should_rejectStructure_whenAdmissionFormUsesWrongEvaluation() {
            assertThatThrownBy(() -> QueryRequestValidator.validate(request(new QueryItem("k1",
                new OperationAdmission(REPORT_VIEW), Evaluation.full(), ResultForm.ADMISSION,
                OutputSpec.minimal()))))
                .as("OPERATION_ADMISSION+ADMISSION 固定 EVALUATE+SKIP")
                .isInstanceOf(QueryValidationException.class)
                .hasMessageContaining("EVALUATE+SKIP");
            assertThatThrownBy(() -> QueryRequestValidator.validate(request(new QueryItem("k1",
                new OperationAdmission(REPORT_VIEW), Evaluation.evaluateSkip(), ResultForm.FACTS,
                OutputSpec.kept()))))
                .as("OPERATION_ADMISSION+FACTS 固定 PRESERVE+SKIP")
                .isInstanceOf(QueryValidationException.class)
                .hasMessageContaining("PRESERVE+SKIP");
        }

        @Test
        void should_rejectStructure_whenSelectionIncompatibleWithForm() {
            assertThatThrownBy(() -> QueryRequestValidator.validate(request(new QueryItem("k1",
                new OperationAdmission(REPORT_VIEW), Evaluation.evaluateSkip(), ResultForm.DECISION,
                OutputSpec.minimal()))))
                .as("OPERATION_ADMISSION+DECISION 不能借新用途绕过普通鉴权")
                .isInstanceOf(QueryValidationException.class);
            assertThatThrownBy(() -> QueryRequestValidator.validate(request(new QueryItem("k1",
                new GrantList(null), Evaluation.full(), ResultForm.DECISION, OutputSpec.minimal()))))
                .as("GRANT_LIST+DECISION 结构错误（C03 首半边，canonical 构造经校验器拒绝）")
                .isInstanceOf(QueryValidationException.class)
                .hasMessageContaining("DECISION");
            assertThatThrownBy(() -> QueryRequestValidator.validate(request(
                targetSetItem("k1", ResultForm.ADMISSION, Evaluation.evaluateSkip(), OutputSpec.minimal()))))
                .as("TARGET_SET+ADMISSION 非法")
                .isInstanceOf(QueryValidationException.class);
            assertThatThrownBy(() -> QueryRequestValidator.validate(request(
                typeLevelItem("k1", ResultForm.ADMISSION, Evaluation.evaluateSkip(), OutputSpec.minimal()))))
                .isInstanceOf(QueryValidationException.class);
            assertThatThrownBy(() -> QueryRequestValidator.validate(request(new QueryItem("k1",
                new GrantList(null), Evaluation.full(), ResultForm.ADMISSION, OutputSpec.minimal()))))
                .as("GRANT_LIST+ADMISSION 非法")
                .isInstanceOf(QueryValidationException.class);
        }
    }

    @Nested
    class FirstVersionMixedBatch {

        @Test
        void should_rejectStructure_whenGrantListMixedOrDuplicated() {
            assertThatThrownBy(() -> QueryRequestValidator.validate(request(
                QueryItem.grantListFacts("k1", null, Evaluation.preserveSkip(), OutputSpec.kept()),
                targetSetItem("k2", ResultForm.DECISION, Evaluation.full(), OutputSpec.minimal()))))
                .as("GRANT_LIST 单项独占请求（C03 混批）")
                .isInstanceOf(QueryValidationException.class)
                .hasMessageContaining("独占");
            assertThatThrownBy(() -> QueryRequestValidator.validate(request(
                QueryItem.grantListFacts("k1", null, Evaluation.preserveSkip(), OutputSpec.kept()),
                QueryItem.grantListFacts("k2", null, Evaluation.preserveSkip(), OutputSpec.kept()))))
                .isInstanceOf(QueryValidationException.class);
        }

        @Test
        void should_rejectStructure_whenAdmissionMixedWithNormalTargets() {
            assertThatThrownBy(() -> QueryRequestValidator.validate(request(
                QueryItem.admission("k1", REPORT_VIEW, OutputSpec.minimal()),
                targetSetItem("k2", ResultForm.DECISION, Evaluation.full(), OutputSpec.minimal()))))
                .as("OPERATION_ADMISSION 首版不与普通目标混批")
                .isInstanceOf(QueryValidationException.class)
                .hasMessageContaining("混批");
        }

        @Test
        void should_rejectStructure_whenAdmissionBatchUsesMixedResultForms() {
            assertThatThrownBy(() -> QueryRequestValidator.validate(request(
                QueryItem.admission("k1", REPORT_VIEW, OutputSpec.minimal()),
                QueryItem.admissionFacts("k2", new TypeOperation("REPORT", "EXPORT"), OutputSpec.kept()))))
                .as("准入同批必须使用同一结果形式")
                .isInstanceOf(QueryValidationException.class)
                .hasMessageContaining("同一结果形式");
        }

        @Test
        void should_pass_whenAdmissionBatchSharesSameForm() {
            assertThatCode(() -> QueryRequestValidator.validate(request(
                QueryItem.admission("k1", REPORT_VIEW, OutputSpec.minimal()),
                QueryItem.admission("k2", new TypeOperation("REPORT", "EXPORT"), OutputSpec.minimal()))))
                .doesNotThrowAnyException();
        }

        @Test
        void should_pass_whenNormalTargetsMixed() {
            assertThatCode(() -> QueryRequestValidator.validate(request(
                targetSetItem("k1", ResultForm.DECISION, Evaluation.full(), OutputSpec.minimal()),
                typeLevelItem("k2", ResultForm.FACTS, Evaluation.preserveSkip(), OutputSpec.kept()))))
                .as("TYPE_LEVEL/TARGET_SET 可混批（含混合结果形式）")
                .doesNotThrowAnyException();
        }

        @Test
        void should_rejectStructure_whenMultipleDistinctParentRequirements() {
            ParentRequirement parentA = new ParentRequirement("FOLDER",
                new ByCode("F1", null, null), Set.of("VIEW"));
            ParentRequirement parentB = new ParentRequirement("FOLDER",
                new ByCode("F2", null, null), Set.of("VIEW"));
            assertThatThrownBy(() -> QueryRequestValidator.validate(request(
                new QueryItem("k1", new TargetSet(List.of(reportAClause()), Inheritance.SELF,
                    TypeFallback.ALLOW, parentA), Evaluation.full(), ResultForm.DECISION,
                    OutputSpec.minimal()),
                new QueryItem("k2", new TargetSet(List.of(reportAClause()), Inheritance.SELF,
                    TypeFallback.ALLOW, parentB), Evaluation.full(), ResultForm.DECISION,
                    OutputSpec.minimal()))))
                .as("存在父要求时至多一个不同父要求")
                .isInstanceOf(QueryValidationException.class)
                .hasMessageContaining("父要求");
        }

        @Test
        void should_pass_whenSameParentRequirementSharedByItems() {
            ParentRequirement parent = new ParentRequirement("FOLDER",
                new ByCode("F1", null, null), Set.of("VIEW"));
            assertThatCode(() -> QueryRequestValidator.validate(request(
                new QueryItem("k1", new TargetSet(List.of(reportAClause()), Inheritance.SELF,
                    TypeFallback.ALLOW, parent), Evaluation.full(), ResultForm.DECISION,
                    OutputSpec.minimal()),
                new QueryItem("k2", new TypeLevel(List.of(REPORT_VIEW)), Evaluation.full(),
                    ResultForm.DECISION, OutputSpec.minimal()))))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class StructuralFields {

        @Test
        void should_rejectStructure_whenRequestLevelFieldsInvalid() {
            assertThatThrownBy(() -> QueryRequestValidator.validate(new QueryRequest(0L,
                new Roles(Set.of()), CallerContext.of(null), ReadOptions.defaults(), List.of())))
                .hasMessageContaining("tenantId");
            assertThatThrownBy(() -> QueryRequestValidator.validate(new QueryRequest(1L,
                null, CallerContext.of(null), ReadOptions.defaults(), List.of())))
                .hasMessageContaining("subject");
            assertThatThrownBy(() -> QueryRequestValidator.validate(new QueryRequest(1L,
                new User(0L), CallerContext.of(null), ReadOptions.defaults(), List.of())))
                .hasMessageContaining("userId");
            assertThatThrownBy(() -> QueryRequestValidator.validate(new QueryRequest(1L,
                new Roles(Set.of(0L)), CallerContext.of(null), ReadOptions.defaults(), List.of())))
                .hasMessageContaining("roleIds");
            assertThatThrownBy(() -> QueryRequestValidator.validate(new QueryRequest(1L,
                new Roles(Set.of()), null, ReadOptions.defaults(), List.of())))
                .hasMessageContaining("context");
            assertThatThrownBy(() -> QueryRequestValidator.validate(new QueryRequest(1L,
                new Roles(Set.of()), CallerContext.of(null), null, List.of())))
                .hasMessageContaining("reads");
            assertThatThrownBy(() -> QueryRequestValidator.validate(null))
                .hasMessageContaining("请求为空");
        }

        @Test
        void should_rejectStructure_whenItemKeyNullOrBlankOrDuplicated() {
            assertThatThrownBy(() -> QueryRequestValidator.validate(request(
                typeLevelItem(" ", ResultForm.DECISION, Evaluation.full(), OutputSpec.minimal()))))
                .as("空 key 结构错误（C05）")
                .isInstanceOf(QueryValidationException.class)
                .hasMessageContaining("key");
            assertThatThrownBy(() -> QueryRequestValidator.validate(request(
                new QueryItem(null, new TypeLevel(List.of(REPORT_VIEW)), Evaluation.full(),
                    ResultForm.DECISION, OutputSpec.minimal()))))
                .isInstanceOf(QueryValidationException.class);
            assertThatThrownBy(() -> QueryRequestValidator.validate(request(
                typeLevelItem("k1", ResultForm.DECISION, Evaluation.full(), OutputSpec.minimal()),
                typeLevelItem("k1", ResultForm.DECISION, Evaluation.full(), OutputSpec.minimal()))))
                .as("重复 key 结构错误（C05）")
                .isInstanceOf(QueryValidationException.class)
                .hasMessageContaining("重复");
        }

        @Test
        void should_rejectStructure_whenItemComponentsNull() {
            assertThatThrownBy(() -> QueryRequestValidator.validate(request(
                new QueryItem("k1", null, Evaluation.full(), ResultForm.DECISION, OutputSpec.minimal()))))
                .hasMessageContaining("selection");
            assertThatThrownBy(() -> QueryRequestValidator.validate(request(
                new QueryItem("k1", new TypeLevel(List.of(REPORT_VIEW)), null, ResultForm.DECISION,
                    OutputSpec.minimal()))))
                .hasMessageContaining("evaluation");
            assertThatThrownBy(() -> QueryRequestValidator.validate(request(
                new QueryItem("k1", new TypeLevel(List.of(REPORT_VIEW)), Evaluation.full(), null,
                    OutputSpec.minimal()))))
                .hasMessageContaining("resultForm");
            assertThatThrownBy(() -> QueryRequestValidator.validate(request(
                new QueryItem("k1", new TypeLevel(List.of(REPORT_VIEW)), Evaluation.full(),
                    ResultForm.DECISION, null))))
                .hasMessageContaining("output");
        }

        @Test
        void should_rejectStructure_whenSelectionContentInvalid() {
            assertThatThrownBy(() -> QueryRequestValidator.validate(request(
                new QueryItem("k1", new TypeLevel(List.of()), Evaluation.full(), ResultForm.DECISION,
                    OutputSpec.minimal()))))
                .as("空 TYPE_LEVEL requirements（C05）")
                .isInstanceOf(QueryValidationException.class)
                .hasMessageContaining("requirements");
            assertThatThrownBy(() -> QueryRequestValidator.validate(request(new QueryItem("k1",
                new TypeLevel(List.of(new TypeOperation(" ", "VIEW"))), Evaluation.full(),
                ResultForm.DECISION, OutputSpec.minimal()))))
                .as("空资源类型（C05）")
                .isInstanceOf(QueryValidationException.class)
                .hasMessageContaining("resourceTypeCode");
            assertThatThrownBy(() -> QueryRequestValidator.validate(request(new QueryItem("k1",
                new TypeLevel(List.of(new TypeOperation("REPORT", ""))), Evaluation.full(),
                ResultForm.DECISION, OutputSpec.minimal()))))
                .as("空操作码（C05）")
                .isInstanceOf(QueryValidationException.class)
                .hasMessageContaining("operationCode");
            assertThatThrownBy(() -> QueryRequestValidator.validate(request(
                new QueryItem("k1", new TargetSet(List.of(), Inheritance.SELF, TypeFallback.ALLOW, null),
                    Evaluation.full(), ResultForm.DECISION, OutputSpec.minimal()))))
                .as("无实例 clause（C05）")
                .isInstanceOf(QueryValidationException.class)
                .hasMessageContaining("clauses");
            assertThatThrownBy(() -> QueryRequestValidator.validate(request(
                new QueryItem("k1", new TargetSet(List.of(new TargetClause(REPORT_VIEW,
                    new ByCode(" ", null, null))), Inheritance.SELF, TypeFallback.ALLOW, null),
                    Evaluation.full(), ResultForm.DECISION, OutputSpec.minimal()))))
                .isInstanceOf(QueryValidationException.class)
                .hasMessageContaining("code");
            assertThatThrownBy(() -> QueryRequestValidator.validate(request(
                new QueryItem("k1", new TargetSet(List.of(new TargetClause(REPORT_VIEW,
                    new ByEntityId(0L))), Inheritance.SELF, TypeFallback.ALLOW, null),
                    Evaluation.full(), ResultForm.DECISION, OutputSpec.minimal()))))
                .as("非法实体 ID（C05）")
                .isInstanceOf(QueryValidationException.class)
                .hasMessageContaining("entityId");
            assertThatThrownBy(() -> QueryRequestValidator.validate(request(
                new QueryItem("k1", new TargetSet(List.of(reportAClause()), null, TypeFallback.ALLOW, null),
                    Evaluation.full(), ResultForm.DECISION, OutputSpec.minimal()))))
                .hasMessageContaining("inheritance");
            assertThatThrownBy(() -> QueryRequestValidator.validate(request(
                new QueryItem("k1", new TargetSet(List.of(reportAClause()), Inheritance.SELF, null, null),
                    Evaluation.full(), ResultForm.DECISION, OutputSpec.minimal()))))
                .hasMessageContaining("typeFallback");
        }

        @Test
        void should_rejectStructure_whenParentRequirementInvalid() {
            assertThatThrownBy(() -> QueryRequestValidator.validate(request(
                new QueryItem("k1", new TargetSet(List.of(reportAClause()), Inheritance.SELF,
                    TypeFallback.ALLOW, new ParentRequirement(" ", new ByCode("F1", null, null),
                        Set.of("VIEW"))), Evaluation.full(), ResultForm.DECISION, OutputSpec.minimal()))))
                .hasMessageContaining("resourceTypeCode");
            assertThatThrownBy(() -> QueryRequestValidator.validate(request(
                new QueryItem("k1", new TargetSet(List.of(reportAClause()), Inheritance.SELF,
                    TypeFallback.ALLOW, new ParentRequirement("FOLDER", new ByCode("F1", null, null),
                        Set.of())), Evaluation.full(), ResultForm.DECISION, OutputSpec.minimal()))))
                .as("空父操作集不代表不限操作")
                .isInstanceOf(QueryValidationException.class)
                .hasMessageContaining("父操作集");
            assertThatThrownBy(() -> QueryRequestValidator.validate(request(
                new QueryItem("k1", new TargetSet(List.of(reportAClause()), Inheritance.SELF,
                    TypeFallback.ALLOW, new ParentRequirement("FOLDER", new ByEntityId(-1L),
                        Set.of("VIEW"))), Evaluation.full(), ResultForm.DECISION, OutputSpec.minimal()))))
                .isInstanceOf(QueryValidationException.class)
                .hasMessageContaining("entityId");
        }

        @Test
        void should_rejectStructure_whenFactsOutputKeepsNothing() {
            assertThatThrownBy(() -> QueryRequestValidator.validate(request(
                typeLevelItem("k1", ResultForm.FACTS, Evaluation.preserveSkip(), OutputSpec.minimal()))))
                .as("FACTS 至少 KEPT")
                .isInstanceOf(QueryValidationException.class)
                .hasMessageContaining("KEPT");
            assertThatCode(() -> QueryRequestValidator.validate(request(
                typeLevelItem("k1", ResultForm.DECISION, Evaluation.full(), OutputSpec.minimal()))))
                .as("最小 DECISION 可为 NONE")
                .doesNotThrowAnyException();
        }
    }
}
