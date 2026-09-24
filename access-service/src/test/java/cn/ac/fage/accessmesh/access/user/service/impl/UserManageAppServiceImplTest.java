package cn.ac.fage.accessmesh.access.user.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.engine.constant.OperationCode;
import cn.ac.fage.accessmesh.perm.common.dto.req.UserAssignRoleReq;
import cn.ac.fage.accessmesh.access.user.dto.req.AbstractUserCreateReq;
import cn.ac.fage.accessmesh.access.role.dto.req.UserRoleBatchAssignReq;
import cn.ac.fage.accessmesh.access.user.dto.req.AbstractUserUpdateReq;
import cn.ac.fage.accessmesh.perm.common.dto.req.UserRoleBatchRevokeReq;
import cn.ac.fage.accessmesh.access.user.entity.AbstractUser;
import cn.ac.fage.accessmesh.access.role.entity.UserRole;
import cn.ac.fage.accessmesh.access.infrastructure.enums.AccessErrorCode;
import cn.ac.fage.accessmesh.access.type.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.user.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.access.audit.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.domain.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.access.projection.LocalProjectionDomainService;
import cn.ac.fage.accessmesh.access.rule.service.domain.PermissionConflictDomainService;
import cn.ac.fage.accessmesh.access.engine.core.SubjectDomainService;
import cn.ac.fage.accessmesh.access.engine.core.TypeResolutionService;
import cn.ac.fage.accessmesh.access.engine.core.PermQueryEngine;
import cn.ac.fage.accessmesh.access.infrastructure.util.OperatorContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserManageAppServiceImplTest {

    @Mock private AbstractUserMapper abstractUserMapper;
    @Mock private SubjectDomainService subjectDomainService;
    @Mock private TypeResolutionService typeResolutionService;
    @Mock private DomainClassifyService domainClassifyService;
    @Mock private AuditDomainService auditDomainService;
    @Mock private LocalProjectionDomainService localProjectionDomainService;
    @Mock private PermQueryEngine engine;
    @Mock private PermissionConflictDomainService permissionConflictDomainService;

    private UserManageAppServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserManageAppServiceImpl(
            abstractUserMapper,
            subjectDomainService,
            typeResolutionService,
            domainClassifyService,
            auditDomainService,
            new cn.ac.fage.accessmesh.access.sync.guard.LocalProjectionGuard(),
            localProjectionDomainService,
            new ObjectMapper(),
            engine,
            permissionConflictDomainService
        );
    }

    @Test
    void shouldThrowUserRoleRelationNotFoundWhenRelationMissingDuringBatchRevoke() {
        UserRoleBatchRevokeReq req = new UserRoleBatchRevokeReq(List.of(
            new UserRoleBatchRevokeReq.RevokeItem("user", "u-1", "default", "role", "r-1", null)
        ));

        when(typeResolutionService.batchResolveRoleIds(eq(1L), eq("role"), eq(Set.of("r-1")), eq("default")))
            .thenReturn(Map.of("r-1", 10L));
        when(typeResolutionService.batchResolveUserIds(eq(1L), eq("user"), eq(Set.of("u-1"))))
            .thenReturn(Map.of("u-1", 20L));

        try (MockedStatic<OperatorContext> operatorContext = org.mockito.Mockito.mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.getDeniedResourceCodes(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE), eq(Set.of("10")), eq(OperationCode.MANAGE)))
                .thenReturn(Set.of());
            when(subjectDomainService.selectValidRolesByIds(eq(1L), eq(Set.of(10L)))).thenReturn(List.of());
            when(subjectDomainService.selectValidUserRolesByUserIdsTypeAndTargetIds(eq(1L), eq(Set.of(20L)), eq(ResourceTypeCode.ROLE), eq(Set.of(10L))))
                .thenReturn(List.<UserRole>of());

            BizException exception = assertThrows(BizException.class, () -> service.revokeRolesBatch(1L, req));

            assertEquals(AccessErrorCode.USER_ROLE_RELATION_NOT_FOUND.getCode(), exception.getErrorCode());
        }
    }

    /** T-ACCESS-019：createUser 同事务维护 resource_entity(USER) 投影（code=subjectId）并登记变更日志。 */
    @Test
    void shouldProjectUserResourceOnCreate() {
        AbstractUserCreateReq req = new AbstractUserCreateReq("USER", "ext-u-1", "外部用户", true, null);
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.USER),
            org.mockito.ArgumentMatchers.isNull(), eq(OperationCode.CREATE))).thenReturn(true);
        when(typeResolutionService.resolveTypeValue(1L, "user_type", "USER")).thenReturn(1);
        when(typeResolutionService.resolveTypeCode(1L, "user_type", 1)).thenReturn("USER");
        org.mockito.Mockito.doAnswer(invocation -> {
            AbstractUser inserted = invocation.getArgument(0);
            inserted.setId(77L);
            return 1;
        }).when(abstractUserMapper).insert(any(AbstractUser.class));

        try (MockedStatic<OperatorContext> operatorContext = org.mockito.Mockito.mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);

            service.createUser(1L, req);
        }

        verify(localProjectionDomainService).upsertUserResource(1L, 77L, "外部用户", true);
        verify(auditDomainService).recordChangeLog(any(), any());
    }

    private AbstractUser externalUser(Long id) {
        AbstractUser existing = new AbstractUser();
        existing.setId(id);
        existing.setTenantId(1L);
        existing.setUserType(1);
        existing.setExternalId("ext-u-" + id);
        existing.setName("外部用户" + id);
        existing.setEnabled(true);
        return existing;
    }

    /** T-ACCESS-034 字段分档：name-only 变更查 USER:UPDATE（实例级，resource_entity(USER).code = subjectId）。 */
    @Test
    void shouldRejectUpdateUserNameOnlyWhenUpdateDenied() {
        when(subjectDomainService.selectValidUserById(1L, 77L)).thenReturn(externalUser(77L));
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.USER),
            eq("77"), eq(OperationCode.UPDATE))).thenReturn(false);

        try (MockedStatic<OperatorContext> operatorContext = org.mockito.Mockito.mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);

            assertThrows(SecurityException.class,
                () -> service.updateUser(1L, new AbstractUserUpdateReq(77L, "新名", null, null, null)));
        }
        verify(abstractUserMapper, org.mockito.Mockito.never()).update(any(AbstractUser.class));
    }

    /** T-ACCESS-034 字段分档：extra-only 变更同样查 USER:UPDATE。 */
    @Test
    void shouldRejectUpdateUserExtraOnlyWhenUpdateDenied() {
        when(subjectDomainService.selectValidUserById(1L, 77L)).thenReturn(externalUser(77L));
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.USER),
            eq("77"), eq(OperationCode.UPDATE))).thenReturn(false);

        try (MockedStatic<OperatorContext> operatorContext = org.mockito.Mockito.mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);

            assertThrows(SecurityException.class,
                () -> service.updateUser(1L, new AbstractUserUpdateReq(77L, null, null, "{\"k\":1}", null)));
        }
        verify(abstractUserMapper, org.mockito.Mockito.never()).update(any(AbstractUser.class));
    }

    /** T-API-004 双轨评审 P1：extraClear-only 清空同属 extra 变更，须查 USER:UPDATE——
     * 门禁条件曾漏计 extraClear（DTO 冲突锁保证 extraClear=true 时 extra 必 null），
     * 无 UPDATE 权限者可清空他人 extra；本用例在漏计实现下不抛异常必红。 */
    @Test
    void shouldRejectUpdateUserExtraClearOnlyWhenUpdateDenied() {
        when(subjectDomainService.selectValidUserById(1L, 77L)).thenReturn(externalUser(77L));
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.USER),
            eq("77"), eq(OperationCode.UPDATE))).thenReturn(false);

        try (MockedStatic<OperatorContext> operatorContext = org.mockito.Mockito.mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);

            assertThrows(SecurityException.class,
                () -> service.updateUser(1L, new AbstractUserUpdateReq(77L, null, null, null, true)));
        }
        verify(abstractUserMapper, org.mockito.Mockito.never()).update(any(AbstractUser.class));
    }

    /** T-ACCESS-034 字段分档：enabled-only 变更查 USER:ENABLE（防 UPDATE 绕过启停分权）。 */
    @Test
    void shouldRejectUpdateUserEnabledOnlyWhenEnableDenied() {
        when(subjectDomainService.selectValidUserById(1L, 77L)).thenReturn(externalUser(77L));
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.USER),
            eq("77"), eq(OperationCode.ENABLE))).thenReturn(false);

        try (MockedStatic<OperatorContext> operatorContext = org.mockito.Mockito.mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);

            assertThrows(SecurityException.class,
                () -> service.updateUser(1L, new AbstractUserUpdateReq(77L, null, false, null, null)));
        }
        verify(abstractUserMapper, org.mockito.Mockito.never()).update(any(AbstractUser.class));
    }

    /** T-ACCESS-034 组合字段：name+enabled 须同时通过 UPDATE 与 ENABLE（ENABLE 拒 → 拒）。
     * T-PERM-067 重排后启停门禁先行：ENABLE 拒绝即短路，UPDATE 门禁不再到达。 */
    @Test
    void shouldRejectCombinedPatchWhenEitherOperationDenied() {
        when(subjectDomainService.selectValidUserById(1L, 77L)).thenReturn(externalUser(77L));
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.USER),
            eq("77"), eq(OperationCode.ENABLE))).thenReturn(false);

        try (MockedStatic<OperatorContext> operatorContext = org.mockito.Mockito.mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);

            assertThrows(SecurityException.class,
                () -> service.updateUser(1L, new AbstractUserUpdateReq(77L, "新名", false, null, null)));
        }
        verify(engine).hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.USER),
            eq("77"), eq(OperationCode.ENABLE));
        verify(engine, org.mockito.Mockito.never()).hasPermissionByCode(eq(1L), eq(100L),
            eq(ResourceTypeCode.USER), eq("77"), eq(OperationCode.UPDATE));
        verify(abstractUserMapper, org.mockito.Mockito.never()).update(any(AbstractUser.class));
    }

    /** T-PERM-067（Q-002 收窄）：自身档案字段豁免保留——self+name 零门禁直接落库。
     * （生产面操作者==目标必被 rejectIfLocalUser 先拒，此处经 mock 守卫直测门禁语义。） */
    @Test
    void shouldAllowSelfProfileEditWithoutAnyUserOperation() {
        when(subjectDomainService.selectValidUserById(1L, 100L)).thenReturn(externalUser(100L));
        when(typeResolutionService.resolveTypeCode(1L, "user_type", 1)).thenReturn("USER");

        try (MockedStatic<OperatorContext> operatorContext = org.mockito.Mockito.mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);

            service.updateUser(1L, new AbstractUserUpdateReq(100L, "自改名", null, null, null));
        }
        verify(engine, org.mockito.Mockito.never()).hasPermissionByCode(anyLong(), anyLong(),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString());
        verify(abstractUserMapper).update(any(AbstractUser.class));
    }

    /** T-PERM-067（Q-002 收窄）：自身 enabled 变更不豁免——零 USER:ENABLE 操作位被拒
     * （旧实现自身整段跳过门禁，本用例必红）。 */
    @Test
    void shouldRejectSelfEnabledChangeWithoutEnableBit() {
        when(subjectDomainService.selectValidUserById(1L, 100L)).thenReturn(externalUser(100L));
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.USER),
            eq("100"), eq(OperationCode.ENABLE))).thenReturn(false);

        try (MockedStatic<OperatorContext> operatorContext = org.mockito.Mockito.mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);

            assertThrows(SecurityException.class,
                () -> service.updateUser(1L, new AbstractUserUpdateReq(100L, null, false, null, null)));
        }
        verify(abstractUserMapper, org.mockito.Mockito.never()).update(any(AbstractUser.class));
    }

    /** T-ACCESS-019：updateUser 同事务镜像 name/enabled 到 USER 投影（门禁按 T-ACCESS-034 字段分档）。 */
    @Test
    void shouldProjectUserResourceOnUpdate() {
        AbstractUser existing = new AbstractUser();
        existing.setId(77L);
        existing.setTenantId(1L);
        existing.setUserType(1);
        existing.setExternalId("ext-u-1");
        existing.setName("外部用户");
        existing.setEnabled(true);
        when(subjectDomainService.selectValidUserById(1L, 77L)).thenReturn(existing);
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.USER),
            eq("77"), eq(OperationCode.UPDATE))).thenReturn(true);
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.USER),
            eq("77"), eq(OperationCode.ENABLE))).thenReturn(true);
        when(typeResolutionService.resolveTypeCode(1L, "user_type", 1)).thenReturn("USER");

        try (MockedStatic<OperatorContext> operatorContext = org.mockito.Mockito.mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);

            service.updateUser(1L, new AbstractUserUpdateReq(77L, "新名", false, null, null));
        }

        verify(localProjectionDomainService).upsertUserResource(1L, 77L, "新名", false);
        verify(auditDomainService).recordChangeLog(any(), any());
    }

    /** T-ACCESS-019：deleteUsers 同事务批量软删 USER 投影（门禁按 T-ACCESS-034 换绑 USER:DELETE）。 */
    @Test
    void shouldSoftDeleteUserResourcesOnRemove() {
        AbstractUser existing = new AbstractUser();
        existing.setId(77L);
        existing.setTenantId(1L);
        existing.setUserType(1);
        existing.setExternalId("ext-u-1");
        existing.setName("外部用户");
        existing.setEnabled(true);
        when(abstractUserMapper.selectValidByIds(eq(1L), eq(Set.of(77L)))).thenReturn(List.of(existing));
        when(engine.getDeniedResourceCodes(eq(1L), eq(100L), eq(ResourceTypeCode.USER),
            eq(Set.of("77")), eq(OperationCode.DELETE))).thenReturn(Set.of());
        when(subjectDomainService.selectValidUserRolesByUserIds(eq(1L), eq(Set.of(77L)))).thenReturn(List.<UserRole>of());

        try (MockedStatic<OperatorContext> operatorContext = org.mockito.Mockito.mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);

            service.deleteUsers(1L, List.of(77L));
        }

        verify(localProjectionDomainService).softDeleteUserResources(1L, Set.of(77L));
        verify(auditDomainService).recordChangeLog(any(), any());
    }

    /** T-PERM-067（Q-002 收窄）：删除不豁免——门禁查全量 existing ids 含操作者自身，
     * nonSelfUserIds 静默剔除特例退役（旧实现门禁集 Set.of("77")，本用例参数断言必红）。
     * （生产面批量含自身必被 rejectIfLocalUser 先拒，此处经 mock 守卫直测门禁语义。） */
    @Test
    void shouldGateAllExistingIdsIncludingSelfOnRemove() {
        when(abstractUserMapper.selectValidByIds(eq(1L), eq(Set.of(100L, 77L))))
            .thenReturn(List.of(externalUser(100L), externalUser(77L)));
        when(engine.getDeniedResourceCodes(eq(1L), eq(100L), eq(ResourceTypeCode.USER),
            eq(Set.of("100", "77")), eq(OperationCode.DELETE))).thenReturn(Set.of());
        when(subjectDomainService.selectValidUserRolesByUserIds(eq(1L), eq(Set.of(100L, 77L)))).thenReturn(List.<UserRole>of());

        try (MockedStatic<OperatorContext> operatorContext = org.mockito.Mockito.mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);

            service.deleteUsers(1L, List.of(100L, 77L));
        }

        verify(engine).getDeniedResourceCodes(eq(1L), eq(100L), eq(ResourceTypeCode.USER),
            eq(Set.of("100", "77")), eq(OperationCode.DELETE));
        verify(localProjectionDomainService).softDeleteUserResources(1L, Set.of(100L, 77L));
    }

    /** T-PERM-067（Q-002 收窄）：自身 DELETE 被拒 → 整批拒绝（旧实现自身被剔出门禁集随批量软删，本用例必红）。 */
    @Test
    void shouldRejectWholeBatchWhenSelfDeleteDenied() {
        when(abstractUserMapper.selectValidByIds(eq(1L), eq(Set.of(100L, 77L))))
            .thenReturn(List.of(externalUser(100L), externalUser(77L)));
        when(engine.getDeniedResourceCodes(eq(1L), eq(100L), eq(ResourceTypeCode.USER),
            eq(Set.of("100", "77")), eq(OperationCode.DELETE))).thenReturn(Set.of("100"));

        try (MockedStatic<OperatorContext> operatorContext = org.mockito.Mockito.mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);

            assertThrows(SecurityException.class, () -> service.deleteUsers(1L, List.of(100L, 77L)));
        }
        verify(abstractUserMapper, org.mockito.Mockito.never()).softDeleteBatch(anyLong(), any(), any());
        verify(localProjectionDomainService, org.mockito.Mockito.never()).softDeleteUserResources(anyLong(), any());
    }

    /** 装配 assignRole 公共依赖：用户/角色解析、门禁放行、无既有关系。 */
    private void stubAssignRoleBasics() {
        when(typeResolutionService.batchResolveUserIds(eq(1L), eq("USER"), eq(Set.of("u-1"))))
            .thenReturn(Map.of("u-1", 20L));
        when(typeResolutionService.batchResolveRoleIds(eq(1L), eq("BASIC_ROLE"), eq(Set.of("r-200")), eq((String) null)))
            .thenReturn(Map.of("r-200", 200L));
        when(subjectDomainService.selectValidUserRolesByUserIdsAndTargetIds(eq(1L), eq(Set.of(20L)), eq(Set.of(200L)), eq(ResourceTypeCode.ROLE)))
            .thenReturn(List.<UserRole>of());
        when(subjectDomainService.batchResolveRawHoldings(eq(1L), eq(Set.of(20L))))
            .thenReturn(Map.of(20L, Set.of(raw(100L))));
    }

    /** T-PERM-063：授予后状态命中互斥对 → 整批原子拒绝 20062（旧实现直接落库，本用例必红）。 */
    @Test
    void shouldRejectAssignRoleWhenPostStateHitsMutexPair() {
        UserAssignRoleReq req = new UserAssignRoleReq(List.of(
            new UserAssignRoleReq.AssignItem(
                "USER", "u-1", null, "BASIC_ROLE", "r-200", null, null, null)
        ));
        stubAssignRoleBasics();

        try (MockedStatic<OperatorContext> operatorContext = org.mockito.Mockito.mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.getDeniedResourceCodes(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE),
                eq(Set.of("200")), eq(OperationCode.MANAGE))).thenReturn(Set.of());
            when(permissionConflictDomainService.findAssignMutexConflicts(eq(1L), any()))
                .thenReturn(List.of(new PermissionConflictDomainService.RoleMutexAssignConflict(
                    20L, 9L, 100L, 200L)));

            BizException exception = assertThrows(BizException.class, () -> service.assignRole(1L, req));

            assertEquals(AccessErrorCode.ROLE_MUTEX_ASSIGN_CONFLICT.getCode(), exception.getErrorCode());
            org.mockito.Mockito.verify(subjectDomainService, org.mockito.Mockito.never()).insertUserRoles(any());
        }
    }

    /** T-PERM-063：无冲突照常落库（守卫不拦截正常授予）。 */
    @Test
    void shouldAssignRoleWhenNoMutexConflict() {
        UserAssignRoleReq req = new UserAssignRoleReq(List.of(
            new UserAssignRoleReq.AssignItem(
                "USER", "u-1", null, "BASIC_ROLE", "r-200", null, null, null)
        ));
        stubAssignRoleBasics();

        try (MockedStatic<OperatorContext> operatorContext = org.mockito.Mockito.mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.getDeniedResourceCodes(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE),
                eq(Set.of("200")), eq(OperationCode.MANAGE))).thenReturn(Set.of());
            when(permissionConflictDomainService.findAssignMutexConflicts(eq(1L), any()))
                .thenReturn(List.of());

            service.assignRole(1L, req);

            org.mockito.Mockito.verify(subjectDomainService).insertUserRoles(any());
        }
    }

    /**
     * T-PERM-075 U002-2（用户拍板 2026-09-22）：目标角色禁用仍进写时候选——绑定时刻已知互斥对即拒绝，
     * 不把冲突推迟到启用动作。旧口径「禁用目标不参与互斥判定、放行」随之退役
     * （旧实现在本用例下放行落库，必红）。
     */
    @Test
    void shouldRejectAssignRoleWhenTargetRoleDisabledButMutexPairPresent() {
        UserAssignRoleReq req = new UserAssignRoleReq(List.of(
            new UserAssignRoleReq.AssignItem("USER", "u-1", null, "BASIC_ROLE", "r-200", null, null, null)
        ));
        when(typeResolutionService.batchResolveUserIds(eq(1L), eq("USER"), eq(Set.of("u-1"))))
            .thenReturn(Map.of("u-1", 20L));
        when(typeResolutionService.batchResolveRoleIds(eq(1L), eq("BASIC_ROLE"), eq(Set.of("r-200")), eq((String) null)))
            .thenReturn(Map.of("r-200", 200L));
        when(subjectDomainService.selectValidUserRolesByUserIdsAndTargetIds(eq(1L), eq(Set.of(20L)), eq(Set.of(200L)), eq(ResourceTypeCode.ROLE)))
            .thenReturn(List.<UserRole>of());
        // 目标 200 禁用：postState 仍并入 200（原始候选口径）→ 用户已持互斥对端 100 → 命中规则拒绝
        when(subjectDomainService.batchResolveRawHoldings(eq(1L), eq(Set.of(20L))))
            .thenReturn(Map.of(20L, Set.of(raw(100L))));

        try (MockedStatic<OperatorContext> operatorContext = org.mockito.Mockito.mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.getDeniedResourceCodes(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE),
                eq(Set.of("200")), eq(OperationCode.MANAGE))).thenReturn(Set.of());
            when(permissionConflictDomainService.findAssignMutexConflicts(eq(1L), any()))
                .thenReturn(List.of(new PermissionConflictDomainService.RoleMutexAssignConflict(
                    20L, 9L, 100L, 200L)));

            BizException exception = assertThrows(BizException.class, () -> service.assignRole(1L, req));

            assertEquals(AccessErrorCode.ROLE_MUTEX_ASSIGN_CONFLICT.getCode(), exception.getErrorCode());
            org.mockito.Mockito.verify(subjectDomainService, org.mockito.Mockito.never()).insertUserRoles(any());
            // 锁候选口径：禁用目标 200 在 postState（收敛启用的旧实现在此必红）
            org.mockito.ArgumentCaptor<Map<Long, Set<cn.ac.fage.accessmesh.access.engine.core.SubjectDomainService.RawHolding>>> postStateCaptor =
                org.mockito.ArgumentCaptor.forClass(Map.class);
            org.mockito.Mockito.verify(permissionConflictDomainService)
                .findAssignMutexConflicts(eq(1L), postStateCaptor.capture());
            assertEquals(Set.of(100L, 200L), rawRoleIds(postStateCaptor.getValue().get(20L)));
        }
    }

    /**
     * T-PERM-075 U002-1（用户拍板 2026-09-22）：未来 valid_from 窗口进写时候选——与既有持有
     * 的重叠在写时即拒绝，不留给运行时双删。旧口径「生效期未到的目标不计入授予后状态」
     * 随之退役（旧实现 postState 不含 200，规则 (100,200) 不命中放行，本用例必红）。
     */
    @Test
    void shouldIncludeFutureValidFromTargetInPostState() {
        // 混合批：r-200 带 7 天后生效的 validFrom、r-300 即时生效——postState 应同时含 200/300
        UserAssignRoleReq req = new UserAssignRoleReq(List.of(
            new UserAssignRoleReq.AssignItem("USER", "u-1", null, "BASIC_ROLE", "r-200",
                null, java.time.LocalDateTime.now().plusDays(7), null),
            new UserAssignRoleReq.AssignItem("USER", "u-1", null, "BASIC_ROLE", "r-300",
                null, null, null)
        ));
        when(typeResolutionService.batchResolveUserIds(eq(1L), eq("USER"), eq(Set.of("u-1"))))
            .thenReturn(Map.of("u-1", 20L));
        when(typeResolutionService.batchResolveRoleIds(eq(1L), eq("BASIC_ROLE"), eq(Set.of("r-200", "r-300")), eq((String) null)))
            .thenReturn(Map.of("r-200", 200L, "r-300", 300L));
        when(subjectDomainService.selectValidUserRolesByUserIdsAndTargetIds(eq(1L), eq(Set.of(20L)), eq(Set.of(200L, 300L)), eq(ResourceTypeCode.ROLE)))
            .thenReturn(List.<UserRole>of());
        when(subjectDomainService.batchResolveRawHoldings(eq(1L), eq(Set.of(20L))))
            .thenReturn(Map.of(20L, Set.of(raw(100L))));

        try (MockedStatic<OperatorContext> operatorContext = org.mockito.Mockito.mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.getDeniedResourceCodes(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE),
                eq(Set.of("200", "300")), eq(OperationCode.MANAGE))).thenReturn(Set.of());

            service.assignRole(1L, req);

            // 锁候选口径：未来 validFrom 的 200 与即时 300 均入 postState（旧谓词实现必红）
            org.mockito.ArgumentCaptor<Map<Long, Set<cn.ac.fage.accessmesh.access.engine.core.SubjectDomainService.RawHolding>>> postStateCaptor =
                org.mockito.ArgumentCaptor.forClass(Map.class);
            org.mockito.Mockito.verify(permissionConflictDomainService)
                .findAssignMutexConflicts(eq(1L), postStateCaptor.capture());
            assertEquals(Set.of(100L, 200L, 300L), rawRoleIds(postStateCaptor.getValue().get(20L)));
            org.mockito.Mockito.verify(subjectDomainService).insertUserRoles(any());
        }
    }

    /** T-PERM-075 U002-1 边界：已过期（valid_to < now）新增行永不生效，不进写时候选。 */
    @Test
    void shouldExcludeExpiredTargetFromPostState() {
        UserAssignRoleReq req = new UserAssignRoleReq(List.of(
            new UserAssignRoleReq.AssignItem("USER", "u-1", null, "BASIC_ROLE", "r-200",
                null, null, java.time.LocalDateTime.now().minusDays(1))
        ));
        when(typeResolutionService.batchResolveUserIds(eq(1L), eq("USER"), eq(Set.of("u-1"))))
            .thenReturn(Map.of("u-1", 20L));
        when(typeResolutionService.batchResolveRoleIds(eq(1L), eq("BASIC_ROLE"), eq(Set.of("r-200")), eq((String) null)))
            .thenReturn(Map.of("r-200", 200L));
        when(subjectDomainService.selectValidUserRolesByUserIdsAndTargetIds(eq(1L), eq(Set.of(20L)), eq(Set.of(200L)), eq(ResourceTypeCode.ROLE)))
            .thenReturn(List.<UserRole>of());

        try (MockedStatic<OperatorContext> operatorContext = org.mockito.Mockito.mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.getDeniedResourceCodes(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE),
                eq(Set.of("200")), eq(OperationCode.MANAGE))).thenReturn(Set.of());

            service.assignRole(1L, req);

            // 全部新增行已过期 → 无未过期新增 → 不触发守卫查询（守卫查询被调用即红）
            org.mockito.Mockito.verify(permissionConflictDomainService, org.mockito.Mockito.never())
                .findAssignMutexConflicts(anyLong(), any());
            org.mockito.Mockito.verify(subjectDomainService).insertUserRoles(any());
        }
    }

    /** T-PERM-063：batch-assign（单角色×多用户）同款守卫——用户现持有互斥对端角色时整批拒绝。 */
    @Test
    void shouldRejectBatchAssignWhenPostStateHitsMutexPair() {
        UserRoleBatchAssignReq req =
            new UserRoleBatchAssignReq(
                List.of("u-1"), "USER", null, "BASIC_ROLE", "r-200", null);
        when(typeResolutionService.batchResolveUserIds(eq(1L), eq("USER"), eq(Set.of("u-1"))))
            .thenReturn(Map.of("u-1", 20L));
        when(typeResolutionService.resolveRoleId(eq(1L), eq("BASIC_ROLE"), eq("r-200"), eq((String) null)))
            .thenReturn(200L);
        when(subjectDomainService.selectValidUserRolesByUserIdsAndTargetId(eq(1L), eq(Set.of(20L)), eq(200L), eq(ResourceTypeCode.ROLE)))
            .thenReturn(List.<UserRole>of());
        when(subjectDomainService.batchResolveRawHoldings(eq(1L), eq(Set.of(20L))))
            .thenReturn(Map.of(20L, Set.of(raw(100L))));

        try (MockedStatic<OperatorContext> operatorContext = org.mockito.Mockito.mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.getDeniedResourceCodes(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE),
                eq(Set.of("200")), eq(OperationCode.MANAGE))).thenReturn(Set.of());
            when(permissionConflictDomainService.findAssignMutexConflicts(eq(1L), any()))
                .thenReturn(List.of(new PermissionConflictDomainService.RoleMutexAssignConflict(
                    20L, 9L, 100L, 200L)));

            BizException exception = assertThrows(BizException.class, () -> service.assignRolesBatch(1L, req));

            assertEquals(AccessErrorCode.ROLE_MUTEX_ASSIGN_CONFLICT.getCode(), exception.getErrorCode());
            org.mockito.Mockito.verify(subjectDomainService, org.mockito.Mockito.never()).insertUserRoles(any());
        }
    }

    /**
     * T-PERM-075 U002-2 反方向：用户已持有「禁用」的互斥对端（经原始持有候选可见），
     * 再绑定启用角色时同样拒绝——有效角色集口径看不到禁用持有（旧实现放行，本用例必红）。
     */
    @Test
    void shouldRejectAssignRoleWhenHoldingDisabledMutexPeer() {
        UserAssignRoleReq req = new UserAssignRoleReq(List.of(
            new UserAssignRoleReq.AssignItem("USER", "u-1", null, "BASIC_ROLE", "r-200", null, null, null)
        ));
        when(typeResolutionService.batchResolveUserIds(eq(1L), eq("USER"), eq(Set.of("u-1"))))
            .thenReturn(Map.of("u-1", 20L));
        when(typeResolutionService.batchResolveRoleIds(eq(1L), eq("BASIC_ROLE"), eq(Set.of("r-200")), eq((String) null)))
            .thenReturn(Map.of("r-200", 200L));
        when(subjectDomainService.selectValidUserRolesByUserIdsAndTargetIds(eq(1L), eq(Set.of(20L)), eq(Set.of(200L)), eq(ResourceTypeCode.ROLE)))
            .thenReturn(List.<UserRole>of());
        // 原始持有候选含禁用的 100（运行时有效角色集会过滤掉它，写守卫不看过滤集）
        when(subjectDomainService.batchResolveRawHoldings(eq(1L), eq(Set.of(20L))))
            .thenReturn(Map.of(20L, Set.of(raw(100L))));

        try (MockedStatic<OperatorContext> operatorContext = org.mockito.Mockito.mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.getDeniedResourceCodes(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE),
                eq(Set.of("200")), eq(OperationCode.MANAGE))).thenReturn(Set.of());
            when(permissionConflictDomainService.findAssignMutexConflicts(eq(1L), any()))
                .thenReturn(List.of(new PermissionConflictDomainService.RoleMutexAssignConflict(
                    20L, 9L, 100L, 200L)));

            BizException exception = assertThrows(BizException.class, () -> service.assignRole(1L, req));

            assertEquals(AccessErrorCode.ROLE_MUTEX_ASSIGN_CONFLICT.getCode(), exception.getErrorCode());
            org.mockito.Mockito.verify(subjectDomainService, org.mockito.Mockito.never()).insertUserRoles(any());
        }
    }

    private static cn.ac.fage.accessmesh.access.engine.core.SubjectDomainService.RawHolding raw(Long roleId) {
        return new cn.ac.fage.accessmesh.access.engine.core.SubjectDomainService.RawHolding(roleId, null, null);
    }

    private static Set<Long> rawRoleIds(Set<cn.ac.fage.accessmesh.access.engine.core.SubjectDomainService.RawHolding> holdings) {
        return holdings.stream()
            .map(cn.ac.fage.accessmesh.access.engine.core.SubjectDomainService.RawHolding::roleId)
            .collect(java.util.stream.Collectors.toSet());
    }
}