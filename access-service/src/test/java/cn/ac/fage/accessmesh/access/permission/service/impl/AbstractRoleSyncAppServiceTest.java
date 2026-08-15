package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.permission.dto.common.SyncVersionRef;
import cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleSyncReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.SyncMetadataDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.sync.SyncAuthVerifier;
import cn.ac.fage.accessmesh.access.permission.service.sync.SyncResultBuilder;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AbstractRoleSyncAppServiceImpl} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class AbstractRoleSyncAppServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final String SOURCE_SERVICE = "example-service";
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Mock
    private SyncMetadataDomainService syncMetadataDomainService;
    @Mock
    private TypeResolutionService typeResolutionService;
    @Mock
    private AbstractRoleMapper abstractRoleMapper;
    @Mock
    private HttpServletRequest httpRequest;
    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        AccessRequestContext.clear();
    }

    private AbstractRoleSyncAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AbstractRoleSyncAppServiceImpl(syncMetadataDomainService,
                typeResolutionService, abstractRoleMapper, new ObjectMapper(),
                new cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionGuard());
    }

    /**
     * UPSERT 请求，无 parent。
     */
    private AbstractRoleSyncReq upsertReqNoParent() {
        return new AbstractRoleSyncReq("UPSERT", "BASIC_ROLE", "org-100",
                "Org 100", null, null, "ROOT",
                1, 0, null,
                SOURCE_SERVICE, "org", "100",
                new SyncVersionRef(OCCURRED_AT, 1L));
    }

    /**
     * UPSERT 请求，带 parent。
     */
    private AbstractRoleSyncReq upsertReqWithParent() {
        return new AbstractRoleSyncReq("UPSERT", "BASIC_ROLE", "org-200",
                "Org 200", "BASIC_ROLE", "org-100", "ROOT",
                1, 0, null,
                SOURCE_SERVICE, "org", "200",
                new SyncVersionRef(OCCURRED_AT, 1L));
    }

    private void mockHeaderMatch() {
        AccessRequestContext.bind(RequestContext.service(TENANT_ID, SOURCE_SERVICE));
    }

    @Test
    void shouldReturnApplied_whenUpsertNewVersion() {
        mockHeaderMatch();
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "role_type", "BASIC_ROLE")).thenReturn(2);
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), eq("ABSTRACT_ROLE"),
                eq(SOURCE_SERVICE), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), eq(OCCURRED_AT), eq(1L)))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.APPLIED);
        when(abstractRoleMapper.selectByTypeAndExternalId(TENANT_ID, 2, "org-100")).thenReturn(null);
        lenient().when(abstractRoleMapper.insert(any(AbstractRole.class))).thenReturn(1);

        SyncResultResp resp = service.sync(TENANT_ID, upsertReqNoParent(), httpRequest);

        assertThat(resp.accepted()).isTrue();
        assertThat(resp.applied()).isTrue();
        assertThat(resp.stale()).isFalse();
        assertThat(resp.retryClass()).isNull();
    }

    @Test
    void shouldReturnStale_whenVersionStale() {
        mockHeaderMatch();
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "role_type", "BASIC_ROLE")).thenReturn(2);
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), eq("ABSTRACT_ROLE"),
                eq(SOURCE_SERVICE), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), anyLong()))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.STALE);

        SyncResultResp resp = service.sync(TENANT_ID, upsertReqNoParent(), httpRequest);

        assertThat(resp.accepted()).isTrue();
        assertThat(resp.applied()).isFalse();
        assertThat(resp.stale()).isTrue();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_STALE_VERSION);
    }

    @Test
    void shouldReturnDependencyMissing_whenParentRoleNotFound() {
        mockHeaderMatch();
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "role_type", "BASIC_ROLE")).thenReturn(2);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "BASIC_ROLE", "org-100", null)).thenReturn(null);

        SyncResultResp resp = service.sync(TENANT_ID, upsertReqWithParent(), httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.applied()).isFalse();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_DEPENDENCY_MISSING);
        assertThat(resp.reason()).contains("PARENT_ROLE_NOT_FOUND");
    }

    @Test
    void shouldRejectReservedOrgRoleType() {
        mockHeaderMatch();
        AbstractRoleSyncReq req = new AbstractRoleSyncReq("UPSERT", "ORG", "org-100",
                "Org 100", null, null, "ROOT",
                1, 0, null,
                SOURCE_SERVICE, "org", "100",
                new SyncVersionRef(OCCURRED_AT, 1L));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.sync(TENANT_ID, req, httpRequest))
                .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class);
    }

    @Test
    void shouldReturnSecurityDenied_whenSourceServiceMismatch() {
        AccessRequestContext.bind(RequestContext.service(TENANT_ID, "other-service"));

        SyncResultResp resp = service.sync(TENANT_ID, upsertReqNoParent(), httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.applied()).isFalse();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_SECURITY_DENIED);
    }

    @Test
    void shouldBackfillTargetId_whenUpsertCreatesNewAbstractRole() {
        mockHeaderMatch();
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "role_type", "BASIC_ROLE")).thenReturn(2);
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), eq("ABSTRACT_ROLE"),
                eq(SOURCE_SERVICE), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), eq(OCCURRED_AT), eq(1L)))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.APPLIED);
        when(abstractRoleMapper.selectByTypeAndExternalId(TENANT_ID, 2, "org-100")).thenReturn(null);
        // 模拟 mapper.insert 设置 id
        when(abstractRoleMapper.insert(any(AbstractRole.class))).thenAnswer(inv -> {
            AbstractRole r = inv.getArgument(0);
            r.setId(9999L);
            return 1;
        });

        SyncResultResp resp = service.sync(TENANT_ID, upsertReqNoParent(), httpRequest);

        assertThat(resp.applied()).isTrue();
        verify(syncMetadataDomainService).backfillTargetId(
                eq(TENANT_ID), eq("ABSTRACT_ROLE"), eq(SOURCE_SERVICE),
                anyString(), anyString(), eq(9999L));
    }
}
