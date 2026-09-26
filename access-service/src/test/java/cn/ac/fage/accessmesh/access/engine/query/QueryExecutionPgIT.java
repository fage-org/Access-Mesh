package cn.ac.fage.accessmesh.access.engine.query;

import cn.ac.fage.accessmesh.access.characterization.R2BaselineFixture;
import cn.ac.fage.accessmesh.access.engine.core.PermQueryEngine;
import cn.ac.fage.accessmesh.access.engine.core.SubjectDomainService;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.engine.dto.PermQuery;
import cn.ac.fage.accessmesh.access.engine.dto.PermBatchQuery;
import cn.ac.fage.accessmesh.access.engine.dto.PermEvalContext;
import cn.ac.fage.accessmesh.access.engine.util.RolePermEntryMapper;
import cn.ac.fage.accessmesh.access.grant.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.grant.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.access.infrastructure.cache.AccessCacheCatalog;
import cn.ac.fage.accessmesh.access.it.ItInfra;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.resource.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.access.role.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConditionDomainService;
import cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConflictDomainService;
import cn.ac.fage.accessmesh.access.type.service.domain.OperationPermissionDomainService;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import java.util.stream.IntStream;

import static cn.ac.fage.accessmesh.access.characterization.R2BaselineFixture.*;
import static org.assertj.core.api.Assertions.*;

