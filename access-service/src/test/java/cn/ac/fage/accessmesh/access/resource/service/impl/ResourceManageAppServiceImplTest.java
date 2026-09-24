package cn.ac.fage.accessmesh.access.resource.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport;
import cn.ac.fage.accessmesh.access.engine.constant.OperationCode;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceApiMapping;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.type.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceApiMappingMapper;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.grant.service.domain.RoleResourcePermissionDomainService;
import cn.ac.fage.accessmesh.access.domain.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.access.resource.service.domain.ResourceEntityDomainService;
import cn.ac.fage.accessmesh.access.type.service.domain.ResourceTypeOwnershipGuard;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.engine.core.PermQueryEngine;
import cn.ac.fage.accessmesh.access.infrastructure.util.OperatorContext;
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
    @Mock private RoleResourcePermissionDomainService rolePermMapper;
    @Mock private ResourceTypeOwnershipGuard resourceTypeOwnershipGuard;
    @Mock private TreeWriteLockSupport treeWriteLockSupport;

    private ResourceManageAppServiceImpl service;

    @BeforeEach
    void setUp() {
        // 测试简化：投影主体 = 传入 operatorId
        service = new ResourceManageAppServiceImpl(
            resourceEntityMapper,
            apiMappingMapper,
            org.mockito.Mockito.mock(cn.ac.fage.accessmesh.access.resource.mapper.ServiceConfigMapper.class),
            resourceEntityDomainService,
            typeResolutionService,
            domainClassifyService,
            engine,
            rolePermMapper,
            org.mockito.Mockito.mock(cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConditionDomainService.class),
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
                new cn.ac.fage.accessmesh.perm.common.dto.req.ResourceUpdateReq(
                    "MENU", "m1", "default", null, null, null, null, null), 9L))
            .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class);
        org.mockito.Mockito.verify(treeWriteLockSupport).lockTreeWrites(1L,
            cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);

        org.mockito.Mockito.clearInvocations(treeWriteLockSupport);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.moveResource(1L,
                new cn.ac.fage.accessmesh.access.resource.dto.req.ResourceMoveReq(
                    new cn.ac.fage.accessmesh.perm.common.dto.req.ResourceKeyReq("MENU", "m1", "default"),
                    null), 9L))
            .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class);
        org.mockito.Mockito.verify(treeWriteLockSupport).lockTreeWrites(1L,
            cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
    }

    @Test
    @DisplayName("类型级拒且租户零可见资源 → SecurityException（T-ACCESS-052 fail-closed 语义保持）")
    void shouldRejectResourceTreeWithoutResourceViewPermission() {
        when(resourceEntityMapper.selectValidResourceIds(1L)).thenReturn(List.of());
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
                isNull(), eq(OperationCode.VIEW))).thenReturn(false);

            assertThrows(SecurityException.class, () -> service.getResourceTree(1L, null, null));
        }
        verifyNoInteractions(typeResolutionService);
    }

    @Test
    @DisplayName("有 RESOURCE:VIEW → 正常返回资源树")
    void shouldReturnResourceTreeWhenViewGranted() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
                isNull(), eq(OperationCode.VIEW))).thenReturn(true);
            when(resourceEntityMapper.selectResourceTree(eq(1L), isNull(), eq(false)))
                .thenReturn(List.<ResourceEntity>of());

            assertEquals(List.of(), service.getResourceTree(1L, null, null));
        }
    }

    // ========== T-PERM-028：业务键定位 + 读门禁补齐 + extraClear + move 校验 ==========

    private static cn.ac.fage.accessmesh.perm.common.dto.req.ResourceKeyReq key(String code) {
        return new cn.ac.fage.accessmesh.perm.common.dto.req.ResourceKeyReq("MENU", code, null);
    }

    @Test
    @DisplayName("create 落库前归一 codeType：空白→default、去首尾空白（键路径 trim 对称，双轨评审 P2 回归锁）")
    void shouldNormalizeCodeTypeOnCreate() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
            isNull(), eq(OperationCode.CREATE))).thenReturn(true);
        // API 类型已由种子声明 SYNC+access-service（T-PERM-069，生产 create 被 guard 20055 拒）；
        // 此处 mock 守卫放行返回权威类型行，单测只覆盖守卫之后的 AppService 落库逻辑；
        // codex 三轮复评 P1-2：create 消费门禁返回的权威类型行（不再经 TYPE_VALUE 类型缓存）
        when(resourceTypeOwnershipGuard.rejectIfSyncManagedType(1L, "API")).thenReturn(apiType());

        // 带空白 codeType：落库前 trim，否则该行无法经业务键（归一 trim）寻址
        service.createResource(1L, new cn.ac.fage.accessmesh.perm.common.dto.req.ResourceCreateReq(
            null, null, null, null, null, "API", "res-a", " X ", "资源A", null, null, null), 100L);
        org.mockito.ArgumentCaptor<ResourceEntity> captor1 = org.mockito.ArgumentCaptor.forClass(ResourceEntity.class);
        verify(resourceEntityMapper, org.mockito.Mockito.times(1)).insert(captor1.capture());
        assertEquals("X", captor1.getValue().getCodeType());

        // null codeType：落库 default
        service.createResource(1L, new cn.ac.fage.accessmesh.perm.common.dto.req.ResourceCreateReq(
            null, null, null, null, null, "API", "res-b", null, "资源B", null, null, null), 100L);
        org.mockito.ArgumentCaptor<ResourceEntity> captor2 = org.mockito.ArgumentCaptor.forClass(ResourceEntity.class);
        verify(resourceEntityMapper, org.mockito.Mockito.times(2)).insert(captor2.capture());
        assertEquals("default", captor2.getAllValues().get(1).getCodeType());
    }

    @Test
    @DisplayName("detail 实例级拒（T-ACCESS-052：先查实体再按实体判定）→ SecurityException")
    void shouldRejectResourceDetailWithoutResourceViewPermission() {
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "MENU")).thenReturn(1);
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(1L, 1, "x", "default"))
            .thenReturn(resourceWithKey(10L));
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByEntityId(1L, 100L, ResourceTypeCode.RESOURCE, 10L,
                OperationCode.VIEW)).thenReturn(false);

            assertThrows(SecurityException.class, () -> service.getResource(1L, key("x")));
        }
    }

    @Test
    @DisplayName("detail 按业务键查询（codeType 缺省归一 default），查不到 → 20004")
    void shouldGetResourceByBusinessKeyAndThrowWhenMissing() {
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByEntityId(1L, 100L, ResourceTypeCode.RESOURCE, 10L,
                OperationCode.VIEW)).thenReturn(true);
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
    @DisplayName("类型级拒且租户零可见资源 → SecurityException（分页/计数与树同口径 fail-closed）")
    void shouldRejectResourceListWithoutResourceViewPermission() {
        when(resourceEntityMapper.selectValidResourceIds(1L)).thenReturn(List.of());
        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
                isNull(), eq(OperationCode.VIEW))).thenReturn(false);

            assertThrows(SecurityException.class, () -> service.listResources(1L, null, null, 0, 10));
            assertThrows(SecurityException.class, () -> service.countResources(1L, null, null));
        }
    }

    @Test
    @DisplayName("T-ACCESS-052 资源目录实例准入：类型级拒但持实例授权 → 可见子集下推分页/计数")
    void shouldPushVisibleEntityIdsWhenTypeLevelDenied() {
        when(resourceEntityMapper.selectValidResourceIds(1L)).thenReturn(List.of(10L, 20L, 30L));
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
            isNull(), eq(OperationCode.VIEW))).thenReturn(false);
        // 引擎批量判定：20/30 被拒（仅持实体 10 的 VIEW 实例授权）
        when(engine.getDeniedEntityIds(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
            any(), eq(OperationCode.VIEW))).thenReturn(java.util.Set.of(20L, 30L));
        when(resourceEntityMapper.selectResourceListPaged(eq(1L), isNull(), eq(false),
            eq(java.util.Set.of(10L)), eq(0), eq(10))).thenReturn(List.of(resourceWithKey(10L)));

        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);

            var result = service.listResources(1L, null, null, 0, 10);
            assertEquals(1, result.size());
            assertEquals(10L, result.get(0).id());
            service.countResources(1L, null, null);
        }
        verify(resourceEntityMapper).selectResourceListCount(1L, null, false, java.util.Set.of(10L));
    }

    @Test
    @DisplayName("T-ACCESS-052 pageResources 组合形态：可见集合单次解析（count 与分页共用）")
    void shouldResolveVisibleSetOnceInPageResources() {
        when(resourceEntityMapper.selectValidResourceIds(1L)).thenReturn(List.of(10L, 20L));
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
            isNull(), eq(OperationCode.VIEW))).thenReturn(false);
        when(engine.getDeniedEntityIds(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
            any(), eq(OperationCode.VIEW))).thenReturn(java.util.Set.of(20L));
        when(resourceEntityMapper.selectResourceListCount(eq(1L), isNull(), eq(false), eq(java.util.Set.of(10L))))
            .thenReturn(1L);
        when(resourceEntityMapper.selectResourceListPaged(eq(1L), isNull(), eq(false),
            eq(java.util.Set.of(10L)), eq(0), eq(10))).thenReturn(List.of(resourceWithKey(10L)));

        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);

            var page = service.pageResources(1L, null, null, 1, 10);
            org.junit.jupiter.api.Assertions.assertEquals(1L, page.total());
            org.junit.jupiter.api.Assertions.assertEquals(1, page.items().size());
        }
        // 可见集只解析一次（旧 Controller 分调 count+list 形态为两次）
        org.mockito.Mockito.verify(resourceEntityMapper, org.mockito.Mockito.times(1)).selectValidResourceIds(1L);
        org.mockito.Mockito.verify(resourceEntityMapper, org.mockito.Mockito.times(1))
            .selectResourceListCount(eq(1L), isNull(), eq(false), eq(java.util.Set.of(10L)));
    }

    @Test
    @DisplayName("T-ACCESS-052 资源树实例裁剪：可见节点保留祖先导航链")
    void shouldKeepAncestorChainInTreeWhenInstanceFiltered() {
        // 三层树：root(1) > mid(2) > leaf(3)；仅 leaf 可见 → 树含 root+mid+leaf（骨架完整）
        ResourceEntity root = resourceWithKey(1L);
        root.setParentId(null);
        ResourceEntity mid = resourceWithKey(2L);
        mid.setParentId(1L);
        ResourceEntity leaf = resourceWithKey(3L);
        leaf.setParentId(2L);
        ResourceEntity other = resourceWithKey(9L);
        other.setParentId(null);
        when(resourceEntityMapper.selectValidResourceIds(1L)).thenReturn(List.of(1L, 2L, 3L, 9L));
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
            isNull(), eq(OperationCode.VIEW))).thenReturn(false);
        when(engine.getDeniedEntityIds(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
            any(), eq(OperationCode.VIEW))).thenReturn(java.util.Set.of(1L, 2L, 9L));
        when(resourceEntityMapper.selectResourceTree(eq(1L), isNull(), eq(false)))
            .thenReturn(List.of(root, mid, leaf, other));

        try (MockedStatic<OperatorContext> operatorContext = mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);

            var tree = service.getResourceTree(1L, null, null);
            // 仅根节点（骨架 root>mid>leaf 一条链）；other 子树整支被裁
            assertEquals(1, tree.size());
            assertEquals(1L, tree.get(0).root().id());
            assertEquals(2L, tree.get(0).root().children().get(0).id());
            assertEquals(3L, tree.get(0).root().children().get(0).children().get(0).id());
        }
    }

    @Test
    @DisplayName("update 按业务键定位 + extraClear=true 清空 extra（null 与「未传」区分）")
    void shouldClearExtraWhenExtraClearTrue() {
        ResourceEntity entity = resourceWithKey(10L);
        entity.setExtra("{\"k\":1}");
        when(typeResolutionService.resolveTypeValue(1L, "resource_type", "MENU")).thenReturn(1);
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(1L, 1, "x", "default")).thenReturn(entity);
        when(engine.hasPermissionByEntityId(1L, 100L, ResourceTypeCode.RESOURCE, 10L, OperationCode.MANAGE))
            .thenReturn(true);

        var resp = service.updateResource(1L,
            new cn.ac.fage.accessmesh.perm.common.dto.req.ResourceUpdateReq(
                "MENU", "x", null, null, null, null, null, Boolean.TRUE), 100L);

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
        when(engine.hasPermissionByEntityId(1L, 100L, ResourceTypeCode.RESOURCE, 10L, OperationCode.MANAGE))
            .thenReturn(true);

        var resp = service.updateResource(1L,
            new cn.ac.fage.accessmesh.perm.common.dto.req.ResourceUpdateReq(
                "MENU", "x", null, "新名称", null, null, null, null), 100L);

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
        when(engine.hasPermissionByEntityId(1L, 100L, ResourceTypeCode.RESOURCE, 10L, OperationCode.MANAGE))
            .thenReturn(true);

        cn.ac.fage.accessmesh.common.exception.BizException ex = assertThrows(
            cn.ac.fage.accessmesh.common.exception.BizException.class,
            () -> service.moveResource(1L, new cn.ac.fage.accessmesh.access.resource.dto.req.ResourceMoveReq(
                key("x"), new cn.ac.fage.accessmesh.perm.common.dto.req.ResourceKeyReq("BUTTON", "y", null)), 100L));
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
        when(engine.hasPermissionByEntityId(1L, 100L, ResourceTypeCode.RESOURCE, 10L, OperationCode.MANAGE))
            .thenReturn(true);
        when(resourceEntityDomainService.batchGetDescendantIds(1L, Set.of(10L)))
            .thenReturn(java.util.Map.of(10L, List.of(11L)));

        cn.ac.fage.accessmesh.common.exception.BizException ex = assertThrows(
            cn.ac.fage.accessmesh.common.exception.BizException.class,
            () -> service.moveResource(1L, new cn.ac.fage.accessmesh.access.resource.dto.req.ResourceMoveReq(
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
        when(engine.hasPermissionByEntityId(1L, 100L, ResourceTypeCode.RESOURCE, 10L, OperationCode.MANAGE))
            .thenReturn(true);

        service.moveResource(1L, new cn.ac.fage.accessmesh.access.resource.dto.req.ResourceMoveReq(
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
            isNull(), eq(OperationCode.CREATE))).thenReturn(true);
        stubSyncTypeRejection("API");

        cn.ac.fage.accessmesh.common.exception.BizException ex = assertThrows(
            cn.ac.fage.accessmesh.common.exception.BizException.class,
            () -> service.createResource(1L, new cn.ac.fage.accessmesh.perm.common.dto.req.ResourceCreateReq(
                null, null, null, null, null, "API", "res-a", null, "资源A", null, null, null), 100L));
        assertEquals(20055, ex.getErrorCode());
        verify(resourceTypeOwnershipGuard).rejectIfSyncManagedType(1L, "API");
        verify(resourceEntityMapper, org.mockito.Mockito.never()).insert(any(ResourceEntity.class));
    }

    @Test
    @DisplayName("batch-create 命中 SYNC 类型 → 20055 拒绝，不落库（评审批次补回归锁）")
    void shouldRejectBatchCreateOnSyncManagedType() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
            isNull(), eq(OperationCode.CREATE))).thenReturn(true);
        org.mockito.Mockito.doThrow(new cn.ac.fage.accessmesh.common.exception.BizException(20055,
                "资源由外部来源维护，请到来源系统操作: resourceTypeCode=HR_ORG"))
            .when(resourceTypeOwnershipGuard).rejectIfAnySyncManagedByCodes(1L, java.util.Set.of("HR_ORG"));

        cn.ac.fage.accessmesh.common.exception.BizException ex = assertThrows(
            cn.ac.fage.accessmesh.common.exception.BizException.class,
            () -> service.batchCreateResources(1L, java.util.List.of(
                new cn.ac.fage.accessmesh.perm.common.dto.req.ResourceCreateReq(
                    null, null, null, null, null, "HR_ORG", "org-a", null, "部门A", null, null, null)), 100L));
        assertEquals(20055, ex.getErrorCode());
        verify(resourceTypeOwnershipGuard).rejectIfAnySyncManagedByCodes(1L, java.util.Set.of("HR_ORG"));
        verify(resourceEntityMapper, org.mockito.Mockito.never()).insertBatch(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("create/batch-create 与声明变更互斥：权限→树写锁→所有权门禁→落库（codex 二轮复评 P1-2 + 三轮 P2-1 回归锁）")
    void createResources_shouldLockTreeWritesBeforeOwnershipGate() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
            isNull(), eq(OperationCode.CREATE))).thenReturn(true);
        when(resourceTypeOwnershipGuard.rejectIfSyncManagedType(1L, "API")).thenReturn(apiType());

        service.createResource(1L, new cn.ac.fage.accessmesh.perm.common.dto.req.ResourceCreateReq(
            null, null, null, null, null, "API", "res-lock", null, "资源锁序", null, null, null), 100L);

        // codex 三轮复评 P2-1：engine 入序（钉「权限在锁前」）；旧实现无锁/门禁在锁前时失败
        org.mockito.InOrder createOrder = org.mockito.Mockito.inOrder(
            engine, treeWriteLockSupport, resourceTypeOwnershipGuard, resourceEntityMapper);
        createOrder.verify(engine).hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
            isNull(), eq(OperationCode.CREATE));
        createOrder.verify(treeWriteLockSupport).lockTreeWrites(1L,
            cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
        createOrder.verify(resourceTypeOwnershipGuard).rejectIfSyncManagedType(1L, "API");
        createOrder.verify(resourceEntityMapper).insert(any(ResourceEntity.class));

        // codex 四轮复评 P2-2：批量段改成功路径全序（拒绝路径只能证明锁→门禁）——
        // 权限 → 锁 → 批量门禁（返回权威行）→ insertBatch；旧实现无锁/门禁在锁前时失败
        org.mockito.Mockito.clearInvocations(engine, treeWriteLockSupport, resourceTypeOwnershipGuard, resourceEntityMapper);
        when(resourceTypeOwnershipGuard.rejectIfAnySyncManagedByCodes(1L, java.util.Set.of("API")))
            .thenReturn(java.util.Map.of("API", apiType()));
        when(resourceEntityDomainService.batchSelectByIdsMap(eq(1L), any())).thenReturn(java.util.Map.of());
        // T-PERM-076：完整键查重首查空集 + 落库后回查返回带主键新行（响应身份回查校准）
        when(resourceEntityMapper.selectByTypesAndCodesAndCodeTypes(eq(1L), anySet(), anySet(), anySet()))
            .thenReturn(List.of())
            .thenReturn(List.of(resourceWithTriple(61L, 3, "res-lock-b", "default")));
        when(typeResolutionService.batchResolveResourceIds(eq(1L), any())).thenReturn(java.util.Map.of());

        service.batchCreateResources(1L, java.util.List.of(
            new cn.ac.fage.accessmesh.perm.common.dto.req.ResourceCreateReq(
                null, null, null, null, null, "API", "res-lock-b", null, "资源B", null, null, null)), 100L);

        org.mockito.InOrder batchOrder = org.mockito.Mockito.inOrder(
            engine, treeWriteLockSupport, resourceTypeOwnershipGuard, resourceEntityMapper);
        batchOrder.verify(engine).hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
            isNull(), eq(OperationCode.CREATE));
        batchOrder.verify(treeWriteLockSupport).lockTreeWrites(1L,
            cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
        batchOrder.verify(resourceTypeOwnershipGuard).rejectIfAnySyncManagedByCodes(1L, java.util.Set.of("API"));
        batchOrder.verify(resourceEntityMapper).insertBatch(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("create 类型不存在 → fail-closed 20021（写路径权威化：类型缓存陈旧也不落库，codex 三轮复评 P1-2 回归锁）")
    void shouldRejectCreateWhenTypeMissingEvenIfStaleCacheResolves() {
        // 场景：类型已删但 TYPE_VALUE 缓存（10s L2）仍返回旧值——门禁库内直查 null 必须当场拒绝；
        // 旧实现（resolveTypeValue 兜底）下会以陈旧值 35 落库，产出引用不到有效 type_definition 的孤儿行
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
            isNull(), eq(OperationCode.CREATE))).thenReturn(true);
        when(resourceTypeOwnershipGuard.rejectIfSyncManagedType(1L, "GHOST")).thenReturn(null);
        // lenient：新实现不消费类型缓存（该桩仅用于证明「陈旧缓存存在时旧实现会落库」——回归锁语义）
        org.mockito.Mockito.lenient().when(typeResolutionService.resolveTypeValue(1L, "resource_type", "GHOST")).thenReturn(35);

        cn.ac.fage.accessmesh.common.exception.BizException ex = assertThrows(
            cn.ac.fage.accessmesh.common.exception.BizException.class,
            () -> service.createResource(1L, new cn.ac.fage.accessmesh.perm.common.dto.req.ResourceCreateReq(
                null, null, null, null, null, "GHOST", "res-ghost", null, "幽灵类型", null, null, null), 100L));
        assertEquals(20021, ex.getErrorCode());
        verify(resourceEntityMapper, org.mockito.Mockito.never()).insert(any(ResourceEntity.class));
        verify(typeResolutionService, org.mockito.Mockito.never())
            .resolveTypeValue(anyLong(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    /** API 类型权威行（value=3，门禁返回值消费用） */
    private static cn.ac.fage.accessmesh.access.type.entity.TypeDefinition apiType() {
        cn.ac.fage.accessmesh.access.type.entity.TypeDefinition td =
            new cn.ac.fage.accessmesh.access.type.entity.TypeDefinition();
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
        lenient().when(engine.hasPermissionByEntityId(1L, 100L, ResourceTypeCode.RESOURCE, 10L, OperationCode.MANAGE))
            .thenReturn(true);
        stubSyncTypeRejection("HR_ORG");

        cn.ac.fage.accessmesh.common.exception.BizException ex = assertThrows(
            cn.ac.fage.accessmesh.common.exception.BizException.class,
            () -> service.updateResource(1L,
                new cn.ac.fage.accessmesh.perm.common.dto.req.ResourceUpdateReq(
                    "HR_ORG", "x", null, "改名", null, null, null, null), 100L));
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
        lenient().when(engine.hasPermissionByEntityId(1L, 100L, ResourceTypeCode.RESOURCE, 10L, OperationCode.MANAGE))
            .thenReturn(true);
        stubSyncTypeRejection("HR_ORG");

        cn.ac.fage.accessmesh.common.exception.BizException ex = assertThrows(
            cn.ac.fage.accessmesh.common.exception.BizException.class,
            () -> service.moveResource(1L, new cn.ac.fage.accessmesh.access.resource.dto.req.ResourceMoveReq(
                new cn.ac.fage.accessmesh.perm.common.dto.req.ResourceKeyReq("HR_ORG", "x", null), null), 100L));
        assertEquals(20055, ex.getErrorCode());
        verify(resourceTypeOwnershipGuard).rejectIfSyncManagedType(1L, "HR_ORG");
        verify(resourceEntityMapper, org.mockito.Mockito.never()).update(any(ResourceEntity.class));
    }

    @Test
    @DisplayName("remove 级联守卫：MANAGED 根 + SYNC 类型后代（跨类型父子边）→ 20055 拒绝，后代不软删")
    void shouldRejectRemoveWhenCascadeHitsSyncManagedDescendant() {
        // 根：HR_MENU(类型1) MANAGED；后代：id=11 类型7（如 BI_MENU，SYNC）——跨类型父边
        // 入口已收紧（T-PERM-068）但守卫须防 DB 直写脏数据，旧实现会连同后代一并软删
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
        when(engine.getDeniedEntityIds(1L, 99L, ResourceTypeCode.RESOURCE, Set.of(10L), OperationCode.MANAGE))
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
                List.of(new cn.ac.fage.accessmesh.perm.common.dto.req.ResourceKeyReq("HR_MENU", "x", null)), 99L));
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
                eq("svc-a"), eq(OperationCode.VIEW))).thenReturn(false);

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
                isNull(), eq(OperationCode.VIEW))).thenReturn(false);

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
                isNull(), eq(OperationCode.VIEW))).thenReturn(true);
            when(apiMappingMapper.selectValidList(1L, null, null))
                .thenReturn(List.of(mapping(301L, 1001L, "svc-a"), mapping(302L, 1002L, "svc-b")));
            when(engine.getDeniedResourceCodes(eq(1L), eq(100L), eq(ResourceTypeCode.SERVICE),
                eq(Set.of("svc-a", "svc-b")), eq(OperationCode.VIEW)))
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
                eq("svc-a"), eq(OperationCode.VIEW))).thenReturn(true);
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
                eq("svc-a"), eq(OperationCode.VIEW))).thenReturn(true);
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
                isNull(), eq(OperationCode.VIEW))).thenReturn(true);
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

    // ========== T-PERM-068（Q-007 定案②，2026-09-17）：create/batch-create 跨类型父边对齐 move（20053）+ 单条裸 parentId 补存在性/类型校验。以下用例在旧实现（父只查存在、裸 parentId 不校验）下失败。 ==========

    @Test
    @DisplayName("create 业务键父跨类型 → 20053 拒绝（旧实现仅查父存在，解析成功即挂异类型父）")
    void shouldRejectCreateWithCrossTypeParentBusinessKey() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
            isNull(), eq(OperationCode.CREATE))).thenReturn(true);
        when(resourceTypeOwnershipGuard.rejectIfSyncManagedType(1L, "API")).thenReturn(apiType());

        cn.ac.fage.accessmesh.common.exception.BizException ex = assertThrows(
            cn.ac.fage.accessmesh.common.exception.BizException.class,
            () -> service.createResource(1L, new cn.ac.fage.accessmesh.perm.common.dto.req.ResourceCreateReq(
                null, "MENU", "parent-x", null, null, "API", "res-a", null, "资源A", null, null, null), 100L));
        assertEquals(20053, ex.getErrorCode());
        verify(resourceEntityMapper, org.mockito.Mockito.never()).insert(any(ResourceEntity.class));
    }

    @Test
    @DisplayName("create 裸 parentId 不存在 → 20004 拒绝（旧实现不校验直接落库成悬挂引用）")
    void shouldRejectCreateWhenRawParentIdMissing() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
            isNull(), eq(OperationCode.CREATE))).thenReturn(true);
        when(resourceTypeOwnershipGuard.rejectIfSyncManagedType(1L, "API")).thenReturn(apiType());
        when(resourceEntityDomainService.selectValidById(1L, 777L)).thenReturn(null);

        cn.ac.fage.accessmesh.common.exception.BizException ex = assertThrows(
            cn.ac.fage.accessmesh.common.exception.BizException.class,
            () -> service.createResource(1L, new cn.ac.fage.accessmesh.perm.common.dto.req.ResourceCreateReq(
                777L, null, null, null, null, "API", "res-a", null, "资源A", null, null, null), 100L));
        assertEquals(20004, ex.getErrorCode());
        verify(resourceEntityMapper, org.mockito.Mockito.never()).insert(any(ResourceEntity.class));
    }

    @Test
    @DisplayName("create 裸 parentId 跨类型 → 20053 拒绝（旧实现不校验直接落库）")
    void shouldRejectCreateWhenRawParentIdCrossType() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
            isNull(), eq(OperationCode.CREATE))).thenReturn(true);
        when(resourceTypeOwnershipGuard.rejectIfSyncManagedType(1L, "API")).thenReturn(apiType());
        // 父实体类型=1（MENU），自身类型=3（API）→ 跨类型
        when(resourceEntityDomainService.selectValidById(1L, 55L)).thenReturn(resourceWithKey(55L));

        cn.ac.fage.accessmesh.common.exception.BizException ex = assertThrows(
            cn.ac.fage.accessmesh.common.exception.BizException.class,
            () -> service.createResource(1L, new cn.ac.fage.accessmesh.perm.common.dto.req.ResourceCreateReq(
                55L, null, null, null, null, "API", "res-a", null, "资源A", null, null, null), 100L));
        assertEquals(20053, ex.getErrorCode());
        verify(resourceEntityMapper, org.mockito.Mockito.never()).insert(any(ResourceEntity.class));
    }

    @Test
    @DisplayName("batch-create 跨类型父项跳过、其余项成功（宽容收集语义内新增校验；旧实现跨类型父解析成功即入库）")
    void batchCreateSkipsCrossTypeParentItems() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
            isNull(), eq(OperationCode.CREATE))).thenReturn(true);
        when(resourceTypeOwnershipGuard.rejectIfAnySyncManagedByCodes(eq(1L), eq(Set.of("API"))))
            .thenReturn(java.util.Map.of("API", apiType()));
        // T-PERM-076：查重首查空集 + 落库后回查返回带主键新行
        when(resourceEntityMapper.selectByTypesAndCodesAndCodeTypes(eq(1L), anySet(), anySet(), anySet()))
            .thenReturn(List.of())
            .thenReturn(List.of(resourceWithTriple(71L, 3, "res-ok", "default")));
        // 旧实现会以 ("MENU","parent-x") 批量解析并拿到有效父 id → 跨类型项照常入库（本用例因此红）；
        // 新实现跨类型项被过滤出解析集 → stub 不再被消费，lenient 声明
        lenient().when(typeResolutionService.batchResolveResourceIds(eq(1L), any())).thenReturn(java.util.Map.of(
            new cn.ac.fage.accessmesh.access.resource.dto.req.ResourceResolveRequest(
                "MENU", "parent-x", null, null).toKey(), 66L));

        var resp = service.batchCreateResources(1L, java.util.List.of(
            new cn.ac.fage.accessmesh.perm.common.dto.req.ResourceCreateReq(
                null, "MENU", "parent-x", null, null, "API", "res-cross", null, "跨类型", null, null, null),
            new cn.ac.fage.accessmesh.perm.common.dto.req.ResourceCreateReq(
                null, null, null, null, null, "API", "res-ok", null, "正常", null, null, null)), 100L);

        assertEquals(1, resp.size());
        assertEquals("res-ok", resp.get(0).code());
    }

    @Test
    @DisplayName("batch-create 裸 parentId 跨类型 → 该项跳过、其余成功（裸 id 轨类型比对分支）")
    void batchCreateSkipsCrossTypeRawParentIdItems() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
            isNull(), eq(OperationCode.CREATE))).thenReturn(true);
        when(resourceTypeOwnershipGuard.rejectIfAnySyncManagedByCodes(eq(1L), eq(Set.of("API"))))
            .thenReturn(java.util.Map.of("API", apiType()));
        // T-PERM-076：查重首查空集 + 落库后回查返回带主键新行
        when(resourceEntityMapper.selectByTypesAndCodesAndCodeTypes(eq(1L), anySet(), anySet(), anySet()))
            .thenReturn(List.of())
            .thenReturn(List.of(resourceWithTriple(81L, 3, "res-ok", "default")));
        // 父实体类型=1（MENU），自身类型=3（API）→ 裸 id 轨跨类型
        when(resourceEntityDomainService.batchSelectByIdsMap(eq(1L), eq(Set.of(55L))))
            .thenReturn(java.util.Map.of(55L, resourceWithKey(55L)));
        when(typeResolutionService.batchResolveResourceIds(eq(1L), any())).thenReturn(java.util.Map.of());

        var resp = service.batchCreateResources(1L, java.util.List.of(
            new cn.ac.fage.accessmesh.perm.common.dto.req.ResourceCreateReq(
                55L, null, null, null, null, "API", "res-cross", null, "跨类型裸id", null, null, null),
            new cn.ac.fage.accessmesh.perm.common.dto.req.ResourceCreateReq(
                null, null, null, null, null, "API", "res-ok", null, "正常", null, null, null)), 100L);

        assertEquals(1, resp.size());
        assertEquals("res-ok", resp.get(0).code());
    }

    // ========== T-PERM-076：批量创建完整键查重（存量+批内）、畸形项收集与响应身份回查 ==========

    /** 批量项构造助手（仅业务键三段 + name，其余 null） */
    private static cn.ac.fage.accessmesh.perm.common.dto.req.ResourceCreateReq batchReq(
        String resourceTypeCode, String code, String codeType, String name) {
        return new cn.ac.fage.accessmesh.perm.common.dto.req.ResourceCreateReq(
            null, null, null, null, null, resourceTypeCode, code, codeType, name, null, null, null);
    }

    /** 带完整键三元组的实体（回查/存量桩共用） */
    private static ResourceEntity resourceWithTriple(Long id, int resourceType, String code, String codeType) {
        ResourceEntity e = new ResourceEntity();
        e.setId(id);
        e.setTenantId(1L);
        e.setResourceType(resourceType);
        e.setCode(code);
        e.setCodeType(codeType);
        e.setName(code);
        return e;
    }

    /** MANAGED 类型权威行（BUTTON=2/DATA=4 种子值） */
    private static cn.ac.fage.accessmesh.access.type.entity.TypeDefinition managedType(String code, int value) {
        cn.ac.fage.accessmesh.access.type.entity.TypeDefinition td =
            new cn.ac.fage.accessmesh.access.type.entity.TypeDefinition();
        td.setId((long) value);
        td.setTenantId(1L);
        td.setTypeKey("resource_type");
        td.setTypeCode(code);
        td.setTypeValue(value);
        return td;
    }

    @Test
    @DisplayName("T-PERM-076：同码跨类型/同类型跨 codeType 均可创建且响应携带回查主键（旧实现 tenant+code 查重误拒 + id 恒 null）")
    void batchCreateAllowsSameCodeAcrossTypeAndCodeType() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
            isNull(), eq(OperationCode.CREATE))).thenReturn(true);
        when(resourceTypeOwnershipGuard.rejectIfAnySyncManagedByCodes(eq(1L), eq(Set.of("BUTTON", "DATA"))))
            .thenReturn(java.util.Map.of("BUTTON", managedType("BUTTON", 2), "DATA", managedType("DATA", 4)));
        when(resourceEntityDomainService.batchSelectByIdsMap(eq(1L), any())).thenReturn(java.util.Map.of());
        // 存量：BUTTON/R076_X/default（同码不同类型）与 DATA/R076_X/default（同类型同码不同 codeType）——
        // 二者均不构成新项重复；本批新项 DATA/R076_X/custom 与 BUTTON/R076_Y/default 均应成功
        when(resourceEntityMapper.selectByTypesAndCodesAndCodeTypes(eq(1L), anySet(), anySet(), anySet()))
            .thenReturn(List.of(
                resourceWithTriple(11L, 2, "R076_X", "default"),
                resourceWithTriple(12L, 4, "R076_X", "default")))
            .thenReturn(List.of(
                resourceWithTriple(101L, 4, "R076_X", "custom"),
                resourceWithTriple(102L, 2, "R076_Y", "default")));
        when(typeResolutionService.batchResolveResourceIds(eq(1L), any())).thenReturn(java.util.Map.of());

        var resp = service.batchCreateResources(1L, List.of(
            batchReq("DATA", "R076_X", "custom", "跨codeType"),
            batchReq("BUTTON", "R076_Y", null, "跨类型")), 100L);

        assertEquals(2, resp.size());
        org.assertj.core.api.Assertions.assertThat(resp)
            .extracting(r -> r.code() + "/" + r.codeType())
            .containsExactlyInAnyOrder("R076_X/custom", "R076_Y/default");
        // 响应主键取自回查行（insertBatch 不回填；旧实现恒 null）
        assertEquals(101L, resp.stream().filter(r -> "R076_X".equals(r.code())).findFirst().orElseThrow().id());
        assertEquals(102L, resp.stream().filter(r -> "R076_Y".equals(r.code())).findFirst().orElseThrow().id());
    }

    @Test
    @DisplayName("T-PERM-076：批内同完整键重复首项胜出、后到项跳过不撞唯一索引（旧实现两项齐入 insertBatch 整批 SQL 失败）")
    void batchCreateIntraBatchDuplicateFullKeyFirstWins() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
            isNull(), eq(OperationCode.CREATE))).thenReturn(true);
        when(resourceTypeOwnershipGuard.rejectIfAnySyncManagedByCodes(eq(1L), eq(Set.of("BUTTON"))))
            .thenReturn(java.util.Map.of("BUTTON", managedType("BUTTON", 2)));
        when(resourceEntityDomainService.batchSelectByIdsMap(eq(1L), any())).thenReturn(java.util.Map.of());
        when(resourceEntityMapper.selectByTypesAndCodesAndCodeTypes(eq(1L), anySet(), anySet(), anySet()))
            .thenReturn(List.of())                                  // 存量查重：空
            .thenReturn(List.of(resourceWithTriple(201L, 2, "R076_DUP", "default"))); // 回查
        when(typeResolutionService.batchResolveResourceIds(eq(1L), any())).thenReturn(java.util.Map.of());

        var resp = service.batchCreateResources(1L, List.of(
            batchReq("BUTTON", "R076_DUP", null, "首项"),
            batchReq("BUTTON", "R076_DUP", null, "后到同键项")), 100L);

        // 旧实现：两项均通过 code 查重（互不可见）→ resp=2 且 insertBatch 带两行同键实体
        assertEquals(1, resp.size());
        assertEquals(201L, resp.get(0).id());
        org.mockito.ArgumentCaptor<List<ResourceEntity>> captor =
            org.mockito.ArgumentCaptor.forClass(List.class);
        verify(resourceEntityMapper).insertBatch(captor.capture());
        assertEquals(1, captor.getValue().size());
    }

    @Test
    @DisplayName("T-PERM-076：存量同完整键命中逐项跳过（部分成功），同批其余项不受连坐")
    void batchCreateSkipsExistingFullKeyItems() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
            isNull(), eq(OperationCode.CREATE))).thenReturn(true);
        when(resourceTypeOwnershipGuard.rejectIfAnySyncManagedByCodes(eq(1L), eq(Set.of("BUTTON"))))
            .thenReturn(java.util.Map.of("BUTTON", managedType("BUTTON", 2)));
        when(resourceEntityDomainService.batchSelectByIdsMap(eq(1L), any())).thenReturn(java.util.Map.of());
        when(resourceEntityMapper.selectByTypesAndCodesAndCodeTypes(eq(1L), anySet(), anySet(), anySet()))
            .thenReturn(List.of(resourceWithTriple(31L, 2, "R076_E", "default")))
            .thenReturn(List.of(resourceWithTriple(32L, 2, "R076_F", "default")));
        when(typeResolutionService.batchResolveResourceIds(eq(1L), any())).thenReturn(java.util.Map.of());

        var resp = service.batchCreateResources(1L, List.of(
            batchReq("BUTTON", "R076_E", null, "存量重复项"),
            batchReq("BUTTON", "R076_F", null, "正常项")), 100L);

        assertEquals(1, resp.size());
        assertEquals("R076_F", resp.get(0).code());
        assertEquals(32L, resp.get(0).id());
    }

    @Test
    @DisplayName("T-PERM-076：codeType 归一参与查重——空白归一 default 命中存量 default 行（trim 后同键即重复）")
    void batchCreateNormalizesCodeTypeForDedup() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
            isNull(), eq(OperationCode.CREATE))).thenReturn(true);
        when(resourceTypeOwnershipGuard.rejectIfAnySyncManagedByCodes(eq(1L), eq(Set.of("BUTTON"))))
            .thenReturn(java.util.Map.of("BUTTON", managedType("BUTTON", 2)));
        when(resourceEntityDomainService.batchSelectByIdsMap(eq(1L), any())).thenReturn(java.util.Map.of());
        when(resourceEntityMapper.selectByTypesAndCodesAndCodeTypes(eq(1L), anySet(), anySet(), anySet()))
            .thenReturn(List.of(resourceWithTriple(41L, 2, "R076_H", "default")))
            .thenReturn(List.of(resourceWithTriple(42L, 2, "R076_I", "default")));
        when(typeResolutionService.batchResolveResourceIds(eq(1L), any())).thenReturn(java.util.Map.of());

        var resp = service.batchCreateResources(1L, List.of(
            batchReq("BUTTON", "R076_H", "  ", "空白codeType归一default应判重"),
            batchReq("BUTTON", "R076_I", null, "正常项")), 100L);

        assertEquals(1, resp.size());
        assertEquals("R076_I", resp.get(0).code());
    }

    @Test
    @DisplayName("T-PERM-076：畸形项（code/name 空白）宽容收集跳过，不再整批 SQL 崩（2026-09-22 拍板顺手修）")
    void batchCreateSkipsMalformedItems() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
            isNull(), eq(OperationCode.CREATE))).thenReturn(true);
        when(resourceTypeOwnershipGuard.rejectIfAnySyncManagedByCodes(eq(1L), eq(Set.of("BUTTON"))))
            .thenReturn(java.util.Map.of("BUTTON", managedType("BUTTON", 2)));
        when(resourceEntityDomainService.batchSelectByIdsMap(eq(1L), any())).thenReturn(java.util.Map.of());
        when(resourceEntityMapper.selectByTypesAndCodesAndCodeTypes(eq(1L), anySet(), anySet(), anySet()))
            .thenReturn(List.of())
            .thenReturn(List.of(resourceWithTriple(51L, 2, "R076_OK", "default")));
        when(typeResolutionService.batchResolveResourceIds(eq(1L), any())).thenReturn(java.util.Map.of());

        var resp = service.batchCreateResources(1L, java.util.Arrays.asList(
            batchReq("BUTTON", null, null, "缺code"),
            batchReq("BUTTON", "   ", null, "空白code"),
            batchReq("BUTTON", "R076_N", null, null),
            batchReq("BUTTON", "R076_N2", null, "  "),
            batchReq("BUTTON", "R076_OK", null, "正常项")), 100L);

        // 旧实现：缺 code/缺 name 项原样进 insertBatch → PG NOT NULL 违例整批回滚（本用例红于 resp 与落库清单）
        assertEquals(1, resp.size());
        assertEquals("R076_OK", resp.get(0).code());
        org.mockito.ArgumentCaptor<List<ResourceEntity>> captor =
            org.mockito.ArgumentCaptor.forClass(List.class);
        verify(resourceEntityMapper).insertBatch(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals("R076_OK", captor.getValue().get(0).getCode());
    }

    @Test
    @DisplayName("T-PERM-076 双轨评审 P3-1：全批类型码 null 不再 NPE 500（Map.of().get(null) 防护），混合批 null 项宽容跳过")
    void batchCreateAllNullTypeCodeCollectedNotNpe() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
            isNull(), eq(OperationCode.CREATE))).thenReturn(true);
        // 全空类型码批：守卫入参空集——真实守卫返回 Map.of()（get(null) 即 NPE），显式桩还原生产形态
        //（mock 默认返回宽容空 Map 会掩盖旧实现的 NPE 分支，红跑判别力依赖本桩）
        when(resourceTypeOwnershipGuard.rejectIfAnySyncManagedByCodes(eq(1L), eq(Set.of())))
            .thenReturn(java.util.Map.of());
        when(resourceTypeOwnershipGuard.rejectIfAnySyncManagedByCodes(eq(1L), eq(Set.of("BUTTON"))))
            .thenReturn(java.util.Map.of("BUTTON", managedType("BUTTON", 2)));
        when(resourceEntityDomainService.batchSelectByIdsMap(eq(1L), any())).thenReturn(java.util.Map.of());
        when(resourceEntityMapper.selectByTypesAndCodesAndCodeTypes(eq(1L), anySet(), anySet(), anySet()))
            .thenReturn(List.of())
            .thenReturn(List.of(resourceWithTriple(91L, 2, "R076_J", "default")));
        when(typeResolutionService.batchResolveResourceIds(eq(1L), any())).thenReturn(java.util.Map.of());

        // 全空批：旧实现 Map.of().get(null) NPE 整批 500；新实现落「未知类型」宽容分支返回空
        var allNull = service.batchCreateResources(1L, List.of(
            batchReq(null, "R076_K", null, "类型码null"),
            batchReq("  ", "R076_K2", null, "类型码空白")), 100L);
        assertEquals(0, allNull.size());
        verify(resourceEntityMapper, org.mockito.Mockito.never()).insertBatch(org.mockito.ArgumentMatchers.anyList());

        // 混合批：同形态 null 项宽容跳过、正常项照常成功（行为一致性）
        org.mockito.Mockito.clearInvocations(resourceEntityMapper);
        var mixed = service.batchCreateResources(1L, java.util.Arrays.asList(
            batchReq(null, "R076_K3", null, "null项"),
            batchReq("BUTTON", "R076_J", null, "正常项")), 100L);
        assertEquals(1, mixed.size());
        assertEquals("R076_J", mixed.get(0).code());
    }

    @Test
    @DisplayName("T-PERM-076 claude 外评 P3-1：三元组拼接键塌缩不再误判重——(T,\"a:b\",\"c\") 与 (T,\"a\",\"b:c\") 可共存")
    void batchCreateCollapsibleTripleKeysNotDeduped() {
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.RESOURCE),
            isNull(), eq(OperationCode.CREATE))).thenReturn(true);
        when(resourceTypeOwnershipGuard.rejectIfAnySyncManagedByCodes(eq(1L), eq(Set.of("DATA"))))
            .thenReturn(java.util.Map.of("DATA", managedType("DATA", 4)));
        when(resourceEntityDomainService.batchSelectByIdsMap(eq(1L), any())).thenReturn(java.util.Map.of());
        // 存量行 (DATA,"a:b","c") 与新项 (DATA,"a","b:c") 拼接串同为 "4:a:b:c"——
        // 旧键下新项被静默判重跳过（resp=0 本用例红）；TripleKey 元组键下两键不同均可落库
        when(resourceEntityMapper.selectByTypesAndCodesAndCodeTypes(eq(1L), anySet(), anySet(), anySet()))
            .thenReturn(List.of(resourceWithTriple(31L, 4, "a:b", "c")))
            .thenReturn(List.of(resourceWithTriple(301L, 4, "a", "b:c")));
        when(typeResolutionService.batchResolveResourceIds(eq(1L), any())).thenReturn(java.util.Map.of());

        var resp = service.batchCreateResources(1L, List.of(
            batchReq("DATA", "a", "b:c", "塌缩对新项")), 100L);

        assertEquals(1, resp.size());
        assertEquals("a", resp.get(0).code());
        assertEquals("b:c", resp.get(0).codeType());
        assertEquals(301L, resp.get(0).id());
    }

    @Test
    @DisplayName("T-PERM-076 claude 外评 P3-1（remove 同根因）：请求键塌缩不把未请求行纳入删除集合")
    void deleteResourcesCollapsibleTripleKeyExcludesUnrequestedRow() {
        // 请求 (DATA,"a:b","c")；库中存在未请求行 (DATA,"a","b:c")——拼接串同为 "4:a:b:c"，
        // 旧键下未请求行被纳入删除集合并 softDelete（本用例红）；TripleKey 下精确排除、空集跳过
        ResourceEntity unrequested = resourceWithTriple(77L, 4, "a", "b:c");
        when(typeResolutionService.batchResolveTypeValues(1L, "resource_type", Set.of("DATA")))
            .thenReturn(java.util.Map.of("DATA", 4));
        when(resourceEntityMapper.selectByTypesAndCodesAndCodeTypes(eq(1L), eq(Set.of(4)), anySet(), anySet()))
            .thenReturn(List.of(unrequested));

        service.deleteResources(1L, List.of(
            new cn.ac.fage.accessmesh.perm.common.dto.req.ResourceKeyReq("DATA", "a:b", "c")), 99L);

        verify(resourceEntityDomainService, org.mockito.Mockito.never())
            .softDeleteBatch(anyLong(), any(), any());
    }
}
