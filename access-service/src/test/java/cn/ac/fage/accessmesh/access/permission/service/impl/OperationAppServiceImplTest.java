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
    @Mock private cn.ac.fage.accessmesh.common.cache.CacheService cacheService;
    @Mock private cn.ac.fage.accessmesh.access.permission.mapper.TypeDefinitionMapper typeDefinitionMapper;
    @Mock private cn.ac.fage.accessmesh.access.permission.service.domain.GrantOriginDomainService grantOriginDomainService;
    @Mock private cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport treeWriteLockSupport;

    private OperationAppServiceImpl service;

    @BeforeEach
    void setUp() {
        // 测试简化：投影主体 = 传入 operatorId
        service = new OperationAppServiceImpl(operationPermissionMapper, typeResolutionService, engine,
            cacheService, typeDefinitionMapper, grantOriginDomainService, treeWriteLockSupport);
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
    @DisplayName("update 按业务键定位更新（operationId 形态已删除）+ 提交后失效该类型操作缓存（T-PERM-047）")
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
        // T-PERM-047 回归锁：旧实现（写路径无失效，靠 L1 60m/L2 120m TTL 兜底）下 verify 失败——
        // 位值变更后引擎最长 1-2 小时按旧位值判定
        verify(cacheService).evictAfterCommit(
            eq(cn.ac.fage.accessmesh.access.permission.cache.PermCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE),
            eq(1L), eq("op_perm:5"));
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
        // T-PERM-047 回归锁：批量软删后按受影响类型集合批量失效（同类型去重）
        verify(cacheService).evictBatchAfterCommit(
            eq(cn.ac.fage.accessmesh.access.permission.cache.PermCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE),
            eq(1L), eq(java.util.Set.of("op_perm:5", "op_perm:6")));
    }

    // ========== T-PERM-047：OPERATION_PERMISSIONS_BY_TYPE 写路径失效接线 ==========

    @Test
    @DisplayName("create 落库后提交失效该类型操作缓存（T-PERM-047）")
    void shouldEvictOperationCacheAfterCreate() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.OPERATION),
            isNull(), eq(OperationCodeConstants.CREATE))).thenReturn(true);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "USER")).thenReturn(7);

        service.createOperation(1L, "USER", "EXPORT", "导出", 64L, 0L, 100L);

        // 旧实现（无失效接线）下 verify 失败：新增操作在 TTL 窗口内不参与覆盖判定
        verify(operationPermissionMapper).insert(any(OperationPermission.class));
        verify(cacheService).evictAfterCommit(
            eq(cn.ac.fage.accessmesh.access.permission.cache.PermCacheCatalog.OPERATION_PERMISSIONS_BY_TYPE),
            eq(1L), eq("op_perm:7"));
    }

    // ========== T-PERM-062：自定义类型追加操作同事务补种授权根 ==========

    @Test
    @DisplayName("向自定义 resource_type 追加操作 → 同事务向所有者补种该操作位首授行")
    void shouldSeedAuthorityRootWhenAppendingOperationToCustomType() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.OPERATION),
            isNull(), eq(OperationCodeConstants.CREATE))).thenReturn(true);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "ORDER")).thenReturn(12);
        when(typeDefinitionMapper.selectByTypeKeyAndCode(1L, "resource_type", "ORDER"))
            .thenReturn(customType(12, false));
        when(grantOriginDomainService.resolveOwnerRoleId(eq(1L), any())).thenReturn(55L);

        service.createOperation(1L, "ORDER", "EXPORT", "导出订单", 16L, 0L, 100L);

        // 不钩追加操作则死锁转移到第五个操作（EXPORT 位 16 先例）；种子单操作位与操作行同事务
        verify(grantOriginDomainService).seedAuthorityRootGrants(eq(1L), eq(55L), eq(12),
            eq(List.of(16L)), eq(100L));
        // 评审批次 P2-1：操作创建与类型生命周期写路径共持 RESOURCE_ENTITY 树写锁
        //（锁内重读类型行——无锁时与所有者变更/类型删除交错产生不可回收种子行/漏级联）
        verify(treeWriteLockSupport).lockTreeWrites(1L,
            cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
    }

    @Test
    @DisplayName("向系统预置类型追加操作 → 不补种（内置类型转授链收窄不动，T-PERM-027 口径）")
    void shouldSkipSeedWhenAppendingOperationToSystemType() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.OPERATION),
            isNull(), eq(OperationCodeConstants.CREATE))).thenReturn(true);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "SERVICE")).thenReturn(4);
        when(typeDefinitionMapper.selectByTypeKeyAndCode(1L, "resource_type", "SERVICE"))
            .thenReturn(customType(4, true));

        service.createOperation(1L, "SERVICE", "AUDIT", "审计", 64L, 0L, 100L);

        verify(grantOriginDomainService, never()).seedAuthorityRootGrants(any(), any(), any(), any(), any());
        verify(operationPermissionMapper).insert(any(OperationPermission.class));
    }

    @Test
    @DisplayName("所有者角色解析失败 → 整单回滚（追加操作与种子同为单事务）")
    void shouldRollbackWhenOwnerUnresolvableOnOperationCreate() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.OPERATION),
            isNull(), eq(OperationCodeConstants.CREATE))).thenReturn(true);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "ORDER")).thenReturn(12);
        when(typeDefinitionMapper.selectByTypeKeyAndCode(1L, "resource_type", "ORDER"))
            .thenReturn(customType(12, false));
        when(grantOriginDomainService.resolveOwnerRoleId(eq(1L), any()))
            .thenThrow(new cn.ac.fage.accessmesh.common.exception.BizException(
                cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode.ROLE_NOT_FOUND.getCode(),
                "类型授权根角色不存在"));

        assertThrows(cn.ac.fage.accessmesh.common.exception.BizException.class,
            () -> service.createOperation(1L, "ORDER", "EXPORT", "导出订单", 16L, 0L, 100L));
    }

    private cn.ac.fage.accessmesh.access.permission.entity.TypeDefinition customType(int typeValue, boolean isSystem) {
        cn.ac.fage.accessmesh.access.permission.entity.TypeDefinition type =
            new cn.ac.fage.accessmesh.access.permission.entity.TypeDefinition();
        type.setTenantId(1L);
        type.setTypeKey("resource_type");
        type.setTypeValue(typeValue);
        type.setIsSystem(isSystem);
        return type;
    }

    @Test
    @DisplayName("remove 空键早退不失效（无数据变更，T-PERM-047）")
    void shouldSkipEvictionWhenDeleteHitsNothing() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.OPERATION),
            isNull(), eq(OperationCodeConstants.MANAGE))).thenReturn(true);

        service.deleteOperations(1L, List.of(), 100L);

        verify(cacheService, never()).evictBatchAfterCommit(any(), any(), anySet());
        verify(cacheService, never()).evictAfterCommit(any(), any(), any());
    }

    @Test
    @DisplayName("remove 键非空但零命中（类型解析空）早退不失效（T-PERM-047）")
    void shouldSkipEvictionWhenDeleteResolvesNoEntities() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.OPERATION),
            isNull(), eq(OperationCodeConstants.MANAGE))).thenReturn(true);
        when(typeResolutionService.batchResolveTypeValues(eq(1L), eq("resource_type"), eq(java.util.Set.of("ROLE"))))
            .thenReturn(java.util.Map.of());

        service.deleteOperations(1L, List.of(
            new cn.ac.fage.accessmesh.access.permission.dto.req.OperationKeyReq("ROLE", "VIEW")), 100L);

        verify(operationPermissionMapper, never()).softDeleteBatch(any(), any(), any());
        verify(cacheService, never()).evictBatchAfterCommit(any(), any(), anySet());
        verify(cacheService, never()).evictAfterCommit(any(), any(), any());
    }
}
