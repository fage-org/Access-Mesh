package cn.ac.fage.accessmesh.access.resource.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.sync.metadata.SyncVersionRef;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntityFullSyncReq;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntitySyncItem;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntitySyncScope;
import cn.ac.fage.accessmesh.access.resource.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.access.resource.dto.req.ResourceResolveRequest;
import cn.ac.fage.accessmesh.access.sync.dto.ResourceEntitySyncReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import cn.ac.fage.accessmesh.access.resource.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.resource.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.sync.mapper.SyncMetadataMapper;
import cn.ac.fage.accessmesh.access.sync.metadata.SyncMetadataDomainService;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.type.service.domain.ResourceTypeOwnershipGuard;
import cn.ac.fage.accessmesh.access.sync.SyncAuthVerifier;
import cn.ac.fage.accessmesh.access.sync.SyncResultBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ResourceEntitySyncAppServiceImpl} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class ResourceEntitySyncAppServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final String SOURCE_SERVICE = "example-service";
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Mock
    private SyncMetadataDomainService syncMetadataDomainService;
    @Mock
    private SyncMetadataMapper syncMetadataMapper;
    @Mock
    private TypeResolutionService typeResolutionService;
    @Mock
    private ResourceEntityMapper resourceEntityMapper;
    @Mock
    private HttpServletRequest httpRequest;
    @Mock
    private ResourceTypeOwnershipGuard resourceTypeOwnershipGuard;
    @Mock
    private cn.ac.fage.accessmesh.access.resource.service.domain.ResourceEntityDomainService resourceEntityDomainService;
    @Mock
    private cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport treeWriteLockSupport;
    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        AccessRequestContext.clear();
    }

    private ResourceEntitySyncAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ResourceEntitySyncAppServiceImpl(syncMetadataDomainService, syncMetadataMapper,
                typeResolutionService, resourceEntityMapper, new ObjectMapper(),
                new cn.ac.fage.accessmesh.access.sync.guard.LocalProjectionGuard(),
                resourceTypeOwnershipGuard,
                resourceEntityDomainService, treeWriteLockSupport);
        // 默认桩：类型门禁放行（SYNC+来源匹配+服务注册启用，T-PERM-052；评审 P1 后门禁含
        // service_config 状态校验）；拒绝态用例按需覆盖为 false
        lenient().when(resourceTypeOwnershipGuard.isSyncEntranceAllowed(anyLong(), anyString(), anyString()))
                .thenReturn(true);
    }

    private ResourceEntitySyncReq upsertReq() {
        return new ResourceEntitySyncReq("UPSERT", "MENU", "menu-1", "default",
                "Menu One", null, null, null, "/menu/one", 1, null,
                SOURCE_SERVICE, "menu", "menu-1",
                new SyncVersionRef(OCCURRED_AT, 1L));
    }

    private void mockHeaderMatch() {
        AccessRequestContext.bind(RequestContext.service(TENANT_ID, SOURCE_SERVICE));
    }

    @Test
    void shouldReturnApplied_whenUpsertNewVersion() {
        mockHeaderMatch();
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), eq("RESOURCE_ENTITY"),
                eq(SOURCE_SERVICE), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), anyLong()))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.APPLIED);
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "resource_type", "MENU")).thenReturn(0);
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(TENANT_ID, 0, "menu-1", "default")).thenReturn(null);
        lenient().when(resourceEntityMapper.insert(any(ResourceEntity.class))).thenReturn(1);

        SyncResultResp resp = service.sync(TENANT_ID, upsertReq(), httpRequest);

        assertThat(resp.accepted()).isTrue();
        assertThat(resp.applied()).isTrue();
        assertThat(resp.stale()).isFalse();

        ArgumentCaptor<ResourceEntity> captor = ArgumentCaptor.forClass(ResourceEntity.class);
        verify(resourceEntityMapper).insert(captor.capture());
        assertThat(captor.getValue().getOwnerServiceCode()).isNull();
    }

    @Test
    void shouldKeepOwnerNull_whenExternalSourceService() {
        // 外部业务服务来源（sourceService 非 access-service）：所有权保持 NULL，以 sync_metadata 为准
        ResourceEntitySyncReq req = new ResourceEntitySyncReq("UPSERT", "MENU", "menu-1", "default",
                "Menu One", null, null, null, "/menu/one", 1, null,
                "example-service", "menu", "menu-1",
                new SyncVersionRef(OCCURRED_AT, 1L));
        AccessRequestContext.bind(RequestContext.service(TENANT_ID, "example-service"));
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), eq("RESOURCE_ENTITY"),
                eq("example-service"), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), anyLong()))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.APPLIED);
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "resource_type", "MENU")).thenReturn(0);
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(TENANT_ID, 0, "menu-1", "default")).thenReturn(null);
        lenient().when(resourceEntityMapper.insert(any(ResourceEntity.class))).thenReturn(1);

        SyncResultResp resp = service.sync(TENANT_ID, req, httpRequest);

        assertThat(resp.accepted()).isTrue();
        ArgumentCaptor<ResourceEntity> captor = ArgumentCaptor.forClass(ResourceEntity.class);
        verify(resourceEntityMapper).insert(captor.capture());
        assertThat(captor.getValue().getOwnerServiceCode()).isNull();
    }

    @Test
    void shouldReturnStale_whenOldVersion() {
        mockHeaderMatch();
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "resource_type", "MENU")).thenReturn(0);
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), eq("RESOURCE_ENTITY"),
                eq(SOURCE_SERVICE), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), anyLong()))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.STALE);

        SyncResultResp resp = service.sync(TENANT_ID, upsertReq(), httpRequest);

        assertThat(resp.accepted()).isTrue();
        assertThat(resp.applied()).isFalse();
        assertThat(resp.stale()).isTrue();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_STALE_VERSION);
    }

    @Test
    void shouldReturnDependencyMissing_whenParentResourceNotFound() {
        mockHeaderMatch();
        // parent 解析与判环先于 applyVersion（T-PERM-044 评审 P1 对齐角色先例：依赖缺失/环路
        // 拒绝不推进同步版本，上游修正后同版本重试不被判 STALE）——本用例不触达 applyVersion
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "resource_type", "MENU")).thenReturn(0);
        when(typeResolutionService.resolveResourceId(TENANT_ID, "MENU", "parent-x", "default", null))
                .thenReturn(null);

        ResourceEntitySyncReq req = new ResourceEntitySyncReq("UPSERT", "MENU", "menu-1", "default",
                "Menu One", "MENU", "parent-x", "default", "/menu/one", 1, null,
                SOURCE_SERVICE, "menu", "menu-1",
                new SyncVersionRef(OCCURRED_AT, 1L));

        SyncResultResp resp = service.sync(TENANT_ID, req, httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_DEPENDENCY_MISSING);
        assertThat(resp.reason()).isEqualTo("PARENT_RESOURCE_NOT_FOUND");
        org.mockito.Mockito.verify(syncMetadataDomainService, org.mockito.Mockito.never()).applyVersion(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void shouldReturnSecurityDenied_whenSourceServiceMismatch() {
        AccessRequestContext.bind(RequestContext.service(TENANT_ID, "other-service"));

        SyncResultResp resp = service.sync(TENANT_ID, upsertReq(), httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_SECURITY_DENIED);
        assertThat(resp.reason()).isEqualTo("SOURCE_SERVICE_MISMATCH");
    }

    // ------------------------------------------------------------------
    // T-PERM-052（2026-09-05 定案）：类型级所有权门禁取代 syncTypes.resourceTypeCodes
    // 白名单维度——目标类型必须声明 extra.managedMode=SYNC 且 syncSourceService==调用服务，
    // 否则 SECURITY_DENIED / RESOURCE_TYPE_OWNERSHIP_DENIED。以下用例在旧实现
    // （白名单放行后直接写库）下会因 insert/update 实际发生而失败。
    // ------------------------------------------------------------------

    @Test
    void shouldReturnSecurityDenied_whenTypeNotSyncManaged() {
        mockHeaderMatch();
        // MANAGED 类型（管理面维护/公共基础类型）——外部同步整类拒绝
        when(resourceTypeOwnershipGuard.isSyncEntranceAllowed(TENANT_ID, "MENU", SOURCE_SERVICE))
                .thenReturn(false);

        SyncResultResp resp = service.sync(TENANT_ID, upsertReq(), httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_SECURITY_DENIED);
        assertThat(resp.reason()).isEqualTo("RESOURCE_TYPE_OWNERSHIP_DENIED");
        verify(resourceEntityMapper, org.mockito.Mockito.never()).insert(any(ResourceEntity.class));
        verify(resourceEntityMapper, org.mockito.Mockito.never()).update(any(ResourceEntity.class));
        verify(resourceEntityMapper, org.mockito.Mockito.never())
                .softDeleteBatch(anyLong(), any(), any());
        // codex 复评 P1 回归锁：门禁在树锁之后（与类型声明变更/删除的行数守卫互斥），
        // 旧实现门禁在锁前
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(treeWriteLockSupport, resourceTypeOwnershipGuard);
        order.verify(treeWriteLockSupport).lockTreeWrites(TENANT_ID,
                cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
        order.verify(resourceTypeOwnershipGuard).isSyncEntranceAllowed(TENANT_ID, "MENU", SOURCE_SERVICE);
    }

    @Test
    void shouldReturnSecurityDenied_whenSourceNotTypeOwner() {
        mockHeaderMatch();
        // 类型声明 SYNC 但来源是别的服务——非声明来源不得同步（同类型单来源）
        when(resourceTypeOwnershipGuard.isSyncEntranceAllowed(TENANT_ID, "MENU", SOURCE_SERVICE))
                .thenReturn(false);

        SyncResultResp resp = service.sync(TENANT_ID, upsertReq(), httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_SECURITY_DENIED);
        assertThat(resp.reason()).isEqualTo("RESOURCE_TYPE_OWNERSHIP_DENIED");
        verify(resourceEntityMapper, org.mockito.Mockito.never()).insert(any(ResourceEntity.class));
        verify(resourceEntityMapper, org.mockito.Mockito.never()).update(any(ResourceEntity.class));
    }

    @Test
    void shouldReturnSecurityDenied_whenTypeDeclarationMissing() {
        mockHeaderMatch();
        // 类型不存在（声明缺失）fail-closed 一并拒绝
        when(resourceTypeOwnershipGuard.isSyncEntranceAllowed(TENANT_ID, "MENU", SOURCE_SERVICE))
                .thenReturn(false);

        SyncResultResp resp = service.sync(TENANT_ID, upsertReq(), httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_SECURITY_DENIED);
        assertThat(resp.reason()).isEqualTo("RESOURCE_TYPE_OWNERSHIP_DENIED");
        verify(resourceEntityMapper, org.mockito.Mockito.never()).insert(any(ResourceEntity.class));
    }

    @Test
    void fullSyncRejectsManagedType_atEntry() {
        mockHeaderMatch();
        when(resourceTypeOwnershipGuard.isSyncEntranceAllowed(TENANT_ID, "MENU", SOURCE_SERVICE))
                .thenReturn(false);

        ResourceEntityFullSyncReq req = new ResourceEntityFullSyncReq(
                new ResourceEntitySyncScope(SOURCE_SERVICE, "MENU"),
                java.util.List.of(new ResourceEntitySyncItem("menu-1", "default", "Menu One",
                        null, null, null, null, 1, null, null, null,
                        new SyncVersionRef(OCCURRED_AT, 1L))));

        SyncResultResp resp = service.fullSync(TENANT_ID, req, httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_SECURITY_DENIED);
        assertThat(resp.reason()).isEqualTo("RESOURCE_TYPE_OWNERSHIP_DENIED");
        verify(resourceEntityMapper, org.mockito.Mockito.never()).insert(any(ResourceEntity.class));
        verify(resourceEntityMapper, org.mockito.Mockito.never()).update(any(ResourceEntity.class));
        verify(resourceEntityMapper, org.mockito.Mockito.never())
                .softDeleteBatch(anyLong(), any(), any());
        // codex 二轮复评 P2-2 回归锁：fullSync 门禁同样在树锁之后（单条 sync 已钉、此处补齐；
        // 把门禁挪回锁前的旧实现下本断言失败）
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(treeWriteLockSupport, resourceTypeOwnershipGuard);
        order.verify(treeWriteLockSupport).lockTreeWrites(TENANT_ID,
                cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
        order.verify(resourceTypeOwnershipGuard).isSyncEntranceAllowed(TENANT_ID, "MENU", SOURCE_SERVICE);
    }

    // ------------------------------------------------------------------
    // T-PERM-052 内部来源统一（2026-09-05）：USER/ORG/MENU/ROLE 四类事实链路类型种子声明
    // SYNC+access-service——外部同步对这四类一律入口拒绝（来源不匹配），比对旧实现更严
    // （旧口径下外部同步 USER/MENU 类型只要不撞投影行是放行的）。原 rejectIfLocalResource
    // 行级防线（命中 owner=access-service 行 20045）已收编删除。
    // ------------------------------------------------------------------

    @Test
    void shouldReturnSecurityDenied_whenInternalSourceDeclaredType() {
        mockHeaderMatch();
        // USER 类型声明 SYNC+access-service（事实链路类型种子声明）——外部来源入口即拒
        ResourceEntitySyncReq req = new ResourceEntitySyncReq("UPSERT", "USER", "1001", "default",
                "张三", null, null, null, null, 1, null,
                SOURCE_SERVICE, "user", "1001", new SyncVersionRef(OCCURRED_AT, 1L));
        when(resourceTypeOwnershipGuard.isSyncEntranceAllowed(TENANT_ID, "USER", SOURCE_SERVICE))
                .thenReturn(false);

        SyncResultResp resp = service.sync(TENANT_ID, req, httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_SECURITY_DENIED);
        assertThat(resp.reason()).isEqualTo("RESOURCE_TYPE_OWNERSHIP_DENIED");
        // 不触达行查询与任何写路径
        verify(resourceEntityMapper, org.mockito.Mockito.never())
                .selectByTypeCodeAndCodeType(anyLong(), org.mockito.ArgumentMatchers.anyInt(),
                        anyString(), anyString());
        verify(resourceEntityMapper, org.mockito.Mockito.never()).insert(any(ResourceEntity.class));
        verify(resourceEntityMapper, org.mockito.Mockito.never()).update(any(ResourceEntity.class));
        verify(resourceEntityMapper, org.mockito.Mockito.never())
                .softDeleteBatch(anyLong(), any(), any());
    }

    @Test
    void shouldRejectCyclicParent_withoutAdvancingVersion() {
        mockHeaderMatch();
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "resource_type", "MENU")).thenReturn(0);
        // existing id=5；目标 parent id=9 是其子孙 → 判环拒绝（先于 applyVersion，不推进同步版本）
        ResourceEntity existing = new ResourceEntity();
        existing.setId(5L);
        existing.setTenantId(TENANT_ID);
        existing.setResourceType(0);
        existing.setCode("menu-1");
        existing.setCodeType("default");
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(TENANT_ID, 0, "menu-1", "default"))
                .thenReturn(existing);
        when(typeResolutionService.resolveResourceId(TENANT_ID, "MENU", "child-x", "default", null))
                .thenReturn(9L);
        when(resourceEntityDomainService.batchGetDescendantIds(TENANT_ID, java.util.Set.of(5L)))
                .thenReturn(java.util.Map.of(5L, java.util.List.of(9L)));

        ResourceEntitySyncReq req = new ResourceEntitySyncReq("UPSERT", "MENU", "menu-1", "default",
                "Menu One", "MENU", "child-x", "default", "/menu/one", 1, null,
                SOURCE_SERVICE, "menu", "menu-1",
                new SyncVersionRef(OCCURRED_AT, 1L));

        SyncResultResp resp = service.sync(TENANT_ID, req, httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_NON_RETRYABLE);
        assertThat(resp.reason()).isEqualTo("RESOURCE_PARENT_INVALID: MENU:child-x");
        // 外部同步与 moveResource 共持 (resource_entity, 租户) 树写锁（T-PERM-044 评审 P1）
        org.mockito.Mockito.verify(treeWriteLockSupport).lockTreeWrites(TENANT_ID,
                cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
        org.mockito.Mockito.verify(syncMetadataDomainService, org.mockito.Mockito.never()).applyVersion(
                anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), anyLong());
        org.mockito.Mockito.verify(resourceEntityMapper, org.mockito.Mockito.never())
                .update(org.mockito.ArgumentMatchers.any(ResourceEntity.class));
    }

    @Test
    void fullSyncRejectsCyclicParent_viaInMemoryGraph() {
        mockHeaderMatch();
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "resource_type", "MENU")).thenReturn(0);
        // 库内图：9 → 5（child-x 挂在 menu-1 下）；事件要求 menu-1 挂到 child-x 下（新边 5 → 9）
        // → 与既有 9 → 5 闭合环，拒绝该 item
        ResourceEntity existing = new ResourceEntity();
        existing.setId(5L);
        existing.setTenantId(TENANT_ID);
        existing.setResourceType(0);
        existing.setCode("menu-1");
        existing.setCodeType("default");
        existing.setParentId(null);
        ResourceEntity child = new ResourceEntity();
        child.setId(9L);
        child.setTenantId(TENANT_ID);
        child.setResourceType(0);
        child.setCode("child-x");
        child.setCodeType("default");
        child.setParentId(5L);
        when(resourceEntityMapper.selectByTypeAndCodesAndCodeTypes(
                org.mockito.ArgumentMatchers.eq(TENANT_ID), org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of(existing));
        when(resourceEntityMapper.selectAllValid(TENANT_ID))
                .thenReturn(java.util.List.of(existing, child));
        when(typeResolutionService.batchResolveResourceIds(org.mockito.ArgumentMatchers.eq(TENANT_ID),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Map.of(new ResourceResolveKey("MENU", "child-x", "default", null), 9L));

        ResourceEntityFullSyncReq req = new ResourceEntityFullSyncReq(
                new ResourceEntitySyncScope(SOURCE_SERVICE, "MENU"),
                java.util.List.of(new ResourceEntitySyncItem("menu-1", "default", "Menu One",
                        "MENU", "child-x", "default", null, 1, null, null, null,
                        new SyncVersionRef(OCCURRED_AT, 1L))));

        SyncResultResp resp = service.fullSync(TENANT_ID, req, httpRequest);

        assertThat(resp.detail()).isNotNull();
        assertThat(resp.detail().appliedCount()).isZero();
        assertThat(resp.detail().itemResults()).hasSize(1);
        assertThat(resp.detail().itemResults().get(0).applied()).isFalse();
        assertThat(resp.detail().itemResults().get(0).reason())
                .isEqualTo("RESOURCE_PARENT_INVALID: MENU:child-x");
        org.mockito.Mockito.verify(resourceEntityMapper, org.mockito.Mockito.never())
                .update(org.mockito.ArgumentMatchers.any(ResourceEntity.class));
    }

    @Test
    void fullSyncWithLegalParentAppliesUpdate_withoutPerItemDescendantQuery() {
        mockHeaderMatch();
        // 合法新 parent（不闭环）→ 通过外层内存判环、实际进入 doSyncOneInternal 执行 update——
        // 该路径下 cyclePreChecked 必须跳过内部 DB 子孙判定（无参数时每项一次递归 CTE，N+1）
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "resource_type", "MENU")).thenReturn(0);
        ResourceEntity existing = new ResourceEntity();
        existing.setId(5L);
        existing.setTenantId(TENANT_ID);
        existing.setResourceType(0);
        existing.setCode("menu-1");
        existing.setCodeType("default");
        existing.setParentId(null);
        ResourceEntity parent = new ResourceEntity();
        parent.setId(9L);
        parent.setTenantId(TENANT_ID);
        parent.setResourceType(0);
        parent.setCode("parent-x");
        parent.setCodeType("default");
        parent.setParentId(null);
        when(resourceEntityMapper.selectByTypeAndCodesAndCodeTypes(
                org.mockito.ArgumentMatchers.eq(TENANT_ID), org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of(existing));
        when(resourceEntityMapper.selectAllValid(TENANT_ID))
                .thenReturn(java.util.List.of(existing, parent));
        when(typeResolutionService.batchResolveResourceIds(org.mockito.ArgumentMatchers.eq(TENANT_ID),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Map.of(new ResourceResolveKey("MENU", "parent-x", "default", null), 9L));
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), eq("RESOURCE_ENTITY"),
                eq(SOURCE_SERVICE), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), anyLong()))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.APPLIED);
        when(syncMetadataDomainService.listScopeForFullSync(eq(TENANT_ID), eq("RESOURCE_ENTITY"),
                eq(SOURCE_SERVICE), anyString())).thenReturn(java.util.List.of());

        ResourceEntityFullSyncReq req = new ResourceEntityFullSyncReq(
                new ResourceEntitySyncScope(SOURCE_SERVICE, "MENU"),
                java.util.List.of(new ResourceEntitySyncItem("menu-1", "default", "Menu One",
                        "MENU", "parent-x", "default", null, 1, null, null, null,
                        new SyncVersionRef(OCCURRED_AT, 1L))));

        SyncResultResp resp = service.fullSync(TENANT_ID, req, httpRequest);

        assertThat(resp.detail().appliedCount()).isEqualTo(1);
        // full-sync 入口接锁（外部评审 P2：sync 与 fullSync 分别验证）
        org.mockito.Mockito.verify(treeWriteLockSupport).lockTreeWrites(TENANT_ID,
                cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport.TreeLockTarget.RESOURCE_ENTITY);
        // N+1 回归锁：项通过外层判环并执行 update，doSyncOneInternal 内的 DB 子孙查询判定
        // 不得触发（撤销 cyclePreChecked 时本断言失败）
        org.mockito.Mockito.verify(resourceEntityDomainService, org.mockito.Mockito.never())
                .batchGetDescendantIds(org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(resourceEntityMapper)
                .update(org.mockito.ArgumentMatchers.any(ResourceEntity.class));
    }

    // ------------------------------------------------------------------
    // T-PERM-068（Q-007 定案①③，2026-09-17）：sync 通道跨类型父边收紧（NON_RETRYABLE
    // PARENT_TYPE_MISMATCH）+ 父字段缺省回填同类型（parentResourceTypeCode 缺省=item/scope
    // 类型，兑现契约 §19.2 原意与角色域 effectiveParentTypeCode 先例）。以下用例在旧实现
    // （父类型不比对、半传静默解挂）下失败。
    // ------------------------------------------------------------------

    @Test
    void shouldRejectCrossTypeParent_withoutResolvingOrAdvancingVersion() {
        mockHeaderMatch();
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "resource_type", "MENU")).thenReturn(0);

        // parentResourceTypeCode=BUTTON 显式异类型 → item 级拒绝，先于父解析与版本写入
        ResourceEntitySyncReq req = new ResourceEntitySyncReq("UPSERT", "MENU", "menu-1", "default",
                "Menu One", "BUTTON", "btn-1", "default", "/menu/one", 1, null,
                SOURCE_SERVICE, "menu", "menu-1", new SyncVersionRef(OCCURRED_AT, 1L));

        SyncResultResp resp = service.sync(TENANT_ID, req, httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_NON_RETRYABLE);
        assertThat(resp.reason()).isEqualTo("PARENT_TYPE_MISMATCH: BUTTON:btn-1");
        verify(typeResolutionService, org.mockito.Mockito.never())
                .resolveResourceId(anyLong(), anyString(), anyString(), anyString(), any());
        org.mockito.Mockito.verify(syncMetadataDomainService, org.mockito.Mockito.never()).applyVersion(
                anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), anyLong());
        verify(resourceEntityMapper, org.mockito.Mockito.never()).insert(any(ResourceEntity.class));
        verify(resourceEntityMapper, org.mockito.Mockito.never()).update(any(ResourceEntity.class));
    }

    @Test
    void shouldResolveParentByOwnType_whenParentTypeCodeOmitted() {
        mockHeaderMatch();
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "resource_type", "MENU")).thenReturn(0);
        when(typeResolutionService.resolveResourceId(TENANT_ID, "MENU", "parent-x", "default", null))
                .thenReturn(88L);
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(TENANT_ID, 0, "menu-1", "default"))
                .thenReturn(null);
        lenient().when(resourceEntityMapper.insert(any(ResourceEntity.class))).thenReturn(1);
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), eq("RESOURCE_ENTITY"),
                eq(SOURCE_SERVICE), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), anyLong()))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.APPLIED);

        // parentResourceTypeCode 缺省 → 按 item 自身类型 MENU 解析挂父（旧实现半传被静默解挂）
        ResourceEntitySyncReq req = new ResourceEntitySyncReq("UPSERT", "MENU", "menu-1", "default",
                "Menu One", null, "parent-x", "default", "/menu/one", 1, null,
                SOURCE_SERVICE, "menu", "menu-1", new SyncVersionRef(OCCURRED_AT, 1L));

        SyncResultResp resp = service.sync(TENANT_ID, req, httpRequest);

        assertThat(resp.applied()).isTrue();
        ArgumentCaptor<ResourceEntity> captor = ArgumentCaptor.forClass(ResourceEntity.class);
        verify(resourceEntityMapper).insert(captor.capture());
        assertThat(captor.getValue().getParentId()).isEqualTo(88L);
    }

    @Test
    void fullSyncRejectsCrossTypeParentItem_butAppliesOthers() {
        mockHeaderMatch();
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "resource_type", "MENU")).thenReturn(0);
        ResourceEntity existing = new ResourceEntity();
        existing.setId(5L);
        existing.setTenantId(TENANT_ID);
        existing.setResourceType(0);
        existing.setCode("menu-1");
        existing.setCodeType("default");
        when(resourceEntityMapper.selectByTypeAndCodesAndCodeTypes(
                org.mockito.ArgumentMatchers.eq(TENANT_ID), org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of(existing));
        // 跨类型项不参与批量父解析（合法项无父 → 请求集为空）
        lenient().when(typeResolutionService.batchResolveResourceIds(
                org.mockito.ArgumentMatchers.eq(TENANT_ID), org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Map.of());
        lenient().when(resourceEntityMapper.selectAllValid(TENANT_ID))
                .thenReturn(java.util.List.of(existing));
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), eq("RESOURCE_ENTITY"),
                eq(SOURCE_SERVICE), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), anyLong()))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.APPLIED);
        when(syncMetadataDomainService.listScopeForFullSync(eq(TENANT_ID), eq("RESOURCE_ENTITY"),
                eq(SOURCE_SERVICE), anyString())).thenReturn(java.util.List.of());

        ResourceEntityFullSyncReq req = new ResourceEntityFullSyncReq(
                new ResourceEntitySyncScope(SOURCE_SERVICE, "MENU"),
                java.util.List.of(
                        new ResourceEntitySyncItem("menu-0", "default", "Cross",
                                "BUTTON", "btn-1", "default", null, 1, null, null, null,
                                new SyncVersionRef(OCCURRED_AT, 1L)),
                        new ResourceEntitySyncItem("menu-1", "default", "Menu One",
                                null, null, null, null, 1, null, null, null,
                                new SyncVersionRef(OCCURRED_AT, 2L))));

        SyncResultResp resp = service.fullSync(TENANT_ID, req, httpRequest);

        assertThat(resp.detail().appliedCount()).isEqualTo(1);
        assertThat(resp.detail().itemResults()).hasSize(2);
        assertThat(resp.detail().itemResults().get(0).applied()).isFalse();
        assertThat(resp.detail().itemResults().get(0).retryClass())
                .isEqualTo(SyncResultBuilder.RETRY_NON_RETRYABLE);
        assertThat(resp.detail().itemResults().get(0).reason())
                .isEqualTo("PARENT_TYPE_MISMATCH: BUTTON:btn-1");
        assertThat(resp.detail().itemResults().get(1).applied()).isTrue();
        // 跨类型 item 不推进版本：applyVersion 仅同批合法项调用一次
        org.mockito.Mockito.verify(syncMetadataDomainService, org.mockito.Mockito.times(1)).applyVersion(
                anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), anyLong());
    }

    @Test
    void shouldTreatParentAsAbsent_whenOnlyParentTypeCodeProvided() {
        mockHeaderMatch();
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "resource_type", "MENU")).thenReturn(0);
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(TENANT_ID, 0, "menu-1", "default"))
                .thenReturn(null);
        lenient().when(resourceEntityMapper.insert(any(ResourceEntity.class))).thenReturn(1);
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), eq("RESOURCE_ENTITY"),
                eq(SOURCE_SERVICE), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), anyLong()))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.APPLIED);

        // 只有 parentResourceTypeCode、无 parentResourceCode → 父字段组不激活（激活条件=code 非空，
        // 防未来回改为「任一非空」的语义钉子）
        ResourceEntitySyncReq req = new ResourceEntitySyncReq("UPSERT", "MENU", "menu-1", "default",
                "Menu One", "MENU", null, null, "/menu/one", 1, null,
                SOURCE_SERVICE, "menu", "menu-1", new SyncVersionRef(OCCURRED_AT, 1L));

        SyncResultResp resp = service.sync(TENANT_ID, req, httpRequest);

        assertThat(resp.applied()).isTrue();
        verify(typeResolutionService, org.mockito.Mockito.never())
                .resolveResourceId(anyLong(), anyString(), anyString(), anyString(), any());
        ArgumentCaptor<ResourceEntity> captor = ArgumentCaptor.forClass(ResourceEntity.class);
        verify(resourceEntityMapper).insert(captor.capture());
        assertThat(captor.getValue().getParentId()).isNull();
    }

    // ------------------------------------------------------------------
    // 外评处置回归锁（2026-09-18，claude/grok 双通道）：①父字段组仅 UPSERT 生效——
    // DISABLE/DELETE 忽略父字段（用户拍板）；②DELETE 有有效后代 → CHILDREN_EXIST 可重试拒绝
    // （用户拍板，先于 applyVersion）；③解挂（全不传父字段 UPSERT 已存在行）必须 UpdateEntity
    // 显式清 parent 列（grok P2——flex update(entity) 忽略 null 列，旧父残留 fail-open）。
    // ①③在处置前实现下失败；②拒绝/自愈两态分别锁定。
    // ------------------------------------------------------------------

    @Test
    void shouldIgnoreParentFields_whenDisableOperation() {
        mockHeaderMatch();
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "resource_type", "MENU")).thenReturn(0);
        ResourceEntity existing = new ResourceEntity();
        existing.setId(5L);
        existing.setTenantId(TENANT_ID);
        existing.setResourceType(0);
        existing.setCode("menu-1");
        existing.setCodeType("default");
        existing.setStatus(1);
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(TENANT_ID, 0, "menu-1", "default"))
                .thenReturn(existing);
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), eq("RESOURCE_ENTITY"),
                eq(SOURCE_SERVICE), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), anyLong()))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.APPLIED);

        // DISABLE 携带父字段（父不存在也不得阻塞停用——父字段仅 UPSERT 生效）
        ResourceEntitySyncReq req = new ResourceEntitySyncReq("DISABLE", "MENU", "menu-1", "default",
                null, "MENU", "ghost-parent", "default", null, null, null,
                SOURCE_SERVICE, "menu", "menu-1", new SyncVersionRef(OCCURRED_AT, 1L));

        SyncResultResp resp = service.sync(TENANT_ID, req, httpRequest);

        assertThat(resp.applied()).isTrue();
        verify(typeResolutionService, org.mockito.Mockito.never())
                .resolveResourceId(anyLong(), anyString(), anyString(), anyString(), any());
        verify(resourceEntityMapper).update(any(ResourceEntity.class));
        assertThat(existing.getStatus()).isEqualTo(0);
    }

    @Test
    void shouldRejectDeleteWithoutAdvancingVersion_whenValidDescendantsExist() {
        mockHeaderMatch();
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "resource_type", "MENU")).thenReturn(0);
        ResourceEntity existing = new ResourceEntity();
        existing.setId(5L);
        existing.setTenantId(TENANT_ID);
        existing.setResourceType(0);
        existing.setCode("menu-1");
        existing.setCodeType("default");
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(TENANT_ID, 0, "menu-1", "default"))
                .thenReturn(existing);
        when(resourceEntityDomainService.batchGetDescendantIds(TENANT_ID, java.util.Set.of(5L)))
                .thenReturn(java.util.Map.of(5L, java.util.List.of(9L)));

        ResourceEntitySyncReq req = new ResourceEntitySyncReq("DELETE", "MENU", "menu-1", "default",
                null, null, null, null, null, null, null,
                SOURCE_SERVICE, "menu", "menu-1", new SyncVersionRef(OCCURRED_AT, 1L));

        SyncResultResp resp = service.sync(TENANT_ID, req, httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_DEPENDENCY_MISSING);
        assertThat(resp.reason()).isEqualTo("CHILDREN_EXIST");
        // 拒绝先于 applyVersion：子删除后同版本重发自愈，不被 STALE 挡
        org.mockito.Mockito.verify(syncMetadataDomainService, org.mockito.Mockito.never()).applyVersion(
                anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), anyLong());
        verify(resourceEntityMapper, org.mockito.Mockito.never())
                .softDeleteBatch(anyLong(), any(), any());
    }

    @Test
    void shouldDelete_whenNoValidDescendants() {
        mockHeaderMatch();
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "resource_type", "MENU")).thenReturn(0);
        ResourceEntity existing = new ResourceEntity();
        existing.setId(5L);
        existing.setTenantId(TENANT_ID);
        existing.setResourceType(0);
        existing.setCode("menu-1");
        existing.setCodeType("default");
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(TENANT_ID, 0, "menu-1", "default"))
                .thenReturn(existing);
        when(resourceEntityDomainService.batchGetDescendantIds(TENANT_ID, java.util.Set.of(5L)))
                .thenReturn(java.util.Map.of());
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), eq("RESOURCE_ENTITY"),
                eq(SOURCE_SERVICE), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), anyLong()))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.APPLIED);

        ResourceEntitySyncReq req = new ResourceEntitySyncReq("DELETE", "MENU", "menu-1", "default",
                null, null, null, null, null, null, null,
                SOURCE_SERVICE, "menu", "menu-1", new SyncVersionRef(OCCURRED_AT, 1L));

        SyncResultResp resp = service.sync(TENANT_ID, req, httpRequest);

        assertThat(resp.applied()).isTrue();
        verify(resourceEntityMapper).softDeleteBatch(eq(TENANT_ID), eq(java.util.List.of(5L)), any());
    }

    @Test
    void shouldForceNullParentColumn_whenUpsertWithoutParentFieldsOnExistingRow() {
        mockHeaderMatch();
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "resource_type", "MENU")).thenReturn(0);
        ResourceEntity existing = new ResourceEntity();
        existing.setId(5L);
        existing.setTenantId(TENANT_ID);
        existing.setResourceType(0);
        existing.setCode("menu-1");
        existing.setCodeType("default");
        existing.setParentId(9L);
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(TENANT_ID, 0, "menu-1", "default"))
                .thenReturn(existing);
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), eq("RESOURCE_ENTITY"),
                eq(SOURCE_SERVICE), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), anyLong()))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.APPLIED);

        // 全不传父字段（契约 §19.1 解挂形态）：已存在行必须显式清 parent 列——只断言 Java 字段
        // null 在旧实现（plain update(entity)）下同样通过，须断言 updates map 显式含 parentId=null
        SyncResultResp resp = service.sync(TENANT_ID, upsertReq(), httpRequest);

        assertThat(resp.applied()).isTrue();
        ArgumentCaptor<ResourceEntity> captor = ArgumentCaptor.forClass(ResourceEntity.class);
        verify(resourceEntityMapper).update(captor.capture());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> updates =
                ((com.mybatisflex.core.update.UpdateWrapper<ResourceEntity>) captor.getValue()).getUpdates();
        assertThat(updates).containsKey("parentId");
        assertThat(updates.get("parentId")).isNull();
        assertThat(captor.getValue().getId()).isEqualTo(5L);
    }

    @Test
    void fullSyncResolvesParentByScopeType_whenParentTypeCodeOmitted() {
        mockHeaderMatch();
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "resource_type", "MENU")).thenReturn(0);
        ResourceEntity existing = new ResourceEntity();
        existing.setId(5L);
        existing.setTenantId(TENANT_ID);
        existing.setResourceType(0);
        existing.setCode("menu-1");
        existing.setCodeType("default");
        existing.setParentId(null);
        ResourceEntity parent = new ResourceEntity();
        parent.setId(9L);
        parent.setTenantId(TENANT_ID);
        parent.setResourceType(0);
        parent.setCode("parent-x");
        parent.setCodeType("default");
        when(resourceEntityMapper.selectByTypeAndCodesAndCodeTypes(
                org.mockito.ArgumentMatchers.eq(TENANT_ID), org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of(existing));
        lenient().when(resourceEntityMapper.selectAllValid(TENANT_ID))
                .thenReturn(java.util.List.of(existing, parent));
        lenient().when(typeResolutionService.batchResolveResourceIds(
                org.mockito.ArgumentMatchers.eq(TENANT_ID), org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Map.of(new ResourceResolveKey("MENU", "parent-x", "default", null), 9L));
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), eq("RESOURCE_ENTITY"),
                eq(SOURCE_SERVICE), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), anyLong()))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.APPLIED);
        when(syncMetadataDomainService.listScopeForFullSync(eq(TENANT_ID), eq("RESOURCE_ENTITY"),
                eq(SOURCE_SERVICE), anyString())).thenReturn(java.util.List.of());

        // parentResourceTypeCode 缺省 → 按 scope 类型 MENU 解析挂父（旧实现半传被静默解挂）
        ResourceEntityFullSyncReq req = new ResourceEntityFullSyncReq(
                new ResourceEntitySyncScope(SOURCE_SERVICE, "MENU"),
                java.util.List.of(new ResourceEntitySyncItem("menu-1", "default", "Menu One",
                        null, "parent-x", "default", null, 1, null, null, null,
                        new SyncVersionRef(OCCURRED_AT, 1L))));

        SyncResultResp resp = service.fullSync(TENANT_ID, req, httpRequest);

        assertThat(resp.detail().appliedCount()).isEqualTo(1);
        ArgumentCaptor<ResourceEntity> captor = ArgumentCaptor.forClass(ResourceEntity.class);
        verify(resourceEntityMapper).update(captor.capture());
        assertThat(captor.getValue().getParentId()).isEqualTo(9L);
    }
}
