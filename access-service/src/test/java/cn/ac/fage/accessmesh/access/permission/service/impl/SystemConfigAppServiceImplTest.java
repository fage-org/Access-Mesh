package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.permission.dto.req.SystemConfigReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.SystemConfigResp;
import cn.ac.fage.accessmesh.access.infrastructure.entity.SystemConfig;
import cn.ac.fage.accessmesh.access.infrastructure.mapper.SystemConfigMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemConfigAppServiceImplTest {

    @Mock private SystemConfigMapper systemConfigMapper;
    @Mock private PermQueryEngine engine;

    private SystemConfigAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SystemConfigAppServiceImpl(systemConfigMapper, engine);
    }

    @Test
    void shouldUpsertSystemConfigWhenPermissionGranted() {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermission(eq(1L), eq(100L), any(), eq((Long) null), any()))
                .thenReturn(true);
            when(systemConfigMapper.selectByConfigKey(1L, "key1")).thenReturn(null);

            SystemConfigReq req = new SystemConfigReq("key1", "{}", "desc");
            SystemConfigResp result = service.upsertSystemConfig(1L, req);

            ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
            verify(systemConfigMapper).insert(captor.capture());
            SystemConfig inserted = captor.getValue();

            assertNotNull(result);
            assertEquals("key1", inserted.getConfigKey());
        }
    }

    @Test
    void shouldThrowWhenUpsertSystemConfigPermissionDenied() {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermission(eq(1L), eq(100L), any(), eq((Long) null), any()))
                .thenReturn(false);

            SystemConfigReq req = new SystemConfigReq("key1", "{}", "desc");
            assertThrows(SecurityException.class, () -> service.upsertSystemConfig(1L, req));
        }
    }
}
