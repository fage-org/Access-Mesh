package cn.ac.fage.accessmesh.access.engine.service.impl;

import cn.ac.fage.accessmesh.perm.common.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.BatchAuthCheckReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.CheckInterfaceReq;
import cn.ac.fage.accessmesh.access.engine.dto.BatchAuthCheckResp;
import cn.ac.fage.accessmesh.access.engine.dto.CheckInterfaceResp;
import cn.ac.fage.accessmesh.access.engine.query.ByCode;
import cn.ac.fage.accessmesh.access.engine.query.ByEntityId;
import cn.ac.fage.accessmesh.access.engine.query.DecisionResult;
import cn.ac.fage.accessmesh.access.engine.query.EvaluationCoverage;
import cn.ac.fage.accessmesh.access.engine.query.GrantFact;
import cn.ac.fage.accessmesh.access.engine.query.Inheritance;
import cn.ac.fage.accessmesh.access.engine.query.OperationDefinition;
import cn.ac.fage.accessmesh.access.engine.query.QueryExecutionEngine;
import cn.ac.fage.accessmesh.access.engine.query.QueryItem;
import cn.ac.fage.accessmesh.access.engine.query.QueryRequest;
import cn.ac.fage.accessmesh.access.engine.query.QueryResult;
import cn.ac.fage.accessmesh.access.engine.query.ResultDetails;
import cn.ac.fage.accessmesh.access.engine.query.Stage;
import cn.ac.fage.accessmesh.access.engine.query.StageFacts;
import cn.ac.fage.accessmesh.access.engine.query.TargetClause;
import cn.ac.fage.accessmesh.access.engine.query.TargetSet;
import cn.ac.fage.accessmesh.access.engine.query.TypeFallback;
import cn.ac.fage.accessmesh.access.engine.query.TypeLevel;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 权限检查应用服务测试类
 * <p>
 * check/batchCheck 随 T-PERM-089、checkInterface 随 T-PERM-090 走新 execute
 * （外部响应断言保留作回归锁，另锁适配层请求形状：两档选择、inheritMode 映射、
 * 空白编码归一、原序/重复项、退化 context 保留键拒绝、LEGACY_API 共同集合
 * TARGET_SET 形状与空实体引用 TYPE_LEVEL 退化）。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class PermissionCheckAppServiceImplTest {

    @Mock private TypeResolutionService typeResolutionService;
    @Mock private QueryExecutionEngine queryEngine;
    @Mock private ResourceApiMappingMapper apiMappingMapper;

    private PermissionCheckAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PermissionCheckAppServiceImpl(typeResolutionService, queryEngine, apiMappingMapper);
    }

    @Test
    void shouldDenyWhenInterfaceNotRegistered() {
        CheckInterfaceReq req = new CheckInterfaceReq("USER", "u-1", "example-service", "POST", "/api/user/list", Map.of());
        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
        when(apiMappingMapper.selectForInterfaceCheck(any(), any(), any())).thenReturn(List.of());

        CheckInterfaceResp resp = service.checkInterface(1L, req);

        assertFalse(resp.allowed());
        assertEquals("API_NOT_REGISTERED", resp.reason());
        assertTrue(resp.matchedResources().isEmpty());
    }

    @Test
    void shouldAllowWhenAnyMatchedMappingPasses() {
        CheckInterfaceReq req = new CheckInterfaceReq("USER", "u-1", "example-service", "POST", "/api/user/list", Map.of());
        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);

        ResourceApiMapping m1 = new ResourceApiMapping();
        m1.setResourceEntityId(100L);
        m1.setPathPattern("/api/user/list");
        ResourceApiMapping m2 = new ResourceApiMapping();
        m2.setResourceEntityId(101L);
        m2.setPathPattern("/api/user/list");
        when(apiMappingMapper.selectForInterfaceCheck(any(), any(), any())).thenReturn(List.of(m1, m2));

        // LEGACY_API 共同集合（T-PERM-090）：全部匹配 API 组成一个 TARGET_SET 单 item，
        // 互斥候选同场判定（D02，不拆项 OR）；持 101 的 ACCESS 实例授权 → 允许
        GrantFact allowEntry = new GrantFact(401L, 200L, 1, 101L, 1L, false, null, null, false, null, "MANUAL");
        when(queryEngine.execute(any(QueryRequest.class))).thenReturn(result(
            interfaceAllowItem(List.of(allowEntry))));

        CheckInterfaceResp resp = service.checkInterface(1L, req);

        assertTrue(resp.allowed());
        assertEquals(1, resp.matchedResources().size());
        CheckInterfaceResp.MatchedResource matched = resp.matchedResources().get(0);
        assertTrue(matched.allowed());
        // T-API-003：resourceId 与 matched id 字段族恢复回传（裁剪态下这些断言失败）
        assertEquals(101L, matched.resourceId());
        assertEquals("api:user:list:2", matched.resourceCode());
        assertEquals("ACCESS", matched.operationCode());
        assertEquals(List.of(200L), matched.matchedRoleIds());
        assertEquals(List.of(401L), matched.matchedPermissionIds());

        // 适配层请求形状锁：单 item TARGET_SET＝两 clause（全部匹配 API 组成一个共同集合）、
        // SELF（API 扁平无判面继承）、TypeFallback.ALLOW（scopeAll 类型级放行，等价旧 INSTANCE 回退）
        QueryRequest request = capturedRequest();
        assertEquals(1, request.items().size());
        QueryItem item = request.items().get(0);
        assertEquals("checkInterface", item.key());
        assertInstanceOf(TargetSet.class, item.selection());
        TargetSet target = (TargetSet) item.selection();
        assertEquals(2, target.clauses().size());
        assertEquals(Set.of(new ByEntityId(100L), new ByEntityId(101L)),
            target.clauses().stream().map(TargetClause::resource).collect(java.util.stream.Collectors.toSet()));
        target.clauses().forEach(clause ->
            assertEquals(new cn.ac.fage.accessmesh.access.engine.query.TypeOperation("API", "ACCESS"), clause.operation()));
        assertEquals(Inheritance.SELF, target.inheritance());
        assertEquals(TypeFallback.ALLOW, target.typeFallback());
    }

    @Test
    void shouldDenyNoPermissionWhenResourceExists() {
        CheckInterfaceReq req = new CheckInterfaceReq("USER", "u-1", "example-service", "POST", "/api/user/list", Map.of());
        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);

        ResourceApiMapping mapping = new ResourceApiMapping();
        mapping.setResourceEntityId(100L);
        mapping.setPathPattern("/api/user/list");
        when(apiMappingMapper.selectForInterfaceCheck(any(), any(), any())).thenReturn(List.of(mapping));

        DecisionResult deny = DecisionResult.deny("checkInterface", DecisionResult.Reason.NO_PERMISSION,
            coverage(), ResultDetails.empty());
        when(queryEngine.execute(any(QueryRequest.class))).thenReturn(result(deny));

        CheckInterfaceResp resp = service.checkInterface(1L, req);

        assertFalse(resp.allowed());
        assertEquals("NO_PERMISSION", resp.reason());
    }

    @Test
    void checkInterfaceMustFallBackToTypeLevelWhenNoMappingCarriesEntity() {
        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);

        // 数据异常退化：映射无实体引用（旧引擎空目标集下仅查 scopeAll 的行为等价物）——
        // TYPE_LEVEL 单要求，不构造空 clause TARGET_SET（结构错误）
        ResourceApiMapping dangling = new ResourceApiMapping();
        dangling.setResourceEntityId(null);
        dangling.setPathPattern("/api/user/list");
        when(apiMappingMapper.selectForInterfaceCheck(any(), any(), any())).thenReturn(List.of(dangling));

        GrantFact scopeAll = new GrantFact(409L, 26L, 1, null, 1L, true, null, null, false, null, "MANUAL");
        when(queryEngine.execute(any(QueryRequest.class))).thenReturn(allowResult(List.of(26L), List.of(409L), scopeAll));

        CheckInterfaceResp resp = service.checkInterface(1L, new CheckInterfaceReq(
            "USER", "u-1", "example-service", "POST", "/api/user/list", Map.of()));

        assertTrue(resp.allowed());
        assertTrue(resp.matchedResources().isEmpty(), "scopeAll 类型级放行无实例行，matched 为空（沿旧形态）");
        assertInstanceOf(TypeLevel.class, capturedRequest().items().get(0).selection());
    }

    @Test
    void shouldDenyCheckWhenUserNotFound() {
        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(null);

        var req = new AuthCheckReq("USER", "u-1", "REPORT", "report:1", "VIEW", null, null, null, null, null, null, null, null);
        var resp = service.check(1L, req);

        assertFalse(resp.allowed());
        assertEquals("USER_NOT_FOUND", resp.reason());
    }

    @Test
    void shouldAllowCheckWhenPermissionGranted() {
        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
        GrantFact fact = new GrantFact(401L, 20L, 1, 200L, 2L, false, null, null, false, null, "MANUAL");
        when(queryEngine.execute(any(QueryRequest.class))).thenReturn(allowResult(List.of(20L), List.of(401L), fact));

        var req = new AuthCheckReq("USER", "u-1", "REPORT", "report:1", "VIEW", null, null, null, null, null, null, null, null);
        var resp = service.check(1L, req);

        assertTrue(resp.allowed());
        // T-API-003：matched id 字段族恢复回传（裁剪态下这些断言失败）
        assertEquals(List.of(20L), resp.matchedRoleIds());
        assertEquals(List.of(401L), resp.matchedPermissionIds());
        assertFalse(resp.conditionEvaluated());

        // 适配层请求形状锁：单 clause TARGET_SET（ByCode 目标）＋判面继承默认关
        QueryRequest request = capturedRequest();
        assertEquals(1, request.items().size());
        QueryItem item = request.items().get(0);
        assertInstanceOf(TargetSet.class, item.selection());
        TargetSet target = (TargetSet) item.selection();
        assertEquals(1, target.clauses().size());
        TargetClause clause = target.clauses().get(0);
        assertEquals(new ByCode("report:1", null, null), clause.resource());
        assertEquals(Inheritance.SELF, target.inheritance());
        assertEquals(TypeFallback.ALLOW, target.typeFallback());
    }

    @Test
    void shouldCarryConditionEvaluatedWhenRetainedFactHasCondition() {
        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
        GrantFact conditioned = new GrantFact(402L, 21L, 1, 200L, 2L, false, null, 77L, true, null, "MANUAL");
        when(queryEngine.execute(any(QueryRequest.class))).thenReturn(allowResult(List.of(21L), List.of(402L), conditioned));

        var req = new AuthCheckReq("USER", "u-1", "REPORT", "report:1", "VIEW", null, null, null, null, null, null, null, null);
        var resp = service.check(1L, req);

        assertTrue(resp.allowed());
        assertTrue(resp.conditionEvaluated());
    }

    @Test
    void checkMustBuildTypeLevelItemForBlankResourceCodeAndInheritanceFromInheritMode() {
        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
        GrantFact scopeAll = new GrantFact(403L, 22L, 1, null, 2L, true, null, null, false, null, "MANUAL");
        when(queryEngine.execute(any(QueryRequest.class))).thenReturn(allowResult(List.of(22L), List.of(403L), scopeAll));

        // 空白 resourceCode 归一 TYPE_LEVEL（2026-09-27 用户拍板）；inheritMode=PARENT → 判面继承开
        var blank = new AuthCheckReq("USER", "u-1", "REPORT", "", "VIEW", null, null, null, null, null, null, null, null);
        assertTrue(service.check(1L, blank).allowed());
        QueryRequest request = capturedRequest();
        assertEquals(1, request.items().size());
        assertInstanceOf(TypeLevel.class, request.items().get(0).selection());

        var inherited = new AuthCheckReq("USER", "u-1", "REPORT", "report:1", "VIEW", null, null, "PARENT", null, null, null, null, null);
        assertTrue(service.check(1L, inherited).allowed());
        TargetSet target = (TargetSet) capturedRequest().items().get(0).selection();
        assertEquals(Inheritance.SELF_AND_ANCESTORS, target.inheritance());
    }

    @Test
    void checkMustRejectReservedContextKeysWithStructuralError() {
        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);

        // 2026-09-27 用户拍板：顶层 evaluatedAt/timestamp 保留键不再静默处理——CallerContext
        // 结构拒绝（500）；clientIp 为 SDK 文档化契约键，提取为受信 IP 后不受影响
        var forged = new AuthCheckReq("USER", "u-1", "REPORT", "report:1", "VIEW",
            null, null, null, null, null, null, null, Map.of("timestamp", "2026-01-01T00:00:00"));
        org.junit.jupiter.api.Assertions.assertThrows(
            cn.ac.fage.accessmesh.access.engine.query.QueryValidationException.class,
            () -> service.check(1L, forged));

        var withClientIp = new AuthCheckReq("USER", "u-1", "REPORT", "report:1", "VIEW",
            null, null, null, null, null, null, null, Map.of("clientIp", "10.0.0.9"));
        GrantFact fact = new GrantFact(404L, 23L, 1, 200L, 2L, false, null, null, false, null, "MANUAL");
        when(queryEngine.execute(any(QueryRequest.class))).thenReturn(allowResult(List.of(23L), List.of(404L), fact));
        assertTrue(service.check(1L, withClientIp).allowed());
        assertEquals("10.0.0.9", capturedRequest().context().clientIp());
    }

    @Test
    void checkMustNormalizeBlankParentCodeToNoParentInsteadOfStructuralRejection() {
        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
        GrantFact fact = new GrantFact(406L, 25L, 1, 200L, 2L, false, null, null, false, null, "MANUAL");
        when(queryEngine.execute(any(QueryRequest.class))).thenReturn(allowResult(List.of(25L), List.of(406L), fact));

        // 外评 P2（2026-09-27 claude+grok）：父编码空白串不得构造 ByCode（结构拒绝→500）；
        // 归一无父＝旧链路空白父编码解析落空（父判定不命中、主行照常判定），depend_on 子行 fail-closed
        var req = new AuthCheckReq("USER", "u-1", "REPORT", "report:1", "VIEW",
            null, null, null, "REPORT", " ", "default", List.of("VIEW"), null);
        var resp = service.check(1L, req);

        assertTrue(resp.allowed());
        TargetSet target = (TargetSet) capturedRequest().items().get(0).selection();
        assertNull(target.parent());
    }

    @Test
    void batchCheckMustCarryMatchedRecordsAndEmptyOnUserNotFound() {
        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);

        // T-PERM-089：batchCheck 一次 execute 批量表达（禁循环 N 次）——stub 新入口，
        // 不作等价证据（引擎批量语义由容器轨等价差分锁钉死）
        GrantFact fact = new GrantFact(401L, 20L, 1, 200L, 2L, false, null, null, false, null, "MANUAL");
        when(queryEngine.execute(any(QueryRequest.class))).thenReturn(result(
            allowItem("0", List.of(20L), List.of(401L), fact)));

        var req = new BatchAuthCheckReq("USER", "u-1",
            List.of(new BatchAuthCheckReq.AuthCheckItem("REPORT", "report:1", "VIEW", null, null, null)),
            null, null, null, null, Map.of());
        BatchAuthCheckResp resp = service.batchCheck(1L, req);

        assertEquals(1, resp.items().size());
        BatchAuthCheckResp.AuthCheckItemResult item = resp.items().get(0);
        assertTrue(item.allowed());
        // T-API-003：单项结果记录恢复回传（裁剪态下这些断言失败）
        assertEquals(List.of(20L), item.matchedRoleIds());
        assertEquals(List.of(401L), item.matchedPermissionIds());

        // 主体不存在分支：拒绝 + 结果记录为空列表（非 null）
        when(typeResolutionService.resolveUserId(1L, "USER", "ghost")).thenReturn(null);
        BatchAuthCheckResp notFound = service.batchCheck(1L, new BatchAuthCheckReq("USER", "ghost",
            List.of(new BatchAuthCheckReq.AuthCheckItem("REPORT", "report:1", "VIEW", null, null, null)),
            null, null, null, null, Map.of()));
        assertFalse(notFound.items().get(0).allowed());
        assertEquals("USER_NOT_FOUND", notFound.items().get(0).reason());
        assertEquals(List.of(), notFound.items().get(0).matchedRoleIds());
        assertEquals(List.of(), notFound.items().get(0).matchedPermissionIds());
    }

    @Test
    void batchCheckMustPreserveInputOrderAndDuplicateItemsWithIndexKeys() {
        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
        GrantFact fact = new GrantFact(405L, 24L, 1, 200L, 2L, false, null, null, false, null, "MANUAL");
        DecisionResult allow = allowItem("0", List.of(24L), List.of(405L), fact);
        DecisionResult deny = DecisionResult.deny("1", DecisionResult.Reason.NO_PERMISSION,
            coverage(), ResultDetails.empty());
        when(queryEngine.execute(any(QueryRequest.class))).thenReturn(result(allow, deny, allow));

        // 原序 + 重复项（同输入两次）：下标 key 天然唯一，结果按输入序回填
        BatchAuthCheckResp resp = service.batchCheck(1L, new BatchAuthCheckReq("USER", "u-1",
            List.of(new BatchAuthCheckReq.AuthCheckItem("REPORT", "report:1", "VIEW", null, null, null),
                new BatchAuthCheckReq.AuthCheckItem("REPORT", "report:9", "VIEW", null, null, null),
                new BatchAuthCheckReq.AuthCheckItem("REPORT", "report:1", "VIEW", null, null, null)),
            null, null, null, null, Map.of()));

        assertEquals(3, resp.items().size());
        assertTrue(resp.items().get(0).allowed());
        assertFalse(resp.items().get(1).allowed());
        assertEquals("NO_PERMISSION", resp.items().get(1).reason());
        assertTrue(resp.items().get(2).allowed());
        QueryRequest request = capturedRequest();
        assertEquals(List.of("0", "1", "2"), request.items().stream().map(QueryItem::key).toList());
    }

    private QueryRequest capturedRequest() {
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(queryEngine, org.mockito.Mockito.atLeastOnce()).execute(captor.capture());
        return captor.getValue();
    }

    private static QueryResult result(DecisionResult... decisions) {
        List<cn.ac.fage.accessmesh.access.engine.query.ItemResult> items = List.of(decisions);
        return new QueryResult("exec", LocalDateTime.now(), items);
    }

    private static QueryResult allowResult(List<Long> roleIds, List<Long> permissionIds, GrantFact retained) {
        return result(allowItem("check", roleIds, permissionIds, retained));
    }

    private static DecisionResult allowItem(String key, List<Long> roleIds, List<Long> permissionIds,
                                            GrantFact retained) {
        return DecisionResult.allow(key, coverage(), new ResultDetails(
            Set.of(ResultDetails.DetailSection.MATCHED_IDS, ResultDetails.DetailSection.FACTS_KEPT),
            roleIds, permissionIds, List.of(new StageFacts(Stage.INSTANCE, List.of(retained),
                List.of(retained), StageFacts.Status.PRESENT))));
    }

    /** checkInterface 允许项：保留事实（INSTANCE 阶段）＋描述块（资源业务键与操作目录）。 */
    private static DecisionResult interfaceAllowItem(List<GrantFact> retained) {
        ResultDetails.Descriptions descriptions = new ResultDetails.Descriptions(
            Map.of(101L, new ResultDetails.ResourceDescription(101L, 1, "api:user:list:2",
                "default", "用户列表API-2", null, null, 0, null, null, null)),
            Map.of(),
            Map.of(300L, new OperationDefinition(300L, 1, "ACCESS", "接口访问", 1L, 0L, 1L,
                null, null, null, null, null, null, 0L)),
            Map.of());
        return DecisionResult.allow("checkInterface", coverage(), new ResultDetails(
            Set.of(ResultDetails.DetailSection.MATCHED_IDS, ResultDetails.DetailSection.FACTS_KEPT,
                ResultDetails.DetailSection.DESCRIPTIONS),
            retained.stream().map(GrantFact::roleId).filter(Objects::nonNull).distinct().toList(),
            retained.stream().map(GrantFact::permissionId).filter(Objects::nonNull).distinct().toList(),
            List.of(new StageFacts(Stage.INSTANCE, List.of(), retained, StageFacts.Status.PRESENT)),
            descriptions, List.of(), List.of(), new ResultDetails.ParentCheckSummary(List.of()), null));
    }

    private static EvaluationCoverage coverage() {
        return new EvaluationCoverage(EvaluationCoverage.SubjectResolution.USER_EFFECTIVE_WITH_MUTEX,
            EvaluationCoverage.ConditionCoverage.EVALUATED, EvaluationCoverage.MutexCoverage.EVALUATED,
            EvaluationCoverage.ParentCheckCoverage.NOT_REQUIRED, Set.of(Stage.INSTANCE), Map.of(),
            true, EvaluationCoverage.AuthorizationStage.FINAL_DECISION);
    }
}
