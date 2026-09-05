package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.permission.dto.common.SyncVersionRef;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceEntityFullSyncReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceEntitySyncItem;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceEntitySyncScope;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveKey;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceResolveRequest;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceEntitySyncReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import cn.ac.fage.accessmesh.access.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.SyncMetadataMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.SyncMetadataDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.sync.SyncAuthVerifier;
import cn.ac.fage.accessmesh.access.permission.service.sync.SyncResultBuilder;
import cn.ac.fage.accessmesh.access.permission.service.domain.SyncTypeGuard;
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
    private SyncTypeGuard syncTypeGuard;
    @Mock
    private cn.ac.fage.accessmesh.access.permission.service.domain.ResourceEntityDomainService resourceEntityDomainService;
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
                new cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionGuard(), syncTypeGuard,
                resourceEntityDomainService, treeWriteLockSupport);
        org.mockito.Mockito.lenient().when(syncTypeGuard.validate(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
    }

    private ResourceEntitySyncReq upsertReq() {
        return new ResourceEntitySyncReq("UPSERT", "MENU", "menu-1", "default",
                "Menu One", null, null, null, "/menu/one", 1, 0, null,
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
                "Menu One", null, null, null, "/menu/one", 1, 0, null,
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
                "Menu One", "MENU", "parent-x", "default", "/menu/one", 1, 0, null,
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
    // T-ACCESS-018：resource 侧取消类型级保留后，本地投影行的保护按所有权检查——
    // 外部 sync 的 UPSERT/DISABLE/DELETE 任一 mutation 命中 owner=access-service
    // 实体即整体拒绝（BizException 20045，事务回滚）。MENU 是公共基础类型，
    // 本组用例证明保护与类型无关、只与所有权相关。
    // ------------------------------------------------------------------

    private ResourceEntity localProjectionRow() {
        ResourceEntity existing = new ResourceEntity();
        existing.setId(9001L);
        existing.setTenantId(TENANT_ID);
        existing.setResourceType(0);
        existing.setCode("menu-1");
        existing.setCodeType("default");
        existing.setName("Menu One");
        existing.setOwnerServiceCode("access-service");
        return existing;
    }

    private void stubExistingLocalProjectionRow() {
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "resource_type", "MENU")).thenReturn(0);
        when(resourceEntityMapper.selectByTypeCodeAndCodeType(TENANT_ID, 0, "menu-1", "default"))
                .thenReturn(localProjectionRow());
    }

    @Test
    void shouldRejectUpsert_whenHittingLocalProjectionRow() {
        mockHeaderMatch();
        stubExistingLocalProjectionRow();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.sync(TENANT_ID, upsertReq(), httpRequest))
                .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class)
                .hasMessageContaining("access-service")
                .extracting("errorCode")
                .isEqualTo(20045);
        verify(resourceEntityMapper, org.mockito.Mockito.never()).update(any(ResourceEntity.class));
    }

    @Test
    void shouldRejectDisable_whenHittingLocalProjectionRow() {
        mockHeaderMatch();
        stubExistingLocalProjectionRow();
        ResourceEntitySyncReq req = new ResourceEntitySyncReq("DISABLE", "MENU", "menu-1", "default",
                null, null, null, null, null, 0, 0, null,
                SOURCE_SERVICE, "menu", "menu-1", new SyncVersionRef(OCCURRED_AT, 1L));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.sync(TENANT_ID, req, httpRequest))
                .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class)
                .extracting("errorCode")
                .isEqualTo(20045);
        verify(resourceEntityMapper, org.mockito.Mockito.never()).update(any(ResourceEntity.class));
    }

    @Test
    void shouldRejectDelete_whenHittingLocalProjectionRow() {
        mockHeaderMatch();
        stubExistingLocalProjectionRow();
        ResourceEntitySyncReq req = new ResourceEntitySyncReq("DELETE", "MENU", "menu-1", "default",
                null, null, null, null, null, null, null, null,
                SOURCE_SERVICE, "menu", "menu-1", new SyncVersionRef(OCCURRED_AT, 1L));

        // DELETE 分支现状直接软删命中实体——所有权检查必须在其之前拦截（architecture §4.3）
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.sync(TENANT_ID, req, httpRequest))
                .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class)
                .extracting("errorCode")
                .isEqualTo(20045);
        verify(resourceEntityMapper, org.mockito.Mockito.never())
                .softDeleteBatch(anyLong(), any(), any());
    }

    @Test
    void shouldRejectLocalRow_beforeVersionMetadataAndDependencyReturns() {
        // 评审 P2：所有权是安全边界，必须先于 applyVersion（外部 sync_metadata 持久化副作用）
        // 与 parent 缺失的 dependencyMissing 提前返回——否则命中本地行的 UPSERT 可携带
        // 无效 parent 绕过 20045 并留下悬挂的外部同步元数据
        mockHeaderMatch();
        stubExistingLocalProjectionRow();
        ResourceEntitySyncReq req = new ResourceEntitySyncReq("UPSERT", "MENU", "menu-1", "default",
                "Menu One", "MENU", "parent-not-exist", "default", "/menu/one", 1, 0, null,
                SOURCE_SERVICE, "menu", "menu-1", new SyncVersionRef(OCCURRED_AT, 1L));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.sync(TENANT_ID, req, httpRequest))
                .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class)
                .extracting("errorCode")
                .isEqualTo(20045);
        // 不持久化外部 sync_metadata、不做 parent 解析（20045 优先级最高）
        verify(syncMetadataDomainService, org.mockito.Mockito.never()).applyVersion(
                anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), anyLong());
        verify(typeResolutionService, org.mockito.Mockito.never())
                .resolveResourceId(anyLong(), anyString(), anyString(), anyString(), any());
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
                "Menu One", "MENU", "child-x", "default", "/menu/one", 1, 0, null,
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
                        "MENU", "child-x", "default", null, 1, 0, null, null, null,
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
                        "MENU", "parent-x", "default", null, 1, 0, null, null, null,
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
}
