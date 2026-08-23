package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.permission.dto.common.SyncVersionRef;
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
    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        AccessRequestContext.clear();
    }

    private ResourceEntitySyncAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ResourceEntitySyncAppServiceImpl(syncMetadataDomainService, syncMetadataMapper,
                typeResolutionService, resourceEntityMapper, new ObjectMapper(),
                new cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionGuard(), syncTypeGuard);
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
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), eq("RESOURCE_ENTITY"),
                eq(SOURCE_SERVICE), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), anyLong()))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.APPLIED);
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
}
