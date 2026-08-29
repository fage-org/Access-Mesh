package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.perm.common.dto.req.UserEffectivePermissionCodesReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.UserEffectivePermissionCodesResp;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.constant.PermConstants;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermQuery;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermViewResult;
import cn.ac.fage.accessmesh.access.permission.dto.req.PermissionExplainReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserPermissionViewReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserResourceTreeReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.PermissionEffectivePermissionsResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.PermissionExplainResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.RecentChangeResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ResourcePermissionTreeResp;
import cn.ac.fage.accessmesh.access.permission.entity.PermissionChangeLog;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.permission.service.PermissionViewAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.PermissionConditionDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.PermissionConflictDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.access.permission.util.PermViewAssembler;
import cn.ac.fage.accessmesh.access.permission.vo.ConditionEvaluationDetail;
import cn.ac.fage.accessmesh.access.permission.vo.MutexFilterResult;
import cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry;
import cn.ac.fage.accessmesh.perm.common.enums.ScopeMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 权限视图应用服务测试类
 */
@ExtendWith(MockitoExtension.class)
class PermissionViewAppServiceImplTest {

    @Mock private AbstractRoleMapper abstractRoleMapper;
    @Mock private ResourceEntityMapper resourceEntityMapper;
    @Mock private OperationPermissionMapper operationPermissionMapper;
    @Mock private RoleResourcePermissionMapper rolePermMapper;
    @Mock private SubjectDomainService subjectDomainService;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private AuditDomainService auditDomainService;
    @Mock private PermQueryEngine engine;
    @Mock private PermViewAssembler permViewAssembler;
    @Mock private PermissionConditionDomainService conditionDomainService;
    @Mock private PermissionConflictDomainService conflictDomainService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private PermissionViewAppServiceImpl service;
    private MockedStatic<OperatorContext> operatorContextMock;

    @BeforeEach
    void setUp() {
        operatorContextMock = mockStatic(OperatorContext.class);
        operatorContextMock.when(OperatorContext::getOperatorId).thenReturn(1L);

        service = new PermissionViewAppServiceImpl(
            abstractRoleMapper, resourceEntityMapper, operationPermissionMapper,
            rolePermMapper, subjectDomainService,
            typeResolutionService, auditDomainService, objectMapper, engine, permViewAssembler,
            conditionDomainService, conflictDomainService
        );
        // explain 解释明细路径的默认返回（候选为空时也调用，宽松桩避免严格模式误报）
        lenient().when(conditionDomainService.evaluateDetailed(anyLong(), any(), any()))
            .thenReturn(List.of());
        lenient().when(conflictDomainService.filterPermMutexWithDrops(anyLong(), any()))
            .thenReturn(new MutexFilterResult(List.of(), List.of()));
    }

    @AfterEach
    void tearDown() {
        operatorContextMock.close();
    }

    @Test
    void shouldReturnEmptyWhenUserNotFound() {
        when(typeResolutionService.resolveUserId(1L, "USER", "u-999")).thenReturn(null);

        UserPermissionViewReq req = new UserPermissionViewReq(
            PermConstants.TargetType.USER, "USER", "u-999", null,
            null, null, null, null, null, null,
            false, false, false, null, 1, 20
        );
        PermissionEffectivePermissionsResp resp = service.getEffectivePermissions(1L, req);

        assertNotNull(resp);
        assertEquals(PermConstants.TargetType.USER, resp.targetType());
        assertTrue(resp.items().isEmpty());
    }

    @Test
    void shouldReturnNullWhenRoleNotFound() {
        when(engine.hasPermissionByCode(anyLong(), anyLong(), eq(ResourceTypeCode.ROLE), eq(null), eq(OperationCodeConstants.VIEW)))
            .thenReturn(true);
        when(typeResolutionService.resolveRoleId(1L, "ADMIN", "r-nonexistent", "default")).thenReturn(null);

        var resp = service.getRolePermissions(1L, "default", "ADMIN", "r-nonexistent", false);

        assertEquals(null, resp);
    }

