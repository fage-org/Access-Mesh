package cn.ac.fage.accessmesh.access.permission.service.domain.impl;

import cn.ac.fage.accessmesh.access.permission.dto.query.PermEvalContext;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermQuery;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper.BitMaskEntry;
import cn.ac.fage.accessmesh.access.permission.service.domain.PermissionConditionDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.PermissionConflictDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.access.permission.util.RolePermEntryMapper;
import cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry;
import cn.ac.fage.accessmesh.access.permission.cache.PermCacheCatalog;
import cn.ac.fage.accessmesh.common.cache.CacheReadToken;
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.common.cache.CacheCatalogEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermQueryEngineTest {

    @Mock
    private SubjectDomainService subjectDomainService;
    @Mock
    private RoleResourcePermissionMapper rolePermMapper;
    @Mock
    private ResourceEntityMapper resourceEntityMapper;
    @Mock
    private AbstractRoleMapper abstractRoleMapper;
    @Mock
    private PermissionConditionDomainService conditionDomainService;
    @Mock
    private PermissionConflictDomainService conflictDomainService;
    @Mock
    private TypeResolutionService typeResolutionService;
    @Mock
    private CacheService cacheService;
    @Mock
    private OperationPermissionMapper operationPermissionMapper;

    private PermQueryEngine engine;

    /** beginRead 委托对象：mock cacheService 直接返回 null 令牌，借真实实现产生合法读取令牌 */
    private final cn.ac.fage.accessmesh.common.cache.DefaultCacheService realCacheService =
        new cn.ac.fage.accessmesh.common.cache.DefaultCacheService(null, null, null,
            new cn.ac.fage.accessmesh.common.cache.CacheProperties(), null);

    @BeforeEach
    void setUp() {
        engine = new PermQueryEngine(
            subjectDomainService,
            rolePermMapper,
            resourceEntityMapper,
            abstractRoleMapper,
            conditionDomainService,
            conflictDomainService,
            new RolePermEntryMapper(),
            typeResolutionService,
            cacheService,
            operationPermissionMapper
        );
        // T-ACCESS-008：beginRead 委托真实实现——mock 默认返回 null 令牌会导致
        // putBatch(token) 断言失真
        org.mockito.Mockito.lenient().when(cacheService.beginRead(org.mockito.ArgumentMatchers.any(
                cn.ac.fage.accessmesh.common.cache.CacheCatalogEntry.class)))
            .thenAnswer(inv -> realCacheService.beginRead(inv.getArgument(0)));
    }

    @Test
    void queryShouldUseBitMaskQueriesAndPopulateGrantedOperations() {
        when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(20L));
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("MENU")))
            .thenReturn(Map.of("MENU", 1));
        when(typeResolutionService.batchResolveOperationIds(1L, "MENU", Set.of("VIEW")))
            .thenReturn(Map.of("VIEW", 101L));

        OperationPermission viewOp = operation(101L, 1, "VIEW", 1L, 0L);
        OperationPermission manageOp = operation(102L, 1, "MANAGE", 8L, 1L);

        when(operationPermissionMapper.selectValidByIds(1L, Set.of(101L))).thenReturn(List.of(viewOp));
        when(operationPermissionMapper.selectByTenantAndResourceType(1L, 1)).thenReturn(List.of(viewOp, manageOp));

        // Mock cacheService.getBatch() to return operation permissions map for ID index
        Map<Long, OperationPermission> opMap = Map.of(101L, viewOp, 102L, manageOp);
        when(cacheService.getBatch(any(CacheCatalogEntry.class), eq(1L), eq(Set.of("op_perm:1"))))
            .thenReturn(Map.of("op_perm:1", opMap));

        RoleResourcePermission grantedPerm = new RoleResourcePermission();
        grantedPerm.setId(501L);
        grantedPerm.setAbstractRoleId(20L);
        grantedPerm.setResourceEntityId(200L);
        grantedPerm.setResourceType(1);
        grantedPerm.setGrantedBits(8L);
        grantedPerm.setDeleteFlag(0L);

        // 使用正确的批量方法名和参数
        List<BitMaskEntry> bitMaskEntries = List.of(new BitMaskEntry(1, 9L));
        when(rolePermMapper.selectScopeAllPermsByBitsBatch(1L, Set.of(20L), bitMaskEntries)).thenReturn(List.of());
        when(rolePermMapper.selectInstancePermsByBitsBatch(1L, Set.of(20L), Set.of(200L), bitMaskEntries))
            .thenReturn(List.of(grantedPerm));
        when(conditionDomainService.evaluate(eq(1L), any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(conflictDomainService.filterPermMutex(eq(1L), any())).thenAnswer(invocation -> invocation.getArgument(1));

        when(typeResolutionService.batchResolveResourceIds(eq(1L), any()))
            .thenReturn(Map.of(new ResourceResolveKey("MENU", "sys:user", null, null), 200L));

        PermQuery query = PermQuery.forAuthCheck(1L, 10L, "MENU", "sys:user", "VIEW");
        query.setIncludeOperations(true);

        PermResult result = engine.query(query);

        assertEquals(1, result.instanceEntries().size());
        assertTrue(result.operationMap().values().stream().anyMatch(op -> "MANAGE".equals(op.getCode())));
        verify(rolePermMapper).selectInstancePermsByBitsBatch(1L, Set.of(20L), Set.of(200L), bitMaskEntries);
    }

    /**
     * T-ACCESS-017 特征测试（链路 4）：scopeAll 类型级授权命中时提前返回放行——
     * 允许结果 + scopeAllMatched=true + 零实例级查询（verify never）。
     * T-PERM-057 拉平后 forValidate 评估条件与条目互斥（Q13 定案），本用例同时锁
     * 「评估通过不改变放行结论」。
     */
    @Test
    void queryShouldEarlyReturnAllowOnScopeAllMatch() {
        when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(20L));
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("SERVICE")))
            .thenReturn(Map.of("SERVICE", 8));
        when(typeResolutionService.batchResolveOperationIds(1L, "SERVICE", Set.of("VIEW")))
            .thenReturn(Map.of("VIEW", 901L));

        OperationPermission viewOp = operation(901L, 8, "VIEW", 2L, 0L);
        when(operationPermissionMapper.selectValidByIds(1L, Set.of(901L))).thenReturn(List.of(viewOp));
        when(cacheService.getBatch(any(CacheCatalogEntry.class), eq(1L), eq(Set.of("op_perm:8"))))
            .thenReturn(Map.of("op_perm:8", Map.of(901L, viewOp)));

        // scope_all=true 的授权行：resource_entity_id 必须为 null（DDL CHECK 约束）
        RoleResourcePermission scopeAllPerm = new RoleResourcePermission();
        scopeAllPerm.setId(501L);
        scopeAllPerm.setAbstractRoleId(20L);
        scopeAllPerm.setResourceEntityId(null);
        scopeAllPerm.setResourceType(8);
        scopeAllPerm.setGrantedBits(2L);
        scopeAllPerm.setScopeAll(true);
        scopeAllPerm.setDeleteFlag(0L);
        when(rolePermMapper.selectScopeAllPermsByBitsBatch(eq(1L), eq(Set.of(20L)), any()))
            .thenReturn(List.of(scopeAllPerm));
        when(conditionDomainService.evaluate(eq(1L), any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(conflictDomainService.filterPermMutex(eq(1L), any())).thenAnswer(invocation -> invocation.getArgument(1));

        PermQuery query = PermQuery.forValidate(1L, 10L, "SERVICE", "svc-code-1", "VIEW");

        PermResult result = engine.query(query);

        assertTrue(result.allowed());
        assertTrue(result.scopeAllMatched());
        assertEquals(1, result.scopeAllEntries().size());
        // 类型级放行：跳过实例级查询（提前返回分支的核心特征）
        verify(rolePermMapper, never()).selectInstancePermsByBitsBatch(any(), any(), any(), any());
    }

    /**
     * T-ACCESS-017 特征测试（链路 4）：getDeniedEntityIds 批量门禁在 scopeAll 命中时
     * 短路返回空拒绝集（全部允许），不触发实例级批量查询。
     */
    @Test
    void getDeniedEntityIdsShouldReturnEmptyOnScopeAllMatch() {
        when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(20L));
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("SERVICE")))
            .thenReturn(Map.of("SERVICE", 8));
        when(typeResolutionService.batchResolveOperationIds(1L, "SERVICE", Set.of("VIEW")))
            .thenReturn(Map.of("VIEW", 901L));

        OperationPermission viewOp = operation(901L, 8, "VIEW", 2L, 0L);
        when(operationPermissionMapper.selectValidByIds(1L, Set.of(901L))).thenReturn(List.of(viewOp));
        when(cacheService.getBatch(any(CacheCatalogEntry.class), eq(1L), eq(Set.of("op_perm:8"))))
            .thenReturn(Map.of("op_perm:8", Map.of(901L, viewOp)));

        RoleResourcePermission scopeAllPerm = new RoleResourcePermission();
        scopeAllPerm.setId(501L);
        scopeAllPerm.setAbstractRoleId(20L);
        scopeAllPerm.setResourceEntityId(null);
        scopeAllPerm.setResourceType(8);
        scopeAllPerm.setGrantedBits(2L);
        scopeAllPerm.setScopeAll(true);
        scopeAllPerm.setDeleteFlag(0L);
        when(rolePermMapper.selectScopeAllPermsByBitsBatch(eq(1L), eq(Set.of(20L)), any()))
            .thenReturn(List.of(scopeAllPerm));
        when(conditionDomainService.evaluate(eq(1L), any(), any())).thenAnswer(inv -> inv.getArgument(1));
        when(conflictDomainService.filterPermMutex(eq(1L), any())).thenAnswer(inv -> inv.getArgument(1));

        Set<Long> denied = engine.getDeniedEntityIds(1L, 10L, "SERVICE", Set.of(1L, 2L, 3L), "VIEW");

        assertTrue(denied.isEmpty());
        verify(rolePermMapper, never()).selectInstancePermsByBitsBatch(any(), any(), any(), any());
    }

    @Test
    void testForUserView() {
        when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(20L));

        // T-PERM-018：forUserView 走 ROLE_PERM_SNAPSHOT 读缓存；缓存 miss → 回源 selectValidByRoleIds
        when(cacheService.getBatch(eq(PermCacheCatalog.ROLE_PERM_SNAPSHOT), eq(1L), eq(Set.of(20L))))
            .thenReturn(Map.of());

        RoleResourcePermission perm1 = new RoleResourcePermission();
        perm1.setId(501L);
        perm1.setAbstractRoleId(20L);
        perm1.setResourceEntityId(200L);
        perm1.setResourceType(1);
        perm1.setGrantedBits(8L);
        perm1.setDeleteFlag(0L);
        perm1.setConditionId(null);
        perm1.setCanGrant(true);
        perm1.setGrantSource("DIRECT");

        RoleResourcePermission perm2 = new RoleResourcePermission();
        perm2.setId(502L);
        perm2.setAbstractRoleId(20L);
        perm2.setResourceEntityId(201L);
        perm2.setResourceType(2);
        perm2.setGrantedBits(4L);
        perm2.setDeleteFlag(0L);
        perm2.setConditionId(null);
        perm2.setCanGrant(false);
        perm2.setGrantSource("INHERITED");

        when(rolePermMapper.selectValidByRoleIds(1L, Set.of(20L))).thenReturn(List.of(perm1, perm2));
        when(conditionDomainService.evaluate(eq(1L), any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(conflictDomainService.filterPermMutex(eq(1L), any())).thenAnswer(invocation -> invocation.getArgument(1));

        ResourceEntity resource1 = new ResourceEntity();
        resource1.setId(200L);
        resource1.setCode("sys:user");
        resource1.setName("用户资源");
        resource1.setResourceType(1);
        ResourceEntity resource2 = new ResourceEntity();
        resource2.setId(201L);
        resource2.setCode("sys:role");
        resource2.setName("角色资源");
        resource2.setResourceType(2);
        when(resourceEntityMapper.selectValidByIds(1L, Set.of(200L, 201L)))
            .thenReturn(List.of(resource1, resource2));

        OperationPermission op1 = operation(101L, 1, "VIEW", 1L, 0L);
        OperationPermission op2 = operation(102L, 1, "MANAGE", 8L, 1L);
        OperationPermission op3 = operation(201L, 2, "EDIT", 4L, 0L);
        when(operationPermissionMapper.selectByTenantAndResourceType(1L, 1)).thenReturn(List.of(op1, op2));
        when(operationPermissionMapper.selectByTenantAndResourceType(1L, 2)).thenReturn(List.of(op3));

        AbstractRole role = new AbstractRole();
        role.setId(20L);
        role.setName("测试角色");
        role.setDeleteFlag(0L);
        when(abstractRoleMapper.selectValidByIds(1L, Set.of(20L))).thenReturn(List.of(role));

        PermQuery query = PermQuery.forUserView(1L, 10L);
        PermResult result = engine.query(query);

        assertTrue(result.allowed());
        assertEquals(2, result.allEntries().size());
        assertEquals(2, result.resourceMap().size());
        assertTrue(result.resourceMap().containsKey(200L));
        assertTrue(result.resourceMap().containsKey(201L));
        assertTrue(result.operationMap().size() >= 3);
        List<String> effectiveType1Ops = result.effectiveOperationEntries().stream()
            .filter(e -> Integer.valueOf(1).equals(e.resourceType()))
            .map(PermResult.EffectiveOperationEntry::operationCode)
            .toList();
        assertTrue(effectiveType1Ops.contains("VIEW"));
        assertTrue(effectiveType1Ops.contains("MANAGE"));
        assertEquals(1, result.roleMap().size());
        assertTrue(result.roleMap().containsKey(20L));

        verify(rolePermMapper).selectValidByRoleIds(1L, Set.of(20L));
    }

    @Test
    void testForUserViewEmptyRolesShouldDeny() {
        when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of());

        PermQuery query = PermQuery.forUserView(1L, 10L);
        PermResult result = engine.query(query);

        assertEquals(false, result.allowed());
        assertEquals("NO_ROLE", result.reason());
    }

    @Test
    void testForUserViewEmptyPermissionsShouldDeny() {
        when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(20L));
        when(cacheService.getBatch(eq(PermCacheCatalog.ROLE_PERM_SNAPSHOT), eq(1L), eq(Set.of(20L))))
            .thenReturn(Map.of());
        when(rolePermMapper.selectValidByRoleIds(1L, Set.of(20L))).thenReturn(List.of());

        PermQuery query = PermQuery.forUserView(1L, 10L);
        PermResult result = engine.query(query);

        assertEquals(false, result.allowed());
        assertEquals("NO_PERMISSION", result.reason());
    }

    // ===== T-PERM-018: ROLE_PERM_SNAPSHOT 读缓存激活 =====

    @Test
    void forUserViewShouldHitCacheAndSkipDbWhenAllRolesCached() {
        when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(20L));

        RolePermEntry cachedEntry = new RolePermEntry(
            501L, 20L, 200L, null, 1, 8L, null, null,
            "DIRECT", true, null, false, null, true);
        when(cacheService.getBatch(eq(PermCacheCatalog.ROLE_PERM_SNAPSHOT), eq(1L), eq(Set.of(20L))))
            .thenReturn(Map.of(20L, List.of(cachedEntry)));
        when(conditionDomainService.evaluate(eq(1L), any(), any())).thenAnswer(inv -> inv.getArgument(1));
        when(conflictDomainService.filterPermMutex(eq(1L), any())).thenAnswer(inv -> inv.getArgument(1));

        PermResult result = engine.query(PermQuery.forUserView(1L, 10L));

        assertTrue(result.allowed());
        assertEquals(1, result.allEntries().size());
        // 全 hit：不应回源 DB
        verify(rolePermMapper, never()).selectValidByRoleIds(any(), any());
        // T-ACCESS-008：全 hit 不回填（含读取令牌路径）
        verify(cacheService, never()).putBatch(org.mockito.ArgumentMatchers.any(
                cn.ac.fage.accessmesh.common.cache.CacheReadToken.class),
            eq(1L), any());
        verify(cacheService, never()).putBatch(org.mockito.ArgumentMatchers.any(
                cn.ac.fage.accessmesh.common.cache.CacheCatalogEntry.class),
            eq(1L), any());
    }

    @Test
    void forUserViewShouldMissCacheAndBackfillBatch() {
        when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(20L, 21L));
        when(cacheService.getBatch(eq(PermCacheCatalog.ROLE_PERM_SNAPSHOT), eq(1L), eq(Set.of(20L, 21L))))
            .thenReturn(Map.of());

        RoleResourcePermission perm1 = new RoleResourcePermission();
        perm1.setId(501L); perm1.setAbstractRoleId(20L); perm1.setResourceEntityId(200L);
        perm1.setResourceType(1); perm1.setGrantedBits(8L); perm1.setDeleteFlag(0L);
        when(rolePermMapper.selectValidByRoleIds(1L, Set.of(20L, 21L))).thenReturn(List.of(perm1));
        when(conditionDomainService.evaluate(eq(1L), any(), any())).thenAnswer(inv -> inv.getArgument(1));
        when(conflictDomainService.filterPermMutex(eq(1L), any())).thenAnswer(inv -> inv.getArgument(1));

        PermResult result = engine.query(PermQuery.forUserView(1L, 10L));

        assertTrue(result.allowed());
        assertEquals(1, result.allEntries().size());
        // miss 集合 1 SQL 回源 + putBatch 回填（含 role 21 的空列表防穿透）
        verify(rolePermMapper).selectValidByRoleIds(1L, Set.of(20L, 21L));
        org.mockito.ArgumentCaptor<Map<Long, List<RolePermEntry>>> captor =
            org.mockito.ArgumentCaptor.forClass(Map.class);
        // T-ACCESS-008：回填走读取令牌（剩余 TTL），验证令牌绑定同一 catalog
        org.mockito.ArgumentCaptor<cn.ac.fage.accessmesh.common.cache.CacheReadToken<List<RolePermEntry>>> tokenCaptor =
            org.mockito.ArgumentCaptor.forClass(cn.ac.fage.accessmesh.common.cache.CacheReadToken.class);
        verify(cacheService).putBatch(tokenCaptor.capture(), eq(1L), captor.capture());
        assertEquals(PermCacheCatalog.ROLE_PERM_SNAPSHOT, tokenCaptor.getValue().catalog());
        Map<Long, List<RolePermEntry>> backfilled = captor.getValue();
        assertEquals(2, backfilled.size());
        assertEquals(1, backfilled.get(20L).size());
        // 空权限角色缓存空列表（非 null）防穿透
        assertTrue(backfilled.get(21L).isEmpty());
    }

    @Test
    void forUserViewShouldHandleMixedHitAndMiss() {
        when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(20L, 21L));
        // role 20 命中，role 21 miss
        RolePermEntry cachedEntry = new RolePermEntry(
            501L, 20L, 200L, null, 1, 8L, null, null,
            "DIRECT", true, null, false, null, true);
        when(cacheService.getBatch(eq(PermCacheCatalog.ROLE_PERM_SNAPSHOT), eq(1L), eq(Set.of(20L, 21L))))
            .thenReturn(Map.of(20L, List.of(cachedEntry)));

        RoleResourcePermission perm2 = new RoleResourcePermission();
        perm2.setId(502L); perm2.setAbstractRoleId(21L); perm2.setResourceEntityId(201L);
        perm2.setResourceType(2); perm2.setGrantedBits(4L); perm2.setDeleteFlag(0L);
        // 仅查 miss 集合 {21}
        when(rolePermMapper.selectValidByRoleIds(1L, Set.of(21L))).thenReturn(List.of(perm2));
        when(conditionDomainService.evaluate(eq(1L), any(), any())).thenAnswer(inv -> inv.getArgument(1));
        when(conflictDomainService.filterPermMutex(eq(1L), any())).thenAnswer(inv -> inv.getArgument(1));

        PermResult result = engine.query(PermQuery.forUserView(1L, 10L));

        assertTrue(result.allowed());
        assertEquals(2, result.allEntries().size());
        // 仅 miss 角色回源，命中角色不查 DB
        verify(rolePermMapper).selectValidByRoleIds(1L, Set.of(21L));
    }

    @Test
    void forUserViewShouldCacheEmptyListForRoleWithNoPermissions() {
        when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(20L));
        when(cacheService.getBatch(eq(PermCacheCatalog.ROLE_PERM_SNAPSHOT), eq(1L), eq(Set.of(20L))))
            .thenReturn(Map.of());
        when(rolePermMapper.selectValidByRoleIds(1L, Set.of(20L))).thenReturn(List.of());

        PermResult result = engine.query(PermQuery.forUserView(1L, 10L));

        assertEquals(false, result.allowed());
        assertEquals("NO_PERMISSION", result.reason());
        // 空权限角色缓存 List.of()（非 null）防穿透
        org.mockito.ArgumentCaptor<Map<Long, List<RolePermEntry>>> captor =
            org.mockito.ArgumentCaptor.forClass(Map.class);
        // T-ACCESS-008：回填走读取令牌（剩余 TTL）
        verify(cacheService).putBatch(org.mockito.ArgumentMatchers.any(
                cn.ac.fage.accessmesh.common.cache.CacheReadToken.class),
            eq(1L), captor.capture());
        List<RolePermEntry> cachedForRole = captor.getValue().get(20L);
        assertNotNull(cachedForRole);
        assertTrue(cachedForRole.isEmpty());
    }

    // ===== T-PERM-042：显式资源 API（业务编码轨 / entityId 轨）=====

    /**
     * T-PERM-042 错参正确预期（USER 业务编码轨）：getDeniedResourceCodes 先经
     * TypeResolutionService 一次批量 code → entity 解析，再在 resource_entity.id 空间
     * 查实例授权——业务编码（abstract_user.id 字符串化）不再被当作 resource_entity.id 直查。
     * 未解析到投影实体的编码（"30"）fail-closed 进入拒绝集合。
     */
    @Test
    void getDeniedResourceCodesShouldResolveBusinessCodesInEntitySpace() {
        when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(20L));
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("USER")))
            .thenReturn(Map.of("USER", 6));
        when(typeResolutionService.batchResolveOperationIds(1L, "USER", Set.of("MANAGE")))
            .thenReturn(Map.of("MANAGE", 601L));

        OperationPermission manageOp = operation(601L, 6, "MANAGE", 16L, 2L);
        when(operationPermissionMapper.selectValidByIds(1L, Set.of(601L))).thenReturn(List.of(manageOp));
        when(cacheService.getBatch(any(CacheCatalogEntry.class), eq(1L), eq(Set.of("op_perm:6"))))
            .thenReturn(Map.of("op_perm:6", Map.of(601L, manageOp)));

        // scopeAll 未命中
        when(rolePermMapper.selectScopeAllPermsByBitsBatch(eq(1L), eq(Set.of(20L)), any()))
            .thenReturn(List.of());

        // 一次批量解析：code "10"→entity 1001、code "20"→entity 1002；"30" 无投影
        when(typeResolutionService.batchResolveResourceIds(eq(1L), any())).thenReturn(Map.of(
            new cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveKey("USER", "10", null, null), 1001L,
            new cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveKey("USER", "20", null, null), 1002L));

        // 实例授权只挂在 entity 1001（code "10"）
        RoleResourcePermission granted = new RoleResourcePermission();
        granted.setId(501L);
        granted.setAbstractRoleId(20L);
        granted.setResourceEntityId(1001L);
        granted.setResourceType(6);
        granted.setGrantedBits(16L);
        granted.setDeleteFlag(0L);
        when(rolePermMapper.selectInstancePermsByBitsBatch(eq(1L), eq(Set.of(20L)), eq(Set.of(1001L, 1002L)), any()))
            .thenReturn(List.of(granted));
        when(conditionDomainService.evaluate(eq(1L), any(), any())).thenAnswer(inv -> inv.getArgument(1));
        when(conflictDomainService.filterPermMutex(eq(1L), any())).thenAnswer(inv -> inv.getArgument(1));

        Set<String> denied = engine.getDeniedResourceCodes(
            1L, 10L, "USER", new java.util.LinkedHashSet<>(List.of("10", "20", "30")), "MANAGE");

        assertEquals(Set.of("20", "30"), denied);
        // code → entity 解析一次批量完成（无 N+1），实例查询落在投影 ID 空间
        verify(typeResolutionService).batchResolveResourceIds(eq(1L), any());
        verify(rolePermMapper).selectInstancePermsByBitsBatch(eq(1L), eq(Set.of(20L)), eq(Set.of(1001L, 1002L)), any());
    }

    /** T-PERM-042：无角色主体 fail-closed 全量拒绝（不解析 code、不查实例级）。 */
    @Test
    void getDeniedResourceCodesShouldDenyAllWhenSubjectHasNoRole() {
        when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of());

        Set<String> denied = engine.getDeniedResourceCodes(1L, 10L, "ROLE", Set.of("1", "2"), "MANAGE");

        assertEquals(Set.of("1", "2"), denied);
        verify(typeResolutionService, never()).batchResolveResourceIds(any(), any());
        verify(rolePermMapper, never()).selectInstancePermsByBitsBatch(any(), any(), any(), any());
    }

    /**
     * T-PERM-042 评审 P1：scopeAll 类型级授权命中时放行全部业务编码——包括尚无投影实体的编码，
     * 且不做 code 解析（管线与 implementation §3.1 一致：先 scopeAll、未命中才解析 code）。
     */
    @Test
    void getDeniedResourceCodesShouldAllowAllOnScopeAllWithoutProjectionResolution() {
        when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(20L));
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("USER")))
            .thenReturn(Map.of("USER", 6));
        when(typeResolutionService.batchResolveOperationIds(1L, "USER", Set.of("MANAGE")))
            .thenReturn(Map.of("MANAGE", 601L));

        OperationPermission manageOp = operation(601L, 6, "MANAGE", 16L, 2L);
        when(operationPermissionMapper.selectValidByIds(1L, Set.of(601L))).thenReturn(List.of(manageOp));
        when(cacheService.getBatch(any(CacheCatalogEntry.class), eq(1L), eq(Set.of("op_perm:6"))))
            .thenReturn(Map.of("op_perm:6", Map.of(601L, manageOp)));

        RoleResourcePermission scopeAllPerm = new RoleResourcePermission();
        scopeAllPerm.setId(501L);
        scopeAllPerm.setAbstractRoleId(20L);
        scopeAllPerm.setResourceEntityId(null);
        scopeAllPerm.setResourceType(6);
        scopeAllPerm.setGrantedBits(16L);
        scopeAllPerm.setScopeAll(true);
        scopeAllPerm.setDeleteFlag(0L);
        when(rolePermMapper.selectScopeAllPermsByBitsBatch(eq(1L), eq(Set.of(20L)), any()))
            .thenReturn(List.of(scopeAllPerm));
        when(conditionDomainService.evaluate(eq(1L), any(), any())).thenAnswer(inv -> inv.getArgument(1));
        when(conflictDomainService.filterPermMutex(eq(1L), any())).thenAnswer(inv -> inv.getArgument(1));

        // 全部 code 均无投影（batchResolveResourceIds 未打桩 → 返回空 Map），scopeAll 仍放行
        Set<String> denied = engine.getDeniedResourceCodes(1L, 10L, "USER", Set.of("10", "20"), "MANAGE");

        assertTrue(denied.isEmpty());
        // scopeAll 命中短路：零 code 解析、零实例级查询
        verify(typeResolutionService, never()).batchResolveResourceIds(any(), any());
        verify(rolePermMapper, never()).selectInstancePermsByBitsBatch(any(), any(), any(), any());
    }

    /**
     * T-PERM-042（RESOURCE entityId 轨）：hasPermissionByEntityId 直接按 resource_entity.id
     * 匹配实例授权，不做任何 code 解析（资源实体管理链路语义）。
     */
    @Test
    void hasPermissionByEntityIdShouldQueryEntityIdSpaceDirectly() {
        when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(20L));
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("RESOURCE")))
            .thenReturn(Map.of("RESOURCE", 7));
        when(typeResolutionService.batchResolveOperationIds(1L, "RESOURCE", Set.of("MANAGE")))
            .thenReturn(Map.of("MANAGE", 701L));

        OperationPermission manageOp = operation(701L, 7, "MANAGE", 16L, 2L);
        when(operationPermissionMapper.selectValidByIds(1L, Set.of(701L))).thenReturn(List.of(manageOp));
        when(cacheService.getBatch(any(CacheCatalogEntry.class), eq(1L), eq(Set.of("op_perm:7"))))
            .thenReturn(Map.of("op_perm:7", Map.of(701L, manageOp)));

        when(rolePermMapper.selectScopeAllPermsByBitsBatch(eq(1L), eq(Set.of(20L)), any()))
            .thenReturn(List.of());

        RoleResourcePermission granted = new RoleResourcePermission();
        granted.setId(502L);
        granted.setAbstractRoleId(20L);
        granted.setResourceEntityId(200L);
        granted.setResourceType(7);
        granted.setGrantedBits(16L);
        granted.setDeleteFlag(0L);
        when(rolePermMapper.selectInstancePermsByBitsBatch(eq(1L), eq(Set.of(20L)), eq(Set.of(200L)), any()))
            .thenReturn(List.of(granted));
        when(conditionDomainService.evaluate(eq(1L), any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(conflictDomainService.filterPermMutex(eq(1L), any())).thenAnswer(invocation -> invocation.getArgument(1));

        assertTrue(engine.hasPermissionByEntityId(1L, 10L, "RESOURCE", 200L, "MANAGE"));
        // entityId 轨零 code 解析
        verify(typeResolutionService, never()).batchResolveResourceIds(any(), any());
        verify(rolePermMapper).selectInstancePermsByBitsBatch(eq(1L), eq(Set.of(20L)), eq(Set.of(200L)), any());
    }

    /**
     * T-PERM-034 复评 P2：操作定义冷缓存回源为批量口径——getBatch 收集 miss 类型后
     * 仅 1 次批量专属 SQL（IN），putBatch 分组回填；已命中类型不重复回源。
     * 全局操作概念已退役（2026-08-30 设计定案）：目标操作与缓存内容均为类型专属定义，
     * 位空间按类型隔离、uk_operation_permission_typed_bit 保证同位不异码。
     */
    @Test
    void resolveBitMasksShouldLoadColdCacheWithSingleBatchedRoundTrip() {
        when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(20L));
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("ATYPE", "BTYPE", "CTYPE")))
            .thenReturn(Map.of("ATYPE", 1, "BTYPE", 2, "CTYPE", 3));
        when(typeResolutionService.batchResolveOperationIds(1L, "ATYPE", Set.of("VIEW")))
            .thenReturn(Map.of("VIEW", 901L));
        when(typeResolutionService.batchResolveOperationIds(1L, "BTYPE", Set.of("VIEW")))
            .thenReturn(Map.of("VIEW", 902L));
        when(typeResolutionService.batchResolveOperationIds(1L, "CTYPE", Set.of("VIEW")))
            .thenReturn(Map.of("VIEW", 903L));

        OperationPermission aView = operation(901L, 1, "VIEW", 1L, 0L);
        OperationPermission bView = operation(902L, 2, "VIEW", 1L, 0L);
        OperationPermission cView = operation(903L, 3, "VIEW", 1L, 0L);
        when(operationPermissionMapper.selectValidByIds(1L, Set.of(901L, 902L, 903L)))
            .thenReturn(List.of(aView, bView, cView));
        // op_perm:2 已缓存，仅 {1,3} 回源
        when(cacheService.getBatch(any(CacheCatalogEntry.class), eq(1L),
                eq(Set.of("op_perm:1", "op_perm:2", "op_perm:3"))))
            .thenReturn(Map.of("op_perm:2", Map.of(902L, bView)));
        when(operationPermissionMapper.selectByTenantAndResourceTypes(1L, Set.of(1, 3)))
            .thenReturn(List.of(aView, cView));

        when(rolePermMapper.selectScopeAllPermsByBitsBatch(eq(1L), eq(Set.of(20L)), any()))
            .thenReturn(List.of());

        engine.query(PermQuery.forInterfaceCheck(1L, 10L, Set.of("ATYPE", "BTYPE", "CTYPE"), Set.of(300L), "VIEW"));

        // miss 集合一次批量专属查询、零逐类型查询
        verify(operationPermissionMapper).selectByTenantAndResourceTypes(1L, Set.of(1, 3));
        verify(operationPermissionMapper, never()).selectByTenantAndResourceType(eq(1L), any());
        // miss 类型分组回填（命中类型不回写）
        org.mockito.ArgumentCaptor<Map<String, Map<Long, OperationPermission>>> putCaptor =
            org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(cacheService).putBatch(any(CacheCatalogEntry.class), eq(1L), putCaptor.capture());
        assertEquals(Set.of("op_perm:1", "op_perm:3"), putCaptor.getValue().keySet());
    }


    // ===== T-PERM-057 targetMode 三态互不串义回归锁 =====

    /**
     * TYPE_LEVEL 不被实例授权命中：只有实例级授权行时类型级门禁必须拒绝，
     * 且管线零实例查询（TYPE_LEVEL 只消费 scopeAll——实例授权放行类型级门禁=越权）。
     */
    @Test
    void typeLevelMustNotBeSatisfiedByInstanceOnlyGrantAndNeverQueryInstance() {
        when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(20L));
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("MENU")))
            .thenReturn(Map.of("MENU", 1));
        when(typeResolutionService.batchResolveOperationIds(1L, "MENU", Set.of("VIEW")))
            .thenReturn(Map.of("VIEW", 101L));
        OperationPermission viewOp = operation(101L, 1, "VIEW", 1L, 0L);
        when(operationPermissionMapper.selectValidByIds(1L, Set.of(101L))).thenReturn(List.of(viewOp));
        when(cacheService.getBatch(any(CacheCatalogEntry.class), eq(1L), eq(Set.of("op_perm:1"))))
            .thenReturn(Map.of("op_perm:1", Map.of(101L, viewOp)));
        // 仅有实例级授权行，无 scopeAll 行
        when(rolePermMapper.selectScopeAllPermsByBitsBatch(eq(1L), eq(Set.of(20L)), any()))
            .thenReturn(List.of());

        // 类型级门禁（code=null → TYPE_LEVEL）
        assertFalse(engine.hasPermissionByCode(1L, 10L, "MENU", null, "VIEW"));
        // TYPE_LEVEL 管线零实例查询（互不串义形态锁）
        verify(rolePermMapper, never()).selectInstancePermsByBitsBatch(any(), any(), any(), any());
        verify(resourceEntityMapper, never()).selectSelfAndAncestorClosureBatch(any(), any());
    }

    /**
     * LIST 返回实例条目（修复锁：旧 forScopeQuery 形态 queryInstance=true 却无目标，
     * 实例条目永不返回——query-scopes INSTANCE 四态不可达；收编后 LIST 按角色全量拉取）。
     */
    @Test
    void listModeMustReturnInstanceEntries() {
        when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(20L));
        RolePermEntry instanceEntry = new RolePermEntry(
            501L, 20L, 200L, "res-a", 1, 1L, "VIEW", 1L, "MANUAL", false, null, false, null, false);
        when(cacheService.getBatch(any(CacheCatalogEntry.class), eq(1L), eq(Set.of(20L))))
            .thenReturn(Map.of(20L, List.of(instanceEntry)));
        when(conditionDomainService.evaluate(eq(1L), any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(conflictDomainService.filterPermMutex(eq(1L), any())).thenAnswer(invocation -> invocation.getArgument(1));

        PermResult result = engine.query(PermQuery.forScopeQuery(1L, 10L, Set.of("MENU"), Set.of("VIEW")));

        assertTrue(result.allowed());
        assertThat(result.instanceEntries()).anyMatch(e -> Long.valueOf(200L).equals(e.resourceEntityId()));
        // LIST 管线不做 scopeAll/实例 SQL 下推（按角色全量缓存读路径）
        verify(rolePermMapper, never()).selectInstancePermsByBitsBatch(any(), any(), any(), any());
    }

    /**
     * INSTANCE + 判定面继承：目标集扩为 {目标}∪同类型祖先链（闭包成员入实例查询下推）。
     */
    @Test
    void instanceClosureMustExpandQueryTargets() {
        when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(20L));
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("MENU")))
            .thenReturn(Map.of("MENU", 1));
        when(typeResolutionService.batchResolveOperationIds(1L, "MENU", Set.of("VIEW")))
            .thenReturn(Map.of("VIEW", 101L));
        OperationPermission viewOp = operation(101L, 1, "VIEW", 1L, 0L);
        when(operationPermissionMapper.selectValidByIds(1L, Set.of(101L))).thenReturn(List.of(viewOp));
        when(cacheService.getBatch(any(CacheCatalogEntry.class), eq(1L), eq(Set.of("op_perm:1"))))
            .thenReturn(Map.of("op_perm:1", Map.of(101L, viewOp)));
        when(rolePermMapper.selectScopeAllPermsByBitsBatch(eq(1L), eq(Set.of(20L)), any()))
            .thenReturn(List.of());

        // 闭包：目标 200 的祖先链含 100
        ResourceEntityMapper.AncestorClosureResult closure = new ResourceEntityMapper.AncestorClosureResult();
        closure.setTargetId(200L);
        closure.setClosureId(100L);
        when(resourceEntityMapper.selectSelfAndAncestorClosureBatch(1L, Set.of(200L)))
            .thenReturn(List.of(closure));
        // 授权行挂祖先 100（不在请求目标集内）
        RoleResourcePermission ancestorPerm = new RoleResourcePermission();
        ancestorPerm.setId(501L);
        ancestorPerm.setAbstractRoleId(20L);
        ancestorPerm.setResourceEntityId(100L);
        ancestorPerm.setResourceType(1);
        ancestorPerm.setGrantedBits(1L);
        ancestorPerm.setDeleteFlag(0L);
        when(rolePermMapper.selectInstancePermsByBitsBatch(eq(1L), eq(Set.of(20L)), eq(Set.of(200L, 100L)), any()))
            .thenReturn(List.of(ancestorPerm));
        when(conditionDomainService.evaluate(eq(1L), any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(conflictDomainService.filterPermMutex(eq(1L), any())).thenAnswer(invocation -> invocation.getArgument(1));

        assertTrue(engine.hasPermissionByEntityId(1L, 10L, "MENU", 200L, "VIEW"));
        // 判定面闭包 SQL 以请求目标为起点（查询前扩大目标集）
        verify(resourceEntityMapper).selectSelfAndAncestorClosureBatch(1L, Set.of(200L));
    }


    /**
     * 双轨评审 P1 修复锁（query-scopes 收编管线）：LIST+主资源上下文真实管线断言——
     * ①parentMatchedOperationCodes 非空（旧实现 forAuthCheck 不装 operationMap → 恒空）；
     * ②条件评估上下文透传调用方 clientIp（旧实现父判定退化空上下文，挂 IP 条件的父授权误拒）。
     */
    @Test
    void parentResourceContextMustResolveMatchedOpsAndPropagateEvalContext() {
        when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(20L));
        // 主资源 MENU / 父资源 REPORT；父 scopeAll VIEW 授权
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("REPORT")))
            .thenReturn(Map.of("REPORT", 5));
        when(typeResolutionService.batchResolveOperationIds(1L, "REPORT", Set.of("VIEW")))
            .thenReturn(Map.of("VIEW", 501L));
        OperationPermission reportView = operation(501L, 5, "VIEW", 1L, 0L);
        when(operationPermissionMapper.selectValidByIds(1L, Set.of(501L))).thenReturn(List.of(reportView));
        when(cacheService.getBatch(any(CacheCatalogEntry.class), eq(1L), eq(Set.of("op_perm:5"))))
            .thenReturn(Map.of("op_perm:5", Map.of(501L, reportView)));
        when(operationPermissionMapper.selectByTenantAndResourceType(1L, 5)).thenReturn(List.of(reportView));

        RoleResourcePermission parentScopeAll = new RoleResourcePermission();
        parentScopeAll.setId(601L);
        parentScopeAll.setAbstractRoleId(20L);
        parentScopeAll.setResourceEntityId(null);
        parentScopeAll.setResourceType(5);
        parentScopeAll.setGrantedBits(1L);
        parentScopeAll.setScopeAll(true);
        parentScopeAll.setDeleteFlag(0L);
        when(rolePermMapper.selectScopeAllPermsByBitsBatch(eq(1L), eq(Set.of(20L)), any()))
            .thenReturn(List.of(parentScopeAll));

        // LIST 全量条目（scope 行，dependOn=null）
        RolePermEntry scopeEntry = new RolePermEntry(
            701L, 20L, 300L, "dept-a", 2, 1L, "VIEW", 1L, "MANUAL", false, null, false, null, false);
        when(cacheService.getBatch(any(CacheCatalogEntry.class), eq(1L), eq(Set.of(20L))))
            .thenReturn(Map.of(20L, List.of(scopeEntry)));
        when(operationPermissionMapper.selectByTenantAndResourceType(1L, 2)).thenReturn(List.of());

        org.mockito.ArgumentCaptor<Map<String, Object>> evalCaptor =
            org.mockito.ArgumentCaptor.forClass(Map.class);
        when(conditionDomainService.evaluate(eq(1L), any(), evalCaptor.capture()))
            .thenAnswer(invocation -> invocation.getArgument(1));
        when(conflictDomainService.filterPermMutex(eq(1L), any())).thenAnswer(invocation -> invocation.getArgument(1));

        PermQuery q = PermQuery.forScopeQuery(1L, 10L, Set.of("DEPT"), Set.of("VIEW"));
        q.setParentResource("REPORT", "report:sales", "default", Set.of("VIEW"));
        q.setEvalContext(new PermEvalContext("10.1.2.3", null, Map.of()));

        PermResult result = engine.query(q);

        assertTrue(result.allowed());
        assertThat(result.parentMatchedOperationCodes()).as("matchedParentOps 非空（旧实现恒空）").contains("VIEW");
        assertThat(result.parentMatchedPermissionIds()).contains(601L);
        boolean sawCallerClientIp = evalCaptor.getAllValues().stream()
            .anyMatch(ctx -> "10.1.2.3".equals(ctx.get(PermEvalContext.KEY_CLIENT_IP)));
        assertThat(sawCallerClientIp).as("父资源条件评估透传调用方 clientIp（旧实现空上下文）").isTrue();
    }

    /**
     * grok 外评 P1 修复锁：LIST 评估清空 deny 路径仍装载操作定义（源=rawEntries 超集）——
     * 旧实现 deny 直接 build 不装 ancillary，四态组装层 covers 缺目标操作定义 → EMPTY 误判 DENIED。
     */
    @Test
    void listDenyPathMustLoadOperationDefinitionsForRawEntries() {
        when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(20L));
        // raw 条目类型=2；条件评估清空全部
        RolePermEntry rawEntry = new RolePermEntry(
            501L, 20L, 300L, "data-1", 2, 1L, "READ", 1L, "MANUAL", false, 301L, true, null, false);
        when(cacheService.getBatch(any(CacheCatalogEntry.class), eq(1L), eq(Set.of(20L))))
            .thenReturn(Map.of(20L, List.of(rawEntry)));
        when(conditionDomainService.evaluate(eq(1L), any(), any())).thenReturn(List.of());
        when(operationPermissionMapper.selectByTenantAndResourceType(1L, 2))
            .thenReturn(List.of(operation(601L, 2, "READ", 1L, 0L)));

        PermResult result = engine.query(PermQuery.forScopeQuery(1L, 10L, Set.of("DATA"), Set.of("READ")));

        assertFalse(result.allowed());
        assertEquals("CONDITION_NOT_MET_OR_CONFLICT", result.reason());
        assertEquals(1, result.rawEntries().size());
        assertThat(result.operationMap())
            .as("deny 路径操作定义装载源=rawEntries（旧实现为空）")
            .containsKey(601L);
    }

    /**
     * codex 外评 P2-1 锁：TYPE_LEVEL 有授权但条件评估清空 → reason=CONDITION_NOT_MET_OR_CONFLICT
     * （非 NO_PERMISSION——基线语义，拒绝原因进 /auth/check·explain 响应，勿把「条件不满足」
     * 误报为「无权限」）。
     */
    @Test
    void typeLevelDenyReasonMustDistinguishConditionFromNoPermission() {
        when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(20L));
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("MENU")))
            .thenReturn(Map.of("MENU", 1));
        when(typeResolutionService.batchResolveOperationIds(1L, "MENU", Set.of("VIEW")))
            .thenReturn(Map.of("VIEW", 101L));
        OperationPermission viewOp = operation(101L, 1, "VIEW", 1L, 0L);
        when(operationPermissionMapper.selectValidByIds(1L, Set.of(101L))).thenReturn(List.of(viewOp));
        when(cacheService.getBatch(any(CacheCatalogEntry.class), eq(1L), eq(Set.of("op_perm:1"))))
            .thenReturn(Map.of("op_perm:1", Map.of(101L, viewOp)));

        RoleResourcePermission conditional = new RoleResourcePermission();
        conditional.setId(501L);
        conditional.setAbstractRoleId(20L);
        conditional.setResourceEntityId(null);
        conditional.setResourceType(1);
        conditional.setGrantedBits(1L);
        conditional.setScopeAll(true);
        conditional.setConditionId(301L);
        conditional.setDeleteFlag(0L);
        when(rolePermMapper.selectScopeAllPermsByBitsBatch(eq(1L), eq(Set.of(20L)), any()))
            .thenReturn(List.of(conditional));
        // 条件评估清空全部条目
        when(conditionDomainService.evaluate(eq(1L), any(), any())).thenReturn(List.of());

        PermResult result = engine.query(PermQuery.forAuthCheck(1L, 10L, "MENU", null, "VIEW"));

        assertFalse(result.allowed());
        assertEquals("CONDITION_NOT_MET_OR_CONFLICT", result.reason(),
            "有授权但评估清空 = 条件/冲突拒绝（旧重写误报 NO_PERMISSION）");
    }

    /**
     * codex 外评 P2-2 锁：exactInstanceOnly 模式跳过 scopeAll 类型级查询与放行——
     * 角色仅有 scopeAll 授权时 explain scopeMode=INSTANCE 判定 denied（契约：精确匹配）。
     */
    @Test
    void exactInstanceOnlyMustNotFallBackToScopeAll() {
        when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(20L));
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("MENU")))
            .thenReturn(Map.of("MENU", 1));
        when(typeResolutionService.batchResolveOperationIds(1L, "MENU", Set.of("VIEW")))
            .thenReturn(Map.of("VIEW", 101L));
        OperationPermission viewOp = operation(101L, 1, "VIEW", 1L, 0L);
        when(operationPermissionMapper.selectValidByIds(1L, Set.of(101L))).thenReturn(List.of(viewOp));
        when(cacheService.getBatch(any(CacheCatalogEntry.class), eq(1L), eq(Set.of("op_perm:1"))))
            .thenReturn(Map.of("op_perm:1", Map.of(101L, viewOp)));
        when(typeResolutionService.batchResolveResourceIds(eq(1L), any()))
            .thenReturn(Map.of(new ResourceResolveKey("MENU", "sys:user", null, null), 200L));
        when(rolePermMapper.selectInstancePermsByBitsBatch(eq(1L), eq(Set.of(20L)), eq(Set.of(200L)), any()))
            .thenReturn(List.of());

        PermQuery q = PermQuery.forAuthCheck(1L, 10L, "MENU", "sys:user", "VIEW");
        q.setExactInstanceOnly(true);
        PermResult result = engine.query(q);

        assertFalse(result.allowed(), "scopeAll-only 角色 + 精确实例模式 → denied（旧重写经类型级放行）");
        verify(rolePermMapper, never()).selectScopeAllPermsByBitsBatch(any(), any(), any());
    }

    /**
     * codex 外评 P2-3 锁：SDK context Map 含 null 值不得 NPE（JSON 反序列化可产 null value，
     * Map.copyOf 禁 null 会在权限判定前抛 NPE 变 500）——null 项静默过滤。
     */
    @Test
    void evalContextMustTolerateNullValuesInCallerMap() {
        java.util.Map<String, Object> caller = new java.util.HashMap<>();
        caller.put("clientIp", "1.2.3.4");
        caller.put("extra", null);

        PermEvalContext ctx = PermEvalContext.fromCallerMap(caller);

        assertEquals("1.2.3.4", ctx.clientIp());
        org.junit.jupiter.api.Assertions.assertTrue(ctx.toEvalMap().containsKey("clientIp"));
        org.junit.jupiter.api.Assertions.assertFalse(ctx.toEvalMap().containsKey("extra"), "null 值项被过滤");
    }

    /**
     * codex 四轮 P1 锁：bypassPermSnapshot 模式绕过 ROLE_PERM_SNAPSHOT（直查 DB 不读不回填）——
     * 写校验面（canGrant）消费；旧实现（走缓存）下 verify 失败。
     */
    @Test
    void bypassPermSnapshotMustQueryDbDirectlyWithoutCache() {
        when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(20L));
        when(rolePermMapper.selectValidByRoleIds(1L, Set.of(20L))).thenReturn(List.of());

        PermQuery q = PermQuery.forUserView(1L, 10L);
        q.setBypassPermSnapshot(true);
        q.setEvaluateConditions(false);
        q.setEvaluateConflicts(false);
        PermResult result = engine.query(q);

        // bypass：直查 DB（mock 返回空）→ deny，缓存里的条目不可见；非 bypass 时会命中缓存条目
        assertFalse(result.allowed());
        verify(rolePermMapper).selectValidByRoleIds(1L, Set.of(20L));
        verify(cacheService, never()).putBatch(any(CacheReadToken.class), eq(1L), any());
    }

    /**
     * codex 四轮 P2-1 锁：scopeAll 命中但评估清空 + 目标实体解析为空（编码不存在/软删）→
     * CONDITION_NOT_MET_OR_CONFLICT（勿丢评估拒绝原因报 NO_PERMISSION）。
     */
    @Test
    void unresolvableTargetMustKeepConditionReasonWhenScopeAllEvaluatedEmpty() {
        when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(20L));
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("MENU")))
            .thenReturn(Map.of("MENU", 1));
        when(typeResolutionService.batchResolveOperationIds(1L, "MENU", Set.of("VIEW")))
            .thenReturn(Map.of("VIEW", 101L));
        OperationPermission viewOp = operation(101L, 1, "VIEW", 1L, 0L);
        when(operationPermissionMapper.selectValidByIds(1L, Set.of(101L))).thenReturn(List.of(viewOp));
        when(cacheService.getBatch(any(CacheCatalogEntry.class), eq(1L), eq(Set.of("op_perm:1"))))
            .thenReturn(Map.of("op_perm:1", Map.of(101L, viewOp)));
        // scopeAll 命中（条件行）→ 评估清空；目标编码解析不到实体
        RoleResourcePermission conditional = new RoleResourcePermission();
        conditional.setId(501L);
        conditional.setAbstractRoleId(20L);
        conditional.setResourceEntityId(null);
        conditional.setResourceType(1);
        conditional.setGrantedBits(1L);
        conditional.setScopeAll(true);
        conditional.setDeleteFlag(0L);
        when(rolePermMapper.selectScopeAllPermsByBitsBatch(eq(1L), eq(Set.of(20L)), any()))
            .thenReturn(List.of(conditional));
        when(conditionDomainService.evaluate(eq(1L), any(), any())).thenReturn(List.of());
        when(typeResolutionService.batchResolveResourceIds(eq(1L), any())).thenReturn(Map.of());

        PermResult result = engine.query(PermQuery.forAuthCheck(1L, 10L, "MENU", "ghost-code", "VIEW"));

        assertFalse(result.allowed());
        assertEquals("CONDITION_NOT_MET_OR_CONFLICT", result.reason(),
            "scopeAll 曾命中被评估清空 + 目标不可解析 = 条件拒绝（旧分支误报 NO_PERMISSION）");
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
