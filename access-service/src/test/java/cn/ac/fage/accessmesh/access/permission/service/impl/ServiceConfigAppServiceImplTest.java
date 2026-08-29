package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.dto.req.ServiceConfigReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ApiMappingResp;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ServiceConfigResp;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.access.permission.entity.ServiceConfig;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ServiceConfigMapper;
import cn.ac.fage.accessmesh.access.permission.service.ResourceManageAppService;
import cn.ac.fage.accessmesh.access.permission.service.domain.ResourceSyncHandler;
import cn.ac.fage.accessmesh.access.permission.service.domain.SyncTypeGuard;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.common.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceConfigAppServiceImplTest {

    @Mock private ServiceConfigMapper serviceConfigMapper;
    @Mock private PermQueryEngine engine;
    @Mock private ResourceApiMappingMapper resourceApiMappingMapper;
    @Mock private ResourceSyncHandler resourceSyncHandler;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private ResourceManageAppService resourceManageAppService;

    private ServiceConfigAppServiceImpl service;

    @BeforeEach
    void setUp() {
        // 真实 SyncTypeGuard（validateSyncTypesExtra 不依赖 mapper）：保存边界结构校验真实生效
        service = new ServiceConfigAppServiceImpl(serviceConfigMapper, engine, resourceApiMappingMapper,
                new SyncTypeGuard(serviceConfigMapper, new ObjectMapper()),
                resourceSyncHandler, typeResolutionService, resourceManageAppService);
    }

    /**
     * 首次创建服务配置场景：serviceCode 尚无对应资源实体，
     * 权限校验使用类型级 MANAGE（resourceCode=null）。
     */
    @Test
    void shouldSaveServiceConfigWhenPermissionGranted() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L),
            eq(ResourceTypeCode.SERVICE), eq((String) null), eq(OperationCodeConstants.MANAGE)))
            .thenReturn(true);
        when(serviceConfigMapper.selectByTenantAndServiceCode(1L, "my-service")).thenReturn(null);

        ServiceConfigReq req = new ServiceConfigReq("my-service", "MyService", "/api", "desc", 1, null);
        ServiceConfigResp result = service.saveServiceConfig(1L, req, 100L);

        ArgumentCaptor<ServiceConfig> captor = ArgumentCaptor.forClass(ServiceConfig.class);
        verify(serviceConfigMapper).insert(captor.capture());
        ServiceConfig inserted = captor.getValue();

        assertNotNull(result);
        assertEquals("my-service", inserted.getServiceCode());
        // T-PERM-027：Resp 透出 updatedAt（§7.7），与落库实体一致
        assertEquals(inserted.getUpdatedAt(), result.updatedAt());
    }

    @Test
    void shouldThrowWhenSaveServiceConfigPermissionDenied() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L),
            eq(ResourceTypeCode.SERVICE), eq((String) null), eq(OperationCodeConstants.MANAGE)))
            .thenReturn(false);

        ServiceConfigReq req = new ServiceConfigReq("my-service", "MyService", "/api", "desc", 1, null);
        assertThrows(SecurityException.class, () -> service.saveServiceConfig(1L, req, 100L));
    }

    @Test
    void shouldReject_whenSyncTypesNotObject() {
        mockManagePermission();

        // 结构校验在查询/写入前抛错（不 stub select——校验先于 DB 访问）
        ServiceConfigReq req = new ServiceConfigReq("my-service", "MyService", "/api", "desc", 1,
            "{\"syncTypes\": \"EMP\"}");

        BizException ex = assertThrows(BizException.class, () -> service.saveServiceConfig(1L, req, 100L));
        assertEquals(20044, ex.getErrorCode());
        verify(serviceConfigMapper, never()).insert(any());
    }

    @Test
    void shouldReject_whenSyncTypeCategoryNotArray() {
        mockManagePermission();

        // 合法 JSON 但错误结构（分类为字符串而非数组）——保存时必须拒绝，避免运行时空白名单
        ServiceConfigReq req = new ServiceConfigReq("my-service", "MyService", "/api", "desc", 1,
            "{\"syncTypes\": {\"subjectTypeCodes\": \"EMP\"}}");

        assertThrows(BizException.class, () -> service.saveServiceConfig(1L, req, 100L));
        verify(serviceConfigMapper, never()).insert(any());
    }

    @Test
    void shouldReject_whenSyncTypeEntryBlankOrNonString() {
        mockManagePermission();

        assertThrows(BizException.class, () -> service.saveServiceConfig(1L,
            new ServiceConfigReq("my-service", "MyService", "/api", "desc", 1,
                "{\"syncTypes\": {\"subjectTypeCodes\": [\"  \"]}}"), 100L));
        assertThrows(BizException.class, () -> service.saveServiceConfig(1L,
            new ServiceConfigReq("my-service", "MyService", "/api", "desc", 1,
                "{\"syncTypes\": {\"roleTypeCodes\": [123]}}"), 100L));
        verify(serviceConfigMapper, never()).insert(any());
    }

    @Test
    void shouldReject_whenSyncTypeUnknownField() {
        mockManagePermission();

        // 拼写错误字段（subjectTypesCode）与多余字段必须拒绝，防止错误结构保存后解释为空白名单
        assertThrows(BizException.class, () -> service.saveServiceConfig(1L,
            new ServiceConfigReq("my-service", "MyService", "/api", "desc", 1,
                "{\"syncTypes\": {\"subjectTypesCode\": [\"EMP\"]}}"), 100L));
        assertThrows(BizException.class, () -> service.saveServiceConfig(1L,
            new ServiceConfigReq("my-service", "MyService", "/api", "desc", 1,
                "{\"syncTypes\": {\"subjectTypeCodes\": [\"EMP\"], \"extra\": \"x\"}}"), 100L));
        verify(serviceConfigMapper, never()).insert(any());
    }

    @Test
    void shouldAccept_whenSyncTypesWellFormed() {
        mockManagePermission();
        when(serviceConfigMapper.selectByTenantAndServiceCode(1L, "my-service")).thenReturn(null);

        ServiceConfigReq req = new ServiceConfigReq("my-service", "MyService", "/api", "desc", 1,
            "{\"syncTypes\": {\"subjectTypeCodes\": [\"EMP\"], \"roleTypeCodes\": [\"TEAM_ROLE\"],"
                + " \"resourceTypeCodes\": [\"HR_ORG\"], \"sourceTypes\": [\"HR_MEMBER\"]}}");

        ServiceConfigResp result = service.saveServiceConfig(1L, req, 100L);

        assertNotNull(result);
        verify(serviceConfigMapper).insert(any());
    }

    @Test
    void shouldAccept_whenExtraWithoutSyncTypes() {
        mockManagePermission();
        when(serviceConfigMapper.selectByTenantAndServiceCode(1L, "my-service")).thenReturn(null);

        ServiceConfigReq req = new ServiceConfigReq("my-service", "MyService", "/api", "desc", 1,
            "{\"region\": \"CN\"}");

        ServiceConfigResp result = service.saveServiceConfig(1L, req, 100L);

        assertNotNull(result);
        verify(serviceConfigMapper).insert(any());
    }

    // ========== T-PERM-027：删除级联清理（§7.2 设计定案） ==========

    @Test
    @DisplayName("删除服务：同事务级联软删全部映射（含 MANUAL）+ SERVICE_SYNC 孤立资源清理")
    void shouldCascadeDeleteMappingsAndSyncedResourcesOnRemove() {
        mockManagePermission();

        ServiceConfig config = new ServiceConfig();
        config.setId(10L);
        config.setTenantId(1L);
        config.setServiceCode("svc-a");
        when(serviceConfigMapper.selectValidByIds(eq(1L), any())).thenReturn(List.of(config));

        ResourceApiMapping syncMapping = mapping(301L, "svc-a");
        ResourceApiMapping manualMapping = mapping(302L, "svc-a");
        when(resourceApiMappingMapper.selectValidByServiceCodes(1L, Set.of("svc-a")))
            .thenReturn(List.of(syncMapping, manualMapping));

        when(typeResolutionService.resolveTypeValue(1L, "resource_type", ResourceTypeCode.API)).thenReturn(3);
        when(resourceSyncHandler.cleanupServiceOwnedResources(1L, Set.of("svc-a"), 3)).thenReturn(1);

        service.deleteServiceConfigsByIds(1L, List.of(10L), 100L);

        // 服务行软删
        verify(serviceConfigMapper).softDeleteBatch(eq(1L), eq(List.of(10L)), any(LocalDateTime.class));
        // 全部映射软删（含 MANUAL，一次批量，非逐条）
        verify(resourceApiMappingMapper).softDeleteBatch(eq(1L), eq(List.of(301L, 302L)), any(LocalDateTime.class));
        // SERVICE_SYNC 孤立资源清理走领域处理器（FULL diff 同边界）
        verify(resourceSyncHandler).cleanupServiceOwnedResources(1L, Set.of("svc-a"), 3);
    }

    @Test
    @DisplayName("删除服务：API 类型值缺失时跳过资源清理（不抛错，映射级联仍执行）")
    void shouldSkipResourceCleanupWhenApiTypeMissing() {
        mockManagePermission();

        ServiceConfig config = new ServiceConfig();
        config.setId(11L);
        config.setTenantId(1L);
        config.setServiceCode("svc-b");
        when(serviceConfigMapper.selectValidByIds(eq(1L), any())).thenReturn(List.of(config));
        when(resourceApiMappingMapper.selectValidByServiceCodes(1L, Set.of("svc-b"))).thenReturn(List.of());
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", ResourceTypeCode.API)).thenReturn(null);

        assertDoesNotThrow(() -> service.deleteServiceConfigsByIds(1L, List.of(11L), 100L));

        verify(resourceApiMappingMapper, never()).softDeleteBatch(anyLong(), any(), any());
        verify(resourceSyncHandler, never()).cleanupServiceOwnedResources(anyLong(), any(), any());
    }

    @Test
    @DisplayName("listServiceApis 委托 ResourceManageAppService.listApiMappings（同层复用，门禁/补全单点）")
    void shouldDelegateListServiceApisToMappingList() {
        ApiMappingResp resp = new ApiMappingResp(1L, 1L, 100L, "svc-a", "POST", "/api/x",
            0, true, null, null, null, "res:x", "资源X", "API", "SERVICE_SYNC");
        when(resourceManageAppService.listApiMappings(1L, null, "svc-a")).thenReturn(List.of(resp));

        List<ApiMappingResp> result = service.listServiceApis(1L, "svc-a");

        assertEquals(1, result.size());
        assertEquals("res:x", result.get(0).resourceCode());
        assertEquals("SERVICE_SYNC", result.get(0).maintainSource());
    }

    private ResourceApiMapping mapping(Long id, String serviceCode) {
        ResourceApiMapping mapping = new ResourceApiMapping();
        mapping.setId(id);
        mapping.setTenantId(1L);
        mapping.setResourceEntityId(1000L + id);
        mapping.setServiceCode(serviceCode);
        mapping.setHttpMethod("POST");
        mapping.setPathPattern("/api/" + id);
        mapping.setMatchOrder(0);
        mapping.setEnabled(true);
        return mapping;
    }

    private void mockManagePermission() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L),
            eq(ResourceTypeCode.SERVICE), eq((String) null), eq(OperationCodeConstants.MANAGE)))
            .thenReturn(true);
    }
}
