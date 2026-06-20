package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.dto.query.PermQuery;
import cn.ac.fage.accessmesh.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper.BitMaskEntry;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionConditionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionConflictDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.permission.util.RolePermEntryMapper;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot.RolePermEntry;
import cn.ac.fage.accessmesh.permission.cache.PermCacheCatalog;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

        // Mock cacheService.get() to return operation permissions map for ID index
        Map<Long, OperationPermission> opMap = Map.of(101L, viewOp, 102L, manageOp);
        when(cacheService.get(any(CacheCatalogEntry.class), eq(1L), eq("op_perm:1"))).thenReturn(opMap);

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

        ResourceEntity resource = new ResourceEntity();
        resource.setId(200L);
        resource.setCode("sys:user");
        resource.setCodeType("default");
        resource.setName("用户资源");
        resource.setResourceType(1);
        when(resourceEntityMapper.selectValidByIds(1L, Set.of(200L))).thenReturn(List.of(resource));

        PermQuery query = PermQuery.forScopeQuery(1L, 10L, Set.of("MENU"), Set.of("VIEW"));
        query.setResourceCodes(Set.of("sys:user"));
        query.setResourceEntityIds(Set.of(200L));
        query.setEvaluateConditions(true);
        query.setEvaluateConflicts(true);
        query.setEvaluateMatchesBit(true);

        PermResult result = engine.query(query);

        assertEquals(1, result.instanceEntries().size());
        assertTrue(result.operationMap().values().stream().anyMatch(op -> "MANAGE".equals(op.getCode())));
        verify(rolePermMapper).selectInstancePermsByBitsBatch(1L, Set.of(20L), Set.of(200L), bitMaskEntries);
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
        verify(cacheService, never()).putBatch(eq(PermCacheCatalog.ROLE_PERM_SNAPSHOT), eq(1L), any());
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
        verify(cacheService).putBatch(eq(PermCacheCatalog.ROLE_PERM_SNAPSHOT), eq(1L), captor.capture());
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
        verify(cacheService).putBatch(eq(PermCacheCatalog.ROLE_PERM_SNAPSHOT), eq(1L), captor.capture());
        List<RolePermEntry> cachedForRole = captor.getValue().get(20L);
        assertNotNull(cachedForRole);
        assertTrue(cachedForRole.isEmpty());
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