/** 真实 execute 的 PostgreSQL/Redis 验收；独立类库与缓存，动态互斥规则逐例清理。 */
@Tag("testcontainers")
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "spring.config.import=optional:classpath:/test-nacos-dummy.yml",
    "spring.cloud.nacos.config.enabled=false", "spring.cloud.nacos.config.import-check.enabled=false",
    "spring.cloud.nacos.discovery.enabled=false", "accessmesh.sync.scheduler.enabled=false",
    "mybatis-flex.configuration.map-underscore-to-camel-case=true", "logging.level.cn.ac.fage.accessmesh=WARN"
})
class QueryExecutionPgIT {
    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) { ItInfra.register(registry, QueryExecutionPgIT.class); }

    @Autowired TypeResolutionService types;
    @Autowired OperationPermissionDomainService operations;
    @Autowired ResourceEntityDomainService resources;
    @Autowired ResourceEntityMapper resourceMapper;
    @Autowired AbstractRoleMapper roleMapper;
    @Autowired RoleResourcePermissionMapper grants;
    @Autowired RolePermEntryMapper entries;
    @Autowired SubjectDomainService subjects;
    @Autowired PermissionConditionDomainService conditions;
    @Autowired PermissionConflictDomainService conflicts;
    @Autowired PermQueryEngine legacy;
    @Autowired CacheService cache;
    @Autowired JdbcTemplate jdbc;
    @Autowired SqlSessionFactory sessions;
    QueryExecutionEngine engine;
    R2BaselineFixture fixture;
    QueryReadSupportPgIT.SqlCounter counter;
    final List<Long> createdRuleIds = new ArrayList<>();

    GrantSetResult listFacts(long role, ParentRequirement parent, ListGrantRead source, Evaluation evaluation) {
        return (GrantSetResult) engine.execute(new QueryRequest(TENANT, new Roles(Set.of(role)), CallerContext.of(null),
            new ReadOptions(source), List.of(QueryItem.grantListFacts("list", parent, evaluation, OutputSpec.rawAndKept()))))
            .orderedResults().getFirst();
    }

    @Test
    void should_matchLegacyParentOperationSummary_whenScopeAdapterUsesNewResult() {
        var key = new TypeOperation(TYPE_T1_CODE, "VIEW");
        var output = new OutputSpec(FactDetail.RAW_AND_KEPT, true, true, false,
            PresentationExpansion.NONE, Set.of(key), false);
        var parent = new ParentRequirement(TYPE_T1_CODE, new ByCode(CODE_R1, null, null), Set.of("VIEW"));
        var fresh = (GrantSetResult) execute(new User(USER_INST),
            QueryItem.grantListFacts("scopes", parent, Evaluation.full(), output)).orderedResults().getFirst();
        var oldRequest = PermQuery.forScopeQuery(TENANT, USER_INST, Set.of(TYPE_T1_CODE), Set.of("VIEW"));
        oldRequest.setParentResource(TYPE_T1_CODE, CODE_R1, null, Set.of("VIEW"));
        oldRequest.setEvalContext(new PermEvalContext(null, java.time.LocalDateTime.of(2026, 9, 26, 2, 0), java.util.Map.of()));
        var old = legacy.query(oldRequest);
        assertThat(fresh.details().parentCheck().matchedOperationCodes())
            .containsExactly("VIEW").containsExactlyInAnyOrderElementsOf(old.parentMatchedOperationCodes());
        assertThat(fresh.details().loadedSections()).contains(ResultDetails.DetailSection.PARENT_CHECK);
        assertThat(ScopeCoverageProjector.project(fresh, List.of(key)).getFirst().scopeMode()).isEqualTo(ScopeMode.INSTANCE);
    }

    @Test
    @Transactional
    void should_projectRealTreeDirectionsWithoutMutatingGrants_whenParentAndChildPresentationRequested() {
        long role = fixture.insertRoleRow(TENANT, "projection-tree");
        long permission = fixture.insertPermRow(role, TYPE_T1, RES_R1, T1_UPDATE_BIT, false, null);
        var output = new OutputSpec(FactDetail.RAW_AND_KEPT, true, true, true,
            PresentationExpansion.CHILDREN, Set.of(), false);
        var result = (GrantSetResult) execute(new Roles(Set.of(role)),
            QueryItem.grantListFacts("children", null, Evaluation.preserveSkip(), output)).orderedResults().getFirst();
        assertThat(result.details().presentation()).extracting(PresentationEntry::displayedEntityId)
            .contains(RES_R1, RES_R2, RES_R3);
        assertThat(result.details().stageFacts().getFirst().retainedAfterEvaluation()).singleElement()
            .satisfies(f -> { assertThat(f.permissionId()).isEqualTo(permission); assertThat(f.resourceEntityId()).isEqualTo(RES_R1); });
        assertThat(result.details().descriptions().resources()).containsKeys(RES_R1, RES_R2, RES_R3);
        assertThat(result.details().effectiveOperations()).anySatisfy(e -> {
            assertThat(e.displayedEntityId()).isEqualTo(RES_R2);
            assertThat(e.operationCode()).isEqualTo("VIEW");
            assertThat(e.sourcePermissionId()).isEqualTo(permission);
        });
        var cached = cache.get(AccessCacheCatalog.ROLE_PERM_SNAPSHOT, TENANT, role);
        assertThat(cached).singleElement().satisfies(f -> {
            assertThat(f.resourceEntityId()).isEqualTo(RES_R1);
            assertThat(f.grantSource()).isNotEqualTo("INHERITED");
        });
        var parents = new OutputSpec(FactDetail.KEPT, true, true, false, PresentationExpansion.PARENTS, Set.of(), false);
        var childRole = fixture.insertRoleRow(TENANT, "projection-parent");
        fixture.insertPermRow(childRole, TYPE_T1, RES_R2, T1_VIEW_BIT, false, null);
        var parentResult = (GrantSetResult) execute(new Roles(Set.of(childRole)),
            QueryItem.grantListFacts("parents", null, Evaluation.preserveSkip(), parents)).orderedResults().getFirst();
        assertThat(parentResult.details().presentation()).contains(
            new PresentationEntry(parentResult.details().matchedPermissionIds().getFirst(), childRole, RES_R1,
                PresentationEntry.Derivation.PARENT));
        assertThat(parentResult.details().presentation()).extracting(PresentationEntry::displayedEntityId).doesNotContain(RES_R3);
    }

    @Test
    @Transactional
    void should_returnEmptyForDeletedResourceDespiteWarmGrantSnapshot_whenScopeProjectionLoadsCurrentDescriptions() {
        long role = fixture.insertRoleRow(TENANT, "projection-deleted");
        long resource = fixture.insertResourceRow(TYPE_T1, "projection-deleted");
        fixture.insertPermRow(role, TYPE_T1, resource, T1_VIEW_BIT, false, null);
        var key = new TypeOperation(TYPE_T1_CODE, "VIEW");
        var output = new OutputSpec(FactDetail.RAW_AND_KEPT, true, true, false, PresentationExpansion.NONE, Set.of(key), false);
        var item = QueryItem.grantListFacts("scopes", null, Evaluation.full(), output);
        var first = (GrantSetResult) execute(new Roles(Set.of(role)), item).orderedResults().getFirst();
        assertThat(ScopeCoverageProjector.project(first, List.of(key)).getFirst().scopeMode()).isEqualTo(ScopeMode.INSTANCE);
        // 走生产的 Mapper 写通道，使同事务 MyBatis 一级缓存与数据库同步失效。
        // JdbcTemplate 旁路写不会通知该缓存，不能用它模拟应用内的资源删除。
        assertThat(resources.softDeleteBatch(TENANT, List.of(resource), java.time.LocalDateTime.of(2026, 9, 26, 2, 0)))
            .isEqualTo(1);
        var second = (GrantSetResult) execute(new Roles(Set.of(role)), item).orderedResults().getFirst();
        assertThat(second.details().stageFacts().getFirst().retainedAfterEvaluation()).hasSize(1);
        assertThat(ScopeCoverageProjector.project(second, List.of(key)).getFirst().scopeMode()).isEqualTo(ScopeMode.EMPTY);
        assertThat(second.details().descriptions().operations()).isNotEmpty();
    }

    @Test
    void should_bindToDatabaseParentDespiteWarmRoleSnapshot_whenParentGrantIsRevoked() {
        long role = fixture.insertRoleRow(TENANT, "list-parent");
        long parent = fixture.insertPermRow(role, TYPE_T2, null, 2, true, null);
        long child = fixture.insertPermRow(role, TYPE_T1, RES_R1, 2, false, null);
        jdbc.update("UPDATE role_resource_permission SET depend_on=? WHERE id=? AND tenant_id=?", parent, child, TENANT);
        var requirement = new ParentRequirement(TYPE_T2_CODE, new ByEntityId(RES_S1), Set.of("VIEW"));
        var first = listFacts(role, requirement, ListGrantRead.ROLE_SNAPSHOT, Evaluation.preserveSkip());
        assertThat(first.details().matchedPermissionIds()).containsExactlyInAnyOrder(parent, child);
        assertThat(first.coverage().parentCheck()).isEqualTo(EvaluationCoverage.ParentCheckCoverage.PASSED);
        assertThat(cache.get(AccessCacheCatalog.ROLE_PERM_SNAPSHOT, TENANT, role))
            .extracting(cn.ac.fage.accessmesh.access.engine.vo.RolePermEntry::permissionId).containsExactlyInAnyOrder(parent, child);
        // 故意不发缓存失效，证明父阶段独立数据库读取，不能从根清单快照拼出允许。
        jdbc.update("UPDATE role_resource_permission SET delete_flag=id WHERE id=? AND tenant_id=?", parent, TENANT);
        counter.grants.set(0);
        var second = listFacts(role, requirement, ListGrantRead.ROLE_SNAPSHOT, Evaluation.preserveSkip());
        assertThat(second.collectionStatus()).isEqualTo(GrantSetResult.CollectionStatus.PARENT_DENIED);
        assertThat(second.details().matchedPermissionIds()).isEmpty();
        assertThat(counter.grants.get()).as("热清单零授权读取，父 scopeAll/INSTANCE 各读取一次").isEqualTo(2);
    }

    @Test
    void should_preserveRawSnapshotAndListMutexSemantics_whenDependentRowConflictsWithMainRow() {
        fixture.newType(957, "R2LIST");
        long view = fixture.insertOperation(957, "VIEW", 2, 0);
        long update = fixture.insertOperation(957, "UPDATE", 4, 2);
        long role = fixture.insertRoleRow(TENANT, "list-mutex");
        long x = fixture.insertResourceRow(957, "list-x"); long y = fixture.insertResourceRow(957, "list-y");
        long main = fixture.insertPermRow(role, 957, x, 2, false, null);
        long child = fixture.insertPermRow(role, 957, y, 4, false, null);
        jdbc.update("UPDATE role_resource_permission SET depend_on=? WHERE id=? AND tenant_id=?", main, child, TENANT);
        createdRuleIds.add(fixture.insertPermMutexRule(view, update));
        var filtered = listFacts(role, null, ListGrantRead.ROLE_SNAPSHOT, Evaluation.full());
        assertThat(filtered.collectionStatus()).isEqualTo(GrantSetResult.CollectionStatus.FILTERED_EMPTY);
        assertThat(filtered.details().stageFacts().getFirst().rawAfterContext()).extracting(GrantFact::permissionId)
            .containsExactlyInAnyOrder(main, child);
        counter.grants.set(0);
        var raw = listFacts(role, null, ListGrantRead.ROLE_SNAPSHOT, Evaluation.preserveSkip());
        assertThat(raw.details().matchedPermissionIds()).containsExactlyInAnyOrder(main, child);
        assertThat(counter.grants.get()).as("第二次执行热缓存仍是原始事实，未缓存首次筛空结果").isZero();
    }

    @Test
    @Transactional
    void should_readOwnWriteWithoutChangingSnapshot_whenListDatabaseModeSelected() {
        long role = fixture.insertRoleRow(TENANT, "list-database");
        cache.put(AccessCacheCatalog.ROLE_PERM_SNAPSHOT, TENANT, role, List.of());
        long permission = fixture.insertPermRow(role, TYPE_T1, RES_R1, 2, false, null);
        var result = listFacts(role, null, ListGrantRead.DATABASE, Evaluation.preserveSkip());
        assertThat(result.details().matchedPermissionIds()).containsExactly(permission);
        assertThat(cache.get(AccessCacheCatalog.ROLE_PERM_SNAPSHOT, TENANT, role)).isEmpty();
    }

    @AfterEach
    void cleanupRules() {
        createdRuleIds.forEach(id -> jdbc.update("DELETE FROM permission_conflict_rule WHERE id=? AND tenant_id=?", id, TENANT));
    }

    @BeforeEach
    void prepare() {
        fixture = new R2BaselineFixture(jdbc);
        fixture.seedBaselineGraph();
        cache.evictAll(AccessCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE, TENANT);
        cache.evictAll(AccessCacheCatalog.CONDITION_RULES, TENANT);
        cache.evictAll(AccessCacheCatalog.EFFECTIVE_ROLES, TENANT);
        cache.evictAll(AccessCacheCatalog.ROLE_MUTEX_RULE, TENANT);
        cache.evictAll(AccessCacheCatalog.TYPE_VALUE, TENANT);
        counter = sessions.getConfiguration().getInterceptors().stream().filter(QueryReadSupportPgIT.SqlCounter.class::isInstance)
            .map(QueryReadSupportPgIT.SqlCounter.class::cast).findFirst().orElseGet(() -> {
                var installed = new QueryReadSupportPgIT.SqlCounter();
                sessions.getConfiguration().addInterceptor(installed);
                return installed;
            });
        counter.operations.set(0); counter.grants.set(0);
        engine = new QueryExecutionEngine(Clock.fixed(Instant.parse("2026-09-26T02:00:00Z"), ZoneOffset.UTC),
            new QueryReadSupport(types, operations, resources, roleMapper, grants, cache, entries),
            subjects, conditions, conflicts, resourceMapper);
    }

    static TargetClause clause(String type, String code) {
        return new TargetClause(new TypeOperation(type, "VIEW"), new ByCode(code, null, null));
    }
    static QueryItem decision(String key, Inheritance inheritance, TypeFallback fallback, TargetClause... clauses) {
        return QueryItem.decision(key, new TargetSet(List.of(clauses), inheritance, fallback, null), OutputSpec.minimalWithMatchIds());
    }
    QueryResult execute(Subject subject, QueryItem... items) {
        return engine.execute(new QueryRequest(TENANT, subject, CallerContext.of(null), ReadOptions.defaults(), List.of(items)));
    }
    static DecisionResult result(QueryResult results, int index) { return (DecisionResult) results.orderedResults().get(index); }

    @Test
    void should_matchIndependentGetDeniedProjectionAndRejectUnion_whenRealSqlLoadsMutexEndpoints() {
        fixture.newType(951, "R2STAGE");
        long view = fixture.insertOperation(951, "VIEW", 2, 0);
        long update = fixture.insertOperation(951, "UPDATE", 4, 2);
        long role = fixture.insertRoleRow(TENANT, "stages");
        long user = fixture.insertUserWithRoles(TENANT, "stages", role);
        long x = fixture.insertResourceRow(951, "x"); long y = fixture.insertResourceRow(951, "y");
        fixture.insertPermRow(role, 951, x, 2, false, null);
        fixture.insertPermRow(role, 951, y, 4, false, null);
        createdRuleIds.add(fixture.insertPermMutexRule(view, update));
        QueryResult results = execute(new User(user),
            decision("x", Inheritance.SELF, TypeFallback.ALLOW, clause("R2STAGE", "x")),
            decision("y", Inheritance.SELF, TypeFallback.ALLOW, clause("R2STAGE", "y")),
            decision("union", Inheritance.SELF, TypeFallback.ALLOW, clause("R2STAGE", "x"), clause("R2STAGE", "y")));
        assertThat(result(results, 0).outcome()).isEqualTo(DecisionResult.Decision.ALLOW);
        assertThat(result(results, 1).outcome()).isEqualTo(DecisionResult.Decision.ALLOW);
        assertThat(result(results, 2).reason()).isEqualTo(DecisionResult.Reason.CONDITION_NOT_MET_OR_CONFLICT);
        assertThat(legacy.getDeniedEntityIds(TENANT, user, "R2STAGE", Set.of(x, y), "VIEW")).isEmpty();
        assertThat(legacy.getDeniedResourceCodes(TENANT, user, "R2STAGE", Set.of("x", "y"), "VIEW")).isEmpty();
    }

    @Test
    void should_keepPerItemClosureAndSameTypeBoundary_whenTargetsMixInheritance() {
        assertThat(jdbc.queryForList("SELECT id FROM role_resource_permission WHERE tenant_id=? AND resource_entity_id=? AND delete_flag=0",
            Long.class, TENANT, RES_R3)).as("该基线目标没有自身授权").isEmpty();
        QueryResult results = execute(new Roles(Set.of(ROLE_A)),
            decision("self", Inheritance.SELF, TypeFallback.DISALLOW, clause(TYPE_T1_CODE, CODE_R3)),
            decision("ancestor", Inheritance.SELF_AND_ANCESTORS, TypeFallback.DISALLOW, clause(TYPE_T1_CODE, CODE_R3)),
            decision("wrong-type", Inheritance.SELF_AND_ANCESTORS, TypeFallback.DISALLOW,
                new TargetClause(new TypeOperation(TYPE_T2_CODE, "VIEW"), new ByEntityId(RES_R3))));
        assertThat(result(results, 0).reason()).as("SELF 结果: %s", result(results, 0)).isEqualTo(DecisionResult.Reason.NO_PERMISSION);
        assertThat(result(results, 1).details().matchedPermissionIds()).containsExactlyInAnyOrder(PERM_R1_VIEW, PERM_R1_UPDATE);
        assertThat(result(results, 2).reason()).isEqualTo(DecisionResult.Reason.NO_PERMISSION);
    }

    @Test
    void should_filterScopeConditionThenAllowInstance_whenFactsRequireBothStages() {
        long entity = fixture.insertResourceRow(TYPE_T3, "condition-instance");
        long instance = fixture.insertPermRow(ROLE_C, TYPE_T3, entity, 2, false, null);
        var selection = new TargetSet(List.of(new TargetClause(new TypeOperation(TYPE_T3_CODE, "VIEW"), new ByEntityId(entity))),
            Inheritance.SELF, TypeFallback.ALLOW, null);
        GrantSetResult facts = (GrantSetResult) execute(new Roles(Set.of(ROLE_C)),
            QueryItem.facts("facts", selection, Evaluation.full(), OutputSpec.rawAndKept())).orderedResults().getFirst();
        assertThat(facts.details().stageFacts()).extracting(StageFacts::stageStatus)
            .containsExactly(StageFacts.Status.FILTERED_EMPTY, StageFacts.Status.PRESENT);
        assertThat(facts.details().stageFacts().getFirst().rawAfterContext()).extracting(GrantFact::permissionId).containsExactly(PERM_T3_SCOPE_ALL_COND);
        assertThat(facts.details().matchedPermissionIds()).containsExactly(instance);
    }

    @Test
    void should_preserveScopeFailureReason_whenInstanceCodeUnknown() {
        QueryResult results = execute(new User(USER_EMPTY), decision("unknown", Inheritance.SELF, TypeFallback.ALLOW,
            clause(TYPE_T3_CODE, "not-registered")));
        assertThat(result(results, 0).reason()).isEqualTo(DecisionResult.Reason.CONDITION_NOT_MET_OR_CONFLICT);
    }

    @Test
    void should_skipInstanceSqlAndIgnoreRoleSnapshot_whenScopeAllIsSufficient() {
        var item = decision("unknown", Inheritance.SELF, TypeFallback.ALLOW, clause(TYPE_T2_CODE, "not-registered"));
        cache.put(AccessCacheCatalog.ROLE_PERM_SNAPSHOT, TENANT, ROLE_B, List.of());
        QueryResult results = execute(new Roles(Set.of(ROLE_B)), item);
        assertThat(result(results, 0).outcome()).isEqualTo(DecisionResult.Decision.ALLOW);
        assertThat(counter.grants.get()).isEqualTo(1);
        assertThat(result(results, 0).coverage().completedStages()).containsExactly(Stage.TYPE_GRANT);
        assertThat(result(results, 0).coverage().skippedStages()).containsEntry(Stage.INSTANCE, EvaluationCoverage.SkipReason.SUFFICIENT_DECISION);
    }

    @Test
    @Transactional
    void should_rereadOwnTransactionAfterWrite_whenExecuteInvokedAgain() {
        var item = decision("write", Inheritance.SELF, TypeFallback.DISALLOW, clause(TYPE_T1_CODE, CODE_R3));
        assertThat(result(execute(new Roles(Set.of(ROLE_A)), item), 0).outcome()).isEqualTo(DecisionResult.Decision.DENY);
        RoleResourcePermission row = new RoleResourcePermission();
        row.setTenantId(TENANT); row.setAbstractRoleId(ROLE_A); row.setResourceType(TYPE_T1);
        row.setResourceEntityId(RES_R3); row.setGrantedBits(2L); row.setScopeAll(false); row.setGrantSource("MANUAL");
        row.setCanGrant(false); row.setDeleteFlag(0L);
        row.setCreatedAt(java.time.LocalDateTime.of(2026, 9, 26, 2, 0)); row.setUpdatedAt(row.getCreatedAt());
        grants.insert(row);
        assertThat(result(execute(new Roles(Set.of(ROLE_A)), item), 0).details().matchedPermissionIds()).containsExactly(row.getId());
    }

    @Test
    void should_loadDefinitionsInBatchesAndReuseEmptyBuckets_whenManyUnknownOperationsShareTypes() {
        List<QueryItem> items = IntStream.range(0, 20).mapToObj(i -> QueryItem.decision("item-" + i,
            new TypeLevel(List.of(new TypeOperation(TYPE_T1_CODE, "UNKNOWN"))), OutputSpec.minimal())).toList();
        QueryResult results = engine.execute(new QueryRequest(TENANT, new Roles(Set.of(ROLE_A)), CallerContext.of(null), ReadOptions.defaults(), items));
        assertThat(results.orderedResults()).hasSize(20).allSatisfy(r ->
            assertThat(((DecisionResult) r).reason()).isEqualTo(DecisionResult.Reason.NO_PERMISSION));
        assertThat(counter.operations.get()).isEqualTo(1);
        assertThat(counter.grants.get()).isZero();
    }

    @Test
    void should_keepTenantAndRoleFilters_whenCallerUsesForeignIds() {
        var request = new QueryRequest(TENANT + 1, new Roles(Set.of(ROLE_A)), CallerContext.of(null), ReadOptions.defaults(),
            List.of(decision("other", Inheritance.SELF, TypeFallback.ALLOW, clause(TYPE_T1_CODE, CODE_R2))));
        assertThat(result(engine.execute(request), 0).reason()).isEqualTo(DecisionResult.Reason.NO_PERMISSION);
        assertThat(result(execute(new Roles(Set.of(ROLE_B)), decision("wrong-role", Inheritance.SELF, TypeFallback.DISALLOW,
            clause(TYPE_T1_CODE, CODE_R2))), 0).reason()).isEqualTo(DecisionResult.Reason.NO_PERMISSION);
    }

    @Test
    void should_mergeActualSqlChunksBeforeMutex_whenTargetSetExceedsSqlBatch() {
        List<Long> ids = jdbc.queryForList("INSERT INTO resource_entity(tenant_id,resource_type,code,code_type,name) "
            + "SELECT ?, ?, 'stage-chunk-' || n, 'default', 'chunk' FROM generate_series(1,501) n RETURNING id",
            Long.class, TENANT, TYPE_T1);
        fixture.insertPermRow(ROLE_A, TYPE_T1, ids.getFirst(), 2, false, null);
        fixture.insertPermRow(ROLE_A, TYPE_T1, ids.getLast(), 4, false, null);
        createdRuleIds.add(fixture.insertPermMutexRule(OP_T1_VIEW, OP_T1_UPDATE));
        TargetClause[] clauses = ids.stream().map(id -> new TargetClause(new TypeOperation(TYPE_T1_CODE, "VIEW"), new ByEntityId(id)))
            .toArray(TargetClause[]::new);
        QueryResult results = execute(new Roles(Set.of(ROLE_A)), decision("chunks", Inheritance.SELF, TypeFallback.DISALLOW, clauses));
        assertThat(result(results, 0).reason()).isEqualTo(DecisionResult.Reason.CONDITION_NOT_MET_OR_CONFLICT);
        assertThat(counter.grants.get()).as("实际授权 SQL prepare 分块计数").isEqualTo(2);
    }

    @Test
    void should_matchLegacyTypeLevelReason_withoutChangingTargetParentExclusion() {
        long role = fixture.insertRoleRow(TENANT, "type-dependent");
        long user = fixture.insertUserWithRoles(TENANT, "type-dependent", role);
        long parent = fixture.insertPermRow(role, TYPE_T2, null, 2, true, null);
        long child = fixture.insertPermRow(role, TYPE_T1, null, 2, true, null);
        jdbc.update("UPDATE role_resource_permission SET depend_on=? WHERE id=? AND tenant_id=?", parent, child, TENANT);
        QueryResult results = execute(new User(user),
            QueryItem.decision("type", new TypeLevel(List.of(new TypeOperation(TYPE_T1_CODE, "VIEW"))), OutputSpec.minimal()),
            decision("target", Inheritance.SELF, TypeFallback.ALLOW, clause(TYPE_T1_CODE, CODE_R3)));
        assertThat(result(results, 0).reason()).isEqualTo(DecisionResult.Reason.NO_PERMISSION);
        assertThat(result(results, 1).reason()).isEqualTo(DecisionResult.Reason.DEPENDENT_NOT_IN_PARENT_CONTEXT);
        assertThat(legacy.query(PermQuery.forAuthCheck(TENANT, user, TYPE_T1_CODE, null, "VIEW")).reason())
            .isEqualTo(result(results, 0).reason().name());
        PermBatchQuery batch = PermBatchQuery.forAuthCheckBatch(TENANT, user,
            List.of(new PermBatchQuery.Item(TYPE_T1_CODE, null, "VIEW", null, null, false)));
        batch.setEvalContext(new PermEvalContext(null, results.evaluatedAt(), java.util.Map.of()));
        assertThat(legacy.queryBatch(batch).outcomes().getFirst().reason()).isEqualTo(result(results, 0).reason().name());
    }
}
