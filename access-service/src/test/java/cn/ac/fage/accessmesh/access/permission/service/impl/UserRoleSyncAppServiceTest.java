package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.permission.dto.common.SyncVersionRef;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleFullSyncReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleSyncItem;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleSyncReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleSyncScope;
import cn.ac.fage.accessmesh.access.permission.entity.UserRole;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import cn.ac.fage.accessmesh.access.permission.mapper.UserRoleMapper;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionGuard;
import cn.ac.fage.accessmesh.access.permission.service.domain.SyncMetadataDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.sync.SyncResultBuilder;
import cn.ac.fage.accessmesh.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UserRoleSyncAppServiceImpl} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class UserRoleSyncAppServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final String SOURCE_SERVICE = "example-service";
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Mock
    private SyncMetadataDomainService syncMetadataDomainService;
    @Mock
    private TypeResolutionService typeResolutionService;
    @Mock
    private UserRoleMapper userRoleMapper;
    @Mock
    private HttpServletRequest httpRequest;
    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        AccessRequestContext.clear();
    }

    private UserRoleSyncAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserRoleSyncAppServiceImpl(syncMetadataDomainService,
                typeResolutionService, userRoleMapper, new LocalProjectionGuard());
    }

    private UserRoleSyncReq bindReq() {
        return new UserRoleSyncReq("BIND", "SYS_USER_ORG",
                "USER", "u1", "ORG", "1", "org-100", "ORG:org-200",
                null, null, SOURCE_SERVICE, "user", "u1",
                new SyncVersionRef(OCCURRED_AT, 1L));
    }

    /** 外部自有类型 BIND 请求（调用方自有 sourceType/主体/角色/relationKey 类型）。 */
    private UserRoleSyncReq externalBindReq() {
        return new UserRoleSyncReq("BIND", "HR_MEMBER",
                "EMP", "e-100", "TEAM_ROLE", "1", "team-1", "TEAM_ROLE:team-2",
                null, null, SOURCE_SERVICE, "hr_member", "e-100:team-1",
                new SyncVersionRef(OCCURRED_AT, 1L));
    }

    private void mockApplyVersionApplied() {
        when(syncMetadataDomainService.applyVersion(anyLong(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), any(), anyLong()))
                .thenReturn(SyncMetadataDomainService.ApplyVersionResult.APPLIED);
    }

    /** 本地投影所有权行（owner=access-service）。 */
    private static UserRole localOwnedRow() {
        UserRole ur = new UserRole();
        ur.setId(555L);
        ur.setOwnerServiceCode("access-service");
        return ur;
    }

    private void mockHeaderMatch() {
        AccessRequestContext.bind(RequestContext.service(TENANT_ID, SOURCE_SERVICE));
    }

    @Test
    void shouldRejectReservedSysUserOrgSource_whenBind() {
        mockHeaderMatch();
        assertThatThrownBy(() -> service.sync(TENANT_ID, bindReq(), httpRequest))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(PermissionErrorCode.LOCAL_PROJECTION_IMMUTABLE.getCode());
    }

    @Test
    void shouldReturnSecurityDenied_whenSourceServiceMismatch() {
        AccessRequestContext.bind(RequestContext.service(TENANT_ID, "other-service"));

        SyncResultResp resp = service.sync(TENANT_ID, bindReq(), httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_SECURITY_DENIED);
        assertThat(resp.reason()).isEqualTo("SOURCE_SERVICE_MISMATCH");
    }

    @Test
    void shouldApply_whenExternalOwnTypes() {
        mockHeaderMatch();
        mockApplyVersionApplied();
        when(typeResolutionService.resolveUserId(TENANT_ID, "EMP", "e-100")).thenReturn(100L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "TEAM_ROLE", "team-1", null)).thenReturn(200L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "TEAM_ROLE", "team-2", null)).thenReturn(300L);
        when(userRoleMapper.insert(any(UserRole.class))).thenReturn(1);

        SyncResultResp resp = service.sync(TENANT_ID, externalBindReq(), httpRequest);

        assertThat(resp.applied()).isTrue();
        ArgumentCaptor<UserRole> cap = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleMapper).insert(cap.capture());
        assertThat(cap.getValue().getAbstractUserId()).isEqualTo(100L);
        assertThat(cap.getValue().getTargetId()).isEqualTo(200L);
        assertThat(cap.getValue().getRelationId()).isEqualTo(300L);
        // 外部同步写入行所有权保持 NULL（owner 由本地投影独占）
        assertThat(cap.getValue().getOwnerServiceCode()).isNull();
    }

    @Test
    void shouldRejectReservedSubjectType_whenBind() {
        mockHeaderMatch();
        UserRoleSyncReq req = new UserRoleSyncReq("BIND", "HR_MEMBER",
                "ADMIN_USER", "u1", "TEAM_ROLE", "1", "team-1", "TEAM_ROLE:team-2",
                null, null, SOURCE_SERVICE, "user", "u1",
                new SyncVersionRef(OCCURRED_AT, 1L));

        assertThatThrownBy(() -> service.sync(TENANT_ID, req, httpRequest))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(PermissionErrorCode.LOCAL_PROJECTION_IMMUTABLE.getCode());
    }

    @Test
    void shouldRejectReservedRoleType_whenBind() {
        mockHeaderMatch();
        UserRoleSyncReq req = new UserRoleSyncReq("BIND", "HR_MEMBER",
                "EMP", "e-100", "ORG", "1", "org-100", "TEAM_ROLE:team-2",
                null, null, SOURCE_SERVICE, "user", "u1",
                new SyncVersionRef(OCCURRED_AT, 1L));

        assertThatThrownBy(() -> service.sync(TENANT_ID, req, httpRequest))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(PermissionErrorCode.LOCAL_PROJECTION_IMMUTABLE.getCode());
    }

    @Test
    void shouldRejectReservedRelationType_whenBind() {
        mockHeaderMatch();
        UserRoleSyncReq req = new UserRoleSyncReq("BIND", "HR_MEMBER",
                "EMP", "e-100", "TEAM_ROLE", "1", "team-1", "ORG:org-200",
                null, null, SOURCE_SERVICE, "user", "u1",
                new SyncVersionRef(OCCURRED_AT, 1L));

        assertThatThrownBy(() -> service.sync(TENANT_ID, req, httpRequest))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(PermissionErrorCode.LOCAL_PROJECTION_IMMUTABLE.getCode());
    }

    @Test
    void shouldRejectLocalOwnedUserRole_whenBind() {
        mockHeaderMatch();
        mockApplyVersionApplied();
        when(typeResolutionService.resolveUserId(TENANT_ID, "EMP", "e-100")).thenReturn(100L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "TEAM_ROLE", "team-1", null)).thenReturn(200L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "TEAM_ROLE", "team-2", null)).thenReturn(300L);
        // 已有行是本地投影（owner=access-service）→ 外部 BIND 不得改写
        when(userRoleMapper.selectOneByQuery(any())).thenReturn(localOwnedRow());

        assertThatThrownBy(() -> service.sync(TENANT_ID, externalBindReq(), httpRequest))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(PermissionErrorCode.LOCAL_PROJECTION_IMMUTABLE.getCode());
        verify(userRoleMapper, never()).update(any(UserRole.class));
    }

    @Test
    void shouldRejectLocalOwnedUserRole_whenUnbind() {
        mockHeaderMatch();
        mockApplyVersionApplied();
        when(typeResolutionService.resolveUserId(TENANT_ID, "EMP", "e-100")).thenReturn(100L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "TEAM_ROLE", "team-1", null)).thenReturn(200L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "TEAM_ROLE", "team-2", null)).thenReturn(300L);
        when(userRoleMapper.selectOneByQuery(any())).thenReturn(localOwnedRow());

        UserRoleSyncReq unbindReq = new UserRoleSyncReq("UNBIND", "HR_MEMBER",
                "EMP", "e-100", "TEAM_ROLE", "1", "team-1", "TEAM_ROLE:team-2",
                null, null, SOURCE_SERVICE, "hr_member", "e-100:team-1",
                new SyncVersionRef(OCCURRED_AT, 1L));

        assertThatThrownBy(() -> service.sync(TENANT_ID, unbindReq, httpRequest))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(PermissionErrorCode.LOCAL_PROJECTION_IMMUTABLE.getCode());
        verify(userRoleMapper, never()).softDeleteBatch(any(), any(), any());
    }

    @Test
    void shouldRejectReservedSysUserOrgSource_whenFullSync() {
        mockHeaderMatch();
        UserRoleFullSyncReq req = new UserRoleFullSyncReq(
                new UserRoleSyncScope(SOURCE_SERVICE, "SYS_USER_ORG", "ORG", "1"),
                List.of(new UserRoleSyncItem(
                        "USER", "u1",
                        "ORG", "org-1",
                        "ORG:org-1",
                        null, null, "user", "u1",
                        new SyncVersionRef(OCCURRED_AT, 1L))));

        assertThatThrownBy(() -> service.fullSync(TENANT_ID, req, httpRequest))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(PermissionErrorCode.LOCAL_PROJECTION_IMMUTABLE.getCode());
    }
}
