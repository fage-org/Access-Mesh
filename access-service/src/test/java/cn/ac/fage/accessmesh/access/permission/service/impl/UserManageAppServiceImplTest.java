package cn.ac.fage.accessmesh.access.permission.service.impl;

import cn.ac.fage.accessmesh.common.exception.BizException;
import cn.ac.fage.accessmesh.access.permission.constant.OperationCodeConstants;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserAssignRoleReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserCreateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleBatchAssignReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserUpdateReq;
import cn.ac.fage.accessmesh.access.permission.dto.req.UserRoleBatchRevokeReq;
import cn.ac.fage.accessmesh.access.permission.entity.AbstractUser;
import cn.ac.fage.accessmesh.access.permission.entity.UserRole;
import cn.ac.fage.accessmesh.access.permission.enums.PermissionErrorCode;
import cn.ac.fage.accessmesh.access.permission.enums.ResourceTypeCode;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractRoleMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.AbstractUserMapper;
import cn.ac.fage.accessmesh.access.permission.mapper.UserRoleMapper;
import cn.ac.fage.accessmesh.access.permission.service.domain.AuditDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.DomainClassifyService;
import cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.PermissionConflictDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.SubjectDomainService;
import cn.ac.fage.accessmesh.access.permission.service.domain.TypeResolutionService;
import cn.ac.fage.accessmesh.access.permission.service.domain.impl.PermQueryEngine;
import cn.ac.fage.accessmesh.access.permission.util.OperatorContext;
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
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private AbstractRoleMapper abstractRoleMapper;
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
            userRoleMapper,
            abstractRoleMapper,
            subjectDomainService,
            typeResolutionService,
            domainClassifyService,
            auditDomainService,
            new cn.ac.fage.accessmesh.access.permission.service.domain.LocalProjectionGuard(),
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
            when(engine.getDeniedResourceCodes(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE), eq(Set.of("10")), eq(OperationCodeConstants.MANAGE)))
                .thenReturn(Set.of());
            when(abstractRoleMapper.selectValidByIds(eq(1L), eq(Set.of(10L)))).thenReturn(List.of());
            when(userRoleMapper.selectValidByUserIdsTypeAndTargetIds(eq(1L), eq(Set.of(20L)), eq(ResourceTypeCode.ROLE), eq(Set.of(10L))))
                .thenReturn(List.<UserRole>of());

            BizException exception = assertThrows(BizException.class, () -> service.revokeRolesBatch(1L, req));

            assertEquals(PermissionErrorCode.USER_ROLE_RELATION_NOT_FOUND.getCode(), exception.getErrorCode());
        }
    }

    /** T-ACCESS-019：createUser 同事务维护 resource_entity(USER) 投影（code=subjectId）并登记变更日志。 */
    @Test
    void shouldProjectUserResourceOnCreate() {
        UserCreateReq req = new UserCreateReq("USER", "ext-u-1", "外部用户", true, null);
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.USER),
            org.mockito.ArgumentMatchers.isNull(), eq(OperationCodeConstants.CREATE))).thenReturn(true);
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

    /** T-ACCESS-019：updateUser 门禁升实例级（resource_entity(USER).code = subjectId）。 */
    @Test
    void shouldRejectUpdateUserByInstanceCodeWhenDenied() {
        AbstractUser existing = new AbstractUser();
        existing.setId(77L);
        existing.setTenantId(1L);
        existing.setUserType(1);
        existing.setExternalId("ext-u-1");
        existing.setName("外部用户");
        existing.setEnabled(true);
        when(subjectDomainService.selectValidUserById(1L, 77L)).thenReturn(existing);
        when(engine.hasPermissionByCode(eq(1L), eq(100L), eq(ResourceTypeCode.USER),
            eq("77"), eq(OperationCodeConstants.MANAGE))).thenReturn(false);

        try (MockedStatic<OperatorContext> operatorContext = org.mockito.Mockito.mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);

            assertThrows(SecurityException.class,
                () -> service.updateUser(1L, new UserUpdateReq(77L, "新名", null, null)));
        }
        verify(abstractUserMapper, org.mockito.Mockito.never()).update(any(AbstractUser.class));
    }

    /** T-ACCESS-019：updateUser 同事务镜像 name/enabled 到 USER 投影。 */
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
            eq("77"), eq(OperationCodeConstants.MANAGE))).thenReturn(true);
        when(typeResolutionService.resolveTypeCode(1L, "user_type", 1)).thenReturn("USER");

        try (MockedStatic<OperatorContext> operatorContext = org.mockito.Mockito.mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);

            service.updateUser(1L, new UserUpdateReq(77L, "新名", false, null));
        }

        verify(localProjectionDomainService).upsertUserResource(1L, 77L, "新名", false);
        verify(auditDomainService).recordChangeLog(any(), any());
    }

    /** T-ACCESS-019：deleteUsers 同事务批量软删 USER 投影。 */
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
            eq(Set.of("77")), eq(OperationCodeConstants.MANAGE))).thenReturn(Set.of());
        when(userRoleMapper.selectValidByUserIds(eq(1L), eq(Set.of(77L)))).thenReturn(List.<UserRole>of());

        try (MockedStatic<OperatorContext> operatorContext = org.mockito.Mockito.mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);

            service.deleteUsers(1L, List.of(77L));
        }

        verify(localProjectionDomainService).softDeleteUserResources(1L, Set.of(77L));
        verify(auditDomainService).recordChangeLog(any(), any());
    }

    /** 装配 assignRole 公共依赖：用户/角色解析、门禁放行、无既有关系。 */
    private void stubAssignRoleBasics() {
        when(typeResolutionService.batchResolveUserIds(eq(1L), eq("USER"), eq(Set.of("u-1"))))
            .thenReturn(Map.of("u-1", 20L));
        when(typeResolutionService.batchResolveRoleIds(eq(1L), eq("BASIC_ROLE"), eq(Set.of("r-200")), eq((String) null)))
            .thenReturn(Map.of("r-200", 200L));
        when(userRoleMapper.selectValidByUserIdsAndTargetIds(eq(1L), eq(Set.of(20L)), eq(Set.of(200L)), eq(ResourceTypeCode.ROLE)))
            .thenReturn(List.<UserRole>of());
        when(abstractRoleMapper.selectEnabledIdsByIds(eq(1L), eq(Set.of(200L)))).thenReturn(List.of(200L));
        when(subjectDomainService.batchResolveEffectiveRoles(eq(1L), eq(Set.of(20L))))
            .thenReturn(Map.of(20L, Set.of(100L)));
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
                eq(Set.of("200")), eq(OperationCodeConstants.MANAGE))).thenReturn(Set.of());
            when(permissionConflictDomainService.findAssignMutexConflicts(eq(1L), any()))
                .thenReturn(List.of(new PermissionConflictDomainService.RoleMutexAssignConflict(
                    20L, 9L, 100L, 200L)));

            BizException exception = assertThrows(BizException.class, () -> service.assignRole(1L, req));

            assertEquals(PermissionErrorCode.ROLE_MUTEX_ASSIGN_CONFLICT.getCode(), exception.getErrorCode());
            org.mockito.Mockito.verify(userRoleMapper, org.mockito.Mockito.never()).insertBatch(any());
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
                eq(Set.of("200")), eq(OperationCodeConstants.MANAGE))).thenReturn(Set.of());
            when(permissionConflictDomainService.findAssignMutexConflicts(eq(1L), any()))
                .thenReturn(List.of());

            service.assignRole(1L, req);

            org.mockito.Mockito.verify(userRoleMapper).insertBatch(any());
        }
    }

    /** T-PERM-063：目标角色禁用不参与互斥判定（与运行时有效角色集合同源），授予放行。 */
    @Test
    void shouldAssignRoleWhenTargetRoleDisabled() {
        UserAssignRoleReq req = new UserAssignRoleReq(List.of(
            new UserAssignRoleReq.AssignItem("USER", "u-1", null, "BASIC_ROLE", "r-200", null, null, null)
        ));
        when(typeResolutionService.batchResolveUserIds(eq(1L), eq("USER"), eq(Set.of("u-1"))))
            .thenReturn(Map.of("u-1", 20L));
        when(typeResolutionService.batchResolveRoleIds(eq(1L), eq("BASIC_ROLE"), eq(Set.of("r-200")), eq((String) null)))
            .thenReturn(Map.of("r-200", 200L));
        when(userRoleMapper.selectValidByUserIdsAndTargetIds(eq(1L), eq(Set.of(20L)), eq(Set.of(200L)), eq(ResourceTypeCode.ROLE)))
            .thenReturn(List.<UserRole>of());
        // 目标角色 200 禁用：postState 不并入 → 即使用户已持互斥对端 100 也放行
        when(abstractRoleMapper.selectEnabledIdsByIds(eq(1L), eq(Set.of(200L)))).thenReturn(List.of());
        when(subjectDomainService.batchResolveEffectiveRoles(eq(1L), eq(Set.of(20L))))
            .thenReturn(Map.of(20L, Set.of(100L)));

        try (MockedStatic<OperatorContext> operatorContext = org.mockito.Mockito.mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.getDeniedResourceCodes(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE),
                eq(Set.of("200")), eq(OperationCodeConstants.MANAGE))).thenReturn(Set.of());

            service.assignRole(1L, req);

            // 锁「仅计启用角色」分支：postState 不含禁用目标 200（去掉过滤的实现在此必红）
            org.mockito.ArgumentCaptor<Map<Long, Set<Long>>> postStateCaptor =
                org.mockito.ArgumentCaptor.forClass(Map.class);
            org.mockito.Mockito.verify(permissionConflictDomainService)
                .findAssignMutexConflicts(eq(1L), postStateCaptor.capture());
            assertEquals(Set.of(100L), postStateCaptor.getValue().get(20L));
            org.mockito.Mockito.verify(userRoleMapper).insertBatch(any());
        }
    }

    /** T-PERM-063（外评 P3）：生效期未到的目标不计入授予后状态——有效期谓词镜像运行时。 */
    @Test
    void shouldExcludeFutureValidFromTargetFromPostState() {
        // 混合批：r-200 带 7 天后生效的 validFrom、r-300 即时生效——postState 应含 300 不含 200
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
        when(userRoleMapper.selectValidByUserIdsAndTargetIds(eq(1L), eq(Set.of(20L)), eq(Set.of(200L, 300L)), eq(ResourceTypeCode.ROLE)))
            .thenReturn(List.<UserRole>of());
        // 有效期过滤先行：future validFrom 的 200 已不入目标集，启用查询只见 300
        when(abstractRoleMapper.selectEnabledIdsByIds(eq(1L), eq(Set.of(300L)))).thenReturn(List.of(300L));
        when(subjectDomainService.batchResolveEffectiveRoles(eq(1L), eq(Set.of(20L))))
            .thenReturn(Map.of(20L, Set.of(100L)));

        try (MockedStatic<OperatorContext> operatorContext = org.mockito.Mockito.mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.getDeniedResourceCodes(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE),
                eq(Set.of("200", "300")), eq(OperationCodeConstants.MANAGE))).thenReturn(Set.of());

            service.assignRole(1L, req);

            // 锁有效期谓词：future validFrom 的 200 不入 postState、即时的 300 入（无谓词实现
            // postState={100,200,300}——若存在规则 (100,200) 即被误拒，断言不等于此形态必红）
            org.mockito.ArgumentCaptor<Map<Long, Set<Long>>> postStateCaptor =
                org.mockito.ArgumentCaptor.forClass(Map.class);
            org.mockito.Mockito.verify(permissionConflictDomainService)
                .findAssignMutexConflicts(eq(1L), postStateCaptor.capture());
            assertEquals(Set.of(100L, 300L), postStateCaptor.getValue().get(20L));
            org.mockito.Mockito.verify(userRoleMapper).insertBatch(any());
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
        when(userRoleMapper.selectValidByUserIdsAndTargetId(eq(1L), eq(Set.of(20L)), eq(200L), eq(ResourceTypeCode.ROLE)))
            .thenReturn(List.<UserRole>of());
        when(abstractRoleMapper.selectEnabledIdsByIds(eq(1L), eq(Set.of(200L)))).thenReturn(List.of(200L));
        when(subjectDomainService.batchResolveEffectiveRoles(eq(1L), eq(Set.of(20L))))
            .thenReturn(Map.of(20L, Set.of(100L)));

        try (MockedStatic<OperatorContext> operatorContext = org.mockito.Mockito.mockStatic(OperatorContext.class)) {
            operatorContext.when(OperatorContext::getOperatorId).thenReturn(100L);
            when(engine.getDeniedResourceCodes(eq(1L), eq(100L), eq(ResourceTypeCode.ROLE),
                eq(Set.of("200")), eq(OperationCodeConstants.MANAGE))).thenReturn(Set.of());
            when(permissionConflictDomainService.findAssignMutexConflicts(eq(1L), any()))
                .thenReturn(List.of(new PermissionConflictDomainService.RoleMutexAssignConflict(
                    20L, 9L, 100L, 200L)));

            BizException exception = assertThrows(BizException.class, () -> service.assignRolesBatch(1L, req));

            assertEquals(PermissionErrorCode.ROLE_MUTEX_ASSIGN_CONFLICT.getCode(), exception.getErrorCode());
            org.mockito.Mockito.verify(userRoleMapper, org.mockito.Mockito.never()).insertBatch(any());
        }
    }
}