    @Test
    void shouldDenyExplainWhenUserNotFound() {
        // USER 未解析：不检查门禁（与 effective-permissions USER 分支同款），返回 USER_NOT_FOUND
        when(typeResolutionService.resolveUserId(1L, "USER", "u-999")).thenReturn(null);

        PermissionExplainReq req = new PermissionExplainReq(
            PermConstants.TargetType.USER, "USER", "u-999", null, null, null,
            "API", "api-1", "default", "VIEW", ScopeMode.INSTANCE, null, null, null, null
        );
        PermissionExplainResp resp = service.explain(1L, req);

        assertNotNull(resp);
        assertFalse(resp.allowed());
        assertEquals("USER_NOT_FOUND", resp.reason());
        verify(engine, never()).query(any(PermQuery.class));
    }

    /** T-PERM-033 门禁：explain 查 USER 目标需对被查用户有 USER:VIEW（目标实例检查） */
    @Test
    void shouldDenyExplainWhenOperatorLacksTargetUserView() {
        when(typeResolutionService.resolveUserId(1L, "USER", "u-2")).thenReturn(1002L);
        when(engine.hasPermissionByCode(1L, 1L, ResourceTypeCode.USER, "1002", OperationCodeConstants.VIEW))
            .thenReturn(false);

        PermissionExplainReq req = new PermissionExplainReq(
            PermConstants.TargetType.USER, "USER", "u-2", null, null, null,
            "API", "api-1", "default", "VIEW", ScopeMode.INSTANCE, null, null, null, null
        );

        assertThrows(SecurityException.class, () -> service.explain(1L, req));
        verify(engine, never()).query(any(PermQuery.class));
    }

    /** T-PERM-033 门禁：explain 查 ROLE 目标需对被查角色有 ROLE:VIEW（目标实例检查） */
    @Test
    void shouldDenyExplainWhenOperatorLacksTargetRoleView() {
        when(typeResolutionService.resolveRoleId(1L, "ROLE", "role-1", "admin")).thenReturn(20L);
        when(engine.hasPermissionByCode(1L, 1L, ResourceTypeCode.ROLE, "20", OperationCodeConstants.VIEW))
            .thenReturn(false);

        PermissionExplainReq req = new PermissionExplainReq(
            PermConstants.TargetType.ROLE, null, null, "ROLE", "role-1", "admin",
            "MENU", null, null, "VIEW", ScopeMode.ALL, false, false, null, null
        );

        assertThrows(SecurityException.class, () -> service.explain(1L, req));
        verify(engine, never()).query(any(PermQuery.class));
    }

    /** T-PERM-033 门禁回归锁：持 SYSTEM_CONFIG:VIEW 而无目标 VIEW 者被拒（旧实现放行） */
    @Test
    void shouldRejectExplainWithOnlySystemConfigView() {
        when(typeResolutionService.resolveUserId(1L, "USER", "u-2")).thenReturn(1002L);
        lenient().when(engine.hasPermissionByCode(anyLong(), anyLong(),
            eq(ResourceTypeCode.SYSTEM_CONFIG), eq((String) null), eq(OperationCodeConstants.VIEW)))
            .thenReturn(true);
        when(engine.hasPermissionByCode(1L, 1L, ResourceTypeCode.USER, "1002", OperationCodeConstants.VIEW))
            .thenReturn(false);

        PermissionExplainReq req = new PermissionExplainReq(
            PermConstants.TargetType.USER, "USER", "u-2", null, null, null,
            "API", "api-1", "default", "VIEW", ScopeMode.INSTANCE, null, null, null, null
        );

        assertThrows(SecurityException.class, () -> service.explain(1L, req));
    }

    /** T-PERM-033 评估上下文：未输入 context 回退当前请求（单测无 HTTP 上下文 → IP null），
     * 来源标 CURRENT_REQUEST；判定与候选查询共用同一上下文 */
    @Test
    void shouldFallBackToCurrentRequestContext() {
        when(typeResolutionService.resolveRoleId(1L, "ROLE", "role-1", "admin")).thenReturn(20L);
        when(engine.hasPermissionByCode(anyLong(), anyLong(), eq(ResourceTypeCode.ROLE), any(), eq(OperationCodeConstants.VIEW)))
            .thenReturn(true);
        when(engine.query(any(PermQuery.class))).thenReturn(PermResult.builder(true, null).build());

        PermissionExplainReq req = new PermissionExplainReq(
            PermConstants.TargetType.ROLE, null, null, "ROLE", "role-1", "admin",
            "MENU", null, null, "VIEW", ScopeMode.ALL, false, false, null, null
        );
        PermissionExplainResp resp = service.explain(1L, req);

        assertEquals("CURRENT_REQUEST", resp.evaluationContextSource());
        assertNull(resp.evaluatedClientIp());
        ArgumentCaptor<PermQuery> captor = ArgumentCaptor.forClass(PermQuery.class);
        verify(engine, times(2)).query(captor.capture());
        for (PermQuery q : captor.getAllValues()) {
            assertNotNull(q.context());
        }
    }

