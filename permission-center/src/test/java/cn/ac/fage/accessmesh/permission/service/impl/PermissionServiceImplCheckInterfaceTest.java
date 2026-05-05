package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.req.CheckInterfaceReq;
import cn.ac.fage.accessmesh.permission.dto.query.PermQuery;
import cn.ac.fage.accessmesh.permission.dto.query.PermResult;
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
import cn.ac.fage.accessmesh.permission.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.EntityBatchLoadDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.RolePermissionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.permission.service.domain.impl.RolePermEntryMapper;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.UserRoleDomainService;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot.RolePermEntry;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
    @Mock private ResourceEntityDomainService resourceEntityDomainService;
    @Mock private EntityBatchLoadDomainService entityBatchLoadDomainService;
    @Mock private RolePermEntryMapper rolePermEntryMapper;
    @Mock private PermQueryEngine engine;

    private PermissionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PermissionServiceImpl(
            abstractUserMapper, resourceEntityMapper, apiMappingMapper, operationPermissionMapper, rolePermMapper,
            resourceDependencyMapper, userRoleDomainService, permissionConflictDomainService,
            permissionConditionDomainService, rolePermissionDomainService, typeResolutionService, permCacheDomainService,
            permissionVersionDomainService, resourceEntityDomainService, entityBatchLoadDomainService,
            rolePermEntryMapper, engine
        );
    }

    @Test
    void shouldDenyWhenInterfaceNotRegistered() {
        CheckInterfaceReq req = new CheckInterfaceReq("USER", "u-1", "admin-service", "POST", "/api/user/list", Map.of());
        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
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

        ResourceApiMapping m1 = new ResourceApiMapping();
        m1.setResourceEntityId(100L);
        m1.setPathPattern("/api/user/list");
        ResourceApiMapping m2 = new ResourceApiMapping();
        m2.setResourceEntityId(101L);
        m2.setPathPattern("/api/user/list");
        when(apiMappingMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(m1, m2));

        ResourceEntity r1 = new ResourceEntity();
        r1.setId(100L); r1.setDeleteFlag(0L); r1.setResourceType(1); r1.setCode("api:user:list:1");
        ResourceEntity r2 = new ResourceEntity();
        r2.setId(101L); r2.setDeleteFlag(0L); r2.setResourceType(1); r2.setCode("api:user:list:2");
        OperationPermission op = new OperationPermission();
        op.setId(300L); op.setCode("ACCESS"); op.setBinaryBit(1L); op.setInheritMask(0L);

        RolePermEntry allowEntry = new RolePermEntry(
            401L, 200L, 101L, null, 1, 300L, null, null, "MANUAL", false, null, false, null);

        PermResult mockResult = PermResult.builder(true, null)
            .scopeAllMatched(false)
            .scopeAllEntries(List.of())
            .instanceEntries(List.of(allowEntry))
            .resourceMap(Map.of(101L, r2))
            .operationMap(Map.of(300L, op))
            .build();

        when(engine.query(any(PermQuery.class))).thenReturn(mockResult);

        CheckInterfaceResp resp = service.checkInterface(1L, req);

        assertTrue(resp.allowed());
        assertEquals(1, resp.matchedResources().size());
        assertTrue(resp.matchedResources().stream().anyMatch(CheckInterfaceResp.MatchedResource::allowed));
    }

    @Test
    void shouldDenyNoPermissionWhenResourceExists() {
        CheckInterfaceReq req = new CheckInterfaceReq("USER", "u-1", "admin-service", "POST", "/api/user/list", Map.of());
        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);

        ResourceApiMapping mapping = new ResourceApiMapping();
        mapping.setResourceEntityId(100L);
        mapping.setPathPattern("/api/user/list");
        when(apiMappingMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(mapping));

        ResourceEntity r1 = new ResourceEntity();
        r1.setId(100L); r1.setDeleteFlag(0L); r1.setResourceType(1); r1.setCode("api:user:list:1");
        OperationPermission op = new OperationPermission();
        op.setId(300L); op.setCode("ACCESS"); op.setBinaryBit(1L); op.setInheritMask(0L);

        PermResult mockResult = PermResult.builder(false, "NO_PERMISSION")
            .scopeAllMatched(false)
            .scopeAllEntries(List.of())
            .instanceEntries(List.of())
            .resourceMap(Map.of(100L, r1))
            .operationMap(Map.of(300L, op))
            .build();

        when(engine.query(any(PermQuery.class))).thenReturn(mockResult);

        CheckInterfaceResp resp = service.checkInterface(1L, req);

        assertFalse(resp.allowed());
        assertEquals("NO_PERMISSION", resp.reason());
    }
}
