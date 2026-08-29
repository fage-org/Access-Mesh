package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.dto.req.ServiceConfigSyncReq;
import cn.ac.fage.accessmesh.access.permission.entity.ServiceConfig;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.ServiceConfigMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.service.domain.sync.SyncModeStrategyFactory;
import cn.ac.fage.accessmesh.access.permission.service.domain.ResourceSyncHandler;
import cn.ac.fage.accessmesh.access.permission.service.domain.MappingSyncHandler;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceSyncAppServiceImplTest {

    @Mock private ResourceSyncHandler resourceSyncHandler;
    @Mock private MappingSyncHandler mappingSyncHandler;
    @Mock private ServiceConfigMapper serviceConfigMapper;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private SyncModeStrategyFactory strategyFactory;
    @Mock private PermQueryEngine engine;

    private ServiceSyncAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ServiceSyncAppServiceImpl(
            resourceSyncHandler, mappingSyncHandler, serviceConfigMapper,
            typeResolutionService, strategyFactory, engine);
    }

    @Test
    void shouldThrowWhenSyncPermissionDenied() {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L),
                eq(ResourceTypeCode.SERVICE), eq("my-svc"), eq(OperationCodeConstants.SYNC_INTERFACE)))
                .thenReturn(false);

            ServiceConfigSyncReq req = new ServiceConfigSyncReq("my-svc", null, "FULL",
                List.of(new ServiceConfigSyncReq.GroupItem("default", "默认", List.of(
                    new ServiceConfigSyncReq.ApiItem("test", "GET", "/api/test", "READ", "test:read", "test api")
                ))));
            assertThrows(SecurityException.class, () -> service.syncInterfaces(1L, req));
        }
    }

    @Test
    void shouldThrowWhenServiceConfigNotFound() {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L),
                eq(ResourceTypeCode.SERVICE), eq("my-svc"), eq(OperationCodeConstants.SYNC_INTERFACE)))
                .thenReturn(true);
            when(serviceConfigMapper.selectByTenantAndServiceCode(1L, "my-svc")).thenReturn(null);

            ServiceConfigSyncReq req = new ServiceConfigSyncReq("my-svc", null, "FULL",
                List.of(new ServiceConfigSyncReq.GroupItem("default", "默认", List.of(
                    new ServiceConfigSyncReq.ApiItem("test", "GET", "/api/test", "READ", "test:read", "test api")
                ))));
            BizException exception = assertThrows(BizException.class, () -> service.syncInterfaces(1L, req));
            assertEquals(PermissionErrorCode.RESOURCE_NOT_FOUND.getCode(), exception.getErrorCode());
        }
    }

    /**
     * T-PERM-027（§7.4 设计定案）：权威契约 §6.3 首期仅允许 syncMode=FULL。
     * 校验层 @Pattern 拒绝其他值（INCREMENTAL 已随策略删除，防止入口重新放开）。
     */
    @Test
    void shouldRejectNonFullSyncModeAtValidationLayer() {
        jakarta.validation.Validator validator = jakarta.validation.Validation
            .buildDefaultValidatorFactory().getValidator();

        ServiceConfigSyncReq fullReq = new ServiceConfigSyncReq("my-svc", null, "FULL",
            List.of(new ServiceConfigSyncReq.GroupItem("default", "默认", List.of())));
        assertTrue(validator.validate(fullReq).isEmpty(), "FULL 必须通过校验");

        for (String illegal : new String[] {"INCREMENTAL", "PARTIAL", "full", ""}) {
            ServiceConfigSyncReq req = new ServiceConfigSyncReq("my-svc", null, illegal,
                List.of(new ServiceConfigSyncReq.GroupItem("default", "默认", List.of())));
            assertFalse(validator.validate(req).isEmpty(),
                "syncMode=" + illegal + " 必须被校验层拒绝");
        }
    }
}
