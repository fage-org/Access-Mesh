package cn.ac.fage.accessmesh.access.engine.query;

import cn.ac.fage.accessmesh.access.audit.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.engine.core.SubjectDomainService;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.engine.util.RolePermEntryMapper;
import cn.ac.fage.accessmesh.access.grant.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.access.grant.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.grant.service.domain.RoleResourcePermissionDomainService;
import cn.ac.fage.accessmesh.access.infrastructure.cache.AccessCacheCatalog;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.resource.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.access.role.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.rule.entity.PermissionConflictRule;
import cn.ac.fage.accessmesh.access.rule.mapper.PermissionConditionMapper;
import cn.ac.fage.accessmesh.access.rule.mapper.PermissionConflictRuleMapper;
import cn.ac.fage.accessmesh.access.rule.service.domain.impl.PermissionConditionDomainServiceImpl;
import cn.ac.fage.accessmesh.access.rule.service.domain.impl.PermissionConflictDomainServiceImpl;
import cn.ac.fage.accessmesh.access.type.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.type.service.domain.OperationPermissionDomainService;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 根级受控证据提交、技术故障边界与 TRACE 输出（T-PERM-088，A01~A05/X01/X02）。
 * <p>
 * 真实 execute + 读取/条件/互斥部件（域服务真实现），只有存储边界与审计落库被替换：
 * 旧通知入口（filterRoleMutex/filterPermMutex/notifyHits）零调用——所有
 * CONFLICT_DETECTED 行均来自新核心受控提交（A03 不重复通知的判定基础）。
 * </p>
 */
class QueryAuditAndTraceTest {

    private static final TypeOperation REPORT_VIEW = new TypeOperation("REPORT", "VIEW");

    private final TypeResolutionService types = mock(TypeResolutionService.class);
    private final OperationPermissionDomainService operations = mock(OperationPermissionDomainService.class);
    private final ResourceEntityDomainService resources = mock(ResourceEntityDomainService.class);
    private final ResourceEntityMapper resourceMapper = mock(ResourceEntityMapper.class);
    private final AbstractRoleMapper roleMapper = mock(AbstractRoleMapper.class);
    private final RoleResourcePermissionMapper grants = mock(RoleResourcePermissionMapper.class);
    private final PermissionConflictRuleMapper rules = mock(PermissionConflictRuleMapper.class);
    private final PermissionConditionMapper conditionMapper = mock(PermissionConditionMapper.class);
    private final SubjectDomainService subjects = mock(SubjectDomainService.class);
    private final CacheService cache = mock(CacheService.class);
    private final AuditDomainService audit = mock(AuditDomainService.class);
    private final RecordingMetrics metrics = new RecordingMetrics();
    private final List<OperationPermission> definitions = new ArrayList<>();
    private final List<RoleResourcePermission> scopeRows = new ArrayList<>();
    private final List<RoleResourcePermission> instanceRows = new ArrayList<>();
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-26T02:00:00Z"), ZoneOffset.UTC);
    private QueryExecutionEngine engine;

    @BeforeEach
    void setup() {
        definitions.add(operation(11, 1, "VIEW", 2, 0));
        definitions.add(operation(12, 1, "UPDATE", 4, 2));
        definitions.add(operation(21, 2, "VIEW", 2, 0));
        definitions.add(operation(22, 2, "UPDATE", 4, 0));
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
        engine = newEngine(metrics);
    }

    /** 复用同一批 mock 部件构造引擎（不同指标实现的回归锁用）。 */
    QueryExecutionEngine newEngine(QueryEngineMetrics metricsImpl) {
        var conditions = new PermissionConditionDomainServiceImpl(conditionMapper,
            mock(RoleResourcePermissionDomainService.class), new ObjectMapper(), cache);
        var conflicts = new PermissionConflictDomainServiceImpl(rules, cache, new ObjectMapper(), audit, operations, subjects);
        return new QueryExecutionEngine(clock,
            new QueryReadSupport(types, operations, resources, roleMapper, grants, cache, new RolePermEntryMapper()),
            subjects, conditions, conflicts, resourceMapper, new QueryAuditCollector(audit, metricsImpl), metricsImpl);
    }

