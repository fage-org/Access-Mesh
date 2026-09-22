package cn.ac.fage.accessmesh.access.role.service.impl;

import cn.ac.fage.accessmesh.access.infrastructure.AccessRequestContext;
import cn.ac.fage.accessmesh.access.infrastructure.RequestContext;
import cn.ac.fage.accessmesh.access.sync.metadata.SyncVersionRef;
import cn.ac.fage.accessmesh.access.sync.dto.UserRoleFullSyncReq;
import cn.ac.fage.accessmesh.access.sync.dto.UserRoleSyncItem;
import cn.ac.fage.accessmesh.access.sync.dto.UserRoleSyncReq;
import cn.ac.fage.accessmesh.access.sync.dto.UserRoleSyncScope;
import cn.ac.fage.accessmesh.access.role.entity.UserRole;
import cn.ac.fage.accessmesh.perm.common.dto.resp.SyncResultResp;
import cn.ac.fage.accessmesh.access.role.mapper.UserRoleMapper;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.sync.guard.LocalProjectionGuard;
import cn.ac.fage.accessmesh.access.sync.metadata.SyncMetadataDomainService;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.sync.SyncResultBuilder;
import cn.ac.fage.accessmesh.access.sync.guard.SyncTypeGuard;
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
    private cn.ac.fage.accessmesh.access.engine.core.SubjectDomainService subjectDomainService;
    @Mock
    private cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConflictDomainService permissionConflictDomainService;
    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        AccessRequestContext.clear();
    }

    private UserRoleSyncAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserRoleSyncAppServiceImpl(syncMetadataDomainService,
                typeResolutionService, userRoleMapper, new LocalProjectionGuard(), syncTypeGuard,
                subjectDomainService, permissionConflictDomainService, org.mockito.Mockito.mock(cn.ac.fage.accessmesh.access.infrastructure.TreeWriteLockSupport.class));
        // 默认放行类型白名单（白名单语义由 SyncTypeGuardTest 单独覆盖）
        lenient().when(syncTypeGuard.validate(anyLong(), anyString(), any())).thenReturn(true);
    }

    @Test
    void shouldNotConsumeVersion_whenSubjectMissing() {
        mockHeaderMatch();
        when(typeResolutionService.resolveUserId(TENANT_ID, "EMP", "e-100")).thenReturn(null);
        SyncResultResp result = service.sync(TENANT_ID, externalBindReq(), httpRequest);
        assertThat(result.reason()).isEqualTo("SUBJECT_NOT_FOUND");
        verify(syncMetadataDomainService, never()).applyVersion(anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyLong());
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
                .isEqualTo(AccessErrorCode.LOCAL_PROJECTION_IMMUTABLE.getCode());
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
                .isEqualTo(AccessErrorCode.LOCAL_PROJECTION_IMMUTABLE.getCode());
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
                .isEqualTo(AccessErrorCode.LOCAL_PROJECTION_IMMUTABLE.getCode());
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
                .isEqualTo(AccessErrorCode.LOCAL_PROJECTION_IMMUTABLE.getCode());
    }

    @Test
    void shouldRejectLocalOwnedUserRole_whenBind() {
        mockHeaderMatch();
        when(typeResolutionService.resolveUserId(TENANT_ID, "EMP", "e-100")).thenReturn(100L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "TEAM_ROLE", "team-1", null)).thenReturn(200L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "TEAM_ROLE", "team-2", null)).thenReturn(300L);
        // 已有行是本地投影（owner=access-service）→ 外部 BIND 不得改写
        when(userRoleMapper.selectOneByQuery(any())).thenReturn(localOwnedRow());

        assertThatThrownBy(() -> service.sync(TENANT_ID, externalBindReq(), httpRequest))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(AccessErrorCode.LOCAL_PROJECTION_IMMUTABLE.getCode());
        verify(userRoleMapper, never()).update(any(UserRole.class));
    }

    @Test
    void shouldRejectLocalOwnedUserRole_whenUnbind() {
        mockHeaderMatch();
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
                .isEqualTo(AccessErrorCode.LOCAL_PROJECTION_IMMUTABLE.getCode());
        verify(userRoleMapper, never()).softDeleteBatch(any(), any(), any());
    }

    // ===== T-PERM-064/T-PERM-075：sync 通道角色互斥授予守卫 =====

    /** BIND 命中互斥对 → item 级 NON_RETRYABLE ROLE_MUTEX_CONFLICT 零写库（旧实现直接落库必红）。 */
    @Test
    void shouldRejectBindAsNonRetryableWhenPostStateHitsMutex() {
        mockHeaderMatch();
        when(typeResolutionService.resolveUserId(TENANT_ID, "EMP", "e-100")).thenReturn(100L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "TEAM_ROLE", "team-1", null)).thenReturn(200L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "TEAM_ROLE", "team-2", null)).thenReturn(300L);
        when(subjectDomainService.resolveRawHoldings(TENANT_ID, 100L))
            .thenReturn(java.util.Set.of(new cn.ac.fage.accessmesh.access.engine.core.SubjectDomainService.RawHolding(100L, null, null)));
        when(permissionConflictDomainService.findAssignMutexConflicts(eq(TENANT_ID), any()))
            .thenReturn(java.util.List.of(new cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConflictDomainService.RoleMutexAssignConflict(
                100L, 9L, 100L, 200L)));

        SyncResultResp resp = service.sync(TENANT_ID, externalBindReq(), httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.retryClass()).isEqualTo(SyncResultBuilder.RETRY_NON_RETRYABLE);
        verify(syncMetadataDomainService, never()).applyVersion(anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyLong());
        assertThat(resp.reason()).isEqualTo("ROLE_MUTEX_CONFLICT");
        verify(userRoleMapper, never()).insert(any(UserRole.class));
    }

    /**
     * T-PERM-075 U002-2（用户拍板 2026-09-22）：目标角色禁用仍进写时候选——BIND 时刻已知互斥对即拒绝。
     * 旧口径「禁用目标不参与判定放行」退役（旧实现在本用例下放行，必红）。
     */
    @Test
    void shouldRejectBindWhenTargetRoleDisabledButMutexPairPresent() {
        mockHeaderMatch();
        when(typeResolutionService.resolveUserId(TENANT_ID, "EMP", "e-100")).thenReturn(100L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "TEAM_ROLE", "team-1", null)).thenReturn(200L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "TEAM_ROLE", "team-2", null)).thenReturn(300L);
        // 目标 200 禁用：原始持有候选口径下仍并入 postState（不再收敛启用）
        when(subjectDomainService.resolveRawHoldings(TENANT_ID, 100L))
            .thenReturn(java.util.Set.of(new cn.ac.fage.accessmesh.access.engine.core.SubjectDomainService.RawHolding(100L, null, null)));
        when(permissionConflictDomainService.findAssignMutexConflicts(eq(TENANT_ID), any()))
            .thenReturn(java.util.List.of(new cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConflictDomainService.RoleMutexAssignConflict(
                100L, 9L, 100L, 200L)));

        SyncResultResp resp = service.sync(TENANT_ID, externalBindReq(), httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.reason()).isEqualTo("ROLE_MUTEX_CONFLICT");
        verify(userRoleMapper, never()).insert(any(UserRole.class));
    }

    /**
     * T-PERM-075 U002-1（用户拍板 2026-09-22）：未来 valid_from 窗口的 BIND 与既有持有重叠 → 写时拒绝。
     * 旧口径「生效期未到不做冲突检查」退役（旧实现 applied，本用例必红）。
     */
    @Test
    void shouldRejectBindWhenFutureWindowOverlapsMutexPeer() {
        mockHeaderMatch();
        when(typeResolutionService.resolveUserId(TENANT_ID, "EMP", "e-100")).thenReturn(100L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "TEAM_ROLE", "team-1", null)).thenReturn(200L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "TEAM_ROLE", "team-2", null)).thenReturn(300L);
        when(subjectDomainService.resolveRawHoldings(TENANT_ID, 100L))
            .thenReturn(java.util.Set.of(new cn.ac.fage.accessmesh.access.engine.core.SubjectDomainService.RawHolding(100L, null, null)));
        when(permissionConflictDomainService.findAssignMutexConflicts(eq(TENANT_ID), any()))
            .thenReturn(java.util.List.of(new cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConflictDomainService.RoleMutexAssignConflict(
                100L, 9L, 100L, 200L)));
        UserRoleSyncReq futureReq = new UserRoleSyncReq("BIND", "HR_MEMBER",
                "EMP", "e-100", "TEAM_ROLE", "1", "team-1", "TEAM_ROLE:team-2",
                java.time.LocalDateTime.now().plusDays(7), null, SOURCE_SERVICE, "hr_member", "e-100:team-1",
                new SyncVersionRef(OCCURRED_AT, 1L));

        SyncResultResp resp = service.sync(TENANT_ID, futureReq, httpRequest);

        assertThat(resp.accepted()).isFalse();
        assertThat(resp.reason()).isEqualTo("ROLE_MUTEX_CONFLICT");
        verify(userRoleMapper, never()).insert(any(UserRole.class));
    }

    /** T-PERM-075 U002-1 边界：valid_to 已过期（永不生效）不触发守卫。 */
    @Test
    void shouldSkipGuardWhenBindWindowExpired() {
        mockHeaderMatch();
        mockApplyVersionApplied();
        when(typeResolutionService.resolveUserId(TENANT_ID, "EMP", "e-100")).thenReturn(100L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "TEAM_ROLE", "team-1", null)).thenReturn(200L);
        when(typeResolutionService.resolveRoleId(TENANT_ID, "TEAM_ROLE", "team-2", null)).thenReturn(300L);
        when(userRoleMapper.insert(any(UserRole.class))).thenReturn(1);
        UserRoleSyncReq expiredReq = new UserRoleSyncReq("BIND", "HR_MEMBER",
                "EMP", "e-100", "TEAM_ROLE", "1", "team-1", "TEAM_ROLE:team-2",
                null, java.time.LocalDateTime.now().minusDays(1), SOURCE_SERVICE, "hr_member", "e-100:team-1",
                new SyncVersionRef(OCCURRED_AT, 1L));

        SyncResultResp resp = service.sync(TENANT_ID, expiredReq, httpRequest);

        assertThat(resp.applied()).isTrue();
        verify(permissionConflictDomainService, never()).findAssignMutexConflicts(anyLong(), any());
    }

    /**
     * T-PERM-075 口径修订：改写后未过期即触发守卫（幂等重放因持有侧无冲突天然通过）——
     * 旧「已当前有效行的幂等改期不触发」口径随窗口重叠判定消解，本用例锁「守卫被调用且无冲突放行」。
     */
    @Test
    void shouldRecheckGuardOnRewriteEvenWhenAlreadyEffective() {
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
        // 有效期全空=当前有效 → 改写后未过期仍走守卫；持有侧无重叠冲突 → 放行
        when(userRoleMapper.selectOneByQuery(any())).thenReturn(existing);
        when(syncMetadataDomainService.resolveTargetId(anyLong(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(java.util.Optional.of(888L));
        when(subjectDomainService.resolveRawHoldings(TENANT_ID, 100L))
            .thenReturn(java.util.Set.of());
        when(permissionConflictDomainService.findAssignMutexConflicts(eq(TENANT_ID), any()))
            .thenReturn(java.util.List.of());

        SyncResultResp resp = service.sync(TENANT_ID, externalBindReq(), httpRequest);

        assertThat(resp.applied()).isTrue();
        verify(permissionConflictDomainService).findAssignMutexConflicts(eq(TENANT_ID), any());
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
                .isEqualTo(AccessErrorCode.LOCAL_PROJECTION_IMMUTABLE.getCode());
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
        verify(syncMetadataDomainService, never()).applyVersion(anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyLong());
        assertThat(resp.reason()).isEqualTo("OWNERSHIP_CONFLICT");
        verify(userRoleMapper, never()).update(any(UserRole.class));
    }

    @Test
    void shouldRejectManualOwnedRow_whenUnbind() {
        mockHeaderMatch();
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
        verify(syncMetadataDomainService, never()).applyVersion(anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyLong());
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
