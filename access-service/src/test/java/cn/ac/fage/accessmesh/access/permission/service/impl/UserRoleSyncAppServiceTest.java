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
import cn.ac.fage.accessmesh.access.permission.service.domain.SyncTypeGuard;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
    @Mock
    private SyncTypeGuard syncTypeGuard;
    @Mock
    private cn.ac.fage.accessmesh.access.permission.service.domain.SubjectDomainService subjectDomainService;
    @Mock
    private cn.ac.fage.accessmesh.access.permission.service.domain.PermissionConflictDomainService permissionConflictDomainService;
    @Mock
    private cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper abstractRoleMapper;
    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        AccessRequestContext.clear();
    }

    private UserRoleSyncAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserRoleSyncAppServiceImpl(syncMetadataDomainService,
                typeResolutionService, userRoleMapper, new LocalProjectionGuard(), syncTypeGuard,
                subjectDomainService, permissionConflictDomainService, abstractRoleMapper);
        // 默认放行类型白名单（白名单语义由 SyncTypeGuardTest 单独覆盖）
        lenient().when(syncTypeGuard.validate(anyLong(), anyString(), any())).thenReturn(true);
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
                "LOCAL_USER", "u1", "TEAM_ROLE", "1", "team-1", "TEAM_ROLE:team-2",
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

    // ===== T-PERM-064：sync 通道角色互斥授予守卫 =====

    /** BIND 命中互斥对 → item 级 NON_RETRYABLE ROLE_MUTEX_CONFLICT 零写库（旧实现直接落库必红）。 */
    @Test
    void shouldRejectBindAsNonRetryableWhenPostStateHitsMutex() {
        mockHeaderMatch();
        mockApplyVersionApplied();
        when(typeResolutionService.resolveUserId(TENANT_ID, "EMP", "e-100")).thenReturn(100L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "TEAM_ROLE", "team-1", null)).thenReturn(200L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "TEAM_ROLE", "team-2", null)).thenReturn(300L);
        when(abstractRoleMapper.selectEnabledIdsByIds(TENANT_ID, java.util.Set.of(200L)))
            .thenReturn(java.util.List.of(200L));
        when(subjectDomainService.resolveEffectiveRoles(TENANT_ID, 100L))
            .thenReturn(java.util.Set.of(100L));
        when(permissionConflictDomainService.findAssignMutexConflicts(eq(TENANT_ID), any()))
            .thenReturn(java.util.List.of(new cn.ac.fage.accessmesh.access.permission.service.domain.PermissionConflictDomainService.RoleMutexAssignConflict(
                100L, 9L, 100L, 200L)));

        SyncResultResp resp = service.sync(TENANT_ID, externalBindReq(), httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_NON_RETRYABLE);
        assertThat(resp.reason()).isEqualTo("ROLE_MUTEX_CONFLICT");
        verify(userRoleMapper, never()).insert(any(UserRole.class));
    }

    /** 目标角色禁用不参与判定 → 放行（与管理面守卫启用态过滤同源）。 */
    @Test
    void shouldAllowBindWhenTargetRoleDisabled() {
        mockHeaderMatch();
        mockApplyVersionApplied();
        when(typeResolutionService.resolveUserId(TENANT_ID, "EMP", "e-100")).thenReturn(100L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "TEAM_ROLE", "team-1", null)).thenReturn(200L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "TEAM_ROLE", "team-2", null)).thenReturn(300L);
        when(abstractRoleMapper.selectEnabledIdsByIds(TENANT_ID, java.util.Set.of(200L)))
            .thenReturn(java.util.List.of());
        when(userRoleMapper.insert(any(UserRole.class))).thenReturn(1);

        SyncResultResp resp = service.sync(TENANT_ID, externalBindReq(), httpRequest);

        assertThat(resp.applied()).isTrue();
        verify(permissionConflictDomainService, never()).findAssignMutexConflicts(anyLong(), any());
    }

    /** 生效期未到（future validFrom）不新增当前有效持有 → 不做冲突检查。 */
    @Test
    void shouldAllowBindWhenValidFromInFuture() {
        mockHeaderMatch();
        mockApplyVersionApplied();
        when(typeResolutionService.resolveUserId(TENANT_ID, "EMP", "e-100")).thenReturn(100L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "TEAM_ROLE", "team-1", null)).thenReturn(200L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "TEAM_ROLE", "team-2", null)).thenReturn(300L);
        when(userRoleMapper.insert(any(UserRole.class))).thenReturn(1);
        UserRoleSyncReq futureReq = new UserRoleSyncReq("BIND", "HR_MEMBER",
                "EMP", "e-100", "TEAM_ROLE", "1", "team-1", "TEAM_ROLE:team-2",
                java.time.LocalDateTime.now().plusDays(7), null, SOURCE_SERVICE, "hr_member", "e-100:team-1",
                new SyncVersionRef(OCCURRED_AT, 1L));

        SyncResultResp resp = service.sync(TENANT_ID, futureReq, httpRequest);

        assertThat(resp.applied()).isTrue();
        verify(permissionConflictDomainService, never()).findAssignMutexConflicts(anyLong(), any());
    }

    /** 已当前有效行的幂等改期不新增持有 → 不做冲突检查（拒更新不消除既有违规）。 */
    @Test
    void shouldSkipGuardWhenExistingRowAlreadyEffective() {
        mockHeaderMatch();
        mockApplyVersionApplied();
        when(typeResolutionService.resolveUserId(TENANT_ID, "EMP", "e-100")).thenReturn(100L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "TEAM_ROLE", "team-1", null)).thenReturn(200L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "TEAM_ROLE", "team-2", null)).thenReturn(300L);
        UserRole existing = new UserRole();
        existing.setId(888L);
        existing.setTenantId(TENANT_ID);
        existing.setAbstractUserId(100L);
        existing.setTargetType("ROLE");
        existing.setTargetId(200L);
        existing.setRelationId(300L);
        existing.setDeleteFlag(0L);
        // 有效期全空=当前有效 → 幂等改写不触发守卫
        when(userRoleMapper.selectOneByQuery(any())).thenReturn(existing);
        when(syncMetadataDomainService.resolveTargetId(anyLong(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(java.util.Optional.of(888L));

        SyncResultResp resp = service.sync(TENANT_ID, externalBindReq(), httpRequest);

        assertThat(resp.applied()).isTrue();
        verify(permissionConflictDomainService, never()).findAssignMutexConflicts(anyLong(), any());
        verify(userRoleMapper, never()).insert(any(UserRole.class));
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

    @Test
    void shouldReturnSecurityDenied_whenTypeNotWhitelisted() {
        mockHeaderMatch();
        lenient().doReturn(false).when(syncTypeGuard).validate(anyLong(), anyString(), any());

        SyncResultResp resp = service.sync(TENANT_ID, externalBindReq(), httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_SECURITY_DENIED);
        assertThat(resp.reason()).isEqualTo("SERVICE_TYPE_NOT_ALLOWED");
        verify(userRoleMapper, never()).insert(any(UserRole.class));
    }

    @Test
    void shouldRejectManualOwnedRow_whenBind() {
        mockHeaderMatch();
        mockApplyVersionApplied();
        when(typeResolutionService.resolveUserId(TENANT_ID, "EMP", "e-100")).thenReturn(100L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "TEAM_ROLE", "team-1", null)).thenReturn(200L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "TEAM_ROLE", "team-2", null)).thenReturn(300L);
        // 现有行 owner=NULL（人工维护或其他来源），当前来源无 metadata 指向 → 不得接管
        when(userRoleMapper.selectOneByQuery(any())).thenReturn(manualRow(555L));
        when(syncMetadataDomainService.resolveTargetId(eq(TENANT_ID), eq("USER_ROLE"), eq(SOURCE_SERVICE),
                anyString(), anyString()))
                .thenReturn(java.util.Optional.empty());

        SyncResultResp resp = service.sync(TENANT_ID, externalBindReq(), httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_NON_RETRYABLE);
        assertThat(resp.reason()).isEqualTo("OWNERSHIP_CONFLICT");
        verify(userRoleMapper, never()).update(any(UserRole.class));
    }

    @Test
    void shouldRejectManualOwnedRow_whenUnbind() {
        mockHeaderMatch();
        mockApplyVersionApplied();
        when(typeResolutionService.resolveUserId(TENANT_ID, "EMP", "e-100")).thenReturn(100L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "TEAM_ROLE", "team-1", null)).thenReturn(200L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "TEAM_ROLE", "team-2", null)).thenReturn(300L);
        when(userRoleMapper.selectOneByQuery(any())).thenReturn(manualRow(555L));
        when(syncMetadataDomainService.resolveTargetId(eq(TENANT_ID), eq("USER_ROLE"), eq(SOURCE_SERVICE),
                anyString(), anyString()))
                .thenReturn(java.util.Optional.empty());

        UserRoleSyncReq unbindReq = new UserRoleSyncReq("UNBIND", "HR_MEMBER",
                "EMP", "e-100", "TEAM_ROLE", "1", "team-1", "TEAM_ROLE:team-2",
                null, null, SOURCE_SERVICE, "hr_member", "e-100:team-1",
                new SyncVersionRef(OCCURRED_AT, 1L));

        SyncResultResp resp = service.sync(TENANT_ID, unbindReq, httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_NON_RETRYABLE);
        assertThat(resp.reason()).isEqualTo("OWNERSHIP_CONFLICT");
        verify(userRoleMapper, never()).softDeleteBatch(any(), any(), any());
    }

    @Test
    void shouldUpdateOwnedRow_whenBind() {
        mockHeaderMatch();
        mockApplyVersionApplied();
        when(typeResolutionService.resolveUserId(TENANT_ID, "EMP", "e-100")).thenReturn(100L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "TEAM_ROLE", "team-1", null)).thenReturn(200L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "TEAM_ROLE", "team-2", null)).thenReturn(300L);
        // 现有行由当前来源 metadata 指向（target_id 匹配）→ 允许更新有效期
        UserRole owned = manualRow(555L);
        when(userRoleMapper.selectOneByQuery(any())).thenReturn(owned);
        when(syncMetadataDomainService.resolveTargetId(eq(TENANT_ID), eq("USER_ROLE"), eq(SOURCE_SERVICE),
                anyString(), anyString()))
                .thenReturn(java.util.Optional.of(555L));

        SyncResultResp resp = service.sync(TENANT_ID, externalBindReq(), httpRequest);

        assertThat(resp.applied()).isTrue();
        verify(userRoleMapper).update(owned);
        verify(userRoleMapper, never()).insert(any(UserRole.class));
    }

    @Test
    void fullSync_shouldApplySingleItem() {
        mockHeaderMatch();
        mockApplyVersionApplied();
        when(typeResolutionService.batchResolveUserIds(TENANT_ID, "EMP", java.util.Set.of("e-100")))
                .thenReturn(java.util.Map.of("e-100", 100L));
        when(typeResolutionService.batchResolveRoleIds(TENANT_ID, "TEAM_ROLE", java.util.Set.of("team-1"), null))
                .thenReturn(java.util.Map.of("team-1", 200L));
        when(typeResolutionService.batchResolveRoleIds(TENANT_ID, "TEAM_ROLE", java.util.Set.of("team-2"), null))
                .thenReturn(java.util.Map.of("team-2", 300L));
        when(userRoleMapper.selectValidByUserTargetRelation(anyLong(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(userRoleMapper.insert(any(UserRole.class))).thenReturn(1);
        when(syncMetadataDomainService.listScopeForFullSync(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(List.of());

        UserRoleFullSyncReq req = new UserRoleFullSyncReq(
                new UserRoleSyncScope(SOURCE_SERVICE, "HR_MEMBER", "TEAM_ROLE", "1"),
                List.of(new UserRoleSyncItem(
                        "EMP", "e-100",
                        "TEAM_ROLE", "team-1",
                        "TEAM_ROLE:team-2",
                        null, null, "hr_member", "e-100:team-1",
                        new SyncVersionRef(OCCURRED_AT, 1L))));

        SyncResultResp resp = service.fullSync(TENANT_ID, req, httpRequest);

        assertThat(resp.accepted()).isTrue();
        assertThat(resp.detail().appliedCount()).isEqualTo(1);
        ArgumentCaptor<UserRole> cap = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleMapper).insert(cap.capture());
        assertThat(cap.getValue().getAbstractUserId()).isEqualTo(100L);
        assertThat(cap.getValue().getTargetId()).isEqualTo(200L);
        assertThat(cap.getValue().getRelationId()).isEqualTo(300L);
    }

    @Test
    void fullSync_shouldRejectDuplicateBusinessKey() {
        mockHeaderMatch();
        mockApplyVersionApplied();
        when(typeResolutionService.batchResolveUserIds(TENANT_ID, "EMP", java.util.Set.of("e-100")))
                .thenReturn(java.util.Map.of("e-100", 100L));
        when(typeResolutionService.batchResolveRoleIds(TENANT_ID, "TEAM_ROLE", java.util.Set.of("team-1"), null))
                .thenReturn(java.util.Map.of("team-1", 200L));
        when(typeResolutionService.batchResolveRoleIds(TENANT_ID, "TEAM_ROLE", java.util.Set.of("team-2"), null))
                .thenReturn(java.util.Map.of("team-2", 300L));
        when(userRoleMapper.selectValidByUserTargetRelation(anyLong(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(userRoleMapper.insert(any(UserRole.class))).thenReturn(1);
        when(syncMetadataDomainService.listScopeForFullSync(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(List.of());

        UserRoleSyncItem item = new UserRoleSyncItem(
                "EMP", "e-100",
                "TEAM_ROLE", "team-1",
                "TEAM_ROLE:team-2",
                null, null, "hr_member", "e-100:team-1",
                new SyncVersionRef(OCCURRED_AT, 1L));
        // 同 businessKey 两项（第二项版本更高）：第一项 INSERT，第二项写入前拒绝
        UserRoleFullSyncReq req = new UserRoleFullSyncReq(
                new UserRoleSyncScope(SOURCE_SERVICE, "HR_MEMBER", "TEAM_ROLE", "1"),
                List.of(item, item));

        SyncResultResp resp = service.fullSync(TENANT_ID, req, httpRequest);

        assertThat(resp.accepted()).isTrue();
        assertThat(resp.detail().appliedCount()).isEqualTo(1);
        assertThat(resp.detail().failedCount()).isEqualTo(1);
        assertThat(resp.detail().itemResults().get(1).retryClass())
                .isEqualTo(SyncResultBuilder.RETRY_NON_RETRYABLE);
        // 只 INSERT 一次，未触发 uk_user_role 唯一约束
        verify(userRoleMapper).insert(any(UserRole.class));
    }

    private static UserRole manualRow(long id) {
        UserRole ur = new UserRole();
        ur.setId(id);
        ur.setOwnerServiceCode(null);
        return ur;
    }
}
