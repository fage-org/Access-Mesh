package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.permission.dto.req.ServiceConfigReq;
import cn.ac.fage.accessmesh.permission.dto.resp.ServiceConfigResp;
import cn.ac.fage.accessmesh.permission.entity.ServiceConfig;
import cn.ac.fage.accessmesh.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.permission.mapper.ServiceConfigMapper;
import cn.ac.fage.accessmesh.permission.service.domain.impl.PermQueryEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
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
        service = new ServiceConfigAppServiceImpl(serviceConfigMapper, engine, resourceApiMappingMapper);
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
}