    /** T-PERM-033 评估上下文：管理员输入 clientIp → 来源 ADMIN_INPUT，
     * 判定查询与候选查询（评估关闭）都携带该上下文 */
    @Test
    void shouldUseAdminInputContextForBothQueries() {
        when(typeResolutionService.resolveRoleId(1L, "ROLE", "role-1", "admin")).thenReturn(20L);
        when(engine.hasPermissionByCode(anyLong(), anyLong(), eq(ResourceTypeCode.ROLE), any(), eq(OperationCodeConstants.VIEW)))
            .thenReturn(true);
        when(engine.query(any(PermQuery.class))).thenReturn(PermResult.builder(true, null).build());

        PermissionExplainReq req = new PermissionExplainReq(
            PermConstants.TargetType.ROLE, null, null, "ROLE", "role-1", "admin",
            "MENU", null, null, "VIEW", ScopeMode.ALL, false, false, null,
            new PermissionExplainReq.ExplainContext("10.1.2.3")
        );
        PermissionExplainResp resp = service.explain(1L, req);

        assertEquals("ADMIN_INPUT", resp.evaluationContextSource());
        assertEquals("10.1.2.3", resp.evaluatedClientIp());
        ArgumentCaptor<PermQuery> captor = ArgumentCaptor.forClass(PermQuery.class);
        verify(engine, times(2)).query(captor.capture());
        for (PermQuery q : captor.getAllValues()) {
            assertEquals("10.1.2.3", q.context().get("clientIp"));
        }
    }

    @Test
    void shouldExplainRoleScopeAllWithTypeLevelQuery() {
        when(typeResolutionService.resolveRoleId(1L, "ROLE", "role-1", "admin")).thenReturn(20L);
        when(engine.hasPermissionByCode(1L, 1L, ResourceTypeCode.ROLE, "20", OperationCodeConstants.VIEW))
            .thenReturn(true);
        when(engine.query(any(PermQuery.class))).thenReturn(PermResult.builder(true, null).build());

        PermissionExplainReq req = new PermissionExplainReq(
            PermConstants.TargetType.ROLE, null, null, "ROLE", "role-1", "admin",
            "MENU", null, null, "VIEW", ScopeMode.ALL, false, false, null, null
        );
        PermissionExplainResp resp = service.explain(1L, req);

        assertTrue(resp.allowed());
        assertEquals(ScopeMode.ALL, resp.permission().scopeMode());
        assertNull(resp.permission().resourceCode());
        ArgumentCaptor<PermQuery> captor = ArgumentCaptor.forClass(PermQuery.class);
        verify(engine, times(2)).query(captor.capture());
        // 两次查询形状一致：判定查询（评估开）+ 候选查询（评估关）
        for (PermQuery q : captor.getAllValues()) {
            assertNull(q.resourceCodes());
            assertTrue(q.queryScopeAll());
            assertFalse(q.queryInstance());
        }
        assertFalse(captor.getAllValues().get(1).evaluateConditions());
        assertFalse(captor.getAllValues().get(1).evaluateConflicts());
    }

    @Test
    void shouldExplainRoleInstanceWithoutScopeAllFallback() {
        when(typeResolutionService.resolveRoleId(1L, "ROLE", "role-1", "admin")).thenReturn(20L);
        when(engine.hasPermissionByCode(1L, 1L, ResourceTypeCode.ROLE, "20", OperationCodeConstants.VIEW))
            .thenReturn(true);
        when(engine.query(any(PermQuery.class))).thenReturn(PermResult.deny("NO_PERMISSION"));

        PermissionExplainReq req = new PermissionExplainReq(
            PermConstants.TargetType.ROLE, null, null, "ROLE", "role-1", "admin",
            "MENU", "sys:user", "default", "VIEW", ScopeMode.INSTANCE, false, false, null, null
        );
        PermissionExplainResp resp = service.explain(1L, req);

        assertFalse(resp.allowed());
        assertEquals("NO_PERMISSION", resp.reason());
        ArgumentCaptor<PermQuery> captor = ArgumentCaptor.forClass(PermQuery.class);
        verify(engine, times(2)).query(captor.capture());
        for (PermQuery q : captor.getAllValues()) {
            assertFalse(q.queryScopeAll());
            assertTrue(q.queryInstance());
            assertEquals(Set.of("sys:user"), q.resourceCodes());
            assertEquals("default", q.codeType());
        }
    }