    /** 新核心直连的互斥评估器（describeRules 契约锁用）。 */
    cn.ac.fage.accessmesh.access.engine.core.BatchPermMutexEvaluator mutexEvaluator() {
        var conflicts = new PermissionConflictDomainServiceImpl(rules, cache, new ObjectMapper(), audit, operations, subjects);
        return conflicts.openBatchMutexEvaluator(1L, requested -> definitions.stream()
            .filter(op -> requested.contains(op.getResourceType())).toList());
    }

    static OperationPermission operation(long id, int type, String code, long bit, long inherit) {
        OperationPermission op = new OperationPermission();
        op.setId(id); op.setTenantId(1L); op.setResourceType(type); op.setCode(code);
        op.setBinaryBit(bit); op.setInheritMask(inherit);
        return op;
    }

    static RoleResourcePermission grant(long id, int type, Long entity, long bits) {
        RoleResourcePermission row = new RoleResourcePermission();
        row.setId(id); row.setTenantId(1L); row.setAbstractRoleId(10L); row.setResourceType(type);
        row.setResourceEntityId(entity); row.setGrantedBits(bits);
        row.setScopeAll(entity == null); row.setCanGrant(false); row.setGrantSource("MANUAL");
        return row;
    }

    /** PERM 互斥规则装载（规则集可含多条；未触发规则不进证据=A01 的反例构造面）。 */
    void permMutexRules(PermissionConflictRule... perms) {
        when(rules.selectByConflictType(1L, "PERM_MUTEX")).thenReturn(List.of(perms));
    }

    static PermissionConflictRule permRule(long ruleId, long firstOp, long secondOp) {
        PermissionConflictRule rule = new PermissionConflictRule();
        rule.setId(ruleId); rule.setFirstOperationPermissionId(firstOp); rule.setSecondOperationPermissionId(secondOp);
        return rule;
    }

    void roleMutexRules(PermissionConflictRule... roleRules) {
        when(rules.selectByConflictType(1L, "ROLE_MUTEX")).thenReturn(List.of(roleRules));
    }

    static PermissionConflictRule roleRule(long ruleId, long firstRole, long secondRole) {
        PermissionConflictRule rule = new PermissionConflictRule();
        rule.setId(ruleId); rule.setFirstAbstractRoleId(firstRole); rule.setSecondAbstractRoleId(secondRole);
        return rule;
    }

    static TargetClause clause(long id) { return new TargetClause(REPORT_VIEW, new ByEntityId(id)); }
    static TargetSet target(TargetClause... clauses) {
        return new TargetSet(List.of(clauses), Inheritance.SELF, TypeFallback.DISALLOW, null);
    }

    QueryResult execute(QueryItem... items) {
        return engine.execute(new QueryRequest(1L, new Roles(Set.of(10L)), CallerContext.of("127.0.0.1"),
            ReadOptions.defaults(), List.of(items)));
    }

    QueryResult executeAsUser(Long userId, QueryItem... items) {
        return engine.execute(new QueryRequest(1L, new User(userId), CallerContext.of(null),
            ReadOptions.defaults(), List.of(items)));
    }

    /** 全量提取审计 sink 收到的 CONFLICT_DETECTED 摘要（含旧通知入口混入时的全量面）。 */
    List<String> auditSummaries() {
        return org.mockito.Mockito.mockingDetails(audit).getInvocations().stream()
            .filter(invocation -> invocation.getMethod().getName().equals("asyncRecordLog"))
            .map(invocation -> (AuditDomainService.OperationLogEntry) invocation.getArgument(0))
            .map(AuditDomainService.OperationLogEntry::summary)
            .toList();
    }

