package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.permission.dto.req.BizDomainCreateReq;
import cn.ac.fage.accessmesh.access.permission.dto.resp.BizDomainResp;
import cn.ac.fage.accessmesh.access.permission.entity.BizDomain;
import cn.ac.fage.accessmesh.access.permission.mapper.BizDomainMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BizDomainAppServiceImplTest {

    @Mock private BizDomainMapper bizDomainMapper;
    @Mock private PermQueryEngine engine;

    private BizDomainAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BizDomainAppServiceImpl(bizDomainMapper, engine);
    }

    @Test
    void shouldCreateBizDomainWhenPermissionGranted() {
        when(engine.hasPermission(eq(1L), eq(100L), any(), eq((Long) null), any()))
            .thenReturn(true);

        BizDomainCreateReq req = new BizDomainCreateReq("HR", "人力资源", "desc");
        BizDomainResp result = service.createBizDomain(1L, req, 100L);

        ArgumentCaptor<BizDomain> captor = ArgumentCaptor.forClass(BizDomain.class);
        verify(bizDomainMapper).insert(captor.capture());
        BizDomain inserted = captor.getValue();

        assertNotNull(result);
        assertEquals("HR", inserted.getCode());
        assertEquals("人力资源", inserted.getName());
    }

    @Test
    void shouldThrowWhenCreateBizDomainPermissionDenied() {
        when(engine.hasPermission(eq(1L), eq(100L), any(), eq((Long) null), any()))
            .thenReturn(false);

        BizDomainCreateReq req = new BizDomainCreateReq("HR", "人力资源", "desc");
        assertThrows(SecurityException.class, () -> service.createBizDomain(1L, req, 100L));
    }
}
