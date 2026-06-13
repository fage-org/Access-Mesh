package cn.ac.fage.accessmesh.permission.service.impl;

import cn.ac.fage.accessmesh.permission.dto.common.SyncVersionRef;
import cn.ac.fage.accessmesh.permission.dto.req.ResourceEntitySyncReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import cn.ac.fage.accessmesh.permission.entity.ResourceEntity;
import cn.ac.fage.accessmesh.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.permission.mapper.SyncMetadataMapper;
import cn.ac.fage.accessmesh.permission.service.domain.SyncMetadataDomainService;
import cn.ac.fage.accessmesh.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.permission.service.sync.SyncAuthVerifier;
import cn.ac.fage.accessmesh.permission.service.sync.SyncResultBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * {@link ResourceEntitySyncAppServiceImpl} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class ResourceEntitySyncAppServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final String SOURCE_SERVICE = "admin-service";
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

    private ResourceEntitySyncAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ResourceEntitySyncAppServiceImpl(syncMetadataDomainService, syncMetadataMapper,
                typeResolutionService, resourceEntityMapper, new ObjectMapper());
    }

    private ResourceEntitySyncReq upsertReq() {
        return new ResourceEntitySyncReq("UPSERT", "MENU", "menu-1", "default",
                "Menu One", null, null, null, "/menu/one", 1, 0, null,
                SOURCE_SERVICE, "menu", "menu-1",
                new SyncVersionRef(OCCURRED_AT, 1L));
    }

    private void mockHeaderMatch() {
        when(httpRequest.getHeader(SyncAuthVerifier.HEADER_SERVICE_CODE)).thenReturn(SOURCE_SERVICE);
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
    }

    @Test
    void shouldReturnStale_whenOldVersion() {
        mockHeaderMatch();
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
        when(httpRequest.getHeader(SyncAuthVerifier.HEADER_SERVICE_CODE)).thenReturn("other-service");

        SyncResultResp resp = service.sync(TENANT_ID, upsertReq(), httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_SECURITY_DENIED);
        assertThat(resp.reason()).isEqualTo("SOURCE_SERVICE_MISMATCH");
    }
}