    /** T-PERM-033 解释明细：条件逐项评估（脱敏参数）+ 互斥丢弃条目透出 */
    @Test
    void shouldExposeConditionAndConflictDetails() {
        when(typeResolutionService.resolveRoleId(1L, "ROLE", "role-1", "admin")).thenReturn(20L);
        when(engine.hasPermissionByCode(1L, 1L, ResourceTypeCode.ROLE, "20", OperationCodeConstants.VIEW))
            .thenReturn(true);
        RolePermEntry candidate = new RolePermEntry(
            501L, 20L, 200L, "sys:user", 1, 2L, "VIEW", 2L, "DIRECT", true, 77L, true, null, false);
        when(engine.query(any(PermQuery.class)))
            .thenReturn(PermResult.deny("NO_PERMISSION"))          // 判定查询
            .thenReturn(PermResult.builder(true, null)             // 候选查询（评估关闭）
                .instanceEntries(List.of(candidate)).build());
        when(conditionDomainService.evaluateDetailed(eq(1L), any(), any()))
            .thenReturn(List.of(new ConditionEvaluationDetail(
                77L, 501L, 20L, ConditionEvaluationDetail.STATUS_OK, "AND", false,
                List.of(new ConditionEvaluationDetail.ItemDetail("IP_WHITELIST", "192.168.*.*/24", false)))));
        when(conflictDomainService.filterPermMutexWithDrops(eq(1L), any()))
            .thenReturn(new MutexFilterResult(List.of(), List.of(new MutexFilterResult.MutexDrop(
                candidate, 9L, "VIEW", "MANAGE"))));

        PermissionExplainReq req = new PermissionExplainReq(
            PermConstants.TargetType.ROLE, null, null, "ROLE", "role-1", "admin",
            "MENU", "sys:user", "default", "VIEW", ScopeMode.INSTANCE, false, false, null, null
        );
        PermissionExplainResp resp = service.explain(1L, req);

        assertEquals(1, resp.conditionEvaluations().size());
        PermissionExplainResp.ConditionEvaluation cond = resp.conditionEvaluations().get(0);
        assertEquals(77L, cond.conditionId());
        assertFalse(cond.passed());
        assertEquals("192.168.*.*/24", cond.items().get(0).maskedParams());
        assertEquals(1, resp.conflictDrops().size());
        PermissionExplainResp.ConflictDrop drop = resp.conflictDrops().get(0);
        assertEquals(501L, drop.permissionId());
        assertEquals(9L, drop.ruleId());
        assertEquals("MANAGE", drop.secondOperationCode());
    }

