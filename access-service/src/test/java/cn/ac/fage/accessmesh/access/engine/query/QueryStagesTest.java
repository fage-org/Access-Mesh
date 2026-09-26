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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
        assertThat(decision(execute(type), 0).reason()).isEqualTo(DecisionResult.Reason.DEPENDENT_NOT_IN_PARENT_CONTEXT);
        verify(grants, never()).selectInstancePermsByBitsBatch(anyLong(), anySet(), anySet(), anyList());
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
    void should_notReadOutputOnlyKeys_whenMinimalOutputNamesExtraOperations() {
        scopeRows.add(grant(101, 1, null, 2));
        var output = new OutputSpec(FactDetail.NONE, false, false, false, false, Set.of(new TypeOperation("USER", "UPDATE")), false);
        assertThat(decision(execute(QueryItem.decision("x", new TypeLevel(List.of(new TypeOperation("REPORT", "VIEW"))), output)), 0)
            .outcome()).isEqualTo(DecisionResult.Decision.ALLOW);
        verify(types).batchResolveTypeValues(1L, "resource_type", Set.of("REPORT"));
        verifyNoInteractions(resources, roleMapper);
    }

    @Test
    void should_failExplicitly_whenUnimplementedOutputOrParentRequested() {
        assertThatThrownBy(() -> execute(QueryItem.facts("full", target(Inheritance.SELF, TypeFallback.ALLOW, clause(100)),
            Evaluation.full(), OutputSpec.full()))).isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("087/088");
        var parent = new ParentRequirement("REPORT", new ByEntityId(200), Set.of("VIEW"));
        assertThatThrownBy(() -> execute(QueryItem.decision("parent", new TargetSet(List.of(clause(100)),
            Inheritance.SELF, TypeFallback.ALLOW, parent), OutputSpec.minimal())))
            .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("086");
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

    static PermissionCondition condition(long id, boolean enabled, String rules) {
        PermissionCondition row = new PermissionCondition();
        row.setId(id); row.setTenantId(1L); row.setEnabled(enabled); row.setConditionRules(rules);
        return row;
    }
}
