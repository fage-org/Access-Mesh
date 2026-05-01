package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.req.CheckInterfaceReq;
import cn.ac.fage.accessmesh.permission.dto.resp.CheckInterfaceResp;
import cn.ac.fage.accessmesh.permission.entity.AbstractUser;
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
import cn.ac.fage.accessmesh.permission.service.domain.PermCacheDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionConditionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionConflictDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionVersionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.RolePermissionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.UserRoleDomainService;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceImplCheckInterfaceTest {

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

    private PermissionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PermissionServiceImpl(
            abstractUserMapper, resourceEntityMapper, apiMappingMapper, operationPermissionMapper, rolePermMapper,
            resourceDependencyMapper, userRoleDomainService, permissionConflictDomainService,
            permissionConditionDomainService, rolePermissionDomainService, typeResolutionService, permCacheDomainService,
            permissionVersionDomainService
        );
    }

    @Test
    void shouldDenyWhenInterfaceNotRegistered() {
        CheckInterfaceReq req = new CheckInterfaceReq("USER", "u-1", "admin-service", "POST", "/api/user/list", Map.of());
        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
        AbstractUser user = new AbstractUser();
        user.setId(10L);
        user.setDeleteFlag(0L);
        user.setEnabled(true);
        when(abstractUserMapper.selectOneById(10L)).thenReturn(user);
        when(apiMappingMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

        CheckInterfaceResp resp = service.checkInterface(1L, req);

        assertFalse(resp.allowed());
        assertEquals("API_NOT_REGISTERED", resp.reason());
        assertTrue(resp.matchedResources().isEmpty());
    }

    @Test
    void shouldAllowWhenAnyMatchedMappingPasses() {
        CheckInterfaceReq req = new CheckInterfaceReq("USER", "u-1", "admin-service", "POST", "/api/user/list", Map.of());
        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
        AbstractUser user = new AbstractUser();
        user.setId(10L);
        user.setDeleteFlag(0L);
        user.setEnabled(true);
        when(abstractUserMapper.selectOneById(10L)).thenReturn(user);

        ResourceApiMapping m1 = new ResourceApiMapping();
        m1.setResourceEntityId(100L);
        m1.setPathPattern("/api/user/list");
        ResourceApiMapping m2 = new ResourceApiMapping();
        m2.setResourceEntityId(101L);
        m2.setPathPattern("/api/user/list");
        when(apiMappingMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(m1, m2));

        when(userRoleDomainService.resolveEffectiveRoles(1L, 10L, null)).thenReturn(Set.of(200L));
        when(permissionConflictDomainService.filterRoleMutex(1L, Set.of(200L))).thenReturn(Set.of(200L));

        ResourceEntity r1 = new ResourceEntity();
        r1.setId(100L);
        r1.setDeleteFlag(0L);
        r1.setResourceType(1);
        r1.setCode("api:user:list:1");
        ResourceEntity r2 = new ResourceEntity();
        r2.setId(101L);
        r2.setDeleteFlag(0L);
        r2.setResourceType(1);
        r2.setCode("api:user:list:2");
        when(resourceEntityMapper.selectOneById(100L)).thenReturn(r1);
        when(resourceEntityMapper.selectOneById(101L)).thenReturn(r2);
        OperationPermission op = new OperationPermission();
        op.setId(300L);
        op.setCode("ACCESS");
        op.setBinaryBit(1L);
        op.setInheritMask(0L);
        when(typeResolutionService.resolveTypeCode(1L, "resource_type", 1)).thenReturn("API");
        when(operationPermissionMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(op), List.of(op));
        when(operationPermissionMapper.selectOneById(300L)).thenReturn(op);

        RoleResourcePermission denyPerm = new RoleResourcePermission();
        denyPerm.setId(400L);
        denyPerm.setAbstractRoleId(200L);
        denyPerm.setResourceEntityId(100L);
        denyPerm.setOperationPermissionId(300L);
        denyPerm.setResourceType(1);
        denyPerm.setDeleteFlag(0L);
        RoleResourcePermission allowPerm = new RoleResourcePermission();
        allowPerm.setId(401L);
        allowPerm.setAbstractRoleId(200L);
        allowPerm.setResourceEntityId(101L);
        allowPerm.setOperationPermissionId(300L);
        allowPerm.setResourceType(1);
        allowPerm.setDeleteFlag(0L);
        when(rolePermMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(denyPerm), List.of(allowPerm));

        when(permissionConditionDomainService.evaluate(eq(1L), any(), any())).thenAnswer(inv -> inv.getArgument(1));
        when(permissionConflictDomainService.filterPermMutex(eq(1L), any()))
            .thenReturn(List.of(), List.of(new RolePermSnapshot.RolePermEntry(
                401L, 200L, 101L, null, 1, 300L, "ACCESS", null, "MANUAL", false, null, false, null
            )));

        CheckInterfaceResp resp = service.checkInterface(1L, req);

        assertTrue(resp.allowed());
        assertEquals(2, resp.matchedResources().size());
        assertTrue(resp.matchedResources().stream().allMatch(item -> "API".equals(item.resourceTypeCode())));
        assertTrue(resp.matchedResources().stream().anyMatch(CheckInterfaceResp.MatchedResource::allowed));
    }

    @Test
    void shouldDenyNoPermissionWhenResourceExists() {
        CheckInterfaceReq req = new CheckInterfaceReq("USER", "u-1", "admin-service", "POST", "/api/user/list", Map.of());
        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
        AbstractUser user = new AbstractUser();
        user.setId(10L);
        user.setDeleteFlag(0L);
        user.setEnabled(true);
        when(abstractUserMapper.selectOneById(10L)).thenReturn(user);

        ResourceApiMapping mapping = new ResourceApiMapping();
        mapping.setResourceEntityId(100L);
        mapping.setPathPattern("/api/user/list");
        when(apiMappingMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(mapping));
        when(userRoleDomainService.resolveEffectiveRoles(1L, 10L, null)).thenReturn(Set.of(200L));
        when(permissionConflictDomainService.filterRoleMutex(1L, Set.of(200L))).thenReturn(Set.of(200L));

        ResourceEntity r1 = new ResourceEntity();
        r1.setId(100L);
        r1.setDeleteFlag(0L);
        r1.setResourceType(1);
        r1.setCode("api:user:list:1");
        when(resourceEntityMapper.selectOneById(100L)).thenReturn(r1);
        when(typeResolutionService.resolveTypeCode(1L, "resource_type", 1)).thenReturn("API");

        OperationPermission op = new OperationPermission();
        op.setId(300L);
        op.setCode("ACCESS");
        op.setBinaryBit(1L);
        op.setInheritMask(0L);
        when(operationPermissionMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(op));
        when(rolePermMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

        CheckInterfaceResp resp = service.checkInterface(1L, req);

        assertFalse(resp.allowed());
        assertEquals("NO_PERMISSION", resp.reason());
        assertEquals(1, resp.matchedResources().size());
        assertFalse(resp.matchedResources().get(0).allowed());
    }
}
