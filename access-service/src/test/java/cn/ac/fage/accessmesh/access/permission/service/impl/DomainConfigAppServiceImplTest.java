package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.permission.dto.req.DomainConfigReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.DomainConfigResp;
import cn.ac.fage.accessmesh.access.permission.entity.DomainConfig;
import cn.ac.fage.accessmesh.access.permission.mapper.DomainConfigMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
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
class DomainConfigAppServiceImplTest {

    @Mock private DomainConfigMapper domainConfigMapper;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private PermQueryEngine engine;

    private DomainConfigAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DomainConfigAppServiceImpl(domainConfigMapper, typeResolutionService, engine);
    }

    @Test
    void shouldUpsertDomainConfigWhenPermissionGranted() {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermission(eq(1L), eq(100L), any(), eq((Long) null), any()))
                .thenReturn(true);
            when(typeResolutionService.resolveDomainId(1L, "HR")).thenReturn(10L);
            when(domainConfigMapper.selectValidByTypeString(1L, 10L, "SCOPE")).thenReturn(null);

            DomainConfigReq req = new DomainConfigReq("HR", "SCOPE", "{\"key\":\"val\"}");
            DomainConfigResp result = service.upsertDomainConfig(1L, req);

            ArgumentCaptor<DomainConfig> captor = ArgumentCaptor.forClass(DomainConfig.class);
            verify(domainConfigMapper).insert(captor.capture());
            DomainConfig inserted = captor.getValue();

            assertNotNull(result);
            assertEquals("SCOPE", inserted.getConfigType());
        }
    }

    @Test
    void shouldThrowWhenUpsertDomainConfigPermissionDenied() {
        try (MockedStatic<OperatorContext> ctx = mockStatic(OperatorContext.class)) {
            ctx.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermission(eq(1L), eq(100L), any(), eq((Long) null), any()))
                .thenReturn(false);

            DomainConfigReq req = new DomainConfigReq("HR", "SCOPE", "{}");
            assertThrows(SecurityException.class, () -> service.upsertDomainConfig(1L, req));
        }
    }
}
