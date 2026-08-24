package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.entity.OperationPermission;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.OperationPermissionMapper;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-PERM-042（architecture §14.5）：操作列表读接口补类型级 OPERATION:VIEW 门禁；
 * T-ACCESS-021 补齐 api-contract §5.3「专属优先、全局回退」合并（includeGlobalFallback）。
 */
@ExtendWith(MockitoExtension.class)
class OperationAppServiceImplTest {

    @Mock private OperationPermissionMapper operationPermissionMapper;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private PermQueryEngine engine;

    private OperationAppServiceImpl service;

    @BeforeEach
    void setUp() {
        // 测试简化：投影主体 = 传入 operatorId；共享解析器为纯内存实现，直接用真实例
        service = new OperationAppServiceImpl(operationPermissionMapper, typeResolutionService, engine,
            new cn.ac.fage.accessmesh.access.permission.service.domain.impl.OperationResolutionDomainServiceImpl());
    }

    @Test
    @DisplayName("无 OPERATION:VIEW → SecurityException，不查询操作列表")
    void shouldRejectOperationListWithoutOperationViewPermission() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.OPERATION),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(false);

            assertThrows(SecurityException.class,
                () -> service.listOperations(1L, null, null, null));
        }
        verifyNoInteractions(operationPermissionMapper);
    }

    @Test
    @DisplayName("有 OPERATION:VIEW 且 includeGlobalFallback 缺省 → 维持现状（原始定义集合）")
    void shouldReturnOperationListWhenViewGranted() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.OPERATION),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(true);
            when(operationPermissionMapper.selectByTenantAndResourceType(eq(1L), isNull()))
                .thenReturn(List.<OperationPermission>of());

            assertEquals(List.of(), service.listOperations(1L, null, null, null));
            verify(operationPermissionMapper).selectByTenantAndResourceType(eq(1L), isNull());
        }
    }

    private static OperationPermission op(Integer resourceType, String code) {
        OperationPermission o = new OperationPermission();
        o.setTenantId(1L);
        o.setResourceType(resourceType);
        o.setCode(code);
        o.setName(code);
        o.setBinaryBit(1L);
        o.setInheritMask(0L);
        return o;
    }

    @Test
    @DisplayName("includeGlobalFallback=true + 指定类型 → 专属优先合并：同码全局被剔除、无码冲突的全局保留")
    void shouldMergeDedicatedAndGlobalWithDedicatedPriority() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.OPERATION),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(true);
            when(typeResolutionService.resolveTypeValue(eq(1L), eq("resource_type"), eq("ROLE")))
                .thenReturn(5);
            // 合并路径单查全量（含其它类型专属与全局），由共享解析器内存合并：
            // ROLE 专属 VIEW/MANAGE；全局 VIEW（同码被剔除）+ SYNC（保留）；USER 专属不入结果
            when(operationPermissionMapper.selectByTenantAndResourceType(eq(1L), isNull()))
                .thenReturn(List.of(op(5, "VIEW"), op(5, "MANAGE"),
                    op(null, "VIEW"), op(null, "SYNC"), op(6, "CREATE")));

            List<String> codes = service.listOperations(1L, "ROLE", null, true)
                .stream().map(r -> r.code()).toList();
            assertEquals(List.of("VIEW", "MANAGE", "SYNC"), codes);
        }
    }

    @Test
    @DisplayName("includeGlobalFallback=true + resourceTypeCode 缺省 → 仅返回全局操作集合")
    void shouldReturnOnlyGlobalOperationsWhenTypeMissing() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.OPERATION),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(true);
            when(operationPermissionMapper.selectByTenantAndResourceType(eq(1L), isNull()))
                .thenReturn(List.of(op(5, "VIEW"), op(null, "VIEW"), op(null, "SYNC")));

            List<String> codes = service.listOperations(1L, null, null, true)
                .stream().map(r -> r.code()).toList();
            assertEquals(List.of("VIEW", "SYNC"), codes);
        }
    }
}
