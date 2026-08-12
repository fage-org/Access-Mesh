package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.permission.dto.common.SyncVersionRef;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleFullSyncReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleSyncItem;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleSyncReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleSyncScope;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import cn.ac.fage.accessmesh.access.permission.entity.UserRole;
import cn.ac.fage.accessmesh.access.permission.mapper.UserRoleMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.SyncMetadataDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.sync.SyncAuthVerifier;
import cn.ac.fage.accessmesh.access.permission.service.sync.SyncResultBuilder;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * {@link UserRoleSyncAppServiceImpl} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class UserRoleSyncAppServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final String SOURCE_SERVICE = "admin-service";
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Mock
    private SyncMetadataDomainService syncMetadataDomainService;
    @Mock
    private TypeResolutionService typeResolutionService;
    @Mock
    private UserRoleMapper userRoleMapper;
    @Mock
    private HttpServletRequest httpRequest;

    private UserRoleSyncAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserRoleSyncAppServiceImpl(syncMetadataDomainService,
                typeResolutionService, userRoleMapper);
    }

    private UserRoleSyncReq bindReq() {
        return new UserRoleSyncReq("BIND", "SYS_USER_ORG",
                "USER", "u1", "ORG", "1", "org-100", "ORG:org-200",
                null, null, SOURCE_SERVICE, "user", "u1",
                new SyncVersionRef(OCCURRED_AT, 1L));
    }

    private void mockHeaderMatch() {
        when(httpRequest.getHeader(SyncAuthVerifier.HEADER_SERVICE_CODE)).thenReturn(SOURCE_SERVICE);
    }

    @Test
    void shouldReturnApplied_whenBindNewVersion() {
        mockHeaderMatch();
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), eq("USER_ROLE"),
                eq(SOURCE_SERVICE), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), eq(OCCURRED_AT), eq(1L)))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.APPLIED);
        when(typeResolutionService.resolveUserId(TENANT_ID, "USER", "u1")).thenReturn(10L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "ORG", "org-100", null)).thenReturn(20L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "ORG", "org-200", null)).thenReturn(30L);
        when(userRoleMapper.selectOneByQuery(any())).thenReturn(null);
        lenient().when(userRoleMapper.insert(any(UserRole.class))).thenReturn(1);

        SyncResultResp resp = service.sync(TENANT_ID, bindReq(), httpRequest);

        assertThat(resp.accepted()).isTrue();
        assertThat(resp.applied()).isTrue();
        assertThat(resp.stale()).isFalse();
        assertThat(resp.retryClass()).isNull();
    }

    @Test
    void shouldReturnApplied_whenUnbindExisting() {
        mockHeaderMatch();
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), eq("USER_ROLE"),
                eq(SOURCE_SERVICE), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), anyLong()))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.APPLIED);
        when(typeResolutionService.resolveUserId(TENANT_ID, "USER", "u1")).thenReturn(10L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "ORG", "org-100", null)).thenReturn(20L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "ORG", "org-200", null)).thenReturn(30L);
        UserRole existing = new UserRole();
        existing.setId(99L);
        when(userRoleMapper.selectOneByQuery(any())).thenReturn(existing);

        UserRoleSyncReq req = new UserRoleSyncReq("UNBIND", "SYS_USER_ORG",
                "USER", "u1", "ORG", "1", "org-100", "ORG:org-200",
                null, null, SOURCE_SERVICE, "user", "u1",
                new SyncVersionRef(OCCURRED_AT, 1L));
        SyncResultResp resp = service.sync(TENANT_ID, req, httpRequest);

        assertThat(resp.applied()).isTrue();
        assertThat(resp.accepted()).isTrue();
    }

    @Test
    void shouldReturnStale_whenVersionStale() {
        mockHeaderMatch();
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), eq("USER_ROLE"),
                eq(SOURCE_SERVICE), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), anyLong()))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.STALE);

        SyncResultResp resp = service.sync(TENANT_ID, bindReq(), httpRequest);

        assertThat(resp.accepted()).isTrue();
        assertThat(resp.applied()).isFalse();
        assertThat(resp.stale()).isTrue();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_STALE_VERSION);
        assertThat(resp.reason()).isEqualTo(SyncResultBuilder.REASON_STALE);
    }

    @Test
    void shouldReturnSecurityDenied_whenSourceServiceMismatch() {
        when(httpRequest.getHeader(SyncAuthVerifier.HEADER_SERVICE_CODE)).thenReturn("other-service");

        SyncResultResp resp = service.sync(TENANT_ID, bindReq(), httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_SECURITY_DENIED);
        assertThat(resp.reason()).isEqualTo("SOURCE_SERVICE_MISMATCH");
    }

    @Test
    void shouldReturnNonRetryable_whenSourceTypeNotSysUserOrg() {
        mockHeaderMatch();
        UserRoleSyncReq req = new UserRoleSyncReq("BIND", "OTHER_SOURCE",
                "USER", "u1", "ORG", "1", "org-100", "ORG:org-200",
                null, null, SOURCE_SERVICE, "user", "u1",
                new SyncVersionRef(OCCURRED_AT, 1L));

        SyncResultResp resp = service.sync(TENANT_ID, req, httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_NON_RETRYABLE);
        assertThat(resp.reason()).isEqualTo("INVALID_USER_ROLE_SOURCE_OR_TYPE");
    }

    @Test
    void shouldReturnDependencyMissing_whenRelationRoleNotFound() {
        mockHeaderMatch();
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), eq("USER_ROLE"),
                eq(SOURCE_SERVICE), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(), anyLong()))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.APPLIED);
        when(typeResolutionService.resolveUserId(TENANT_ID, "USER", "u1")).thenReturn(10L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "ORG", "org-100", null)).thenReturn(20L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "ORG", "org-200", null)).thenReturn(null);

        SyncResultResp resp = service.sync(TENANT_ID, bindReq(), httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_DEPENDENCY_MISSING);
        assertThat(resp.reason()).isEqualTo("RELATION_ROLE_NOT_FOUND");
    }

    /**
     * 守卫专项：full-sync 中 item.roleTypeCode 与 scope.roleTypeCode 不一致时，
     * 单条记 NON_RETRYABLE/ROLE_TYPE_CODE_MISMATCH_WITH_SCOPE，不进入 markStatus 路径，避免污染 sync_metadata。
     * 与 fix4 的 admin 端按 orgType 分组 envelope 形成端到端守护。
     */
    @Test
    void shouldReturnNonRetryableItem_whenItemRoleTypeCodeMismatchScope() {
        mockHeaderMatch();

        UserRoleFullSyncReq req = new UserRoleFullSyncReq(
                new UserRoleSyncScope(SOURCE_SERVICE, "SYS_USER_ORG", "ORG", "1"),
                List.of(new UserRoleSyncItem(
                        "USER", "u1",
                        "POSITION", "pos-1",        // 与 scope.roleTypeCode=ORG 不一致
                        "POSITION:pos-1",
                        null, null, "user", "u1",
                        new SyncVersionRef(OCCURRED_AT, 1L))));

        SyncResultResp resp = service.fullSync(TENANT_ID, req, httpRequest);

        assertThat(resp.accepted()).isTrue();
        assertThat(resp.applied()).isFalse();
        assertThat(resp.detail()).isNotNull();
        assertThat(resp.detail().failedCount()).isEqualTo(1);
        SyncResultResp.ItemResult ir = resp.detail().itemResults().get(0);
        assertThat(ir.applied()).isFalse();
        assertThat(ir.retryClass()).isEqualTo(SyncResultBuilder.RETRY_NON_RETRYABLE);
        assertThat(ir.reason()).isEqualTo("ROLE_TYPE_CODE_MISMATCH_WITH_SCOPE");
    }
}