    /** 记录式指标 fake：维度值全部来自固定枚举（低基数结构性锁定的消费面）。 */
    static final class RecordingMetrics implements QueryEngineMetrics {
        final List<String> events = new ArrayList<>();
        final List<QueryEngineMetrics.EvidenceKind> submissionFailures = new ArrayList<>();
        @Override public void itemStage(SelectionKind selection, Stage stage, StageOutcome outcome) {
            events.add("stage:" + selection + ":" + stage + ":" + outcome);
        }
        @Override public void executionCompleted(ExecutionOutcome outcome) {
            events.add("execution:" + outcome);
        }
        @Override public void evidenceSubmissionFailed(EvidenceKind evidenceKind) {
            submissionFailures.add(evidenceKind);
        }
    }

    // ===== A01：未触发规则不进证据 =====

    @Test
    void should_collectOnlyTriggeredRules_whenMultipleRulesLoadedButOneEndAbsent() {
        permMutexRules(permRule(90L, 11L, 12L), permRule(91L, 11L, 21L));
        instanceRows.add(grant(101, 1, 100L, 2));
        instanceRows.add(grant(102, 1, 100L, 4));
        var result = execute(QueryItem.decision("a", target(clause(100)), OutputSpec.minimal()));
        assertThat(((DecisionResult) result.orderedResults().get(0)).outcome())
            .isEqualTo(DecisionResult.Decision.DENY);
        var summaries = auditSummaries();
        assertThat(summaries).singleElement().satisfies(summary -> {
            assertThat(summary).contains("rule=90").contains("stage=INSTANCE").contains("item=a");
            assertThat(summary).doesNotContain("rule=91").as("A-C 未触发不进证据（A01）");
        });
    }

    // ===== A02：重复 key 与 scope/instance 同规则各维分别成证 =====

    @Test
    void should_keepItemAndStageDimensions_whenDuplicateKeysAndBothStagesHitSameRule() {
        permMutexRules(permRule(90L, 11L, 12L));
        scopeRows.add(grant(103, 1, null, 2));
        scopeRows.add(grant(104, 1, null, 4));
        instanceRows.add(grant(101, 1, 100L, 2));
        instanceRows.add(grant(102, 1, 100L, 4));
        var fallback = new TargetSet(List.of(clause(100)), Inheritance.SELF, TypeFallback.ALLOW, null);
        execute(QueryItem.decision("key-a", fallback, OutputSpec.minimal()),
            QueryItem.decision("key-b", fallback, OutputSpec.minimal()));
        var summaries = auditSummaries();
        assertThat(summaries).as("重复 key 各自成证、scope/instance 同规则按 stage 分列（A02，不按 entity 去重）")
            .hasSize(4)
            .anySatisfy(s -> assertThat(s).contains("item=key-a").contains("stage=TYPE_GRANT"))
            .anySatisfy(s -> assertThat(s).contains("item=key-a").contains("stage=INSTANCE"))
            .anySatisfy(s -> assertThat(s).contains("item=key-b").contains("stage=TYPE_GRANT"))
            .anySatisfy(s -> assertThat(s).contains("item=key-b").contains("stage=INSTANCE"))
            .allSatisfy(s -> assertThat(s).contains("rule=90").contains("ops=[11, 12]"));
    }

    // ===== A03：父、角色、主阶段证据一次受控提交，无重复通知 =====

