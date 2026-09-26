package cn.ac.fage.accessmesh.access.engine.query;

import cn.ac.fage.accessmesh.access.audit.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.engine.core.SubjectDomainService;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.engine.util.RolePermEntryMapper;
import cn.ac.fage.accessmesh.access.grant.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.access.grant.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.grant.service.domain.RoleResourcePermissionDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.cache.AccessCacheCatalog;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.resource.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.access.role.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.rule.entity.PermissionCondition;
import cn.ac.fage.accessmesh.access.rule.entity.PermissionConflictRule;
import cn.ac.fage.accessmesh.access.rule.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.access.rule.mapper.PermissionConflictRuleMapper;
import cn.ac.fage.accessmesh.access.rule.service.domain.impl.PermissionConditionDomainServiceImpl;
import cn.ac.fage.accessmesh.access.rule.service.domain.impl.PermissionConflictDomainServiceImpl;
import cn.ac.fage.accessmesh.access.type.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.type.service.domain.OperationPermissionDomainService;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.common.cache.CacheProperties;
import cn.ac.fage.accessmesh.common.cache.CacheReadToken;
import cn.ac.fage.accessmesh.common.cache.DefaultCacheService;
import cn.ac.fage.accessmesh.common.cache.spi.DistributedCacheStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实 execute + 读取/条件/互斥部件，只有存储边界被替换。 */
class QueryStagesTest {
    final TypeResolutionService types = mock(TypeResolutionService.class);
    final OperationPermissionDomainService operations = mock(OperationPermissionDomainService.class);
    final ResourceEntityDomainService resources = mock(ResourceEntityDomainService.class);
    final ResourceEntityMapper resourceMapper = mock(ResourceEntityMapper.class);
    final AbstractRoleMapper roleMapper = mock(AbstractRoleMapper.class);
    final RoleResourcePermissionMapper grants = mock(RoleResourcePermissionMapper.class);
    final PermissionConflictRuleMapper rules = mock(PermissionConflictRuleMapper.class);
    final PermissionConditionMapper conditionMapper = mock(PermissionConditionMapper.class);
    final SubjectDomainService subjects = mock(SubjectDomainService.class);
    final CacheService cache = mock(CacheService.class);
    final AuditDomainService audit = mock(AuditDomainService.class);
    final List<OperationPermission> definitions = new ArrayList<>();
    final List<RoleResourcePermission> scopeRows = new ArrayList<>();
    final List<RoleResourcePermission> instanceRows = new ArrayList<>();
    final Clock clock = Clock.fixed(Instant.parse("2004-01-02T02:00:00Z"), ZoneOffset.UTC);
    QueryExecutionEngine engine;

