package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionGuard;
import cn.ac.fage.accessmesh.access.permission.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * T-PERM-042（architecture §14.5）：资源树读接口补类型级 RESOURCE:VIEW 门禁。
 */
@ExtendWith(MockitoExtension.class)
class ResourceManageAppServiceImplTest {

    @Mock private ResourceEntityMapper resourceEntityMapper;
    @Mock private ResourceApiMappingMapper apiMappingMapper;
    @Mock private ResourceEntityDomainService resourceEntityDomainService;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private DomainClassifyService domainClassifyService;
    @Mock private PermQueryEngine engine;
    @Mock private RoleResourcePermissionMapper rolePermMapper;

    private ResourceManageAppServiceImpl service;

    @BeforeEach
    void setUp() {
        // 测试简化：投影主体 = 传入 operatorId
        service = new ResourceManageAppServiceImpl(
            resourceEntityMapper,
            apiMappingMapper,
            resourceEntityDomainService,
            typeResolutionService,
            domainClassifyService,
            engine,
            rolePermMapper,
            new LocalProjectionGuard()
        );
    }

    @Test
    @DisplayName("无 RESOURCE:VIEW → SecurityException，不触碰资源查询")
    void shouldRejectResourceTreeWithoutResourceViewPermission() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(false);

            assertThrows(SecurityException.class, () -> service.getResourceTree(1L, null, null));
        }
        verifyNoInteractions(resourceEntityMapper);
        verifyNoInteractions(typeResolutionService);
    }

    @Test
    @DisplayName("有 RESOURCE:VIEW → 正常返回资源树")
    void shouldReturnResourceTreeWhenViewGranted() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(true);
            when(resourceEntityMapper.selectResourceTree(eq(1L), isNull(), eq(false)))
                .thenReturn(List.<ResourceEntity>of());

            assertEquals(List.of(), service.getResourceTree(1L, null, null));
        }
    }
}