    @Test
    void should_submitRoleMainStageAndParentEvidenceInOneControlledSubmission() {
        permMutexRules(permRule(90L, 11L, 12L));
        roleMutexRules(roleRule(80L, 10L, 20L));
        when(subjects.resolveEffectiveRoles(1L, 500L)).thenReturn(Set.of(10L, 20L, 30L));
        // 父 900 行 bits6=VIEW+UPDATE 复合位（互斥精确查表排除）＝父判定幸存者；
        // 102/103 为父场景互斥两端 → 父 INSTANCE 阶段命中规则 90（一条父证据关联根项）。
        instanceRows.add(grant(900, 1, 900L, 6));
        instanceRows.add(grant(102, 1, 900L, 2));
        instanceRows.add(grant(103, 1, 900L, 4));
        // 子：101 依赖父命中权限 900（绑定保留）、105 独立主行 → 子阶段两端同场命中规则 90。
        instanceRows.add(grant(101, 1, 100L, 2));
        instanceRows.get(3).setDependOn(900L);
        instanceRows.add(grant(105, 1, 100L, 4));
        var parent = new ParentRequirement("REPORT", new ByEntityId(900L), Set.of("VIEW", "UPDATE"));
        var result = executeAsUser(500L, QueryItem.decision("root",
            new TargetSet(List.of(clause(100)), Inheritance.SELF, TypeFallback.DISALLOW, parent),
            OutputSpec.minimal()));
        assertThat(result.orderedResults()).hasSize(1);
        var summaries = auditSummaries();
        assertThat(summaries).as("角色对＋根项阶段＋共享父各一条，全部来自根级一次提交（A03）").hasSize(3);
        assertThat(summaries).anySatisfy(s -> assertThat(s).contains("Role mutex evidence")
            .contains("pair=10 vs 20").contains("affected=[root]").contains("completion=COMPLETE"));
        assertThat(summaries).anySatisfy(s -> assertThat(s).contains("Perm conflict evidence")
            .contains("item=parent#1").contains("affected=[root]").contains("stage=INSTANCE"));
        assertThat(summaries).anySatisfy(s -> assertThat(s).contains("Perm conflict evidence")
            .contains("item=root").contains("stage=INSTANCE"));
    }

    // ===== A04：后续装载故障保留技术失败，已确认阶段证据标未完成执行 =====

    @Test
    void should_keepTechnicalFailureAndMarkEvidenceIncomplete_whenLaterLoadFails() {
        permMutexRules(permRule(90L, 11L, 12L));
        scopeRows.add(grant(103, 1, null, 2));
        scopeRows.add(grant(104, 1, null, 4));
        instanceRows.add(grant(101, 1, 100L, 2));
        when(grants.selectInstancePermsByBitsBatch(eq(1L), anySet(), anySet(), anyList()))
            .thenThrow(new IllegalStateException("instance database down"));
        var fallback = new TargetSet(List.of(clause(100)), Inheritance.SELF, TypeFallback.ALLOW, null);
        assertThatThrownBy(() -> execute(QueryItem.decision("a", fallback, OutputSpec.minimal())))
            .isInstanceOf(QueryExecutionException.class)
            .hasRootCauseMessage("instance database down");
        var summaries = auditSummaries();
        assertThat(summaries).singleElement().satisfies(summary -> {
            assertThat(summary).contains("item=a").contains("stage=TYPE_GRANT").contains("rule=90");
            assertThat(summary).as("已确认阶段证据标执行未完成（A04）")
                .contains("completion=EXECUTION_ERROR_AFTER_CONFIRMED_STAGE");
        });
        assertThat(metrics.events).contains("execution:TECHNICAL_FAILURE");
    }

    // ===== X01：DB/规则装载故障为技术异常，不当 DENY/空清单 =====

    @Test
    void should_wrapRuleLoadingFailureAsTechnicalException_notPlainDenyOrEmptyList() {
        when(rules.selectByConflictType(eq(1L), eq("PERM_MUTEX"))).thenThrow(new IllegalStateException("rule db down"));
        instanceRows.add(grant(101, 1, 100L, 2));
        assertThatThrownBy(() -> execute(QueryItem.decision("a", target(clause(100)), OutputSpec.minimal())))
            .as("X01：规则装载故障=技术异常，不是 DENY")
            .isInstanceOf(QueryExecutionException.class)
            .hasRootCauseMessage("rule db down");
        verify(audit, never()).asyncRecordLog(any());
        assertThat(metrics.events).contains("execution:TECHNICAL_FAILURE");
    }

    // ===== X02：中途超限类故障不返回半份 FACTS（EngineLimits 配置本体归 T-PERM-093） =====

