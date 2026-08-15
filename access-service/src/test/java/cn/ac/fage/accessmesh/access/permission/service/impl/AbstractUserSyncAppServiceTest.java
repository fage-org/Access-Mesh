package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.permission.dto.common.SyncVersionRef;
import cn.ac.fage.accessmesh.access.permission.dto.req.AbstractUserSyncReq;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractUser;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractUserMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AbstractUserSyncAppServiceImpl} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class AbstractUserSyncAppServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final String SOURCE_SERVICE = "example-service";
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Mock
    private SyncMetadataDomainService syncMetadataDomainService;
    @Mock
    private TypeResolutionService typeResolutionService;
    @Mock
    private AbstractUserMapper abstractUserMapper;
    @Mock
    private HttpServletRequest httpRequest;
    @Mock
    private SyncTypeGuard syncTypeGuard;
    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        AccessRequestContext.clear();
    }

    private AbstractUserSyncAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AbstractUserSyncAppServiceImpl(syncMetadataDomainService,
                typeResolutionService, abstractUserMapper, new ObjectMapper(), new cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionGuard(), syncTypeGuard);

        org.mockito.Mockito.lenient().when(syncTypeGuard.validate(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
    }

    private AbstractUserSyncReq upsertReq() {
        return new AbstractUserSyncReq("UPSERT", "USER", "u1",
                "User One", Boolean.TRUE, null,
                SOURCE_SERVICE, "user", "u1",
                new SyncVersionRef(OCCURRED_AT, 1L));
    }

    private void mockHeaderMatch() {
        AccessRequestContext.bind(RequestContext.service(TENANT_ID, SOURCE_SERVICE));
    }

    @Test
    void shouldReturnApplied_whenUpsertNewVersion() {
        mockHeaderMatch();
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "user_type", "USER")).thenReturn(0);
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), eq("ABSTRACT_USER"),
                eq(SOURCE_SERVICE), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), eq(OCCURRED_AT), eq(1L)))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.APPLIED);
        when(abstractUserMapper.selectByTypeAndExternalId(TENANT_ID, 0, "u1")).thenReturn(null);
        lenient().when(abstractUserMapper.insert(any(AbstractUser.class))).thenReturn(1);

        SyncResultResp resp = service.sync(TENANT_ID, upsertReq(), httpRequest);

        assertThat(resp.accepted()).isTrue();
        assertThat(resp.applied()).isTrue();
        assertThat(resp.stale()).isFalse();
        assertThat(resp.retryClass()).isNull();
    }

    @Test
    void shouldReturnStale_whenVersionStale() {
        mockHeaderMatch();
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "user_type", "USER")).thenReturn(0);
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), eq("ABSTRACT_USER"),
                eq(SOURCE_SERVICE), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), anyLong()))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.STALE);

        SyncResultResp resp = service.sync(TENANT_ID, upsertReq(), httpRequest);

        assertThat(resp.accepted()).isTrue();
        assertThat(resp.applied()).isFalse();
        assertThat(resp.stale()).isTrue();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_STALE_VERSION);
        assertThat(resp.reason()).isEqualTo(SyncResultBuilder.REASON_STALE);
    }

    @Test
    void shouldRejectInternalSourceService() {
        AbstractUserSyncReq req = new AbstractUserSyncReq("UPSERT", "USER", "u1",
                "User One", Boolean.TRUE, null,
                "admin-service", "user", "u1",
                new SyncVersionRef(OCCURRED_AT, 1L));
        AccessRequestContext.bind(RequestContext.service(TENANT_ID, "admin-service"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.sync(TENANT_ID, req, httpRequest))
                .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class);
    }

    @Test
    void shouldReturnSecurityDenied_whenSourceServiceMismatch() {
        AccessRequestContext.bind(RequestContext.service(TENANT_ID, "other-service"));

        SyncResultResp resp = service.sync(TENANT_ID, upsertReq(), httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.applied()).isFalse();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_SECURITY_DENIED);
    }

    @Test
    void shouldReturnNonRetryable_whenInvalidOperation() {
        mockHeaderMatch();
        AbstractUserSyncReq req = new AbstractUserSyncReq("REPLACE", "USER", "u1",
                "User One", Boolean.TRUE, null,
                SOURCE_SERVICE, null, null,
                new SyncVersionRef(OCCURRED_AT, 1L));

        SyncResultResp resp = service.sync(TENANT_ID, req, httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_NON_RETRYABLE);
        assertThat(resp.reason()).contains("invalid operation");
    }

    @Test
    void shouldBackfillTargetId_whenUpsertCreatesNewAbstractUser() {
        mockHeaderMatch();
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "user_type", "USER")).thenReturn(0);
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), eq("ABSTRACT_USER"),
                eq(SOURCE_SERVICE), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), eq(OCCURRED_AT), eq(1L)))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.APPLIED);
        when(abstractUserMapper.selectByTypeAndExternalId(TENANT_ID, 0, "u1")).thenReturn(null);
        // 模拟 mapper.insert 在传入实体上设置 id（MyBatis-Flex 行为）
        when(abstractUserMapper.insert(any(AbstractUser.class))).thenAnswer(inv -> {
            AbstractUser u = inv.getArgument(0);
            u.setId(8888L);
            return 1;
        });

        SyncResultResp resp = service.sync(TENANT_ID, upsertReq(), httpRequest);

        assertThat(resp.applied()).isTrue();
        verify(syncMetadataDomainService).backfillTargetId(
                eq(TENANT_ID), eq("ABSTRACT_USER"), eq(SOURCE_SERVICE),
                anyString(), anyString(), eq(8888L));
    }
}
