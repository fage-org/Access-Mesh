package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.RoleResourcePermissionMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.access.permission.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.ResourceTypeOwnershipGuard;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * T-PERM-042（architecture §14.5）：资源树读接口补类型级 RESOURCE:VIEW 门禁。
 * T-PERM-027（§7.5）：API 映射列表补 SERVICE:VIEW 门禁与结果裁剪、响应补资源业务字段。
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
    @Mock private ResourceTypeOwnershipGuard resourceTypeOwnershipGuard;
    @Mock private TreeWriteLockSupport treeWriteLockSupport;

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
            resourceTypeOwnershipGuard,
            treeWriteLockSupport
        );
    }

    @Test
    @DisplayName("树写锁无条件先于业务校验：updateResource/moveResource 异常路径同样验证入口已接锁")
    void treeWriteLockTakenBeforeResourceWriteValidation() {
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "MENU")).thenReturn(0);
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(1L, 0, "m1", "default")).thenReturn(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.updateResource(1L,
                new cn.ac.fage.accessmesh.access.permission.dto.req.ResourceUpdateReq(
                    "MENU", "m1", "default", null, null, null, null, null, null), 9L))
            .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class);
        org.mockito.Mockito.verify(treeWriteLockSupport).lockTreeWrites(1L,
            cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);

        org.mockito.Mockito.clearInvocations(treeWriteLockSupport);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.moveResource(1L,
                new cn.ac.fage.accessmesh.access.permission.dto.req.ResourceMoveReq(
                    new cn.ac.fage.accessmesh.access.permission.dto.req.ResourceKeyReq("MENU", "m1", "default"),
                    null), 9L))
            .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class);
        org.mockito.Mockito.verify(treeWriteLockSupport).lockTreeWrites(1L,
            cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
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

    // ========== T-PERM-028：业务键定位 + 读门禁补齐 + extraClear + move 校验 ==========

    private static cn.ac.fage.accessmesh.access.permission.dto.req.ResourceKeyReq key(String code) {
        return new cn.ac.fage.accessmesh.access.permission.dto.req.ResourceKeyReq("MENU", code, null);
    }

    @Test
    @DisplayName("create 落库前归一 codeType：空白→default、去首尾空白（键路径 trim 对称，双轨评审 P2 回归锁）")
    void shouldNormalizeCodeTypeOnCreate() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
            isNull(), eq(OperationCodeConstants.CREATE))).thenReturn(true);
        // API 为非保留类型（USER/ORG/MENU/ROLE 保留给管理事实链路，create 被 guard 拒绝）；
        // codex 三轮复评 P1-2：create 消费门禁返回的权威类型行（不再经 TYPE_VALUE 类型缓存）
        when(resourceTypeOwnershipGuard.rejectIfSyncManagedType(1L, "API")).thenReturn(apiType());

        // 带空白 codeType：落库前 trim，否则该行无法经业务键（归一 trim）寻址
        service.createResource(1L, new cn.ac.fage.accessmesh.access.permission.dto.req.ResourceCreateReq(
            null, null, null, null, null, "API", "res-a", " X ", "资源A", null, null, null, null), 100L);
        org.mockito.ArgumentCaptor<ResourceEntity> captor1 = org.mockito.ArgumentCaptor.forClass(ResourceEntity.class);
        verify(resourceEntityMapper, org.mockito.Mockito.times(1)).insert(captor1.capture());
        assertEquals("X", captor1.getValue().getCodeType());

        // null codeType：落库 default
        service.createResource(1L, new cn.ac.fage.accessmesh.access.permission.dto.req.ResourceCreateReq(
            null, null, null, null, null, "API", "res-b", null, "资源B", null, null, null, null), 100L);
        org.mockito.ArgumentCaptor<ResourceEntity> captor2 = org.mockito.ArgumentCaptor.forClass(ResourceEntity.class);
        verify(resourceEntityMapper, org.mockito.Mockito.times(2)).insert(captor2.capture());
        assertEquals("default", captor2.getAllValues().get(1).getCodeType());
    }

    @Test
    @DisplayName("detail 无 RESOURCE:VIEW → SecurityException，不触碰查询")
    void shouldRejectResourceDetailWithoutResourceViewPermission() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(false);

            assertThrows(SecurityException.class, () -> service.getResource(1L, key("x")));
        }
        verifyNoInteractions(resourceEntityMapper);
        verifyNoInteractions(typeResolutionService);
    }

    @Test
    @DisplayName("detail 按业务键查询（codeType 缺省归一 default），查不到 → 20004")
    void shouldGetResourceByBusinessKeyAndThrowWhenMissing() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(true);
            when(typeResolutionService.resolveTypeValue(1L, "resource_type", "MENU")).thenReturn(1);
            when(resourceEntityMapper.selectByTypeCodeAndCodeType(1L, 1, "x", "default"))
                .thenReturn(resourceWithKey(10L));

            assertEquals("res:x", service.getResource(1L, key("x")).code());

            when(resourceEntityMapper.selectByTypeCodeAndCodeType(1L, 1, "y", "default")).thenReturn(null);
            cn.ac.fage.accessmesh.common.exception.BizException ex = assertThrows(
                cn.ac.fage.accessmesh.common.exception.BizException.class, () -> service.getResource(1L, key("y")));
            assertEquals(20004, ex.getErrorCode());
        }
    }

    @Test
    @DisplayName("list 无 RESOURCE:VIEW → SecurityException（分页列表与树同口径门禁）")
    void shouldRejectResourceListWithoutResourceViewPermission() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(false);

            assertThrows(SecurityException.class, () -> service.listResources(1L, null, null, 0, 10));
            assertThrows(SecurityException.class, () -> service.countResources(1L, null, null));
        }
        verifyNoInteractions(resourceEntityMapper);
    }

    @Test
    @DisplayName("update 按业务键定位 + extraClear=true 清空 extra（null 与「未传」区分）")
    void shouldClearExtraWhenExtraClearTrue() {
        ResourceEntity entity = resourceWithKey(10L);
        entity.setExtra("{\"k\":1}");
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "MENU")).thenReturn(1);
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(1L, 1, "x", "default")).thenReturn(entity);
        when(engine.hasPermissionByEntityId(1L, 100L, ResourceTypeCode.RESOURCE, 10L, OperationCodeConstants.MANAGE))
            .thenReturn(true);

        var resp = service.updateResource(1L,
            new cn.ac.fage.accessmesh.access.permission.dto.req.ResourceUpdateReq(
                "MENU", "x", null, null, null, null, null, null, Boolean.TRUE), 100L);

        assertEquals(null, resp.extra());
        // 置 null 走 UpdateEntity 显式更新列（BaseMapper.update 忽略 null 字段）
        verify(resourceEntityMapper).update(any(ResourceEntity.class));
    }

    @Test
    @DisplayName("update extra=null 且未传 extraClear → 不更新 extra（维持原值）")
    void shouldKeepExtraWhenNullWithoutClearFlag() {
        ResourceEntity entity = resourceWithKey(10L);
        entity.setExtra("{\"k\":1}");
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "MENU")).thenReturn(1);
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(1L, 1, "x", "default")).thenReturn(entity);
        when(engine.hasPermissionByEntityId(1L, 100L, ResourceTypeCode.RESOURCE, 10L, OperationCodeConstants.MANAGE))
            .thenReturn(true);

        var resp = service.updateResource(1L,
            new cn.ac.fage.accessmesh.access.permission.dto.req.ResourceUpdateReq(
                "MENU", "x", null, "新名称", null, null, null, null, null), 100L);

        assertEquals("{\"k\":1}", resp.extra());
        assertEquals("新名称", resp.name());
    }

    @Test
    @DisplayName("move 跨资源类型 → 20053 拒绝")
    void shouldRejectCrossTypeMove() {
        ResourceEntity entity = resourceWithKey(10L);
        ResourceEntity parent = resourceWithKey(20L);
        parent.setResourceType(2);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "MENU")).thenReturn(1);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "BUTTON")).thenReturn(2);
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(1L, 1, "x", "default")).thenReturn(entity);
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(1L, 2, "y", "default")).thenReturn(parent);
        when(engine.hasPermissionByEntityId(1L, 100L, ResourceTypeCode.RESOURCE, 10L, OperationCodeConstants.MANAGE))
            .thenReturn(true);

        cn.ac.fage.accessmesh.common.exception.BizException ex = assertThrows(
            cn.ac.fage.accessmesh.common.exception.BizException.class,
            () -> service.moveResource(1L, new cn.ac.fage.accessmesh.access.permission.dto.req.ResourceMoveReq(
                key("x"), new cn.ac.fage.accessmesh.access.permission.dto.req.ResourceKeyReq("BUTTON", "y", null)), 100L));
        assertEquals(20053, ex.getErrorCode());
        verify(resourceEntityMapper, org.mockito.Mockito.never()).update(any(ResourceEntity.class));
    }

    @Test
    @DisplayName("move 目标父为子孙节点 → 20053 拒绝（防环）")
    void shouldRejectMoveToOwnDescendant() {
        ResourceEntity entity = resourceWithKey(10L);
        ResourceEntity descendant = resourceWithKey(11L);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "MENU")).thenReturn(1);
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(1L, 1, "x", "default")).thenReturn(entity);
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(1L, 1, "child", "default")).thenReturn(descendant);
        when(engine.hasPermissionByEntityId(1L, 100L, ResourceTypeCode.RESOURCE, 10L, OperationCodeConstants.MANAGE))
            .thenReturn(true);
        when(resourceEntityDomainService.batchGetDescendantIds(1L, Set.of(10L)))
            .thenReturn(java.util.Map.of(10L, List.of(11L)));

        cn.ac.fage.accessmesh.common.exception.BizException ex = assertThrows(
            cn.ac.fage.accessmesh.common.exception.BizException.class,
            () -> service.moveResource(1L, new cn.ac.fage.accessmesh.access.permission.dto.req.ResourceMoveReq(
                key("x"), key("child")), 100L));
        assertEquals(20053, ex.getErrorCode());
        verify(resourceEntityMapper, org.mockito.Mockito.never()).update(any(ResourceEntity.class));
    }

    @Test
    @DisplayName("move parent=null → 移动到顶层（parentId 置 null）")
    void shouldMoveToTopLevelWhenParentNull() {
        ResourceEntity entity = resourceWithKey(10L);
        entity.setParentId(20L);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "MENU")).thenReturn(1);
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(1L, 1, "x", "default")).thenReturn(entity);
        when(engine.hasPermissionByEntityId(1L, 100L, ResourceTypeCode.RESOURCE, 10L, OperationCodeConstants.MANAGE))
            .thenReturn(true);

        service.moveResource(1L, new cn.ac.fage.accessmesh.access.permission.dto.req.ResourceMoveReq(
            key("x"), null), 100L);

        assertEquals(null, entity.getParentId());
        verify(resourceEntityMapper).update(any(ResourceEntity.class));
    }

    // ========== T-PERM-052：类型级所有权——SYNC 类型管理面只读（20055）==========
    // 守卫为 mock：本组用例锁「管理面写入口必须调用类型守卫且拒绝时不落库」；
    // 守卫判定语义（20055 抛出）由 ResourceTypeOwnershipGuardTest 覆盖。
    // 以下用例在旧实现（无类型门禁）下会因守卫未被调用/实际写库而失败。

    private void stubSyncTypeRejection(String typeCode) {
        org.mockito.Mockito.doThrow(new cn.ac.fage.accessmesh.common.exception.BizException(20055,
                "资源由外部来源维护，请到来源系统操作: resourceTypeCode=" + typeCode))
            .when(resourceTypeOwnershipGuard).rejectIfSyncManagedType(1L, typeCode);
    }

    @Test
    @DisplayName("create 命中 SYNC 类型 → 20055 拒绝，不落库（资源事实归声明来源服务）")
    void shouldRejectCreateOnSyncManagedType() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
            isNull(), eq(OperationCodeConstants.CREATE))).thenReturn(true);
        stubSyncTypeRejection("API");

        cn.ac.fage.accessmesh.common.exception.BizException ex = assertThrows(
            cn.ac.fage.accessmesh.common.exception.BizException.class,
            () -> service.createResource(1L, new cn.ac.fage.accessmesh.access.permission.dto.req.ResourceCreateReq(
                null, null, null, null, null, "API", "res-a", null, "资源A", null, null, null, null), 100L));
        assertEquals(20055, ex.getErrorCode());
        verify(resourceTypeOwnershipGuard).rejectIfSyncManagedType(1L, "API");
        verify(resourceEntityMapper, org.mockito.Mockito.never()).insert(any(ResourceEntity.class));
    }

    @Test
    @DisplayName("batch-create 命中 SYNC 类型 → 20055 拒绝，不落库（评审批次补回归锁）")
    void shouldRejectBatchCreateOnSyncManagedType() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
            isNull(), eq(OperationCodeConstants.CREATE))).thenReturn(true);
        org.mockito.Mockito.doThrow(new cn.ac.fage.accessmesh.common.exception.BizException(20055,
                "资源由外部来源维护，请到来源系统操作: resourceTypeCode=HR_ORG"))
            .when(resourceTypeOwnershipGuard).rejectIfAnySyncManagedByCodes(1L, java.util.Set.of("HR_ORG"));

        cn.ac.fage.accessmesh.common.exception.BizException ex = assertThrows(
            cn.ac.fage.accessmesh.common.exception.BizException.class,
            () -> service.batchCreateResources(1L, java.util.List.of(
                new cn.ac.fage.accessmesh.access.permission.dto.req.ResourceCreateReq(
                    null, null, null, null, null, "HR_ORG", "org-a", null, "部门A", null, null, null, null)), 100L));
        assertEquals(20055, ex.getErrorCode());
        verify(resourceTypeOwnershipGuard).rejectIfAnySyncManagedByCodes(1L, java.util.Set.of("HR_ORG"));
        verify(resourceEntityMapper, org.mockito.Mockito.never()).insertBatch(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("create/batch-create 与声明变更互斥：权限→树写锁→所有权门禁→落库（codex 二轮复评 P1-2 + 三轮 P2-1 回归锁）")
    void createResources_shouldLockTreeWritesBeforeOwnershipGate() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
            isNull(), eq(OperationCodeConstants.CREATE))).thenReturn(true);
        when(resourceTypeOwnershipGuard.rejectIfSyncManagedType(1L, "API")).thenReturn(apiType());

        service.createResource(1L, new cn.ac.fage.accessmesh.access.permission.dto.req.ResourceCreateReq(
            null, null, null, null, null, "API", "res-lock", null, "资源锁序", null, null, null, null), 100L);

        // codex 三轮复评 P2-1：engine 入序（钉「权限在锁前」）；旧实现无锁/门禁在锁前时失败
        org.mockito.InOrder createOrder = org.mockito.Mockito.inOrder(
            engine, treeWriteLockSupport, resourceTypeOwnershipGuard, resourceEntityMapper);
        createOrder.verify(engine).hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
            isNull(), eq(OperationCodeConstants.CREATE));
        createOrder.verify(treeWriteLockSupport).lockTreeWrites(1L,
            cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
        createOrder.verify(resourceTypeOwnershipGuard).rejectIfSyncManagedType(1L, "API");
        createOrder.verify(resourceEntityMapper).insert(any(ResourceEntity.class));

        // codex 三轮复评 P2-1：批量段前清调用记录——旧断言可被单创建段的锁满足（假阳性）
        org.mockito.Mockito.clearInvocations(treeWriteLockSupport, resourceTypeOwnershipGuard, resourceEntityMapper);
        // batch-create 同口径（拒绝路径足以钉锁序：锁 → 批量门禁）
        org.mockito.Mockito.doThrow(new cn.ac.fage.accessmesh.common.exception.BizException(20055,
                "资源由外部来源维护: resourceTypeCode=API"))
            .when(resourceTypeOwnershipGuard).rejectIfAnySyncManagedByCodes(1L, java.util.Set.of("API"));
        assertThrows(cn.ac.fage.accessmesh.common.exception.BizException.class, () ->
            service.batchCreateResources(1L, java.util.List.of(
                new cn.ac.fage.accessmesh.access.permission.dto.req.ResourceCreateReq(
                    null, null, null, null, null, "API", "res-lock-b", null, "资源B", null, null, null, null)), 100L));
        org.mockito.InOrder batchOrder = org.mockito.Mockito.inOrder(treeWriteLockSupport, resourceTypeOwnershipGuard);
        batchOrder.verify(treeWriteLockSupport).lockTreeWrites(1L,
            cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
        batchOrder.verify(resourceTypeOwnershipGuard).rejectIfAnySyncManagedByCodes(1L, java.util.Set.of("API"));
    }

    @Test
    @DisplayName("create 类型不存在 → fail-closed 20021（写路径权威化：类型缓存陈旧也不落库，codex 三轮复评 P1-2 回归锁）")
    void shouldRejectCreateWhenTypeMissingEvenIfStaleCacheResolves() {
        // 场景：类型已删但 TYPE_VALUE 缓存（10s L2）仍返回旧值——门禁库内直查 null 必须当场拒绝；
        // 旧实现（resolveTypeValue 兜底）下会以陈旧值 35 落库，产出引用不到有效 type_definition 的孤儿行
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
            isNull(), eq(OperationCodeConstants.CREATE))).thenReturn(true);
        when(resourceTypeOwnershipGuard.rejectIfSyncManagedType(1L, "GHOST")).thenReturn(null);
        // lenient：新实现不消费类型缓存（该桩仅用于证明「陈旧缓存存在时旧实现会落库」——回归锁语义）
        org.mockito.Mockito.lenient().when(typeResolutionService.resolveTypeValue(1L, "resource_type", "GHOST")).thenReturn(35);

        cn.ac.fage.accessmesh.common.exception.BizException ex = assertThrows(
            cn.ac.fage.accessmesh.common.exception.BizException.class,
            () -> service.createResource(1L, new cn.ac.fage.accessmesh.access.permission.dto.req.ResourceCreateReq(
                null, null, null, null, null, "GHOST", "res-ghost", null, "幽灵类型", null, null, null, null), 100L));
        assertEquals(20021, ex.getErrorCode());
        verify(resourceEntityMapper, org.mockito.Mockito.never()).insert(any(ResourceEntity.class));
        verify(typeResolutionService, org.mockito.Mockito.never())
            .resolveTypeValue(anyLong(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    /** API 类型权威行（value=3，门禁返回值消费用） */
    private static cn.ac.fage.accessmesh.access.permission.entity.TypeDefinition apiType() {
        cn.ac.fage.accessmesh.access.permission.entity.TypeDefinition td =
            new cn.ac.fage.accessmesh.access.permission.entity.TypeDefinition();
        td.setId(99L);
        td.setTenantId(1L);
        td.setTypeKey("resource_type");
        td.setTypeCode("API");
        td.setTypeValue(3);
        return td;
    }

    @Test
    @DisplayName("update 命中 SYNC 类型行 → 20055 拒绝（含 name 在内管理面完全只读）")
    void shouldRejectUpdateOnSyncManagedType() {
        ResourceEntity entity = resourceWithKey(10L);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "HR_ORG")).thenReturn(1);
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(1L, 1, "x", "default")).thenReturn(entity);
        // 旧实现放行至写库的对照：门禁拒绝不依赖引擎权限结果
        lenient().when(engine.hasPermissionByEntityId(1L, 100L, ResourceTypeCode.RESOURCE, 10L, OperationCodeConstants.MANAGE))
            .thenReturn(true);
        stubSyncTypeRejection("HR_ORG");

        cn.ac.fage.accessmesh.common.exception.BizException ex = assertThrows(
            cn.ac.fage.accessmesh.common.exception.BizException.class,
            () -> service.updateResource(1L,
                new cn.ac.fage.accessmesh.access.permission.dto.req.ResourceUpdateReq(
                    "HR_ORG", "x", null, "改名", null, null, null, null, null), 100L));
        assertEquals(20055, ex.getErrorCode());
        verify(resourceTypeOwnershipGuard).rejectIfSyncManagedType(1L, "HR_ORG");
        verify(resourceEntityMapper, org.mockito.Mockito.never()).update(any(ResourceEntity.class));
    }

    @Test
    @DisplayName("move 命中 SYNC 类型行 → 20055 拒绝（树位置归声明来源服务）")
    void shouldRejectMoveOnSyncManagedType() {
        ResourceEntity entity = resourceWithKey(10L);
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "HR_ORG")).thenReturn(1);
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(1L, 1, "x", "default")).thenReturn(entity);
        lenient().when(engine.hasPermissionByEntityId(1L, 100L, ResourceTypeCode.RESOURCE, 10L, OperationCodeConstants.MANAGE))
            .thenReturn(true);
        stubSyncTypeRejection("HR_ORG");

        cn.ac.fage.accessmesh.common.exception.BizException ex = assertThrows(
            cn.ac.fage.accessmesh.common.exception.BizException.class,
            () -> service.moveResource(1L, new cn.ac.fage.accessmesh.access.permission.dto.req.ResourceMoveReq(
                new cn.ac.fage.accessmesh.access.permission.dto.req.ResourceKeyReq("HR_ORG", "x", null), null), 100L));
        assertEquals(20055, ex.getErrorCode());
        verify(resourceTypeOwnershipGuard).rejectIfSyncManagedType(1L, "HR_ORG");
        verify(resourceEntityMapper, org.mockito.Mockito.never()).update(any(ResourceEntity.class));
    }

    @Test
    @DisplayName("remove 级联守卫：MANAGED 根 + SYNC 类型后代（跨类型父子边）→ 20055 拒绝，后代不软删")
    void shouldRejectRemoveWhenCascadeHitsSyncManagedDescendant() {
        // 根：HR_MENU(类型1) MANAGED；后代：id=11 类型7（如 BI_MENU，SYNC）——sync 通道允许
        // 跨类型父子边，旧实现会连同后代一并软删
        ResourceEntity root = resourceWithKey(10L);
        root.setResourceType(1);
        root.setCode("x");
        ResourceEntity syncChild = resourceWithKey(11L);
        syncChild.setResourceType(7);
        syncChild.setCode("bi-1");
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("HR_MENU")))
            .thenReturn(java.util.Map.of("HR_MENU", 1));
        when(resourceEntityMapper.selectByTypesAndCodesAndCodeTypes(eq(1L), eq(Set.of(1)), anySet(), anySet()))
            .thenReturn(List.of(root));
        when(engine.getDeniedEntityIds(1L, 99L, ResourceTypeCode.RESOURCE, Set.of(10L), OperationCodeConstants.MANAGE))
            .thenReturn(Set.of());
        when(resourceEntityDomainService.batchGetDescendantIds(1L, Set.of(10L)))
            .thenReturn(java.util.Map.of(10L, List.of(11L)));
        when(resourceEntityDomainService.batchSelectByIdsMap(1L, Set.of(10L, 11L)))
            .thenReturn(java.util.Map.of(10L, root, 11L, syncChild));
        org.mockito.Mockito.doThrow(new cn.ac.fage.accessmesh.common.exception.BizException(20055,
                "资源由外部来源维护，请到来源系统操作"))
            .when(resourceTypeOwnershipGuard).rejectIfAnySyncManagedByValues(1L, Set.of(1, 7));

        cn.ac.fage.accessmesh.common.exception.BizException ex = assertThrows(
            cn.ac.fage.accessmesh.common.exception.BizException.class,
            () -> service.deleteResources(1L,
                List.of(new cn.ac.fage.accessmesh.access.permission.dto.req.ResourceKeyReq("HR_MENU", "x", null)), 99L));
        assertEquals(20055, ex.getErrorCode());
        // 守卫以删除全集（含展开后代）的类型值调用——旧实现无此调用且后代被软删
        verify(resourceTypeOwnershipGuard).rejectIfAnySyncManagedByValues(1L, Set.of(1, 7));
        verify(resourceEntityDomainService, org.mockito.Mockito.never())
            .softDeleteBatch(anyLong(), any(), any());
    }

    private ResourceEntity resourceWithKey(Long id) {
        ResourceEntity entity = new ResourceEntity();
        entity.setId(id);
        entity.setTenantId(1L);
        entity.setResourceType(1);
        entity.setCode("res:x");
        entity.setCodeType("default");
        entity.setName("资源X");
        return entity;
    }

    // ========== T-PERM-027 §7.5：API 映射列表 SERVICE:VIEW 门禁与结果裁剪 ==========

    @Test
    @DisplayName("按 serviceCode 查映射：无该服务实例 VIEW → SecurityException，不触碰查询")
    void shouldRejectMappingListWithoutInstanceViewPermission() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.SERVICE),
                eq("svc-a"), eq(OperationCodeConstants.VIEW))).thenReturn(false);

            assertThrows(SecurityException.class,
                () -> service.listApiMappings(1L, null, "svc-a"));
        }
        verifyNoInteractions(apiMappingMapper);
    }

    @Test
    @DisplayName("管理全量列表：无类型级 SERVICE:VIEW → SecurityException")
    void shouldRejectMappingListWithoutTypeLevelViewPermission() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.SERVICE),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(false);

            assertThrows(SecurityException.class,
                () -> service.listApiMappings(1L, null, null));
        }
        verifyNoInteractions(apiMappingMapper);
    }

    @Test
    @DisplayName("管理全量列表：类型级 VIEW 通过后按服务裁剪——拒绝服务的映射不出现在结果中")
    void shouldTrimDeniedServiceMappingsInUngatedList() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.SERVICE),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(true);
            when(apiMappingMapper.selectValidList(1L, null, null))
                .thenReturn(List.of(mapping(301L, 1001L, "svc-a"), mapping(302L, 1002L, "svc-b")));
            when(engine.getDeniedResourceCodes(eq(1L), eq(100L), eq(ResourceTypeCode.SERVICE),
                eq(Set.of("svc-a", "svc-b")), eq(OperationCodeConstants.VIEW)))
                .thenReturn(Set.of("svc-b"));
            when(resourceEntityMapper.selectValidByIds(eq(1L), any())).thenReturn(List.of(resource(1001L)));

            var result = service.listApiMappings(1L, null, null);

            assertEquals(1, result.size());
            assertEquals("svc-a", result.get(0).serviceCode());
        }
    }

    @Test
    @DisplayName("映射响应补全资源业务字段（§7.3）：resourceCode/resourceName/resourceTypeCode/maintainSource")
    void shouldEnrichMappingRespWithResourceBusinessFields() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.SERVICE),
                eq("svc-a"), eq(OperationCodeConstants.VIEW))).thenReturn(true);
            when(apiMappingMapper.selectValidList(1L, null, "svc-a"))
                .thenReturn(List.of(mapping(301L, 1001L, "svc-a")));
            ResourceEntity resource = resource(1001L);
            when(resourceEntityMapper.selectValidByIds(eq(1L), any())).thenReturn(List.of(resource));
            when(typeResolutionService.resolveTypeCode(1L, "resource_type", 3)).thenReturn("API");

            var result = service.listApiMappings(1L, null, "svc-a");

            assertEquals(1, result.size());
            assertEquals("res:x", result.get(0).resourceCode());
            assertEquals("资源X", result.get(0).resourceName());
            assertEquals("API", result.get(0).resourceTypeCode());
            assertEquals("SERVICE_SYNC", result.get(0).maintainSource());
        }
    }

    @Test
    @DisplayName("资源已软删的映射：业务字段置 null 而非报错")
    void shouldReturnNullResourceFieldsWhenResourceDeleted() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.SERVICE),
                eq("svc-a"), eq(OperationCodeConstants.VIEW))).thenReturn(true);
            when(apiMappingMapper.selectValidList(1L, null, "svc-a"))
                .thenReturn(List.of(mapping(301L, 9999L, "svc-a")));
            when(resourceEntityMapper.selectValidByIds(eq(1L), any())).thenReturn(List.of());

            var result = service.listApiMappings(1L, null, "svc-a");

            assertEquals(1, result.size());
            assertEquals(null, result.get(0).resourceCode());
            assertEquals(null, result.get(0).maintainSource());
        }
    }

    @Test
    @DisplayName("空白 serviceCode 与 null 同义：类型级门禁 + 不过滤查询（门禁与 SQL 语义不分叉）")
    void shouldTreatBlankServiceCodeAsUnfiltered() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.SERVICE),
                isNull(), eq(OperationCodeConstants.VIEW))).thenReturn(true);
            when(apiMappingMapper.selectValidList(eq(1L), isNull(), isNull()))
                .thenReturn(List.of());

            var result = service.listApiMappings(1L, null, " ");

            assertEquals(0, result.size());
            // 规整后 mapper 收到 null 而非字面 " "（旧实现直传 " " 会被 SQL 当过滤条件）
            verify(apiMappingMapper).selectValidList(eq(1L), isNull(), isNull());
        }
    }

    private ResourceApiMapping mapping(Long id, Long resourceEntityId, String serviceCode) {
        ResourceApiMapping mapping = new ResourceApiMapping();
        mapping.setId(id);
        mapping.setTenantId(1L);
        mapping.setResourceEntityId(resourceEntityId);
        mapping.setServiceCode(serviceCode);
        mapping.setHttpMethod("POST");
        mapping.setPathPattern("/api/" + id);
        mapping.setMatchOrder(0);
        mapping.setEnabled(true);
        return mapping;
    }

    private ResourceEntity resource(Long id) {
        ResourceEntity entity = new ResourceEntity();
        entity.setId(id);
        entity.setTenantId(1L);
        entity.setResourceType(3);
        entity.setCode("res:x");
        entity.setName("资源X");
        entity.setMaintainSource("SERVICE_SYNC");
        return entity;
    }
}