    @BeforeEach
    void setup() {
        definitions.add(op(11, 1, "VIEW", 2, 0));
        definitions.add(op(12, 1, "UPDATE", 4, 2));
        definitions.add(op(21, 2, "VIEW", 2, 0));
        definitions.add(op(22, 2, "UPDATE", 4, 0));
        when(types.batchResolveTypeValues(eq(1L), eq("resource_type"), anySet())).thenAnswer(i -> {
            Set<String> requested = i.getArgument(2);
            return Map.of("REPORT", 1, "USER", 2).entrySet().stream().filter(e -> requested.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        });
        when(operations.selectByTenantAndResourceTypes(eq(1L), anySet())).thenAnswer(i -> {
            Set<Integer> requested = i.getArgument(1);
            return definitions.stream().filter(op -> requested.contains(op.getResourceType())).toList();
        });
        when(grants.selectScopeAllPermsByBitsBatch(eq(1L), anySet(), anyList())).thenAnswer(i -> List.copyOf(scopeRows));
        when(grants.selectInstancePermsByBitsBatch(eq(1L), anySet(), anySet(), anyList())).thenAnswer(i -> {
            Set<Long> ids = i.getArgument(2);
            return instanceRows.stream().filter(r -> ids.contains(r.getResourceEntityId())).toList();
        });
        ObjectMapper json = new ObjectMapper();
        var conditions = new PermissionConditionDomainServiceImpl(conditionMapper,
            mock(RoleResourcePermissionDomainService.class), json, cache);
        var conflicts = new PermissionConflictDomainServiceImpl(rules, cache, json, audit, operations, subjects);
        engine = new QueryExecutionEngine(clock,
            new QueryReadSupport(types, operations, resources, roleMapper, grants, cache, new RolePermEntryMapper()),
            subjects, conditions, conflicts, resourceMapper);
    }

    static OperationPermission op(long id, int type, String code, long bit, long inherit) {
        OperationPermission op = QueryReadSupportTest.operation(id, type, code, bit);
        op.setInheritMask(inherit);
        return op;
    }

    static RoleResourcePermission grant(long id, int type, Long entity, long bits) {
        RoleResourcePermission row = QueryReadSupportTest.grant(id, 10);
        row.setResourceType(type); row.setResourceEntityId(entity); row.setGrantedBits(bits);
        row.setScopeAll(entity == null);
        return row;
    }

    void mutex() {
        PermissionConflictRule rule = new PermissionConflictRule();
        rule.setId(90L); rule.setFirstOperationPermissionId(11L); rule.setSecondOperationPermissionId(12L);
        when(rules.selectByConflictType(1L, "PERM_MUTEX")).thenReturn(List.of(rule));
    }

    static TargetClause clause(long id) { return new TargetClause(new TypeOperation("REPORT", "VIEW"), new ByEntityId(id)); }
    static TargetSet target(Inheritance inheritance, TypeFallback fallback, TargetClause... clauses) {
        return new TargetSet(List.of(clauses), inheritance, fallback, null);
    }
    static QueryItem item(String key, TargetClause... clauses) {
        return QueryItem.decision(key, target(Inheritance.SELF, TypeFallback.DISALLOW, clauses), OutputSpec.minimalWithMatchIds());
    }
    QueryResult execute(QueryItem... items) {
        return engine.execute(new QueryRequest(1L, new Roles(Set.of(10L)), CallerContext.of("127.0.0.1"),
            ReadOptions.defaults(), List.of(items)));
    }
    static DecisionResult decision(QueryResult result, int index) { return (DecisionResult) result.orderedResults().get(index); }

    @Test
    void should_loadRequestedDefinitionsWithoutExpandingSelection_whenExtraOperationHasNoGrant() {
        scopeRows.add(grant(101, 1, null, 2));
        OutputSpec output = new OutputSpec(FactDetail.NONE, true, true, false, PresentationExpansion.NONE,
            Set.of(new TypeOperation("USER", "UPDATE")), false);
        var result = decision(execute(QueryItem.decision("scope",
            target(Inheritance.SELF, TypeFallback.ALLOW, clause(100)), output)), 0);
        assertThat(result.outcome()).isEqualTo(DecisionResult.Decision.ALLOW);
        assertThat(result.details().loadedSections()).contains(ResultDetails.DetailSection.DESCRIPTIONS);
        assertThat(result.details().matchedPermissionIds()).containsExactly(101L);
        assertThat(result.details().descriptions().requestedOperations())
            .containsKey(new TypeOperation("USER", "UPDATE"));
        assertThat(result.coverage().skippedStages()).containsEntry(Stage.INSTANCE,
            EvaluationCoverage.SkipReason.SUFFICIENT_DECISION);
        verify(grants, never()).selectInstancePermsByBitsBatch(anyLong(), anySet(), anySet(), anyList());
    }

    @Test
    void should_distinguishLoadedEmptyDescriptionBlocks_whenSubjectHasNoRoles() {
        OutputSpec output = new OutputSpec(FactDetail.RAW_AND_KEPT, true, true, true, PresentationExpansion.NONE,
            Set.of(new TypeOperation("REPORT", "VIEW")), false);
        var result = (GrantSetResult) engine.execute(new QueryRequest(1L, new Roles(Set.of()),
            CallerContext.of(null), ReadOptions.defaults(), List.of(
                QueryItem.grantListFacts("empty", null, Evaluation.full(), output)))).orderedResults().getFirst();
        assertThat(result.collectionStatus()).isEqualTo(GrantSetResult.CollectionStatus.NO_ROLE);
        assertThat(result.details().loadedSections()).contains(ResultDetails.DetailSection.DESCRIPTIONS,
            ResultDetails.DetailSection.EFFECTIVE_OPERATIONS);
        assertThat(result.details().matchedPermissionIds()).isEmpty();
        assertThat(result.details().descriptions().requestedOperations()).containsKey(new TypeOperation("REPORT", "VIEW"));
        assertThat(ScopeCoverageProjector.project(result, List.of(new TypeOperation("REPORT", "VIEW"))).getFirst().scopeMode())
            .isEqualTo(ScopeMode.DENIED);
        verifyNoInteractions(grants, resources, resourceMapper, roleMapper);
    }

    static OutputSpec scopeOutput(TypeOperation... requirements) {
        return new OutputSpec(FactDetail.RAW_AND_KEPT, true, true, false, PresentationExpansion.NONE,
            Set.of(requirements), false);
    }

    @Test
    void should_projectOnlyMatchedParentOperationsFromRetainedFacts_withoutReevaluatingOrExposingIds() {
        scopeRows.add(grant(201, 1, null, 2));
        var conditional = grant(202, 1, null, 4); conditional.setConditionId(500L); scopeRows.add(conditional);
        instanceRows.add(dependent(101, 100L, 201)); instanceRows.add(dependent(102, 110L, 201));
        var parent = new ParentRequirement("REPORT", new ByEntityId(200), Set.of("VIEW", "UPDATE"));
        var first = QueryItem.decision("first", new TargetSet(List.of(clause(100)), Inheritance.SELF,
            TypeFallback.DISALLOW, parent), OutputSpec.minimal());
        var second = QueryItem.decision("second", new TargetSet(List.of(clause(110)), Inheritance.SELF,
            TypeFallback.DISALLOW, parent), OutputSpec.minimal());
        var result = execute(first, second);
        for (DecisionResult item : result.orderedResults().stream().map(DecisionResult.class::cast).toList()) {
            assertThat(item.details().parentCheck().matchedOperationCodes()).containsExactly("VIEW");
            assertThat(item.details().loadedSections()).contains(ResultDetails.DetailSection.PARENT_CHECK);
            assertThat(item.details().matchedPermissionIds()).isEmpty();
            assertThat(item.details().stageFacts()).isEmpty();
            assertThat(item.coverage().parentCheck()).isEqualTo(EvaluationCoverage.ParentCheckCoverage.PASSED);
        }
        assertThatThrownBy(() -> decision(result, 0).details().parentCheck().matchedOperationCodes().clear())
            .isInstanceOf(UnsupportedOperationException.class);
        verify(conditionMapper).selectValidByIds(1L, Set.of(500L));
        verify(grants).selectScopeAllPermsByBitsBatch(eq(1L), anySet(), anyList());
        verify(grants, never()).selectInstancePermsByBitsBatch(eq(1L), anySet(), eq(Set.of(200L)), anyList());
        // 判定所需完整目录 + 普通掩码冷填各一次；摘要不能增加第三次操作读取。
        verify(operations, times(2)).selectByTenantAndResourceTypes(1L, Set.of(1));
    }

    @Test
    void should_distinguishUntriggeredAndDeniedParentSummary_whenParentDoesNotAllow() {
        instanceRows.add(grant(101, 1, 100L, 2));
        var untriggered = decision(execute(childItem("main", 100)), 0);
        assertThat(untriggered.coverage().parentCheck()).isEqualTo(EvaluationCoverage.ParentCheckCoverage.NOT_TRIGGERED);
        assertThat(untriggered.details().loadedSections()).doesNotContain(ResultDetails.DetailSection.PARENT_CHECK);
        assertThat(untriggered.details().parentCheck().matchedOperationCodes()).isEmpty();
        instanceRows.clear(); instanceRows.add(dependent(102, 100L, 999));
        var denied = decision(execute(childItem("child", 100)), 0);
        assertThat(denied.coverage().parentCheck()).isEqualTo(EvaluationCoverage.ParentCheckCoverage.FAILED);
        assertThat(denied.details().loadedSections()).contains(ResultDetails.DetailSection.PARENT_CHECK);
        assertThat(denied.details().parentCheck().matchedOperationCodes()).isEmpty();
    }

    GrantSetResult projectedList(OutputSpec output, Evaluation evaluation, RoleResourcePermission... rows) {
        when(grants.selectValidByRoleIds(1L, Set.of(10L))).thenReturn(List.of(rows));
        return (GrantSetResult) execute(QueryItem.grantListFacts("list", null, evaluation, output))
            .orderedResults().getFirst();
    }

    static ResourceEntity describedResource(long id, String code, String codeType) {
        ResourceEntity row = new ResourceEntity();
        row.setId(id); row.setResourceType(1); row.setCode(code); row.setCodeType(codeType);
        row.setName("report-" + id); row.setDeleteFlag(0L); row.setStatus(1);
        return row;
    }

    @Test
    void should_returnEmptyAndKeepRawDefinitions_whenConditionsRemoveEveryCoveringGrant() {
        var key = new TypeOperation("REPORT", "VIEW");
        var row = grant(101, 1, 100L, 4); row.setConditionId(500L);
        var result = projectedList(scopeOutput(key), Evaluation.full(), row);
        assertThat(result.details().stageFacts().getFirst().retainedAfterEvaluation()).isEmpty();
        assertThat(result.details().descriptions().operations()).containsKeys(11L, 12L);
        assertThat(ScopeCoverageProjector.project(result, List.of(key)).getFirst().scopeMode()).isEqualTo(ScopeMode.EMPTY);
        verify(conditionMapper).selectValidByIds(1L, Set.of(500L));
        verify(operations).selectByTenantAndResourceTypes(1L, Set.of(1));
    }

    @Test
    void should_returnDenied_whenRawDoesNotCoverOrRequestedOperationIsUnknown() {
        var update = new TypeOperation("REPORT", "UPDATE");
        var unknown = new TypeOperation("REPORT", "UNKNOWN");
        var missing = new TypeOperation("UNKNOWN", "VIEW");
        var result = projectedList(scopeOutput(update, unknown, missing), Evaluation.full(), grant(101, 1, 100L, 2));
        assertThat(ScopeCoverageProjector.project(result, List.of(update, unknown, missing)))
            .extracting(g -> g.scopeMode()).containsOnly(ScopeMode.DENIED);
    }

    @Test
    void should_preferAllWithoutExpandingBusinessInstances_whenRetainedScopeAllCovers() {
        var key = new TypeOperation("REPORT", "VIEW");
        var output = new OutputSpec(FactDetail.RAW_AND_KEPT, true, true, true,
            PresentationExpansion.BOTH, Set.of(key), false);
        var result = projectedList(output, Evaluation.full(), grant(101, 1, null, 4));
        var group = ScopeCoverageProjector.project(result, List.of(key)).getFirst();
        assertThat(group.scopeMode()).isEqualTo(ScopeMode.ALL);
        assertThat(group.items()).isEmpty();
        assertThat(result.details().presentation()).containsExactly(
            new PresentationEntry(101L, 10L, null, PresentationEntry.Derivation.ORIGINAL));
        verifyNoInteractions(resources, resourceMapper);
    }

    @Test
    void should_returnEmpty_whenRetainedInstancesNoLongerExist() {
        var key = new TypeOperation("REPORT", "VIEW");
        var result = projectedList(scopeOutput(key), Evaluation.full(), grant(101, 1, 100L, 2));
        assertThat(result.collectionStatus()).isEqualTo(GrantSetResult.CollectionStatus.PRESENT);
        assertThat(ScopeCoverageProjector.project(result, List.of(key)).getFirst().scopeMode()).isEqualTo(ScopeMode.EMPTY);
        verify(resources).selectValidByIds(1L, Set.of(100L));
    }

    @Test
    void should_deduplicateResourceTuplesWithoutMergingGrantFacts_whenSameResourceHasMultipleSources() {
        var key = new TypeOperation("REPORT", "VIEW");
        var first = describedResource(100, "sys:user", "default");
        var second = describedResource(200, "sys", "user:default");
        when(resources.selectValidByIds(1L, Set.of(100L, 200L))).thenReturn(List.of(first, second));
        var result = projectedList(scopeOutput(key), Evaluation.full(),
            grant(101, 1, 100L, 2), grant(102, 1, 100L, 4), grant(103, 1, 200L, 2));
        first.setName("changed after projection"); first.setCode("changed");
        var group = ScopeCoverageProjector.project(result, List.of(key)).getFirst();
        assertThat(group.scopeMode()).isEqualTo(ScopeMode.INSTANCE);
        assertThat(group.items()).extracting(i -> i.resourceCode()).containsExactly("sys:user", "sys");
        assertThat(group.items().getFirst().resourceName()).isEqualTo("report-100");
        assertThat(result.details().matchedPermissionIds()).containsExactly(101L, 102L, 103L);
        assertThatThrownBy(() -> result.details().descriptions().resources().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @EnumSource(PresentationExpansion.class)
    void should_expandOnlyRequestedDirectionsWithoutChangingFacts_whenProjectionIsEnabled(PresentationExpansion direction) {
        var ancestor = new ResourceEntityMapper.AncestorClosureResult();
        ancestor.setTargetId(100L); ancestor.setClosureId(90L);
        var child = new ResourceEntityMapper.DescendantResult();
        child.setResourceId(100L); child.setDescendantId(110L);
        when(resourceMapper.selectSelfAndAncestorClosureBatch(1L, Set.of(100L))).thenReturn(List.of(ancestor));
        when(resourceMapper.selectDescendantIdsBatch(1L, Set.of(100L))).thenReturn(List.of(child));
        var one = grant(101, 1, 100L, 4); one.setGrantSource("MANUAL");
        var two = grant(102, 1, 100L, 4); two.setGrantSource("AUTO_DEP"); two.setDependOn(999L); two.setConditionId(500L);
        var output = new OutputSpec(FactDetail.RAW_AND_KEPT, true, false, true, direction, Set.of(), false);
        var result = projectedList(output, Evaluation.preserveSkip(), one, two);
        assertThat(result.details().stageFacts().getFirst().retainedAfterEvaluation())
            .extracting(GrantFact::resourceEntityId).containsExactly(100L, 100L);
        assertThat(result.details().stageFacts().getFirst().retainedAfterEvaluation())
            .extracting(GrantFact::grantSource).containsExactly("MANUAL", "AUTO_DEP");
        assertThat(result.details().effectiveOperations()).filteredOn(e -> e.displayedEntityId().equals(100L))
            .extracting(ResultDetails.EffectiveOperationEntry::operationCode).containsExactly("VIEW", "UPDATE", "VIEW", "UPDATE");
        if (direction == PresentationExpansion.NONE) {
            assertThat(result.details().presentation()).isEmpty();
            assertThat(result.details().loadedSections()).doesNotContain(ResultDetails.DetailSection.PRESENTATION);
        } else {
            Set<Long> expected = new LinkedHashSet<>(Set.of(100L));
            if (direction.parents()) expected.add(90L);
            if (direction.children()) expected.add(110L);
            assertThat(result.details().presentation()).extracting(PresentationEntry::displayedEntityId)
                .containsOnlyElementsOf(expected);
            assertThat(result.details().presentation()).hasSize(expected.size() * 2);
        }
        verify(resourceMapper, times(direction.parents() ? 1 : 0)).selectSelfAndAncestorClosureBatch(anyLong(), anySet());
        verify(resourceMapper, times(direction.children() ? 1 : 0)).selectDescendantIdsBatch(anyLong(), anySet());
        verifyNoInteractions(conditionMapper, rules, resources);
    }

    @Test
    void should_batchTwentyOutputTypesOnce_whenGrantListNeedsAncillaryDefinitions() {
        definitions.clear();
        List<RoleResourcePermission> rows = new ArrayList<>();
        for (int type = 1; type <= 20; type++) {
            definitions.add(op(type, type, "VIEW", 2, 0));
            rows.add(grant(100 + type, type, null, 2));
        }
        var output = new OutputSpec(FactDetail.KEPT, false, true, true, PresentationExpansion.NONE, Set.of(), false);
        var result = projectedList(output, Evaluation.preserveSkip(), rows.toArray(RoleResourcePermission[]::new));
        assertThat(result.details().descriptions().operations()).hasSize(20);
        assertThat(result.details().effectiveOperations()).hasSize(20);
        verify(operations).selectByTenantAndResourceTypes(eq(1L), argThat(types -> types.size() == 20));
        verifyNoMoreInteractions(operations);
        verifyNoInteractions(types, conditionMapper, rules, resources, resourceMapper);
    }

    @Test
    void should_keepCachedJudgementSeparateFromFreshProjection_whenOperationCoverageChanged() {
        definitions.removeIf(op -> op.getId().equals(12L));
        definitions.add(op(12, 1, "UPDATE", 4, 0));
        when(cache.getBatch(eq(AccessCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE), eq(1L), anySet()))
            .thenReturn(Map.of(AccessCacheCatalog.operationPermissionsByTypeKey(1),
                Map.of(11L, op(11, 1, "VIEW", 2, 0), 12L, op(12, 1, "UPDATE", 4, 2))));
        scopeRows.add(grant(101, 1, null, 4));
        var output = new OutputSpec(FactDetail.NONE, true, true, true, PresentationExpansion.NONE, Set.of(), false);
        var a = QueryItem.decision("described", target(Inheritance.SELF, TypeFallback.ALLOW, clause(100)), output);
        var b = QueryItem.decision("minimal", target(Inheritance.SELF, TypeFallback.ALLOW, clause(100)), OutputSpec.minimal());
        var result = execute(a, b);
        assertThat(decision(result, 0).outcome()).isEqualTo(DecisionResult.Decision.ALLOW);
        assertThat(decision(result, 1).outcome()).isEqualTo(DecisionResult.Decision.ALLOW);
        assertThat(decision(result, 0).details().effectiveOperations())
            .extracting(ResultDetails.EffectiveOperationEntry::operationCode).containsExactly("UPDATE");
        verify(operations).selectByTenantAndResourceTypes(1L, Set.of(1));
        verifyNoMoreInteractions(operations);
        verify(grants, never()).selectInstancePermsByBitsBatch(anyLong(), anySet(), anySet(), anyList());
    }

    @Test
    void should_rejectPartialOrPreservedFacts_whenUsedAsEvaluatedScopes() {
        var key = new TypeOperation("REPORT", "VIEW");
        var preserved = projectedList(scopeOutput(key), Evaluation.preserveSkip(), grant(101, 1, 100L, 2));
        assertThatThrownBy(() -> ScopeCoverageProjector.project(preserved, List.of(key)))
            .isInstanceOf(IllegalArgumentException.class);
        var noRaw = projectedList(OutputSpec.kept(), Evaluation.full(), grant(101, 1, 100L, 2));
        assertThatThrownBy(() -> ScopeCoverageProjector.project(noRaw, List.of(key)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_propagateDescriptionFailure_whenFactsWereAlreadyEvaluated() {
        when(resources.selectValidByIds(1L, Set.of(100L))).thenThrow(new IllegalStateException("description database down"));
        assertThatThrownBy(() -> projectedList(scopeOutput(new TypeOperation("REPORT", "VIEW")),
            Evaluation.full(), grant(101, 1, 100L, 2))).isInstanceOf(IllegalStateException.class)
            .hasMessage("description database down");
    }

    @Test
    void should_rejectTraceAndMissingDirectionBeforeReading_whenOutputContractCannotBeSatisfied() {
        assertThatThrownBy(() -> execute(QueryItem.decision("trace",
            target(Inheritance.SELF, TypeFallback.ALLOW, clause(100)), OutputSpec.full())))
            .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("TRACE");
        var invalid = new OutputSpec(FactDetail.NONE, false, false, false, null, Set.of(), false);
        assertThatThrownBy(() -> execute(QueryItem.decision("invalid",
            target(Inheritance.SELF, TypeFallback.ALLOW, clause(100)), invalid))).isInstanceOf(QueryValidationException.class);
        verifyNoInteractions(grants, types, operations, resources, resourceMapper, subjects);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void should_diagnoseBrokenConditionWithoutNormalizingFacts_whenPreservedForDisplay(boolean declaredConditional) {
        Long conditionId = declaredConditional ? null : 500L;
        var broken = new cn.ac.fage.accessmesh.access.engine.vo.RolePermEntry(101L, 10L, 100L, null,
            1, 2L, null, null, "MANUAL", true, conditionId, declaredConditional, null, false);
        when(cache.getBatch(eq(AccessCacheCatalog.ROLE_PERM_SNAPSHOT), eq(1L), anySet()))
            .thenReturn(Map.of(10L, List.of(broken)));
        var logger = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getLogger(CandidateEvaluator.class);
        List<String> diagnostics = new ArrayList<>();
        var appender = new org.apache.logging.log4j.core.appender.AbstractAppender("condition-reference-test", null,
            null, true, org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY) {
            @Override public void append(org.apache.logging.log4j.core.LogEvent event) {
                diagnostics.add(event.getMessage().getFormattedMessage());
            }
        };
        appender.start(); logger.addAppender(appender);
        try {
            var output = new OutputSpec(FactDetail.RAW_AND_KEPT, true, true, true, PresentationExpansion.NONE, Set.of(), false);
            var preserved = projectedList(output, Evaluation.preserveSkip());
            assertThat(preserved.details().stageFacts().getFirst().retainedAfterEvaluation()).singleElement().satisfies(f -> {
                assertThat(f.hasCondition()).isEqualTo(declaredConditional);
                assertThat(f.conditionId()).isEqualTo(conditionId);
            });
            assertThat(diagnostics).anyMatch(message -> message.contains("Inconsistent condition reference"));
            var evaluated = projectedList(output, Evaluation.full());
            assertThat(evaluated.details().stageFacts().getFirst().retainedAfterEvaluation()).isEmpty();
            assertThat(evaluated.details().effectiveOperations()).isEmpty();
        } finally {
            logger.removeAppender(appender); appender.stop();
        }
    }

    @Test
    void should_allowIndependentTargetsButDenyTheirUnion_whenMutuallyExclusiveGrantsCoverView() {
        mutex();
        instanceRows.add(grant(101, 1, 100L, 2)); instanceRows.add(grant(102, 1, 200L, 4));
        QueryResult result = execute(item("x", clause(100)), item("y", clause(200)), item("both", clause(100), clause(200)));
        assertThat(result.orderedResults()).extracting(ItemResult::key).containsExactly("x", "y", "both");
        assertThat(decision(result, 0).outcome()).isEqualTo(DecisionResult.Decision.ALLOW);
        assertThat(decision(result, 1).outcome()).isEqualTo(DecisionResult.Decision.ALLOW);
        assertThat(decision(result, 2).reason()).isEqualTo(DecisionResult.Reason.CONDITION_NOT_MET_OR_CONFLICT);
        assertThat(decision(result, 2).details().matchedPermissionIds()).isEmpty();
        verifyNoInteractions(audit);
    }

    @Test
    void should_denySameTargetWithBothEndpoints_whenFirstGrantAloneWouldAllow() {
        mutex(); instanceRows.add(grant(101, 1, 100L, 2)); instanceRows.add(grant(102, 1, 100L, 4));
        assertThat(decision(execute(item("x", clause(100))), 0).reason()).isEqualTo(DecisionResult.Reason.CONDITION_NOT_MET_OR_CONFLICT);
    }

    @Test
    void should_removeFailedConditionBeforeMutex_whenOnlyOtherEndpointRemains() {
        mutex(); instanceRows.add(grant(101, 1, 100L, 2));
        RoleResourcePermission conditional = grant(102, 1, 100L, 4); conditional.setConditionId(500L); instanceRows.add(conditional);
        assertThat(decision(execute(item("x", clause(100))), 0).details().matchedPermissionIds()).containsExactly(101L);
        verify(conditionMapper).selectValidByIds(1L, Set.of(500L));
    }

    @Test
    void should_ignoreInstancesAndDependentScopeAll_whenCheckingTypeLevel() {
        instanceRows.add(grant(101, 1, 100L, 2));
        RoleResourcePermission child = grant(102, 1, null, 2); child.setDependOn(999L); scopeRows.add(child);
        QueryItem type = QueryItem.decision("type", new TypeLevel(List.of(new TypeOperation("REPORT", "VIEW"))), OutputSpec.minimal());
        assertThat(decision(execute(type), 0).reason()).isEqualTo(DecisionResult.Reason.NO_PERMISSION);
        verify(grants, never()).selectInstancePermsByBitsBatch(anyLong(), anySet(), anySet(), anyList());
    }

    @Test
    void should_distinguishTypeSelectionFromTargetParentExclusion_whenOnlyDependentScopeAllExists() {
        RoleResourcePermission child = grant(102, 1, null, 2); child.setDependOn(999L); scopeRows.add(child);
        QueryResult result = execute(
            QueryItem.decision("type", new TypeLevel(List.of(new TypeOperation("REPORT", "VIEW"))), OutputSpec.minimal()),
            QueryItem.decision("target", target(Inheritance.SELF, TypeFallback.ALLOW, clause(100)), OutputSpec.minimal()));
        assertThat(decision(result, 0).reason()).isEqualTo(DecisionResult.Reason.NO_PERMISSION);
        assertThat(decision(result, 1).reason()).isEqualTo(DecisionResult.Reason.DEPENDENT_NOT_IN_PARENT_CONTEXT);
    }

    @Test
    void should_skipUnknownInstanceResolution_whenScopeAllSufficesForMinimalDecision() {
        scopeRows.add(grant(101, 1, null, 2));
        TargetClause unknown = new TargetClause(new TypeOperation("REPORT", "VIEW"), new ByCode("unknown", null, null));
        QueryItem item = QueryItem.decision("scope", target(Inheritance.SELF, TypeFallback.ALLOW, unknown), OutputSpec.minimal());
        assertThat(decision(execute(item), 0).outcome()).isEqualTo(DecisionResult.Decision.ALLOW);
        verifyNoInteractions(resources, resourceMapper, roleMapper);
        verify(grants, never()).selectInstancePermsByBitsBatch(anyLong(), anySet(), anySet(), anyList());
    }

    @Test
    void should_notReadScopeAll_whenFallbackDisallowed() {
        scopeRows.add(grant(101, 1, null, 2));
        assertThat(decision(execute(item("x", clause(100))), 0).reason()).isEqualTo(DecisionResult.Reason.NO_PERMISSION);
        verify(grants, never()).selectScopeAllPermsByBitsBatch(anyLong(), anySet(), anyList());
    }

    @Test
    void should_continueInstanceAndPreserveFailurePriority_whenScopeConditionFails() {
        RoleResourcePermission scope = grant(101, 1, null, 2); scope.setConditionId(500L); scopeRows.add(scope);
        instanceRows.add(grant(102, 1, 100L, 2));
        QueryResult result = execute(
            QueryItem.decision("exists", target(Inheritance.SELF, TypeFallback.ALLOW, clause(100)), OutputSpec.minimal()),
            QueryItem.decision("unknown", target(Inheritance.SELF, TypeFallback.ALLOW,
                new TargetClause(new TypeOperation("REPORT", "VIEW"), new ByCode("unknown", null, null))), OutputSpec.minimal()));
        assertThat(decision(result, 0).outcome()).isEqualTo(DecisionResult.Decision.ALLOW);
        assertThat(decision(result, 1).reason()).isEqualTo(DecisionResult.Reason.CONDITION_NOT_MET_OR_CONFLICT);
        verify(conditionMapper, times(1)).selectValidByIds(1L, Set.of(500L));
    }

    @Test
    void should_keepSelfSeparateFromOtherItemsClosure_whenMixedInheritanceRequested() {
        mutex(); instanceRows.add(grant(101, 1, 100L, 2)); instanceRows.add(grant(102, 1, 200L, 4));
        ResourceEntityMapper.AncestorClosureResult ancestor = new ResourceEntityMapper.AncestorClosureResult();
        ancestor.setTargetId(100L); ancestor.setClosureId(200L);
        when(resourceMapper.selectSelfAndAncestorClosureBatch(1L, Set.of(100L))).thenReturn(List.of(ancestor));
        QueryResult result = execute(item("self", clause(100)), QueryItem.decision("parents",
            target(Inheritance.SELF_AND_ANCESTORS, TypeFallback.DISALLOW, clause(100)), OutputSpec.minimal()));
        assertThat(decision(result, 0).outcome()).isEqualTo(DecisionResult.Decision.ALLOW);
        assertThat(decision(result, 1).reason()).isEqualTo(DecisionResult.Reason.CONDITION_NOT_MET_OR_CONFLICT);
        verify(resourceMapper, times(1)).selectSelfAndAncestorClosureBatch(anyLong(), anySet());
    }

    @Test
    void should_restoreClausePairs_whenSqlLoadsCrossTypeAndOperationSuperset() {
        // REPORT.UPDATE 不覆盖 USER.VIEW，USER.VIEW 也不满足该 clause 的 UPDATE。
        instanceRows.add(grant(101, 1, 100L, 4)); instanceRows.add(grant(102, 2, 200L, 2));
        QueryResult result = execute(item("pair",
            new TargetClause(new TypeOperation("REPORT", "VIEW"), new ByEntityId(300)),
            new TargetClause(new TypeOperation("USER", "UPDATE"), new ByEntityId(200))),
            item("load", clause(100)));
        assertThat(decision(result, 0).reason()).isEqualTo(DecisionResult.Reason.NO_PERMISSION);
        assertThat(decision(result, 1).outcome()).isEqualTo(DecisionResult.Decision.ALLOW);
    }

    @Test
    void should_denyUnknownItemsWithoutFailingOthers_whenTypeOperationOrCodeMissing() {
        instanceRows.add(grant(101, 1, 100L, 2));
        QueryResult result = execute(item("good", clause(100)), item("type", new TargetClause(new TypeOperation("missing", "VIEW"), new ByEntityId(100))),
            item("op", new TargetClause(new TypeOperation("REPORT", "missing"), new ByEntityId(100))),
            item("code", new TargetClause(new TypeOperation("REPORT", "VIEW"), new ByCode("missing", null, null))));
        assertThat(decision(result, 0).outcome()).isEqualTo(DecisionResult.Decision.ALLOW);
        assertThat(result.orderedResults().subList(1, 4)).allSatisfy(r ->
            assertThat(((DecisionResult) r).reason()).isEqualTo(DecisionResult.Reason.NO_PERMISSION));
    }

    @Test
    void should_evaluateMutexOnlyAfterAllSqlChunks_whenOneItemSpansBatchBoundary() {
        mutex(); instanceRows.add(grant(101, 1, 1L, 2)); instanceRows.add(grant(102, 1, 501L, 4));
        TargetClause[] clauses = LongStream.rangeClosed(1, 501).mapToObj(QueryStagesTest::clause).toArray(TargetClause[]::new);
        assertThat(decision(execute(item("large", clauses)), 0).reason()).isEqualTo(DecisionResult.Reason.CONDITION_NOT_MET_OR_CONFLICT);
        verify(grants, times(2)).selectInstancePermsByBitsBatch(eq(1L), anySet(), anySet(), anyList());
    }

    @Test
    void should_collectBothStagesIndependently_whenFactsRequestedDespiteScopeSuccess() {
        mutex(); scopeRows.add(grant(101, 1, null, 2)); instanceRows.add(grant(102, 1, 100L, 4));
        var selection = target(Inheritance.SELF, TypeFallback.ALLOW, clause(100));
        QueryResult result = execute(QueryItem.facts("facts", selection, Evaluation.full(), OutputSpec.rawAndKept()),
            QueryItem.decision("decision", selection, OutputSpec.minimal()));
        GrantSetResult facts = (GrantSetResult) result.orderedResults().getFirst();
        assertThat(facts.collectionStatus()).isEqualTo(GrantSetResult.CollectionStatus.PRESENT);
        assertThat(facts.coverage().requestedSelectionComplete()).isTrue();
        assertThat(facts.details().stageFacts()).extracting(StageFacts::stage).containsExactly(Stage.TYPE_GRANT, Stage.INSTANCE);
        assertThat(facts.details().matchedPermissionIds()).containsExactly(101L, 102L);
        assertThat(facts.details().loadedSections()).contains(ResultDetails.DetailSection.FACTS_RAW, ResultDetails.DetailSection.FACTS_KEPT);
        assertThatThrownBy(() -> facts.details().stageFacts().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> facts.details().stageFacts().getFirst().rawAfterContext().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(decision(result, 1).coverage().skippedStages()).containsEntry(Stage.INSTANCE, EvaluationCoverage.SkipReason.SUFFICIENT_DECISION);
        assertThat(decision(result, 1).coverage().requestedSelectionComplete()).isFalse();
        assertThat(decision(result, 1).details().stageFacts()).isEmpty();
    }

    @Test
    void should_keepRawAfterBindingAndSeparateEvaluationPolicies_whenFactsShareCandidates() {
        mutex(); instanceRows.add(grant(101, 1, 100L, 2));
        RoleResourcePermission failed = grant(102, 1, 100L, 4); failed.setConditionId(500L); instanceRows.add(failed);
        RoleResourcePermission child = grant(103, 1, 100L, 2); child.setDependOn(999L); instanceRows.add(child);
        var selection = target(Inheritance.SELF, TypeFallback.DISALLOW, clause(100), clause(100));
        QueryResult result = execute(
            QueryItem.facts("full", selection, Evaluation.full(), OutputSpec.rawAndKept()),
            QueryItem.facts("preserve", selection, Evaluation.preserveSkip(), OutputSpec.kept()),
            QueryItem.facts("mutex", selection, Evaluation.preserveEnforce(), OutputSpec.rawAndKept()));
        var full = ((GrantSetResult) result.orderedResults().get(0)).details().stageFacts().getFirst();
        assertThat(full.rawAfterContext()).extracting(GrantFact::permissionId).containsExactly(101L, 102L);
        assertThat(full.retainedAfterEvaluation()).extracting(GrantFact::permissionId).containsExactly(101L);
        var preserve = (GrantSetResult) result.orderedResults().get(1);
        assertThat(preserve.details().stageFacts().getFirst().rawAfterContext()).isEmpty();
        assertThat(preserve.details().matchedPermissionIds()).containsExactly(101L, 102L);
        assertThat(preserve.coverage().conditions()).isEqualTo(EvaluationCoverage.ConditionCoverage.PRESERVED);
        assertThat(preserve.coverage().permissionMutex()).isEqualTo(EvaluationCoverage.MutexCoverage.SKIPPED);
        assertThat(((GrantSetResult) result.orderedResults().get(2)).collectionStatus()).isEqualTo(GrantSetResult.CollectionStatus.FILTERED_EMPTY);
        verify(conditionMapper, times(1)).selectValidByIds(1L, Set.of(500L));
    }

    @Test
    void should_notLoadConditionRules_whenAllItemsPreserveConditions() {
        RoleResourcePermission row = grant(101, 1, 100L, 2); row.setConditionId(500L); instanceRows.add(row);
        var result = execute(QueryItem.facts("facts", target(Inheritance.SELF, TypeFallback.DISALLOW, clause(100)),
            Evaluation.preserveSkip(), OutputSpec.kept()));
        assertThat(((GrantSetResult) result.orderedResults().getFirst()).details().matchedPermissionIds()).containsExactly(101L);
        verifyNoInteractions(conditionMapper, rules);
    }

    @Test
    void should_useFreshDefinitionsForMutexAndCachedDefinitionsOnlyForMasks_whenSourcesDisagree() {
        mutex();
        // 缓存操作 UPDATE 的 ID 与当前定义不同，仍覆盖 VIEW；互斥必须认数据库 ID 12。
        when(cache.getBatch(AccessCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE, 1L,
            Set.of(AccessCacheCatalog.operationPermissionsByTypeKey(1))))
            .thenReturn(Map.of(AccessCacheCatalog.operationPermissionsByTypeKey(1),
                Map.of(11L, op(11, 1, "VIEW", 2, 0), 99L, op(99, 1, "UPDATE", 4, 2))));
        instanceRows.add(grant(101, 1, 100L, 2)); instanceRows.add(grant(102, 1, 100L, 4));
        assertThat(decision(execute(item("x", clause(100))), 0).reason()).isEqualTo(DecisionResult.Reason.CONDITION_NOT_MET_OR_CONFLICT);
        verify(operations, times(1)).selectByTenantAndResourceTypes(1L, Set.of(1));
        verify(grants, never()).selectValidByRoleIds(anyLong(), anySet());
        verify(cache, never()).getBatch(eq(AccessCacheCatalog.ROLE_PERM_SNAPSHOT), anyLong(), anySet());
    }

    @Test
    void should_honorCachedCoverageRatherThanFreshInheritance_whenOrdinaryMaskCacheIsHot() {
        when(cache.getBatch(eq(AccessCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE), eq(1L), anySet()))
            .thenReturn(Map.of(AccessCacheCatalog.operationPermissionsByTypeKey(1),
                Map.of(11L, op(11, 1, "VIEW", 2, 0), 12L, op(12, 1, "UPDATE", 4, 0))));
        instanceRows.add(grant(101, 1, 100L, 4));
        assertThat(decision(execute(item("x", clause(100))), 0).reason()).isEqualTo(DecisionResult.Reason.NO_PERMISSION);
    }

    @Test
    void should_rememberUnknownOperationsAndResourcesAcrossItems_whenKeysRepeat() {
        TargetClause missing = new TargetClause(new TypeOperation("REPORT", "missing"), new ByCode("missing", null, null));
        TargetClause missingCode = new TargetClause(new TypeOperation("REPORT", "VIEW"), new ByCode("missing", null, null));
        execute(item("a", missing), item("b", missing), item("c", missingCode), item("d", missingCode));
        verify(types, times(1)).batchResolveTypeValues(1L, "resource_type", Set.of("REPORT"));
        verify(resources, times(1)).selectByTypesAndCodesAndCodeTypes(1L, Set.of(1), Set.of("missing"), Set.of("default"));
        // fresh 与冷 mask 各有独立数据库读取；重复项与互斥不增加第三次读取。
        verify(operations, times(2)).selectByTenantAndResourceTypes(1L, Set.of(1));
        verifyNoInteractions(conditionMapper, resourceMapper);
    }

    @Test
    void should_discardPreviousReadMemory_whenNewExecuteFollowsWrite() {
        assertThat(decision(execute(item("x", clause(100))), 0).outcome()).isEqualTo(DecisionResult.Decision.DENY);
        instanceRows.add(grant(101, 1, 100L, 2));
        QueryResult first = execute(item("x", clause(100)));
        instanceRows.clear();
        QueryResult second = execute(item("x", clause(100)));
        assertThat(decision(first, 0).outcome()).isEqualTo(DecisionResult.Decision.ALLOW);
        assertThat(decision(second, 0).outcome()).isEqualTo(DecisionResult.Decision.DENY);
        assertThat(first.executionId()).isNotEqualTo(second.executionId());
    }

    @Test
    void should_preserveTechnicalFailureAndReleaseMemory_whenLaterStageReadFails() {
        scopeRows.add(grant(101, 1, null, 2));
        var failure = new org.springframework.dao.DataAccessResourceFailureException("db unavailable");
        when(grants.selectInstancePermsByBitsBatch(eq(1L), anySet(), anySet(), anyList())).thenThrow(failure);
        var facts = QueryItem.facts("facts", target(Inheritance.SELF, TypeFallback.ALLOW, clause(100)), Evaluation.full(), OutputSpec.kept());
        assertThatThrownBy(() -> execute(facts)).isSameAs(failure);
        when(grants.selectInstancePermsByBitsBatch(eq(1L), anySet(), anySet(), anyList())).thenReturn(List.of(grant(102, 1, 100L, 2)));
        assertThat(((GrantSetResult) execute(facts).orderedResults().getFirst()).details().matchedPermissionIds()).containsExactly(101L, 102L);
        verify(grants, times(2)).selectScopeAllPermsByBitsBatch(eq(1L), anySet(), anyList());
    }

    @Test
    void should_filterUserRolesOnceWithoutNotification_butKeepExplicitRoles() {
        when(subjects.resolveEffectiveRoles(1L, 1000L)).thenReturn(Set.of(10L, 20L));
        when(cache.get(AccessCacheCatalog.ROLE_MUTEX_RULE, 1L, "all")).thenReturn("[{\"first\":10,\"second\":20}]");
        instanceRows.add(grant(101, 1, 100L, 2));
        var user = engine.execute(new QueryRequest(1L, new User(1000), CallerContext.of(null), ReadOptions.defaults(),
            List.of(item("a", clause(100)), item("b", clause(100)))));
        assertThat(user.orderedResults()).allSatisfy(r -> assertThat(((DecisionResult) r).reason()).isEqualTo(DecisionResult.Reason.NO_ROLE));
        verify(subjects, times(1)).resolveEffectiveRoles(1L, 1000L);
        assertThat(decision(execute(item("explicit", clause(100))), 0).outcome()).isEqualTo(DecisionResult.Decision.ALLOW);
        verifyNoInteractions(audit);
    }

    @Test
    void should_preserveResolvedUserRoleOrder_whenPassingRolesToBatchedGrantReads() {
        when(cache.get(AccessCacheCatalog.ROLE_MUTEX_RULE, 1L, "all")).thenReturn("[]");
        instanceRows.add(grant(101, 1, 100L, 2));
        // 两个相反输入序列避免依赖 SetN 的 JVM 随机迭代起点：旧复制至少会丢失其中一个顺序。
        for (List<Long> order : List.of(List.of(10L, 20L, 30L), List.of(30L, 20L, 10L))) {
            when(subjects.resolveEffectiveRoles(1L, 1000L)).thenReturn(new LinkedHashSet<>(order));
            clearInvocations(grants);
            QueryResult result = engine.execute(new QueryRequest(1L, new User(1000L), CallerContext.of(null),
                ReadOptions.defaults(), List.of(item("ordered", clause(100)))));
            assertThat(decision(result, 0).outcome()).isEqualTo(DecisionResult.Decision.ALLOW);
            org.mockito.ArgumentCaptor<Set<Long>> captured = org.mockito.ArgumentCaptor.forClass(Set.class);
            verify(grants).selectInstancePermsByBitsBatch(eq(1L), captured.capture(), eq(Set.of(100L)), anyList());
            assertThat(captured.getValue()).containsExactlyElementsOf(order);
        }
    }

    @Test
    void should_notReadOutputOnlyKeys_whenMinimalOutputNamesExtraOperations() {
        scopeRows.add(grant(101, 1, null, 2));
        var output = new OutputSpec(FactDetail.NONE, false, false, false, PresentationExpansion.NONE, Set.of(new TypeOperation("USER", "UPDATE")), false);
        assertThat(decision(execute(QueryItem.decision("x", new TypeLevel(List.of(new TypeOperation("REPORT", "VIEW"))), output)), 0)
            .outcome()).isEqualTo(DecisionResult.Decision.ALLOW);
        verify(types).batchResolveTypeValues(1L, "resource_type", Set.of("REPORT"));
        verifyNoInteractions(resources, roleMapper);
    }

    @Test
    void should_failExplicitly_whenTraceOutputRequestedBefore088() {
        assertThatThrownBy(() -> execute(QueryItem.facts("full", target(Inheritance.SELF, TypeFallback.ALLOW, clause(100)),
            Evaluation.full(), OutputSpec.full()))).isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("T-PERM-088");
        verifyNoInteractions(grants, operations);
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 11})
    void should_shareConditionReadTokenAndPinnedClock_whenStagesDiscoverDifferentConditions(int secondReadSeconds) throws Exception {
        AtomicLong nanos = new AtomicLong();
        DistributedCacheStore store = mock(DistributedCacheStore.class);
        CacheService timed = new DefaultCacheService(null, store, null, new CacheProperties(), null, nanos::get);
        when(cache.beginRead(AccessCacheCatalog.CONDITION_RULES)).thenAnswer(i -> timed.beginRead(AccessCacheCatalog.CONDITION_RULES));
        doAnswer(i -> {
            CacheReadToken<JsonNode> token = i.getArgument(0);
            timed.putBatch(token, i.getArgument(1), i.getArgument(2));
            return null;
        }).when(cache).putBatch(any(CacheReadToken.class), eq(1L), anyMap());
        RoleResourcePermission scope = grant(101, 1, null, 2); scope.setConditionId(501L); scopeRows.add(scope);
        RoleResourcePermission instance = grant(102, 1, 100L, 2); instance.setConditionId(502L); instanceRows.add(instance);
        when(conditionMapper.selectValidByIds(eq(1L), anySet())).thenAnswer(i -> {
            Set<Long> ids = i.getArgument(1);
            nanos.set(Duration.ofSeconds(ids.contains(501L) ? 3 : secondReadSeconds).toNanos());
            return ids.stream().map(id -> condition(id, true,
                "{\"logic\":\"AND\",\"items\":[{\"type\":\"DATE_RANGE\",\"params\":{\"start\":\"2004-01-02\",\"end\":\"2004-01-02\"}}]}")).toList();
        });
        var result = execute(QueryItem.facts("facts", target(Inheritance.SELF, TypeFallback.ALLOW, clause(100)), Evaluation.full(), OutputSpec.kept()));
        assertThat(((GrantSetResult) result.orderedResults().getFirst()).details().matchedPermissionIds()).containsExactly(101L, 102L);
        assertThat(result.evaluatedAt().toString()).isEqualTo("2004-01-02T02:00");
        verify(cache, times(1)).beginRead(AccessCacheCatalog.CONDITION_RULES);
        verify(store).putBatch(eq(AccessCacheCatalog.CONDITION_RULES), anyMap(), eq(Duration.ofSeconds(7)));
        if (secondReadSeconds < 10) {
            verify(store).putBatch(eq(AccessCacheCatalog.CONDITION_RULES), anyMap(), eq(Duration.ofSeconds(2)));
        }
        verify(store, times(secondReadSeconds < 10 ? 2 : 1))
            .putBatch(eq(AccessCacheCatalog.CONDITION_RULES), anyMap(), any(Duration.class));
    }

    @Test
    void should_reuseHotConditionAcrossStages_whenSameConditionAppearsInScopeAndInstance() throws Exception {
        JsonNode rule = new ObjectMapper().readTree("{\"logic\":\"AND\",\"items\":[{\"type\":\"DATE_RANGE\",\"params\":{\"start\":\"2004-01-02\",\"end\":\"2004-01-02\"}}]}");
        when(cache.getBatch(AccessCacheCatalog.CONDITION_RULES, 1L, Set.of(501L))).thenReturn(Map.of(501L, rule));
        RoleResourcePermission scope = grant(101, 1, null, 2); scope.setConditionId(501L); scopeRows.add(scope);
        RoleResourcePermission instance = grant(102, 1, 100L, 2); instance.setConditionId(501L); instanceRows.add(instance);
        var result = execute(QueryItem.facts("facts", target(Inheritance.SELF, TypeFallback.ALLOW, clause(100)), Evaluation.full(), OutputSpec.kept()));
        assertThat(((GrantSetResult) result.orderedResults().getFirst()).details().matchedPermissionIds()).containsExactly(101L, 102L);
        verify(cache, times(1)).getBatch(AccessCacheCatalog.CONDITION_RULES, 1L, Set.of(501L));
        verify(cache, never()).beginRead(AccessCacheCatalog.CONDITION_RULES);
        verifyNoInteractions(conditionMapper);
    }

    static ParentRequirement reportParent() {
        return new ParentRequirement("REPORT", new ByEntityId(200), Set.of("VIEW"));
    }

    static QueryItem childItem(String key, long entity) {
        return QueryItem.decision(key, new TargetSet(List.of(clause(entity)), Inheritance.SELF,
            TypeFallback.DISALLOW, reportParent()), OutputSpec.minimalWithMatchIds());
    }

    static RoleResourcePermission dependent(long id, Long entity, long parentId) {
        RoleResourcePermission row = grant(id, 1, entity, 2);
        row.setDependOn(parentId);
        return row;
    }

    GrantSetResult listFacts(ParentRequirement parent, Evaluation evaluation, ListGrantRead source) {
        return (GrantSetResult) engine.execute(new QueryRequest(1L, new Roles(Set.of(10L)),
            CallerContext.of("127.0.0.1"), new ReadOptions(source), List.of(
                QueryItem.grantListFacts("list", parent, evaluation, OutputSpec.rawAndKept()))))
            .orderedResults().getFirst();
    }

    @Test
    void should_skipParent_whenOnlyIndependentMainGrantIsSelected() {
        instanceRows.add(grant(101, 1, 100L, 2));
        var result = decision(execute(childItem("child", 100)), 0);
        assertThat(result.details().matchedPermissionIds()).containsExactly(101L);
        assertThat(result.coverage().parentCheck()).isEqualTo(EvaluationCoverage.ParentCheckCoverage.NOT_TRIGGERED);
        verify(grants, never()).selectScopeAllPermsByBitsBatch(anyLong(), anySet(), anyList());
        verify(grants, times(1)).selectInstancePermsByBitsBatch(eq(1L), anySet(), eq(Set.of(100L)), anyList());
    }

    @Test
    void should_preserveMainGrant_whenParentFailsAndDependentGrantIsExcluded() {
        instanceRows.add(grant(101, 1, 100L, 2));
        instanceRows.add(dependent(102, 100L, 999));
        var result = decision(execute(childItem("child", 100)), 0);
        assertThat(result.details().matchedPermissionIds()).containsExactly(101L);
        assertThat(result.coverage().parentCheck()).isEqualTo(EvaluationCoverage.ParentCheckCoverage.FAILED);
    }

    @Test
    void should_bindOnlyScopeParentIds_withoutExpandingParentInstanceOrRequiringPublicMatchIds() {
        scopeRows.add(grant(201, 1, null, 2));
        instanceRows.add(grant(202, 1, 200L, 2));
        instanceRows.add(dependent(101, 100L, 201));
        instanceRows.add(dependent(102, 100L, 202));
        var selection = new TargetSet(List.of(clause(100)), Inheritance.SELF, TypeFallback.DISALLOW, reportParent());
        var output = new OutputSpec(FactDetail.RAW_AND_KEPT, false, false, false, PresentationExpansion.NONE, Set.of(), false);
        var result = (GrantSetResult) execute(QueryItem.facts("child", selection, Evaluation.full(), output))
            .orderedResults().getFirst();
        assertThat(result.details().stageFacts().getFirst().rawAfterContext()).extracting(GrantFact::permissionId).containsExactly(101L);
        assertThat(result.details().matchedPermissionIds()).isEmpty();
        assertThat(result.coverage().parentCheck()).isEqualTo(EvaluationCoverage.ParentCheckCoverage.PASSED);
        verify(grants, never()).selectInstancePermsByBitsBatch(eq(1L), anySet(), eq(Set.of(200L)), anyList());
    }

    @Test
    void should_computeSharedParentOnce_andMarkEveryDependentItem() {
        mutex();
        instanceRows.add(grant(201, 1, 200L, 2));
        instanceRows.add(grant(202, 1, 200L, 4));
        instanceRows.add(dependent(101, 100L, 201));
        instanceRows.add(dependent(102, 300L, 201));
        var result = execute(childItem("first", 100), childItem("second", 300));
        assertThat(result.orderedResults()).allSatisfy(item -> {
            assertThat(((DecisionResult) item).reason()).isEqualTo(DecisionResult.Reason.DEPENDENT_NOT_IN_PARENT_CONTEXT);
            assertThat(((DecisionResult) item).coverage().parentCheck()).isEqualTo(EvaluationCoverage.ParentCheckCoverage.FAILED);
        });
        verify(grants, times(1)).selectInstancePermsByBitsBatch(eq(1L), anySet(), eq(Set.of(200L)), anyList());
        verify(grants, times(1)).selectScopeAllPermsByBitsBatch(eq(1L), anySet(), anyList());
        verifyNoInteractions(audit);
    }

    @Test
    void should_evaluateParentFully_whenRootPreservesConditionsAndSkipsMutex() {
        RoleResourcePermission parent = grant(201, 1, 200L, 2);
        parent.setConditionId(500L); instanceRows.add(parent);
        instanceRows.add(dependent(101, 100L, 201));
        var selection = new TargetSet(List.of(clause(100)), Inheritance.SELF, TypeFallback.DISALLOW, reportParent());
        var result = (GrantSetResult) execute(QueryItem.facts("child", selection, Evaluation.preserveSkip(), OutputSpec.rawAndKept()))
            .orderedResults().getFirst();
        assertThat(result.collectionStatus()).isEqualTo(GrantSetResult.CollectionStatus.NO_MATCH);
        assertThat(result.coverage().parentCheck()).isEqualTo(EvaluationCoverage.ParentCheckCoverage.FAILED);
        verify(conditionMapper).selectValidByIds(1L, Set.of(500L));
    }

    @Test
    void should_rejectEmptyParentOperations_beforeAnyPermissionRead() {
        var parent = new ParentRequirement("REPORT", new ByEntityId(200), Set.of());
        assertThatThrownBy(() -> listFacts(parent, Evaluation.preserveSkip(), ListGrantRead.DATABASE))
            .isInstanceOf(QueryValidationException.class);
        verifyNoInteractions(grants, resources, cache, subjects);
    }

    @Test
    void should_keepDependentRowsInUnboundList_andNeverTouchSnapshotInDatabaseMode() {
        when(grants.selectValidByRoleIds(1L, Set.of(10L)))
            .thenReturn(List.of(grant(101, 1, 100L, 2), dependent(102, 300L, 999)));
        var result = listFacts(null, Evaluation.preserveSkip(), ListGrantRead.DATABASE);
        assertThat(result.details().matchedPermissionIds()).containsExactly(101L, 102L);
        assertThat(result.coverage().requestedSelectionComplete()).isTrue();
        assertThat(result.coverage().completedStages()).containsExactly(Stage.GRANT_LIST);
        verifyNoInteractions(cache, operations, resources, resourceMapper);
    }

    @Test
    void should_applyMutexToWholeList_beforeAnyConsumerFiltersOneResource() {
        mutex();
        // 子行同样参与清单互斥；若提前隐藏它，主授权 101 会错误复活。
        RoleResourcePermission other = dependent(102, 300L, 999); other.setGrantedBits(4L);
        when(grants.selectValidByRoleIds(1L, Set.of(10L))).thenReturn(List.of(grant(101, 1, 100L, 2), other));
        var result = listFacts(null, Evaluation.full(), ListGrantRead.DATABASE);
        assertThat(result.collectionStatus()).isEqualTo(GrantSetResult.CollectionStatus.FILTERED_EMPTY);
        assertThat(result.details().stageFacts().getFirst().rawAfterContext()).extracting(GrantFact::permissionId)
            .containsExactly(101L, 102L);
        assertThat(result.details().stageFacts().getFirst().retainedAfterEvaluation()).isEmpty();
    }

    @Test
    void should_defineRawAfterParentBinding_whenListHasRequiredParent() {
        scopeRows.add(grant(201, 1, null, 2));
        when(grants.selectValidByRoleIds(1L, Set.of(10L))).thenReturn(List.of(
            grant(101, 1, 100L, 2), dependent(102, 300L, 201), dependent(103, 400L, 999)));
        var result = listFacts(reportParent(), Evaluation.preserveSkip(), ListGrantRead.DATABASE);
        assertThat(result.details().stageFacts().getFirst().rawAfterContext()).extracting(GrantFact::permissionId)
            .containsExactly(101L, 102L);
        assertThat(result.details().matchedPermissionIds()).containsExactly(101L, 102L);
    }

    @Test
    void should_notEvaluateParent_whenListSourceIsEmpty() {
        var result = listFacts(reportParent(), Evaluation.full(), ListGrantRead.DATABASE);
        assertThat(result.collectionStatus()).isEqualTo(GrantSetResult.CollectionStatus.NO_MATCH);
        assertThat(result.coverage().parentCheck()).isEqualTo(EvaluationCoverage.ParentCheckCoverage.NOT_TRIGGERED);
        verify(grants, never()).selectScopeAllPermsByBitsBatch(anyLong(), anySet(), anyList());
        verifyNoInteractions(operations, resources, conditionMapper, rules);
    }

    @Test
    void should_returnBothExplicitRolesFacts_withoutApplyingUserRoleMutex() {
        var a = grant(101, 1, 100L, 2);
        var b = grant(102, 1, 300L, 2); b.setAbstractRoleId(20L);
        when(grants.selectValidByRoleIds(1L, Set.of(10L, 20L))).thenReturn(List.of(a, b));
        var result = (GrantSetResult) engine.execute(new QueryRequest(1L, new Roles(Set.of(10L, 20L)), CallerContext.of(null),
            new ReadOptions(ListGrantRead.DATABASE), List.of(QueryItem.grantListFacts("roles", null,
                Evaluation.preserveSkip(), OutputSpec.kept())))).orderedResults().getFirst();
        assertThat(result.details().matchedRoleIds()).containsExactlyInAnyOrder(10L, 20L);
        assertThat(result.details().matchedPermissionIds()).containsExactlyInAnyOrder(101L, 102L);
        verifyNoInteractions(subjects, rules, audit);
    }

    @Test
    void should_stopWholeListAtParentGate_withoutReportingSuccessfulEmptyCollection() {
        var main = grant(101, 1, 100L, 2); main.setConditionId(501L);
        when(grants.selectValidByRoleIds(1L, Set.of(10L))).thenReturn(List.of(main));
        var key = new TypeOperation("REPORT", "VIEW");
        var result = (GrantSetResult) engine.execute(new QueryRequest(1L, new Roles(Set.of(10L)),
            CallerContext.of(null), new ReadOptions(ListGrantRead.DATABASE), List.of(
                QueryItem.grantListFacts("list", reportParent(), Evaluation.full(), scopeOutput(key)))))
            .orderedResults().getFirst();
        assertThat(result.collectionStatus()).isEqualTo(GrantSetResult.CollectionStatus.PARENT_DENIED);
        assertThat(result.coverage().parentCheck()).isEqualTo(EvaluationCoverage.ParentCheckCoverage.FAILED);
        assertThat(result.coverage().requestedSelectionComplete()).isFalse();
        assertThat(result.coverage().completedStages()).isEmpty();
        assertThat(result.coverage().skippedStages()).containsEntry(Stage.GRANT_LIST, EvaluationCoverage.SkipReason.PARENT_DENIED);
        assertThat(result.details().stageFacts()).isEmpty();
        assertThat(result.details().matchedPermissionIds()).isEmpty();
        assertThat(ScopeCoverageProjector.project(result, List.of(key)).getFirst().scopeMode()).isEqualTo(ScopeMode.DENIED);
        verifyNoInteractions(conditionMapper);
    }

    @Test
    void should_enforceParentMutex_whenListPreservesAllItsOwnFacts() {
        mutex();
        instanceRows.add(grant(201, 1, 200L, 2)); instanceRows.add(grant(202, 1, 200L, 4));
        when(grants.selectValidByRoleIds(1L, Set.of(10L))).thenReturn(List.of(grant(101, 1, 100L, 2)));
        var result = listFacts(reportParent(), Evaluation.preserveSkip(), ListGrantRead.DATABASE);
        assertThat(result.collectionStatus()).isEqualTo(GrantSetResult.CollectionStatus.PARENT_DENIED);
        verify(rules, times(1)).selectByConflictType(1L, "PERM_MUTEX");
        verifyNoInteractions(audit);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void should_readOnlyMissingRolesAndCacheUnfilteredFacts_whenSnapshotHeatVaries(int hotRoles) {
        var first = grant(101, 1, 100L, 2);
        var second = dependent(102, 300L, 999); second.setAbstractRoleId(20L);
        var mapper = new RolePermEntryMapper();
        Map<Long, List<cn.ac.fage.accessmesh.access.engine.vo.RolePermEntry>> hot = new LinkedHashMap<>();
        if (hotRoles > 0) hot.put(10L, List.of(mapper.toEntry(first)));
        if (hotRoles > 1) hot.put(20L, List.of(mapper.toEntry(second)));
        when(cache.getBatch(AccessCacheCatalog.ROLE_PERM_SNAPSHOT, 1L, Set.of(10L, 20L))).thenReturn(hot);
        when(grants.selectValidByRoleIds(eq(1L), anySet())).thenAnswer(i -> {
            Set<Long> ids = i.getArgument(1);
            return List.of(first, second).stream().filter(row -> ids.contains(row.getAbstractRoleId())).toList();
        });
        CacheService timed = new DefaultCacheService(null, mock(DistributedCacheStore.class), null, new CacheProperties(), null);
        var token = timed.beginRead(AccessCacheCatalog.ROLE_PERM_SNAPSHOT);
        when(cache.beginRead(AccessCacheCatalog.ROLE_PERM_SNAPSHOT)).thenReturn(token);
        var result = (GrantSetResult) engine.execute(new QueryRequest(1L, new Roles(Set.of(10L, 20L)), CallerContext.of(null),
            ReadOptions.defaults(), List.of(QueryItem.grantListFacts("list", null, Evaluation.preserveSkip(), OutputSpec.kept()))))
            .orderedResults().getFirst();
        assertThat(result.details().matchedPermissionIds()).containsExactlyInAnyOrder(101L, 102L);
        if (hotRoles == 2) {
            verifyNoInteractions(grants);
            verify(cache, never()).beginRead(AccessCacheCatalog.ROLE_PERM_SNAPSHOT);
        } else {
            Set<Long> misses = hotRoles == 0 ? Set.of(10L, 20L) : Set.of(20L);
            var order = inOrder(cache, grants);
            order.verify(cache).beginRead(AccessCacheCatalog.ROLE_PERM_SNAPSHOT);
            order.verify(grants).selectValidByRoleIds(1L, misses);
            order.verify(cache).putBatch(eq(token), eq(1L), argThat(data ->
                data.keySet().equals(misses) && data.get(20L).getFirst().dependOn().equals(999L)));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 11})
    void should_keepFirstSnapshotTokenAcrossSqlChunks_whenDatabaseReadConsumesBudget(int readSeconds) {
        AtomicLong nanos = new AtomicLong();
        DistributedCacheStore store = mock(DistributedCacheStore.class);
        CacheService timed = new DefaultCacheService(null, store, null, new CacheProperties(), null, nanos::get);
        when(cache.beginRead(AccessCacheCatalog.ROLE_PERM_SNAPSHOT)).thenAnswer(i -> timed.beginRead(AccessCacheCatalog.ROLE_PERM_SNAPSHOT));
        doAnswer(i -> {
            CacheReadToken<List<cn.ac.fage.accessmesh.access.engine.vo.RolePermEntry>> token = i.getArgument(0);
            timed.putBatch(token, i.getArgument(1), i.getArgument(2)); return null;
        }).when(cache).putBatch(any(CacheReadToken.class), eq(1L), anyMap());
        int size = cn.ac.fage.accessmesh.access.infrastructure.util.SqlBatches.BATCH_SIZE + 1;
        Set<Long> roles = LongStream.rangeClosed(1, size).boxed().collect(Collectors.toCollection(LinkedHashSet::new));
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        when(grants.selectValidByRoleIds(eq(1L), anySet())).thenAnswer(i -> {
            nanos.set(Duration.ofSeconds(calls.incrementAndGet() == 1 ? 3 : readSeconds).toNanos());
            Set<Long> batch = i.getArgument(1);
            return batch.stream().map(role -> {
                var row = grant(role + 10000, 1, 100L, 2); row.setAbstractRoleId(role); return row;
            }).toList();
        });
        var result = (GrantSetResult) engine.execute(new QueryRequest(1L, new Roles(roles), CallerContext.of(null),
            ReadOptions.defaults(), List.of(QueryItem.grantListFacts("list", null, Evaluation.preserveSkip(), OutputSpec.kept()))))
            .orderedResults().getFirst();
        assertThat(result.details().matchedPermissionIds()).hasSize(size);
        assertThat(calls.get()).isEqualTo(2);
        var order = inOrder(cache, grants);
        order.verify(cache).beginRead(AccessCacheCatalog.ROLE_PERM_SNAPSHOT);
        order.verify(grants, times(2)).selectValidByRoleIds(eq(1L), anySet());
        verify(cache, times(1)).beginRead(AccessCacheCatalog.ROLE_PERM_SNAPSHOT);
        if (readSeconds < 10) {
            verify(store).putBatch(eq(AccessCacheCatalog.ROLE_PERM_SNAPSHOT), anyMap(), eq(Duration.ofSeconds(2)));
        } else {
            verify(store, never()).putBatch(eq(AccessCacheCatalog.ROLE_PERM_SNAPSHOT), anyMap(), any(Duration.class));
        }
    }

    @Test
    void should_keepParentAtSelfAndIgnoreDependentParentRows_whenOnlyAncestorOrNestedGrantExists() {
        instanceRows.add(dependent(101, 100L, 201));
        instanceRows.add(dependent(201, 200L, 999));
        instanceRows.add(grant(202, 1, 400L, 2));
        assertThat(decision(execute(childItem("child", 100)), 0).reason())
            .isEqualTo(DecisionResult.Reason.DEPENDENT_NOT_IN_PARENT_CONTEXT);
        verifyNoInteractions(resourceMapper);
    }

    @Test
    void should_shareClockAndConditionMemory_whenParentFallsBackToInstanceAndChildAlsoEvaluates() {
        var failedScope = grant(200, 1, null, 2); failedScope.setConditionId(500L); scopeRows.add(failedScope);
        var parent = grant(201, 1, 200L, 2); parent.setConditionId(501L); instanceRows.add(parent);
        var child = dependent(101, 100L, 201); child.setConditionId(501L); instanceRows.add(child);
        when(conditionMapper.selectValidByIds(1L, Set.of(501L))).thenReturn(List.of(condition(501, true,
            "{\"logic\":\"AND\",\"items\":[{\"type\":\"DATE_RANGE\",\"params\":{\"start\":\"2004-01-02\",\"end\":\"2004-01-02\"}}]}")));
        var result = decision(execute(childItem("parent", 100)), 0);
        assertThat(result.details().matchedPermissionIds()).containsExactly(101L);
        assertThat(result.coverage().parentCheck()).isEqualTo(EvaluationCoverage.ParentCheckCoverage.PASSED);
        verify(conditionMapper, times(1)).selectValidByIds(1L, Set.of(501L));
        verify(grants).selectInstancePermsByBitsBatch(eq(1L), anySet(), eq(Set.of(200L)), anyList());
        verifyNoInteractions(resourceMapper, audit);
    }

    static PermissionCondition condition(long id, boolean enabled, String rules) {
        PermissionCondition row = new PermissionCondition();
        row.setId(id); row.setTenantId(1L); row.setEnabled(enabled); row.setConditionRules(rules);
        return row;
    }
}
