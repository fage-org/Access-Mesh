package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.dto.resp.OperationPermissionResp;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T-PERM-042（architecture §14.5）：操作列表读接口补类型级 OPERATION:VIEW 门禁；
 * T-PERM-028：业务键定位 + detail 门禁 + bigint 字符串线格式。
 * 全局操作概念已退役（2026-08-30 设计定案）：includeGlobalFallback 合并与
 * resourceTypeCode 缺省全局轨用例随概念一并移除。
 */
@ExtendWith(MockitoExtension.class)
class OperationAppServiceImplTest {

    @Mock private OperationPermissionMapper operationPermissionMapper;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private PermQueryEngine engine;

    private OperationAppServiceImpl service;

    @BeforeEach
    void setUp() {
        // 测试简化：投影主体 = 传入 operatorId
        service = new OperationAppServiceImpl(operationPermissionMapper, typeResolutionService, engine);
    }

    @Test
    @DisplayName("无 OPERATION:VIEW → SecurityException，不查询操作列表")
    void shouldRejectOperationListWithoutOperationViewPermission() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.OPERATION),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(false);

            assertThrows(SecurityException.class,
                () -> service.listOperations(1L, null));
        }
        verifyNoInteractions(operationPermissionMapper);
    }

    @Test
    @DisplayName("有 OPERATION:VIEW → 返回原始定义集合（按类型过滤）")
    void shouldReturnOperationListWhenViewGranted() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.OPERATION),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(true);
            when(operationPermissionMapper.selectByTenantAndResourceType(eq(1L), isNull()))
                .thenReturn(List.<OperationPermission>of());

            assertEquals(List.of(), service.listOperations(1L, null));
            verify(operationPermissionMapper).selectByTenantAndResourceType(eq(1L), isNull());
        }
    }

    // ========== T-PERM-040：类型过滤 fail-closed（未知类型空列表，不回退全量） ==========

    @Test
    @DisplayName("resourceTypeCode 指定但类型不存在 → 空列表，不回退全量（fail-closed）")
    void shouldReturnEmptyListForUnknownResourceType() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.OPERATION),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(true);
            when(typeResolutionService.resolveTypeValue(1L, "resource_type", "TYPO")).thenReturn(null);

            assertEquals(List.of(), service.listOperations(1L, "TYPO"));
            verifyNoInteractions(operationPermissionMapper);
        }
    }

    @Test
    @DisplayName("resourceTypeCode 已知 → 解析为内部值后下发 Mapper 过滤，类型码批量反解零逐项解析")
    void shouldResolveTypeAndDelegateFilterToMapper() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.OPERATION),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(true);
            when(typeResolutionService.resolveTypeValue(1L, "resource_type", "USER")).thenReturn(7);
            when(operationPermissionMapper.selectByTenantAndResourceType(eq(1L), eq(7)))
                .thenReturn(List.of(op(7, "VIEW")));
            when(typeResolutionService.batchResolveTypeCodes(1L, "resource_type", java.util.Set.of(7)))
                .thenReturn(java.util.Map.of(7, "USER"));

            List<OperationPermissionResp> resp = service.listOperations(1L, "USER");

            assertEquals(1, resp.size());
            assertEquals("USER", resp.get(0).resourceTypeCode());
            verify(operationPermissionMapper).selectByTenantAndResourceType(eq(1L), eq(7));
            // N+1 锁定：列表路径类型码走批量反解，禁止逐项 resolveTypeCode（冷缓存单查）
            verify(typeResolutionService).batchResolveTypeCodes(eq(1L), eq("resource_type"), anySet());
            verify(typeResolutionService, never()).resolveTypeCode(any(), any(), any());
        }
    }

    @Test
    @DisplayName("类型定义已软删的孤儿操作行 fail-closed 过滤——不回退逐项解析、不返回 null 类型码")
    void shouldFilterOutOrphanOperationsWhoseTypeDefinitionDeleted() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.OPERATION),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(true);
            // 全量路径：USER(7) 正常 + 类型 9 的孤儿行（批量反解不含 9——类型定义已软删）
            when(operationPermissionMapper.selectByTenantAndResourceType(eq(1L), isNull()))
                .thenReturn(List.of(op(7, "VIEW"), op(9, "ORPHAN_VIEW")));
            when(typeResolutionService.batchResolveTypeCodes(eq(1L), eq("resource_type"), anySet()))
                .thenReturn(java.util.Map.of(7, "USER"));

            List<OperationPermissionResp> resp = service.listOperations(1L, null);

            // 旧实现（缺项回退逐项解析）下：孤儿行走 resolveTypeCode 单查且返回 null 类型码——
            // 逐项解析零调用与单条结果两断言均失败
            assertEquals(1, resp.size());
            assertEquals("USER", resp.get(0).resourceTypeCode());
            verify(typeResolutionService, never()).resolveTypeCode(any(), any(), any());
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

    // ========== T-PERM-028：业务键定位 + detail 门禁 + bigint 字符串线格式 ==========

    @Test
    @DisplayName("detail 无 OPERATION:VIEW → SecurityException，不查询")
    void shouldRejectOperationDetailWithoutOperationViewPermission() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.OPERATION),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(false);

            assertThrows(SecurityException.class,
                () -> service.getOperation(1L, new cn.ac.fage.accessmesh.access.permission.dto.req.OperationKeyReq("ROLE", "VIEW")));
        }
        verifyNoInteractions(operationPermissionMapper);
    }

    @Test
    @DisplayName("detail 按业务键查询专属操作 → selectByResourceTypeAndCode")
    void shouldGetTypedOperationByBusinessKey() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.OPERATION),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(true);
            when(typeResolutionService.resolveTypeValue(1L, "resource_type", "ROLE")).thenReturn(5);
            OperationPermission entity = op(5, "VIEW");
            when(operationPermissionMapper.selectByResourceTypeAndCode(1L, 5, "VIEW")).thenReturn(entity);

            assertEquals("VIEW", service.getOperation(1L,
                new cn.ac.fage.accessmesh.access.permission.dto.req.OperationKeyReq("ROLE", "VIEW")).code());
        }
    }

    @Test
    @DisplayName("detail 业务键查不到 → 20005 OPERATION_NOT_FOUND")
    void shouldThrowOperationNotFoundForMissingBusinessKey() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.OPERATION),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(true);
            when(typeResolutionService.resolveTypeValue(1L, "resource_type", "ROLE")).thenReturn(5);
            when(operationPermissionMapper.selectByResourceTypeAndCode(1L, 5, "VIEW")).thenReturn(null);

            cn.ac.fage.accessmesh.common.exception.BizException ex = assertThrows(
                cn.ac.fage.accessmesh.common.exception.BizException.class,
                () -> service.getOperation(1L, new cn.ac.fage.accessmesh.access.permission.dto.req.OperationKeyReq("ROLE", "VIEW")));
            assertEquals(20005, ex.getErrorCode());
        }
    }

    @Test
    @DisplayName("update 按业务键定位更新（operationId 形态已删除）")
    void shouldUpdateOperationByBusinessKey() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.OPERATION),
            isNull(), eq(OperationCodeConstants.MANAGE))).thenReturn(true);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "ROLE")).thenReturn(5);
        OperationPermission entity = op(5, "MANAGE");
        when(operationPermissionMapper.selectByResourceTypeAndCode(1L, 5, "MANAGE")).thenReturn(entity);

        cn.ac.fage.accessmesh.access.permission.dto.resp.OperationPermissionResp resp = service.updateOperation(1L,
            new cn.ac.fage.accessmesh.access.permission.dto.req.OperationUpdateReq("ROLE", "MANAGE", "管理", 16L, 2L), 100L);

        assertEquals("管理", resp.name());
        assertEquals(16L, resp.binaryBit());
        assertEquals(2L, resp.inheritMask());
        verify(operationPermissionMapper).update(entity);
    }

    @Test
    @DisplayName("remove 按业务键批量解析，未命中键静默跳过")
    void shouldDeleteOperationsByBusinessKeys() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.OPERATION),
            isNull(), eq(OperationCodeConstants.MANAGE))).thenReturn(true);
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", java.util.Set.of("ROLE")))
            .thenReturn(java.util.Map.of("ROLE", 5));
        OperationPermission typed = op(5, "VIEW");
        typed.setId(11L);
        // 跨类型单查返回超集（含未请求的类型/码组合），由 (resourceType, code) 二元组内存过滤
        OperationPermission other = op(6, "VIEW");
        other.setId(33L);
        when(operationPermissionMapper.selectByTenantResourceTypesAndOpCodes(
            1L, java.util.Set.of(5), java.util.Set.of("VIEW", "MISSING")))
            .thenReturn(List.of(typed, other));

        service.deleteOperations(1L, List.of(
            new cn.ac.fage.accessmesh.access.permission.dto.req.OperationKeyReq("ROLE", "VIEW"),
            new cn.ac.fage.accessmesh.access.permission.dto.req.OperationKeyReq("ROLE", "MISSING")), 100L);

        // 删除集合恰为请求键命中的一行；超集行（USER:VIEW 未在请求键内）不得混入
        verify(operationPermissionMapper).softDeleteBatch(eq(1L), org.mockito.ArgumentMatchers.argThat(
            ids -> ids != null && ids.size() == 1 && ids.contains(11L) && !ids.contains(33L)), any());
    }

    @Test
    @DisplayName("remove 多类型稀疏组合：仅删请求的 (type, code) 对，笛卡尔超集行（ROLE:DELETE/USER:VIEW）不误删（复评 P1 回归锁）")
    void shouldNotDeleteCartesianSupersetRowsForSparseMultiTypeKeys() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.OPERATION),
            isNull(), eq(OperationCodeConstants.MANAGE))).thenReturn(true);
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", java.util.Set.of("ROLE", "USER")))
            .thenReturn(java.util.Map.of("ROLE", 5, "USER", 6));
        // 跨类型 SQL 笛卡尔命中四行（ROLE:VIEW 请求、ROLE:DELETE 未请求、USER:VIEW 未请求、USER:DELETE 请求）
        OperationPermission roleView = op(5, "VIEW");
        roleView.setId(11L);
        OperationPermission roleDelete = op(5, "DELETE");
        roleDelete.setId(12L);
        OperationPermission userView = op(6, "VIEW");
        userView.setId(21L);
        OperationPermission userDelete = op(6, "DELETE");
        userDelete.setId(22L);
        when(operationPermissionMapper.selectByTenantResourceTypesAndOpCodes(
            1L, java.util.Set.of(5, 6), java.util.Set.of("VIEW", "DELETE")))
            .thenReturn(List.of(roleView, roleDelete, userView, userDelete));

        // 仅请求 ROLE:VIEW 与 USER:DELETE
        service.deleteOperations(1L, List.of(
            new cn.ac.fage.accessmesh.access.permission.dto.req.OperationKeyReq("ROLE", "VIEW"),
            new cn.ac.fage.accessmesh.access.permission.dto.req.OperationKeyReq("USER", "DELETE")), 100L);

        // 误删即旧实现行为：笛卡尔二元组会把 ROLE:DELETE(12)/USER:VIEW(21) 一并软删
        verify(operationPermissionMapper).softDeleteBatch(eq(1L), org.mockito.ArgumentMatchers.argThat(
            ids -> ids != null && ids.size() == 2
                && ids.containsAll(List.of(11L, 22L))
                && !ids.contains(12L) && !ids.contains(21L)), any());
    }
}
