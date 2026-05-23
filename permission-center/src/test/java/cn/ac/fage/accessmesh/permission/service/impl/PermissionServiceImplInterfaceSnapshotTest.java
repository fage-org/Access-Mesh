package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.permission.cache.PermCacheCatalog;
import cn.ac.fage.accessmesh.permission.dto.req.InterfaceSnapshotReq;
import cn.ac.fage.accessmesh.permission.dto.resp.InterfaceSnapshotResp;
import cn.ac.fage.accessmesh.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.permission.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.entity.RoleResourcePermission;
import cn.ac.fage.accessmesh.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceDependencyMapper;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionConditionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionConflictDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionVersionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.permission.util.RolePermEntryMapper;
import cn.ac.fage.accessmesh.permission.vo.InterfaceSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceImplInterfaceSnapshotTest {

    @Mock private AbstractUserMapper abstractUserMapper;
    @Mock private ResourceEntityMapper resourceEntityMapper;
    @Mock private ResourceApiMappingMapper apiMappingMapper;
    @Mock private OperationPermissionMapper operationPermissionMapper;
    @Mock private RoleResourcePermissionMapper rolePermMapper;
    @Mock private ResourceDependencyMapper resourceDependencyMapper;
    @Mock private SubjectDomainService subjectDomainService;
    @Mock private PermissionConflictDomainService permissionConflictDomainService;
    @Mock private PermissionConditionDomainService permissionConditionDomainService;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private CacheService cacheService;
    @Mock private PermissionVersionDomainService permissionVersionDomainService;
    @Mock private ResourceEntityDomainService resourceEntityDomainService;
    @Mock private RolePermEntryMapper rolePermEntryMapper;
    @Mock private PermQueryEngine engine;

    private PermissionServiceImpl service;
    private Map<String, InterfaceSnapshot> snapshotCache;

    @BeforeEach
    void setUp() {
        service = new PermissionServiceImpl(
            abstractUserMapper, resourceEntityMapper, apiMappingMapper, operationPermissionMapper, rolePermMapper,
            subjectDomainService, permissionConflictDomainService,
            permissionConditionDomainService, typeResolutionService, cacheService,
            permissionVersionDomainService, resourceEntityDomainService,
            engine
        );

        snapshotCache = new HashMap<>();
        when(cacheService.get(eq(PermCacheCatalog.INTERFACE_SNAPSHOT), eq(1L), any()))
            .thenAnswer(invocation -> snapshotCache.get(invocation.getArgument(2, String.class)));
        doAnswer(invocation -> {
            snapshotCache.put(
                invocation.getArgument(2, String.class),
                invocation.getArgument(3, InterfaceSnapshot.class)
            );
            return null;
        }).when(cacheService).put(eq(PermCacheCatalog.INTERFACE_SNAPSHOT), eq(1L), any(), any());
    }

    @Test
    void shouldReturnNotModifiedWhenPermissionTokenMatchesCurrentState() {
        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
        when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(200L));
        when(permissionConflictDomainService.filterRoleMutex(1L, Set.of(200L))).thenReturn(Set.of(200L));
        when(permissionVersionDomainService.batchGetCurrentVersions(1L, Set.of(200L))).thenReturn(Map.of(200L, 7L));
        when(rolePermMapper.selectValidByRoleIds(1L, Set.of(200L))).thenReturn(List.of(resourcePerm(200L, 100L, false, null)));
        when(apiMappingMapper.selectForSnapshot(1L, "admin-service", Set.of(100L)))
            .thenReturn(List.of(apiMapping(100L, "admin-service", "POST", "/api/user/list")));

        InterfaceSnapshotResp first = service.interfaceSnapshot(1L, new InterfaceSnapshotReq(
            "USER", "u-1", "admin-service", null
        ));

        InterfaceSnapshotResp second = service.interfaceSnapshot(1L, new InterfaceSnapshotReq(
            "USER", "u-1", "admin-service", first.permissionVersion()
        ));

        assertFalse(first.notModified());
        assertEquals(1, first.allowedApis().size());
        assertTrue(second.notModified());
        assertEquals(first.permissionVersion(), second.permissionVersion());
        assertTrue(second.allowedApis().isEmpty());
        verify(apiMappingMapper, times(1)).selectForSnapshot(1L, "admin-service", Set.of(100L));
    }

    @Test
    void shouldRebuildSnapshotWhenPermissionTokenChanges() {
        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
        when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(200L));
        when(permissionConflictDomainService.filterRoleMutex(1L, Set.of(200L))).thenReturn(Set.of(200L));
        when(permissionVersionDomainService.batchGetCurrentVersions(1L, Set.of(200L)))
            .thenReturn(Map.of(200L, 7L), Map.of(200L, 8L));
        when(rolePermMapper.selectValidByRoleIds(1L, Set.of(200L)))
            .thenReturn(List.of(resourcePerm(200L, 100L, false, null)))
            .thenReturn(List.of(resourcePerm(200L, 101L, false, null)));
        when(apiMappingMapper.selectForSnapshot(1L, "admin-service", Set.of(100L)))
            .thenReturn(List.of(apiMapping(100L, "admin-service", "POST", "/api/user/list")));
        when(apiMappingMapper.selectForSnapshot(1L, "admin-service", Set.of(101L)))
            .thenReturn(List.of(apiMapping(101L, "admin-service", "POST", "/api/user/export")));

        InterfaceSnapshotResp first = service.interfaceSnapshot(1L, new InterfaceSnapshotReq(
            "USER", "u-1", "admin-service", null
        ));
        InterfaceSnapshotResp second = service.interfaceSnapshot(1L, new InterfaceSnapshotReq(
            "USER", "u-1", "admin-service", first.permissionVersion()
        ));

        assertFalse(first.notModified());
        assertFalse(second.notModified());
        assertNotEquals(first.permissionVersion(), second.permissionVersion());
        assertEquals("/api/user/list", first.allowedApis().get(0).pathPattern());
        assertEquals("/api/user/export", second.allowedApis().get(0).pathPattern());
    }

    @Test
    void shouldIsolateSnapshotsByPermissionTokenForDifferentUsers() {
        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
        when(typeResolutionService.resolveUserId(1L, "USER", "u-2")).thenReturn(11L);
        when(subjectDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(200L));
        when(subjectDomainService.resolveEffectiveRoles(1L, 11L)).thenReturn(Set.of(201L));
        when(permissionConflictDomainService.filterRoleMutex(1L, Set.of(200L))).thenReturn(Set.of(200L));
        when(permissionConflictDomainService.filterRoleMutex(1L, Set.of(201L))).thenReturn(Set.of(201L));
        when(permissionVersionDomainService.batchGetCurrentVersions(1L, Set.of(200L))).thenReturn(Map.of(200L, 7L));
        when(permissionVersionDomainService.batchGetCurrentVersions(1L, Set.of(201L))).thenReturn(Map.of(201L, 7L));
        when(rolePermMapper.selectValidByRoleIds(1L, Set.of(200L))).thenReturn(List.of(resourcePerm(200L, 100L, false, null)));
        when(rolePermMapper.selectValidByRoleIds(1L, Set.of(201L))).thenReturn(List.of(resourcePerm(201L, 101L, false, null)));
        when(apiMappingMapper.selectForSnapshot(1L, "admin-service", Set.of(100L)))
            .thenReturn(List.of(apiMapping(100L, "admin-service", "POST", "/api/user/list")));
        when(apiMappingMapper.selectForSnapshot(1L, "admin-service", Set.of(101L)))
            .thenReturn(List.of(apiMapping(101L, "admin-service", "POST", "/api/user/export")));

        InterfaceSnapshotResp first = service.interfaceSnapshot(1L, new InterfaceSnapshotReq(
            "USER", "u-1", "admin-service", null
        ));
        InterfaceSnapshotResp second = service.interfaceSnapshot(1L, new InterfaceSnapshotReq(
            "USER", "u-2", "admin-service", null
        ));

        assertNotEquals(first.permissionVersion(), second.permissionVersion());
        assertEquals("/api/user/list", first.allowedApis().get(0).pathPattern());
        assertEquals("/api/user/export", second.allowedApis().get(0).pathPattern());
    }

    private ResourceApiMapping apiMapping(Long resourceEntityId, String serviceCode, String httpMethod, String pathPattern) {
        ResourceApiMapping mapping = new ResourceApiMapping();
        mapping.setResourceEntityId(resourceEntityId);
        mapping.setServiceCode(serviceCode);
        mapping.setHttpMethod(httpMethod);
        mapping.setPathPattern(pathPattern);
        return mapping;
    }

    private RoleResourcePermission resourcePerm(Long roleId, Long resourceEntityId, boolean scopeAll, Long conditionId) {
        RoleResourcePermission permission = new RoleResourcePermission();
        permission.setAbstractRoleId(roleId);
        permission.setResourceEntityId(resourceEntityId);
        permission.setScopeAll(scopeAll);
        permission.setConditionId(conditionId);
        permission.setDeleteFlag(0L);
        return permission;
    }
}