    /** T-PERM-033 recentChanges：按权限键过滤（匹配 item 摘要）+ USER 目标保留角色分配/回收 */
    @Test
    void shouldFilterRecentChangesByPermissionKey() {
        when(typeResolutionService.resolveUserId(1L, "USER", "u-2")).thenReturn(1002L);
        when(engine.hasPermissionByCode(1L, 1L, ResourceTypeCode.USER, "1002", OperationCodeConstants.VIEW))
            .thenReturn(true);
        when(engine.query(any(PermQuery.class))).thenReturn(PermResult.deny("NO_PERMISSION"));

        // log-1：两个 item，仅第 2 个匹配权限键 → 取第 2 个 item 摘要（POSSIBLE，间接影响）
        PermissionChangeLog log1 = changeLog(91L, """
            {"eventType":"ROLE_PERMISSION_CHANGE","items":[
              {"changeType":"REMOVE","permission":{"resourceTypeCode":"MENU","resourceCode":"menu:other","codeType":"default","operationCode":"VIEW","scopeMode":"INSTANCE"}},
              {"changeType":"REMOVE","permission":{"domainCode":"admin","resourceTypeCode":"MENU","resourceCode":"sys:user","codeType":"default","operationCode":"VIEW","scopeMode":"INSTANCE"},"role":{"roleExternalId":"role-x","roleName":"角色X"}}]}
            """);
        // log-2：ROLE_PERMISSION_CHANGE 但权限键不匹配 → 排除
        PermissionChangeLog log2 = changeLog(92L, """
            {"eventType":"ROLE_PERMISSION_CHANGE","items":[
              {"changeType":"REMOVE","permission":{"resourceTypeCode":"MENU","resourceCode":"menu:other","codeType":"default","operationCode":"VIEW","scopeMode":"INSTANCE"}}]}
            """);
        // log-3：USER_ROLE_CHANGE（无权限键）→ USER 目标保留（DIRECT）
        PermissionChangeLog log3 = changeLog(93L, """
            {"eventType":"USER_ROLE_CHANGE","items":[
              {"changeType":"REMOVE","role":{"roleExternalId":"role-x","roleName":"角色X"}}]}
            """);
        when(auditDomainService.queryRecentChanges(eq(1L), eq(1002L), eq(null), any(), any(), eq(null), eq(0), eq(200)))
            .thenReturn(List.of(log1, log2, log3));

        PermissionExplainReq req = new PermissionExplainReq(
            PermConstants.TargetType.USER, "USER", "u-2", null, null, "admin",
            "MENU", "sys:user", "default", "VIEW", ScopeMode.INSTANCE,
            null, true, null, null
        );
        PermissionExplainResp resp = service.explain(1L, req);

        assertEquals(2, resp.recentChanges().size());
        RecentChangeResp first = resp.recentChanges().get(0);
        assertEquals(91L, first.changeLogId());
        assertEquals("sys:user", first.permission().resourceCode());
        assertEquals("POSSIBLE", first.impactLevel());
        RecentChangeResp second = resp.recentChanges().get(1);
        assertEquals(93L, second.changeLogId());
        assertEquals("DIRECT", second.impactLevel());
        assertNull(second.permission().resourceCode());
    }

    /** 构造带 diffSnapshot 的变更日志实体 */
    private PermissionChangeLog changeLog(Long id, String diffSnapshot) {
        PermissionChangeLog log = new PermissionChangeLog();
        log.setId(id);
        log.setTenantId(1L);
        log.setDiffSnapshot(diffSnapshot);
        return log;
    }


    @Test
    void shouldReturnEmptyTreeWhenUserNotFound() {
        when(engine.hasPermissionByCode(anyLong(), anyLong(), eq(ResourceTypeCode.USER), eq("999"), eq(OperationCodeConstants.VIEW)))
            .thenReturn(true);
        when(subjectDomainService.resolveEffectiveRoles(1L, 999L)).thenReturn(Set.of());

        UserResourceTreeReq req = new UserResourceTreeReq("USER", "u-999", null, null, null, null);
        List<ResourcePermissionTreeResp> tree = service.getUserResourceTree(1L, 999L, req);

        assertNotNull(tree);
        assertTrue(tree.isEmpty());
    }

