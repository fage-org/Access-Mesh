package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.permission.dto.req.AuthCheckReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.CheckInterfaceReq;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermQuery;
import cn.ac.fage.accessmesh.access.permission.dto.query.PermResult;
import cn.ac.fage.accessmesh.access.permission.dto.resp.CheckInterfaceResp;
import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.vo.RolePermEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 权限检查应用服务测试类
 * <p>
 * 测试 PermissionCheckAppServiceImpl 的方法。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class PermissionCheckAppServiceImplTest {

    @Mock private TypeResolutionService typeResolutionService;
    @Mock private PermQueryEngine engine;
    @Mock private ResourceApiMappingMapper apiMappingMapper;

    private PermissionCheckAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PermissionCheckAppServiceImpl(typeResolutionService, engine, apiMappingMapper);
    }

    @Test
    void shouldDenyWhenInterfaceNotRegistered() {
        CheckInterfaceReq req = new CheckInterfaceReq("USER", "u-1", "admin-service", "POST", "/api/user/list", Map.of());
        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
        when(apiMappingMapper.selectForInterfaceCheck(any(), any(), any())).thenReturn(List.of());

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
        when(apiMappingMapper.selectForInterfaceCheck(any(), any(), any())).thenReturn(List.of(m1, m2));

        ResourceEntity r1 = new ResourceEntity();
        r1.setId(100L); r1.setDeleteFlag(0L); r1.setResourceType(1); r1.setCode("api:user:list:1");
        ResourceEntity r2 = new ResourceEntity();
        r2.setId(101L); r2.setDeleteFlag(0L); r2.setResourceType(1); r2.setCode("api:user:list:2");
        OperationPermission op = new OperationPermission();
        op.setId(300L); op.setCode("ACCESS"); op.setBinaryBit(1L); op.setInheritMask(0L);

        RolePermEntry allowEntry = new RolePermEntry(
            401L, 200L, 101L, null, 1, 300L, null, null, "MANUAL", false, null, false, null, null);

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
        when(apiMappingMapper.selectForInterfaceCheck(any(), any(), any())).thenReturn(List.of(mapping));

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

    @Test
    void shouldDenyCheckWhenUserNotFound() {
        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(null);

        var req = new AuthCheckReq("USER", "u-1", "REPORT", "report:1", "VIEW", null, null, null, null);
        var resp = service.check(1L, req);

        assertFalse(resp.allowed());
        assertEquals("USER_NOT_FOUND", resp.reason());
    }

    @Test
    void shouldAllowCheckWhenPermissionGranted() {
        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);

        RolePermEntry entry = new RolePermEntry(
            401L, 20L, 200L, "report:1", 1, 1L, "VIEW", 1L,
            "MANUAL", false, null, false, null, false);
        PermResult r = PermResult.builder(true, null)
            .instanceEntries(List.of(entry)).build();
        when(engine.query(any(PermQuery.class))).thenReturn(r);

        var req = new AuthCheckReq("USER", "u-1", "REPORT", "report:1", "VIEW", null, null, null, null);
        var resp = service.check(1L, req);

        assertTrue(resp.allowed());
    }
}
