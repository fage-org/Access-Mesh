package cn.ac.fage.accessmesh.access.grant.service.domain.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.projection.PermConstants;
import cn.ac.fage.accessmesh.access.resource.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.access.type.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.grant.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.access.engine.query.Evaluation;
import cn.ac.fage.accessmesh.access.engine.query.EvaluationCoverage;
import cn.ac.fage.accessmesh.access.engine.query.FactDetail;
import cn.ac.fage.accessmesh.access.engine.query.GrantFact;
import cn.ac.fage.accessmesh.access.engine.query.GrantSetResult;
import cn.ac.fage.accessmesh.access.engine.query.ListGrantRead;
import cn.ac.fage.accessmesh.access.engine.query.OutputSpec;
import cn.ac.fage.accessmesh.access.engine.query.PresentationExpansion;
import cn.ac.fage.accessmesh.access.engine.query.QueryExecutionEngine;
import cn.ac.fage.accessmesh.access.engine.query.QueryItem;
import cn.ac.fage.accessmesh.access.engine.query.QueryRequest;
import cn.ac.fage.accessmesh.access.engine.query.QueryResult;
import cn.ac.fage.accessmesh.access.engine.query.ReadOptions;
import cn.ac.fage.accessmesh.access.engine.query.ResultDetails;
import cn.ac.fage.accessmesh.access.engine.query.Stage;
import cn.ac.fage.accessmesh.access.engine.query.StageFacts;
import cn.ac.fage.accessmesh.access.type.service.domain.OperationPermissionDomainService;
import cn.ac.fage.accessmesh.access.grant.service.domain.PermissionGrantDomainService;
import cn.ac.fage.accessmesh.access.grant.service.domain.PermissionGrantDomainService.GrantCheckKey;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionGrantDomainServiceImplTest {

    @Mock
    private TypeResolutionService typeResolutionService;
    @Mock
    private OperationPermissionDomainService operationPermissionMapper;
    @Mock
    private QueryExecutionEngine queryEngine;
    @Mock
    private cn.ac.fage.accessmesh.access.grant.mapper.RoleResourcePermissionMapper roleResourcePermissionMapper;
    @Mock
    private cn.ac.fage.accessmesh.access.type.service.domain.TypeDefinitionDomainService typeDefinitionMapper;

    private PermissionGrantDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PermissionGrantDomainServiceImpl(
            typeResolutionService,
            queryEngine,
            operationPermissionMapper,
            roleResourcePermissionMapper,
            typeDefinitionMapper
        );
    }

    @Test
    void canGrantPermissionShouldAllowInheritedGrantCoverage() {
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("MENU")))
            .thenReturn(Map.of("MENU", 1));

        OperationPermission viewOp = operation(101L, 1, "VIEW", 1L, 0L);
        OperationPermission manageOp = operation(102L, 1, "MANAGE", 8L, 1L);

        when(operationPermissionMapper.selectByTenantResourceTypesAndOpCodes(1L, Set.of(1), Set.of("VIEW")))
            .thenReturn(List.of(viewOp));
        when(typeResolutionService.batchResolveResourceIds(any(), any()))
            .thenReturn(Map.of(new ResourceResolveKey("MENU", "sys:user", PermConstants.CodeType.DEFAULT, null), 100L));

        // T-PERM-091 迁新 execute：授权事实来自引擎 GRANT_LIST（PRESERVE+SKIP）保留事实
        // （MANAGE 授予行 canGrant=true，经 inheritMask 覆盖 VIEW 目标操作）；授予目录领域自查
        GrantFact grantedEntry = fact(500L, 20L, 1, 100L, 8L, false, true, null, false, null, "MANUAL");
        when(queryEngine.execute(any(QueryRequest.class))).thenReturn(grantResult(grantedEntry));
        when(operationPermissionMapper.selectByTenantAndResourceTypes(1L, Set.of(1)))
            .thenReturn(List.of(viewOp, manageOp));

        boolean allowed = service.canGrantPermission(1L, 10L, "MENU", "sys:user", PermConstants.CodeType.DEFAULT, "VIEW", false, null);

        assertTrue(allowed);
    }

    @Test
    void canGrantPermissionShouldDenyWhenCodeTypeDiffers() {
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("MENU")))
            .thenReturn(Map.of("MENU", 1));

        OperationPermission viewOp = operation(101L, 1, "VIEW", 1L, 0L);
        when(operationPermissionMapper.selectByTenantResourceTypesAndOpCodes(1L, Set.of(1), Set.of("VIEW")))
            .thenReturn(List.of(viewOp));
        // 请求 codeType=ID 解析到不同实体（200），与授予行实体（100）不匹配 → 拒绝
        when(typeResolutionService.batchResolveResourceIds(any(), any()))
            .thenReturn(Map.of(new ResourceResolveKey("MENU", "sys:user", "ID", null), 200L));

        GrantFact grantedEntry = fact(500L, 20L, 1, 100L, 1L, false, true, null, false, null, "MANUAL");
        when(queryEngine.execute(any(QueryRequest.class))).thenReturn(grantResult(grantedEntry));
        when(operationPermissionMapper.selectByTenantAndResourceTypes(1L, Set.of(1)))
            .thenReturn(List.of(viewOp));

        boolean allowed = service.canGrantPermission(1L, 10L, "MENU", "sys:user", "ID", "VIEW", false, null);

        assertFalse(allowed);
    }

    @Test
    void shouldRejectSecondManualGrantEvenWhenConditionDiffers() {
        RoleResourcePermission existing = permission(501L, "MANUAL", null, 2L);
        RoleResourcePermission candidate = permission(null, "MANUAL", 99L, 2L);

        BizException exception = assertThrows(BizException.class, () ->
            service.validateSingleManualGrants(List.of(existing), List.of(candidate), Set.of()));

        assertEquals(20033, exception.getErrorCode());
    }

    @Test
    void shouldAllowReplacingRemovedManualGrantAndCoexistingAutoDependency() {
        RoleResourcePermission removed = permission(501L, "MANUAL", null, 2L);
        RoleResourcePermission autoDependency = permission(502L, "AUTO_DEP", null, 2L);
        RoleResourcePermission replacement = permission(null, "MANUAL", 99L, 2L);

        assertDoesNotThrow(() -> service.validateSingleManualGrants(
            List.of(removed, autoDependency), List.of(replacement), Set.of(501L)));
    }

    @Test
    void shouldRejectCombinationBitsForNewManualGrant() {
        RoleResourcePermission combination = permission(null, "MANUAL", null, 6L);

        BizException exception = assertThrows(BizException.class, () ->
            service.validateSingleManualGrants(List.of(), List.of(combination), Set.of()));

        assertEquals(20027, exception.getErrorCode());
    }

    @Test
    void shouldScopeChildGrantUniquenessToParentPermission() {
        RoleResourcePermission existingChild = permission(601L, "MANUAL", null, 2L);
        existingChild.setDependOn(701L);
        RoleResourcePermission sameParent = permission(null, "MANUAL", 99L, 2L);
        sameParent.setDependOn(701L);
        RoleResourcePermission otherParent = permission(null, "MANUAL", 99L, 2L);
        otherParent.setDependOn(702L);

        BizException exception = assertThrows(BizException.class, () ->
            service.validateSingleManualGrants(List.of(existingChild), List.of(sameParent), Set.of()));
        assertEquals(20033, exception.getErrorCode());
        assertDoesNotThrow(() ->
            service.validateSingleManualGrants(List.of(existingChild), List.of(otherParent), Set.of()));
    }

    @Test
    void shouldRejectConditionalPermissionWithCanGrantEnabled() {
        RoleResourcePermission permission = permission(null, "MANUAL", 99L, 2L);
        permission.setCanGrant(true);

        BizException exception = assertThrows(BizException.class, () ->
            service.validateSingleManualGrants(List.of(), List.of(permission), Set.of()));

        assertEquals(20041, exception.getErrorCode());
    }

    @Test
    void shouldReturnInvalidResultInsteadOfThrowingForMissingOperationCode() {
        GrantCheckKey invalid = new GrantCheckKey("MENU", "sys:user", "default", null, false);

        Map<String, PermissionGrantDomainService.GrantCheckResult> results =
            service.checkCanGrant(1L, 10L, Set.of(invalid), null);

        assertEquals("INVALID_PERMISSION_KEY", results.values().iterator().next().reason());
        verify(queryEngine, never()).execute(any(QueryRequest.class));
    }

    // ========== T-PERM-091 转授四例（设计 §10.2 T01~T04，迁新 execute 回归锁） ==========

    /**
     * T01 转授授权撤销后清单 DB 读取：checkCanGrant 的引擎请求必须钉 DATABASE 读来源
     * （不读不回填 ROLE_PERM_SNAPSHOT——撤权后 TTL 陈旧/旧读回填竞态可放行已撤销的
     * 转授资格，权限提升），且评估口径=PRESERVE+SKIP（转授资格看原始行，不评估条件/互斥）。
     */
    @Test
    void t01CanGrantShouldReadGrantsFromDatabaseWithPreserveSkipEvaluation() {
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("MENU")))
            .thenReturn(Map.of("MENU", 1));
        OperationPermission viewOp = operation(101L, 1, "VIEW", 1L, 0L);
        when(operationPermissionMapper.selectByTenantResourceTypesAndOpCodes(1L, Set.of(1), Set.of("VIEW")))
            .thenReturn(List.of(viewOp));
        when(typeResolutionService.batchResolveResourceIds(any(), any()))
            .thenReturn(Map.of(new ResourceResolveKey("MENU", "sys:user", PermConstants.CodeType.DEFAULT, null), 100L));
        GrantFact grantedEntry = fact(500L, 20L, 1, 100L, 1L, false, true, null, false, null, "MANUAL");
        when(queryEngine.execute(any(QueryRequest.class))).thenReturn(grantResult(grantedEntry));
        when(operationPermissionMapper.selectByTenantAndResourceTypes(1L, Set.of(1)))
            .thenReturn(List.of(viewOp));

        service.canGrantPermission(1L, 10L, "MENU", "sys:user", PermConstants.CodeType.DEFAULT, "VIEW", false, null);

        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(queryEngine).execute(captor.capture());
        QueryRequest request = captor.getValue();
        assertEquals(ListGrantRead.DATABASE, request.reads().listGrantRead());
        QueryItem item = request.items().get(0);
        assertEquals(Evaluation.preserveSkip(), item.evaluation());
        assertEquals(FactDetail.KEPT, item.output().factDetail());
        assertFalse(item.output().descriptions());
        assertFalse(item.output().effectiveOperations());
        assertEquals(PresentationExpansion.NONE, item.output().presentationExpansion());
    }

    /**
     * T02 可覆盖行不可转授，另行 canGrant 不覆盖：不得拼接两行资格——
     * MANAGE 覆盖 VIEW 但 canGrant=false 的行与 canGrant=true 但不覆盖 VIEW 的行
     * 同场时，目标 VIEW 的转授资格必须判 NO_GRANT_RIGHT（同一条真实授权同行验证）。
     */
    @Test
    void t02CanGrantShouldNotCombineCoverageRowWithUnrelatedGrantRightRow() {
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("MENU")))
            .thenReturn(Map.of("MENU", 1));
        OperationPermission viewOp = operation(101L, 1, "VIEW", 1L, 0L);
        OperationPermission manageOp = operation(102L, 1, "MANAGE", 8L, 1L);
        OperationPermission syncOp = operation(103L, 1, "SYNC", 4L, 0L);
        when(operationPermissionMapper.selectByTenantResourceTypesAndOpCodes(1L, Set.of(1), Set.of("VIEW")))
            .thenReturn(List.of(viewOp));
        when(typeResolutionService.batchResolveResourceIds(any(), any()))
            .thenReturn(Map.of(new ResourceResolveKey("MENU", "sys:user", PermConstants.CodeType.DEFAULT, null), 100L));
        // 行 A：MANAGE（覆盖 VIEW）但 canGrant=false；行 B：SYNC（不覆盖 VIEW）canGrant=true
        GrantFact coveringRow = fact(500L, 20L, 1, 100L, 8L, false, false, null, false, null, "MANUAL");
        GrantFact grantRightRow = fact(501L, 21L, 1, 100L, 4L, false, true, null, false, null, "MANUAL");
        when(queryEngine.execute(any(QueryRequest.class))).thenReturn(grantResult(coveringRow, grantRightRow));
        when(operationPermissionMapper.selectByTenantAndResourceTypes(1L, Set.of(1)))
            .thenReturn(List.of(viewOp, manageOp, syncOp));

        Map<String, PermissionGrantDomainService.GrantCheckResult> results = service.checkCanGrant(
            1L, 10L, Set.of(new GrantCheckKey("MENU", "sys:user", PermConstants.CodeType.DEFAULT, "VIEW", false)), null);

        assertEquals("NO_GRANT_RIGHT", results.values().iterator().next().reason());
    }

    /**
     * T03 操作者无目标类型授权、目标操作实际存在：reason 必须是 NO_PERMISSION 而非
     * INVALID_OPERATION——目标操作解析独立于操作者持有面（T-PERM-062 起领域自查装载）。
     */
    @Test
    void t03CanGrantShouldReturnNoPermissionNotInvalidOperationWhenTargetOpExists() {
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("MENU")))
            .thenReturn(Map.of("MENU", 1));
        // 目标操作存在（类型 1 的 VIEW 定义可解析）
        OperationPermission viewOp = operation(101L, 1, "VIEW", 1L, 0L);
        when(operationPermissionMapper.selectByTenantResourceTypesAndOpCodes(1L, Set.of(1), Set.of("VIEW")))
            .thenReturn(List.of(viewOp));
        when(typeResolutionService.batchResolveResourceIds(any(), any()))
            .thenReturn(Map.of(new ResourceResolveKey("MENU", "sys:user", PermConstants.CodeType.DEFAULT, null), 100L));
        // 操作者仅持类型 5 的授权行（无类型 1 任何行）——有角色有授权行，对目标类型零覆盖
        OperationPermission otherView = operation(202L, 5, "VIEW", 1L, 0L);
        GrantFact otherEntry = fact(500L, 20L, 5, null, 1L, true, false, null, false, null, "MANUAL");
        when(queryEngine.execute(any(QueryRequest.class))).thenReturn(grantResult(otherEntry));
        when(operationPermissionMapper.selectByTenantAndResourceTypes(1L, Set.of(5)))
            .thenReturn(List.of(otherView));

        Map<String, PermissionGrantDomainService.GrantCheckResult> results = service.checkCanGrant(
            1L, 10L, Set.of(new GrantCheckKey("MENU", "sys:user", PermConstants.CodeType.DEFAULT, "VIEW", false)), null);

        assertEquals("NO_PERMISSION", results.values().iterator().next().reason());
    }

    /**
     * T04 运行时祖先可用但转授未授权该继承：转授不自动扩大——操作者仅在父资源（实体 300）
     * 持有覆盖授权，目标为子资源（实体 200）时运行时 INSTANCE 判定可经判定面闭包放行，
     * 但转授资格必须按精确实例键判 NO_PERMISSION（不消费祖先闭包）。
     */
    @Test
    void t04CanGrantShouldNotExtendDelegationThroughRuntimeAncestor() {
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("MENU")))
            .thenReturn(Map.of("MENU", 1));
        OperationPermission viewOp = operation(101L, 1, "VIEW", 1L, 0L);
        OperationPermission manageOp = operation(102L, 1, "MANAGE", 8L, 1L);
        when(operationPermissionMapper.selectByTenantResourceTypesAndOpCodes(1L, Set.of(1), Set.of("VIEW")))
            .thenReturn(List.of(viewOp));
        // 目标解析为子资源 200；操作者授权行在父资源 300（类型一致、canGrant=true、覆盖 VIEW）
        when(typeResolutionService.batchResolveResourceIds(any(), any()))
            .thenReturn(Map.of(new ResourceResolveKey("MENU", "child", PermConstants.CodeType.DEFAULT, null), 200L));
        GrantFact parentRow = fact(500L, 20L, 1, 300L, 8L, false, true, null, false, null, "MANUAL");
        when(queryEngine.execute(any(QueryRequest.class))).thenReturn(grantResult(parentRow));
        when(operationPermissionMapper.selectByTenantAndResourceTypes(1L, Set.of(1)))
            .thenReturn(List.of(viewOp, manageOp));

        Map<String, PermissionGrantDomainService.GrantCheckResult> results = service.checkCanGrant(
            1L, 10L, Set.of(new GrantCheckKey("MENU", "child", PermConstants.CodeType.DEFAULT, "VIEW", false)), null);

        assertEquals("NO_PERMISSION", results.values().iterator().next().reason());
    }

    // ========== T-PERM-062：20040 reason 细分（TYPE_GRANT_ORIGIN_MISSING，仅自定义类型） ==========

    @Test
    void shouldRefineToGrantOriginMissingForCustomTypeWithZeroGrantableRows() {
        // 自定义类型租户内零条可转授覆盖行（种子缺失/被清除）→ NO_PERMISSION 改判
        // TYPE_GRANT_ORIGIN_MISSING（旧实现无细分，本用例必红）
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("ORDER")))
            .thenReturn(Map.of("ORDER", 12));
        OperationPermission orderView = operation(201L, 12, "VIEW", 2L, 0L);
        when(operationPermissionMapper.selectByTenantResourceTypesAndOpCodes(1L, Set.of(12), Set.of("VIEW")))
            .thenReturn(List.of(orderView));
        // 操作者持有另一类型（typeValue=5）的条目：有角色有授权行，但对目标类型零覆盖 → 主路径 NO_PERMISSION
        OperationPermission otherView = operation(202L, 5, "VIEW", 1L, 0L);
        GrantFact otherEntry = fact(500L, 20L, 5, null, 1L, true, false, null, false, null, "MANUAL");
        when(queryEngine.execute(any(QueryRequest.class))).thenReturn(grantResult(otherEntry));
        when(operationPermissionMapper.selectByTenantAndResourceTypes(1L, Set.of(5)))
            .thenReturn(List.of(otherView));
        when(typeDefinitionMapper.selectValidByTenant(1L)).thenReturn(List.of(customTypeRow(12)));
        when(roleResourcePermissionMapper.selectGrantableCoveringCandidates(eq(1L), eq(Set.of(12)), any()))
            .thenReturn(List.of());
        when(operationPermissionMapper.selectByTenantAndResourceTypes(1L, Set.of(12)))
            .thenReturn(List.of(orderView));

        Map<String, PermissionGrantDomainService.GrantCheckResult> results = service.checkCanGrant(
            1L, 10L, Set.of(new GrantCheckKey("ORDER", null, null, "VIEW", true)), null);

        assertEquals("TYPE_GRANT_ORIGIN_MISSING",
            results.values().iterator().next().reason());
    }

    @Test
    void shouldKeepNoPermissionWhenGrantableOriginExistsTenantWide() {
        // 候选行存在（任意角色持有覆盖可转授行）→ 授权根在，操作者持有面不够维持 NO_PERMISSION
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("ORDER")))
            .thenReturn(Map.of("ORDER", 12));
        OperationPermission orderView = operation(201L, 12, "VIEW", 2L, 0L);
        when(operationPermissionMapper.selectByTenantResourceTypesAndOpCodes(1L, Set.of(12), Set.of("VIEW")))
            .thenReturn(List.of(orderView));
        OperationPermission otherView = operation(202L, 5, "VIEW", 1L, 0L);
        GrantFact otherEntry = fact(500L, 20L, 5, null, 1L, true, false, null, false, null, "MANUAL");
        when(queryEngine.execute(any(QueryRequest.class))).thenReturn(grantResult(otherEntry));
        when(operationPermissionMapper.selectByTenantAndResourceTypes(1L, Set.of(5)))
            .thenReturn(List.of(otherView));
        when(typeDefinitionMapper.selectValidByTenant(1L)).thenReturn(List.of(customTypeRow(12)));
        RoleResourcePermission originRow = permission(900L, "AUTHORITY_ROOT", null, 2L);
        originRow.setResourceType(12);
        originRow.setScopeAll(true);
        originRow.setCanGrant(true);
        when(roleResourcePermissionMapper.selectGrantableCoveringCandidates(eq(1L), eq(Set.of(12)), any()))
            .thenReturn(List.of(originRow));
        when(operationPermissionMapper.selectByTenantAndResourceTypes(1L, Set.of(12)))
            .thenReturn(List.of(orderView));

        Map<String, PermissionGrantDomainService.GrantCheckResult> results = service.checkCanGrant(
            1L, 10L, Set.of(new GrantCheckKey("ORDER", null, null, "VIEW", true)), null);

        assertEquals("NO_PERMISSION", results.values().iterator().next().reason());
    }

    @Test
    void shouldNotRefineBuiltinTypeZeroGrantableRows() {
        // is_system 类型零可转授行是转授链收窄的设计状态（T-PERM-027），reason 维持原值；
        // 且不触发租户级候选行查询（用户定案 2026-09-12：细分仅自定义类型）
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("SERVICE")))
            .thenReturn(Map.of("SERVICE", 4));
        OperationPermission serviceView = operation(203L, 4, "VIEW", 2L, 0L);
        when(operationPermissionMapper.selectByTenantResourceTypesAndOpCodes(1L, Set.of(4), Set.of("VIEW")))
            .thenReturn(List.of(serviceView));
        OperationPermission otherView = operation(202L, 5, "VIEW", 1L, 0L);
        GrantFact otherEntry = fact(500L, 20L, 5, null, 1L, true, false, null, false, null, "MANUAL");
        when(queryEngine.execute(any(QueryRequest.class))).thenReturn(grantResult(otherEntry));
        when(operationPermissionMapper.selectByTenantAndResourceTypes(1L, Set.of(5)))
            .thenReturn(List.of(otherView));
        cn.ac.fage.accessmesh.access.type.entity.TypeDefinition builtin = customTypeRow(4);
        builtin.setIsSystem(true);
        when(typeDefinitionMapper.selectValidByTenant(1L)).thenReturn(List.of(builtin));

        Map<String, PermissionGrantDomainService.GrantCheckResult> results = service.checkCanGrant(
            1L, 10L, Set.of(new GrantCheckKey("SERVICE", null, null, "VIEW", true)), null);

        assertEquals("NO_PERMISSION", results.values().iterator().next().reason());
        verify(roleResourcePermissionMapper, never()).selectGrantableCoveringCandidates(any(), any(), any());
    }

    // ========== 夹具 ==========

    /** PRESERVE+SKIP 单阶段保留事实结果（PRESERVE 下 raw=retained），包装为 execute 返回形态。 */
    private QueryResult grantResult(GrantFact... facts) {
        GrantSetResult.CollectionStatus status = facts.length == 0
            ? GrantSetResult.CollectionStatus.NO_MATCH : GrantSetResult.CollectionStatus.PRESENT;
        List<StageFacts> stages = facts.length == 0 ? List.of()
            : List.of(new StageFacts(Stage.GRANT_LIST, List.of(facts), List.of(facts), StageFacts.Status.PRESENT));
        GrantSetResult item = new GrantSetResult("canGrant", status,
            new EvaluationCoverage(EvaluationCoverage.SubjectResolution.USER_EFFECTIVE_WITH_MUTEX,
                EvaluationCoverage.ConditionCoverage.PRESERVED, EvaluationCoverage.MutexCoverage.SKIPPED,
                EvaluationCoverage.ParentCheckCoverage.NOT_REQUIRED,
                Set.of(Stage.GRANT_LIST), Map.of(), true, EvaluationCoverage.AuthorizationStage.FACT_COLLECTION),
            new ResultDetails(Set.of(), List.of(), List.of(), stages));
        return new QueryResult("canGrant-exec", LocalDateTime.now(), List.of(item));
    }

    private GrantFact fact(Long permissionId, Long roleId, Integer resourceType, Long resourceEntityId,
                           Long grantedBits, boolean scopeAll, boolean canGrant, Long conditionId,
                           boolean hasCondition, Long dependOn, String grantSource) {
        return new GrantFact(permissionId, roleId, resourceType, resourceEntityId, grantedBits,
            scopeAll, canGrant, conditionId, hasCondition, dependOn, grantSource);
    }

    private cn.ac.fage.accessmesh.access.type.entity.TypeDefinition customTypeRow(int typeValue) {
        cn.ac.fage.accessmesh.access.type.entity.TypeDefinition row =
            new cn.ac.fage.accessmesh.access.type.entity.TypeDefinition();
        row.setTenantId(1L);
        row.setTypeKey("resource_type");
        row.setTypeCode("ORDER");
        row.setTypeValue(typeValue);
        row.setIsSystem(false);
        return row;
    }

    private RoleResourcePermission permission(Long id, String source, Long conditionId, Long bits) {
        RoleResourcePermission permission = new RoleResourcePermission();
        permission.setId(id);
        permission.setTenantId(1L);
        permission.setAbstractRoleId(20L);
        permission.setResourceEntityId(100L);
        permission.setResourceType(1);
        permission.setGrantedBits(bits);
        permission.setDependOn(null);
        permission.setScopeAll(false);
        permission.setConditionId(conditionId);
        permission.setGrantSource(source);
        return permission;
    }

    private OperationPermission operation(Long id, Integer resourceType, String code, Long binaryBit, Long inheritMask) {
        OperationPermission operationPermission = new OperationPermission();
        operationPermission.setId(id);
        operationPermission.setResourceType(resourceType);
        operationPermission.setCode(code);
        operationPermission.setBinaryBit(binaryBit);
        operationPermission.setInheritMask(inheritMask);
        operationPermission.setDeleteFlag(0L);
        return operationPermission;
    }
}
