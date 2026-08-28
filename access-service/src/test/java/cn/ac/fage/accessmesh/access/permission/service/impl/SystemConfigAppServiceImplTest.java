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
            // 回归锁：is_system NOT NULL——API 新建必须显式置 false，否则 insert 违反非空约束（T-PERM-024 PgIT 发现）
            assertEquals(Boolean.FALSE, inserted.getIsSystem());
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

    @Test
    void shouldListSystemConfigsWithNormalizedKeyword() {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
                .thenReturn(true);
            SystemConfig row = new SystemConfig();
            row.setId(1L);
            row.setTenantId(1L);
            row.setConfigKey("admin.key1");
            row.setConfigValue("{}");
            when(systemConfigMapper.selectPageByCondition(eq(1L), eq("cache"), eq(10), eq(0)))
                .thenReturn(java.util.List.of(row));

            java.util.List<SystemConfigResp> result = service.listSystemConfigs(1L, " cache ", 0, 10);

            assertEquals(1, result.size());
            assertEquals("admin.key1", result.get(0).configKey());
            verify(systemConfigMapper).selectPageByCondition(eq(1L), eq("cache"), eq(10), eq(0));
        }
    }

    @Test
    void shouldNormalizeBlankKeywordToNull() {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(anyLong(), anyLong(), any(), any(), any()))
                .thenReturn(true);
            when(systemConfigMapper.selectPageByCondition(eq(1L), isNull(), anyInt(), anyInt()))
                .thenReturn(java.util.List.of());

            service.listSystemConfigs(1L, "  ", 20, 200);

            verify(systemConfigMapper).selectPageByCondition(eq(1L), isNull(), eq(200), eq(20));
        }
    }

    @Test
    void shouldCountSystemConfigsDeniedWithoutViewPermission() {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), any(), eq((String) null), any()))
                .thenReturn(false);

            assertThrows(SecurityException.class, () -> service.countSystemConfigs(1L, null));
            verify(systemConfigMapper, never()).countByCondition(anyLong(), any());
        }
    }
}
