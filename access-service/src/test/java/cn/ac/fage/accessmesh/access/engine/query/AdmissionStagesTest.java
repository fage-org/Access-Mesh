package cn.ac.fage.accessmesh.access.engine.query;

import cn.ac.fage.accessmesh.access.audit.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.engine.core.SubjectDomainService;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.engine.util.RolePermEntryMapper;
import cn.ac.fage.accessmesh.access.grant.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.grant.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.access.infrastructure.cache.AccessCacheCatalog;
import cn.ac.fage.accessmesh.access.rule.entity.PermissionCondition;
import cn.ac.fage.accessmesh.access.rule.entity.PermissionConflictRule;
import cn.ac.fage.accessmesh.access.grant.service.domain.RoleResourcePermissionDomainService;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.resource.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.access.role.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.rule.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.access.rule.mapper.PermissionConflictRuleMapper;
import cn.ac.fage.accessmesh.access.rule.service.domain.impl.PermissionConditionDomainServiceImpl;
import cn.ac.fage.accessmesh.access.rule.service.domain.impl.PermissionConflictDomainServiceImpl;
import cn.ac.fage.accessmesh.access.type.service.domain.OperationPermissionDomainService;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 准入使用真实执行器、条件与互斥能力；存储边界在单测中替换。 */
class AdmissionStagesTest {
    static final TypeOperation VIEW = new TypeOperation("REPORT", "VIEW");
    final TypeResolutionService types = mock(TypeResolutionService.class);
    final OperationPermissionDomainService operations = mock(OperationPermissionDomainService.class);
    final RoleResourcePermissionMapper grants = mock(RoleResourcePermissionMapper.class);
    final ResourceEntityMapper resources = mock(ResourceEntityMapper.class);
    final PermissionConditionMapper conditionRows = mock(PermissionConditionMapper.class);
    final PermissionConflictRuleMapper rules = mock(PermissionConflictRuleMapper.class);
    final SubjectDomainService subjects = mock(SubjectDomainService.class);
    final CacheService cache = mock(CacheService.class);
    final AuditDomainService audit = mock(AuditDomainService.class);
    final List<RoleResourcePermission> candidates = new ArrayList<>();
    final List<RoleResourcePermission> parents = new ArrayList<>();
    QueryExecutionEngine engine;

    @BeforeEach
    void setup() {
        when(types.batchResolveTypeValues(eq(1L), eq("resource_type"), anySet())).thenReturn(Map.of("REPORT", 1));
        when(operations.selectByTenantAndResourceTypes(eq(1L), anySet())).thenReturn(List.of(
            QueryStagesTest.op(11, 1, "VIEW", 2, 0), QueryStagesTest.op(12, 1, "UPDATE", 4, 2)));
        when(grants.selectAdmissionCandidatesByTypeMasks(eq(1L), anySet(), anyList()))
            .thenAnswer(i -> List.copyOf(candidates));
        when(grants.selectAdmissionParentsByIds(eq(1L), anySet())).thenAnswer(i -> List.copyOf(parents));
        ObjectMapper json = new ObjectMapper();
        var conditionService = new PermissionConditionDomainServiceImpl(conditionRows,
            mock(RoleResourcePermissionDomainService.class), json, cache);
        var conflictService = new PermissionConflictDomainServiceImpl(rules, cache, json, audit, operations, subjects);
        engine = new QueryExecutionEngine(Clock.fixed(Instant.parse("2004-01-02T02:00:00Z"), ZoneOffset.UTC),
            new QueryReadSupport(types, operations, mock(ResourceEntityDomainService.class),
                mock(AbstractRoleMapper.class), grants, cache, new RolePermEntryMapper()),
            subjects, conditionService, conflictService, resources,
            new QueryAuditCollector(audit, QueryEngineMetrics.noop()), QueryEngineMetrics.noop());
    }

