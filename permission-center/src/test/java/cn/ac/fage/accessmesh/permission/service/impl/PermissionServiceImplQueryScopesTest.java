package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.req.QueryScopesReq;
import cn.ac.fage.accessmesh.permission.dto.resp.QueryScopesResp;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceDependencyMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.PermCacheDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionConditionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionConflictDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionVersionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.EntityBatchLoadDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.RolePermissionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.UserRoleDomainService;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceImplQueryScopesTest {

    @Mock private AbstractUserMapper abstractUserMapper;
    @Mock private ResourceEntityMapper resourceEntityMapper;
    @Mock private ResourceApiMappingMapper apiMappingMapper;
    @Mock private OperationPermissionMapper operationPermissionMapper;
    @Mock private RoleResourcePermissionMapper rolePermMapper;
    @Mock private ResourceDependencyMapper resourceDependencyMapper;
    @Mock private UserRoleDomainService userRoleDomainService;
    @Mock private PermissionConflictDomainService permissionConflictDomainService;
    @Mock private PermissionConditionDomainService permissionConditionDomainService;
    @Mock private RolePermissionDomainService rolePermissionDomainService;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private PermCacheDomainService permCacheDomainService;
    @Mock private PermissionVersionDomainService permissionVersionDomainService;
    @Mock private ResourceEntityDomainService resourceEntityDomainService;
    @Mock private EntityBatchLoadDomainService entityBatchLoadDomainService;

    private PermissionServiceImpl service;

    private QueryScopesReq buildReq() {
        return new QueryScopesReq(
            "USER",
            "u-1",
            "MENU",
            "sys:user",
            "default",
            List.of("VIEW"),
            List.of("MENU"),
            List.of("VIEW"),
            "default",
            null,
            java.util.Map.of()
        );
    }

    @BeforeEach
    void setUp() {
        service = new PermissionServiceImpl(
            abstractUserMapper, resourceEntityMapper, apiMappingMapper, operationPermissionMapper, rolePermMapper,
            resourceDependencyMapper, userRoleDomainService, permissionConflictDomainService,
            permissionConditionDomainService, rolePermissionDomainService, typeResolutionService, permCacheDomainService,
            permissionVersionDomainService, resourceEntityDomainService, entityBatchLoadDomainService
        );
    }

    @Test
    void shouldReturnScopeAllWhenRoleHasGlobalScopePermission() {
        QueryScopesReq req = buildReq();
        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
        when(typeResolutionService.resolveResourceId(1L, "MENU", "sys:user", "default", null)).thenReturn(100L);
        when(typeResolutionService.resolveOperationId(1L, "VIEW", "MENU")).thenReturn(300L);
        when(typeResolutionService.resolveDomainId(1L, null)).thenReturn(0L);

        when(userRoleDomainService.resolveEffectiveRoles(1L, 10L, 0L)).thenReturn(Set.of(200L));
        when(permissionConflictDomainService.filterRoleMutex(1L, Set.of(200L))).thenReturn(Set.of(200L));
        when(permissionConditionDomainService.evaluate(any(), any(), any())).thenAnswer(inv -> inv.getArgument(1));
        when(permissionConflictDomainService.filterPermMutex(any(), any())).thenAnswer(inv -> inv.getArgument(1));

        RoleResourcePermission parentPerm = new RoleResourcePermission();
        parentPerm.setId(400L);
        parentPerm.setAbstractRoleId(200L);
        parentPerm.setOperationPermissionId(300L);
        parentPerm.setResourceType(1);
        parentPerm.setResourceEntityId(100L);
        parentPerm.setDeleteFlag(0L);

        RoleResourcePermission scopeAllPerm = new RoleResourcePermission();
        scopeAllPerm.setAbstractRoleId(200L);
        scopeAllPerm.setOperationPermissionId(300L);
        scopeAllPerm.setResourceType(1);
        scopeAllPerm.setResourceEntityId(null);
        scopeAllPerm.setScopeAll(true);
        scopeAllPerm.setDependOn(400L);
        scopeAllPerm.setDeleteFlag(0L);
        when(rolePermMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(parentPerm), List.of(scopeAllPerm));

        OperationPermission op = new OperationPermission();
        op.setId(300L);
        op.setCode("VIEW");
        op.setBinaryBit(1L);
        op.setInheritMask(0L);
        when(operationPermissionMapper.selectOneById(300L)).thenReturn(op);
        when(entityBatchLoadDomainService.batchLoadOperations(eq(1L), any(Set.class)))
            .thenReturn(Map.of(300L, op));

        QueryScopesResp resp = service.queryScopes(1L, req);

        assertTrue(resp.allowed());
        assertTrue(resp.items().stream().anyMatch(QueryScopesResp.ScopeEntry::scopeAll));
    }

    @Test
    void shouldNotReturnScopeAllWhenConditionNotMet() {
        QueryScopesReq req = buildReq();
        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
        when(typeResolutionService.resolveResourceId(1L, "MENU", "sys:user", "default", null)).thenReturn(100L);
        when(typeResolutionService.resolveOperationId(1L, "VIEW", "MENU")).thenReturn(300L);
        when(typeResolutionService.resolveDomainId(1L, null)).thenReturn(0L);
        when(userRoleDomainService.resolveEffectiveRoles(1L, 10L, 0L)).thenReturn(Set.of(200L));
        when(permissionConflictDomainService.filterRoleMutex(1L, Set.of(200L))).thenReturn(Set.of(200L));

        RoleResourcePermission scopeAllPerm = new RoleResourcePermission();
        scopeAllPerm.setId(500L);
        scopeAllPerm.setAbstractRoleId(200L);
        scopeAllPerm.setOperationPermissionId(300L);
        scopeAllPerm.setResourceType(1);
        scopeAllPerm.setResourceEntityId(null);
        scopeAllPerm.setScopeAll(true);
        scopeAllPerm.setDependOn(401L);
        scopeAllPerm.setConditionId(999L);
        scopeAllPerm.setDeleteFlag(0L);
        RoleResourcePermission parentPerm = new RoleResourcePermission();
        parentPerm.setId(401L);
        parentPerm.setAbstractRoleId(200L);
        parentPerm.setOperationPermissionId(300L);
        parentPerm.setResourceType(1);
        parentPerm.setResourceEntityId(100L);
        parentPerm.setDeleteFlag(0L);
        when(rolePermMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(parentPerm), List.of(scopeAllPerm));

        OperationPermission op = new OperationPermission();
        op.setId(300L);
        op.setCode("VIEW");
        op.setBinaryBit(1L);
        op.setInheritMask(0L);
        when(operationPermissionMapper.selectOneById(300L)).thenReturn(op);
        when(permissionConditionDomainService.evaluate(any(), any(), any())).thenReturn(List.of());
        when(permissionConflictDomainService.filterPermMutex(any(), any())).thenAnswer(inv -> inv.getArgument(1));
        when(entityBatchLoadDomainService.batchLoadOperations(eq(1L), any(Set.class)))
            .thenReturn(Map.of(300L, op));

        QueryScopesResp resp = service.queryScopes(1L, req);
        assertFalse(resp.allowed());
        assertFalse(resp.items().stream().anyMatch(QueryScopesResp.ScopeEntry::scopeAll));
    }

    @Test
    void shouldNotReturnScopeAllWhenConflictFiltered() {
        QueryScopesReq req = buildReq();
        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
        when(typeResolutionService.resolveResourceId(1L, "MENU", "sys:user", "default", null)).thenReturn(100L);
        when(typeResolutionService.resolveOperationId(1L, "VIEW", "MENU")).thenReturn(300L);
        when(typeResolutionService.resolveDomainId(1L, null)).thenReturn(0L);
        when(userRoleDomainService.resolveEffectiveRoles(1L, 10L, 0L)).thenReturn(Set.of(200L));
        when(permissionConflictDomainService.filterRoleMutex(1L, Set.of(200L))).thenReturn(Set.of(200L));

        RoleResourcePermission scopeAllPerm = new RoleResourcePermission();
        scopeAllPerm.setId(501L);
        scopeAllPerm.setAbstractRoleId(200L);
        scopeAllPerm.setOperationPermissionId(300L);
        scopeAllPerm.setResourceType(1);
        scopeAllPerm.setResourceEntityId(null);
        scopeAllPerm.setScopeAll(true);
        scopeAllPerm.setDependOn(402L);
        scopeAllPerm.setDeleteFlag(0L);
        RoleResourcePermission parentPerm = new RoleResourcePermission();
        parentPerm.setId(402L);
        parentPerm.setAbstractRoleId(200L);
        parentPerm.setOperationPermissionId(300L);
        parentPerm.setResourceType(1);
        parentPerm.setResourceEntityId(100L);
        parentPerm.setDeleteFlag(0L);
        when(rolePermMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(parentPerm), List.of(scopeAllPerm));

        OperationPermission op = new OperationPermission();
        op.setId(300L);
        op.setCode("VIEW");
        op.setBinaryBit(1L);
        op.setInheritMask(0L);
        when(operationPermissionMapper.selectOneById(300L)).thenReturn(op);
        when(permissionConditionDomainService.evaluate(any(), any(), any())).thenAnswer(inv -> inv.getArgument(1));
        when(permissionConflictDomainService.filterPermMutex(any(), any())).thenReturn(List.of());
        when(entityBatchLoadDomainService.batchLoadOperations(eq(1L), any(Set.class)))
            .thenReturn(Map.of(300L, op));

        QueryScopesResp resp = service.queryScopes(1L, req);
        assertFalse(resp.allowed());
        assertFalse(resp.items().stream().anyMatch(QueryScopesResp.ScopeEntry::scopeAll));
    }

    @Test
    void shouldReturnDirectAndDependentUnionItems() {
        QueryScopesReq req = buildReq();
        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
        when(typeResolutionService.resolveResourceId(1L, "MENU", "sys:user", "default", null)).thenReturn(100L);
        when(typeResolutionService.resolveOperationId(1L, "VIEW", "MENU")).thenReturn(300L);
        when(typeResolutionService.resolveDomainId(1L, null)).thenReturn(0L);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "MENU")).thenReturn(1);
        when(userRoleDomainService.resolveEffectiveRoles(1L, 10L, 0L)).thenReturn(Set.of(200L));
        when(permissionConflictDomainService.filterRoleMutex(1L, Set.of(200L))).thenReturn(Set.of(200L));
        when(permissionConditionDomainService.evaluate(any(), any(), any())).thenAnswer(inv -> inv.getArgument(1));
        when(permissionConflictDomainService.filterPermMutex(any(), any())).thenAnswer(inv -> inv.getArgument(1));

        RoleResourcePermission parentPerm = new RoleResourcePermission();
        parentPerm.setId(410L);
        parentPerm.setAbstractRoleId(200L);
        parentPerm.setOperationPermissionId(300L);
        parentPerm.setResourceType(1);
        parentPerm.setResourceEntityId(100L);
        parentPerm.setDeleteFlag(0L);

        RoleResourcePermission directScope = new RoleResourcePermission();
        directScope.setId(411L);
        directScope.setAbstractRoleId(200L);
        directScope.setOperationPermissionId(300L);
        directScope.setResourceType(1);
        directScope.setResourceEntityId(101L);
        directScope.setDependOn(null);
        directScope.setScopeAll(false);
        directScope.setDeleteFlag(0L);

        RoleResourcePermission dependentScope = new RoleResourcePermission();
        dependentScope.setId(412L);
        dependentScope.setAbstractRoleId(200L);
        dependentScope.setOperationPermissionId(300L);
        dependentScope.setResourceType(1);
        dependentScope.setResourceEntityId(102L);
        dependentScope.setDependOn(410L);
        dependentScope.setScopeAll(false);
        dependentScope.setDeleteFlag(0L);

        when(rolePermMapper.selectListByQuery(any(QueryWrapper.class)))
            .thenReturn(List.of(parentPerm), List.of(directScope, dependentScope));

        OperationPermission op = new OperationPermission();
        op.setId(300L);
        op.setCode("VIEW");
        op.setBinaryBit(1L);
        op.setInheritMask(0L);
        when(operationPermissionMapper.selectOneById(300L)).thenReturn(op);
        when(entityBatchLoadDomainService.batchLoadOperations(eq(1L), any(Set.class)))
            .thenReturn(Map.of(300L, op));

        ResourceEntity directResource = new ResourceEntity();
        directResource.setId(101L);
        directResource.setDeleteFlag(0L);
        directResource.setCode("dept:a");
        directResource.setCodeType("default");
        directResource.setName("Dept A");
        ResourceEntity dependentResource = new ResourceEntity();
        dependentResource.setId(102L);
        dependentResource.setDeleteFlag(0L);
        dependentResource.setCode("dept:b");
        dependentResource.setCodeType("default");
        dependentResource.setName("Dept B");
        when(entityBatchLoadDomainService.batchLoadResources(eq(1L), any(Set.class)))
            .thenReturn(Map.of(101L, directResource, 102L, dependentResource));

        QueryScopesResp resp = service.queryScopes(1L, req);
        assertTrue(resp.allowed());
        assertEquals(2, resp.items().size());
        assertEquals(1, resp.items().stream().filter(i -> i.sources().contains("DIRECT")).count());
        assertEquals(1, resp.items().stream().filter(i -> i.sources().contains("DEPENDENT")).count());
    }
}
