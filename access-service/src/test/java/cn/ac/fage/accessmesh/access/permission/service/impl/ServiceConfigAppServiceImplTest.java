package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.dto.req.ServiceConfigReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.ServiceConfigResp;
import cn.ac.fage.accessmesh.access.permission.entity.ServiceConfig;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ServiceConfigMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.SyncTypeGuard;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.common.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceConfigAppServiceImplTest {

    @Mock private ServiceConfigMapper serviceConfigMapper;
    @Mock private PermQueryEngine engine;
    @Mock private ResourceApiMappingMapper resourceApiMappingMapper;

    private ServiceConfigAppServiceImpl service;

    @BeforeEach
    void setUp() {
        // 真实 SyncTypeGuard（validateSyncTypesExtra 不依赖 mapper）：保存边界结构校验真实生效
        service = new ServiceConfigAppServiceImpl(serviceConfigMapper, engine, resourceApiMappingMapper,
                new SyncTypeGuard(serviceConfigMapper, new ObjectMapper()));
    }

    /**
     * 首次创建服务配置场景：serviceCode 尚无对应资源实体，
     * 权限校验使用类型级 MANAGE（resourceCode=null）。
     */
    @Test
    void shouldSaveServiceConfigWhenPermissionGranted() {
        when(engine.hasPermission(eq(1L), eq(100L),
            eq(ResourceTypeCode.SERVICE), eq((Long) null), eq(OperationCodeConstants.MANAGE)))
            .thenReturn(true);
        when(serviceConfigMapper.selectByTenantAndServiceCode(1L, "my-service")).thenReturn(null);

        ServiceConfigReq req = new ServiceConfigReq("my-service", "MyService", "/api", "desc", 1, null);
        ServiceConfigResp result = service.saveServiceConfig(1L, req, 100L);

        ArgumentCaptor<ServiceConfig> captor = ArgumentCaptor.forClass(ServiceConfig.class);
        verify(serviceConfigMapper).insert(captor.capture());
        ServiceConfig inserted = captor.getValue();

        assertNotNull(result);
        assertEquals("my-service", inserted.getServiceCode());
    }

    @Test
    void shouldThrowWhenSaveServiceConfigPermissionDenied() {
        when(engine.hasPermission(eq(1L), eq(100L),
            eq(ResourceTypeCode.SERVICE), eq((Long) null), eq(OperationCodeConstants.MANAGE)))
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
        verify(serviceConfigMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldReject_whenSyncTypeCategoryNotArray() {
        mockManagePermission();

        // 合法 JSON 但错误结构（分类为字符串而非数组）——保存时必须拒绝，避免运行时空白名单
        ServiceConfigReq req = new ServiceConfigReq("my-service", "MyService", "/api", "desc", 1,
            "{\"syncTypes\": {\"subjectTypeCodes\": \"EMP\"}}");

        assertThrows(BizException.class, () -> service.saveServiceConfig(1L, req, 100L));
        verify(serviceConfigMapper, never()).insert(org.mockito.ArgumentMatchers.any());
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
        verify(serviceConfigMapper, never()).insert(org.mockito.ArgumentMatchers.any());
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
        verify(serviceConfigMapper).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldAccept_whenExtraWithoutSyncTypes() {
        mockManagePermission();
        when(serviceConfigMapper.selectByTenantAndServiceCode(1L, "my-service")).thenReturn(null);

        ServiceConfigReq req = new ServiceConfigReq("my-service", "MyService", "/api", "desc", 1,
            "{\"region\": \"CN\"}");

        ServiceConfigResp result = service.saveServiceConfig(1L, req, 100L);

        assertNotNull(result);
        verify(serviceConfigMapper).insert(org.mockito.ArgumentMatchers.any());
    }

    private void mockManagePermission() {
        when(engine.hasPermission(eq(1L), eq(100L),
            eq(ResourceTypeCode.SERVICE), eq((Long) null), eq(OperationCodeConstants.MANAGE)))
            .thenReturn(true);
    }
}
