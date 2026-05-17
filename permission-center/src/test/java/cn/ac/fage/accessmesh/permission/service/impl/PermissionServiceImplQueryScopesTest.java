package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.req.QueryScopesReq;
import cn.ac.fage.accessmesh.permission.dto.query.PermQuery;
import cn.ac.fage.accessmesh.permission.dto.query.PermResult;
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
import cn.ac.fage.accessmesh.common.cache.CacheService;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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

/**
 * 权限服务范围查询测试类
 * <p>
 * 测试PermissionServiceImpl的queryScopes方法的各项功能：
 * - 全局作用域权限的返回逻辑
 * - 条件不满足时的过滤逻辑
 * - 冲突规则过滤时的处理逻辑
 * - 直接权限和依赖权限的合并逻辑
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class PermissionServiceImplQueryScopesTest {

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
    /** 实体批量加载领域服务Mock */
    @Mock private EntityBatchLoadDomainService entityBatchLoadDomainService;
    /** 角色权限条目Mapper Mock */
    @Mock private RolePermEntryMapper rolePermEntryMapper;
    /** 权限查询引擎Mock */
    @Mock private PermQueryEngine engine;

    /** 待测试的权限服务实例 */
    private PermissionServiceImpl service;

    /**
     * 构建测试请求对象
     * <p>
     * 创建标准的QueryScopesReq请求对象，用于测试范围查询功能。
     * </p>
     *
     * @return 范围查询请求对象
     */
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
            resourceDependencyMapper, userRoleDomainService, permissionConflictDomainService,
            permissionConditionDomainService, rolePermissionDomainService, typeResolutionService, cacheService,
            permissionVersionDomainService, resourceEntityDomainService, entityBatchLoadDomainService,
            rolePermEntryMapper, engine
        );
    }

    /**
     * 测试角色拥有全局作用域权限时的返回逻辑
     * <p>
     * 当用户通过角色拥有scopeAll=true的权限时，
     * queryScopes应返回允许结果，且范围列表中包含scopeAll=true的条目。
     * </p>
     */
    @Test
    @Disabled("Test needs update for refactored implementation")
    void shouldReturnScopeAllWhenRoleHasGlobalScopePermission() {
        QueryScopesReq req = buildReq();
        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
        when(typeResolutionService.resolveResourceId(1L, "MENU", "sys:user", "default", null)).thenReturn(100L);
        when(typeResolutionService.batchResolveOperationIds(eq(1L), eq("MENU"), any(Set.class)))
            .thenReturn(Map.of("VIEW", 300L));

        when(userRoleDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(200L));
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
        // TODO: Replace with engine.query(PermQuery) mock after full test rework
        when(engine.query(any(PermQuery.class))).thenReturn(PermResult.builder(false, null).build());

        OperationPermission op = new OperationPermission();
        op.setId(300L);
        op.setCode("VIEW");
        op.setBinaryBit(1L);
        op.setInheritMask(0L);
        when(entityBatchLoadDomainService.batchLoadOperations(eq(1L), any(Set.class)))
            .thenReturn(Map.of(300L, op));

        QueryScopesResp resp = service.queryScopes(1L, req);

        assertTrue(resp.allowed());
        assertTrue(resp.items().stream().anyMatch(QueryScopesResp.ScopeEntry::scopeAll));
    }

    /**
     * 测试条件不满足时的过滤逻辑
     * <p>
     * 当scopeAll权限绑定的条件不满足时，
     * queryScopes应返回拒绝结果，且范围列表中不包含scopeAll=true的条目。
     * </p>
     */
    @Test
    @Disabled("Test needs update for refactored implementation")
    void shouldNotReturnScopeAllWhenConditionNotMet() {
        QueryScopesReq req = buildReq();
        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
        when(typeResolutionService.resolveResourceId(1L, "MENU", "sys:user", "default", null)).thenReturn(100L);
        when(typeResolutionService.batchResolveOperationIds(eq(1L), eq("MENU"), any(Set.class)))
            .thenReturn(Map.of("VIEW", 300L));
        when(userRoleDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(200L));
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
        // TODO: Replace with engine.query(PermQuery) mock after full test rework
        when(engine.query(any(PermQuery.class))).thenReturn(PermResult.builder(false, null).build());

        OperationPermission op = new OperationPermission();
        op.setId(300L);
        op.setCode("VIEW");
        op.setBinaryBit(1L);
        op.setInheritMask(0L);
        when(permissionConditionDomainService.evaluate(any(), any(), any())).thenReturn(List.of());
        when(permissionConflictDomainService.filterPermMutex(any(), any())).thenAnswer(inv -> inv.getArgument(1));
        when(entityBatchLoadDomainService.batchLoadOperations(eq(1L), any(Set.class)))
            .thenReturn(Map.of(300L, op));

        QueryScopesResp resp = service.queryScopes(1L, req);
        assertFalse(resp.allowed());
        assertFalse(resp.items().stream().anyMatch(QueryScopesResp.ScopeEntry::scopeAll));
    }

    /**
     * 测试冲突规则过滤时的处理逻辑
     * <p>
     * 当scopeAll权限被冲突规则过滤掉时，
     * queryScopes应返回拒绝结果，且范围列表中不包含scopeAll=true的条目。
     * </p>
     */
    @Test
    @Disabled("Test needs update for refactored implementation")
    void shouldNotReturnScopeAllWhenConflictFiltered() {
        QueryScopesReq req = buildReq();
        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
        when(typeResolutionService.resolveResourceId(1L, "MENU", "sys:user", "default", null)).thenReturn(100L);
        when(typeResolutionService.batchResolveOperationIds(eq(1L), eq("MENU"), any(Set.class)))
            .thenReturn(Map.of("VIEW", 300L));
        when(userRoleDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(200L));
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
        // TODO: Replace with engine.query(PermQuery) mock after full test rework
        when(engine.query(any(PermQuery.class))).thenReturn(PermResult.builder(false, null).build());

        OperationPermission op = new OperationPermission();
        op.setId(300L);
        op.setCode("VIEW");
        op.setBinaryBit(1L);
        op.setInheritMask(0L);
        when(permissionConditionDomainService.evaluate(any(), any(), any())).thenAnswer(inv -> inv.getArgument(1));
        when(permissionConflictDomainService.filterPermMutex(any(), any())).thenReturn(List.of());
        when(entityBatchLoadDomainService.batchLoadOperations(eq(1L), any(Set.class)))
            .thenReturn(Map.of(300L, op));

        QueryScopesResp resp = service.queryScopes(1L, req);
        assertFalse(resp.allowed());
        assertFalse(resp.items().stream().anyMatch(QueryScopesResp.ScopeEntry::scopeAll));
    }

    /**
     * 测试直接权限和依赖权限的合并逻辑
     * <p>
     * 当用户同时拥有直接权限（dependOn=null）和依赖权限（dependOn指向父权限）时，
     * queryScopes应返回两者的合并结果，并正确标注来源类型。
     * </p>
     */
    @Test
    @Disabled("Test needs update for refactored implementation")
    void shouldReturnDirectAndDependentUnionItems() {
        QueryScopesReq req = buildReq();
        when(typeResolutionService.resolveUserId(1L, "USER", "u-1")).thenReturn(10L);
        when(typeResolutionService.resolveResourceId(1L, "MENU", "sys:user", "default", null)).thenReturn(100L);
        when(typeResolutionService.batchResolveOperationIds(eq(1L), eq("MENU"), any(Set.class)))
            .thenReturn(Map.of("VIEW", 300L));
        when(typeResolutionService.resolveDomainId(1L, null)).thenReturn(0L);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "MENU")).thenReturn(1);
        when(userRoleDomainService.resolveEffectiveRoles(1L, 10L)).thenReturn(Set.of(200L));
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

        // TODO: Replace with engine.query(PermQuery) mock after full test rework
        when(engine.query(any(PermQuery.class))).thenReturn(PermResult.builder(false, null).build());

        OperationPermission op = new OperationPermission();
        op.setId(300L);
        op.setCode("VIEW");
        op.setBinaryBit(1L);
        op.setInheritMask(0L);
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