    QueryResult execute(QueryItem... items) {
        return engine.execute(new QueryRequest(1L, new Roles(Set.of(10L)), CallerContext.of("127.0.0.1"),
            ReadOptions.defaults(), List.of(items)));
    }

    @Test
    void should_denyNoCandidate_whenRoleHasNoCoveringGrant() {
        var result = (AdmissionResult) execute(QueryItem.admission("view", VIEW, OutputSpec.rawAndKept()))
            .orderedResults().getFirst();
        assertThat(result.reason()).isEqualTo(AdmissionResult.Reason.NO_CANDIDATE);
        assertThat(result.finalCheckRequired()).isTrue();
        assertThat(result.coverage().completedStages()).containsExactly(Stage.ADMISSION_CANDIDATES);
        verifyNoInteractions(resources);
    }

    AdmissionResult admission() {
        return (AdmissionResult) execute(QueryItem.admission("view", VIEW, OutputSpec.rawAndKept()))
            .orderedResults().getFirst();
    }

    GrantSetResult facts() {
        return (GrantSetResult) execute(QueryItem.admissionFacts("view", VIEW, OutputSpec.rawAndKept()))
            .orderedResults().getFirst();
    }

    static List<GrantFact> kept(ItemResult result) {
        ResultDetails details = result instanceof AdmissionResult admission ? admission.details() : ((GrantSetResult) result).details();
        return details.stageFacts().getFirst().retainedAfterEvaluation();
    }

    static RoleResourcePermission row(long id, Long entity, long bit) {
        return QueryStagesTest.grant(id, 1, entity, bit);
    }

    static PermissionCondition condition(long id, boolean enabled, String rules) {
        PermissionCondition condition = new PermissionCondition();
        condition.setId(id); condition.setEnabled(enabled); condition.setConditionRules(rules);
        return condition;
    }

