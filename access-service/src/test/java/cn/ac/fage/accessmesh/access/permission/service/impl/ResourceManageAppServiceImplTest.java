package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionGuard;
import cn.ac.fage.accessmesh.access.permission.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * T-PERM-042（architecture §14.5）：资源树读接口补类型级 RESOURCE:VIEW 门禁。
 * T-PERM-027（§7.5）：API 映射列表补 SERVICE:VIEW 门禁与结果裁剪、响应补资源业务字段。
 */
@ExtendWith(MockitoExtension.class)
class ResourceManageAppServiceImplTest {

    @Mock private ResourceEntityMapper resourceEntityMapper;
    @Mock private ResourceApiMappingMapper apiMappingMapper;
    @Mock private ResourceEntityDomainService resourceEntityDomainService;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private DomainClassifyService domainClassifyService;
    @Mock private PermQueryEngine engine;
    @Mock private RoleResourcePermissionMapper rolePermMapper;

    private ResourceManageAppServiceImpl service;

    @BeforeEach
    void setUp() {
        // 测试简化：投影主体 = 传入 operatorId
        service = new ResourceManageAppServiceImpl(
            resourceEntityMapper,
            apiMappingMapper,
            resourceEntityDomainService,
            typeResolutionService,
            domainClassifyService,
            engine,
            rolePermMapper,
            new LocalProjectionGuard()
        );
    }

    @Test
    @DisplayName("无 RESOURCE:VIEW → SecurityException，不触碰资源查询")
    void shouldRejectResourceTreeWithoutResourceViewPermission() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(false);

            assertThrows(SecurityException.class, () -> service.getResourceTree(1L, null, null));
        }
        verifyNoInteractions(resourceEntityMapper);
        verifyNoInteractions(typeResolutionService);
    }

    @Test
    @DisplayName("有 RESOURCE:VIEW → 正常返回资源树")
    void shouldReturnResourceTreeWhenViewGranted() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(true);
            when(resourceEntityMapper.selectResourceTree(eq(1L), isNull(), eq(false)))
                .thenReturn(List.<ResourceEntity>of());

            assertEquals(List.of(), service.getResourceTree(1L, null, null));
        }
    }

    // ========== T-PERM-027 §7.5：API 映射列表 SERVICE:VIEW 门禁与结果裁剪 ==========

    @Test
    @DisplayName("按 serviceCode 查映射：无该服务实例 VIEW → SecurityException，不触碰查询")
    void shouldRejectMappingListWithoutInstanceViewPermission() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.SERVICE),
                eq("svc-a"), eq(OperationCodeConstants.VIEW))).thenReturn(false);

            assertThrows(SecurityException.class,
                () -> service.listApiMappings(1L, null, "svc-a"));
        }
        verifyNoInteractions(apiMappingMapper);
    }

    @Test
    @DisplayName("管理全量列表：无类型级 SERVICE:VIEW → SecurityException")
    void shouldRejectMappingListWithoutTypeLevelViewPermission() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.SERVICE),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(false);

            assertThrows(SecurityException.class,
                () -> service.listApiMappings(1L, null, null));
        }
        verifyNoInteractions(apiMappingMapper);
    }

    @Test
    @DisplayName("管理全量列表：类型级 VIEW 通过后按服务裁剪——拒绝服务的映射不出现在结果中")
    void shouldTrimDeniedServiceMappingsInUngatedList() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.SERVICE),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(true);
            when(apiMappingMapper.selectValidList(1L, null, null))
                .thenReturn(List.of(mapping(301L, 1001L, "svc-a"), mapping(302L, 1002L, "svc-b")));
            when(engine.getDeniedResourceCodes(eq(1L), eq(100L), eq(ResourceTypeCode.SERVICE),
                eq(Set.of("svc-a", "svc-b")), eq(OperationCodeConstants.VIEW)))
                .thenReturn(Set.of("svc-b"));
            when(resourceEntityMapper.selectValidByIds(eq(1L), any())).thenReturn(List.of(resource(1001L)));

            var result = service.listApiMappings(1L, null, null);

            assertEquals(1, result.size());
            assertEquals("svc-a", result.get(0).serviceCode());
        }
    }

    @Test
    @DisplayName("映射响应补全资源业务字段（§7.3）：resourceCode/resourceName/resourceTypeCode/maintainSource")
    void shouldEnrichMappingRespWithResourceBusinessFields() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.SERVICE),
                eq("svc-a"), eq(OperationCodeConstants.VIEW))).thenReturn(true);
            when(apiMappingMapper.selectValidList(1L, null, "svc-a"))
                .thenReturn(List.of(mapping(301L, 1001L, "svc-a")));
            ResourceEntity resource = resource(1001L);
            when(resourceEntityMapper.selectValidByIds(eq(1L), any())).thenReturn(List.of(resource));
            when(typeResolutionService.resolveTypeCode(1L, "resource_type", 3)).thenReturn("API");

            var result = service.listApiMappings(1L, null, "svc-a");

            assertEquals(1, result.size());
            assertEquals("res:x", result.get(0).resourceCode());
            assertEquals("资源X", result.get(0).resourceName());
            assertEquals("API", result.get(0).resourceTypeCode());
            assertEquals("SERVICE_SYNC", result.get(0).maintainSource());
        }
    }

    @Test
    @DisplayName("资源已软删的映射：业务字段置 null 而非报错")
    void shouldReturnNullResourceFieldsWhenResourceDeleted() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.SERVICE),
                eq("svc-a"), eq(OperationCodeConstants.VIEW))).thenReturn(true);
            when(apiMappingMapper.selectValidList(1L, null, "svc-a"))
                .thenReturn(List.of(mapping(301L, 9999L, "svc-a")));
            when(resourceEntityMapper.selectValidByIds(eq(1L), any())).thenReturn(List.of());

            var result = service.listApiMappings(1L, null, "svc-a");

            assertEquals(1, result.size());
            assertEquals(null, result.get(0).resourceCode());
            assertEquals(null, result.get(0).maintainSource());
        }
    }

    @Test
    @DisplayName("空白 serviceCode 与 null 同义：类型级门禁 + 不过滤查询（门禁与 SQL 语义不分叉）")
    void shouldTreatBlankServiceCodeAsUnfiltered() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.SERVICE),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(true);
            when(apiMappingMapper.selectValidList(eq(1L), isNull(), isNull()))
                .thenReturn(List.of());

            var result = service.listApiMappings(1L, null, " ");

            assertEquals(0, result.size());
            // 规整后 mapper 收到 null 而非字面 " "（旧实现直传 " " 会被 SQL 当过滤条件）
            verify(apiMappingMapper).selectValidList(eq(1L), isNull(), isNull());
        }
    }

    private ResourceApiMapping mapping(Long id, Long resourceEntityId, String serviceCode) {
        ResourceApiMapping mapping = new ResourceApiMapping();
        mapping.setId(id);
        mapping.setTenantId(1L);
        mapping.setResourceEntityId(resourceEntityId);
        mapping.setServiceCode(serviceCode);
        mapping.setHttpMethod("POST");
        mapping.setPathPattern("/api/" + id);
        mapping.setMatchOrder(0);
        mapping.setEnabled(true);
        return mapping;
    }

    private ResourceEntity resource(Long id) {
        ResourceEntity entity = new ResourceEntity();
        entity.setId(id);
        entity.setTenantId(1L);
        entity.setResourceType(3);
        entity.setCode("res:x");
        entity.setName("资源X");
        entity.setMaintainSource("SERVICE_SYNC");
        return entity;
    }
}