    @Test
    void getEffectivePermissionCodesShouldReturnInheritedEffectiveOperationCodes() {
        // 自查场景：operator 投影主体=1001，subject "1" 投影=1001；buildEffectiveView 无 USER:VIEW 门禁
        when(typeResolutionService.resolveUserId(1L, "LOCAL_USER", "1")).thenReturn(1L);
        when(subjectDomainService.resolveEffectiveRoles(1L, 1L)).thenReturn(Set.of(20L));

        RolePermEntry entry = new RolePermEntry(
            501L, 20L, 200L, null, 1, 4L,
            "UPDATE", 6L, "DIRECT", true, null, false, null, false
        );
        PermResult result = PermResult.builder(true, null)
            .instanceEntries(List.of(entry))
            .effectiveOperationEntries(List.of(
                new PermResult.EffectiveOperationEntry(
                    501L, 20L, 200L, 1, 4L, "UPDATE", 6L,
                    "UPDATE", 4L, "DIRECT", false),
                new PermResult.EffectiveOperationEntry(
                    501L, 20L, 200L, 1, 4L, "UPDATE", 6L,
                    "VIEW", 2L, "DIRECT", false)
            ))
            .build();
        when(engine.query(any(PermQuery.class))).thenReturn(result);
        when(permViewAssembler.assemble(eq(1L), eq(result), any()))
            .thenReturn(PermViewResult.builder()
                .entries(List.of(entry))
                .effectiveOperationEntries(result.effectiveOperationEntries())
                .resourceMap(Map.of())
                .operationMap(Map.of())
                .roleMap(Map.of())
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
        when(subjectDomainService.resolveEffectiveRoles(1L, 1L)).thenReturn(Set.of(20L));

        PermResult result = PermResult.builder(true, null)
            .effectiveOperationEntries(List.of(
                new PermResult.EffectiveOperationEntry(
                    501L, 20L, null, 2, 4L, "VIEW", 4L, "VIEW", 2L, "DIRECT", true),  // scopeAll → allScopeTypes=2
                new PermResult.EffectiveOperationEntry(
                    502L, 20L, 200L, 1, 2L, "VIEW", 2L, "VIEW", 1L, "DIRECT", false) // 实例 → resourceEntityIds=200
            ))
            .build();
        when(engine.query(any(PermQuery.class))).thenReturn(result);
        when(permViewAssembler.assemble(eq(1L), eq(result), any()))
            .thenReturn(PermViewResult.builder()
                .entries(List.of())
                .effectiveOperationEntries(result.effectiveOperationEntries())
                .resourceMap(Map.of())
                .operationMap(Map.of())
                .roleMap(Map.of())
                .build());

        PermissionViewAppService.EffectiveResourceAccess access = service.getEffectiveResourceAccess(
            1L, new UserEffectivePermissionCodesReq("LOCAL_USER", "1", List.of("USER", "ORG")));

        assertTrue(access.allScopeTypes().contains(2));
        assertTrue(access.resourceEntityIds().contains(200L));
        assertFalse(access.allScopeTypes().contains(1));
        assertFalse(access.resourceEntityIds().contains(501L));
    }

    @Test
    void getEffectivePermissionCodesForManageShouldAllowSelfWithoutUserView() {
        // 自查：operator 投影主体=1001（sys=1 转换），subject "1" 投影=1001 → 豁免 USER:VIEW，不调用 engine.hasPermission
        when(typeResolutionService.resolveUserId(1L, "LOCAL_USER", "1")).thenReturn(1L);
        when(subjectDomainService.resolveEffectiveRoles(1L, 1L)).thenReturn(Set.of());

        UserEffectivePermissionCodesResp resp = service.getEffectivePermissionCodesForManage(
            1L, new UserEffectivePermissionCodesReq("LOCAL_USER", "1", List.of("USER")));

        assertNotNull(resp);
        assertTrue(resp.permissions().isEmpty());
        verify(engine, never()).hasPermissionByCode(anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void getEffectivePermissionCodesForManageShouldDenyOthersWithoutUserView() {
        // 查他人：operator 投影主体=1001，subject "2" 投影=1002，无 USER:VIEW → SecurityException（门禁用 abstract 主体，非 sys id）
        when(typeResolutionService.resolveUserId(1L, "LOCAL_USER", "2")).thenReturn(1002L);
        when(engine.hasPermissionByCode(1L, 1L, ResourceTypeCode.USER, "1002", OperationCodeConstants.VIEW))
            .thenReturn(false);

        assertThrows(SecurityException.class, () ->
            service.getEffectivePermissionCodesForManage(
                1L, new UserEffectivePermissionCodesReq("LOCAL_USER", "2", List.of("USER"))));
    }

    @Test
    void getEffectivePermissionCodesForManageShouldAllowOthersWithUserView() {
        // 查他人：operator 投影主体=1001，subject "2" 投影=1002，有 USER:VIEW → 正常下发
        when(typeResolutionService.resolveUserId(1L, "LOCAL_USER", "2")).thenReturn(1002L);
        when(engine.hasPermissionByCode(1L, 1L, ResourceTypeCode.USER, "1002", OperationCodeConstants.VIEW))
            .thenReturn(true);
        when(subjectDomainService.resolveEffectiveRoles(1L, 1002L)).thenReturn(Set.of());

        UserEffectivePermissionCodesResp resp = service.getEffectivePermissionCodesForManage(
            1L, new UserEffectivePermissionCodesReq("LOCAL_USER", "2", List.of("USER")));

        assertNotNull(resp);
        assertTrue(resp.permissions().isEmpty());
    }

}
