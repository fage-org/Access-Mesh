package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import cn.ac.fage.accessmesh.access.permission.dto.common.SyncVersionRef;
import cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleFullSyncReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleSyncItem;
import cn.ac.fage.accessmesh.access.permission.dto.req.AbstractRoleSyncScope;
import cn.ac.fage.accessmesh.access.permission.dto.req.AbstractUserFullSyncReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.AbstractUserSyncItem;
import cn.ac.fage.accessmesh.access.permission.dto.req.AbstractUserSyncScope;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceEntityFullSyncReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceEntitySyncItem;
import cn.ac.fage.accessmesh.access.permission.dto.req.ResourceEntitySyncScope;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleFullSyncReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleSyncItem;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleSyncScope;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractRole;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractUser;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.ResourceEntityMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.SyncMetadataMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.UserRoleMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.SyncMetadataDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.sync.SyncAuthVerifier;
import cn.ac.fage.accessmesh.access.permission.service.sync.SyncResultBuilder;
import cn.ac.fage.accessmesh.access.permission.service.sync.SyncTypeGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * full-sync 顶层契约防回归测试。
 * <p>
 * 验证 4 个 *SyncAppService.fullSync 返回的 {@link SyncResultResp} 顶层字段与 sync 一致，
 * 批量明细放入 {@code detail}，禁止独立顶层 DTO。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class FullSyncResponseContractTest {

    private static final Long TENANT_ID = 1L;
    private static final String SOURCE_SERVICE = "example-service";
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Mock
    private SyncMetadataDomainService syncMetadataDomainService;
    @Mock
    private TypeResolutionService typeResolutionService;
    @Mock
    private SyncMetadataMapper syncMetadataMapper;
    @Mock
    private AbstractUserMapper abstractUserMapper;
    @Mock
    private AbstractRoleMapper abstractRoleMapper;
    @Mock
    private UserRoleMapper userRoleMapper;
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

    private void mockHeaderMatch() {
        AccessRequestContext.bind(RequestContext.service(TENANT_ID, SOURCE_SERVICE));
    }

    // ---- 1. AbstractUserSyncAppService.fullSync ----

    @org.junit.jupiter.api.BeforeEach
    void allowTypeWhitelist() {
        lenient().when(syncTypeGuard.validate(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);
    }

    @Test
    void abstractUserFullSync_allSuccess_topLevelMatchesSyncContract() {
        mockHeaderMatch();
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "user_type", "USER")).thenReturn(0);
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(LocalDateTime.class), anyLong()))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.APPLIED);
        when(abstractUserMapper.selectByTypeAndExternalIds(eq(TENANT_ID), eq(0), any())).thenReturn(java.util.Collections.emptyList());
        lenient().when(abstractUserMapper.selectByTypeAndExternalId(eq(TENANT_ID), eq(0), anyString())).thenReturn(null);
        lenient().when(abstractUserMapper.insert(any(AbstractUser.class))).thenReturn(1);
        lenient().when(syncMetadataDomainService.listScopeForFullSync(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());

        AbstractUserSyncAppServiceImpl service = new AbstractUserSyncAppServiceImpl(
                syncMetadataDomainService, typeResolutionService, abstractUserMapper, new ObjectMapper(),
                new cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionGuard(), syncTypeGuard);
        AbstractUserFullSyncReq req = new AbstractUserFullSyncReq(
                new AbstractUserSyncScope(SOURCE_SERVICE, "USER"),
                List.of(new AbstractUserSyncItem("u1", "User One", true, null,
                        null, null, new SyncVersionRef(OCCURRED_AT, 1L))));

        SyncResultResp resp = service.fullSync(TENANT_ID, req, httpRequest);

        assertThat(resp.accepted()).isTrue();
        assertThat(resp.applied()).isTrue();
        assertThat(resp.stale()).isFalse();
        assertThat(resp.retryClass()).isNull();
        assertThat(resp.reason()).isNull();
        assertThat(resp.detail()).isNotNull();
        assertThat(resp.detail().appliedCount()).isEqualTo(1);
        assertThat(resp.detail().staleCount()).isZero();
        assertThat(resp.detail().failedCount()).isZero();
        assertThat(resp.detail().itemResults()).hasSize(1);
    }

    @Test
    void abstractUserFullSync_securityDenied_topLevelRetryable() {
        AccessRequestContext.bind(RequestContext.service(TENANT_ID, "other-service"));

        AbstractUserSyncAppServiceImpl service = new AbstractUserSyncAppServiceImpl(
                syncMetadataDomainService, typeResolutionService, abstractUserMapper, new ObjectMapper(),
                new cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionGuard(), syncTypeGuard);
        AbstractUserFullSyncReq req = new AbstractUserFullSyncReq(
                new AbstractUserSyncScope(SOURCE_SERVICE, "USER"),
                List.of(new AbstractUserSyncItem("u1", "User One", true, null,
                        null, null, new SyncVersionRef(OCCURRED_AT, 1L))));

        SyncResultResp resp = service.fullSync(TENANT_ID, req, httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.applied()).isFalse();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_SECURITY_DENIED);
        assertThat(resp.detail()).isNotNull();
        assertThat(resp.detail().failedCount()).isEqualTo(1);
    }

    // ---- 2. AbstractRoleSyncAppService.fullSync ----

    @Test
    void abstractRoleFullSync_allSuccess_topLevelMatchesSyncContract() {
        mockHeaderMatch();
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "role_type", "BASIC_ROLE")).thenReturn(1);
        when(syncMetadataDomainService.applyVersion(eq(TENANT_ID), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any(LocalDateTime.class), anyLong()))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.APPLIED);
        when(abstractRoleMapper.selectByTypeAndExternalIds(eq(TENANT_ID), eq(1), any())).thenReturn(java.util.Collections.emptyList());
        lenient().when(abstractRoleMapper.selectByTypeAndExternalId(eq(TENANT_ID), eq(1), anyString())).thenReturn(null);
        lenient().when(abstractRoleMapper.insert(any(AbstractRole.class))).thenReturn(1);
        lenient().when(syncMetadataDomainService.listScopeForFullSync(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());

        AbstractRoleSyncAppServiceImpl service = new AbstractRoleSyncAppServiceImpl(
                syncMetadataDomainService, typeResolutionService, abstractRoleMapper, new ObjectMapper(),
                new cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionGuard(), syncTypeGuard);
        AbstractRoleFullSyncReq req = new AbstractRoleFullSyncReq(
                new AbstractRoleSyncScope(SOURCE_SERVICE, "BASIC_ROLE", "ROOT"),
                List.of(new AbstractRoleSyncItem("org-1", "Org 1", null, null,
                        1, 0, null, null, null, new SyncVersionRef(OCCURRED_AT, 1L))));

        SyncResultResp resp = service.fullSync(TENANT_ID, req, httpRequest);

        assertThat(resp.accepted()).isTrue();
        assertThat(resp.applied()).isTrue();
        assertThat(resp.retryClass()).isNull();
        assertThat(resp.detail()).isNotNull();
        assertThat(resp.detail().appliedCount()).isEqualTo(1);
        assertThat(resp.detail().itemResults()).hasSize(1);
    }

    @Test
    void abstractRoleFullSync_partialFailure_topLevelRetryable() {
        mockHeaderMatch();
        when(typeResolutionService.resolveTypeValue(TENANT_ID, "role_type", "BASIC_ROLE")).thenReturn(1);
        when(typeResolutionService.batchResolveRoleIds(eq(TENANT_ID), eq("BASIC_ROLE"), any(), eq(null)))
                .thenReturn(java.util.Collections.emptyMap());
        lenient().when(typeResolutionService.resolveRoleId(eq(TENANT_ID), eq("BASIC_ROLE"), eq("missing-parent"), eq(null)))
                .thenReturn(null);
        lenient().when(syncMetadataDomainService.listScopeForFullSync(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(Collections.emptyList());

        AbstractRoleSyncAppServiceImpl service = new AbstractRoleSyncAppServiceImpl(
                syncMetadataDomainService, typeResolutionService, abstractRoleMapper, new ObjectMapper(),
                new cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionGuard(), syncTypeGuard);
        AbstractRoleFullSyncReq req = new AbstractRoleFullSyncReq(
                new AbstractRoleSyncScope(SOURCE_SERVICE, "BASIC_ROLE", "ROOT"),
                List.of(new AbstractRoleSyncItem("org-1", "Org 1", "BASIC_ROLE", "missing-parent",
                        1, 0, null, null, null, new SyncVersionRef(OCCURRED_AT, 1L))));

        SyncResultResp resp = service.fullSync(TENANT_ID, req, httpRequest);

        assertThat(resp.accepted()).isTrue();
        assertThat(resp.applied()).isFalse();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_RETRYABLE);
        assertThat(resp.reason()).isEqualTo(SyncResultBuilder.REASON_FULL_SYNC_PARTIAL_FAILURE);
        assertThat(resp.detail()).isNotNull();
        assertThat(resp.detail().failedCount()).isEqualTo(1);
        assertThat(resp.detail().itemResults().get(0).retryClass())
                .isEqualTo(SyncResultBuilder.RETRY_DEPENDENCY_MISSING);
    }

    // ---- 3. UserRoleSyncAppService.fullSync ----

    @Test
    void userRoleFullSync_itemRoleTypeMismatch_nonRetryableItem() {
        mockHeaderMatch();

        UserRoleSyncAppServiceImpl service = new UserRoleSyncAppServiceImpl(
                syncMetadataDomainService, typeResolutionService, userRoleMapper,
                new cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionGuard(), syncTypeGuard);
        // scope.roleTypeCode 与 item.roleTypeCode 不一致 → item 级 NON_RETRYABLE（顶层 accepted；
        // 契约 §6.2.2.3：不进入 markStatus 路径，避免污染 metadata）
        UserRoleFullSyncReq req = new UserRoleFullSyncReq(
                new UserRoleSyncScope(SOURCE_SERVICE, "HR_MEMBER", "TEAM_ROLE", "ROOT"),
                List.of(new UserRoleSyncItem("EMP", "u1", "OTHER_ROLE", "org-1",
                        "TEAM_ROLE:org-1", null, null, null, null,
                        new SyncVersionRef(OCCURRED_AT, 1L))));

        SyncResultResp resp = service.fullSync(TENANT_ID, req, httpRequest);

        assertThat(resp.accepted()).isTrue();
        assertThat(resp.applied()).isFalse();
        assertThat(resp.detail()).isNotNull();
        assertThat(resp.detail().failedCount()).isEqualTo(1);
        assertThat(resp.detail().itemResults().get(0).retryClass())
                .isEqualTo(SyncResultBuilder.RETRY_NON_RETRYABLE);
    }

    @Test
    void userRoleFullSync_reservedSysUserOrg_throwsImmutable() {
        mockHeaderMatch();
        UserRoleSyncAppServiceImpl service = new UserRoleSyncAppServiceImpl(
                syncMetadataDomainService, typeResolutionService, userRoleMapper,
                new cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionGuard(), syncTypeGuard);
        UserRoleFullSyncReq req = new UserRoleFullSyncReq(
                new UserRoleSyncScope(SOURCE_SERVICE, "SYS_USER_ORG", "ORG", "ROOT"),
                List.of(new UserRoleSyncItem("USER", "u1", "POSITION", "pos-1",
                        "POSITION:pos-1", null, null, null, null,
                        new SyncVersionRef(OCCURRED_AT, 1L))));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.fullSync(TENANT_ID, req, httpRequest))
                .isInstanceOf(cn.ac.fage.accessmesh.common.exception.BizException.class);
    }

    // ---- 4. ResourceEntitySyncAppService.fullSync ----

    @Test
    void resourceEntityFullSync_securityDenied_topLevelSecurityDenied() {
        AccessRequestContext.bind(RequestContext.service(TENANT_ID, "other-service"));

        ResourceEntitySyncAppServiceImpl service = new ResourceEntitySyncAppServiceImpl(
                syncMetadataDomainService, syncMetadataMapper, typeResolutionService, resourceEntityMapper, new ObjectMapper(),
                new cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionGuard(), syncTypeGuard);
        ResourceEntityFullSyncReq req = new ResourceEntityFullSyncReq(
                new ResourceEntitySyncScope(SOURCE_SERVICE, "MENU"),
                List.of(new ResourceEntitySyncItem("menu-1", "default", "Menu 1",
                        null, null, null, null, 1, 0, null, null, null,
                        new SyncVersionRef(OCCURRED_AT, 1L))));

        SyncResultResp resp = service.fullSync(TENANT_ID, req, httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.applied()).isFalse();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_SECURITY_DENIED);
        assertThat(resp.detail()).isNotNull();
        assertThat(resp.detail().failedCount()).isEqualTo(1);
    }
}
