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
import cn.ac.fage.accessmesh.common.cache.CacheService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionConditionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionConflictDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.PermissionVersionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.RolePermissionDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.permission.service.domain.impl.RolePermEntryMapper;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.domain.UserRoleDomainService;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot;
import cn.ac.fage.accessmesh.permission.vo.RolePermSnapshot.RolePermEntry;
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

/**
 * 权限服务接口校验测试类
 * <p>
 * 测试PermissionServiceImpl的checkInterface方法的各项功能：
 * - 接口未注册时的拒绝逻辑
 * - 有匹配映射时的权限校验逻辑
 * - 无权限但资源存在时的拒绝逻辑
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class PermissionServiceImplCheckInterfaceTest {

    /** 抽象用户Mapper Mock */
    @Mock private AbstractUserMapper abstractUserMapper;
    /** 资源实体Mapper Mock */
    @Mock private ResourceEntityMapper resourceEntityMapper;
    /** API映射Mapper Mock */
    @Mock private ResourceApiMappingMapper apiMappingMapper;
    /** 操作权限Mapper Mock */
    @Mock private OperationPermissionMapper operationPermissionMapper;
    /** 角色资源权限Mapper Mock */
    @Mock private RoleResourcePermissionMapper rolePermMapper;
    /** 资源依赖Mapper Mock */
    @Mock private ResourceDependencyMapper resourceDependencyMapper;
    /** 用户角色领域服务Mock */
    @Mock private UserRoleDomainService userRoleDomainService;
    /** 权限冲突领域服务Mock */
    @Mock private PermissionConflictDomainService permissionConflictDomainService;
    /** 权限条件领域服务Mock */
    @Mock private PermissionConditionDomainService permissionConditionDomainService;
    /** 角色权限领域服务Mock */
    @Mock private RolePermissionDomainService rolePermissionDomainService;
    /** 类型解析服务Mock */
    @Mock private TypeResolutionService typeResolutionService;
    /** 统一缓存服务Mock */
    @Mock private CacheService cacheService;
    /** 权限版本领域服务Mock */
    @Mock private PermissionVersionDomainService permissionVersionDomainService;
    /** 资源实体领域服务Mock */
    @Mock private ResourceEntityDomainService resourceEntityDomainService;
    /** 角色权限条目Mapper Mock */
    @Mock private RolePermEntryMapper rolePermEntryMapper;
    /** 权限查询引擎Mock */
    @Mock private PermQueryEngine engine;

    /** 待测试的权限服务实例 */
    private PermissionServiceImpl service;

    /**
     * 测试前置初始化
     * <p>
     * 在每个测试方法执行前初始化PermissionServiceImpl实例，
     * 注入所有Mock依赖对象。
     * </p>
     */
    @BeforeEach
    void setUp() {
        service = new PermissionServiceImpl(
            abstractUserMapper, resourceEntityMapper, apiMappingMapper, operationPermissionMapper, rolePermMapper,
            userRoleDomainService, permissionConflictDomainService,
            permissionConditionDomainService, rolePermissionDomainService, typeResolutionService, cacheService,
            permissionVersionDomainService, resourceEntityDomainService,
            engine
        );
    }

    /**
     * 测试接口未注册时的拒绝逻辑
     * <p>
     * 当请求的API接口未在系统中注册映射时，
     * checkInterface应返回拒绝结果，拒绝原因为"API_NOT_REGISTERED"。
     * </p>
     */
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

    /**
     * 测试任意匹配映射通过时的允许逻辑
     * <p>
     * 当存在多个API映射且任一个匹配的映射通过权限校验时，
     * checkInterface应返回允许结果，并包含匹配的资源列表。
     * </p>
     */
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

    /**
     * 测试资源存在但无权限时的拒绝逻辑
     * <p>
     * 当API映射存在且关联的资源实体存在，但用户对该资源无权限时，
     * checkInterface应返回拒绝结果，拒绝原因为"NO_PERMISSION"。
     * </p>
     */
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
}