    @Test
    void should_notReturnPartialFacts_whenLateProjectionLoadFails() {
        instanceRows.add(grant(101, 1, 100L, 2));
        when(resources.selectValidByIds(eq(1L), anySet())).thenThrow(new IllegalStateException("budget exceeded"));
        var output = new OutputSpec(FactDetail.KEPT, true, true, false, PresentationExpansion.NONE, Set.of(), false);
        var item = QueryItem.facts("a", target(clause(100)), Evaluation.full(), output);
        assertThatThrownBy(() -> execute(item))
            .as("X02：装载/预算类故障=整体技术失败，不返回半份 FACTS（EngineLimits 配置本体归 T-PERM-093）")
            .isInstanceOf(QueryExecutionException.class)
            .hasRootCauseMessage("budget exceeded");
    }

    // ===== 角色对证据跨请求 1h 去重沿旧口径；PERM 规则证据不去重 =====

    @Test
    void should_deduplicateRolePairEvidenceAcrossRequests_butAlwaysSubmitPermRuleEvidence() {
        permMutexRules(permRule(90L, 11L, 12L));
        roleMutexRules(roleRule(80L, 10L, 20L));
        when(subjects.resolveEffectiveRoles(1L, 500L)).thenReturn(Set.of(10L, 20L, 30L));
        instanceRows.add(grant(101, 1, 100L, 2));
        instanceRows.add(grant(102, 1, 100L, 4));
        var request = new QueryRequest(1L, new User(500L), CallerContext.of(null), ReadOptions.defaults(),
            List.of(QueryItem.decision("a", target(clause(100)), OutputSpec.minimal())));
        engine.execute(request);
        assertThat(auditSummaries()).hasSize(2)
            .anySatisfy(s -> assertThat(s).contains("Role mutex evidence"))
            .anySatisfy(s -> assertThat(s).contains("Perm conflict evidence"));
        reset(audit);
        engine.execute(request);
        assertThat(auditSummaries()).as("角色对 1h 窗口内去重；PERM 规则证据如实重报（沿旧口径拍板）")
            .singleElement().satisfies(s -> assertThat(s).contains("Perm conflict evidence"));
    }

    // ===== NO_ROLE 全删仍提交角色对证据（早退不吞证据） =====

    @Test
    void should_submitRolePairEvidence_whenAllRolesRemovedByMutexLeadsToNoRole() {
        roleMutexRules(roleRule(80L, 10L, 20L));
        when(subjects.resolveEffectiveRoles(1L, 500L)).thenReturn(Set.of(10L, 20L));
        var result = engine.execute(new QueryRequest(1L, new User(500L), CallerContext.of(null),
            ReadOptions.defaults(), List.of(QueryItem.decision("a", target(clause(100)), OutputSpec.minimal()))));
        assertThat(((DecisionResult) result.orderedResults().get(0)).reason())
            .isEqualTo(DecisionResult.Reason.NO_ROLE);
        assertThat(auditSummaries()).singleElement().satisfies(summary ->
            assertThat(summary).contains("Role mutex evidence").contains("pair=10 vs 20"));
    }

    // ===== 空请求审计零调用（C01 审计半边） =====

    @Test
    void should_notTouchAuditNorMetricsEvents_whenItemsEmpty() {
        var result = engine.execute(new QueryRequest(1L, new Roles(Set.of(10L)), CallerContext.of(null),
            ReadOptions.defaults(), List.of()));
        assertThat(result.orderedResults()).isEmpty();
        verifyNoInteractions(audit);
        assertThat(metrics.events).isEmpty();
        assertThat(metrics.submissionFailures).isEmpty();
    }

    // ===== 提交失败只记技术日志与指标，不覆盖主结果/主异常 =====

    @Test
    void should_swallowSubmissionFailure_andCountMetric_whenAuditSinkRejects() {
        permMutexRules(permRule(90L, 11L, 12L));
        instanceRows.add(grant(101, 1, 100L, 2));
        instanceRows.add(grant(102, 1, 100L, 4));
        org.mockito.Mockito.doThrow(new IllegalStateException("audit pool exhausted")).when(audit).asyncRecordLog(any());
        var result = execute(QueryItem.decision("a", target(clause(100)), OutputSpec.minimal()));
        assertThat(((DecisionResult) result.orderedResults().get(0)).outcome())
            .as("提交失败不影响查询结果（非阻塞提交）")
            .isEqualTo(DecisionResult.Decision.DENY);
        assertThat(metrics.submissionFailures)
            .singleElement().isEqualTo(QueryEngineMetrics.EvidenceKind.PERM_RULE);
        assertThat(metrics.events).contains("execution:SUCCESS");
    }

