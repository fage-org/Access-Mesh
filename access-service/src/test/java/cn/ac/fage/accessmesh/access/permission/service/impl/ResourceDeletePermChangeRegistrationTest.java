package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.PermissionChangeContext;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.access.permission.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-PERM-018 (C9)：资源软删路径双重登记回归测试。
 * <p>
 * deleteResources 软删 role_resource_permission 前，应查受影响 roleIds + serviceCodes 双重登记：
 * markRoles（→ evictBatch ROLE_PERM_SNAPSHOT）+ markServiceCodes（→ 广播清 Gateway 本地快照）。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ResourceDeletePermChangeRegistrationTest {

    @Mock private ResourceEntityMapper resourceEntityMapper;
    @Mock private ResourceApiMappingMapper apiMappingMapper;
    @Mock private ResourceEntityDomainService resourceEntityDomainService;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private DomainClassifyService domainClassifyService;
    @Mock private PermQueryEngine engine;
    @Mock private RoleResourcePermissionMapper rolePermMapper;
    @Mock private cn.ac.fage.accessmesh.access.permission.service.domain.ResourceTypeOwnershipGuard resourceTypeOwnershipGuard;
    @Mock private TreeWriteLockSupport treeWriteLockSupport;

    private ResourceManageAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ResourceManageAppServiceImpl(
            resourceEntityMapper, apiMappingMapper, resourceEntityDomainService,
            typeResolutionService, domainClassifyService, engine, rolePermMapper,
            new cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionGuard(),
            resourceTypeOwnershipGuard,
            treeWriteLockSupport);
        // 模拟 @PermissionChange AOP 绑定 context（owner）
        PermissionChangeContext.bindIfAbsent();
    }

    @AfterEach
    void tearDown() {
        PermissionChangeContext.clear();
    }

    @Test
    void shouldMarkRolesAndServiceCodesBeforeSoftDeletePerm() {
        // 资源 10 + 后代 11 待删（业务键 MENU:x/default 定位，T-PERM-028）
        ResourceEntity root = new ResourceEntity();
        root.setId(10L);
        root.setResourceType(1);
        root.setCode("x");
        root.setCodeType("default");
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("MENU")))
            .thenReturn(java.util.Map.of("MENU", 1));
        when(resourceEntityMapper.selectByTypesAndCodesAndCodeTypes(eq(1L), eq(Set.of(1)), anySet(), anySet()))
            .thenReturn(List.of(root));
        when(engine.getDeniedEntityIds(1L, 99L, ResourceTypeCode.RESOURCE, Set.of(10L), OperationCodeConstants.MANAGE))
            .thenReturn(Set.of());
        when(resourceEntityDomainService.batchGetDescendantIds(1L, Set.of(10L)))
            .thenReturn(java.util.Map.of(10L, List.of(11L)));
        // T-PERM-052 级联守卫：删除全集（含后代）批量取实体收集类型值
        ResourceEntity child = new ResourceEntity();
        child.setId(11L);
        child.setResourceType(1);
        child.setCode("child");
        child.setCodeType("default");
        when(resourceEntityDomainService.batchSelectByIdsMap(1L, Set.of(10L, 11L)))
            .thenReturn(java.util.Map.of(10L, root, 11L, child));

        // 受影响 roleIds（软删前查出）
        when(rolePermMapper.selectRoleIdsByResourceIds(eq(1L), anyList()))
            .thenReturn(Set.of(20L, 21L));
        // 受影响 serviceCodes（资源→API mapping→serviceCode，软删前查出）
        ResourceApiMapping mapping1 = new ResourceApiMapping();
        mapping1.setServiceCode("example-service");
        ResourceApiMapping mapping2 = new ResourceApiMapping();
        mapping2.setServiceCode("order-service");
        when(apiMappingMapper.selectByResourceEntityIds(eq(1L), eq(Set.of(10L, 11L))))
            .thenReturn(List.of(mapping1, mapping2));

        // perm id 查询（软删）
        when(rolePermMapper.selectValidPermIdsByResourceIds(eq(1L), anyList()))
            .thenReturn(List.of(501L, 502L));

        service.deleteResources(1L, List.of(new cn.ac.fage.accessmesh.access.permission.dto.req.ResourceKeyReq("MENU", "x", null)), 99L);

        // 双重登记已落入 ThreadLocal accumulator
        PermissionChangeContext.Accumulator acc = PermissionChangeContext.snapshot();
        org.junit.jupiter.api.Assertions.assertEquals(Set.of(20L, 21L), acc.roleIds());
        org.junit.jupiter.api.Assertions.assertEquals(Set.of("example-service", "order-service"), acc.serviceCodes());
        // 软删确实执行
        verify(rolePermMapper).softDeleteBatch(eq(1L), anyList(), any());
    }

    @Test
    void shouldNotMarkWhenNoAffectedRolesOrMappings() {
        ResourceEntity root = new ResourceEntity();
        root.setId(10L);
        root.setResourceType(1);
        root.setCode("x");
        root.setCodeType("default");
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("MENU")))
            .thenReturn(java.util.Map.of("MENU", 1));
        when(resourceEntityMapper.selectByTypesAndCodesAndCodeTypes(eq(1L), eq(Set.of(1)), anySet(), anySet()))
            .thenReturn(List.of(root));
        when(engine.getDeniedEntityIds(1L, 99L, ResourceTypeCode.RESOURCE, Set.of(10L), OperationCodeConstants.MANAGE))
            .thenReturn(Set.of());
        when(resourceEntityDomainService.batchGetDescendantIds(1L, Set.of(10L)))
            .thenReturn(java.util.Map.of(10L, List.of()));
        when(resourceEntityDomainService.batchSelectByIdsMap(1L, Set.of(10L)))
            .thenReturn(java.util.Map.of(10L, root));

        when(rolePermMapper.selectRoleIdsByResourceIds(eq(1L), anyList())).thenReturn(Set.of());
        when(apiMappingMapper.selectByResourceEntityIds(eq(1L), anySet())).thenReturn(List.of());
        when(rolePermMapper.selectValidPermIdsByResourceIds(eq(1L), anyList())).thenReturn(List.of());

        service.deleteResources(1L, List.of(new cn.ac.fage.accessmesh.access.permission.dto.req.ResourceKeyReq("MENU", "x", null)), 99L);

        PermissionChangeContext.Accumulator acc = PermissionChangeContext.snapshot();
        org.junit.jupiter.api.Assertions.assertTrue(acc.roleIds().isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(acc.serviceCodes().isEmpty());
        verify(rolePermMapper, never()).softDeleteBatch(any(), anyList(), any());
    }
}
