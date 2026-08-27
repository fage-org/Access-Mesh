package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.permission.dto.req.DependencyBatchSyncReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceDependencyCreateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceDependencyUpdateReq;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceDependency;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceDependencyMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * autoGrant 预留未实现收口（2026-08-27 设计定案）：自动授权未实现（T-PERM-035 暂缓），
 * 所有写入口必须拒绝 true（20048）、省略时落库 false——
 * 旧实现（null 默认 true、true 静默入库）在本组用例下必然失败，锁住修复。
 */
@ExtendWith(MockitoExtension.class)
class DependencyAppServiceImplTest {

    @Mock private ResourceDependencyMapper dependencyMapper;
    @Mock private ResourceEntityMapper resourceEntityMapper;
    @Mock private OperationPermissionMapper operationPermissionMapper;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private PermQueryEngine engine;

    private DependencyAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DependencyAppServiceImpl(dependencyMapper, resourceEntityMapper,
            operationPermissionMapper, typeResolutionService, engine);
    }

    private ResourceDependencyCreateReq createReq(Boolean autoGrant) {
        return new ResourceDependencyCreateReq("MENU", "menu:sys", null, null,
            "API", "api:hello", null, List.of("ACCESS"), autoGrant, "test dependency");
    }

    @Test
    void shouldRejectAutoGrantTrueOnCreate() {
        when(engine.hasPermissionByCode(eq(1L), anyLong(), eq(ResourceTypeCode.DEPENDENCY),
            isNull(), eq(OperationCodeConstants.CREATE))).thenReturn(true);
        when(typeResolutionService.resolveResourceId(1L, "MENU", "menu:sys", null, null)).thenReturn(11L);
        when(typeResolutionService.resolveResourceId(1L, "API", "api:hello", null, null)).thenReturn(22L);
        when(typeResolutionService.batchResolveOperationIds(eq(1L), eq("API"), anySet())).thenReturn(Map.of());

        BizException ex = assertThrows(BizException.class,
            () -> service.createDependency(1L, createReq(true), 100L));

        assertEquals(PermissionErrorCode.AUTO_GRANT_NOT_SUPPORTED.getCode(), ex.getErrorCode());
        verify(dependencyMapper, never()).insert(any(ResourceDependency.class));
    }

    @Test
    void shouldDefaultAutoGrantToFalseWhenOmittedOnCreate() {
        when(engine.hasPermissionByCode(eq(1L), anyLong(), eq(ResourceTypeCode.DEPENDENCY),
            isNull(), eq(OperationCodeConstants.CREATE))).thenReturn(true);
        when(typeResolutionService.resolveResourceId(1L, "MENU", "menu:sys", null, null)).thenReturn(11L);
        when(typeResolutionService.resolveResourceId(1L, "API", "api:hello", null, null)).thenReturn(22L);
        when(typeResolutionService.batchResolveOperationIds(eq(1L), eq("API"), anySet())).thenReturn(Map.of());
        when(resourceEntityMapper.selectValidByIds(eq(1L), anySet())).thenReturn(List.of());

        service.createDependency(1L, createReq(null), 100L);

        ArgumentCaptor<ResourceDependency> captor = ArgumentCaptor.forClass(ResourceDependency.class);
        verify(dependencyMapper).insert(captor.capture());
        assertEquals(Boolean.FALSE, captor.getValue().getAutoGrant());
    }

    @Test
    void shouldRejectAutoGrantTrueOnUpdate() {
        when(engine.hasPermissionByCode(eq(1L), anyLong(), eq(ResourceTypeCode.DEPENDENCY),
            any(), eq(OperationCodeConstants.UPDATE))).thenReturn(true);
        ResourceDependency existing = new ResourceDependency();
        existing.setId(7L);
        existing.setTenantId(1L);
        existing.setDeleteFlag(0L);
        when(dependencyMapper.selectOneById(7L)).thenReturn(existing);

        ResourceDependencyUpdateReq req = new ResourceDependencyUpdateReq(
            7L, null, null, null, null, Boolean.TRUE, null);

        BizException ex = assertThrows(BizException.class,
            () -> service.updateDependency(1L, req, 100L));

        assertEquals(PermissionErrorCode.AUTO_GRANT_NOT_SUPPORTED.getCode(), ex.getErrorCode());
        verify(dependencyMapper, never()).update(any(ResourceDependency.class));
    }

    @Test
    void shouldRejectAutoGrantTrueOnBatchSyncItem() {
        when(engine.hasPermissionByCode(eq(1L), anyLong(), eq(ResourceTypeCode.DEPENDENCY),
            isNull(), eq(OperationCodeConstants.SYNC))).thenReturn(true);

        DependencyBatchSyncReq req = new DependencyBatchSyncReq("example-service", "SERVICE_SYNC",
            "INCREMENTAL", List.of(new DependencyBatchSyncReq.DependencySyncItem(
                "REPORT", "report:sales", null, null,
                "API", "api:report:sales:query", null, List.of("ACCESS"), Boolean.TRUE, "d")));

        BizException ex = assertThrows(BizException.class,
            () -> service.batchSyncDependencies(1L, req, 100L));

        assertEquals(PermissionErrorCode.AUTO_GRANT_NOT_SUPPORTED.getCode(), ex.getErrorCode());
        verify(dependencyMapper, never()).insert(any(ResourceDependency.class));
        verify(dependencyMapper, never()).insertBatch(anyList());
    }
}