    // ===== TRACE：真实执行解释、不重评条件、敏感 ID 在块内可见（门禁暂缓拍板） =====

    @Test
    void should_explainRealExecutionWithSensitiveIdsInTrace_withoutReEvaluatingConditions() {
        permMutexRules(permRule(90L, 11L, 12L));
        roleMutexRules(roleRule(80L, 10L, 20L));
        when(subjects.resolveEffectiveRoles(1L, 500L)).thenReturn(Set.of(10L, 20L, 30L));
        instanceRows.add(grant(101, 1, 100L, 2));
        instanceRows.add(grant(102, 1, 100L, 4));
        var output = new OutputSpec(FactDetail.NONE, false, false, false, PresentationExpansion.NONE, Set.of(), true);
        var result = engine.execute(new QueryRequest(1L, new User(500L), CallerContext.of(null),
            ReadOptions.defaults(), List.of(QueryItem.decision("a", target(clause(100)), output))));
        var decision = (DecisionResult) result.orderedResults().get(0);
        assertThat(decision.details().loadedSections()).contains(ResultDetails.DetailSection.TRACE);
        var trace = decision.details().trace();
        assertThat(trace.resolvedRoleIds()).as("主体解析后角色（敏感）").containsExactly(30L);
        assertThat(trace.roleMutexHits()).singleElement().satisfies(pair -> {
            assertThat(pair.firstRoleId()).isEqualTo(10L);
            assertThat(pair.secondRoleId()).isEqualTo(20L);
        });
        assertThat(trace.stages()).singleElement().satisfies(stage -> {
            assertThat(stage.stage()).isEqualTo(Stage.INSTANCE);
            assertThat(stage.rawAfterContext()).extracting(GrantFact::permissionId).containsExactly(101L, 102L);
            assertThat(stage.retainedAfterEvaluation()).as("互斥双删后保留为空").isEmpty();
            assertThat(stage.triggeredRules()).singleElement().satisfies(rule -> {
                assertThat(rule.ruleId()).isEqualTo(90L);
                assertThat(rule.firstOperationPermissionId()).isEqualTo(11L);
                assertThat(rule.secondOperationPermissionId()).isEqualTo(12L);
            });
        });
        assertThat(trace.stages()).as("TRACE 不虚构未执行阶段（TYPE_GRANT 不适用 DISALLOW 项）")
            .extracting(ResultDetails.ExecutionTrace.StageTrace::stage).containsExactly(Stage.INSTANCE);
        verify(conditionMapper, never()).selectValidByIds(anyLong(), any());
        verify(resources, never()).selectValidByIds(anyLong(), anySet());
    }

    // ===== 指标端口低基数结构锁：参数只允许枚举/布尔，无高基数标识通道 =====

    @Test
    void should_keepMetricsPortEnumOnly_soNoHighCardinalityLabelCanEnter() {
        var violations = new ArrayList<String>();
        for (var method : QueryEngineMetrics.class.getDeclaredMethods()) {
            for (Class<?> parameter : method.getParameterTypes()) {
                if (!parameter.isEnum() && parameter != boolean.class) {
                    violations.add(method.getName() + ":" + parameter.getSimpleName());
                }
            }
        }
        assertThat(violations).as("resourceCode/permissionId/itemKey 等高基数标签无进入通道（§6.1）").isEmpty();
    }

    // ===== 阶段终态打点覆盖短路路径 =====

    @Test
    void should_emitStageOutcomeMetrics_whenNoRoleShortCircuitsAllStages() {
        when(subjects.resolveEffectiveRoles(1L, 500L)).thenReturn(Set.of());
        executeAsUser(500L, QueryItem.decision("a", target(clause(100)), OutputSpec.minimal()));
        assertThat(metrics.events).contains("stage:TARGET_SET:INSTANCE:SKIPPED_NO_ROLE");
        assertThat(metrics.events).contains("execution:SUCCESS");
    }

