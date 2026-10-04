package cn.ac.fage.accessmesh.access.engine.service.impl;

import cn.ac.fage.accessmesh.perm.common.dto.req.QueryResourcesReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.QueryScopesReq;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import cn.ac.fage.accessmesh.perm.common.dto.resp.QueryResourcesResp;
import cn.ac.fage.accessmesh.perm.common.dto.resp.QueryScopesResp;
import cn.ac.fage.accessmesh.access.domain.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.engine.query.ByCode;
import cn.ac.fage.accessmesh.access.engine.query.Evaluation;
import cn.ac.fage.accessmesh.access.engine.query.EvaluationCoverage;
import cn.ac.fage.accessmesh.access.engine.query.FactDetail;
import cn.ac.fage.accessmesh.access.engine.query.GrantFact;
import cn.ac.fage.accessmesh.access.engine.query.GrantList;
import cn.ac.fage.accessmesh.access.engine.query.GrantSetResult;
import cn.ac.fage.accessmesh.access.engine.query.OperationDefinition;
import cn.ac.fage.accessmesh.access.engine.query.OutputSpec;
import cn.ac.fage.accessmesh.access.engine.query.ParentRequirement;
import cn.ac.fage.accessmesh.access.engine.query.PresentationExpansion;
import cn.ac.fage.accessmesh.access.engine.query.QueryExecutionEngine;
import cn.ac.fage.accessmesh.access.engine.query.QueryItem;
import cn.ac.fage.accessmesh.access.engine.query.QueryRequest;
import cn.ac.fage.accessmesh.access.engine.query.QueryResult;
import cn.ac.fage.accessmesh.access.engine.query.ResultDetails;
import cn.ac.fage.accessmesh.access.engine.query.Stage;
import cn.ac.fage.accessmesh.access.engine.query.StageFacts;
import cn.ac.fage.accessmesh.access.engine.query.TypeOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 权限查询应用服务测试类
 * <p>
 * T-PERM-090：queryResources/queryScopes 全部迁新 execute。
 * 外部响应断言保留作回归锁（X03 等价），另锁适配层请求形状（GRANT_LIST＋父要求＋
 * RAW_AND_KEPT＋extraOperationKeys、展示展开方向映射、快照 PRESERVE/ENFORCE）。
 * 引擎批量语义由容器轨（QuerySemanticsBaselinePgIT）钉死，本类 stub 新入口不作等价证据。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class PermissionQueryAppServiceImplTest {

    @Mock private TypeResolutionService typeResolutionService;
    @Mock private DomainClassifyService domainClassifyService;
    @Mock private QueryExecutionEngine queryEngine;

    private PermissionQueryAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PermissionQueryAppServiceImpl(
            typeResolutionService, domainClassifyService, queryEngine);
    }

    // ===== queryScopes tests =====

    @Test
    void shouldClassifyScopeGroupAsInstanceWhenSpecificEntryMatches() {
        QueryScopesReq req = new QueryScopesReq(
            "USER", "u-1", "MENU", "sys:user", "default",
            List.of("VIEW"), List.of("DEPT"), List.of("VIEW"), "default", null, Map.of()
        );

        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
        when(typeResolutionService.resolveResourceId(1L, "MENU", "sys:user", "default", null)).thenReturn(100L);

        // MANAGE(bit8, inheritMask 覆盖 bit1) 覆盖 VIEW → INSTANCE{dept-a}
        GrantFact scopeEntry = new GrantFact(501L, 200L, 2, 300L, 8L, false, null, null, false, null, "MANUAL");
        TypeOperation deptView = new TypeOperation("DEPT", "VIEW");
        when(queryEngine.execute(any(QueryRequest.class))).thenReturn(result(presentResult(
            List.of(scopeEntry), List.of(scopeEntry),
            Map.of(300L, resource(300L, 2, "dept-a", "default", "部门A")),
            Map.of(601L, operation(601L, 2, "VIEW", 1L, 0L), 602L, operation(602L, 2, "MANAGE", 8L, 1L)),
            Map.of(deptView, operation(601L, 2, "VIEW", 1L, 0L)),
            List.of("VIEW"))));

        QueryScopesResp resp = service.queryScopes(1L, req);

        // 分类模型：(DEPT, VIEW) 应为 INSTANCE，items 含 dept-a
        assertNull(resp.reason());
        assertEquals(List.of("VIEW"), resp.matchedParentOperations());
        assertEquals(1, resp.scopeGroups().size());
        QueryScopesResp.ScopeGroup group = resp.scopeGroups().get(0);
        assertEquals("DEPT", group.resourceTypeCode());
        assertEquals("VIEW", group.operationCode());
        assertEquals(ScopeMode.INSTANCE, group.scopeMode());
        assertEquals(1, group.items().size());
        assertEquals("dept-a", group.items().get(0).resourceCode());

        // 适配层请求形状锁：GRANT_LIST＋父要求（编码轨）＋EVALUATE/ENFORCE＋RAW_AND_KEPT，
        // extraOperationKeys=范围类型×操作全组合（四态投影与 covers 消费）
        QueryRequest request = capturedRequest();
        assertEquals(1, request.items().size());
        QueryItem item = request.items().get(0);
        assertEquals(Evaluation.full(), item.evaluation());
        assertEquals(FactDetail.RAW_AND_KEPT, item.output().factDetail());
        ParentRequirement parent = ((GrantList) item.selection()).requiredParent();
        assertEquals("MENU", parent.resourceTypeCode());
        assertEquals(new ByCode("sys:user", "default", null), parent.resource());
        assertEquals(Set.of("VIEW"), parent.operationCodes());
        assertEquals(Set.of(deptView), item.output().extraOperationKeys());
        assertTrue(item.output().descriptions());
    }

    @Test
    void shouldReturnEmptyWhenInstanceItemsAllFilteredOut() {
        // P2 修复：INSTANCE 收集后 items 全空（资源缺失/已删）→ EMPTY，符合 T-PERM-009 契约
        QueryScopesReq req = new QueryScopesReq(
            "USER", "u-1", "MENU", "sys:user", "default",
            List.of("VIEW"), List.of("DEPT"), List.of("VIEW"), "default", null, Map.of()
        );

        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
        when(typeResolutionService.resolveResourceId(1L, "MENU", "sys:user", "default", null)).thenReturn(100L);

        // scope 条目指向 resourceEntityId=300，但描述块无 300（资源缺失）→ items 收集为空
        GrantFact scopeEntry = new GrantFact(501L, 200L, 2, 300L, 1L, false, null, null, false, null, "MANUAL");
        TypeOperation deptView = new TypeOperation("DEPT", "VIEW");
        when(queryEngine.execute(any(QueryRequest.class))).thenReturn(result(presentResult(
            List.of(scopeEntry), List.of(scopeEntry),
            Map.of(),
            Map.of(601L, operation(601L, 2, "VIEW", 1L, 0L)),
            Map.of(deptView, operation(601L, 2, "VIEW", 1L, 0L)),
            List.of("VIEW"))));

        QueryScopesResp resp = service.queryScopes(1L, req);

        assertEquals(1, resp.scopeGroups().size());
        QueryScopesResp.ScopeGroup group = resp.scopeGroups().get(0);
        assertEquals(ScopeMode.EMPTY, group.scopeMode());
        assertTrue(group.items().isEmpty());
    }

    /** grok 外评 P1 修复锁：条件评估清空（FILTERED_EMPTY）
     * 不得整表拒绝——raw 有覆盖即 EMPTY、matchedParentOperations 照常回传
     * （旧实现一律压成 NO_PERMISSION + 全格 DENIED，业务方把 EMPTY 误当 403）。 */
    @Test
    void shouldClassifyEmptyNotDeniedWhenConditionClearsScopeEntries() {
        QueryScopesReq req = new QueryScopesReq(
            "USER", "u-1", "REPORT", "report:sales", "default",
            List.of("VIEW"), List.of("DATA"), List.of("READ"), "default", null, Map.of()
        );

        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
        when(typeResolutionService.resolveResourceId(1L, "REPORT", "report:sales", "default", null)).thenReturn(100L);

        // DATA 授权行挂时间条件当前不满足：评估后清空（raw 有行、retained 空），
        // 操作定义已装载（描述块）+ 父判定命中
        GrantFact dataEntry = new GrantFact(501L, 200L, 2, 300L, 1L, false, null, 301L, true, null, "MANUAL");
        TypeOperation dataRead = new TypeOperation("DATA", "READ");
        when(queryEngine.execute(any(QueryRequest.class))).thenReturn(result(
            new GrantSetResult("scopes", GrantSetResult.CollectionStatus.FILTERED_EMPTY, coverage(true),
                details(List.of(dataEntry), List.of(),
                    Map.of(), Map.of(601L, operation(601L, 2, "READ", 1L, 0L)),
                    Map.of(dataRead, operation(601L, 2, "READ", 1L, 0L)), List.of("VIEW")))));

        QueryScopesResp resp = service.queryScopes(1L, req);

        assertNull(resp.reason(), "评估清空不是整体拒绝");
        assertEquals(1, resp.matchedParentOperations().size(), "父操作命中照常回传");
        assertEquals(1, resp.scopeGroups().size());
        assertEquals(ScopeMode.EMPTY, resp.scopeGroups().get(0).scopeMode(),
            "有覆盖但评估清空 = EMPTY（勿压 DENIED）");
    }

    @Test
    void queryScopesMustKeepPrecheckOrderAndDegeneratePairsAsDenied() {
        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);

        // 外层预检查保持：主体未解析 → USER_NOT_FOUND；父对象不存在 → OBJECT_KEY_NOT_FOUND
        when(typeResolutionService.resolveResourceId(1L, "MENU", "ghost", "default", null)).thenReturn(null);
        QueryScopesResp notFound = service.queryScopes(1L, new QueryScopesReq(
            "USER", "u-1", "MENU", "ghost", "default",
            List.of("VIEW"), List.of("DEPT"), List.of("VIEW"), "default", null, Map.of()));
        assertEquals("OBJECT_KEY_NOT_FOUND", notFound.reason());

        // 退化元素（null 类型/操作）不得进引擎结构拒绝：直接 DENIED 分组（旧引擎解析落空同形）
        when(typeResolutionService.resolveResourceId(1L, "MENU", "sys:user", "default", null)).thenReturn(100L);
        TypeOperation deptView = new TypeOperation("DEPT", "VIEW");
        GrantFact anyFact = new GrantFact(501L, 200L, 2, 300L, 1L, false, null, null, false, null, "MANUAL");
        when(queryEngine.execute(any(QueryRequest.class))).thenReturn(result(presentResult(
            List.of(anyFact), List.of(anyFact),
            Map.of(300L, resource(300L, 2, "dept-a", "default", "部门A")),
            Map.of(601L, operation(601L, 2, "VIEW", 1L, 0L)),
            Map.of(deptView, operation(601L, 2, "VIEW", 1L, 0L)),
            List.of("VIEW"))));

        QueryScopesResp degenerate = service.queryScopes(1L, new QueryScopesReq(
            "USER", "u-1", "MENU", "sys:user", "default",
            List.of("VIEW"), java.util.Arrays.asList("DEPT", null), java.util.Arrays.asList("VIEW", null),
            "default", null, Map.of()));

        assertNull(degenerate.reason());
        assertEquals(4, degenerate.scopeGroups().size());
        assertEquals(ScopeMode.INSTANCE, degenerate.scopeGroups().get(0).scopeMode());
        assertEquals(ScopeMode.DENIED, degenerate.scopeGroups().get(1).scopeMode());
        assertEquals(ScopeMode.DENIED, degenerate.scopeGroups().get(2).scopeMode());
        assertEquals(ScopeMode.DENIED, degenerate.scopeGroups().get(3).scopeMode());
        // 退化组合不进 extraOperationKeys（避免结构拒绝）
        assertEquals(Set.of(deptView), capturedRequest().items().get(0).output().extraOperationKeys());
    }

    // ===== queryResources tests =====

    @Nested
    @MockitoSettings(strictness = Strictness.LENIENT)
    class QueryResourcesTests {

        @Test
        void shouldReturnScopeAllEntryWhenUserHasScopeAllPermission() {
            lenient().when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
            lenient().when(typeResolutionService.batchResolveTypeCodes(1L, "resource_type", Set.of(1)))
                .thenReturn(Map.of(1, "REPORT"));

            GrantFact scopeAllEntry = new GrantFact(401L, 20L, 1, null, 1L, true, null, null, false, null, "MANUAL");

            when(queryEngine.execute(any(QueryRequest.class))).thenReturn(result(presentResult(
                List.of(scopeAllEntry), List.of(scopeAllEntry),
                Map.of(), Map.of(101L, operation(101L, 1, "VIEW", 1L, 0L)), Map.of(), List.of())));

            var req = new QueryResourcesReq(
                "USER", "u-1", List.of("REPORT"), List.of("VIEW"),
                null, null, null, null, null);
            var resp = service.queryResources(1L, req);

            assertEquals(1, resp.items().size());
            assertEquals(ScopeMode.ALL, resp.items().get(0).scopeMode());
            assertEquals("REPORT", resp.items().get(0).resourceTypeCode());

            // 适配层请求形状锁：无树扩展开关 → 展示展开 NONE（判定与展示分离）
            assertEquals(PresentationExpansion.NONE, capturedRequest().items().get(0).output().presentationExpansion());
        }

        @Test
        void shouldExcludeDependentEntriesFromQueryResourcesItems() {
            // T-PERM-058：子权限行不进清单面——独立 INSTANCE 条目呈现会误导调用方
            // （子行实例 ≠ 独立可访问，其授权只在 query-scopes 主资源上下文内生效）
            lenient().when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
            lenient().when(typeResolutionService.batchResolveTypeCodes(1L, "resource_type", Set.of(1)))
                .thenReturn(Map.of(1, "REPORT"));

            // 主行实例 200（dependOn=null）+ 子行实例 300（dependOn=501）
            GrantFact mainEntry = new GrantFact(401L, 20L, 1, 200L, 1L, false, null, null, false, null, "MANUAL");
            GrantFact dependentEntry = new GrantFact(402L, 20L, 1, 300L, 1L, false, null, null, false, 501L, "MANUAL");

            when(queryEngine.execute(any(QueryRequest.class))).thenReturn(result(presentResult(
                List.of(mainEntry, dependentEntry), List.of(mainEntry, dependentEntry),
                Map.of(200L, resource(200L, 1, "report:1", "default", "报表1"),
                    300L, resource(300L, 1, "city:gd", "default", "城市广东")),
                Map.of(101L, operation(101L, 1, "VIEW", 1L, 0L)), Map.of(), List.of())));

            var req = new QueryResourcesReq(
                "USER", "u-1", List.of("REPORT"), List.of("VIEW"),
                null, null, null, null, null);
            var resp = service.queryResources(1L, req);

            // 旧实现：两条 INSTANCE 条目（子行误报独立可访问）
            assertEquals(1, resp.items().size(), "子权限行不得进清单面（旧实现独立 INSTANCE 条目误报）");
            assertEquals("report:1", resp.items().get(0).resourceCode());
        }

        @Test
        void queryResourcesMustMapTreeSwitchesToPresentationExpansion() {
            // includeChildren/includeInherited → CHILDREN/PARENTS/BOTH（展示面展开，判定不受影响）
            lenient().when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
            lenient().when(typeResolutionService.batchResolveTypeCodes(any(), any(), any())).thenReturn(Map.of(1, "REPORT"));
            GrantFact fact = new GrantFact(401L, 20L, 1, 200L, 1L, false, null, null, false, null, "MANUAL");
            when(queryEngine.execute(any(QueryRequest.class))).thenReturn(result(presentResult(
                List.of(fact), List.of(fact), Map.of(), Map.of(), Map.of(), List.of())));

            service.queryResources(1L, new QueryResourcesReq(
                "USER", "u-1", List.of("REPORT"), List.of("VIEW"), null, null, null, true, null));
            assertEquals(PresentationExpansion.CHILDREN, capturedRequest().items().get(0).output().presentationExpansion());

            service.queryResources(1L, new QueryResourcesReq(
                "USER", "u-1", List.of("REPORT"), List.of("VIEW"), null, null, true, null, null));
            assertEquals(PresentationExpansion.PARENTS, capturedRequest().items().get(0).output().presentationExpansion());

            service.queryResources(1L, new QueryResourcesReq(
                "USER", "u-1", List.of("REPORT"), List.of("VIEW"), null, null, true, true, null));
            assertEquals(PresentationExpansion.BOTH, capturedRequest().items().get(0).output().presentationExpansion());
        }
    }

    // ===== helpers =====

    private QueryRequest capturedRequest() {
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(queryEngine, org.mockito.Mockito.atLeastOnce()).execute(captor.capture());
        assertTrue(captor.getAllValues().stream().flatMap(request -> request.items().stream())
            .noneMatch(item -> item.output().trace()), "外部资源与范围查询不开放 TRACE");
        return captor.getValue();
    }

    private static QueryResult result(GrantSetResult grantSet) {
        return new QueryResult("exec", LocalDateTime.now(), List.of(grantSet));
    }

    /** PRESENT 形态 GrantSetResult：raw/retained 双轨＋描述块（资源/操作/请求操作）＋父摘要。 */
    private static GrantSetResult presentResult(List<GrantFact> raw, List<GrantFact> retained,
                                                Map<Long, ResultDetails.ResourceDescription> resources,
                                                Map<Long, OperationDefinition> operations,
                                                Map<TypeOperation, OperationDefinition> requested,
                                                List<String> parentMatchedOperations) {
        return new GrantSetResult("scopes", GrantSetResult.CollectionStatus.PRESENT, coverage(true),
            details(raw, retained, resources, operations, requested, parentMatchedOperations));
    }

    private static ResultDetails details(List<GrantFact> raw, List<GrantFact> retained,
                                         Map<Long, ResultDetails.ResourceDescription> resources,
                                         Map<Long, OperationDefinition> operations,
                                         Map<TypeOperation, OperationDefinition> requested,
                                         List<String> parentMatchedOperations) {
        return new ResultDetails(
            Set.of(ResultDetails.DetailSection.FACTS_KEPT, ResultDetails.DetailSection.FACTS_RAW,
                ResultDetails.DetailSection.DESCRIPTIONS, ResultDetails.DetailSection.PARENT_CHECK),
            List.of(), List.of(),
            List.of(new StageFacts(Stage.GRANT_LIST, raw, retained,
                retained.isEmpty() ? StageFacts.Status.FILTERED_EMPTY : StageFacts.Status.PRESENT)),
            new ResultDetails.Descriptions(resources, Map.of(), operations, requested),
            List.of(), List.of(),
            new ResultDetails.ParentCheckSummary(parentMatchedOperations), null);
    }

    /** 完整 EVALUATE/ENFORCE＋FACT_COLLECTION 覆盖（ScopeCoverageProjector 前置校验要求）。 */
    private static EvaluationCoverage coverage(boolean complete) {
        return new EvaluationCoverage(EvaluationCoverage.SubjectResolution.USER_EFFECTIVE_WITH_MUTEX,
            EvaluationCoverage.ConditionCoverage.EVALUATED, EvaluationCoverage.MutexCoverage.EVALUATED,
            EvaluationCoverage.ParentCheckCoverage.PASSED, Set.of(Stage.GRANT_LIST), Map.of(),
            complete, EvaluationCoverage.AuthorizationStage.FACT_COLLECTION);
    }

    private static ResultDetails.ResourceDescription resource(Long id, Integer type, String code,
                                                              String codeType, String name) {
        return new ResultDetails.ResourceDescription(id, type, code, codeType, name, null, null, 0, null, null, null);
    }

    private static OperationDefinition operation(Long id, Integer type, String code, Long bit, Long inheritMask) {
        return new OperationDefinition(id, type, code, null, bit, inheritMask, 1L, null, null, null, null, null, null, 0L);
    }

}
