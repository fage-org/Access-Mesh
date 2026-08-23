package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.permission.dto.req.SystemConfigReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.SystemConfigResp;
import cn.ac.fage.accessmesh.access.infrastructure.entity.SystemConfig;
import cn.ac.fage.accessmesh.access.infrastructure.mapper.SystemConfigMapper;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import cn.ac.fage.accessmesh.common.exception.BizException;
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
            when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
                .thenReturn(true);
            when(systemConfigMapper.selectByConfigKey(1L, "admin.key1")).thenReturn(null);

            SystemConfigReq req = new SystemConfigReq("admin.key1", "{}", "desc");
            SystemConfigResp result = service.upsertSystemConfig(1L, req);

            ArgumentCaptor<SystemConfig> captor = ArgumentCaptor.forClass(SystemConfig.class);
            verify(systemConfigMapper).insert(captor.capture());
            SystemConfig inserted = captor.getValue();

            assertNotNull(result);
            assertEquals("admin.key1", inserted.getConfigKey());
        }
    }

    @Test
    void shouldThrowWhenUpsertSystemConfigPermissionDenied() {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
                .thenReturn(false);

            SystemConfigReq req = new SystemConfigReq("admin.key1", "{}", "desc");
            assertThrows(SecurityException.class, () -> service.upsertSystemConfig(1L, req));
        }
    }

    // ---- T-ACCESS-007 §5.2 命名空间前缀校验 ----

    @Test
    void shouldRejectConfigKeyWithoutAllowedPrefix() {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
                .thenReturn(true);

            BizException ex = assertThrows(BizException.class,
                () -> service.upsertSystemConfig(1L, new SystemConfigReq("key1", "{}", "desc")));

            assertEquals(PermissionErrorCode.CONFIG_KEY_NAMESPACE_INVALID.getCode(), ex.getErrorCode());
            // 校验失败必须在触达数据访问前 fail-closed
            verify(systemConfigMapper, never()).selectByConfigKey(anyLong(), anyString());
        }
    }

    @Test
    void shouldRejectBlankConfigKey() {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
                .thenReturn(true);

            assertThrows(BizException.class,
                () -> service.upsertSystemConfig(1L, new SystemConfigReq("", "{}", "desc")));
            assertThrows(BizException.class,
                () -> service.upsertSystemConfig(1L, new SystemConfigReq(null, "{}", "desc")));
        }
    }

    @Test
    void shouldRejectPrefixLookalikeKey() {
        // "adminx.*" 不以 "admin." 开头 → 拒绝，防止前缀近似键绕过
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
                .thenReturn(true);

            assertThrows(BizException.class,
                () -> service.upsertSystemConfig(1L, new SystemConfigReq("adminx.foo", "{}", "desc")));
        }
    }

    @Test
    void shouldAcceptAllAllowedPrefixes() {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
                .thenReturn(true);
            when(systemConfigMapper.selectByConfigKey(eq(1L), anyString())).thenReturn(null);

            service.upsertSystemConfig(1L, new SystemConfigReq("admin.foo", "{}", "desc"));
            service.upsertSystemConfig(1L, new SystemConfigReq("permission.bar", "{}", "desc"));
            service.upsertSystemConfig(1L, new SystemConfigReq("access.baz", "{}", "desc"));

            verify(systemConfigMapper, times(3)).insert(any(SystemConfig.class));
        }
    }
}
