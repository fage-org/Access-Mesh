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
import cn.ac.fage.accessmesh.permission.service.domain.UserRoleDomainService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermQueryEngineTest {

    @Mock
    private UserRoleDomainService userRoleDomainService;
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
            userRoleDomainService,
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
        when(userRoleDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(20L));
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