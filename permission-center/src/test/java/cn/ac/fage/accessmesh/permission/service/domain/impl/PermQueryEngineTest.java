package cn.ac.fage.accessmesh.permission.service.domain.impl;

import cn.ac.fage.accessmesh.permission.dto.query.PermQuery;
import cn.ac.fage.accessmesh.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.EntityBatchLoadDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionConditionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionConflictDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.UserRoleDomainService;
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
    private EntityBatchLoadDomainService entityBatchLoadService;
    @Mock
    private PermissionConditionDomainService conditionDomainService;
    @Mock
    private PermissionConflictDomainService conflictDomainService;
    @Mock
    private TypeResolutionService typeResolutionService;

    private PermQueryEngine engine;

    @BeforeEach
    void setUp() {
        engine = new PermQueryEngine(
            userRoleDomainService,
            rolePermMapper,
            entityBatchLoadService,
            conditionDomainService,
            conflictDomainService,
            new RolePermEntryMapper(),
            typeResolutionService
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

        when(entityBatchLoadService.batchLoadOperations(1L, Set.of(101L))).thenReturn(Map.of(101L, viewOp));
        when(entityBatchLoadService.batchLoadOperationsByResourceTypes(1L, Set.of(1)))
            .thenReturn(Map.of(1, List.of(viewOp, manageOp)));

        RoleResourcePermission grantedPerm = new RoleResourcePermission();
        grantedPerm.setId(501L);
        grantedPerm.setAbstractRoleId(20L);
        grantedPerm.setResourceEntityId(200L);
        grantedPerm.setResourceType(1);
        grantedPerm.setGrantedBits(8L);
        grantedPerm.setDeleteFlag(0L);

        when(rolePermMapper.selectScopeAllPermsByBits(1L, Set.of(20L), Set.of(1), 9L)).thenReturn(List.of());
        when(rolePermMapper.selectInstancePermsByBits(1L, Set.of(20L), Set.of(200L), Set.of(1), 9L))
            .thenReturn(List.of(grantedPerm));
        when(conditionDomainService.evaluate(eq(1L), any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(conflictDomainService.filterPermMutex(eq(1L), any())).thenAnswer(invocation -> invocation.getArgument(1));

        ResourceEntity resource = new ResourceEntity();
        resource.setId(200L);
        resource.setCode("sys:user");
        resource.setCodeType("default");
        resource.setName("用户资源");
        resource.setResourceType(1);
        when(entityBatchLoadService.batchLoadResources(1L, Set.of(200L))).thenReturn(Map.of(200L, resource));

        PermQuery query = PermQuery.forFullQuery(1L, 10L, Set.of("MENU"), Set.of("sys:user"), Set.of("VIEW"));
        query.setResourceEntityIds(Set.of(200L));

        PermResult result = engine.query(query);

        assertEquals(1, result.instanceEntries().size());
        assertTrue(result.operationMap().values().stream().anyMatch(op -> "MANAGE".equals(op.getCode())));
        verify(rolePermMapper).selectInstancePermsByBits(1L, Set.of(20L), Set.of(200L), Set.of(1), 9L);
        verify(rolePermMapper, never()).selectInstancePerms(1L, Set.of(20L), Set.of(200L), Set.of(101L));
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