    @Test
    void should_allowInstanceCandidateWithoutGrantingTypeLevel_whenOnlyInstanceIsGranted() {
        candidates.add(row(101, 100L, 2));
        assertThat(admission().outcome()).isEqualTo(AdmissionResult.Admission.MAY_ENTER);
        var finalType = (DecisionResult) execute(QueryItem.decision("type", new TypeLevel(List.of(VIEW)),
            OutputSpec.minimal())).orderedResults().getFirst();
        assertThat(finalType.outcome()).isEqualTo(DecisionResult.Decision.DENY);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void should_deferPermissionMutex_whenConflictingGrantsAreOnSameOrDifferentInstances(boolean sameInstance) {
        candidates.add(row(101, 100L, 2)); candidates.add(row(102, sameInstance ? 100L : 200L, 4));
        PermissionConflictRule rule = new PermissionConflictRule();
        rule.setId(90L); rule.setFirstOperationPermissionId(11L); rule.setSecondOperationPermissionId(12L);
        when(rules.selectByConflictType(1L, "PERM_MUTEX")).thenReturn(List.of(rule));
        AdmissionResult result = admission();
        assertThat(result.outcome()).isEqualTo(AdmissionResult.Admission.MAY_ENTER);
        assertThat(result.coverage().permissionMutex()).isEqualTo(EvaluationCoverage.MutexCoverage.SKIPPED);
        assertThat(result.coverage().authorizationStage()).isEqualTo(EvaluationCoverage.AuthorizationStage.OPERATION_ADMISSION);
        assertThat(result.coverage().requestedSelectionComplete()).isFalse();
        assertThat(kept(facts())).hasSize(2);
        verify(rules, never()).selectByConflictType(anyLong(), eq("PERM_MUTEX"));
        verifyNoInteractions(audit);
    }

    @Test
    void should_retainContextDeferredWithoutEvaluatingParentCondition_whenChildHasValidParent() {
        RoleResourcePermission child = row(101, 100L, 2);
        child.setDependOn(200L); child.setCanGrant(false);
        RoleResourcePermission parent = row(200, 300L, 4);
        parent.setConditionId(900L);
        candidates.add(child); parents.add(parent);
        AdmissionResult result = admission();
        assertThat(result.outcome()).isEqualTo(AdmissionResult.Admission.MAY_ENTER);
        assertThat(kept(result).getFirst().admissionCandidateKind()).isEqualTo(GrantFact.AdmissionCandidateKind.CONTEXT_DEFERRED);
        assertThat(result.coverage().parentCheck()).isEqualTo(EvaluationCoverage.ParentCheckCoverage.RUNTIME_DEFERRED);
        assertThat(result.details().parentCheck().matchedOperationCodes()).isEmpty();
        assertThat(result.details().trace().parents()).isEmpty();
        verifyNoInteractions(conditionRows, resources);
        verify(grants).selectAdmissionParentsByIds(1L, Set.of(200L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "otherRole", "nested", "childCanGrant", "childCondition"})
    void should_excludeInvalidChild_whenParentStructureIsNotLegal(String invalid) {
        RoleResourcePermission child = row(101, 100L, 2);
        child.setDependOn(200L); child.setCanGrant(false);
        RoleResourcePermission parent = row(200, 300L, 4);
        if (invalid.equals("otherRole")) parent.setAbstractRoleId(99L);
        if (invalid.equals("nested")) parent.setDependOn(300L);
        if (invalid.equals("childCanGrant")) child.setCanGrant(true);
        if (invalid.equals("childCondition")) child.setConditionId(900L);
        candidates.add(child);
        if (!invalid.equals("missing")) parents.add(parent);
        assertThat(admission().reason()).isEqualTo(AdmissionResult.Reason.NO_CANDIDATE);
        assertThat(kept(facts())).isEmpty();
        verifyNoInteractions(conditionRows);
    }

    @Test
    void should_keepSourcesIndependentWithoutPrivileges_whenFactsContainManualAutoDepAndRoot() {
        RoleResourcePermission manual = row(101, 100L, 2); manual.setGrantSource("MANUAL");
        RoleResourcePermission automatic = row(102, 100L, 4); automatic.setGrantSource("AUTO_DEP");
        RoleResourcePermission root = row(103, null, 8); root.setGrantSource("AUTHORITY_ROOT");
        candidates.addAll(List.of(manual, automatic, root));
        assertThat(kept(facts())).extracting(GrantFact::permissionId).containsExactly(101L, 102L);
        candidates.remove(manual);
        assertThat(admission().outcome()).isEqualTo(AdmissionResult.Admission.MAY_ENTER);
        candidates.remove(automatic);
        assertThat(admission().reason()).isEqualTo(AdmissionResult.Reason.NO_CANDIDATE);
        root.setGrantedBits(2L);
        assertThat(kept(facts()).getFirst().admissionCandidateKind()).isEqualTo(GrantFact.AdmissionCandidateKind.ALL);
    }

    @Test
    void should_preserveFailingAndUnconditionalBranchesInFacts_whenCurrentTimeRejectsOneBranch() {
        RoleResourcePermission conditional = row(101, 100L, 2); conditional.setConditionId(900L);
        candidates.add(conditional); candidates.add(row(102, 100L, 2));
        when(conditionRows.selectValidByIds(1L, Set.of(900L))).thenReturn(List.of(condition(900, true,
            "{\"logic\":\"AND\",\"items\":[{\"type\":\"DATE_RANGE\",\"params\":{\"start\":\"2099-01-01\",\"end\":\"2099-12-31\"}}]}")));
        var snapshot = facts();
        assertThat(kept(snapshot)).extracting(GrantFact::permissionId).containsExactly(101L, 102L);
        assertThat(snapshot.coverage().conditions()).isEqualTo(EvaluationCoverage.ConditionCoverage.PRESERVED);
        assertThat(snapshot.coverage().authorizationStage()).isEqualTo(EvaluationCoverage.AuthorizationStage.OPERATION_ADMISSION);
        assertThat(snapshot.coverage().requestedSelectionComplete()).isTrue();
        assertThat(kept(admission())).extracting(GrantFact::permissionId).containsExactly(102L);
        candidates.removeLast();
        assertThat(admission().reason()).isEqualTo(AdmissionResult.Reason.CONDITION_NOT_MET);
        assertThat(admission().coverage().requestedSelectionComplete()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "disabled", "brokenJson", "brokenShape", "nullLogic"})
    void should_excludeBadConditionWithoutTurningItUnconditional_whenConditionIsUnavailable(String invalid) {
        RoleResourcePermission conditional = row(101, 100L, 2); conditional.setConditionId(900L);
        candidates.add(conditional);
        if (!invalid.equals("missing")) {
            String json = invalid.equals("brokenJson") ? "{" : invalid.equals("brokenShape") ? "{}"
                : invalid.equals("nullLogic") ? "{\"logic\":null,\"items\":[]}" : "{\"items\":[]}";
            when(conditionRows.selectValidByIds(1L, Set.of(900L)))
                .thenReturn(List.of(condition(900, !invalid.equals("disabled"), json)));
        }
        assertThat(admission().reason()).isEqualTo(AdmissionResult.Reason.CONDITION_NOT_MET);
        assertThat(kept(facts())).isEmpty();
    }

    @Test
    void should_failWholeExecution_whenConditionReadFails() {
        RoleResourcePermission conditional = row(101, 100L, 2); conditional.setConditionId(900L);
        candidates.add(conditional);
        RuntimeException failure = new IllegalStateException("database unavailable");
        when(conditionRows.selectValidByIds(1L, Set.of(900L))).thenThrow(failure);
        assertThatThrownBy(this::facts).isInstanceOf(QueryExecutionException.class).hasCause(failure);
        assertThatThrownBy(this::admission).isInstanceOf(QueryExecutionException.class).hasCause(failure);
    }

    @Test
    void should_useFreshCoveringDefinitionAndShareLoads_whenRepeatedRequirementsHaveDistinctKeys() {
        candidates.add(row(101, 100L, 4));
        // 旧掩码目录声称 UPDATE 不覆盖 VIEW；准入不得消费它。
        when(cache.getBatch(eq(AccessCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE), eq(1L), anySet()))
            .thenReturn(Map.of(AccessCacheCatalog.operationPermissionsByTypeKey(1),
                Map.of(12L, QueryStagesTest.op(12, 1, "UPDATE", 4, 0))));
        var result = execute(QueryItem.admission("first", VIEW, OutputSpec.kept()),
            QueryItem.admission("duplicate", VIEW, OutputSpec.kept()),
            QueryItem.admission("update", new TypeOperation("REPORT", "UPDATE"), OutputSpec.kept()));
        assertThat(result.orderedResults()).extracting(ItemResult::key).containsExactly("first", "duplicate", "update");
        assertThat(result.orderedResults()).allSatisfy(r ->
            assertThat(((AdmissionResult) r).outcome()).isEqualTo(AdmissionResult.Admission.MAY_ENTER));
        verify(grants).selectAdmissionCandidatesByTypeMasks(eq(1L), eq(Set.of(10L)),
            eq(List.of(new RoleResourcePermissionMapper.BitMaskEntry(1, 6L))));
        verify(operations).selectByTenantAndResourceTypes(1L, Set.of(1));
        verify(cache, never()).getBatch(eq(AccessCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE), anyLong(), anySet());
        verify(grants, never()).selectValidByRoleIds(anyLong(), anySet());
    }

    @Test
    void should_raiseConfigurationFault_whenRequestedOperationIsUnknownOrDamaged() {
        assertThatThrownBy(() -> execute(QueryItem.admission("bad", new TypeOperation("REPORT", "MISSING"), OutputSpec.minimal())))
            .isInstanceOf(AdmissionConfigurationException.class);
        when(operations.selectByTenantAndResourceTypes(eq(1L), anySet()))
            .thenReturn(List.of(QueryStagesTest.op(11, 1, "VIEW", 0, 0)));
        assertThatThrownBy(this::admission).isInstanceOf(AdmissionConfigurationException.class);
        verifyNoInteractions(grants);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1L, Long.MIN_VALUE})
    void should_useBitsWithoutSignRestriction_whenInheritanceMaskIsNegative(long mask) {
        when(operations.selectByTenantAndResourceTypes(eq(1L), anySet())).thenReturn(List.of(
            QueryStagesTest.op(11, 1, "VIEW", 2, 0), QueryStagesTest.op(12, 1, "UPDATE", 4, mask)));
        candidates.add(row(101, 100L, 4));
        boolean coversView = (mask & 2L) != 0;
        assertThat(admission().outcome()).isEqualTo(coversView
            ? AdmissionResult.Admission.MAY_ENTER : AdmissionResult.Admission.DENY);
        assertThat(kept(facts())).hasSize(coversView ? 1 : 0);
        verify(grants, times(2)).selectAdmissionCandidatesByTypeMasks(eq(1L), eq(Set.of(10L)),
            eq(List.of(new RoleResourcePermissionMapper.BitMaskEntry(1, coversView ? 6L : 2L))));
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 12L})
    void should_ignoreUnrelatedDamagedOperation_whenItCannotCoverRequirement(long bit) {
        when(operations.selectByTenantAndResourceTypes(eq(1L), anySet())).thenReturn(List.of(
            QueryStagesTest.op(11, 1, "VIEW", 2, 0), QueryStagesTest.op(12, 1, "UPDATE", bit, 0)));
        candidates.add(row(101, 100L, 2));
        assertThat(admission().outcome()).isEqualTo(AdmissionResult.Admission.MAY_ENTER);
        assertThat(kept(facts())).extracting(GrantFact::permissionId).containsExactly(101L);
        verify(grants, times(2)).selectAdmissionCandidatesByTypeMasks(eq(1L), eq(Set.of(10L)),
            eq(List.of(new RoleResourcePermissionMapper.BitMaskEntry(1, 2L))));
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 3L})
    void should_raiseConfigurationFault_whenDamagedOperationCoversRequirement(long bit) {
        when(operations.selectByTenantAndResourceTypes(eq(1L), anySet())).thenReturn(List.of(
            QueryStagesTest.op(11, 1, "VIEW", 2, 0), QueryStagesTest.op(12, 1, "UPDATE", bit, 2)));
        assertThatThrownBy(this::admission).isInstanceOf(AdmissionConfigurationException.class);
        assertThatThrownBy(this::facts).isInstanceOf(AdmissionConfigurationException.class);
        verifyNoInteractions(grants);
    }

    @Test
    void should_rejectMixedRequestsAndTreeExpansionBeforeReads_whenAdmissionSelectionIsUsed() {
        assertThatThrownBy(() -> execute(QueryItem.admission("a", VIEW, OutputSpec.minimal()),
            QueryItem.decision("b", new TypeLevel(List.of(VIEW)), OutputSpec.minimal())))
            .isInstanceOf(QueryValidationException.class);
        var tree = new OutputSpec(FactDetail.KEPT, false, false, false, PresentationExpansion.CHILDREN, Set.of(), false);
        assertThatThrownBy(() -> execute(QueryItem.admissionFacts("tree", VIEW, tree)))
            .isInstanceOf(QueryValidationException.class);
        verifyNoInteractions(types, operations, grants, cache);
    }

    @Test
    void should_labelOnlyRealRoleConflictEvidence_whenAdmissionLosesAllRoles() {
        PermissionConflictRule rule = QueryAuditAndTraceTest.roleRule(90, 10, 20);
        when(subjects.resolveEffectiveRoles(1L, 100L)).thenReturn(Set.of(10L, 20L));
        when(rules.selectByConflictType(1L, "ROLE_MUTEX")).thenReturn(List.of(rule));
        var result = engine.execute(new QueryRequest(1L, new User(100L), CallerContext.of(null), ReadOptions.defaults(),
            List.of(QueryItem.admission("user", VIEW, OutputSpec.minimal()))));
        assertThat(((AdmissionResult) result.orderedResults().getFirst()).reason()).isEqualTo(AdmissionResult.Reason.NO_ROLE);
        var captured = org.mockito.ArgumentCaptor.forClass(AuditDomainService.OperationLogEntry.class);
        verify(audit).asyncRecordLog(captured.capture());
        assertThat(captured.getValue().summary()).contains("authorizationStage=OPERATION_ADMISSION", "Role mutex evidence")
            .doesNotContain("Perm conflict evidence");
        verify(grants, never()).selectAdmissionCandidatesByTypeMasks(anyLong(), anySet(), anyList());
    }

    @Test
    void should_tryLaterConditionAndPreserveBothIdentities_whenFirstBranchFails() {
        RoleResourcePermission first = row(101, 100L, 2); first.setConditionId(900L);
        RoleResourcePermission second = row(102, 100L, 2); second.setConditionId(901L);
        candidates.addAll(List.of(first, second));
        when(conditionRows.selectValidByIds(1L, Set.of(900L, 901L))).thenReturn(List.of(
            condition(900, true, "{\"logic\":\"OR\",\"items\":[]}"),
            condition(901, true, "{\"logic\":\"AND\",\"items\":[]}")));
        assertThat(kept(admission())).extracting(GrantFact::conditionId).containsExactly(901L);
        assertThat(kept(facts())).extracting(GrantFact::conditionId).containsExactly(900L, 901L);
    }

    @Test
    void should_mergeAllSqlChunksBeforeFactsProjection_whenRoleSetExceedsBatchSize() {
        Set<Long> roles = java.util.stream.LongStream.rangeClosed(1, 501).boxed().collect(java.util.stream.Collectors.toSet());
        when(grants.selectAdmissionCandidatesByTypeMasks(eq(1L), anySet(), anyList())).thenAnswer(i -> {
            Set<Long> batch = i.getArgument(1);
            return batch.stream().sorted().map(role -> {
                RoleResourcePermission grant = row(1000 + role, 100L, 2);
                grant.setAbstractRoleId(role);
                return grant;
            }).toList();
        });
        var result = (GrantSetResult) engine.execute(new QueryRequest(1L, new Roles(roles), CallerContext.of(null),
            ReadOptions.defaults(), List.of(QueryItem.admissionFacts("large", VIEW, OutputSpec.kept()))))
            .orderedResults().getFirst();
        assertThat(kept(result)).hasSize(501);
        assertThat(result.coverage().requestedSelectionComplete()).isTrue();
        verify(grants, times(2)).selectAdmissionCandidatesByTypeMasks(eq(1L), anySet(), anyList());
    }

    @Test
    void should_validateConfigurationBeforeNoRole_whenSubjectIsEmpty() {
        var empty = new Roles(Set.of());
        var good = new QueryRequest(1L, empty, CallerContext.of(null), ReadOptions.defaults(),
            List.of(QueryItem.admission("good", VIEW, OutputSpec.minimal())));
        var result = (AdmissionResult) engine.execute(good).orderedResults().getFirst();
        assertThat(result.reason()).isEqualTo(AdmissionResult.Reason.NO_ROLE);
        assertThat(result.finalCheckRequired()).isTrue();
        assertThat(result.coverage().skippedStages()).containsEntry(Stage.ADMISSION_CANDIDATES, EvaluationCoverage.SkipReason.NO_ROLE);
        var bad = new QueryRequest(1L, empty, CallerContext.of(null), ReadOptions.defaults(),
            List.of(QueryItem.admissionFacts("bad", new TypeOperation("REPORT", "MISSING"), OutputSpec.kept())));
        assertThatThrownBy(() -> engine.execute(bad)).isInstanceOf(AdmissionConfigurationException.class);
        verifyNoInteractions(grants, conditionRows, resources);
    }
}
