package cn.ac.fage.accessmesh.access.engine.service.impl;

import cn.ac.fage.accessmesh.perm.common.dto.req.UserEffectivePermissionCodesReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.UserEffectivePermissionCodesResp;
import cn.ac.fage.accessmesh.access.engine.constant.OperationCode;
import cn.ac.fage.accessmesh.access.engine.dto.PermViewResult;
import cn.ac.fage.accessmesh.access.engine.query.EvaluationCoverage.AuthorizationStage;
import cn.ac.fage.accessmesh.access.engine.query.EvaluationCoverage.ConditionCoverage;
import cn.ac.fage.accessmesh.access.engine.query.EvaluationCoverage.MutexCoverage;
import cn.ac.fage.accessmesh.access.engine.query.EvaluationCoverage.ParentCheckCoverage;
import cn.ac.fage.accessmesh.access.engine.query.EvaluationCoverage.SubjectResolution;
import cn.ac.fage.accessmesh.access.engine.query.GrantFact;
import cn.ac.fage.accessmesh.access.engine.query.GrantSetResult;
import cn.ac.fage.accessmesh.access.engine.query.PresentationEntry.Derivation;
import cn.ac.fage.accessmesh.access.engine.query.QueryExecutionEngine;
import cn.ac.fage.accessmesh.access.engine.query.QueryRequest;
import cn.ac.fage.accessmesh.access.engine.query.QueryResult;
import cn.ac.fage.accessmesh.access.engine.query.ResultDetails;
import cn.ac.fage.accessmesh.access.engine.query.ResultDetails.EffectiveOperationEntry;
import cn.ac.fage.accessmesh.access.engine.query.Stage;
import cn.ac.fage.accessmesh.access.engine.query.StageFacts;
import cn.ac.fage.accessmesh.access.type.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.engine.service.PermissionViewAppService;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.engine.query.QueryGate;
import cn.ac.fage.accessmesh.access.infrastructure.util.OperatorContext;
import cn.ac.fage.accessmesh.access.engine.util.PermViewAssembler;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 权限视图应用服务测试类
 * <p>
 * 原权限排查视图用例族（effective-permissions/role-permissions/explain/resource-tree）
 * 已随七端点删除（T-PERM-059，2026-09-10）；现仅覆盖登录权限串链路
 * （effective-permission-codes + 菜单派生资源访问事实），T-PERM-091 起走新 execute。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class PermissionViewAppServiceImplTest {

    @Mock private ResourceEntityMapper resourceEntityMapper;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private QueryExecutionEngine queryEngine;
    @Mock private QueryGate queryGate;
    @Mock private PermViewAssembler permViewAssembler;

    private PermissionViewAppServiceImpl service;
    private MockedStatic<OperatorContext> operatorContextMock;

    @BeforeEach
    void setUp() {
        operatorContextMock = mockStatic(OperatorContext.class);
        operatorContextMock.when(OperatorContext::getOperatorId).thenReturn(1L);

        service = new PermissionViewAppServiceImpl(
            resourceEntityMapper, typeResolutionService, queryEngine, queryGate, permViewAssembler);
    }

    @AfterEach
    void tearDown() {
        operatorContextMock.close();
    }

    @Test
    void getEffectivePermissionCodesShouldReturnInheritedEffectiveOperationCodes() {
        // 自查场景：operator 投影主体=1001，subject "1" 投影=1001；buildEffectiveView 无 USER:VIEW 门禁
        when(typeResolutionService.resolveUserId(1L, "LOCAL_USER", "1")).thenReturn(1L);

        GrantFact entry = new GrantFact(501L, 20L, 1, 200L, 4L, false, false, null, false, null, "DIRECT");
        // 有效操作投影（方向优先口径，设计 §6.4）：源行授予 UPDATE（derivation=ORIGINAL），
        // 经 inheritMask 覆盖 VIEW 的投影行 derivation=OPERATION_COVERAGE——消费不按
        // derivation 筛选，覆盖操作与原授操作都进权限码（T-PERM-091 验收第 3 条回归锁）
        List<EffectiveOperationEntry> effectiveEntries = List.of(
            new EffectiveOperationEntry(501L, 20L, 200L, Derivation.ORIGINAL, 1, 4L, "UPDATE", 6L, "UPDATE", 4L),
            new EffectiveOperationEntry(501L, 20L, 200L, Derivation.OPERATION_COVERAGE, 1, 4L, "UPDATE", 6L, "VIEW", 2L));
        GrantSetResult item = grantSetResult(List.of(entry), effectiveEntries);
        when(queryEngine.execute(any(QueryRequest.class))).thenReturn(queryResult(item));
        when(permViewAssembler.assemble(eq(1L), eq(item), any()))
            .thenReturn(PermViewResult.builder()
                .entries(List.of(entry))
                .effectiveOperationEntries(effectiveEntries)
                .build());
        when(typeResolutionService.batchResolveTypeCodes(1L, "resource_type", Set.of(1)))
            .thenReturn(Map.of(1, "USER"));

        UserEffectivePermissionCodesResp resp = service.getEffectivePermissionCodes(
            1L,
            new UserEffectivePermissionCodesReq("LOCAL_USER", "1", List.of("USER"))
        );

        assertTrue(resp.permissions().contains("USER:UPDATE"));
        assertTrue(resp.permissions().contains("USER:VIEW"));
    }

    @Test
    void getEffectiveResourceAccessShouldCollectScopeAllTypesAndInstanceIds() {
        // 自查：operator 投影主体=1001，subject "1" 投影=1001；buildEffectiveView 无 USER:VIEW 门禁
        when(typeResolutionService.resolveUserId(1L, "LOCAL_USER", "1")).thenReturn(1L);

        GrantFact scopeAllFact = new GrantFact(501L, 20L, 2, null, 4L, true, false, null, false, null, "DIRECT");
        GrantFact instanceFact = new GrantFact(502L, 20L, 1, 200L, 2L, false, false, null, false, null, "DIRECT");
        List<EffectiveOperationEntry> effectiveEntries = List.of(
            new EffectiveOperationEntry(501L, 20L, null, Derivation.ORIGINAL, 2, 4L, "VIEW", 4L, "VIEW", 2L),
            new EffectiveOperationEntry(502L, 20L, 200L, Derivation.ORIGINAL, 1, 2L, "VIEW", 2L, "VIEW", 1L));
        GrantSetResult item = grantSetResult(List.of(scopeAllFact, instanceFact), effectiveEntries);
        when(queryEngine.execute(any(QueryRequest.class))).thenReturn(queryResult(item));
        when(permViewAssembler.assemble(eq(1L), eq(item), any()))
            .thenReturn(PermViewResult.builder()
                .entries(List.of(scopeAllFact, instanceFact))
                .effectiveOperationEntries(effectiveEntries)
                .build());

        PermissionViewAppService.EffectiveResourceAccess access = service.getEffectiveResourceAccess(
            1L, new UserEffectivePermissionCodesReq("LOCAL_USER", "1", List.of("USER", "ORG")));

        // scopeAll 行 → allScopeTypes=2（resourceEntityId 为 null 不进实例集）；
        // 实例行 → resourceEntityIds=200；instanceIdsByType 不含子孙扩展（验收第 2 条）
        assertTrue(access.allScopeTypes().contains(2));
        assertTrue(access.resourceEntityIds().contains(200L));
        assertFalse(access.allScopeTypes().contains(1));
        assertFalse(access.resourceEntityIds().contains(501L));
        // 判定面继承（读过滤面）锁：授权实例集必须经子孙扩展 CTE——mock 默认空列表会让
        // 无 verify 的实现恒绿（grok 外评指出），verify 钉住调用经被测路径
        verify(resourceEntityMapper).selectDescendantIdsBatch(1L, Set.of(200L));
    }

    @Test
    void getEffectivePermissionCodesForManageShouldAllowSelfWithoutUserView() {
        // 自查：operator 投影主体=1001（sys=1 转换），subject "1" 投影=1001 → 豁免 USER:VIEW，不调用 queryGate
        when(typeResolutionService.resolveUserId(1L, "LOCAL_USER", "1")).thenReturn(1L);
        // 无有效角色：引擎 NO_ROLE → 视图 null → 空权限串
        when(queryEngine.execute(any(QueryRequest.class))).thenReturn(noRoleResult());

        UserEffectivePermissionCodesResp resp = service.getEffectivePermissionCodesForManage(
            1L, new UserEffectivePermissionCodesReq("LOCAL_USER", "1", List.of("USER")));

        assertNotNull(resp);
        assertTrue(resp.permissions().isEmpty());
        verify(queryGate, never()).hasPermissionByCode(anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void getEffectivePermissionCodesForManageShouldDenyOthersWithoutUserView() {
        // 查他人：operator 投影主体=1001，subject "2" 投影=1002，无 USER:VIEW → SecurityException（门禁用 abstract 主体，非 sys id）
        when(typeResolutionService.resolveUserId(1L, "LOCAL_USER", "2")).thenReturn(1002L);
        when(queryGate.hasPermissionByCode(1L, 1L, ResourceTypeCode.USER, "1002", OperationCode.VIEW))
            .thenReturn(false);

        assertThrows(SecurityException.class, () ->
            service.getEffectivePermissionCodesForManage(
                1L, new UserEffectivePermissionCodesReq("LOCAL_USER", "2", List.of("USER"))));
    }

    @Test
    void getEffectivePermissionCodesForManageShouldAllowOthersWithUserView() {
        // 查他人：operator 投影主体=1001，subject "2" 投影=1002，有 USER:VIEW → 正常下发
        when(typeResolutionService.resolveUserId(1L, "LOCAL_USER", "2")).thenReturn(1002L);
        when(queryGate.hasPermissionByCode(1L, 1L, ResourceTypeCode.USER, "1002", OperationCode.VIEW))
            .thenReturn(true);
        when(queryEngine.execute(any(QueryRequest.class))).thenReturn(noRoleResult());

        UserEffectivePermissionCodesResp resp = service.getEffectivePermissionCodesForManage(
            1L, new UserEffectivePermissionCodesReq("LOCAL_USER", "2", List.of("USER")));

        assertNotNull(resp);
        assertTrue(resp.permissions().isEmpty());
    }

    // ========== 夹具（新 execute 结果构造） ==========

    private GrantSetResult grantSetResult(List<GrantFact> kept, List<EffectiveOperationEntry> effective) {
        StageFacts stage = new StageFacts(Stage.GRANT_LIST, kept, kept, StageFacts.Status.PRESENT);
        ResultDetails details = new ResultDetails(Set.of(), List.of(), List.of(), List.of(stage),
            new ResultDetails.Descriptions(Map.of(), Map.of(), Map.of(), Map.of()), effective, List.of(),
            new ResultDetails.ParentCheckSummary(List.of()),
            new ResultDetails.ExecutionTrace(List.of(), List.of(), List.of(), List.of()));
        return new GrantSetResult("view", GrantSetResult.CollectionStatus.PRESENT, coverage(), details);
    }

    private QueryResult queryResult(GrantSetResult item) {
        return new QueryResult("view-exec", LocalDateTime.now(), List.of(item));
    }

    private QueryResult noRoleResult() {
        GrantSetResult item = new GrantSetResult("view", GrantSetResult.CollectionStatus.NO_ROLE,
            coverage(), ResultDetails.empty());
        return queryResult(item);
    }

    /** EVALUATE+ENFORCE 全量评估覆盖形态（值不被消费，仅满足非空契约）。 */
    private static cn.ac.fage.accessmesh.access.engine.query.EvaluationCoverage coverage() {
        return new cn.ac.fage.accessmesh.access.engine.query.EvaluationCoverage(
            SubjectResolution.USER_EFFECTIVE_WITH_MUTEX, ConditionCoverage.EVALUATED,
            MutexCoverage.EVALUATED, ParentCheckCoverage.NOT_REQUIRED,
            Set.of(Stage.GRANT_LIST), Map.of(), true, AuthorizationStage.FACT_COLLECTION);
    }
}