    // ===== 外评 P3 回归锁：打点失败不得放大为查询故障或覆盖主异常 =====

    @Test
    void should_isolateMetricFailures_fromQueryOutcomeAndEvidenceSubmission() {
        QueryEngineMetrics throwing = new QueryEngineMetrics() {
            @Override public void itemStage(SelectionKind selection, Stage stage, StageOutcome outcome) {
                throw new IllegalStateException("metric backend down");
            }
            @Override public void executionCompleted(ExecutionOutcome outcome) {
                throw new IllegalStateException("metric backend down");
            }
            @Override public void evidenceSubmissionFailed(EvidenceKind evidenceKind) {
                throw new IllegalStateException("metric backend down");
            }
        };
        var safeEngine = newEngine(throwing);
        instanceRows.add(grant(101, 1, 100L, 2));
        var result = safeEngine.execute(new QueryRequest(1L, new Roles(Set.of(10L)), CallerContext.of(null),
            ReadOptions.defaults(), List.of(QueryItem.decision("a", target(clause(100)), OutputSpec.minimal()))));
        assertThat(((DecisionResult) result.orderedResults().get(0)).outcome())
            .as("打点实现抛异常不改变查询结果、不外抛（观测故障不放大为查询故障）")
            .isEqualTo(DecisionResult.Decision.ALLOW);
        verify(audit, never()).asyncRecordLog(any());
    }

    // ===== 外评 P3 回归锁：describeRules 对端点缺失规则不 NPE（与 compute AND 判定同守卫） =====

    @Test
    void should_skipNullEndpointRulesInDescribeRules_whenRuleRowHasMissingEndpoints() {
        var broken = permRule(97L, 11L, 12L);
        broken.setSecondOperationPermissionId(null);
        permMutexRules(permRule(90L, 11L, 12L), broken);
        var evaluator = mutexEvaluator();
        // 先经 compute 触发规则装载（VIEW+UPDATE 两端在场触发规则 90；97 端点缺失不满足 AND 判定）
        var view = new cn.ac.fage.accessmesh.access.engine.vo.RolePermEntry(101L, 10L, 100L, null,
            1, 2L, null, null, "MANUAL", true, null, false, null, false);
        var update = new cn.ac.fage.accessmesh.access.engine.vo.RolePermEntry(102L, 10L, 100L, null,
            1, 4L, null, null, "MANUAL", true, null, false, null, false);
        assertThat(evaluator.compute(List.of(view, update)).triggeredRuleIds()).containsExactly(90L);
        assertThat(evaluator.describeRules(Set.of(97L)))
            .as("端点缺失规则不进规则引用（MutexRuleRef 组件为 long，null 拆箱 NPE）")
            .isEmpty();
    }

    // ===== 外评 P3 回归锁：>8 受影响根项不折叠计数，key 列表保留在摘要 =====

    @Test
    void should_keepAllAffectedKeysInSummary_whenMoreThanEightRootItemsShareRolePair() {
        roleMutexRules(roleRule(80L, 10L, 20L));
        when(subjects.resolveEffectiveRoles(1L, 500L)).thenReturn(Set.of(10L, 20L, 30L));
        instanceRows.add(grant(101, 1, 100L, 2));
        var items = new ArrayList<QueryItem>();
        for (int i = 1; i <= 9; i++) {
            items.add(QueryItem.decision("key-" + i, target(clause(100)), OutputSpec.minimal()));
        }
        executeAsUser(500L, items.toArray(QueryItem[]::new));
        assertThat(auditSummaries()).singleElement().satisfies(summary -> {
            assertThat(summary).contains("key-1").contains("key-9")
                .as("9 个根项仍逐一列出（不折叠为计数）且 completion 在 affected 前")
                .matches(s -> s.indexOf("completion=COMPLETE") < s.indexOf("affected="));
        });
    }
}
